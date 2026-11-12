package tech.cameia.cuentas.application.command;

/**
 * Datos de entrada del caso de uso que registra la aceptación de la política de tratamiento de datos.
 *
 * @param firebaseUid identificador del usuario, tomado del encabezado {@code X-User-Id} que emite el Gateway
 * @param policyVersion versión de la política que la persona vio y aceptó, ya recortada
 */
public record AcceptPolicyCommand(String firebaseUid, String policyVersion) {
}
