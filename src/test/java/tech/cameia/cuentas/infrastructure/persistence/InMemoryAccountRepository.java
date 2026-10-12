package tech.cameia.cuentas.infrastructure.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.UnannouncedAccount;
import tech.cameia.cuentas.domain.port.AccountRepository;

/**
 * Doble de prueba del repositorio de cuentas: guarda en memoria y recuerda cada cuenta que se guardó.
 */
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final List<UnannouncedAccount> unannounced = new ArrayList<>();
    private Boolean forcedDeleteResult;

    /**
     * Guarda la cuenta en memoria.
     *
     * @param account cuenta a guardar
     * @return la misma cuenta
     */
    @Override
    public Account save(Account account) {
        accounts.put(account.getFirebaseUid(), account);
        return account;
    }

    /**
     * Busca una cuenta por su identidad de Firebase.
     *
     * @param firebaseUid identidad de Firebase
     * @return la cuenta, o vacío si no existe
     */
    @Override
    public Optional<Account> findByFirebaseUid(String firebaseUid) {
        return Optional.ofNullable(accounts.get(firebaseUid));
    }

    /**
     * Borra la cuenta si está pendiente de verificar, salvo que se haya pedido simular que ya no lo está.
     *
     * @param firebaseUid identidad de Firebase
     * @return {@code true} si borró la cuenta
     */
    @Override
    public boolean deletePendingByFirebaseUid(String firebaseUid) {
        Account account = accounts.get(firebaseUid);
        if (forcedDeleteResult != null) {
            boolean forced = forcedDeleteResult;
            forcedDeleteResult = null;
            return forced && accounts.remove(firebaseUid) != null;
        }
        if (account == null || account.getStatus() != AccountStatus.PENDING_VERIFICATION) {
            return false;
        }
        accounts.remove(firebaseUid);
        return true;
    }

    /**
     * Hace que el próximo borrado responda {@code false} sin tocar la cuenta, como si otra petición la hubiera activado
     * entre la decisión y el borrado.
     */
    public void failNextDeleteAsNoLongerPending() {
        forcedDeleteResult = Boolean.FALSE;
    }

    /**
     * Lista las cuentas guardadas, en el orden en que se guardaron.
     *
     * @return copia de las cuentas guardadas
     */
    public List<Account> saved() {
        return new ArrayList<>(accounts.values());
    }

    /**
     * Prepara las cuentas que {@link #findUnannounced} devolverá.
     *
     * @param account cuenta sin evento
     */
    public void seedUnannounced(UnannouncedAccount account) {
        unannounced.add(account);
    }

    /**
     * Devuelve las cuentas sin evento preparadas con {@link #seedUnannounced}, como máximo {@code limit}.
     *
     * @param limit cantidad máxima de cuentas
     * @return copia de las cuentas preparadas
     */
    @Override
    public List<UnannouncedAccount> findUnannounced(int limit) {
        return unannounced.stream().limit(limit).toList();
    }
}
