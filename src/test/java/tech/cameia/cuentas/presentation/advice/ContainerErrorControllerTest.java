package tech.cameia.cuentas.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Comprueba que cada estado reportado por el contenedor sale con la forma común del servicio. */
@ExtendWith(OutputCaptureExtension.class)
class ContainerErrorControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ContainerErrorController())
            .setControllerAdvice(ProblemDetailTestSupport.manejadorDeErrores())
            .build();

    @ParameterizedTest
    @CsvSource({
        "404,404,ROUTE_NOT_FOUND",
        "405,405,METHOD_NOT_ALLOWED",
        "406,406,MEDIA_TYPE_NOT_ACCEPTABLE",
        "415,415,MEDIA_TYPE_NOT_ALLOWED",
        "400,422,REQUEST_BODY_INVALID_FORMAT",
        "413,500,INTERNAL_ERROR",
        "503,500,INTERNAL_ERROR"})
    @DisplayName("Cada estado del contenedor se traduce al código y estado del servicio")
    void error_shouldMapStatus(int original, int expected, String code) throws Exception {
        mockMvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, original))
                .andExpect(status().is(expected))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().string("Content-Type", "application/problem+json;charset=UTF-8"));
    }

    @Test
    @DisplayName("Sin estado original responde 404 y lo registra como advertencia")
    void error_shouldReturn404_whenNoOriginalStatus(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));

        assertThat(output.getAll()).contains("WARN").contains("ROUTE_NOT_FOUND");
    }

    @Test
    @DisplayName("Conserva el identificador de la petición cuando es válido")
    void error_shouldKeepRequestId_whenHeaderIsValid() throws Exception {
        mockMvc.perform(get("/error").header("X-Request-Id", "abc-1")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 405))
                .andExpect(jsonPath("$.requestId").value("abc-1"))
                .andExpect(header().string("X-Request-Id", "abc-1"));
    }

    @Test
    @DisplayName("No filtra la ruta original cuando está mal codificada")
    void error_shouldNotLeakPath_whenOriginalUriIsMalformed() throws Exception {
        MvcResult result = mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/x/%zz"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("%zz");
    }

    @Test
    @DisplayName("Un estado sin código propio conserva como causa la excepción original del contenedor")
    void error_shouldKeepOriginalException_whenStatusHasNoOwnCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
        IllegalArgumentException original = new IllegalArgumentException("fallo original");
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, original);

        assertThatThrownBy(() -> new ContainerErrorController().error(request))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(original);
    }

    @Test
    @DisplayName("Sin excepción original del contenedor la causa queda vacía")
    void error_shouldHaveNoCause_whenContainerReportedNoException() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 503);

        assertThatThrownBy(() -> new ContainerErrorController().error(request))
                .isInstanceOf(IllegalStateException.class)
                .hasNoCause();
    }
}
