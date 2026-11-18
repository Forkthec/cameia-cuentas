package tech.cameia.cuentas.presentation.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

import tech.cameia.cuentas.domain.exception.PolicyVersionOutdatedException;
import tech.cameia.cuentas.domain.model.CurrentPolicy;

/**
 * Ejecuta en el borde la regla del dominio que compara la versión recibida con la vigente.
 *
 * <p>Si la versión es distinta, la violación lleva el mensaje de la excepción y su código como carga dinámica, que
 * el manejador de errores pone en el elemento de {@code errors}, igual que {@link DomainRuleValidator}.</p>
 */
public class CurrentPolicyVersionValidator implements ConstraintValidator<CurrentPolicyVersion, String> {

    private final CurrentPolicy currentPolicy;

    /**
     * Recibe la versión vigente que configura el servicio.
     *
     * @param currentPolicy versión vigente de la política
     */
    public CurrentPolicyVersionValidator(CurrentPolicy currentPolicy) {
        this.currentPolicy = currentPolicy;
    }

    /**
     * Indica si la versión recibida es la vigente.
     *
     * @param value versión recibida, ya recortada
     * @param context contexto de Bean Validation
     * @return {@code true} si está ausente o en blanco (lo reporta {@code @NotBlank}) o si es la vigente
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            currentPolicy.requireCurrent(value);
            return true;
        } catch (PolicyVersionOutdatedException rejection) {
            context.disableDefaultConstraintViolation();
            context.unwrap(HibernateConstraintValidatorContext.class)
                    .withDynamicPayload(rejection.getErrorCode())
                    .buildConstraintViolationWithTemplate(DomainRuleValidator.literal(rejection.getMessage()))
                    .addConstraintViolation();
            return false;
        }
    }
}
