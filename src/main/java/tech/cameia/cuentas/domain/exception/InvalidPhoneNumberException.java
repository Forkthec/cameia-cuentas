package tech.cameia.cuentas.domain.exception;

/**
 * Señala que el celular no es un número válido para el país de su indicativo.
 *
 * <p>El mensaje no repite el número recibido.</p>
 */
public class InvalidPhoneNumberException extends BusinessException {

    private static final String MENSAJE = "Revisa el número, no coincide con el formato del país elegido.";

    /** Crea la excepción con el mensaje del criterio de aceptación. */
    public InvalidPhoneNumberException() {
        super(ErrorCode.PHONE_NUMBER_INVALID_FORMAT, MENSAJE);
    }

    /**
     * Indica el campo rechazado.
     *
     * @return nombre del campo en el contrato JSON
     */
    public String getField() {
        return "phoneNumber";
    }
}
