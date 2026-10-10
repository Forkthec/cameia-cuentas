package tech.cameia.cuentas.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.port.AccountRepository;

/**
 * Lee la cuenta de quien llama.
 *
 * <p>La identidad sale solo del encabezado que pone el Gateway: nada de la ruta, la consulta ni el
 * cuerpo decide qué cuenta se lee, de modo que nadie puede pedir la cuenta de otra persona.</p>
 */
@Service
public class GetCurrentAccountService {

    /** Largo máximo de un identificador de Firebase; es el de la columna {@code firebase_uid}. */
    static final int MAX_FIREBASE_UID_LENGTH = 128;

    private final AccountRepository repository;

    /**
     * Crea el caso de uso.
     *
     * @param repository puerto de lectura de cuentas
     */
    public GetCurrentAccountService(AccountRepository repository) {
        this.repository = repository;
    }

    /**
     * Busca la cuenta del usuario que propaga el Gateway.
     *
     * <p>Un identificador de Firebase es ASCII, así que contar unidades UTF-16 equivale a contar
     * puntos de código.</p>
     *
     * @param firebaseUid identificador del usuario que propaga el Gateway
     * @return la cuenta de ese usuario
     * @throws IdentityRequiredException si el identificador es nulo, está en blanco o es más largo que el de Firebase
     * @throws AccountNotFoundException si no hay cuenta o la cuenta fue anonimizada
     */
    @Transactional(readOnly = true)
    public Account find(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank() || firebaseUid.length() > MAX_FIREBASE_UID_LENGTH) {
            throw new IdentityRequiredException();
        }
        // Una cuenta anonimizada ya no tiene datos personales que mostrar: para quien llama, no existe.
        return repository.findByFirebaseUid(firebaseUid)
                .filter(account -> account.getStatus() != AccountStatus.ANONYMIZED)
                .orElseThrow(AccountNotFoundException::new);
    }
}
