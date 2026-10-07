package tech.cameia.cuentas.presentation.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Mide un texto en puntos de código Unicode.
 *
 * <p>Recibe el texto ya recortado y en NFC (lo normaliza el constructor del cuerpo de la
 * petición), así que mide lo mismo que se guardará.</p>
 */
public class CodePointSizeValidator implements ConstraintValidator<CodePointSize, String> {

    private int max;

    /**
     * Toma el máximo de la anotación.
     *
     * @param anotacion restricción declarada en el campo
     */
    @Override
    public void initialize(CodePointSize anotacion) {
        this.max = anotacion.max();
    }

    /**
     * Indica si el texto cabe en el máximo.
     *
     * @param value texto recibido
     * @param context contexto de Bean Validation
     * @return {@code true} si está ausente (lo reporta otra restricción) o no supera el máximo
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // La ausencia la reporta @NotBlank; aquí solo se mide lo que llegó.
        return value == null || value.codePointCount(0, value.length()) <= max;
    }
}
