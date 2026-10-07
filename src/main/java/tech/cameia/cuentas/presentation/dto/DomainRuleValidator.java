package tech.cameia.cuentas.presentation.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

import tech.cameia.cuentas.domain.exception.InvalidFieldException;

/**
 * Ejecuta en el borde la regla del dominio que indica {@link DomainRule}.
 *
 * <p>Si el dominio rechaza el valor con uno de los códigos de la regla, la violación lleva el
 * mensaje de la excepción y su código como carga dinámica, que el manejador de errores pone en
 * el elemento de {@code errors}. Cualquier otro rechazo (vacío o demasiado largo) lo informa la
 * restricción que le corresponde.</p>
 */
public class DomainRuleValidator implements ConstraintValidator<DomainRule, String> {

    private DomainRule.Rule regla;

    /**
     * Toma la regla de la anotación.
     *
     * @param anotacion restricción declarada en el campo
     */
    @Override
    public void initialize(DomainRule anotacion) {
        this.regla = anotacion.value();
    }

    /**
     * Indica si el valor cumple la regla del dominio.
     *
     * @param value texto recibido
     * @param context contexto de Bean Validation
     * @return {@code true} si está ausente o en blanco (lo reporta otra restricción), si cumple
     *         la regla o si el dominio lo rechaza por una causa que reporta otra restricción
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            regla.comprobar(value);
            return true;
        } catch (InvalidFieldException rechazo) {
            if (!regla.reporta(rechazo.getErrorCode())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.unwrap(HibernateConstraintValidatorContext.class)
                    .withDynamicPayload(rechazo.getErrorCode())
                    .buildConstraintViolationWithTemplate(literal(rechazo.getMessage()))
                    .addConstraintViolation();
            return false;
        } catch (IllegalArgumentException invariante) {
            // Vacío o demasiado largo para el objeto de valor: lo reporta @NotBlank o @CodePointSize.
            return true;
        }
    }

    /** Escapa el texto para que la plantilla de mensajes no interprete llaves ni expresiones. */
    private static String literal(String mensaje) {
        return mensaje.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
    }
}
