package tech.cameia.cuentas.domain.port;

import tech.cameia.cuentas.domain.event.OutboundEvent;

/**
 * Publica en el broker de mensajes los eventos registrados en la tabla de salida.
 *
 * <p>El dominio solo necesita saber si el evento quedó entregado; cómo se habla con el broker es asunto del adaptador.</p>
 */
public interface EventPublisher {

    /**
     * Intenta publicar un evento pendiente.
     *
     * @param event evento pendiente leído de la tabla de salida
     * @return {@code true} solo cuando el broker confirmó el mensaje y lo enrutó al menos a una cola
     */
    boolean publish(OutboundEvent event);
}
