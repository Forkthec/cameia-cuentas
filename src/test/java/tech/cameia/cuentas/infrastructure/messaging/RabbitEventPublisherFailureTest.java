package tech.cameia.cuentas.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import tech.cameia.cuentas.domain.event.OutboundEvent;
import tech.cameia.cuentas.infrastructure.messaging.publisher.RabbitEventPublisher;

/**
 * Pruebas de los caminos de fallo del publicador con una plantilla simulada: confirmación negativa, plazo vencido,
 * interrupción y fallo de conexión. Todos deben dejar el evento como no publicado.
 */
@DisplayName("RabbitEventPublisher en los caminos de fallo")
class RabbitEventPublisherFailureTest {

    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final RabbitEventPublisher publisher = new RabbitEventPublisher(template, Duration.ofMillis(100));

    @Test
    @DisplayName("devuelve false cuando el broker responde con confirmación negativa")
    void publish_shouldReturnFalse_whenBrokerNacks() {
        doAnswer(invocation -> {
            CorrelationData data = invocation.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(false, "sin espacio"));
            return null;
        }).when(template).send(eq("cuentas.events"), eq("cuenta.creada"), any(Message.class), any(CorrelationData.class));

        assertThat(publisher.publish(event())).isFalse();
    }

    @Test
    @DisplayName("devuelve false cuando el broker no responde dentro del plazo")
    void publish_shouldReturnFalse_whenConfirmationTimesOut() {
        assertThat(publisher.publish(event())).isFalse();
    }

    @Test
    @DisplayName("devuelve false y conserva la marca de interrupción cuando el hilo es interrumpido")
    void publish_shouldReturnFalseAndKeepInterruptFlag_whenThreadIsInterrupted() {
        Thread.currentThread().interrupt();

        boolean result = publisher.publish(event());

        assertThat(result).isFalse();
        // Thread.interrupted() devuelve la marca y la limpia para no contaminar otras pruebas
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    @DisplayName("devuelve false cuando falla la conexión con el broker")
    void publish_shouldReturnFalse_whenConnectionFails() {
        doThrow(new AmqpConnectException(new java.io.IOException("sin conexión")))
                .when(template).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        assertThat(publisher.publish(event())).isFalse();
    }

    private static OutboundEvent event() {
        return new OutboundEvent(UUID.fromString("11111111-1111-4111-8111-111111111111"), "cuenta.creada", 1,
                "6f1d2c3b4a5e4f60718293a4b5c6d7e8", "{}", Instant.parse("2026-10-09T15:04:05.123Z"), "req-1", 0);
    }
}
