package tech.cameia.cuentas.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.PurgeSummary;
import tech.cameia.cuentas.domain.model.UserVerificationStatus;
import tech.cameia.cuentas.domain.port.AccountRepository;
import tech.cameia.cuentas.domain.port.FirebaseUserDirectory;

/**
 * Elimina las cuentas que no verificaron su correo en 168 horas.
 *
 * <p>Se ejecuta una vez por invocación; la hora la decide quien la dispara. Antes de borrar consulta el directorio: si la
 * persona ya verificó, la cuenta se activa. Si no, se borra la credencial y después la fila, que lleva su evento. Cada cuenta
 * es independiente: un rechazo se cuenta y no detiene a las demás. Si el directorio no está disponible, se detiene y
 * lanza, para que la tarea termine con error y se reintente. Al final concilia las credenciales huérfanas del registro.</p>
 *
 * <p>No es transaccional: un fallo en la cuenta 500 no debe deshacer las 499 anteriores. La única transacción es la de cada
 * borrado de fila con su evento, que vive en {@link PendingAccountDeletionService}.</p>
 */
@Service
public class PurgeUnverifiedAccountsService {

    /** Antigüedad a partir de la cual una cuenta sin verificar se purga. */
    static final Duration MAX_AGE = Duration.ofHours(168);

    private static final Logger logger = LoggerFactory.getLogger(PurgeUnverifiedAccountsService.class);

    private final AccountRepository accounts;
    private final FirebaseUserDirectory directory;
    private final PendingAccountDeletionService deletion;
    private final int maxAccountsPerRun;
    private final Clock clock;

    /**
     * Crea el servicio con el reloj UTC del sistema.
     *
     * @param accounts repositorio de cuentas
     * @param directory directorio de usuarios (Firebase)
     * @param deletion borrado de la fila con su evento
     * @param maxAccountsPerRun cuentas como máximo por ejecución; su rango lo valida {@code PurgeProperties} al arrancar
     */
    @Autowired
    public PurgeUnverifiedAccountsService(AccountRepository accounts, FirebaseUserDirectory directory,
            PendingAccountDeletionService deletion, @Value("${cuentas.purge.max-accounts-per-run}") int maxAccountsPerRun) {
        this(accounts, directory, deletion, maxAccountsPerRun, Clock.systemUTC());
    }

    /**
     * Crea el servicio con un reloj propio, para fijar la hora en las pruebas.
     *
     * @param accounts repositorio de cuentas
     * @param directory directorio de usuarios (Firebase)
     * @param deletion borrado de la fila con su evento
     * @param maxAccountsPerRun cuentas como máximo por ejecución
     * @param clock reloj que fija el límite de antigüedad
     */
    public PurgeUnverifiedAccountsService(AccountRepository accounts, FirebaseUserDirectory directory,
            PendingAccountDeletionService deletion, int maxAccountsPerRun, Clock clock) {
        this.accounts = accounts;
        this.directory = directory;
        this.deletion = deletion;
        this.maxAccountsPerRun = maxAccountsPerRun;
        this.clock = clock;
    }

    /**
     * Purga las cuentas sin verificar de más de 168 horas, hasta el tope por ejecución.
     *
     * @return cuántas cuentas se revisaron, borraron, activaron y fallaron, y si quedan candidatas
     * @throws DependencyUnavailableException si el directorio de usuarios no respondió; las cuentas que faltan no se
     *         cuentan como fallidas y la siguiente ejecución las retoma
     */
    public PurgeSummary purge() {
        Instant limit = clock.instant().minus(MAX_AGE);
        // Se pide una de más: si llega, hay candidatas que esta ejecución no procesa, sin una consulta de conteo aparte.
        List<String> candidates = accounts.findPendingVerificationCreatedBefore(limit, maxAccountsPerRun + 1);
        boolean candidatesLeft = candidates.size() > maxAccountsPerRun;
        Totals totals = new Totals();
        try {
            for (String firebaseUid : candidates.subList(0, Math.min(candidates.size(), maxAccountsPerRun))) {
                totals.reviewed++;
                totals.add(process(firebaseUid));
            }
            warnIfEveryAccountFailed(totals);
            reconcileOrphanCredentials(limit, totals);
        } catch (DependencyUnavailableException unavailable) {
            logSummary(totals.summary(true));
            throw unavailable;
        }
        PurgeSummary summary = totals.summary(candidatesLeft);
        logSummary(summary);
        return summary;
    }

    /**
     * Deja un ERROR cuando ninguna cuenta revisada se pudo procesar.
     *
     * <p>Un rechazo aislado se cuenta y se sigue; que fallen todas apunta a una causa común, casi siempre un permiso de
     * Firebase que falta en la cuenta de servicio, y el Job terminaría con éxito sin que nadie lo notara.</p>
     */
    private static void warnIfEveryAccountFailed(Totals totals) {
        if (totals.reviewed > 0 && totals.failed == totals.reviewed) {
            logger.error("Todas las cuentas revisadas fallaron ({}); revisar los permisos de la cuenta de servicio en Firebase",
                    totals.reviewed);
        }
    }

    /**
     * Decide y ejecuta lo que corresponde a una cuenta candidata.
     *
     * @param firebaseUid identificador del usuario
     * @return qué pasó con la cuenta
     * @throws DependencyUnavailableException si el directorio no respondió
     */
    private Outcome process(String firebaseUid) {
        try {
            UserVerificationStatus status = directory.verificationStatus(firebaseUid);
            if (status == UserVerificationStatus.VERIFIED) {
                return activate(firebaseUid);
            }
            return status == UserVerificationStatus.NOT_VERIFIED
                    ? deleteCredentialThenRow(firebaseUid) : deleteRowOnly(firebaseUid);
        } catch (DependencyUnavailableException unavailable) {
            throw unavailable;
        } catch (RuntimeException error) {
            // Solo la clase de la excepción: su mensaje puede traer datos personales
            logger.warn("No se pudo purgar la cuenta del usuario {}: {}", firebaseUid, error.getClass().getSimpleName());
            return Outcome.FAILED;
        }
    }

    /**
     * Borra las credenciales de correo y contraseña sin verificar, de más de 168 horas, que no tienen cuenta local.
     *
     * <p>El registro las deja así cuando falla también su borrado compensatorio. No publica evento: nunca hubo
     * {@code cuenta.creada}. Una credencial huérfana con el correo verificado o de otro proveedor se conserva.</p>
     */
    private void reconcileOrphanCredentials(Instant limit, Totals totals) {
        List<String> credentials = directory.findUnverifiedPasswordUsersCreatedBefore(limit, maxAccountsPerRun);
        Set<String> withAccount = accounts.findExistingFirebaseUids(credentials);
        for (String firebaseUid : credentials) {
            if (!withAccount.contains(firebaseUid)) {
                totals.add(deleteOrphanCredential(firebaseUid));
            }
        }
    }

    private Outcome deleteOrphanCredential(String firebaseUid) {
        try {
            directory.deleteUser(firebaseUid);
            return Outcome.ORPHAN_DELETED;
        } catch (DependencyUnavailableException unavailable) {
            throw unavailable;
        } catch (RuntimeException error) {
            logger.warn("No se pudo borrar la credencial huérfana del usuario {}: {}", firebaseUid,
                    error.getClass().getSimpleName());
            return Outcome.FAILED;
        }
    }

    /** La persona ya verificó su correo: la cuenta se activa en lugar de borrarse. */
    private Outcome activate(String firebaseUid) {
        Account account = accounts.findByFirebaseUid(firebaseUid).orElse(null);
        if (account == null) {
            logger.warn("La cuenta del usuario {} ya no existe al activarla", firebaseUid);
            return Outcome.FAILED;
        }
        account.activate();
        accounts.save(account);
        return Outcome.ACTIVATED;
    }

    /** Sin verificar: primero la credencial y después la fila, que lleva su evento. */
    private Outcome deleteCredentialThenRow(String firebaseUid) {
        directory.deleteUser(firebaseUid);
        if (deletion.deletePending(firebaseUid)) {
            return Outcome.DELETED;
        }
        // La persona activó la cuenta entre la consulta y el borrado de la credencial: alguien debe conciliarla.
        logger.error("Cuenta activa sin credencial tras la purga, uid {}", firebaseUid);
        return Outcome.FAILED;
    }

    /** La credencial ya no existe: solo queda borrar la fila y publicar el evento. */
    private Outcome deleteRowOnly(String firebaseUid) {
        if (deletion.deletePending(firebaseUid)) {
            return Outcome.DELETED;
        }
        logger.warn("La cuenta del usuario {} ya no estaba pendiente al borrarla", firebaseUid);
        return Outcome.FAILED;
    }

    private static void logSummary(PurgeSummary summary) {
        logger.info("Purga de cuentas sin verificar: revisadas={}, eliminadas={}, activadas={}, fallidas={}, "
                + "credencialesHuerfanas={}, quedanCandidatas={}", summary.reviewed(), summary.deleted(),
                summary.activated(), summary.failed(), summary.orphanCredentialsDeleted(), summary.candidatesLeft());
    }

    /** Lo que pasó con una cuenta. */
    private enum Outcome { DELETED, ACTIVATED, FAILED, ORPHAN_DELETED }

    /** Contadores de una ejecución; viven solo durante {@code purge()}. */
    private static final class Totals {

        private int reviewed;
        private int deleted;
        private int activated;
        private int failed;
        private int orphanCredentialsDeleted;

        void add(Outcome outcome) {
            switch (outcome) {
                case DELETED -> deleted++;
                case ACTIVATED -> activated++;
                case FAILED -> failed++;
                case ORPHAN_DELETED -> orphanCredentialsDeleted++;
            }
        }

        PurgeSummary summary(boolean candidatesLeft) {
            return new PurgeSummary(reviewed, deleted, activated, failed, orphanCredentialsDeleted, candidatesLeft);
        }
    }
}
