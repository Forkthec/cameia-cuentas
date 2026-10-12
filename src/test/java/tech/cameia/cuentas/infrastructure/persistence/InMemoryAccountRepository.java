package tech.cameia.cuentas.infrastructure.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private final Map<String, Instant> creationDates = new LinkedHashMap<>();
    private Boolean forcedDeleteResult;
    private Instant requestedLimit;
    private int requestedMax;

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
     * Registra la fecha de creación de una cuenta guardada, para que {@link #findPendingVerificationCreatedBefore} la
     * considere.
     *
     * @param firebaseUid identidad de Firebase
     * @param createdAt instante de creación
     */
    public void seedCreatedAt(String firebaseUid, Instant createdAt) {
        creationDates.put(firebaseUid, createdAt);
    }

    /**
     * Devuelve los usuarios pendientes creados antes del límite, del más antiguo al más nuevo.
     *
     * @param limit instante, exclusivo
     * @param max cantidad máxima
     * @return los identificadores, como máximo {@code max}
     */
    @Override
    public List<String> findPendingVerificationCreatedBefore(Instant limit, int max) {
        requestedLimit = limit;
        requestedMax = max;
        return accounts.values().stream()
                .filter(account -> account.getStatus() == AccountStatus.PENDING_VERIFICATION)
                .filter(account -> creationDates.containsKey(account.getFirebaseUid())
                        && creationDates.get(account.getFirebaseUid()).isBefore(limit))
                .sorted(Comparator.comparing(account -> creationDates.get(account.getFirebaseUid())))
                .map(Account::getFirebaseUid)
                .limit(max)
                .toList();
    }

    /**
     * Indica cuáles de los usuarios indicados tienen cuenta guardada.
     *
     * @param firebaseUids identificadores a comprobar
     * @return los que tienen cuenta, en cualquier estado
     */
    @Override
    public Set<String> findExistingFirebaseUids(Collection<String> firebaseUids) {
        Set<String> existing = new HashSet<>(accounts.keySet());
        existing.retainAll(firebaseUids);
        return existing;
    }

    /** @return el límite de antigüedad de la última lectura de candidatas, o nulo si no hubo ninguna */
    public Instant requestedLimit() {
        return requestedLimit;
    }

    /** @return el máximo pedido en la última lectura de candidatas */
    public int requestedMax() {
        return requestedMax;
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
