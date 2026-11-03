package tech.cameia.cuentas.infrastructure.messaging.publisher;

import tech.cameia.cuentas.domain.event.OutboundEvent;
import tech.cameia.cuentas.domain.port.EventPublisher;

/**
 * Publicador que no habla con ningún broker: no confirma nada.
 *
 * <p>Existe para que la aplicación arranque con {@code cuentas.events.enabled=false} (por ejemplo, en una máquina sin
 * RabbitMQ). Como nunca confirma, los eventos quedan pendientes en la tabla de salida y se publican cuando se encienda
 * el publicador real: no se pierde ninguno.</p>
 */
public class DisabledEventPublisher implements EventPublisher {

    /**
     * No publica el evento.
     *
     * @param event evento pendiente
     * @return siempre {@code false}: el evento sigue pendiente
     */
    @Override
    public boolean publish(OutboundEvent event) {
        return false;
    }
}
