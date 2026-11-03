package tech.cameia.cuentas.application.service;

import java.util.UUID;

import tech.cameia.cuentas.domain.model.Account;

/**
 * Resultado de un registro: la cuenta, si se creó en esta llamada y el evento que quedó guardado con ella.
 *
 * @param account cuenta devuelta
 * @param created {@code true} si esta llamada la creó; {@code false} si ya existía pendiente de verificar
 * @param eventId identificador del evento guardado con una cuenta nueva; nulo cuando no se creó ninguna cuenta
 */
public record RegisterUserResult(Account account, boolean created, UUID eventId) {

    /**
     * Resultado de un registro que no creó ninguna cuenta y por tanto no guardó ningún evento.
     *
     * @param account cuenta devuelta
     * @param created si esta llamada la creó
     */
    public RegisterUserResult(Account account, boolean created) {
        this(account, created, null);
    }
}
