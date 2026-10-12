package tech.cameia.cuentas.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import tech.cameia.cuentas.application.service.PurgeUnverifiedAccountsService;
import tech.cameia.cuentas.domain.model.PurgeSummary;

/**
 * Ejecuta una vez la purga de cuentas sin verificar.
 *
 * <p>Solo existe con el perfil {@code purge-job}, que usa el Cloud Run Job que dispara Cloud Scheduler a diario. Si la
 * purga lanza (por ejemplo, el directorio de usuarios no responde), la excepción sale y el proceso termina con un código
 * distinto de 0 para que el Job se reintente.</p>
 */
@Component
@Profile(PurgeJobRunner.PROFILE)
public class PurgeJobRunner implements ApplicationRunner {

    /** Nombre del perfil de Spring que activa la tarea de purga. */
    public static final String PROFILE = "purge-job";

    private static final Logger logger = LoggerFactory.getLogger(PurgeJobRunner.class);

    private final PurgeUnverifiedAccountsService service;

    /**
     * Crea la tarea.
     *
     * @param service servicio que purga las cuentas sin verificar
     */
    public PurgeJobRunner(PurgeUnverifiedAccountsService service) {
        this.service = service;
    }

    /**
     * Purga las cuentas sin verificar y avisa si quedaron cuentas fallidas o candidatas sin procesar.
     *
     * <p>Las cuentas fallidas no hacen fallar el proceso: la siguiente ejecución las reintenta y el aviso deja constancia.</p>
     *
     * @param arguments argumentos de la aplicación; no se usan
     * @throws RuntimeException si la purga no pudo hacer su trabajo (por ejemplo, el directorio no responde)
     */
    @Override
    public void run(ApplicationArguments arguments) {
        PurgeSummary summary = service.purge();
        if (summary.failed() > 0 || summary.candidatesLeft()) {
            logger.warn("La purga terminó con cuentas pendientes [fallidas={}, quedanCandidatas={}]",
                    summary.failed(), summary.candidatesLeft());
        }
    }
}
