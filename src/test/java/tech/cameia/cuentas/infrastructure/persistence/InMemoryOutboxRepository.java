package tech.cameia.cuentas.infrastructure.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import tech.cameia.cuentas.domain.event.AccountCreated;
import tech.cameia.cuentas.domain.event.OutboundEvent;
import tech.cameia.cuentas.domain.port.OutboxRepository;

/**
 * Doble de prueba de la tabla de salida: impone «un evento por cuenta», como la restricción única de la base, y permite
 * simular un fallo al guardar.
 */
public class InMemoryOutboxRepository implements OutboxRepository {

    private final Map<String, AccountCreated> appended = new LinkedHashMap<>();
    private final Map<UUID, OutboundEvent> pending = new LinkedHashMap<>();
    private final Map<UUID, Instant> publishedTimes = new LinkedHashMap<>();
    private boolean failNextAppend;

    /**
     * Agrega un evento pendiente tal cual, sin pasar por la cuenta que lo origina.
     *
     * @param event evento pendiente
     * @return identificador del evento
     */
    public UUID seed(OutboundEvent event) {
        pending.put(event.id(), event);
        return event.id();
    }

    /**
     * Instante en que se marcó publicado un evento.
     *
     * @param id identificador del evento
     * @return el instante, o {@code null} si no se ha marcado
     */
    public Instant publishedAt(UUID id) {
        return publishedTimes.get(id);
    }

    /**
     * Hace que el próximo {@link #appendAccountCreated} lance una excepción.
     */
    public void failNextAppend() {
        this.failNextAppend = true;
    }

    /**
     * Guarda el evento si la cuenta no tiene uno.
     *
     * @param event evento a guardar
     * @return {@code true} si se guardó; {@code false} si la cuenta ya tenía su evento
     * @throws IllegalStateException si se pidió simular un fallo
     */
    @Override
    public boolean appendAccountCreated(AccountCreated event) {
        if (failNextAppend) {
            failNextAppend = false;
            throw new IllegalStateException("fallo simulado");
        }
        if (appended.containsKey(event.firebaseUid())) {
            return false;
        }
        appended.put(event.firebaseUid(), event);
        pending.put(event.eventId(), new OutboundEvent(event.eventId(), AccountCreated.TYPE, AccountCreated.VERSION,
                event.firebaseUid(), "{}", event.createdAt(), event.correlationId().value(), 0));
        return true;
    }

    /**
     * Lista los eventos guardados, en el orden en que se guardaron.
     *
     * @return copia de los eventos guardados
     */
    public List<AccountCreated> appended() {
        return new ArrayList<>(appended.values());
    }

    /**
     * Lista los eventos pendientes de publicar.
     *
     * @return copia de los pendientes
     */
    public List<OutboundEvent> pending() {
        return new ArrayList<>(pending.values());
    }

    /** {@inheritDoc} */
    @Override
    public List<OutboundEvent> findPending(int limit) {
        return pending.values().stream()
                .sorted(Comparator.comparing(OutboundEvent::createdAt).thenComparing(OutboundEvent::id))
                .limit(limit).toList();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OutboundEvent> findPendingById(UUID id) {
        return Optional.ofNullable(pending.get(id));
    }

    /** {@inheritDoc} */
    @Override
    public boolean markPublished(UUID id, Instant publishedAt) {
        boolean wasPending = pending.remove(id) != null;
        if (wasPending) {
            publishedTimes.put(id, publishedAt);
        }
        return wasPending;
    }

    /** {@inheritDoc} */
    @Override
    public void recordFailedAttempt(UUID id) {
        OutboundEvent event = pending.get(id);
        if (event != null) {
            pending.put(id, new OutboundEvent(event.id(), event.type(), event.version(), event.aggregateId(),
                    event.payloadJson(), event.createdAt(), event.correlationId(), event.attempts() + 1));
        }
    }

    /** {@inheritDoc} */
    @Override
    public long countPending() {
        return pending.size();
    }
}
