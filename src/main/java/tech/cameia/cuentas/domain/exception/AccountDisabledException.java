package tech.cameia.cuentas.domain.exception;

/**
 * Señala que se intentó activar una cuenta deshabilitada.
 *
 * <p>Verificar un correo no revierte esa decisión, así que la activación se rechaza con un mensaje que
 * la persona entiende.</p>
 */
public class AccountDisabledException extends BusinessException {

    private static final String MENSAJE = "Esta cuenta está deshabilitada.";

    /** Crea la excepción con el mensaje que ve la persona. */
    public AccountDisabledException() {
        super(ErrorCode.ACCOUNT_DISABLED, MENSAJE);
    }
}
