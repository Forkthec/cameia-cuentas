package tech.cameia.cuentas.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.Set;

import com.jayway.jsonpath.JsonPath;

import jakarta.validation.Constraint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.presentation.dto.RegisterUserRequest;

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
                if (anotacion.annotationType().isAnnotationPresent(Constraint.class)) {
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
    void unValorInvalidoDelDominioTieneSuPropioCodigo() throws Exception {
        mockMvc.perform(get("/correo-del-dominio"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID_VALUE"))
                .andExpect(jsonPath("$.detail").value("El correo electrónico no tiene un formato válido"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void unValorInvalidoDeLibreriaNoMuestraSuMensaje(CapturedOutput salida) throws Exception {
        mockMvc.perform(get("/valor-de-libreria"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID_VALUE"))
                .andExpect(jsonPath("$.detail").value("Revisa los datos enviados."))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("No enum constant"));

        assertThat(salida.getOut())
                .contains("code=REQUEST_INVALID_VALUE")
                .contains("origen=" + ControladorQueFalla.class.getName() + ".valorDeLibreria")
                .doesNotContain("No enum constant");
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
        mockMvc.perform(get("/valor-de-libreria").header("X-Request-Id", "rechazo-1"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(salida.getOut())
                .contains("WARN")
                .contains("status=422, code=REQUEST_INVALID_VALUE, requestId=rechazo-1")
                .doesNotContain("java.lang.IllegalArgumentException");
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

        @GetMapping("/valor-de-libreria")
        String valorDeLibreria() {
            throw new IllegalArgumentException("No enum constant tech.cameia.X");
        }
    }
}
