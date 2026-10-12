package tech.cameia.cuentas.domain.model;

/**
 * Resultado de una ejecución de la purga de cuentas sin verificar.
 *
 * @param reviewed cuentas que la ejecución revisó
 * @param deleted cuentas borradas junto con su credencial y con su evento
 * @param activated cuentas que resultaron estar verificadas y se activaron en lugar de borrarse
 * @param failed cuentas que no se pudieron purgar (la siguiente ejecución las reintenta)
 * @param orphanCredentialsDeleted credenciales sin cuenta local que se borraron
 * @param candidatesLeft {@code true} si había más cuentas candidatas que el tope de la ejecución
 */
public record PurgeSummary(int reviewed, int deleted, int activated, int failed, int orphanCredentialsDeleted,
        boolean candidatesLeft) {
}
