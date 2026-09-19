package tech.cameia.cuentas.presentation.advice;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.exception.EmailNotVerifiedException;
import tech.cameia.cuentas.domain.exception.InvalidBirthDateException;
import tech.cameia.cuentas.domain.exception.WeakPasswordException;

/**
 * Traduce los fallos a respuestas {@code application/problem+json} (RFC 7807).
 *
 * <p>Los mensajes de las excepciones de negocio llegan al usuario tal cual, porque le
 * dicen qué corregir. Los fallos técnicos, en cambio, se registran completos en el log y
 * al cliente solo le llega un texto genérico: el detalle de una excepción interna describe
 * la infraestructura y no ayuda a quien está llenando un formulario.</p>
 *
 * <p>Ninguna respuesta repite el cuerpo de la petición, donde viaja la contraseña.</p>
 */
@RestControllerAdvice
class BusinessExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(BusinessExceptionHandler.class);

    /** Nombre de la propiedad que lista los campos rechazados. */
    private static final String ERRORS = "errors";

    /**
     * Correo ya registrado.
     *
     * @param error excepción de negocio
     * @return {@code 409 Conflict} con el mensaje del criterio de aceptación
     */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail correoRepetido(EmailAlreadyRegisteredException error) {
        return problema(HttpStatus.CONFLICT, "Correo ya registrado", error.getMessage());
    }

    /**
     * Fecha de nacimiento que no permite registrarse.
     *
     * @param error excepción con el motivo del rechazo
     * @return {@code 422 Unprocessable Entity} señalando el campo {@code birthDate}
     */
    @ExceptionHandler(InvalidBirthDateException.class)
    ProblemDetail fechaInvalida(InvalidBirthDateException error) {
        ProblemDetail problema = problema(HttpStatus.UNPROCESSABLE_ENTITY,
                "Fecha de nacimiento no válida", error.getMessage());
        problema.setProperty(ERRORS, List.of(campo("birthDate", error.getMessage())));
        return problema;
    }

    /**
     * Contraseña que no cumple la política.
     *
     * @param error excepción con la regla incumplida
     * @return {@code 422 Unprocessable Entity} señalando el campo {@code password}
     */
    @ExceptionHandler(WeakPasswordException.class)
    ProblemDetail contraseniaDebil(WeakPasswordException error) {
        ProblemDetail problema = problema(HttpStatus.UNPROCESSABLE_ENTITY,
                "Contraseña no válida", error.getMessage());
        problema.setProperty(ERRORS, List.of(campo("password", error.getMessage())));
        return problema;
    }

    /**
     * Correo aún sin verificar.
     *
     * @param error excepción de negocio
     * @return {@code 403 Forbidden}
     */
    @ExceptionHandler(EmailNotVerifiedException.class)
    ProblemDetail correoSinVerificar(EmailNotVerifiedException error) {
        return problema(HttpStatus.FORBIDDEN, "Correo sin verificar", error.getMessage());
    }

    /**
     * Usuario sin cuenta local.
     *
     * @param error excepción de negocio
     * @return {@code 404 Not Found}
     */
    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail cuentaInexistente(AccountNotFoundException error) {
        return problema(HttpStatus.NOT_FOUND, "Cuenta no encontrada", error.getMessage());
    }

    /**
     * Campos que incumplen las validaciones del contrato.
     *
     * @param error excepción con la lista de campos rechazados
     * @return {@code 422 Unprocessable Entity} con un elemento por campo
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail camposInvalidos(MethodArgumentNotValidException error) {
        List<Map<String, String>> campos = error.getBindingResult().getFieldErrors().stream()
                .map(fallo -> campo(fallo.getField(), fallo.getDefaultMessage()))
                .toList();

        ProblemDetail problema = problema(HttpStatus.UNPROCESSABLE_ENTITY, "Datos no válidos",
                "Revisa los campos marcados");
        problema.setProperty(ERRORS, campos);
        return problema;
    }

    /**
     * Cuerpo que no se puede interpretar, por ejemplo una fecha con otro formato o un
     * pronombre fuera de la lista.
     *
     * <p>El detalle de la excepción no se devuelve: describe la estructura interna del
     * modelo y, en un cuerpo de registro, puede incluir el valor recibido.</p>
     *
     * @param error excepción de deserialización
     * @return {@code 422 Unprocessable Entity} con una indicación del formato esperado
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail cuerpoIlegible(HttpMessageNotReadableException error) {
        // Solo el tipo de fallo. El mensaje de Jackson suele citar el fragmento de JSON que
        // no pudo leer, y en el registro ese fragmento puede ser la contraseña.
        logger.warn("Cuerpo de la petición ilegible: {}", error.getClass().getSimpleName());
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Datos no válidos",
                "Revisa el formato de los datos enviados. La fecha de nacimiento usa el formato DD/MM/AAAA");
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
        logger.warn("Petición sin los datos que exige la ruta: {}", error.getMessage());
        return problema(HttpStatus.BAD_REQUEST, "Petición incompleta",
                "La petición no incluye los datos que exige esta ruta");
    }

    /**
     * Valores que el dominio rechaza al construirse, como un correo mal formado o un
     * celular sin indicativo.
     *
     * @param error excepción con el mensaje del objeto de valor
     * @return {@code 422 Unprocessable Entity}
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail valorInvalido(IllegalArgumentException error) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Datos no válidos", error.getMessage());
    }

    /**
     * Cualquier otro fallo.
     *
     * @param error excepción no prevista
     * @return {@code 500 Internal Server Error} con un mensaje genérico
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail falloInterno(Exception error) {
        logger.error("Fallo no controlado atendiendo la petición", error);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "No pudimos completar la operación. Inténtalo de nuevo en unos minutos");
    }

    private ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }

    private Map<String, String> campo(String nombre, String mensaje) {
        return Map.of("field", nombre, "message", mensaje == null ? "Valor no válido" : mensaje);
    }
}
