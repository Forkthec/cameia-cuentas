package tech.cameia.cuentas.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import tech.cameia.cuentas.application.service.AccountCreatedBackfillService;
import tech.cameia.cuentas.application.service.BackfillSummary;
import tech.cameia.cuentas.application.service.OutboxRelayService;
import tech.cameia.cuentas.application.service.RelaySummary;

/**
 * Ejecuta una vez el relevo de eventos de cuenta: primero registra los eventos de cuenta creada que faltan y luego publica
 * los pendientes.
 *
 * <p>Solo existe con el perfil {@code account-events-relay}, que usa el Cloud Run Job que dispara Cloud Scheduler. Un fallo
 * del directorio de usuarios no impide publicar los eventos pendientes, pero sí hace que el proceso termine con un código
 * distinto de 0 para que el Job se reintente; los eventos sin publicar no son un fallo, esperan a la siguiente
 * ejecución.</p>
 */
@Component
@Profile(AccountEventsRelayJobRunner.PROFILE)
public class AccountEventsRelayJobRunner implements ApplicationRunner {

    /** Nombre del perfil de Spring que activa la tarea de relevo. */
    public static final String PROFILE = "account-events-relay";

    private static final Logger logger = LoggerFactory.getLogger(AccountEventsRelayJobRunner.class);

    private final AccountCreatedBackfillService backfillService;
    private final OutboxRelayService relayService;

    /**
     * Crea la tarea.
     *
     * @param backfillService registra los eventos de las cuentas que no tienen uno
     * @param relayService publica los eventos pendientes
     */
    public AccountEventsRelayJobRunner(AccountCreatedBackfillService backfillService, OutboxRelayService relayService) {
        this.backfillService = backfillService;
        this.relayService = relayService;
    }

    /**
     * Registra los eventos que faltan, publica los pendientes y deja el resumen en el log.
     *
     * <p>Si la carga inicial falla, ya sea porque el directorio de usuarios no responde o porque rechaza la consulta, se
     * interrumpe pero los eventos que ya están en la tabla de salida se publican igual, porque no dependen del directorio.
     * La excepción se vuelve a lanzar al final para que el proceso termine con un código distinto de 0 y el Job se
     * reintente.</p>
     *
     * @param arguments argumentos de la aplicación; no se usan
     * @throws RuntimeException si la carga inicial falló; se lanza después de publicar los pendientes
     */
    @Override
    public void run(ApplicationArguments arguments) {
        BackfillSummary backfill = null;
        RuntimeException backfillFailure = null;
        try {
            backfill = backfillService.enqueueMissing();
        } catch (RuntimeException failure) {
            backfillFailure = failure;
            logger.warn("La carga inicial se interrumpió por un fallo del directorio de usuarios [causa={}]; "
                    + "se publican igual los eventos pendientes", failure.getClass().getSimpleName());
        }
        RelaySummary relay = relayService.relayPending();
        if (backfill != null) {
            logger.info("Relevo de eventos de cuenta terminado [registrados={}, omitidos={}, faltanPorRegistrar={}, "
                    + "publicados={}, fallidos={}, pendientes={}]", backfill.enqueued(), backfill.skipped(),
                    backfill.moreRemain(), relay.published(), relay.failed(), relay.stillPending());
        } else {
            logger.info("Relevo de eventos de cuenta terminado sin carga inicial [publicados={}, fallidos={}, pendientes={}]",
                    relay.published(), relay.failed(), relay.stillPending());
        }
        if (backfillFailure != null) {
            throw backfillFailure;
        }
    }
}
