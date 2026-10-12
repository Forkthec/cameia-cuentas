package tech.cameia.cuentas.application.command;

/**
 * Datos de entrada del caso de uso de activación.
 *
 * @param firebaseUid identificador del usuario, tomado del encabezado {@code X-User-Id}
 *                    que emite el Gateway a partir del token ya validado
 * @param emailVerified {@code true} solo cuando el Gateway afirmó la verificación del correo; la
 *                      ausencia del encabezado o cualquier otro valor llegan como {@code false}
 */
public record ActivateAccountCommand(String firebaseUid, boolean emailVerified) {
}
