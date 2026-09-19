package tech.cameia.cuentas.application.command;

/**
 * Datos de entrada del caso de uso de activación.
 *
 * @param firebaseUid identificador del usuario, tomado del encabezado {@code X-User-Id}
 *                    que emite el Gateway a partir del token ya validado
 * @param emailVerifiedByGateway lo que el Gateway afirma sobre el claim
 *                               {@code email_verified}, o {@code null} si todavía no
 *                               propaga ese encabezado. Solo se cree cuando dice
 *                               {@code true}; en cualquier otro caso el servicio consulta
 *                               a Firebase
 */
public record ActivateAccountCommand(String firebaseUid, Boolean emailVerifiedByGateway) {
}
