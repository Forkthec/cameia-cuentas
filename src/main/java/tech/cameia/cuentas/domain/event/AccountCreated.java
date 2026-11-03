package tech.cameia.cuentas.domain.event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.EmailAddress;

/**
 * Hecho que se publica al crearse la fila de una cuenta: los demás servicios guardan solo lo que necesitan de él.
 *
 * @param eventId identificador único del evento, también el {@code message_id} de AMQP
 * @param firebaseUid identidad de la cuenta que comparten todos los servicios ({@code usuarioId} en el contrato)
 * @param email correo normalizado con el que se creó la credencial
 * @param birthDate fecha de nacimiento ya aceptada por la política de edad
 * @param createdAt instante UTC en que se registró el evento, truncado a milisegundos
 * @param correlationId correlación con la petición
 */
public record AccountCreated(UUID eventId, String firebaseUid, EmailAddress email, BirthDate birthDate,
        Instant createdAt, CorrelationId correlationId) {

    /** Tipo del evento en el contrato publicado. */
    public static final String TYPE = "cuenta.creada";

    /** Versión del contrato del evento. */
    public static final int VERSION = 1;

    /**
     * Comprueba que están todos los componentes y deja el instante con la precisión del contrato.
     *
     * @throws NullPointerException si algún componente es nulo (defensivo: quien llama pasa valores ya validados)
     */
    public AccountCreated {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(firebaseUid, "firebaseUid");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(birthDate, "birthDate");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(correlationId, "correlationId");
        // El contrato publica milisegundos: truncar aquí evita que lo guardado y lo publicado difieran.
        createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS);
    }
}
