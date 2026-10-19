package tech.cameia.cuentas.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        "413,413,REQUEST_BODY_TOO_LARGE",
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
    @DisplayName("instance lleva la ruta original de la petición y no /error")
    void error_shouldPointInstanceToOriginalPath_whenContainerReportedIt() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 405)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/users/me"))
                .andExpect(jsonPath("$.instance").value("/api/v1/users/me"));
    }

    @Test
    @DisplayName("Sin ruta original, instance conserva la ruta del despacho")
    void error_shouldKeepDispatchPath_whenNoOriginalUri() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(jsonPath("$.instance").value("/error"));
    }

    @Test
    @DisplayName("Un despacho de error de una petición ya registrada responde con el mismo identificador y no registra otra vez")
    void error_shouldReuseRequestIdAndNotLogAgain_whenErrorWasAlreadyLogged(CapturedOutput output)
            throws Exception {
        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/api/v1/users");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(first));
        ProblemDetail firstAnswer;
        try {
            firstAnswer = new BusinessExceptionHandler().multipartMalFormado(new MultipartException("x"),
                    multipart(first));
            first.setDispatcherType(DispatcherType.ERROR);
            first.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/users");
            ProblemDetail second = new BusinessExceptionHandler().rutaInexistente(
                    new NoResourceFoundException(HttpMethod.GET, "/", ""));

            assertThat(second.getProperties().get("requestId")).isEqualTo(firstAnswer.getProperties().get("requestId"));
            assertThat(second.getInstance()).hasToString("/api/v1/users");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        assertThat(output.getAll().lines().filter(line -> line.contains("Petición rechazada")).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un fallo imprevisto ya registrado no se registra otra vez en el despacho de error")
    void error_shouldNotLogErrorAgain_whenUnexpectedFailureWasAlreadyLogged(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            new BusinessExceptionHandler().falloInterno(new IllegalStateException("x"), request);
            request.setDispatcherType(DispatcherType.ERROR);
            new BusinessExceptionHandler().falloInterno(new IllegalStateException("x"), request);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        assertThat(output.getAll().lines().filter(line -> line.contains("Fallo no controlado")).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una ruta original que no empieza por barra no se usa como instance")
    void error_shouldKeepDispatchPath_whenOriginalUriIsNotAPath() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "x/y"))
                .andExpect(jsonPath("$.instance").value("/error"));
    }

    private static MockHttpServletRequest multipart(MockHttpServletRequest request) {
        request.setContentType("multipart/form-data");
        return request;
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
