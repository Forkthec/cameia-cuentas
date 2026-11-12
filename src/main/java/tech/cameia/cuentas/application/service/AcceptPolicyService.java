package tech.cameia.cuentas.application.service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tech.cameia.cuentas.application.command.AcceptPolicyCommand;
import tech.cameia.cuentas.domain.exception.AccountConflictException;
import tech.cameia.cuentas.domain.exception.AccountDisabledException;
import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
import tech.cameia.cuentas.domain.exception.PolicyVersionOutdatedException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.CurrentPolicy;
import tech.cameia.cuentas.domain.model.FirebaseUid;
import tech.cameia.cuentas.domain.model.PolicyAcceptance;
import tech.cameia.cuentas.domain.port.AccountRepository;

/**
 * Registra que una persona aceptó la versión vigente de la política de tratamiento de datos.
 *
 * <p>Es idempotente: aceptar la versión que la cuenta ya tiene no escribe nada y conserva la fecha de la primera
 * aceptación. No es transaccional por la misma razón que la activación: el conflicto de versión ocurre al confirmar
 * la escritura y así el servicio puede releer la cuenta para decidir qué responder. El log nombra la cuenta, nunca la
 * versión ni la fecha.</p>
 */
@Service
public class AcceptPolicyService {

    private static final Logger logger = LoggerFactory.getLogger(AcceptPolicyService.class);

    private final AccountRepository repositorio;
    private final CurrentPolicy currentPolicy;
    private final Clock clock;

    /**
     * Crea el caso de uso con el reloj del sistema en UTC.
     *
     * @param repositorio repositorio de cuentas locales
     * @param currentPolicy versión vigente de la política
     */
    @Autowired
    public AcceptPolicyService(AccountRepository repositorio, CurrentPolicy currentPolicy) {
        this(repositorio, currentPolicy, Clock.systemUTC());
    }

    /**
     * Crea el caso de uso con un reloj dado, para que las pruebas fijen el instante.
     *
     * @param repositorio repositorio de cuentas locales
     * @param currentPolicy versión vigente de la política
     * @param clock reloj que da el instante de la aceptación
     */
    public AcceptPolicyService(AccountRepository repositorio, CurrentPolicy currentPolicy, Clock clock) {
        this.repositorio = repositorio;
        this.currentPolicy = currentPolicy;
        this.clock = clock;
    }

    /**
     * Registra la aceptación de la versión vigente para la cuenta del usuario.
     *
     * @param command identificador del usuario y versión que aceptó
     * @return la cuenta con la aceptación guardada
     * @throws IdentityRequiredException si el identificador está ausente, en blanco, es más largo que el de
     *                                   Firebase o trae caracteres de control
     * @throws PolicyVersionOutdatedException si la versión enviada no es la vigente
     * @throws AccountNotFoundException si ese usuario no tiene cuenta local, o la cuenta fue anonimizada
     * @throws AccountDisabledException si la cuenta está deshabilitada
     * @throws AccountConflictException si otra petición cambió la cuenta y sigue sin tener la versión vigente
     */
    public Account accept(AcceptPolicyCommand command) {
        String firebaseUid = FirebaseUid.required(command.firebaseUid()).value();
        // La misma regla del borde, por si el caso de uso se llama sin pasar por él
        currentPolicy.requireCurrent(command.policyVersion());
        Account cuenta = repositorio.findByFirebaseUid(firebaseUid).orElseThrow(AccountNotFoundException::new);

        // El instante se recorta a microsegundos, la precisión de la columna, para que la respuesta de la primera
        // aceptación sea idéntica a la de cualquier lectura posterior.
        if (!cuenta.acceptPolicy(new PolicyAcceptance(currentPolicy.version(), clock.instant().truncatedTo(ChronoUnit.MICROS)))) {
            return cuenta; // idempotente: ya tenía la versión vigente y se conserva su fecha
        }
        try {
            Account guardada = repositorio.save(cuenta);
            logger.info("Aceptación de la política guardada para la cuenta {}", guardada.getId());
            return guardada;
        } catch (AccountConflictException conflicto) {
            return rereadAfterConflict(firebaseUid);
        }
    }

    /**
     * Resuelve un conflicto de versión: la otra petición casi siempre guardó la misma aceptación.
     *
     * @param firebaseUid identificador del usuario
     * @return la cuenta si ya tiene la versión vigente
     * @throws AccountConflictException si la cuenta sigue sin la versión vigente y conviene reintentar
     */
    private Account rereadAfterConflict(String firebaseUid) {
        Account actual = repositorio.findByFirebaseUid(firebaseUid).orElseThrow(AccountNotFoundException::new);
        if (actual.getPolicyAcceptance().version().equals(currentPolicy.version())) {
            return actual; // otra petición igual ganó: misma respuesta
        }
        throw new AccountConflictException();
    }
}
