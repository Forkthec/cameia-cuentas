package tech.cameia.cuentas.domain.exception;

/**
 * Señala que la versión de la política enviada no es la vigente.
 *
 * <p>El mensaje no repite el valor recibido: la persona solo necesita recargar la página para ver la política
 * actual y aceptarla de nuevo.</p>
 */
public class PolicyVersionOutdatedException extends InvalidFieldException {

    /** Crea la excepción con el texto que se muestra junto al campo {@code policyVersion}. */
    public PolicyVersionOutdatedException() {
        super("policyVersion", ErrorCode.POLICY_VERSION_OUTDATED,
                "La política cambió. Recarga la página y vuelve a aceptarla.");
    }
}
