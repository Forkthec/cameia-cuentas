package tech.cameia.cuentas.domain.event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Hecho que se publica al borrarse la fila de una cuenta que nunca verificó su correo: los demás servicios descartan
 * lo que guardaban de ella.
 *
 * @param eventId identificador único del evento, también el {@code message_id} de AMQP
 * @param firebaseUid identidad de la cuenta que comparten todos los servicios ({@code usuarioId} en el contrato)
 * @param deletedAt instante UTC en que se borró la cuenta, truncado a milisegundos
 * @param correlationId correlación del evento; la purga no tiene petición HTTP, así que usa el propio {@code eventId}
 */
public record AccountDeleted(UUID eventId, String firebaseUid, Instant deletedAt, CorrelationId correlationId) {

    /** Tipo del evento en el contrato publicado. */
    public static final String TYPE = "cuenta.eliminada";

    /** Versión del contrato del evento. */
    public static final int VERSION = 1;

    /**
     * Comprueba que están todos los componentes y deja el instante con la precisión del contrato.
     *
     * @throws NullPointerException si algún componente es nulo (defensivo: quien llama pasa valores ya validados)
     */
    public AccountDeleted {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(firebaseUid, "firebaseUid");
        Objects.requireNonNull(deletedAt, "deletedAt");
        Objects.requireNonNull(correlationId, "correlationId");
        // El contrato publica milisegundos: truncar aquí evita que lo guardado y lo publicado difieran.
        deletedAt = deletedAt.truncatedTo(ChronoUnit.MILLIS);
    }
}
