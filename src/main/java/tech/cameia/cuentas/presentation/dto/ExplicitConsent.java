package tech.cameia.cuentas.presentation.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Restricción del contrato: la autorización solo se otorga con el valor JSON {@code true}.
 *
 * <p>Cualquier otro valor la rechaza, sin importar su forma: {@code false}, el texto {@code "true"}, un número, un
 * arreglo o un objeto. También {@code null} y la ausencia del campo. Una autorización para tratar datos personales
 * debe ser expresa, así que no se interpreta ni se convierte lo que llega.</p>
 */
@Documented
@Constraint(validatedBy = ExplicitConsentValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExplicitConsent {

    /**
     * Mensaje para la persona cuando no autorizó el tratamiento de sus datos.
     *
     * @return texto del criterio de aceptación
     */
    String message() default "Acepta la política de tratamiento de datos para continuar.";

    /**
     * Grupos de validación; el contrato no usa grupos.
     *
     * @return grupos en los que aplica la restricción
     */
    Class<?>[] groups() default {};

    /**
     * Metadatos de la restricción; el contrato no los usa.
     *
     * @return cargas asociadas a la restricción
     */
    Class<? extends Payload>[] payload() default {};
}
