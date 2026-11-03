package tech.cameia.cuentas.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Fila pendiente de la tabla de salida, tal como la necesita el publicador.
 *
 * @param id identificador del evento, también el {@code message_id} de AMQP
 * @param type tipo del evento (por ejemplo {@code cuenta.creada})
 * @param version versión del contrato del evento
 * @param aggregateId identificador del agregado al que pertenece (el {@code firebaseUid} de la cuenta)
 * @param payloadJson cuerpo JSON ya serializado
 * @param createdAt instante en que se registró el evento
 * @param correlationId correlación con la petición que lo originó
 * @param attempts intentos de publicación fallidos hasta ahora
 */
public record OutboundEvent(UUID id, String type, int version, String aggregateId, String payloadJson,
        Instant createdAt, String correlationId, int attempts) {

    /**
     * Comprueba que están todos los componentes obligatorios.
     *
     * @throws NullPointerException si algún componente que no sea un contador es nulo (defensivo: la fila sale de la base)
     */
    public OutboundEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(correlationId, "correlationId");
    }
}
