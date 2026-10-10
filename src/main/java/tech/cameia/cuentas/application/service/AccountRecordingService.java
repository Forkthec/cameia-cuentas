package tech.cameia.cuentas.application.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.cuentas.domain.event.AccountCreated;
import tech.cameia.cuentas.domain.event.CorrelationId;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.port.AccountRepository;
import tech.cameia.cuentas.domain.port.OutboxRepository;

/**
 * Guarda una cuenta nueva junto con su evento {@code cuenta.creada} en una sola transacción de base de datos.
 *
 * <p>O existen las dos filas o ninguna: el evento nunca describe una cuenta que no se guardó y toda cuenta guardada tiene
 * su evento. La publicación ocurre después y lee el evento de la tabla de salida. Todo camino de registro que cree una
 * fila de cuenta debe pasar por este método.</p>
 */
@Service
public class AccountRecordingService {

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
    public AccountRecordingService(AccountRepository accounts, OutboxRepository outbox) {
        this(accounts, outbox, Clock.systemUTC());
    }

    /**
     * Crea el servicio con un reloj propio, para fijar la hora en las pruebas.
     *
     * @param accounts repositorio de cuentas
     * @param outbox tabla de salida de eventos
     * @param clock reloj que fija el instante del evento
     */
    public AccountRecordingService(AccountRepository accounts, OutboxRepository outbox, Clock clock) {
        this.accounts = accounts;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Guarda la cuenta y agrega su evento en la misma transacción.
     *
     * @param account cuenta nueva, aún sin guardar
     * @param email correo normalizado de la credencial
     * @param requestId {@code X-Request-Id} tal como llegó, puede ser nulo
     * @return la cuenta guardada y el identificador de su evento
     * @throws IllegalStateException si la tabla de salida ya tiene un evento de cuenta creada para esta cuenta (imposible
     *         con una identidad recién generada; la transacción se deshace)
     */
    @Transactional
    public RecordedAccount recordNewAccount(Account account, EmailAddress email, String requestId) {
        Account saved = accounts.save(account);
        UUID eventId = UUID.randomUUID();
        AccountCreated event = new AccountCreated(eventId, saved.getFirebaseUid(), email, saved.getBirthDate(),
                clock.instant(), CorrelationId.fromRequestIdOrElse(requestId, eventId));
        // Si la tabla de salida rechaza el evento, la excepción deshace también la fila de la cuenta guardada arriba.
        if (!outbox.appendAccountCreated(event)) {
            throw new IllegalStateException("La cuenta ya tiene su evento de cuenta creada");
        }
        return new RecordedAccount(saved, eventId);
    }
}
