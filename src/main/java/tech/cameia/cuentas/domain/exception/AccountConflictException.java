package tech.cameia.cuentas.domain.exception;

/**
 * Señala que otra petición cambió la cuenta mientras esta intentaba guardarla.
 *
 * <p>Es transitorio: la persona puede reintentar.</p>
 */
public class AccountConflictException extends BusinessException {

    private static final String MENSAJE = "La cuenta se está actualizando. Inténtalo de nuevo.";

    /** Crea la excepción con el mensaje que ve la persona. */
    public AccountConflictException() {
        super(ErrorCode.ACCOUNT_CONFLICT, MENSAJE);
    }
}
