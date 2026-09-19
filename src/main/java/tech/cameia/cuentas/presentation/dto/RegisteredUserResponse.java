package tech.cameia.cuentas.presentation.dto;

import java.util.UUID;

import tech.cameia.cuentas.domain.model.Account;

/**
 * Respuesta de un registro exitoso.
 *
 * <p>No devuelve el correo ni ningún dato personal: el cliente acaba de enviarlos y
 * repetirlos solo agregaría información sensible a los registros de la red.</p>
 *
 * @param id identificador de la cuenta local
 * @param firebaseUid identificador del usuario en Firebase, con el que el frontend inicia
 *                    sesión y pide el correo de verificación
 * @param status estado de la cuenta; siempre {@code PENDING_VERIFICATION} al registrarse
 * @param plan plan asignado; siempre {@code FREE}. No respalda todavía ninguna cuota:
 *             mientras no exista la especificación de planes, es solo una etiqueta
 */
public record RegisteredUserResponse(UUID id, String firebaseUid, String status, String plan) {

    /** Plan que recibe toda cuenta nueva. */
    private static final String FREE_PLAN = "FREE";

    /**
     * Construye la respuesta a partir de la cuenta creada.
     *
     * @param account cuenta recién registrada
     * @return respuesta lista para serializarse
     */
    public static RegisteredUserResponse de(Account account) {
        return new RegisteredUserResponse(account.getId(), account.getFirebaseUid(),
                account.getStatus().name(), FREE_PLAN);
    }
}
