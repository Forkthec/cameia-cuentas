package tech.cameia.cuentas.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tech.cameia.cuentas.application.command.ActivateAccountCommand;
import tech.cameia.cuentas.domain.exception.AccountConflictException;
import tech.cameia.cuentas.domain.exception.AccountDisabledException;
import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.EmailNotVerifiedException;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.FirebaseUid;
import tech.cameia.cuentas.domain.port.AccountRepository;

/**
 * Activa una cuenta cuando su correo queda verificado.
 *
 * <p>La verificación sale solo del encabezado que escribe el Gateway a partir del token ya
 * validado: el servicio no consulta a Firebase ni confía en el navegador. El estado de la cuenta decide primero (activa:
 * 200; deshabilitada: 403; anonimizada: igual que una cuenta inexistente) y la afirmación del Gateway solo cuenta para
 * la cuenta pendiente: así una cuenta anonimizada no se distingue de una inexistente y quien repite la llamada con un
 * token anterior a la verificación no recibe un rechazo por una cuenta que ya está activa. No es transaccional
 * porque el conflicto de versión ocurre al confirmar la escritura y así el servicio puede
 * releer la cuenta para decidir qué responder.</p>
 */
@Service
public class ActivateAccountService {

    private static final Logger logger = LoggerFactory.getLogger(ActivateAccountService.class);

    private final AccountRepository repositorio;

    /**
     * Crea el caso de uso.
     *
     * @param repositorio repositorio de cuentas locales
     */
    public ActivateAccountService(AccountRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Activa la cuenta del usuario indicado.
     *
     * <p>Es idempotente: si la cuenta ya está activa, devuelve su estado sin escribir nada.</p>
     *
     * @param command identificador del usuario y lo que el Gateway afirma sobre su correo
     * @return la cuenta, ya activa
     * @throws IdentityRequiredException si el identificador está ausente, en blanco, es más largo que
     *                                   el de Firebase o trae caracteres de control
     * @throws AccountNotFoundException si ese usuario no tiene cuenta local, o la cuenta fue anonimizada
     * @throws EmailNotVerifiedException si la cuenta está pendiente y el Gateway no afirmó que el correo está verificado
     * @throws AccountDisabledException si la cuenta está deshabilitada
     * @throws AccountConflictException si otra petición cambió la cuenta y sigue sin estar activa
     */
    public Account activate(ActivateAccountCommand command) {
        String firebaseUid = FirebaseUid.required(command.firebaseUid()).value(); // misma regla que la consulta de la cuenta
        Account cuenta = repositorio.findByFirebaseUid(firebaseUid).orElseThrow(AccountNotFoundException::new);
        if (cuenta.getStatus() == AccountStatus.ACTIVE) {
            return cuenta; // idempotente: no se escribe, con o sin la afirmación del Gateway
        }
        if (cuenta.getStatus() == AccountStatus.PENDING_VERIFICATION && !command.emailVerified()) {
            throw new EmailNotVerifiedException(); // la verificación sale solo del token y solo importa a la pendiente
        }
        cuenta.activate(); // deshabilitada y anonimizada lanzan su excepción, también sin la afirmación del Gateway
        try {
            Account activada = repositorio.save(cuenta);
            logger.info("Cuenta activada tras verificar el correo del usuario {}", firebaseUid);
            return activada;
        } catch (AccountConflictException conflicto) {
            return rereadAfterConflict(firebaseUid);
        }
    }

    /**
     * Resuelve un conflicto de versión: la otra petición casi siempre ya activó la cuenta.
     *
     * @param firebaseUid identificador del usuario
     * @return la cuenta si ya está activa
     * @throws AccountConflictException si la cuenta sigue sin estar activa y conviene reintentar
     */
    private Account rereadAfterConflict(String firebaseUid) {
        Account actual = repositorio.findByFirebaseUid(firebaseUid).orElseThrow(AccountNotFoundException::new);
        if (actual.getStatus() == AccountStatus.ACTIVE) {
            return actual;
        }
        throw new AccountConflictException();
    }
}
