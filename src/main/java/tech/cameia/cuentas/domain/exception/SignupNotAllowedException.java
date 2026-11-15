package tech.cameia.cuentas.domain.exception;

/**
 * Señala que el correo del registro no está en la lista de correos permitidos.
 *
 * <p>El mensaje es genérico a propósito: no revela que existe una lista ni quién está en ella
 * (CM-360, D-4).</p>
 */
public class SignupNotAllowedException extends BusinessException {

    private static final String MENSAJE = "No es posible crear una cuenta con este correo.";

    /** Crea la excepción con el mensaje que ve la persona. */
    public SignupNotAllowedException() {
        super(ErrorCode.SIGNUP_NOT_ALLOWED, MENSAJE);
    }
}
