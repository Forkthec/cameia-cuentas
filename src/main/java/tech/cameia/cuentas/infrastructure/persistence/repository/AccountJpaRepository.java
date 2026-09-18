package tech.cameia.cuentas.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
