package tech.cameia.cuentas.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Comprueba que los errores del propio framework web (ruta inexistente, método no permitido,
 * tipo de contenido y tipo de respuesta no admitidos) salen con el formato de error del servicio y no como un
 * {@code 500} ni como la página de error por defecto.
 *
 * <p>Usa el contexto completo con un puerto aleatorio porque el comportamiento depende de
 * cómo el contenedor de servlets y Spring resuelven la ruta antes de llegar a un
 * controlador; un {@code MockMvc} autónomo no reproduce esa resolución.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
class FrameworkErrorsTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TestRestTemplate cliente;

    @Test
    void unaRutaInexistenteDevuelveSuCodigoYUn404() {
        ResponseEntity<String> respuesta = cliente.getForEntity("/ruta-que-no-existe", String.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertEsErrorDelServicio(respuesta, "ROUTE_NOT_FOUND", "No existe la ruta solicitada.");
    }

    @Test
    void unMetodoNoPermitidoDevuelveSuCodigoYUn405() {
        ResponseEntity<String> respuesta = cliente.exchange("/api/v1/users", HttpMethod.DELETE,
                HttpEntity.EMPTY, String.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(405);
        assertEsErrorDelServicio(respuesta, "METHOD_NOT_ALLOWED", "Método no permitido.");
        assertThat(respuesta.getHeaders().getAllow()).containsExactly(HttpMethod.POST);
    }

    @Test
    void unTipoDeContenidoNoAdmitidoDevuelveSuCodigoYUn415() {
        HttpHeaders encabezados = new HttpHeaders();
        encabezados.setContentType(MediaType.TEXT_PLAIN);

        ResponseEntity<String> respuesta = cliente.exchange("/api/v1/users", HttpMethod.POST,
                new HttpEntity<>("hola", encabezados), String.class);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(415);
        assertEsErrorDelServicio(respuesta, "MEDIA_TYPE_NOT_ALLOWED", "Tipo de contenido no admitido.");
    }

    @Test
    @DisplayName("Un tipo de respuesta no admitido devuelve su código y un 406")
    void anyRoute_shouldReturn406WithItsCode_whenAcceptedResponseTypeIsNotSupported() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.IMAGE_PNG));

        ResponseEntity<String> response = cliente.exchange("/api/v1/users/health", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(406);
        assertEsErrorDelServicio(response, "MEDIA_TYPE_NOT_ACCEPTABLE", "Tipo de respuesta no admitido.");
    }

    @Test
    @DisplayName("El servicio no responde en XML")
    void anyRoute_shouldReturn406_whenClientAsksForXml() {
        // El contrato es solo JSON. Una dependencia de Firebase traía un convertidor XML con el
        // que el servicio respondía en XML a quien lo pidiera.
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_XML));

        ResponseEntity<String> response = cliente.exchange("/api/v1/users/health", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(406);
        assertEsErrorDelServicio(response, "MEDIA_TYPE_NOT_ACCEPTABLE", "Tipo de respuesta no admitido.");
    }

    @Test
    void ningunaRespuestaDeErrorDelFrameworkRevelaTextosInternos() {
        ResponseEntity<String> respuesta = cliente.getForEntity("/ruta-que-no-existe", String.class);

        assertThat(respuesta.getBody())
                .doesNotContain("NoResourceFoundException")
                .doesNotContain("org.springframework")
                .doesNotContain("Whitelabel");
    }

    private void assertEsErrorDelServicio(ResponseEntity<String> respuesta, String codigo, String detalle) {
        assertThat(respuesta.getHeaders().getContentType()).isNotNull();
        assertThat(respuesta.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        assertThat((String) JsonPath.read(respuesta.getBody(), "$.code")).isEqualTo(codigo);
        assertThat((String) JsonPath.read(respuesta.getBody(), "$.detail")).isEqualTo(detalle);
        assertThat((String) JsonPath.read(respuesta.getBody(), "$.requestId"))
                .isEqualTo(respuesta.getHeaders().getFirst("X-Request-Id"))
                .isNotBlank();
        assertThat(respuesta.getHeaders().getContentType().getCharset()).isEqualTo(StandardCharsets.UTF_8);
    }
}
