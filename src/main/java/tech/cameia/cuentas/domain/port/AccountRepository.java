package tech.cameia.cuentas.domain.port;

import java.util.Optional;

import tech.cameia.cuentas.domain.model.Account;

/**
 * Acceso a las cuentas almacenadas.
 *
 * <p>Es un puerto: el dominio declara lo que necesita y la infraestructura lo implementa
 * sobre PostgreSQL. Gracias a eso, las reglas de negocio se prueban sin base de datos.</p>
 */
public interface AccountRepository {

    /**
     * Guarda la cuenta, sea nueva o modificada.
     *
     * @param account cuenta a persistir
     * @return la cuenta tal como quedó almacenada
     */
    Account save(Account account);

    /**
     * Busca la cuenta de un usuario de Firebase.
     *
     * @param firebaseUid identificador del usuario en Firebase Auth
     * @return la cuenta, o vacío si ese usuario no tiene cuenta local
     */
    Optional<Account> findByFirebaseUid(String firebaseUid);
}
