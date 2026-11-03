package tech.cameia.cuentas.application.service;

/**
 * Resultado de una corrida de la carga inicial de eventos de cuenta creada.
 *
 * @param enqueued eventos agregados a la tabla de salida en esta corrida
 * @param skipped cuentas omitidas porque su usuario no existe en el directorio o no tiene correo
 * @param moreRemain {@code true} si se llegó al límite de la corrida y puede haber más cuentas sin evento
 */
public record BackfillSummary(int enqueued, int skipped, boolean moreRemain) {
}
