package tech.cameia.cuentas.infrastructure.persistence.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tech.cameia.cuentas.infrastructure.persistence.entity.AccountEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code cuenta}.
 *
 * <p>Es una pieza interna de la infraestructura: el resto del microservicio usa el puerto
 * {@code AccountRepository}, no esta interfaz.</p>
 */
public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {

    /**
     * Busca la fila de un usuario de Firebase.
     *
     * @param firebaseUid identificador del usuario en Firebase Auth
     * @return la fila, o vacío si no existe
     */
    Optional<AccountEntity> findByFirebaseUid(String firebaseUid);

    /**
     * Lista las cuentas que no tienen evento de cuenta creada, la más antigua primero.
     *
     * <p>Excluye las anonimizadas y las que no tienen fecha de nacimiento, porque el evento las exige. La consulta es nativa
     * para nombrar el esquema y expresar la ausencia con {@code NOT EXISTS}, que usa el índice único de la tabla de salida.</p>
     *
     * @param limit cantidad máxima de filas
     * @return proyecciones con los tres datos que necesita el evento
     */
    @Query(value = """
            SELECT c.firebase_uid AS firebaseUid, c.fecha_nacimiento AS fechaNacimiento, c.fecha_creacion AS fechaCreacion
            FROM microcuentas.cuenta c
            WHERE c.estado <> 'ANONYMIZED' AND c.fecha_nacimiento IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM microcuentas.evento_saliente e
                              WHERE e.tipo = 'cuenta.creada' AND e.agregado_id = c.firebase_uid)
            ORDER BY c.fecha_creacion, c.id
            LIMIT :limit
            """, nativeQuery = true)
    List<UnannouncedProjection> findUnannounced(@Param("limit") int limit);

    /** Proyección de la consulta de cuentas sin evento. */
    interface UnannouncedProjection {

        /**
         * @return identificador del usuario en Firebase Auth
         */
        String getFirebaseUid();

        /**
         * @return fecha de nacimiento de la cuenta
         */
        LocalDate getFechaNacimiento();

        /**
         * @return instante de creación de la cuenta
         */
        Instant getFechaCreacion();
    }
}
