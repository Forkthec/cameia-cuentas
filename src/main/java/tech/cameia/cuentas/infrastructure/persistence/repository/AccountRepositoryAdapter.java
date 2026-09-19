package tech.cameia.cuentas.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.port.AccountRepository;
import tech.cameia.cuentas.infrastructure.persistence.entity.AccountEntity;
import tech.cameia.cuentas.infrastructure.persistence.mapper.AccountMapper;

/**
 * Implementa el puerto {@code AccountRepository} sobre PostgreSQL.
 *
 * <p>Traduce el agregado a una fila, delega en Spring Data y devuelve siempre objetos del
 * dominio: ninguna entidad de JPA sale de este paquete.</p>
 */
@Repository
public class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository repositorio;
    private final AccountMapper mapeador;

    /**
     * Crea el adaptador.
     *
     * @param repositorio repositorio de Spring Data sobre la tabla {@code cuenta}
     * @param mapeador traductor entre el dominio y la entidad
     */
    public AccountRepositoryAdapter(AccountJpaRepository repositorio, AccountMapper mapeador) {
        this.repositorio = repositorio;
        this.mapeador = mapeador;
    }

    /**
     * Guarda la cuenta.
     *
     * <p>Si ya existe una fila para ese usuario, actualiza su estado en lugar de insertar
     * otra: la unicidad de {@code firebase_uid} la protege la base de datos, y esta ruta
     * evita chocar contra ella al activar una cuenta.</p>
     *
     * @param account cuenta a persistir
     * @return la cuenta tal como quedó almacenada
     */
    @Override
    public Account save(Account account) {
        AccountEntity entidad = repositorio.findByFirebaseUid(account.getFirebaseUid())
                .map(existente -> {
                    existente.setEstado(account.getStatus());
                    return existente;
                })
                .orElseGet(() -> mapeador.toEntity(account));

        return mapeador.toDomain(repositorio.save(entidad));
    }

    /**
     * Busca la cuenta de un usuario de Firebase.
     *
     * @param firebaseUid identificador del usuario en Firebase Auth
     * @return la cuenta, o vacío si ese usuario no tiene cuenta local
     */
    @Override
    public Optional<Account> findByFirebaseUid(String firebaseUid) {
        return repositorio.findByFirebaseUid(firebaseUid).map(mapeador::toDomain);
    }
}
