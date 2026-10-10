package tech.cameia.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.cuentas.application.service.OutboxRelayService;
import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;

/**
 * Comprueba que, con el broker caído, el registro responde 201 sin sumar más de 1,2 s por la publicación inmediata (DES-02)
 * y deja el evento pendiente para la tarea de relevo.
 *
 * <p>Vive en una clase aparte porque necesita otra configuración del broker: ninguno escucha en el puerto configurado.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Evento cuenta.creada con el broker caído")
class AccountCreatedEventBrokerDownEndToEndTest {

    private static final Logger logger = LoggerFactory.getLogger(AccountCreatedEventBrokerDownEndToEndTest.class);
    private static final long LIMIT_MILLIS = 1200;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void brokerlessProperties(DynamicPropertyRegistry registry) throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        registry.add("spring.rabbitmq.host", () -> "localhost");
        registry.add("spring.rabbitmq.port", () -> freePort);
        registry.add("cuentas.events.enabled", () -> "true");
    }

    @Autowired
    private TestRestTemplate client;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InMemoryFirebaseUserDirectory directory;

    @MockitoSpyBean
    private OutboxRelayService relay;

    @BeforeEach
    void cleanState() {
        directory.limpiar();
        jdbc.update("DELETE FROM microcuentas.evento_saliente");
        jdbc.update("DELETE FROM microcuentas.cuenta");
    }

    @Test
    @DisplayName("responde 201 y suma menos de 1 200 ms por la publicación cuando el broker está caído")
    void register_shouldRespond201WithinOneSecondExtra_whenBrokerIsDown() {
        AtomicLong relayMillis = new AtomicLong(-1);
        doAnswer(invocation -> {
            long start = System.nanoTime();
            try {
                return invocation.callRealMethod();
            } finally {
                relayMillis.set((System.nanoTime() - start) / 1_000_000);
            }
        }).when(relay).relay(any());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"firstName":"Ana","lastName":"Pérez","birthDate":"15/03/2008",
                 "email":"ana.perez@ejemplo.test","password":"una frase muy larga 2026","pronoun":"SHE"}
                """;

        long start = System.nanoTime();
        ResponseEntity<String> response = client.postForEntity("/api/v1/users", new HttpEntity<>(body, headers), String.class);
        long totalMillis = (System.nanoTime() - start) / 1_000_000;

        logger.info("Medida DES-02: relevo={} ms, POST total={} ms", relayMillis.get(), totalMillis);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject("SELECT intentos FROM microcuentas.evento_saliente WHERE fecha_publicacion IS NULL",
                Integer.class)).isEqualTo(1);
        assertThat(relayMillis.get()).as("duración de relay(...) con el broker caído").isBetween(0L, LIMIT_MILLIS - 1);
    }
}
