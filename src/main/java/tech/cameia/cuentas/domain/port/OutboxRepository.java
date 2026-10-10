package tech.cameia.cuentas.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import tech.cameia.cuentas.domain.event.AccountCreated;
import tech.cameia.cuentas.domain.event.OutboundEvent;

/**
 * Tabla de salida (outbox): eventos que se guardan junto con el cambio que los origina y se publican después.
 *
 * <p>Guardar el evento en la misma transacción que la cuenta evita que un fallo del broker pierda eventos; un relevo
 * los publica más tarde y, al confirmarse, borra la carga para no conservar datos personales de más.</p>
 */
public interface OutboxRepository {

    /**
     * Guarda el evento de cuenta creada si la cuenta aún no tiene uno.
     *
     * @param event evento a guardar
     * @return {@code true} si se guardó; {@code false} si la cuenta ya tiene un evento de ese tipo
     */
    boolean appendAccountCreated(AccountCreated event);

    /**
     * Lista los eventos pendientes de publicar.
     *
     * @param limit cantidad máxima de eventos
     * @return los pendientes más antiguos primero (por instante de creación y luego por id), como máximo {@code limit}
     */
    List<OutboundEvent> findPending(int limit);

    /**
     * Busca un evento que siga pendiente.
     *
     * @param id identificador del evento
     * @return el evento si existe y sigue pendiente
     */
    Optional<OutboundEvent> findPendingById(UUID id);

    /**
     * Marca el evento como publicado y borra su carga.
     *
     * @param id identificador del evento
     * @param publishedAt instante en que el broker confirmó la publicación
     * @return {@code true} si seguía pendiente; {@code false} si ya estaba publicado o no existe
     */
    boolean markPublished(UUID id, Instant publishedAt);

    /**
     * Suma un intento fallido a un evento pendiente; no hace nada si ya se publicó.
     *
     * @param id identificador del evento
     */
    void recordFailedAttempt(UUID id);

    /**
     * Cuenta los eventos que siguen sin publicar.
     *
     * @return cantidad de eventos pendientes
     */
    long countPending();
}
