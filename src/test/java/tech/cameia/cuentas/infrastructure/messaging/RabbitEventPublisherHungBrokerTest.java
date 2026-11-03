package tech.cameia.cuentas.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
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
 * Comprueba que un broker que acepta la conexión TCP pero nunca responde el protocolo AMQP (proceso colgado o balanceador
 * sin destino) no retiene la publicación más allá del plazo: el registro no puede esperar a un broker que no contesta
 * (DES-02, REST-0002).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("RabbitEventPublisher con un broker colgado")
class RabbitEventPublisherHungBrokerTest {

    private static final long LIMIT_MILLIS = 1200;
    private static final ServerSocket HUNG_BROKER = openHungBroker();
    private static final List<Socket> ACCEPTED = new CopyOnWriteArrayList<>();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void hungBrokerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", () -> "localhost");
        registry.add("spring.rabbitmq.port", HUNG_BROKER::getLocalPort);
        registry.add("cuentas.events.enabled", () -> "true");
    }

    @Autowired
    private EventPublisher publisher;

    /** Abre un puerto que acepta conexiones y las deja abiertas sin escribir ni leer nada. */
    private static ServerSocket openHungBroker() {
        try {
            ServerSocket server = new ServerSocket(0);
            Thread acceptor = new Thread(() -> {
                try {
                    while (!server.isClosed()) {
                        ACCEPTED.add(server.accept());
                    }
                } catch (IOException closed) {
                    // El puerto se cierra al terminar la clase: no hay nada que recuperar
                }
            }, "hung-broker-acceptor");
            acceptor.setDaemon(true);
            acceptor.start();
            return server;
        } catch (IOException failure) {
            throw new IllegalStateException("No se pudo abrir el puerto del broker colgado", failure);
        }
    }

    @AfterAll
    static void closeHungBroker() throws IOException {
        HUNG_BROKER.close();
        for (Socket socket : ACCEPTED) {
            socket.close();
        }
    }

    @Test
    @DisplayName("devuelve false en menos de 1 200 ms cuando el broker acepta la conexión y no responde")
    void publish_shouldReturnFalseWithinTimeout_whenBrokerAcceptsTcpButNeverAnswers() {
        OutboundEvent event = new OutboundEvent(UUID.fromString("11111111-1111-4111-8111-111111111111"), "cuenta.creada", 1,
                "6f1d2c3b4a5e4f60718293a4b5c6d7e8", "{}", Instant.parse("2026-10-09T15:04:05.123Z"), "req-1", 0);

        long start = System.nanoTime();
        boolean result = publisher.publish(event);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(result).isFalse();
        assertThat(elapsedMillis).as("duración de la publicación con el broker colgado").isLessThan(LIMIT_MILLIS);
    }
}
