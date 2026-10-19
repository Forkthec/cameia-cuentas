package tech.cameia.cuentas.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

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
 * Comprueba en el documento OpenAPI publicado que las rutas que usan la base de datos documentan su
 * {@code 503 DATABASE_UNAVAILABLE} con un ejemplo, y que las que no la usan no lo declaran.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "springdoc.api-docs.enabled=true")
@Testcontainers(disabledWithoutDocker = true)
class OpenApiDatabaseUnavailableTest {

    private static final String EXAMPLES = ".content['application/problem+json'].examples";

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

    private static String response503(String docs, String route, String method) {
        return "$.paths['" + route + "']." + method + ".responses['503']";
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/users,post",
        "/api/v1/users/me,get",
        "/api/v1/users/me/verification,post"})
    @DisplayName("Las rutas que usan la base documentan su 503 DATABASE_UNAVAILABLE con un ejemplo")
    void apiDocs_shouldDocument503_whenRequested(String route, String method) throws IOException {
        String docs = apiDocs();
        String base = response503(docs, route, method);

        assertThat((String) JsonPath.read(docs, base + ".description")).contains("DATABASE_UNAVAILABLE");
        assertThat(JsonPath.read(docs, base + EXAMPLES).toString()).contains("DATABASE_UNAVAILABLE");
    }

    @Test
    @DisplayName("El registro conserva además su 503 de Firebase")
    void apiDocs_shouldKeepFirebase503_whenRequested() throws IOException {
        String docs = apiDocs();
        String base = response503(docs, "/api/v1/users", "post");

        assertThat((String) JsonPath.read(docs, base + ".description")).contains("DEPENDENCY_UNAVAILABLE");
        assertThat(JsonPath.read(docs, base + EXAMPLES).toString()).contains("DEPENDENCY_UNAVAILABLE");
    }

    @Test
    @DisplayName("Cerrar sesión en todos los dispositivos conserva su 503 de Firebase y no declara el de la base")
    void apiDocs_shouldNotDocumentDatabase503_whenLogoutAll() throws IOException {
        String docs = apiDocs();
        String base = response503(docs, "/api/v1/auth/logout-all", "post");

        assertThat((String) JsonPath.read(docs, base + ".description"))
                .contains("DEPENDENCY_UNAVAILABLE").doesNotContain("DATABASE_UNAVAILABLE");
    }

    @Test
    @DisplayName("La ruta de salud no declara un 503")
    void apiDocs_shouldNotDocument503_whenHealth() throws IOException {
        String docs = apiDocs();

        assertThat(JsonPath.<java.util.Map<String, Object>>read(docs, "$.paths['/api/v1/users/health'].get.responses"))
                .doesNotContainKey("503");
    }
}
