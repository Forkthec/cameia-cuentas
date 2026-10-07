package tech.cameia.cuentas.domain.exception;

import tech.cameia.cuentas.domain.model.PersonName;

/**
 * Señala que un nombre o un apellido tiene caracteres que una persona no usa en su nombre.
 *
 * <p>Lleva el campo del contrato al que pertenece, para que el error se muestre junto al
 * campo correcto del formulario.</p>
 */
public class InvalidPersonNameException extends BusinessException {

    private final String field;

    /**
     * Crea la excepción para una parte del nombre.
     *
     * @param part parte del nombre rechazada, con su código, su campo y su mensaje
     */
    public InvalidPersonNameException(PersonName.Part part) {
        super(part.code(), part.message());
        this.field = part.field();
    }

    /**
     * Indica el campo rechazado.
     *
     * @return nombre del campo en el contrato JSON
     */
    public String getField() {
        return field;
    }
}
