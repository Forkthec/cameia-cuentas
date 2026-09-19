package tech.cameia.cuentas.presentation.dto;

import java.util.UUID;

import tech.cameia.cuentas.domain.model.Account;

/**
 * Respuesta de la activación de una cuenta.
 *
 * @param id identificador de la cuenta local
 * @param status estado resultante; {@code ACTIVE} cuando la verificación se completó
 */
public record ActivatedAccountResponse(UUID id, String status) {

    /**
     * Construye la respuesta a partir de la cuenta activada.
     *
     * @param account cuenta ya activa
     * @return respuesta lista para serializarse
     */
    public static ActivatedAccountResponse de(Account account) {
        return new ActivatedAccountResponse(account.getId(), account.getStatus().name());
    }
}
