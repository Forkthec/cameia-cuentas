package tech.cameia.cuentas.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Comprueba en el documento OpenAPI publicado que cada ruta del contrato declara su respuesta
 * {@code 415} y que las rutas internas del contenedor no aparecen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "springdoc.api-docs.enabled=true")
@Testcontainers(disabledWithoutDocker = true)
class OpenApiProtocolErrorsTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    private int port;

    private String apiDocs() throws IOException {
        RawHttpClient.Reply reply = new RawHttpClient(port).send("GET", "/v3/api-docs", List.of(), null);
        assertThat(reply.status()).isEqualTo(200);
        return reply.body();
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/users,post",
        "/api/v1/users/me/verification,post",
        "/api/v1/auth/logout-all,post",
        "/api/v1/users/me,get",
        "/api/v1/users/health,get"})
    @DisplayName("Cada ruta del contrato documenta su 415 con el código y un ejemplo")
    void apiDocs_shouldDocument415_whenRequested(String route, String method) throws IOException {
        String docs = apiDocs();
        String base = "$.paths['" + route + "']." + method + ".responses['415']";

        assertThat((String) JsonPath.read(docs, base + ".description"))
                .contains("MEDIA_TYPE_NOT_ALLOWED").contains("REQUEST_MULTIPART_INVALID_FORMAT");
        assertThat(JsonPath.read(docs, base + ".content['application/problem+json'].examples").toString())
                .contains("MEDIA_TYPE_NOT_ALLOWED");
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/users,post",
        "/api/v1/users/me/verification,post",
        "/api/v1/auth/logout-all,post",
        "/api/v1/users/me,get",
        "/api/v1/users/health,get"})
    @DisplayName("Cada ruta del contrato documenta su 413 con el código y un ejemplo")
    void apiDocs_shouldDocument413_whenRequested(String route, String method) throws IOException {
        String docs = apiDocs();
        String base = "$.paths['" + route + "']." + method + ".responses['413']";

        assertThat((String) JsonPath.read(docs, base + ".description")).contains("REQUEST_BODY_TOO_LARGE");
        assertThat(JsonPath.read(docs, base + ".content['application/problem+json'].examples").toString())
                .contains("REQUEST_BODY_TOO_LARGE");
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/users,post",
        "/api/v1/users/me/verification,post",
        "/api/v1/auth/logout-all,post",
        "/api/v1/users/me,get",
        "/api/v1/users/health,get"})
    @DisplayName("Cada ruta del contrato documenta su 406 con el código y un ejemplo")
    void apiDocs_shouldDocument406_whenRequested(String route, String method) throws IOException {
        String docs = apiDocs();
        String base = "$.paths['" + route + "']." + method + ".responses['406']";

        assertThat((String) JsonPath.read(docs, base + ".description")).contains("MEDIA_TYPE_NOT_ACCEPTABLE");
        assertThat(JsonPath.read(docs, base + ".content['application/problem+json'].examples").toString())
                .contains("MEDIA_TYPE_NOT_ACCEPTABLE");
    }

    @Test
    @DisplayName("La ruta de error del contenedor no se publica en el contrato")
    void apiDocs_shouldNotListErrorRoute_whenRequested() throws IOException {
        assertThat(JsonPath.<Map<String, Object>>read(apiDocs(), "$.paths")).doesNotContainKey("/error");
    }
}
