package tech.cameia.cuentas.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.FirebaseUid;
import tech.cameia.cuentas.domain.port.AccountRepository;

/**
 * Lee la cuenta de quien llama.
 *
 * <p>La identidad sale solo del encabezado que pone el Gateway: nada de la ruta, la consulta ni el
 * cuerpo decide qué cuenta se lee, de modo que nadie puede pedir la cuenta de otra persona.</p>
 */
@Service
public class GetCurrentAccountService {

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
     * @param firebaseUid identificador del usuario que propaga el Gateway
     * @return la cuenta de ese usuario
     * @throws IdentityRequiredException si el identificador es nulo, está en blanco, es más largo que el de Firebase o trae caracteres de control
     * @throws AccountNotFoundException si no hay cuenta o la cuenta fue anonimizada
     */
    @Transactional(readOnly = true)
    public Account find(String firebaseUid) {
        FirebaseUid uid = FirebaseUid.required(firebaseUid);
        // Una cuenta anonimizada ya no tiene datos personales que mostrar: para quien llama, no existe.
        return repository.findByFirebaseUid(uid.value())
                .filter(account -> account.getStatus() != AccountStatus.ANONYMIZED)
                .orElseThrow(AccountNotFoundException::new);
    }
}
