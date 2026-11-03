package tech.cameia.cuentas.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.cuentas.domain.event.OutboundEvent;
import tech.cameia.cuentas.domain.port.EventPublisher;

/**
 * Comprueba que, sin broker, el publicador responde {@code false} dentro del plazo y no bloquea el registro (DES-02).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("RabbitEventPublisher sin broker")
class RabbitEventPublisherBrokerDownTest {

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
    private EventPublisher publisher;

    @Test
    @DisplayName("devuelve false en menos de 1 200 ms cuando el broker está caído")
    void publish_shouldReturnFalseWithinTimeout_whenBrokerIsDown() {
        OutboundEvent event = new OutboundEvent(UUID.fromString("11111111-1111-4111-8111-111111111111"), "cuenta.creada", 1,
                "6f1d2c3b4a5e4f60718293a4b5c6d7e8", "{}", Instant.parse("2026-10-09T15:04:05.123Z"), "req-1", 0);

        long start = System.nanoTime();
        boolean result = publisher.publish(event);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(result).isFalse();
        assertThat(elapsedMillis).as("duración de la publicación con el broker caído").isLessThan(LIMIT_MILLIS);
    }
}
