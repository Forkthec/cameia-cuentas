package tech.cameia.cuentas.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tech.cameia.cuentas.infrastructure.persistence.entity.OutboxEventEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code evento_saliente}.
 *
 * <p>Pieza interna de la infraestructura: el resto del microservicio usa el puerto {@code OutboxRepository}.</p>
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Inserta el evento si la cuenta aún no tiene uno de ese tipo; la restricción única decide, así que dos registros
     * simultáneos no producen un error sino una fila afectada.
     *
     * @param id identificador del evento
     * @param type tipo del evento
     * @param version versión del contrato
     * @param aggregateId identificador del agregado
     * @param payload cuerpo JSON
     * @param correlationId correlación con la petición
     * @param createdAt instante de creación
     * @return 1 si insertó, 0 si ya existía
     */
    @Modifying
    @Query(value = """
            INSERT INTO microcuentas.evento_saliente
                (id, tipo, version, agregado_id, carga, id_correlacion, fecha_creacion, intentos)
            VALUES (:id, :type, :version, :aggregateId, CAST(:payload AS jsonb), :correlationId, :createdAt, 0)
            ON CONFLICT ON CONSTRAINT uq_evento_saliente_tipo_agregado DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("type") String type, @Param("version") short version,
            @Param("aggregateId") String aggregateId, @Param("payload") String payload,
            @Param("correlationId") String correlationId, @Param("createdAt") Instant createdAt);

    /**
     * Lista los pendientes, del más antiguo al más reciente.
     *
     * @param limit cantidad máxima de filas
     * @return los pendientes
     */
    @Query("select e from OutboxEventEntity e where e.publishedAt is null order by e.createdAt, e.id")
    List<OutboxEventEntity> findPending(Limit limit);

    /**
     * Busca un evento que siga pendiente.
     *
     * @param id identificador del evento
     * @return la fila, o vacío si no existe o ya se publicó
     */
    Optional<OutboxEventEntity> findByIdAndPublishedAtIsNull(UUID id);

    /**
     * Marca el evento como publicado y borra su carga.
     *
     * @param id identificador del evento
     * @param at instante de la confirmación
     * @return 1 si seguía pendiente, 0 si no
     */
    @Modifying
    @Query("update OutboxEventEntity e set e.publishedAt = :at, e.payload = null where e.id = :id and e.publishedAt is null")
    int markPublished(@Param("id") UUID id, @Param("at") Instant at);

    /**
     * Suma un intento fallido a un evento pendiente.
     *
     * @param id identificador del evento
     * @return 1 si seguía pendiente, 0 si no
     */
    @Modifying
    @Query("update OutboxEventEntity e set e.attempts = e.attempts + 1 where e.id = :id and e.publishedAt is null")
    int incrementAttempts(@Param("id") UUID id);

    /**
     * Cuenta los eventos pendientes.
     *
     * @return cantidad de pendientes
     */
    long countByPublishedAtIsNull();
}
