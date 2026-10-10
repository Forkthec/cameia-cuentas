package tech.cameia.cuentas.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.cuentas.domain.event.AccountCreated;
import tech.cameia.cuentas.domain.event.OutboundEvent;
import tech.cameia.cuentas.domain.port.OutboxRepository;
import tech.cameia.cuentas.infrastructure.messaging.payload.AccountCreatedPayloadV1;
import tech.cameia.cuentas.infrastructure.persistence.entity.OutboxEventEntity;
import tools.jackson.databind.json.JsonMapper;

/**
 * Implementa el puerto {@code OutboxRepository} sobre PostgreSQL.
 *
 * <p>El relevo no puede mantener una transacción abierta mientras espera al broker, así que cada escritura abre aquí su
 * propia transacción corta. Cuando la llama el servicio de registro, en cambio, se une a la transacción de la cuenta:
 * así la cuenta y su evento se confirman o se deshacen juntos.</p>
 */
@Repository
public class OutboxRepositoryAdapter implements OutboxRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final OutboxEventJpaRepository repository;

    /**
     * Crea el adaptador.
     *
     * @param repository repositorio de Spring Data sobre la tabla {@code evento_saliente}
     */
    public OutboxRepositoryAdapter(OutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * Serializa y guarda el evento si la cuenta no tiene uno.
     *
     * @param event evento a guardar
     * @return {@code true} si se guardó; {@code false} si la cuenta ya tenía su evento
     * @throws tools.jackson.core.JacksonException si la carga no se pudo serializar (defensivo: solo contiene texto)
     */
    @Override
    @Transactional
    public boolean appendAccountCreated(AccountCreated event) {
        String payload = JSON.writeValueAsString(AccountCreatedPayloadV1.from(event));
        return repository.insertIfAbsent(event.eventId(), AccountCreated.TYPE, (short) AccountCreated.VERSION,
                event.firebaseUid(), payload, event.correlationId().value(), event.createdAt()) == 1;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<OutboundEvent> findPending(int limit) {
        return repository.findPending(Limit.of(limit)).stream().map(OutboxRepositoryAdapter::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<OutboundEvent> findPendingById(UUID id) {
        return repository.findByIdAndPublishedAtIsNull(id).map(OutboxRepositoryAdapter::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public boolean markPublished(UUID id, Instant publishedAt) {
        return repository.markPublished(id, publishedAt) == 1;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void recordFailedAttempt(UUID id) {
        repository.incrementAttempts(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public long countPending() {
        return repository.countByPublishedAtIsNull();
    }

    private static OutboundEvent toDomain(OutboxEventEntity entity) {
        return new OutboundEvent(entity.getId(), entity.getType(), entity.getVersion(), entity.getAggregateId(),
                entity.getPayload(), entity.getCreatedAt(), entity.getCorrelationId(), entity.getAttempts());
    }
}
