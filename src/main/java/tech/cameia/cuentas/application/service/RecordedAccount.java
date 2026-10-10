package tech.cameia.cuentas.application.service;

import java.util.UUID;

import tech.cameia.cuentas.domain.model.Account;

/**
 * Resultado de guardar una cuenta nueva junto con su evento.
 *
 * @param account cuenta tal como quedó guardada
 * @param eventId identificador del evento {@code cuenta.creada} que quedó en la tabla de salida
 */
public record RecordedAccount(Account account, UUID eventId) {
}
