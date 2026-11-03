package tech.cameia.cuentas.domain.exception;

/**
 * Señala que la contraseña no cumple la política de seguridad.
 *
 * <p>El mensaje describe la regla incumplida, nunca la contraseña recibida: ese valor no
 * sale del objeto que lo transporta.</p>
 */
public class WeakPasswordException extends InvalidFieldException {

    /**
     * Crea la excepción con el código de la regla incumplida y su texto.
     *
     * @param errorCode código estable de la regla incumplida (muy corta, muy larga o común)
     * @param mensaje texto en español que explica qué exige la política
     */
    public WeakPasswordException(ErrorCode errorCode, String mensaje) {
        super("password", errorCode, mensaje);
    }
}
