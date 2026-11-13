package tech.cameia.cuentas.presentation.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida {@link ExplicitConsent}: solo el valor booleano {@code true} es válido.
 */
public class ExplicitConsentValidator implements ConstraintValidator<ExplicitConsent, Object> {

    /**
     * Indica si el valor recibido es el booleano {@code true}.
     *
     * @param value valor del campo tal como lo leyó Jackson, de cualquier tipo
     * @param context contexto de Bean Validation
     * @return {@code true} solo si el valor es {@link Boolean#TRUE}
     */
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        return Boolean.TRUE.equals(value);
    }
}
