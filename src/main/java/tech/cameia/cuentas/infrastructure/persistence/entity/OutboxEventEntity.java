package tech.cameia.cuentas.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la tabla {@code evento_saliente}.
 *
 * <p>No es el modelo de dominio: existe para leer la tabla de salida con JPA. No tiene métodos de escritura porque la
 * inserción y las actualizaciones van por consultas explícitas del repositorio, que son atómicas y no dependen del
 * estado cargado en memoria.</p>
 */
@Entity
@Table(name = "evento_saliente")
public class OutboxEventEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tipo", nullable = false, length = 64)
    private String type;

    @Column(name = "version", nullable = false)
    private short version;

    @Column(name = "agregado_id", nullable = false, length = 128)
    private String aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "carga", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "id_correlacion", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant createdAt;

    @Column(name = "fecha_publicacion")
    private Instant publishedAt;

    @Column(name = "intentos", nullable = false)
    private int attempts;

    /** Constructor que exige JPA; la fila no se arma a mano. */
    protected OutboxEventEntity() {
    }

    /** @return identificador del evento */
    public UUID getId() {
        return id;
    }

    /** @return tipo del evento */
    public String getType() {
        return type;
    }

    /** @return versión del contrato del evento */
    public short getVersion() {
        return version;
    }

    /** @return identificador del agregado ({@code firebase_uid} de la cuenta) */
    public String getAggregateId() {
        return aggregateId;
    }

    /** @return cuerpo JSON del evento, o {@code null} una vez publicado */
    public String getPayload() {
        return payload;
    }

    /** @return correlación con la petición que lo originó */
    public String getCorrelationId() {
        return correlationId;
    }

    /** @return instante en que se registró el evento */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @return intentos de publicación fallidos */
    public int getAttempts() {
        return attempts;
    }
}
