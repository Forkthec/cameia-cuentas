package tech.cameia.cuentas.domain.exception;

/**
 * Raíz de los rechazos del dominio que pertenecen a un solo campo del formulario: la fecha de
 * nacimiento, la contraseña, el nombre, el correo y el celular.
 *
 * <p>Cada rechazo lleva el nombre del campo en el contrato JSON, para que el error se muestre
 * junto al campo correcto. Quien lo traduce a HTTP lo lee de aquí y no lo escribe a mano.</p>
 */
public abstract class InvalidFieldException extends BusinessException {

    private final String field;

    /**
     * Crea el rechazo de un campo.
     *
     * @param field nombre del campo en el contrato JSON
     * @param errorCode código estable de la causa
     * @param message texto del criterio de aceptación, sin el valor recibido
     */
    protected InvalidFieldException(String field, ErrorCode errorCode, String message) {
        super(errorCode, message);
        this.field = field;
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
