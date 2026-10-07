package tech.cameia.cuentas.domain.exception;

/**
 * Señala que el pronombre recibido no es una de las opciones del contrato.
 *
 * <p>El mensaje es el de RT-01 para una lista de opciones y no repite el valor recibido.</p>
 */
public class InvalidPronounException extends InvalidFieldException {

    /** Crea la excepción con el texto de RT-01. */
    public InvalidPronounException() {
        super("pronoun", ErrorCode.PRONOUN_INVALID_VALUE, "Selecciona una opción.");
    }
}
