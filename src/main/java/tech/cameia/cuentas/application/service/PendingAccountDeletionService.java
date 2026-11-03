package tech.cameia.cuentas.application.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.cuentas.domain.event.AccountDeleted;
import tech.cameia.cuentas.domain.event.CorrelationId;
import tech.cameia.cuentas.domain.port.AccountRepository;
import tech.cameia.cuentas.domain.port.OutboxRepository;

/**
 * Borra la cuenta pendiente de verificar y guarda su evento {@code cuenta.eliminada} en una sola transacción.
 *
 * <p>O desaparecen la fila y aparece el evento, o no cambia nada: el evento nunca anuncia un borrado que no ocurrió.</p>
 */
@Service
public class PendingAccountDeletionService {

    private final AccountRepository accounts;
    private final OutboxRepository outbox;
    private final Clock clock;

    /**
     * Crea el servicio con el reloj UTC del sistema.
     *
     * @param accounts repositorio de cuentas
     * @param outbox tabla de salida de eventos
     */
    @Autowired
    public PendingAccountDeletionService(AccountRepository accounts, OutboxRepository outbox) {
        this(accounts, outbox, Clock.systemUTC());
    }

    /**
     * Crea el servicio con un reloj propio, para fijar la hora en las pruebas.
     *
     * @param accounts repositorio de cuentas
     * @param outbox tabla de salida de eventos
     * @param clock reloj que fija el instante del evento
     */
    public PendingAccountDeletionService(AccountRepository accounts, OutboxRepository outbox, Clock clock) {
        this.accounts = accounts;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Borra la cuenta si sigue sin verificar y agrega su evento en la misma transacción.
     *
     * @param firebaseUid identificador del usuario en Firebase Auth
     * @return {@code true} si borró la cuenta y guardó su evento; {@code false} si la cuenta no existía o ya no estaba
     *         pendiente (no cambia nada)
     * @throws IllegalStateException si la tabla de salida ya tiene un evento de cuenta eliminada para esta cuenta
     *         (imposible con una fila recién borrada; la transacción se deshace)
     */
    @Transactional
    public boolean deletePending(String firebaseUid) {
        if (!accounts.deletePendingByFirebaseUid(firebaseUid)) {
            return false;
        }
        UUID eventId = UUID.randomUUID();
        // La purga no nace de una petición HTTP: el propio evento es su correlación.
        AccountDeleted event = new AccountDeleted(eventId, firebaseUid, clock.instant(),
                CorrelationId.fromRequestIdOrElse(null, eventId));
        // Si la tabla de salida rechaza el evento, la excepción deshace también el borrado de arriba.
        if (!outbox.appendAccountDeleted(event)) {
            throw new IllegalStateException("La cuenta ya tiene su evento de cuenta eliminada");
        }
        return true;
    }
}
