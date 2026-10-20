package tech.cameia.cuentas.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;

import com.jayway.jsonpath.JsonPath;

import jakarta.validation.Constraint;

import org.apache.tomcat.util.http.InvalidParameterException;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import tech.cameia.cuentas.domain.exception.AccountConflictException;
import tech.cameia.cuentas.domain.exception.AccountDisabledException;
import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.presentation.dto.DomainRule;
import tech.cameia.cuentas.presentation.dto.RegisterUserRequest;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Prueba el formato común de las respuestas de error: el código estable, el identificador
 * de la petición, el texto genérico de los fallos técnicos y lo que queda en el log.
 *
 * <p>Los fallos se provocan con un controlador propio de la prueba, para comprobar el
 * manejador con excepciones que ningún controlador real lanza a propósito.</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class BusinessExceptionHandlerTest {

    private static final String UUID_V4 = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    private final BusinessExceptionHandler manejador = new BusinessExceptionHandler();

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ControladorQueFalla())
            .setControllerAdvice(ProblemDetailTestSupport.manejadorDeErrores())
            .build();

    @Test
    void todaRestriccionDelContratoTieneCodigo() throws NoSuchFieldException {
        Set<String> restricciones = new HashSet<>();
        for (RecordComponent componente : RegisterUserRequest.class.getRecordComponents()) {
            // Las restricciones de un record se propagan al campo privado del componente.
            for (Annotation anotacion : RegisterUserRequest.class.getDeclaredField(componente.getName())
                    .getAnnotations()) {
                // Una regla de forma del dominio trae su código en la excepción del dominio.
                if (anotacion.annotationType().isAnnotationPresent(Constraint.class)
                        && !(anotacion instanceof DomainRule)) {
                    restricciones.add(componente.getName() + "." + anotacion.annotationType().getSimpleName());
                }
            }
        }

        // En las dos direcciones: ninguna restricción sin código y ningún código huérfano.
        assertThat(restricciones).isNotEmpty();
        assertThat(BusinessExceptionHandler.FIELD_ERROR_CODES.keySet()).containsExactlyInAnyOrderElementsOf(restricciones);
    }

    @Test
    void ningunCodigoDeCampoEsElDeLaOperacionEntera() {
        assertThat(BusinessExceptionHandler.FIELD_ERROR_CODES.values())
                .doesNotContain(ErrorCode.VALIDATION_FAILED, ErrorCode.INTERNAL_ERROR, ErrorCode.REQUEST_INVALID_VALUE);
    }

    @Test
    void unFalloTecnicoDevuelveElMensajeGenericoSinDetalle() throws Exception {
        mockMvc.perform(get("/falla"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsStringIgnoringCase("charset=UTF-8")))
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error. Inténtalo de nuevo."))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("secreto")
                        .doesNotContain("IllegalStateException")
                        .doesNotContain("tech.cameia"));
    }

    @Test
    void unFalloTecnicoDejaEnElLogElMismoRequestIdDeLaRespuestaConSuTraza(CapturedOutput salida) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/falla")).andReturn();
        String requestId = JsonPath.read(resultado.getResponse().getContentAsString(), "$.requestId");

        assertThat(salida.getOut())
                .contains("ERROR")
                .contains("code=INTERNAL_ERROR, requestId=" + requestId)
                .contains("java.lang.IllegalStateException");
    }

    @Test
    void unaViolacionDeRestriccionResponde500SinElValorYRegistraSoloSuNombre(CapturedOutput salida)
            throws Exception {
        mockMvc.perform(get("/restriccion").header("X-Request-Id", "restriccion-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error. Inténtalo de nuevo."))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("Ana Pérez")
                        .doesNotContain("ck_cuenta_nombre"));

        assertThat(salida.getOut())
                .contains("constraint=ck_cuenta_nombre, sqlState=")
                .contains("code=INTERNAL_ERROR, requestId=restriccion-1")
                .doesNotContain("Ana Pérez");
    }

    @Test
    void unaViolacionSinNombreDeRestriccionSeRegistraComoDesconocida(CapturedOutput salida) throws Exception {
        mockMvc.perform(get("/restriccion-sin-nombre"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(salida.getOut()).contains("constraint=desconocida").doesNotContain("Ana Pérez");
    }

    @Test
    void unaRestriccionSinCodigoUsaElRespaldoYRegistraUnError(CapturedOutput salida) throws Exception {
        BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(new Object(), "request");
        resultado.addError(new FieldError("request", "telefono", null, false,
                new String[] {"Pattern.request.telefono", "Pattern"}, null, null));
        MethodArgumentNotValidException error = new MethodArgumentNotValidException(
                new MethodParameter(Object.class.getMethod("equals", Object.class), 0), resultado);

        ProblemDetail problema = manejador.camposInvalidos(error);

        assertThat(problema.getProperties()).containsEntry("code", "VALIDATION_FAILED");
        assertThat(problema.getProperties().get("errors")).asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsExactly(new BusinessExceptionHandler.CampoRechazado("telefono", "REQUEST_INVALID_VALUE",
                        "Valor no válido"));
        assertThat(salida.getOut()).contains("ERROR").contains("Restricción del contrato sin código de error: telefono.Pattern");
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    @DisplayName("Un parámetro mal codificado en un formulario responde 415")
    void invalidParameter_shouldReturn415_whenFormBody(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/users");
        request.setContentType("application/x-www-form-urlencoded");

        ProblemDetail problema = manejador.falloInterno(
                new InvalidParameterException("x"), request);

        assertThat(problema.getStatus()).isEqualTo(415);
        assertThat(problema.getProperties()).containsEntry("code", "MEDIA_TYPE_NOT_ALLOWED");
        assertThat(problema.getProperties()).containsKey("requestId");
    }

    @Test
    @DisplayName("El tipo de contenido del formulario se reconoce con mayúsculas y charset")
    void invalidParameter_shouldReturn415_whenFormContentTypeHasCharsetAndUppercase() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        request.setContentType("Application/X-WWW-Form-Urlencoded; charset=UTF-8");

        ProblemDetail problema = manejador.falloInterno(
                new InvalidParameterException("x"), request);

        assertThat(problema.getStatus()).isEqualTo(415);
    }

    @Test
    @DisplayName("El mismo rechazo de Tomcat fuera de un formulario sigue siendo un fallo interno")
    void invalidParameter_shouldReturn500_whenNotFormBody() {
        ProblemDetail problema = manejador.falloInterno(
                new InvalidParameterException("x"),
                new MockHttpServletRequest("GET", "/api/v1/users"));

        assertThat(problema.getStatus()).isEqualTo(500);
        assertThat(problema.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Una IllegalStateException cualquiera responde 500")
    void illegalState_shouldReturn500_whenNotInvalidParameter() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        request.setContentType("application/x-www-form-urlencoded");

        ProblemDetail problema = manejador.falloInterno(new IllegalStateException("x"), request);

        assertThat(problema.getStatus()).isEqualTo(500);
        assertThat(problema.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Una base de datos no disponible responde 503 DATABASE_UNAVAILABLE sin el mensaje de la librería")
    void databaseUnavailable_shouldReturn503_whenAvailabilityProblem() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");

        ProblemDetail problema = manejador.falloInterno(
                new DataAccessResourceFailureException("host=10.0.0.5 password=x"), request);

        assertThat(problema.getStatus()).isEqualTo(503);
        assertThat(problema.getTitle()).isEqualTo("Servicio no disponible");
        assertThat(problema.getDetail()).isEqualTo("Ocurrió un error. Inténtalo de nuevo.");
        assertThat(problema.getProperties()).containsEntry("code", "DATABASE_UNAVAILABLE")
                .containsKey("requestId");
        assertThat(problema.toString()).doesNotContain("10.0.0.5").doesNotContain("password");
    }

    @Test
    @DisplayName("Una base de datos no disponible se registra una vez en ERROR con código, requestId y SQLState")
    void databaseUnavailable_shouldLogOnce_whenAvailabilityProblem(CapturedOutput salida) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");

        ProblemDetail problema = manejador.falloInterno(new DataAccessResourceFailureException("x",
                new SQLException("FATAL", "57P01")), request);

        String requestId = (String) problema.getProperties().get("requestId");
        assertThat(salida.getOut())
                .contains("ERROR")
                .contains("code=DATABASE_UNAVAILABLE, requestId=" + requestId + ", sqlState=57P01");
        assertThat(salida.getOut().split("Base de datos no disponible", -1)).hasSize(2);
    }

    @Test
    @DisplayName("Una violación de restricción y un estado ilegal siguen respondiendo 500 INTERNAL_ERROR")
    void databaseUnavailable_shouldKeepOtherCodes_whenNotAvailabilityProblem() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");

        ProblemDetail integridad = manejador.integridadDeDatos(
                new DataIntegrityViolationException("x", new SQLException("x", "23502")));
        ProblemDetail ilegal = manejador.falloInterno(new IllegalStateException("x"), request);

        assertThat(integridad.getStatus()).isEqualTo(500);
        assertThat(integridad.getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(ilegal.getStatus()).isEqualTo(500);
        assertThat(ilegal.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Firebase caído sigue respondiendo 503 DEPENDENCY_UNAVAILABLE")
    void databaseUnavailable_shouldKeepDependencyCode_whenFirebaseFails() {
        ProblemDetail problema = manejador.dependenciaNoDisponible(
                new DependencyUnavailableException(new RuntimeException()));

        assertThat(problema.getStatus()).isEqualTo(503);
        assertThat(problema.getProperties()).containsEntry("code", "DEPENDENCY_UNAVAILABLE");
    }

    @Test
    @DisplayName("El mensaje de la excepción de Tomcat no se devuelve")
    void invalidParameter_shouldNotEchoMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        request.setContentType("application/x-www-form-urlencoded");

        ProblemDetail problema = manejador.falloInterno(
                new InvalidParameterException("Parameter [a] with value [%zz]"), request);

        assertThat(problema.toString()).doesNotContain("%zz");
        assertThat(problema.getDetail()).doesNotContain("zz");
    }

    @Test
    @DisplayName("Un multipart roto responde 415 con su código propio")
    void multipart_shouldReturn415_whenMultipartContentType() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        request.setContentType("multipart/form-data");

        ProblemDetail problema = manejador.multipartMalFormado(
                new MultipartException("x"), request);

        assertThat(problema.getStatus()).isEqualTo(415);
        assertThat(problema.getProperties()).containsEntry("code", "REQUEST_MULTIPART_INVALID_FORMAT");
    }

    @Test
    @DisplayName("Un multipart que pasa el tope de tamaño responde 413 con su código propio, en WARN y sin citar el límite")
    void multipart_shouldReturn413_whenSizeLimitIsExceeded(CapturedOutput output) {
        ProblemDetail problema = manejador.cuerpoDemasiadoGrande(new MaxUploadSizeExceededException(1_048_576L));

        assertThat(problema.getStatus()).isEqualTo(413);
        assertThat(problema.getProperties()).containsEntry("code", "REQUEST_BODY_TOO_LARGE");
        assertThat(problema.getDetail()).isEqualTo("Cuerpo de la petición demasiado grande.");
        assertThat(problema.toString()).doesNotContain("1048576");
        assertThat(output.getAll()).contains("WARN", "status=413", "code=REQUEST_BODY_TOO_LARGE")
                .doesNotContain("ERROR").doesNotContain("1048576");
    }

    @Test
    @DisplayName("Entre el tope de tamaño y el multipart roto, Spring elige el manejador más específico")
    void multipart_shouldPickSizeHandler_whenSizeExceptionIsThrown() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FallaDeMultipart()).setControllerAdvice(manejador).build();

        mvc.perform(get("/tamano").contentType("multipart/form-data; boundary=x"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_TOO_LARGE"));
        mvc.perform(get("/lectura").contentType("multipart/form-data; boundary=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("REQUEST_MULTIPART_INVALID_FORMAT"));
    }

    /** Controlador de la prueba: lanza el fallo de tamaño y el de lectura de un multipart. */
    @RestController
    static class FallaDeMultipart {
        @GetMapping("/tamano")
        void tamano() {
            throw new MaxUploadSizeExceededException(1_048_576L);
        }

        @GetMapping("/lectura")
        void lectura() {
            throw new MultipartException("no se pudo leer");
        }
    }

    @Test
    @DisplayName("Un fallo de multipart en una petición que no es multipart responde 500")
    void multipart_shouldReturn500_whenNotMultipartContentType() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        request.setContentType("application/json");

        ProblemDetail problema = manejador.multipartMalFormado(
                new MultipartException("x"), request);

        assertThat(problema.getStatus()).isEqualTo(500);
        assertThat(problema.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Un multipart sin tipo de contenido responde 500")
    void multipart_shouldReturn500_whenContentTypeIsMissing() {
        ProblemDetail problema = manejador.multipartMalFormado(
                new MultipartException("x"), new MockHttpServletRequest());

        assertThat(problema.getStatus()).isEqualTo(500);
    }

    @Test
    void fueraDeUnaPeticionElRequestIdSeGeneraIgual() {
        RequestContextHolder.resetRequestAttributes();

        ProblemDetail problema = manejador.falloInterno(new IllegalStateException("x"), new MockHttpServletRequest());

        assertThat((String) problema.getProperties().get("requestId")).matches(UUID_V4);
    }

    @Test
    @DisplayName("El error fija application/problem+json con charset en la respuesta, para que un Accept ilegible no lo deje sin cuerpo")
    void requestId_shouldSetProblemJsonContentType_whenResponseIsAvailable() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion, respuesta));
        try {
            manejador.falloInterno(new IllegalStateException("x"), peticion);

            assertThat(respuesta.getContentType()).isEqualToIgnoringCase("application/problem+json;charset=UTF-8");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void sinRespuestaAsociadaElRequestIdSeTomaDeLaPeticionSinFallar() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader("X-Request-Id", "solo-peticion");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
        try {
            ProblemDetail problema = manejador.falloInterno(new IllegalStateException("x"), new MockHttpServletRequest());

            assertThat(problema.getProperties()).containsEntry("requestId", "solo-peticion");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("Una cuenta deshabilitada responde 403 con su código, título y mensaje")
    void cuentaDeshabilitada_shouldReturn403_whenAccountIsDisabled() {
        ProblemDetail problema = manejador.cuentaDeshabilitada(new AccountDisabledException());

        assertThat(problema.getStatus()).isEqualTo(403);
        assertThat(problema.getTitle()).isEqualTo("Cuenta deshabilitada");
        assertThat(problema.getDetail()).isEqualTo("Esta cuenta está deshabilitada.");
        assertThat(problema.getProperties()).containsEntry("code", "ACCOUNT_DISABLED").containsKey("requestId");
    }

    @Test
    @DisplayName("Un conflicto de cuenta responde 409 con su código, título y mensaje")
    void conflictoDeCuenta_shouldReturn409_whenAccountChangedMeanwhile() {
        ProblemDetail problema = manejador.conflictoDeCuenta(new AccountConflictException());

        assertThat(problema.getStatus()).isEqualTo(409);
        assertThat(problema.getTitle()).isEqualTo("Conflicto de cuenta");
        assertThat(problema.getDetail()).isEqualTo("La cuenta se está actualizando. Inténtalo de nuevo.");
        assertThat(problema.getProperties()).containsEntry("code", "ACCOUNT_CONFLICT").containsKey("requestId");
    }

    @Test
    void unaPeticionIncompletaQueNoEsPorUnEncabezadoRegistraSoloLaClase(CapturedOutput salida) {
        ProblemDetail problema = manejador.peticionIncompleta(new ServletRequestBindingException("valor secreto"));

        assertThat(problema.getStatus()).isEqualTo(400);
        assertThat(problema.getProperties()).containsEntry("code", "IDENTITY_REQUIRED");
        assertThat(salida.getOut()).contains("causa=ServletRequestBindingException").doesNotContain("valor secreto");
    }

    @Test
    void unCuerpoIlegibleRegistraLaClaseDeLaCausaMasProfundaSinSuMensaje(CapturedOutput salida) {
        HttpMessageNotReadableException error = new HttpMessageNotReadableException("lectura",
                new IllegalStateException("frase secreta larga", new java.io.EOFException("ana@cameia.tech")),
                new MockHttpInputMessage(new byte[0]));

        ProblemDetail problema = manejador.cuerpoIlegible(error);

        assertThat(problema.getProperties()).containsEntry("code", "REQUEST_BODY_INVALID_FORMAT");
        assertThat(salida.getOut()).contains("causa=EOFException")
                .doesNotContain("frase secreta larga").doesNotContain("ana@cameia.tech");
    }

    @Test
    void unaCadenaDeCausasCiclicaNoDejaElHiloEnUnBucle(CapturedOutput salida) {
        IllegalStateException primera = new IllegalStateException("a");
        IllegalStateException segunda = new IllegalStateException("b", primera);
        primera.initCause(segunda);

        ProblemDetail problema = manejador.integridadDeDatos(new DataIntegrityViolationException("x", primera));

        assertThat(problema.getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(salida.getOut()).contains("constraint=desconocida");
    }

    @Test
    void unaRestriccionDeHibernateSinNombreSeRegistraComoDesconocida(CapturedOutput salida) {
        manejador.integridadDeDatos(new DataIntegrityViolationException("x",
                new ConstraintViolationException("sin nombre", new SQLException("Ana Pérez"), null)));

        assertThat(salida.getOut()).contains("constraint=desconocida").doesNotContain("Ana Pérez");
    }

    @Test
    @DisplayName("Una violación sin nombre registra el SQLState para diagnosticarla sin el mensaje")
    void dataIntegrityViolation_shouldLogSqlStateWithoutMessage_whenConstraintHasNoName(CapturedOutput output) {
        manejador.integridadDeDatos(new DataIntegrityViolationException("x",
                new ConstraintViolationException("sin nombre", new SQLException("Ana Pérez", "23502"), null)));

        assertThat(output.getOut()).contains("constraint=desconocida, sqlState=23502").doesNotContain("Ana Pérez");
    }

    @Test
    @DisplayName("Sin excepción de JDBC el SQLState es desconocido")
    void dataIntegrityViolation_shouldLogUnknownSqlState_whenThereIsNoJdbcException(CapturedOutput output) {
        manejador.integridadDeDatos(new DataIntegrityViolationException("x"));

        assertThat(output.getOut()).contains("sqlState=desconocido");
    }

    @Test
    void unCorreoSinFormaSaleComoErrorDeSuCampoConSuCodigo() throws Exception {
        mockMvc.perform(get("/correo-del-dominio"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Revisa los campos marcados."))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("EMAIL_INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[0].message").value("Ingresa un correo electrónico válido."));
    }

    @Test
    void unaIllegalArgumentExceptionInesperadaEsUnFalloInternoSinSuMensaje(CapturedOutput salida)
            throws Exception {
        // Ya no hay respaldo que la convierta en un 422: cada dato tiene su excepción de negocio,
        // y una que llegue hasta aquí es un defecto del servicio, no un error de la persona.
        mockMvc.perform(get("/valor-de-libreria").header("X-Request-Id", "libreria-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error. Inténtalo de nuevo."))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("No enum constant"));

        assertThat(salida.getOut()).contains("ERROR").contains("code=INTERNAL_ERROR, requestId=libreria-1");
    }

    @Test
    void unRequestIdValidoSeDevuelveIgualEnElCuerpoYEnElEncabezado() throws Exception {
        mockMvc.perform(get("/falla").header("X-Request-Id", "abc-123"))
                .andExpect(jsonPath("$.requestId").value("abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"));
    }

    @Test
    void unRequestIdDeExactamente64CaracteresPermitidosSeConserva() throws Exception {
        String limite = "a.b_c-".repeat(10) + "1234";

        mockMvc.perform(get("/falla").header("X-Request-Id", limite))
                .andExpect(jsonPath("$.requestId").value(limite))
                .andExpect(header().string("X-Request-Id", limite));
    }

    @Test
    void sinRequestIdSeGeneraUnUuidV4QueTambienVaEnElEncabezado() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/falla")).andReturn();
        String requestId = JsonPath.read(resultado.getResponse().getContentAsString(), "$.requestId");

        assertThat(requestId).matches(UUID_V4);
        assertThat(resultado.getResponse().getHeader("X-Request-Id")).isEqualTo(requestId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc 123", "abc\u0000", "línea", "a/b", "", "x;rm"})
    void unRequestIdQueNoCumpleLaFormaSeReemplazaPorUnoNuevo(String recibido) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/falla").header("X-Request-Id", recibido)).andReturn();
        String requestId = JsonPath.read(resultado.getResponse().getContentAsString(), "$.requestId");

        assertThat(requestId).matches(UUID_V4).isNotEqualTo(recibido);
        assertThat(resultado.getResponse().getHeader("X-Request-Id")).isEqualTo(requestId);
    }

    @Test
    void unRequestIdDe65CaracteresSeReemplazaYNoLlegaAlLog(CapturedOutput salida) throws Exception {
        String largo = "a".repeat(65);

        MvcResult resultado = mockMvc.perform(get("/falla").header("X-Request-Id", largo)).andReturn();
        String requestId = JsonPath.read(resultado.getResponse().getContentAsString(), "$.requestId");

        assertThat(requestId).matches(UUID_V4);
        assertThat(salida.getOut()).doesNotContain(largo);
    }

    @Test
    void unRechazoDeLaPersonaSeRegistraEnWarnSinTraza(CapturedOutput salida) throws Exception {
        mockMvc.perform(get("/correo-del-dominio").header("X-Request-Id", "rechazo-1"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(salida.getOut())
                .contains("WARN")
                .contains("status=422, code=VALIDATION_FAILED, requestId=rechazo-1] campos=[email=EMAIL_INVALID_FORMAT]")
                .doesNotContain("InvalidEmailException")
                .doesNotContain("ana@");
    }

    @Test
    void unValorDeTipoEquivocadoEnElPronombreEsUnaOpcionInvalida() {
        HttpMessageNotReadableException error = new HttpMessageNotReadableException("lectura",
                MismatchedInputException.from(null, Pronoun.class, "x").prependPath(new Object(), "pronoun"),
                new MockHttpInputMessage(new byte[0]));

        ProblemDetail problema = manejador.cuerpoIlegible(error);

        assertThat(problema.getProperties()).containsEntry("code", "VALIDATION_FAILED");
        assertThat(problema.getProperties().get("errors")).asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsExactly(new BusinessExceptionHandler.CampoRechazado("pronoun", "PRONOUN_INVALID_VALUE",
                        "Selecciona una opción."));
    }

    @Test
    void unValorDeTipoEquivocadoEnOtroCampoEsUnCuerpoIlegible() {
        HttpMessageNotReadableException error = new HttpMessageNotReadableException("lectura",
                MismatchedInputException.from(null, String.class, "x").prependPath(new Object(), "birthDate"),
                new MockHttpInputMessage(new byte[0]));

        ProblemDetail problema = manejador.cuerpoIlegible(error);

        assertThat(problema.getProperties()).containsEntry("code", "REQUEST_BODY_INVALID_FORMAT");
        assertThat(problema.getDetail()).isEqualTo("Revisa el formato de los datos enviados.");
    }

    @Test
    void unJsonMalFormadoEnElPronombreEsUnCuerpoIlegibleAunqueTraigaSuRuta() {
        // Un error de lectura del JSON puede traer la ruta del campo donde se cortó; no es una
        // opción equivocada, es un cuerpo que no se puede interpretar.
        HttpMessageNotReadableException error = new HttpMessageNotReadableException("lectura",
                new StreamReadException(null, "x").prependPath(new Object(), "pronoun"),
                new MockHttpInputMessage(new byte[0]));

        ProblemDetail problema = manejador.cuerpoIlegible(error);

        assertThat(problema.getProperties()).containsEntry("code", "REQUEST_BODY_INVALID_FORMAT");
    }

    @Test
    void unTipoEquivocadoSinRutaEsUnCuerpoIlegible() {
        HttpMessageNotReadableException error = new HttpMessageNotReadableException("lectura",
                MismatchedInputException.from(null, Pronoun.class, "x"), new MockHttpInputMessage(new byte[0]));

        assertThat(manejador.cuerpoIlegible(error).getProperties()).containsEntry("code", "REQUEST_BODY_INVALID_FORMAT");
    }

    @Test
    void elConflictoDeCorreoLlevaSuCampoYRegistraUnSoloWarnSinElUid(CapturedOutput salida) {
        ProblemDetail problema = manejador.correoRepetido(new EmailAlreadyRegisteredException());

        assertThat(problema.getStatus()).isEqualTo(409);
        assertThat(problema.getTitle()).isEqualTo("Correo ya registrado");
        assertThat(problema.getProperties()).containsEntry("code", "EMAIL_ALREADY_REGISTERED");
        assertThat(problema.getProperties().get("errors")).isEqualTo(List.of(new BusinessExceptionHandler.CampoRechazado(
                "email", "EMAIL_ALREADY_REGISTERED", "Ese correo ya tiene una cuenta.")));
        assertThat(lineasConLaPalabra(salida, "WARN")).hasSize(1);
        assertThat(handlerLinesOnly(salida)).doesNotContain("ERROR").doesNotContain("uid=");
    }

    @Test
    void unaCredencialSinCuentaRecienteSeRegistraEnWarnConElUid(CapturedOutput salida) {
        manejador.correoRepetido(new EmailAlreadyRegisteredException("uid-reciente", false));

        assertThat(lineasConLaPalabra(salida, "WARN")).hasSize(1);
        assertThat(handlerLinesOnly(salida)).contains("uid=uid-reciente").contains("registro en curso").doesNotContain("ERROR");
    }

    @Test
    void unaCredencialSinCuentaAntiguaSeRegistraEnUnSoloErrorConTodoLoNecesario(CapturedOutput salida) {
        ProblemDetail problema = manejador.correoRepetido(new EmailAlreadyRegisteredException("uid-antiguo", true));

        assertThat(problema.getStatus()).isEqualTo(409);
        assertThat(lineasConLaPalabra(salida, "ERROR")).hasSize(1);
        assertThat(handlerLinesOnly(salida)).doesNotContain("WARN");
        assertThat(handlerLinesOnly(salida))
                .contains("conciliación manual")
                .contains("code=EMAIL_ALREADY_REGISTERED")
                .contains("requestId=" + problema.getProperties().get("requestId"))
                .contains("uid=uid-antiguo")
                .doesNotContain("ana@");
    }

    private static List<String> lineasConLaPalabra(CapturedOutput salida, String nivel) {
        return handlerLinesOnly(salida).lines().filter(linea -> linea.contains(" " + nivel + " ")).toList();
    }

    /** Controlador de la prueba que provoca cada tipo de fallo. */
    @RestController
    static class ControladorQueFalla {

        @GetMapping("/falla")
        String falla() {
            throw new IllegalStateException("detalle interno secreto");
        }

        @GetMapping("/correo-del-dominio")
        String correoDelDominio() {
            return new EmailAddress("ana@").value();
        }

        @GetMapping("/restriccion")
        String restriccion() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new ConstraintViolationException(
                            "Failing row contains (nombre)=(Ana Pérez) viola ck_cuenta_nombre",
                            new SQLException("Failing row contains (Ana Pérez)"), "ck_cuenta_nombre"));
        }

        @GetMapping("/restriccion-sin-nombre")
        String restriccionSinNombre() {
            throw new DataIntegrityViolationException("duplicate key value (nombre)=(Ana Pérez)");
        }

        @GetMapping("/valor-de-libreria")
        String valorDeLibreria() {
            throw new IllegalArgumentException("No enum constant tech.cameia.X");
        }
    }

    /**
     * Deja solo las líneas del manejador: otros hilos de la JVM (por ejemplo el mantenimiento de un pool de conexiones de un
     * contexto de Spring de otra prueba) escriben en la misma salida y no deben alterar el conteo.
     */
    private static String handlerLinesOnly(CapturedOutput salida) {
        return salida.getOut().lines().filter(linea -> linea.contains("BusinessExceptionHandler"))
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
