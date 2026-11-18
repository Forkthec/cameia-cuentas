package tech.cameia.cuentas.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Comprueba en el documento OpenAPI publicado que el contrato de la aceptación de la política está completo: los
 * campos nuevos del registro y de la cuenta, la ruta de aceptación y sus códigos de error.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "springdoc.api-docs.enabled=true")
@Testcontainers(disabledWithoutDocker = true)
class OpenApiPolicyContractTest {

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

    @Test
    @DisplayName("El esquema del registro trae la autorización y la versión de la política como obligatorias")
    void apiDocs_shouldDocumentPolicyFields_inRegistrationSchema() throws IOException {
        String docs = apiDocs();
        String schema = "$.components.schemas.RegisterUserRequest";

        assertThat((String) JsonPath.read(docs, schema + ".properties.dataAuthorization.type")).isEqualTo("boolean");
        assertThat((Integer) JsonPath.read(docs, schema + ".properties.policyVersion.maxLength")).isEqualTo(20);
        assertThat(JsonPath.read(docs, schema + ".required").toString())
                .contains("dataAuthorization").contains("policyVersion");
    }

    @Test
    @DisplayName("El esquema de la cuenta trae la aceptación de la política")
    void apiDocs_shouldDocumentPolicyAcceptance_inAccountSchema() throws IOException {
        String docs = apiDocs();

        assertThat(JsonPath.read(docs, "$.components.schemas.CurrentAccountResponse.properties").toString())
                .contains("policyAcceptance");
        assertThat(JsonPath.read(docs, "$.components.schemas.PolicyAcceptanceResponse.properties").toString())
                .contains("acceptedVersion").contains("acceptedAt").contains("currentVersion");
    }

    @Test
    @DisplayName("La ruta de aceptación documenta sus respuestas y los tres códigos del 422 del registro")
    void apiDocs_shouldDocumentPolicyRoute_andRegistration422Codes() throws IOException {
        String docs = apiDocs();
        String route = "$.paths['/api/v1/users/me/policy-acceptance'].post.responses";

        assertThat(JsonPath.read(docs, route).toString()).contains("200", "400", "403", "404", "409", "422", "503");
        assertThat((String) JsonPath.read(docs, route + "['422'].description"))
                .contains("POLICY_VERSION_REQUIRED").contains("POLICY_VERSION_OUTDATED");
        assertThat((String) JsonPath.read(docs, "$.paths['/api/v1/users'].post.responses['422'].description"))
                .contains("DATA_AUTHORIZATION_REQUIRED").contains("POLICY_VERSION_REQUIRED")
                .contains("POLICY_VERSION_OUTDATED");
    }
}
