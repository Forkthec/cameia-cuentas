package tech.cameia.cuentas.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Peticiones que un navegador normal no produce (formularios mal codificados, encabezados con
 * caracteres de control, métodos raros) enviadas por un socket real al servicio completo.
 *
 * <p>Cada caso comprueba que la respuesta sale con la forma común del servicio: estado, {@code code},
 * {@code requestId} y {@code application/problem+json} con {@code charset}; nunca un {@code 500} sin
 * formato ni la página por defecto de Spring o de Tomcat.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ProtocolErrorsEndToEndTest {

    private static final String FORM = "Content-Type: application/x-www-form-urlencoded";
    private static final String PROBLEM_JSON = "application/problem+json;charset=UTF-8";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    private int port;

    private RawHttpClient raw() {
        return new RawHttpClient(port);
    }

    private static String codeOf(RawHttpClient.Reply reply) {
        return JsonPath.read(reply.body(), "$.code");
    }

    private static void assertCommonShape(RawHttpClient.Reply reply, int status, String code) {
        assertThat(reply.status()).isEqualTo(status);
        assertThat(codeOf(reply)).isEqualTo(code);
        assertThat((String) JsonPath.read(reply.body(), "$.requestId")).isNotBlank();
        assertThat(reply.headers().get("content-type")).isEqualToIgnoringCase(PROBLEM_JSON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
    @DisplayName("Un formulario mal codificado en un método no admitido responde 405")
    void formMethod_shouldReturn405_whenFormIsMalformed(String method) throws IOException {
        RawHttpClient.Reply reply = raw().send(method, "/api/v1/users", List.of(FORM), "a=%zz");

        assertCommonShape(reply, 405, "METHOD_NOT_ALLOWED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
    @DisplayName("Un formulario mal codificado en una ruta inexistente responde 404")
    void formMethod_shouldReturn404_whenRouteDoesNotExist(String method) throws IOException {
        RawHttpClient.Reply reply = raw().send(method, "/ruta-inexistente", List.of(FORM), "a=%zz");

        assertCommonShape(reply, 404, "ROUTE_NOT_FOUND");
    }

    @Test
    @DisplayName("Un formulario bien formado en el registro sigue respondiendo 415")
    void register_shouldStillReturn415_whenFormIsWellFormed() throws IOException {
        RawHttpClient.Reply reply = raw().send("POST", "/api/v1/users", List.of(FORM), "a=1");

        assertCommonShape(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("Un JSON mal formado en el registro sigue respondiendo 422")
    void register_shouldStillReturn422_whenJsonIsMalformed() throws IOException {
        RawHttpClient.Reply reply = raw().send("POST", "/api/v1/users",
                List.of("Content-Type: application/json"), "{\"a\":");

        assertCommonShape(reply, 422, "REQUEST_BODY_INVALID_FORMAT");
    }

    @Test
    @DisplayName("Las rutas sin cuerpo conservan su respuesta con un formulario mal codificado")
    void noBodyRoutes_shouldKeepTheirAnswer_whenFormIsMalformed() throws IOException {
        RawHttpClient.Reply logout = raw().send("POST", "/api/v1/auth/logout-all",
                List.of(FORM, "X-User-Id: uid-cm335-a"), "a=%zz");
        RawHttpClient.Reply verification = raw().send("POST", "/api/v1/users/me/verification",
                List.of(FORM, "X-User-Id: uid-cm335-a"), "a=%zz");

        assertThat(logout.status()).isEqualTo(204);
        assertCommonShape(verification, 404, "ACCOUNT_NOT_FOUND");
    }
}
