package tech.cameia.cuentas.domain.port;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.UnannouncedAccount;

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

    /**
     * Lista las cuentas que no tienen evento de cuenta creada.
     *
     * @param limit cantidad máxima de cuentas
     * @return las cuentas que no están anonimizadas, tienen fecha de nacimiento y no tienen {@code cuenta.creada} en la tabla
     *         de salida, la más antigua primero, como máximo {@code limit}
     */
    List<UnannouncedAccount> findUnannounced(int limit);

    /**
     * Borra la cuenta del usuario solo si sigue sin verificar.
     *
     * <p>Debe llamarse dentro de una transacción: el servicio que la abre decide qué más ocurre con el borrado.</p>
     *
     * @param firebaseUid identificador del usuario en Firebase Auth
     * @return {@code true} si borró una fila; {@code false} si no existía o ya no estaba pendiente
     */
    boolean deletePendingByFirebaseUid(String firebaseUid);

    /**
     * Lista los usuarios cuya cuenta lleva sin verificar desde antes de un instante.
     *
     * @param limit instante, exclusivo: una cuenta creada exactamente en él no entra
     * @param max cantidad máxima de usuarios
     * @return los {@code firebaseUid} de las cuentas {@code PENDING_VERIFICATION} creadas antes de {@code limit}, de la más
     *         antigua a la más nueva, como máximo {@code max}
     */
    List<String> findPendingVerificationCreatedBefore(Instant limit, int max);

    /**
     * Indica cuáles de los usuarios indicados tienen una cuenta local, en cualquier estado.
     *
     * @param firebaseUids identificadores a comprobar
     * @return los que tienen fila en la tabla de cuentas
     */
    Set<String> findExistingFirebaseUids(Collection<String> firebaseUids);
}
