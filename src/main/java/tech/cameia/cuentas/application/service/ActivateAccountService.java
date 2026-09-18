package tech.cameia.cuentas.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.cuentas.application.command.ActivateAccountCommand;
import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.EmailNotVerifiedException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.port.AccountRepository;
import tech.cameia.cuentas.domain.port.FirebaseUserDirectory;

/**
 * Activa una cuenta cuando su correo queda verificado.
 *
 * <p>La prueba de que el correo está verificado la da Firebase, nunca el navegador. El
 * Gateway puede adelantar ese dato en un encabezado, pero si no llega o dice que no, el
 * servicio lo consulta al directorio antes de rechazar: así la operación funciona aunque
 * el Gateway todavía no propague el encabezado.</p>
 */
@Service
public class ActivateAccountService {

    private static final Logger logger = LoggerFactory.getLogger(ActivateAccountService.class);

    private final AccountRepository repositorio;
    private final FirebaseUserDirectory directorio;

    /**
     * Crea el caso de uso.
     *
     * @param repositorio repositorio de cuentas locales
     * @param directorio directorio de usuarios que certifica la verificación
     */
    public ActivateAccountService(AccountRepository repositorio, FirebaseUserDirectory directorio) {
        this.repositorio = repositorio;
        this.directorio = directorio;
    }

    /**
     * Activa la cuenta del usuario indicado.
     *
     * <p>Es idempotente: si la cuenta ya está activa, devuelve su estado sin tocar nada.</p>
     *
     * @param command identificador del usuario y lo que el Gateway afirma sobre su correo
     * @return la cuenta, ya activa
     * @throws AccountNotFoundException si ese usuario no tiene cuenta local
     * @throws EmailNotVerifiedException si el correo sigue sin verificarse
     * @throws IllegalStateException si la cuenta está bloqueada o anonimizada
     */
    @Transactional
    public Account activate(ActivateAccountCommand command) {
        Account cuenta = repositorio.findByFirebaseUid(command.firebaseUid())
                .orElseThrow(AccountNotFoundException::new);

        if (!estaVerificado(command)) {
            throw new EmailNotVerifiedException();
        }

        if (cuenta.getStatus() == AccountStatus.ACTIVE) {
            return cuenta;
        }

        // Cualquier otro estado lo decide el agregado: una cuenta bloqueada o anonimizada
        // no se reactiva por verificar un correo, y salir en silencio aquí haría creer al
        // frontend que quedó activa.
        cuenta.activate();
        Account activada = repositorio.save(cuenta);
        logger.info("Cuenta activada tras verificar el correo del usuario {}", command.firebaseUid());
        return activada;
    }

    /**
     * Determina si el correo está verificado.
     *
     * <p>Confía en el encabezado solo cuando afirma que sí: un {@code true} solo puede
     * venir del Gateway, que ya validó el token. Cualquier otro caso se consulta a
     * Firebase, porque la ausencia del encabezado no prueba que el correo no esté
     * verificado.</p>
     *
     * @param command datos recibidos del Gateway
     * @return {@code true} si el correo está verificado
     */
    private boolean estaVerificado(ActivateAccountCommand command) {
        if (Boolean.TRUE.equals(command.emailVerifiedByGateway())) {
            return true;
        }
        return directorio.isEmailVerified(command.firebaseUid());
    }
}
