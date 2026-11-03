package tech.cameia.cuentas.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tech.cameia.cuentas.domain.event.OutboundEvent;
import tech.cameia.cuentas.domain.port.EventPublisher;

/**
 * Comprueba que un broker congelado con la conexión ya abierta (el proceso existe pero no atiende) no retiene publicaciones
 * simultáneas: cada una debe terminar dentro del plazo aunque tenga que abrir un canal nuevo (DES-02, REST-0002).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("RabbitEventPublisher con un broker congelado")
class RabbitEventPublisherPausedBrokerTest {

    private static final long LIMIT_MILLIS = 1200;
    private static final int CONCURRENT_PUBLICATIONS = 4;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static GenericContainer<?> rabbit = new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("cuentas.events.enabled", () -> "true");
    }

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private TopicExchange accountEventsExchange;

    @Test
    @DisplayName("cada publicación simultánea termina en menos de 1 200 ms cuando el broker está congelado")
    void publish_shouldReturnFalseWithinTimeout_whenBrokerIsPausedAndRequestsAreConcurrent() throws Exception {
        Queue queue = new Queue("test.paused", false, false, false);
        rabbitAdmin.declareQueue(queue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(accountEventsExchange).with("cuenta.creada"));
        // El calentamiento abre la conexión y deja un canal en uso: el congelamiento llega con la conexión ya establecida
        assertThat(publisher.publish(event(0))).isTrue();

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_PUBLICATIONS);
        rabbit.getDockerClient().pauseContainerCmd(rabbit.getContainerId()).exec();
        try {
            List<Future<Long>> durations = new ArrayList<>();
            for (int i = 1; i <= CONCURRENT_PUBLICATIONS; i++) {
                OutboundEvent event = event(i);
                durations.add(pool.submit(() -> {
                    long start = System.nanoTime();
                    boolean result = publisher.publish(event);
                    assertThat(result).isFalse();
                    return (System.nanoTime() - start) / 1_000_000;
                }));
            }
            for (Future<Long> duration : durations) {
                // El límite de 20 s solo evita que la prueba quede colgada si el defecto sigue presente
                assertThat(duration.get(20, TimeUnit.SECONDS)).isLessThan(LIMIT_MILLIS);
            }
        } finally {
            rabbit.getDockerClient().unpauseContainerCmd(rabbit.getContainerId()).exec();
            pool.shutdownNow();
        }
    }

    private static OutboundEvent event(int number) {
        return new OutboundEvent(UUID.fromString("11111111-1111-4111-8111-11111111111" + (number % 10)), "cuenta.creada", 1,
                "6f1d2c3b4a5e4f60718293a4b5c6d7e" + (number % 10), "{}", Instant.parse("2026-10-09T15:04:05.123Z"), "req-" + number, 0);
    }
}
