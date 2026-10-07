package tech.cameia.cuentas.domain.exception;

/**
 * Señala que el correo no tiene forma de correo o supera el máximo de caracteres.
 *
 * <p>El mensaje no repite el correo recibido.</p>
 */
public class InvalidEmailException extends InvalidFieldException {

    /**
     * Crea la excepción con la causa y su texto.
     *
     * @param errorCode código de la causa ({@code EMAIL_INVALID_FORMAT} o {@code EMAIL_TOO_LONG})
     * @param mensaje texto del criterio de aceptación
     */
    public InvalidEmailException(ErrorCode errorCode, String mensaje) {
        super("email", errorCode, mensaje);
    }

    /**
     * Crea la excepción del correo sin forma de correo, con el texto de CA-1.1.20.
     *
     * <p>Es la misma respuesta si la forma la rechaza {@code EmailAddress} o el directorio de
     * usuarios: para la persona, en los dos casos el correo no es válido.</p>
     *
     * @return excepción con {@code EMAIL_INVALID_FORMAT}
     */
    public static InvalidEmailException invalidFormat() {
        return new InvalidEmailException(ErrorCode.EMAIL_INVALID_FORMAT, "Ingresa un correo electrónico válido.");
    }
}
