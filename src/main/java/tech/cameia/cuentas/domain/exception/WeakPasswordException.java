package tech.cameia.cuentas.domain.exception;

/**
 * Señala que la contraseña no cumple la política de seguridad.
 *
 * <p>El mensaje describe la regla incumplida, nunca la contraseña recibida: ese valor no
 * sale del objeto que lo transporta.</p>
 */
public class WeakPasswordException extends BusinessException {

    /**
     * Crea la excepción con la regla incumplida.
     *
     * @param mensaje texto en español que explica qué exige la política
     */
    public WeakPasswordException(String mensaje) {
        super(mensaje);
    }
}
