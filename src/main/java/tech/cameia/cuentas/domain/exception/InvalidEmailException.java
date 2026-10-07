package tech.cameia.cuentas.domain.exception;

/**
 * Señala que el correo no tiene forma de correo o supera el máximo de caracteres.
 *
 * <p>El mensaje no repite el correo recibido.</p>
 */
public class InvalidEmailException extends BusinessException {

    /**
     * Crea la excepción con la causa y su texto.
     *
     * @param errorCode código de la causa ({@code EMAIL_INVALID_FORMAT} o {@code EMAIL_TOO_LONG})
     * @param mensaje texto del criterio de aceptación
     */
    public InvalidEmailException(ErrorCode errorCode, String mensaje) {
        super(errorCode, mensaje);
    }

    /**
     * Indica el campo rechazado.
     *
     * @return nombre del campo en el contrato JSON
     */
    public String getField() {
        return "email";
    }
}
