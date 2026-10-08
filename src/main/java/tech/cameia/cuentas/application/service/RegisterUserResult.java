package tech.cameia.cuentas.application.service;

import tech.cameia.cuentas.domain.model.Account;

/**
 * Resultado de un registro: la cuenta y si se creó en esta llamada.
 *
 * @param account cuenta devuelta
 * @param created {@code true} si esta llamada la creó; {@code false} si ya existía pendiente de verificar
 */
public record RegisterUserResult(Account account, boolean created) { }
