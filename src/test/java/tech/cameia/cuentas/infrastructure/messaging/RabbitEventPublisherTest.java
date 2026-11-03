package tech.cameia.cuentas.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
 * Prueba del publicador contra un RabbitMQ real: lo que se comprueba es lo que llega al broker (propiedades del mensaje) y
 * que la confirmación y la devolución del broker se traducen en {@code true} o {@code false}.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("RabbitEventPublisher")
class RabbitEventPublisherTest {

    private static final String QUEUE = "test.cuenta-creada";
    private static final String PAYLOAD = "{\"usuarioId\":\"6f1d2c3b4a5e4f60718293a4b5c6d7e8\",\"email\":\"ana.perez@ejemplo.test\","
            + "\"fechaNacimiento\":\"2008-03-15\",\"creadaEn\":\"2026-10-09T15:04:05.123Z\"}";

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
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private TopicExchange accountEventsExchange;

    @BeforeEach
    void bindTestQueue() {
        Queue queue = new Queue(QUEUE, false, false, false);
        rabbitAdmin.declareQueue(queue);
        Binding binding = BindingBuilder.bind(queue).to(accountEventsExchange).with("cuenta.creada");
        rabbitAdmin.declareBinding(binding);
        rabbitAdmin.purgeQueue(QUEUE, false);
    }

    @Test
    @DisplayName("entrega el mensaje exacto y devuelve true cuando el broker confirma")
    void publish_shouldReturnTrueAndDeliverExactMessage_whenBrokerConfirms() {
        OutboundEvent event = event();

        boolean result = publisher.publish(event);

        assertThat(result).isTrue();
        Message message = rabbitTemplate.receive(QUEUE, 5000);
        assertThat(message).isNotNull();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(PAYLOAD);
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getContentEncoding()).isEqualTo("UTF-8");
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(message.getMessageProperties().getType()).isEqualTo("cuenta.creada");
        assertThat(message.getMessageProperties().getAppId()).isEqualTo("cameia-cuentas");
        assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo("req-1");
        assertThat(message.getMessageProperties().getHeaders().get("x-event-version")).isEqualTo(1);
        assertThat(message.getMessageProperties().getHeaders().get("x-causation-id")).isEqualTo("req-1");
        assertThat(message.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        // El timestamp de AMQP guarda segundos; los milisegundos viajan en creadaEn de la carga
        assertThat(message.getMessageProperties().getTimestamp().toInstant()).isEqualTo(Instant.parse("2026-10-09T15:04:05Z"));
    }

    @Test
    @DisplayName("devuelve false cuando ninguna cola recibe el mensaje")
    void publish_shouldReturnFalse_whenNoQueueIsBound(CapturedOutput output) {
        rabbitAdmin.deleteQueue(QUEUE);

        boolean result = publisher.publish(event());

        assertThat(result).isFalse();
        // La devolución ya se trata en el publicador: la plantilla no debe avisar además de que le falta un manejador
        assertThat(output.getAll()).doesNotContain("no callback available");
    }

    private static OutboundEvent event() {
        return new OutboundEvent(UUID.fromString("11111111-1111-4111-8111-111111111111"), "cuenta.creada", 1,
                "6f1d2c3b4a5e4f60718293a4b5c6d7e8", PAYLOAD, Instant.parse("2026-10-09T15:04:05.123Z"), "req-1", 0);
    }
}
