package tech.cameia.cuentas.infrastructure.messaging.publisher;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import tech.cameia.cuentas.domain.event.OutboundEvent;
import tech.cameia.cuentas.domain.port.EventPublisher;

/**
 * Envía los eventos de la tabla de salida al exchange {@code cuentas.events} y espera la confirmación del broker.
 *
 * <p>Un mensaje cuenta como publicado solo si el broker lo confirmó y no lo devolvió por no tener a dónde enrutarlo;
 * cualquier otra cosa (plazo vencido, confirmación negativa, devolución, fallo de conexión) deja el evento pendiente para
 * un reintento posterior. La carga nunca se escribe en el log.</p>
 */
public class RabbitEventPublisher implements EventPublisher {

    static final String EXCHANGE = "cuentas.events";
    static final String APP_ID = "cameia-cuentas";
    static final String VERSION_HEADER = "x-event-version";
    static final String CAUSATION_HEADER = "x-causation-id";

    private static final Logger logger = LoggerFactory.getLogger(RabbitEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final Duration timeout;

    /**
     * Crea el publicador.
     *
     * @param rabbitTemplate plantilla con confirmaciones y devoluciones activadas
     * @param timeout plazo máximo de espera de la confirmación del broker
     */
    public RabbitEventPublisher(RabbitTemplate rabbitTemplate, Duration timeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.timeout = timeout;
    }

    /**
     * Publica el evento y espera la confirmación del broker hasta el plazo configurado.
     *
     * @param event evento pendiente
     * @return {@code true} solo si el broker confirmó el mensaje y lo enrutó a una cola
     */
    @Override
    public boolean publish(OutboundEvent event) {
        Message message = buildMessage(event);
        CorrelationData confirmation = new CorrelationData(event.id().toString());
        try {
            // La clave de enrutamiento es el tipo del evento: cuenta.creada
            rabbitTemplate.send(EXCHANGE, event.type(), message, confirmation);
            CorrelationData.Confirm confirm = confirmation.getFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            // Con "mandatory" el broker devuelve el mensaje si ninguna cola lo recibe: se confirmó, pero a nadie llegó
            boolean routed = confirmation.getReturned() == null;
            if (confirm.ack() && routed) {
                return true;
            }
            logger.warn("El broker no confirmó el evento [eventId={}, type={}, ack={}, enrutado={}]",
                    event.id(), event.type(), confirm.ack(), routed);
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return failed(event, interrupted);
        } catch (AmqpException | ExecutionException | TimeoutException failure) {
            return failed(event, failure);
        }
    }

    /** Arma el mensaje con las propiedades del contrato; el cuerpo es la carga JSON tal como se guardó. */
    private static Message buildMessage(OutboundEvent event) {
        return MessageBuilder.withBody(event.payloadJson().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.id().toString())
                .setType(event.type())
                .setAppId(APP_ID)
                .setTimestamp(Date.from(event.createdAt()))
                .setCorrelationId(event.correlationId())
                .setHeader(VERSION_HEADER, event.version())
                .setHeader(CAUSATION_HEADER, event.correlationId())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }

    /** Registra el fallo sin la carga y lo informa como no publicado. */
    private boolean failed(OutboundEvent event, Exception failure) {
        logger.warn("Falló la publicación del evento [eventId={}, type={}, causa={}]",
                event.id(), event.type(), failure.getClass().getSimpleName());
        return false;
    }
}
