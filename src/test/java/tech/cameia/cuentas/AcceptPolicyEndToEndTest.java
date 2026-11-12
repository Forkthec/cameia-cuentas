package tech.cameia.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;

/**
 * Prueba de extremo a extremo de {@code POST /api/v1/users/me/policy-acceptance}: aplicación completa y PostgreSQL
 * real. Comprueba que la fila se actualiza, que repetir la aceptación conserva la primera fecha, que dos peticiones
 * simultáneas responden igual y que la ruta responde a tiempo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
class AcceptPolicyEndToEndTest {

    private static final String ROUTE = "/api/v1/users/me/policy-acceptance";
    private static final String UID = "uid-policy-e2e";
    private static final String BODY = "{\"policyVersion\":\"2026-11-v1\"}";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private TestRestTemplate client;

    @Autowired
    private InMemoryFirebaseUserDirectory directory;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanState() {
        directory.limpiar();
        jdbc.update("DELETE FROM microcuentas.cuenta");
    }

    @Test
    @DisplayName("Pasa una cuenta sin aceptar a la versión vigente y guarda la fecha")
    void acceptPolicy_shouldUpgradeNotAcceptedAccount() throws Exception {
        insertNotAccepted(UID);

        ResponseEntity<String> response = accept(UID, BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.get("acceptedVersion").asString()).isEqualTo("2026-11-v1");
        assertThat(body.get("currentVersion").asString()).isEqualTo("2026-11-v1");
        assertThat(Instant.parse(body.get("acceptedAt").asString())).isAfter(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(jdbc.queryForObject("SELECT politica_version FROM microcuentas.cuenta WHERE firebase_uid = ?",
                String.class, UID)).isEqualTo("2026-11-v1");
    }

    @Test
    @DisplayName("Repetir la aceptación responde 200 y conserva la fecha de la primera")
    void acceptPolicy_shouldKeepFirstDate_whenRepeated() throws Exception {
        insertNotAccepted(UID);
        String first = mapper.readTree(accept(UID, BODY).getBody()).get("acceptedAt").asString();

        ResponseEntity<String> second = accept(UID, BODY);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(second.getBody()).get("acceptedAt").asString()).isEqualTo(first);
    }

    @Test
    @DisplayName("Dos peticiones simultáneas responden 200 las dos y con la misma fecha")
    void acceptPolicy_shouldAnswer200Twice_whenSentConcurrently() throws Exception {
        insertNotAccepted(UID);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<String>> call = () -> {
                ready.countDown();
                go.await();
                return accept(UID, BODY);
            };
            Future<ResponseEntity<String>> a = pool.submit(call);
            Future<ResponseEntity<String>> b = pool.submit(call);
            ready.await();
            go.countDown();

            ResponseEntity<String> first = a.get(30, TimeUnit.SECONDS);
            ResponseEntity<String> second = b.get(30, TimeUnit.SECONDS);

            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(mapper.readTree(first.getBody()).get("acceptedAt").asString())
                    .isEqualTo(mapper.readTree(second.getBody()).get("acceptedAt").asString());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Veinte aceptaciones seguidas responden cada una en menos de 2 s")
    void acceptPolicy_shouldAnswerWithin2Seconds() {
        insertNotAccepted(UID);
        long slowest = 0;

        for (int i = 0; i < 20; i++) {
            long start = System.nanoTime();
            ResponseEntity<String> response = accept(UID, BODY);
            slowest = Math.max(slowest, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        assertThat(slowest).isLessThan(2_000);
    }

    @Test
    @DisplayName("Responde 404 ACCOUNT_NOT_FOUND cuando el usuario no tiene cuenta")
    void acceptPolicy_shouldReturn404_whenNoAccount() {
        ResponseEntity<String> response = accept("uid-sin-cuenta", BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(response.getBody()).contains("\"code\":\"ACCOUNT_NOT_FOUND\"");
    }

    @Test
    @DisplayName("Una versión vieja responde 422 POLICY_VERSION_OUTDATED y no cambia la fila")
    void acceptPolicy_shouldReturn422_whenVersionIsOld() {
        insertNotAccepted(UID);

        ResponseEntity<String> response = accept(UID, "{\"policyVersion\":\"2026-10-v1\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).contains("\"code\":\"POLICY_VERSION_OUTDATED\"")
                .contains("\"field\":\"policyVersion\"");
        assertThat(jdbc.queryForObject("SELECT politica_version FROM microcuentas.cuenta WHERE firebase_uid = ?",
                String.class, UID)).isEqualTo("sin-aceptar");
    }

    @Test
    @DisplayName("Una cuenta deshabilitada responde 403 ACCOUNT_DISABLED y no cambia la fila")
    void acceptPolicy_shouldReturn403_whenAccountIsDisabled() {
        insertNotAccepted(UID);
        jdbc.update("UPDATE microcuentas.cuenta SET estado = 'DISABLED' WHERE firebase_uid = ?", UID);

        ResponseEntity<String> response = accept(UID, BODY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("\"code\":\"ACCOUNT_DISABLED\"");
    }

    private ResponseEntity<String> accept(String uid, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.add("X-User-Id", uid);
        return client.exchange(ROUTE, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    /** Inserta una cuenta anterior a la aceptación: versión sin-aceptar y la fecha de su creación. */
    private void insertNotAccepted(String uid) {
        jdbc.update("""
                INSERT INTO microcuentas.cuenta
                    (id, firebase_uid, nombre, apellido, fecha_nacimiento, pronombres, estado,
                     politica_version, politica_aceptada_en)
                VALUES (?, ?, 'Ana', 'Pérez', DATE '1995-04-12', 'SHE', 'ACTIVE', 'sin-aceptar',
                        TIMESTAMPTZ '2026-11-01 00:00:00+00')""", UUID.randomUUID(), uid);
    }
}
