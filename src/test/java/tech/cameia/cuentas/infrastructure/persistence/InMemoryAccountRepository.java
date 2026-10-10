package tech.cameia.cuentas.infrastructure.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.port.AccountRepository;

/**
 * Doble de prueba del repositorio de cuentas: guarda en memoria y recuerda cada cuenta que se guardó.
 */
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> accounts = new LinkedHashMap<>();

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
     * Lista las cuentas guardadas, en el orden en que se guardaron.
     *
     * @return copia de las cuentas guardadas
     */
    public List<Account> saved() {
        return new ArrayList<>(accounts.values());
    }
}
