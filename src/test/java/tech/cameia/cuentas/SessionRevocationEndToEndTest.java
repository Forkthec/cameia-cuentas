package tech.cameia.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;

/**
 * Prueba de extremo a extremo de {@code POST /api/v1/auth/logout-all}: aplicación completa, PostgreSQL real y el doble en
 * memoria de Firebase.
 *
 * <p>Comprueba lo que las pruebas por capa no pueden: que la ruta está publicada sin cuerpo en 204, que el error de
 * identidad y el de Firebase salen como {@code application/problem+json} con charset y que dos cierres simultáneos del
 * mismo usuario terminan ambos bien.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
class SessionRevocationEndToEndTest {

    private static final String ROUTE = "/api/v1/auth/logout-all";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TestRestTemplate client;

    @Autowired
    private InMemoryFirebaseUserDirectory directory;

    @BeforeEach
    void cleanState() {
        directory.limpiar();
    }

    @Test
    @DisplayName("Revoca las sesiones del usuario del encabezado y responde 204 sin cuerpo")
    void logoutAll_shouldRevokeSessionsAndReturn204_whenCalledWithIdentity() {
        ResponseEntity<String> response = logoutAll("uid-e2e-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        assertThat(directory.revokedUids()).containsExactly("uid-e2e-1");
    }

    @Test
    @DisplayName("Repetir la petición vuelve a responder 204")
    void logoutAll_shouldReturn204Twice_whenRepeated() {
        ResponseEntity<String> first = logoutAll("uid-e2e-2");
        ResponseEntity<String> second = logoutAll("uid-e2e-2");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(directory.revokedUids()).containsExactly("uid-e2e-2", "uid-e2e-2");
    }

    @Test
    @DisplayName("Sin X-User-Id responde 400 problem+json con charset y no revoca nada")
    void logoutAll_shouldReturn400ProblemJson_whenIdentityMissing() {
        ResponseEntity<String> response = client.exchange(ROUTE, HttpMethod.POST, new HttpEntity<>(new HttpHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String contentType = response.getHeaders().getContentType().toString();
        assertThat(contentType).contains("application/problem+json").containsIgnoringCase("charset=UTF-8");
        assertThat(response.getBody()).contains("IDENTITY_REQUIRED");
        assertThat(directory.revokedUids()).isEmpty();
    }

    @Test
    @DisplayName("Con X-User-Id vacío responde 400 IDENTITY_REQUIRED con su mensaje y no revoca nada")
    void logoutAll_shouldReturn400_whenIdentityBlank() {
        ResponseEntity<String> response = logoutAll("");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("IDENTITY_REQUIRED")
                .contains("La petición no incluye los datos que exige esta ruta.");
        assertThat(directory.revokedUids()).isEmpty();
    }

    @Test
    @DisplayName("Si Firebase no responde, 503 DEPENDENCY_UNAVAILABLE con X-Request-Id")
    void logoutAll_shouldReturn503_whenFirebaseUnavailable() {
        directory.becomeUnavailableOnRevoke();

        ResponseEntity<String> response = logoutAll("uid-e2e-3");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("DEPENDENCY_UNAVAILABLE");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    @Test
    @DisplayName("Si Firebase rechaza la revocación por permisos, 500 INTERNAL_ERROR sin revelar el motivo")
    void logoutAll_shouldReturn500_whenFirebaseRejects() {
        directory.rejectRevocations();

        ResponseEntity<String> response = logoutAll("uid-e2e-5");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("INTERNAL_ERROR").doesNotContain("Firebase");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    @Test
    @DisplayName("Dos cierres simultáneos del mismo usuario responden 204 los dos")
    void logoutAll_shouldReturn204ForBoth_whenTwoConcurrentCalls() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ResponseEntity<String>>> calls = List.of(
                    pool.submit(() -> awaitAndLogout(start, "uid-e2e-4")),
                    pool.submit(() -> awaitAndLogout(start, "uid-e2e-4")));
            start.countDown();

            for (Future<ResponseEntity<String>> call : calls) {
                assertThat(call.get().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(directory.revokedUids()).containsExactly("uid-e2e-4", "uid-e2e-4");
    }

    private ResponseEntity<String> awaitAndLogout(CountDownLatch start, String uid) throws InterruptedException {
        start.await();
        return logoutAll(uid);
    }

    private ResponseEntity<String> logoutAll(String uid) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", uid);
        return client.exchange(ROUTE, HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }
}
