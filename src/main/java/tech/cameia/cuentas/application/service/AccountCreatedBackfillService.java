package tech.cameia.cuentas.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tech.cameia.cuentas.domain.event.AccountCreated;
import tech.cameia.cuentas.domain.event.CorrelationId;
import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.UnannouncedAccount;
import tech.cameia.cuentas.domain.port.AccountRepository;
import tech.cameia.cuentas.domain.port.FirebaseUserDirectory;
import tech.cameia.cuentas.domain.port.OutboxRepository;

/**
 * Registra un evento {@code cuenta.creada} por cada cuenta que nunca tuvo uno (las creadas antes de que existieran los
 * eventos, o aquellas cuyo evento se borró para republicarlo).
 *
 * <p>Lee como máximo {@link #MAX_ACCOUNTS} cuentas por ejecución, toma el correo de cada una del directorio y agrega el
 * evento con el instante de creación de la cuenta. Un usuario ausente del directorio se omite y se registra; una caída del
 * directorio detiene la ejecución para que el Job se reintente. Repetir la ejecución es seguro: la tabla de salida admite
 * un evento de cuenta creada por cuenta.</p>
 */
@Service
public class AccountCreatedBackfillService {

    static final int MAX_ACCOUNTS = 5000;
    private static final int SKIPPED_EXAMPLES = 10;

    private static final Logger logger = LoggerFactory.getLogger(AccountCreatedBackfillService.class);

    private final AccountRepository accounts;
    private final OutboxRepository outbox;
    private final FirebaseUserDirectory directory;

    /**
     * Crea el servicio.
     *
     * @param accounts repositorio de cuentas, para encontrar las que no tienen evento
     * @param outbox tabla de salida de eventos
     * @param directory directorio de usuarios, de donde sale el correo de cada cuenta
     */
    public AccountCreatedBackfillService(AccountRepository accounts, OutboxRepository outbox,
            FirebaseUserDirectory directory) {
        this.accounts = accounts;
        this.outbox = outbox;
        this.directory = directory;
    }

    /**
     * Agrega el evento de cuenta creada de cada cuenta que no lo tiene.
     *
     * @return cuántos eventos se agregaron, cuántas cuentas se omitieron y si quedan más por registrar
     * @throws DependencyUnavailableException si el directorio no responde; los eventos ya agregados se conservan
     */
    public BackfillSummary enqueueMissing() {
        List<UnannouncedAccount> missing = accounts.findUnannounced(MAX_ACCOUNTS);
        int enqueued = 0;
        List<String> skippedUids = new ArrayList<>();
        int skipped = 0;
        for (UnannouncedAccount account : missing) {
            Optional<EmailAddress> email = directory.findEmail(account.firebaseUid());
            if (email.isEmpty()) {
                skipped++;
                if (skippedUids.size() < SKIPPED_EXAMPLES) {
                    skippedUids.add(account.firebaseUid());
                }
            } else if (append(account, email.get())) {
                enqueued++;
            }
        }
        warnSkipped(skipped, skippedUids);
        return new BackfillSummary(enqueued, skipped, missing.size() == MAX_ACCOUNTS);
    }

    /** Agrega el evento de una cuenta; devuelve {@code false} si otra ejecución ya lo había agregado. */
    private boolean append(UnannouncedAccount account, EmailAddress email) {
        UUID eventId = UUID.randomUUID();
        // Sin petición HTTP que correlacionar, la correlación del evento es su propio identificador
        AccountCreated event = new AccountCreated(eventId, account.firebaseUid(), email, account.birthDate(),
                account.createdAt(), CorrelationId.fromRequestIdOrElse(null, eventId));
        return outbox.appendAccountCreated(event);
    }

    /**
     * Escribe un solo aviso por corrida con el total de cuentas omitidas y hasta diez ejemplos, para que una cuenta huérfana
     * permanente no llene el registro con una línea cada vez que corre la tarea. Nunca incluye correos.
     */
    private void warnSkipped(int skipped, List<String> examples) {
        if (skipped > 0) {
            logger.warn("Cuentas omitidas por no existir su usuario en el directorio [omitidas={}, ejemplos={}]",
                    skipped, examples);
        }
    }
}
