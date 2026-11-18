package tech.cameia.cuentas.presentation.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Restricción del contrato: el texto, si no está vacío, es la versión vigente de la política.
 *
 * <p>La ausencia y el texto en blanco no son asunto suyo: los reporta {@code @NotBlank}, para que un campo no
 * reciba dos errores. La comparación la hace el dominio ({@code CurrentPolicy}) y no distingue más que el texto
 * exacto: no normaliza ni cambia mayúsculas.</p>
 */
@Documented
@Constraint(validatedBy = CurrentPolicyVersionValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentPolicyVersion {

    /**
     * Mensaje de respaldo; el que ve la persona es el de la excepción del dominio.
     *
     * @return texto de respaldo
     */
    String message() default "La política cambió. Recarga la página y vuelve a aceptarla.";

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
