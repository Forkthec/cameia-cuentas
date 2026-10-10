package tech.cameia.cuentas.presentation.advice;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.validator.engine.HibernateConstraintViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.exception.EmailNotVerifiedException;
import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
import tech.cameia.cuentas.domain.exception.InvalidFieldException;
import tech.cameia.cuentas.domain.exception.InvalidPronounException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Traduce los fallos a respuestas {@code application/problem+json} (RFC 9457).
 *
 * <p>Toda respuesta de error lleva, además de {@code type}, {@code title}, {@code status},
 * {@code detail} e {@code instance}, dos miembros propios: {@code code}, un código estable
 * del catálogo {@link ErrorCode} con el que el cliente decide qué mostrar, y
 * {@code requestId}, el identificador con el que esa respuesta se encuentra en el log. Los
 * errores de validación agregan {@code errors}, con un elemento {@code field}, {@code code}
 * y {@code message} por campo rechazado.</p>
 *
 * <p>Los mensajes de las excepciones de negocio llegan al usuario tal cual, porque le
 * dicen qué corregir. Los fallos técnicos, en cambio, se registran completos en el log y
 * al cliente solo le llega un texto genérico: el detalle de una excepción interna describe
 * la infraestructura y no ayuda a quien está llenando un formulario.</p>
 *
 * <p>Cada error se registra aquí y una sola vez: los rechazos de la persona (4xx) en
 * {@code WARN} sin traza y los fallos del servicio (5xx) en {@code ERROR} con traza.
 * Ninguna línea del log ni ninguna respuesta repite el cuerpo de la petición, donde viajan
 * la contraseña y el correo, ni el mensaje de una excepción de librería, que suele citar
 * el valor recibido.</p>
 */
@RestControllerAdvice
class BusinessExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(BusinessExceptionHandler.class);

    /** Nombre de la propiedad que lista los campos rechazados. */
    private static final String ERRORS = "errors";

    /** Encabezado con el identificador de la petición, que pone el Gateway. */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * Forma admitida para un identificador recibido. Lo que no la cumple se reemplaza por
     * uno nuevo: así un valor con saltos de línea o de tamaño arbitrario nunca llega al log
     * ni a la respuesta.
     */
    private static final Pattern REQUEST_ID_VALIDO = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    /** Título común de los errores de validación. */
    private static final String TITULO_VALIDACION = "Datos no válidos";
    private static final String TITULO_CORREO_REPETIDO = "Correo ya registrado";

    /** {@code detail} fijo de todo 422 con lista de campos: el mensaje de cada uno va en {@code errors}. */
    private static final String DETALLE_VALIDACION = "Revisa los campos marcados.";

    /** {@code detail} de todo fallo que la persona no puede corregir. */
    private static final String DETALLE_INTERNO = "Ocurrió un error. Inténtalo de nuevo.";

    /**
     * Máximo de causas que se recorren: una cadena con un ciclo (A causada por B y B por A) no
     * debe dejar el hilo en un bucle.
     */
    private static final int MAX_CAUSAS = 32;

    /** Campo del contrato de los pronombres, el único enumerado del registro. */
    private static final String CAMPO_PRONOMBRE = "pronoun";

    /**
     * Código de cada restricción del contrato, con la clave {@code campo.Restriccion}.
     *
     * <p>Es una tabla y no un {@code switch} para que una prueba pueda recorrerla y exigir
     * que toda restricción del contrato tenga su código.</p>
     */
    static final Map<String, ErrorCode> FIELD_ERROR_CODES = Map.ofEntries(
            Map.entry("firstName.NotBlank", ErrorCode.FIRST_NAME_REQUIRED),
            Map.entry("firstName.CodePointSize", ErrorCode.FIRST_NAME_TOO_LONG),
            Map.entry("lastName.NotBlank", ErrorCode.LAST_NAME_REQUIRED),
            Map.entry("lastName.CodePointSize", ErrorCode.LAST_NAME_TOO_LONG),
            Map.entry("birthDate.NotBlank", ErrorCode.BIRTH_DATE_REQUIRED),
            Map.entry("birthDate.BirthDateFormat", ErrorCode.BIRTH_DATE_INVALID_FORMAT),
            Map.entry("email.NotBlank", ErrorCode.EMAIL_REQUIRED),
            Map.entry("email.CodePointSize", ErrorCode.EMAIL_TOO_LONG),
            Map.entry("password.NotBlank", ErrorCode.PASSWORD_REQUIRED),
            Map.entry("pronoun.NotBlank", ErrorCode.PRONOUN_REQUIRED));

    /**
     * Un campo rechazado dentro de la lista {@code errors}.
     *
     * @param field nombre del campo en el contrato JSON
     * @param code código estable de la causa
     * @param message texto para la persona
     */
    record CampoRechazado(String field, String code, String message) {
    }

    /**
     * Correo ya registrado.
     *
     * <p>El nivel del registro depende del origen. Un conflicto común es un {@code WARN}. Una
     * credencial sin cuenta local reciente es un registro en curso (otro {@code WARN}, con el
     * identificador). Una credencial sin cuenta local que ya es antigua es un residuo que alguien
     * debe conciliar a mano: un solo {@code ERROR}, sin el {@code WARN} de siempre. El correo
     * nunca se registra.</p>
     *
     * @param error excepción de negocio
     * @return {@code 409 Conflict} con el mensaje del criterio de aceptación y el campo {@code email}
     */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail correoRepetido(EmailAlreadyRegisteredException error) {
        ProblemDetail problema;
        if (error.requiresReconciliation()) {
            problema = problema(HttpStatus.CONFLICT, TITULO_CORREO_REPETIDO, error.getMessage(), error.getErrorCode());
            logger.error("Credencial sin cuenta local; requiere conciliación manual [code={}, requestId={}, uid={}]",
                    error.getErrorCode(), problema.getProperties().get("requestId"), error.getFirebaseUid());
        } else {
            String contexto = error.getFirebaseUid() == null
                    ? ""
                    : "uid=" + error.getFirebaseUid() + " registro en curso";
            problema = rechazo(HttpStatus.CONFLICT, TITULO_CORREO_REPETIDO, error.getMessage(),
                    error.getErrorCode(), contexto);
        }
        problema.setProperty(ERRORS, List.of(campo("email", error.getErrorCode(), error.getMessage())));
        return problema;
    }

    /**
     * Rechazo del dominio que pertenece a un solo campo: fecha de nacimiento, contraseña,
     * nombre, apellido, correo o celular.
     *
     * <p>Sale con la misma forma que los errores del contrato (un elemento en {@code errors}),
     * para que el cliente lea siempre la lista. El campo lo trae la excepción.</p>
     *
     * @param error excepción con el campo, el código y el mensaje del criterio
     * @return {@code 422 Unprocessable Entity} con un elemento para ese campo
     */
    @ExceptionHandler(InvalidFieldException.class)
    ProblemDetail invalidDomainField(InvalidFieldException error) {
        return validacion(List.of(campo(error.getField(), error.getErrorCode(), error.getMessage())));
    }

    /**
     * Correo aún sin verificar.
     *
     * @param error excepción de negocio
     * @return {@code 403 Forbidden}
     */
    @ExceptionHandler(EmailNotVerifiedException.class)
    ProblemDetail correoSinVerificar(EmailNotVerifiedException error) {
        return rechazo(HttpStatus.FORBIDDEN, "Correo sin verificar", error.getMessage(), error.getErrorCode());
    }

    /**
     * Usuario sin cuenta local.
     *
     * @param error excepción de negocio
     * @return {@code 404 Not Found}
     */
    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail cuentaInexistente(AccountNotFoundException error) {
        return rechazo(HttpStatus.NOT_FOUND, "Cuenta no encontrada", error.getMessage(), error.getErrorCode());
    }

    /**
     * Campos que incumplen las validaciones del contrato.
     *
     * <p>Se devuelve un solo elemento por campo, el de la primera restricción que Spring
     * informa para él: dos mensajes para el mismo campo confunden a la persona y el
     * formulario solo puede mostrar uno.</p>
     *
     * @param error excepción con la lista de campos rechazados
     * @return {@code 422 Unprocessable Entity} con un elemento por campo
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail camposInvalidos(MethodArgumentNotValidException error) {
        Map<String, CampoRechazado> porCampo = new LinkedHashMap<>();
        for (FieldError fallo : error.getBindingResult().getFieldErrors()) {
            porCampo.putIfAbsent(fallo.getField(),
                    campo(fallo.getField(), codigoDe(fallo), fallo.getDefaultMessage()));
        }
        return validacion(new ArrayList<>(porCampo.values()));
    }

    /**
     * Cuerpo que no se puede interpretar: JSON mal formado o un arreglo u objeto donde va un
     * texto.
     *
     * <p>Un arreglo u objeto en el pronombre sale como error de ese campo, igual que cualquier
     * otro valor fuera de la lista: el formulario lo muestra como una opción no válida. Cualquier
     * otra lectura fallida sale como cuerpo ilegible, sin campo.</p>
     *
     * <p>El detalle de la excepción no se devuelve ni se registra: describe la estructura
     * interna del modelo y, en un cuerpo de registro, puede incluir el valor recibido.</p>
     *
     * @param error excepción de deserialización
     * @return {@code 422 Unprocessable Entity}
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail cuerpoIlegible(HttpMessageNotReadableException error) {
        if (CAMPO_PRONOMBRE.equals(campoIlegible(error))) {
            return invalidDomainField(new InvalidPronounException());
        }
        // Solo el tipo de fallo. El mensaje de Jackson suele citar el fragmento de JSON que
        // no pudo leer, y en el registro ese fragmento puede ser la contraseña.
        return rechazo(HttpStatus.UNPROCESSABLE_ENTITY, TITULO_VALIDACION, "Revisa el formato de los datos enviados.",
                ErrorCode.REQUEST_BODY_INVALID_FORMAT, "causa=" + claseMasEspecifica(error));
    }

    /**
     * Petición a la que le falta un encabezado o un parámetro exigido por la ruta.
     *
     * <p>Es un error del cliente, no del servidor: en las rutas autenticadas el Gateway
     * siempre emite {@code X-User-Id}, así que una petición sin él no viene del camino
     * previsto. Sin este manejador terminaría como {@code 500} y parecería una falla de
     * la plataforma.</p>
     *
     * @param error excepción de enlace de la petición
     * @return {@code 400 Bad Request}
     */
    @ExceptionHandler(ServletRequestBindingException.class)
    ProblemDetail peticionIncompleta(ServletRequestBindingException error) {
        // El nombre del encabezado sale del contrato de la ruta; su valor nunca se registra.
        String faltante = error instanceof MissingRequestHeaderException encabezado
                ? "encabezado=" + encabezado.getHeaderName()
                : "causa=" + error.getClass().getSimpleName();
        return rechazo(HttpStatus.BAD_REQUEST, "Petición incompleta",
                "La petición no incluye los datos que exige esta ruta", ErrorCode.IDENTITY_REQUIRED, faltante);
    }

    /**
     * Identidad del Gateway que llegó en blanco o más larga que un identificador de Firebase.
     *
     * <p>Responde igual que el encabezado ausente: para quien llama es la misma falta. El valor del
     * encabezado nunca se registra.</p>
     *
     * @param error excepción de negocio
     * @return {@code 400 Bad Request} con el código {@code IDENTITY_REQUIRED}
     */
    @ExceptionHandler(IdentityRequiredException.class)
    ProblemDetail invalidIdentity(IdentityRequiredException error) {
        return rechazo(HttpStatus.BAD_REQUEST, "Petición incompleta", error.getMessage(), error.getErrorCode(),
                "causa=identidad-invalida");
    }

    /**
     * Dependencia externa (Firebase) que no respondió o falló de su lado.
     *
     * <p>Es un fallo del servicio y no de la persona, así que se registra en {@code ERROR}
     * con la causa técnica; la respuesta solo dice que puede reintentar.</p>
     *
     * @param error excepción con la causa técnica
     * @return {@code 503 Service Unavailable} con el código {@code DEPENDENCY_UNAVAILABLE}
     */
    @ExceptionHandler(DependencyUnavailableException.class)
    ProblemDetail dependenciaNoDisponible(DependencyUnavailableException error) {
        ProblemDetail problema = problema(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible", DETALLE_INTERNO,
                error.getErrorCode());
        logger.error("Dependencia no disponible [code={}, requestId={}]", error.getErrorCode(),
                problema.getProperties().get("requestId"), error);
        return problema;
    }

    /**
     * Violación de una restricción de la base de datos.
     *
     * <p>Ninguna restricción de la tabla de cuentas es alcanzable por una entrada de la
     * persona, porque la validación del contrato y del dominio actúa antes: una violación es
     * un defecto y responde como cualquier fallo imprevisto. El mensaje de la base incluye el
     * valor de la columna (un dato personal), así que ni se devuelve ni se registra: el log
     * lleva solo el nombre de la restricción y el SQLState, el código estándar de cinco
     * caracteres que dice la causa (por ejemplo {@code 23502}, un nulo en una columna
     * obligatoria) cuando la violación no tiene nombre de restricción.</p>
     *
     * @param error excepción traducida por Spring
     * @return {@code 500 Internal Server Error} con un mensaje genérico
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridadDeDatos(DataIntegrityViolationException error) {
        ProblemDetail problema = problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", DETALLE_INTERNO,
                ErrorCode.INTERNAL_ERROR);
        logger.error("Violación de restricción de la base [constraint={}, sqlState={}, code={}, requestId={}, causa={}]",
                restriccionDe(error), sqlStateOf(error), ErrorCode.INTERNAL_ERROR,
                problema.getProperties().get("requestId"), claseMasEspecifica(error));
        return problema;
    }

    /**
     * Ruta que no existe.
     *
     * @param error excepción de Spring cuando ningún controlador ni recurso atiende la ruta
     * @return {@code 404 Not Found} con el código {@code ROUTE_NOT_FOUND}
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail rutaInexistente(NoResourceFoundException error) {
        return rechazo(HttpStatus.NOT_FOUND, "Ruta no encontrada", "No existe la ruta solicitada.",
                ErrorCode.ROUTE_NOT_FOUND, "metodo=" + error.getHttpMethod());
    }

    /**
     * Método HTTP que la ruta no admite.
     *
     * <p>El encabezado {@code Allow} con los métodos admitidos se agrega a la respuesta, como
     * pide HTTP para un 405.</p>
     *
     * @param error excepción con los métodos que sí admite la ruta
     * @return {@code 405 Method Not Allowed} con el código {@code METHOD_NOT_ALLOWED}
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> metodoNoPermitido(HttpRequestMethodNotSupportedException error) {
        ProblemDetail problema = rechazo(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido",
                "Método no permitido.", ErrorCode.METHOD_NOT_ALLOWED, "metodo=" + error.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(error.getHeaders()).body(problema);
    }

    /**
     * Tipo de contenido que la ruta no admite.
     *
     * @param error excepción con el tipo recibido y los admitidos
     * @return {@code 415 Unsupported Media Type} con el código {@code MEDIA_TYPE_NOT_ALLOWED}
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> tipoDeContenidoNoAdmitido(HttpMediaTypeNotSupportedException error) {
        ProblemDetail problema = rechazo(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido",
                "Tipo de contenido no admitido.", ErrorCode.MEDIA_TYPE_NOT_ALLOWED, "");
        // Accept con los tipos admitidos, como sugiere HTTP para un 415.
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).headers(error.getHeaders()).body(problema);
    }

    /**
     * Tipo de respuesta que quien llama no acepta.
     *
     * <p>Es un error del cliente: su {@code Accept} no admite ninguno de los tipos que la ruta
     * produce. El cuerpo sale igual en {@code application/problem+json}, como todo error del
     * servicio.</p>
     *
     * @param error excepción con los tipos que la ruta sí produce
     * @return {@code 406 Not Acceptable} con el código {@code MEDIA_TYPE_NOT_ACCEPTABLE}
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ProblemDetail responseTypeNotAcceptable(HttpMediaTypeNotAcceptableException error) {
        return rechazo(HttpStatus.NOT_ACCEPTABLE, "Tipo de respuesta no admitido", "Tipo de respuesta no admitido.",
                ErrorCode.MEDIA_TYPE_NOT_ACCEPTABLE);
    }

    /**
     * Cualquier otro fallo.
     *
     * @param error excepción no prevista
     * @return {@code 500 Internal Server Error} con un mensaje genérico
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail falloInterno(Exception error) {
        ProblemDetail problema = problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", DETALLE_INTERNO,
                ErrorCode.INTERNAL_ERROR);
        // Con la traza: es la única forma de diagnosticar un fallo que nadie previó. El
        // requestId de la respuesta lleva a esta línea.
        logger.error("Fallo no controlado [code={}, requestId={}]", ErrorCode.INTERNAL_ERROR,
                problema.getProperties().get("requestId"), error);
        return problema;
    }

    /** Respuesta 422 con lista de campos, código {@code VALIDATION_FAILED} y {@code detail} fijo. */
    private ProblemDetail validacion(List<CampoRechazado> campos) {
        // Solo nombres de campo y códigos, que salen del contrato: nunca el valor ni el mensaje.
        String resumen = campos.stream()
                .map(campo -> campo.field() + "=" + campo.code())
                .collect(Collectors.joining(",", "campos=[", "]"));
        ProblemDetail problema = rechazo(HttpStatus.UNPROCESSABLE_ENTITY, TITULO_VALIDACION, DETALLE_VALIDACION,
                ErrorCode.VALIDATION_FAILED, resumen);
        problema.setProperty(ERRORS, campos);
        return problema;
    }

    /** Rechazo de la petición (4xx), registrado en {@code WARN} sin traza. */
    private ProblemDetail rechazo(HttpStatus estado, String titulo, String detalle, ErrorCode codigo) {
        return rechazo(estado, titulo, detalle, codigo, "");
    }

    private ProblemDetail rechazo(HttpStatus estado, String titulo, String detalle, ErrorCode codigo,
            String contexto) {
        ProblemDetail problema = problema(estado, titulo, detalle, codigo);
        logger.warn("Petición rechazada [status={}, code={}, requestId={}] {}", estado.value(), codigo,
                problema.getProperties().get("requestId"), contexto);
        return problema;
    }

    private ProblemDetail problema(HttpStatus estado, String titulo, String detalle, ErrorCode codigo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setProperty("code", codigo.name());
        problema.setProperty("requestId", resolverRequestId());
        return problema;
    }

    private CampoRechazado campo(String nombre, ErrorCode codigo, String mensaje) {
        return new CampoRechazado(nombre, codigo.name(), mensaje == null ? "Valor no válido" : mensaje);
    }

    /**
     * Código de una restricción del contrato.
     *
     * <p>Una regla de forma del dominio ({@code @DomainRule}) trae su código como carga dinámica:
     * es el de la excepción del dominio. Las demás restricciones lo toman de la tabla.</p>
     *
     * <p>Si la restricción no está en la tabla, el elemento lleva {@code REQUEST_INVALID_VALUE}
     * y se registra un {@code ERROR}: nunca {@code VALIDATION_FAILED}, que es el código de la
     * operación entera. Una prueba impide que esto ocurra; la rama existe para que un
     * descuido no pase en silencio.</p>
     */
    private ErrorCode codigoDe(FieldError fallo) {
        if (fallo.contains(ConstraintViolation.class)
                && fallo.unwrap(ConstraintViolation.class) instanceof HibernateConstraintViolation<?> violation
                && violation.getDynamicPayload(ErrorCode.class) != null) {
            return violation.getDynamicPayload(ErrorCode.class);
        }
        String clave = fallo.getField() + "." + fallo.getCode();
        ErrorCode codigo = FIELD_ERROR_CODES.get(clave);
        if (codigo == null) {
            logger.error("Restricción del contrato sin código de error: {}", clave);
            return ErrorCode.REQUEST_INVALID_VALUE;
        }
        return codigo;
    }

    /**
     * Identificador de trazabilidad de la petición: el del Gateway si cumple la forma
     * admitida; si no, uno nuevo. Se devuelve también en el encabezado de la respuesta.
     *
     * <p>Además fija la codificación de la respuesta en UTF-8. Al resolver una excepción,
     * Spring limpia el tipo de contenido de la respuesta y el contenedor de servlets borra
     * con él la codificación que había puesto el filtro de codificación; sin esto, el
     * {@code Content-Type} del error saldría sin {@code charset} (ASVS 4.1.1).</p>
     */
    private String resolverRequestId() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (!(atributos instanceof ServletRequestAttributes servlet)) {
            return UUID.randomUUID().toString();
        }
        String recibido = servlet.getRequest().getHeader(REQUEST_ID_HEADER);
        String requestId = recibido != null && REQUEST_ID_VALIDO.matcher(recibido).matches()
                ? recibido
                : UUID.randomUUID().toString();
        if (servlet.getResponse() != null) {
            servlet.getResponse().setHeader(REQUEST_ID_HEADER, requestId);
            servlet.getResponse().setCharacterEncoding(StandardCharsets.UTF_8.name());
        }
        return requestId;
    }

    /**
     * Campo del contrato cuyo valor tiene un tipo o un valor que no corresponde, según la ruta
     * que informa Jackson.
     *
     * <p>Solo cuenta un JSON bien formado con un valor que no encaja: un JSON mal formado
     * también puede traer la ruta del campo donde se cortó, pero es un cuerpo ilegible, no una
     * opción equivocada.</p>
     *
     * @return nombre del último campo de la ruta, o {@code null} si el error no es de ese tipo
     */
    private static String campoIlegible(HttpMessageNotReadableException error) {
        if (!(error.getCause() instanceof MismatchedInputException jackson) || jackson.getPath().isEmpty()) {
            return null;
        }
        return jackson.getPath().getLast().getPropertyName();
    }

    /** Nombre de la restricción violada, buscado en la cadena de causas; nunca el mensaje. */
    private static String restriccionDe(Throwable error) {
        Throwable actual = error;
        for (int nivel = 0; actual != null && nivel < MAX_CAUSAS; nivel++, actual = actual.getCause()) {
            if (actual instanceof ConstraintViolationException hibernate && hibernate.getConstraintName() != null) {
                return hibernate.getConstraintName();
            }
        }
        return "desconocida";
    }

    /** SQLState de la primera excepción de JDBC en la cadena de causas; nunca su mensaje. */
    private static String sqlStateOf(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSAS; depth++, current = current.getCause()) {
            if (current instanceof SQLException jdbc && jdbc.getSQLState() != null) {
                return jdbc.getSQLState();
            }
        }
        return "desconocido";
    }

    /** Nombre simple de la causa más profunda: dice qué falló sin citar el valor recibido. */
    private static String claseMasEspecifica(Throwable error) {
        Throwable actual = error;
        for (int nivel = 0; actual.getCause() != null && nivel < MAX_CAUSAS; nivel++) {
            actual = actual.getCause();
        }
        return actual.getClass().getSimpleName();
    }
}
