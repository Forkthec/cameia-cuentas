package tech.cameia.cuentas.presentation.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Restricción del contrato: el texto no supera un máximo de caracteres contados como puntos
 * de código Unicode.
 *
 * <p>Sustituye a {@code @Size}, que cuenta unidades UTF-16: con ella un emoji o una letra fuera
 * del plano básico vale dos y un nombre de 120 caracteres reales podía rechazarse. La ausencia
 * no es asunto suyo: la reporta {@code @NotBlank}.</p>
 */
@Documented
@Constraint(validatedBy = CodePointSizeValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CodePointSize {

    /**
     * Máximo admitido.
     *
     * @return máximo de puntos de código
     */
    int max();

    /**
     * Mensaje para la persona cuando el texto supera el máximo.
     *
     * @return texto del criterio de aceptación
     */
    String message() default "El texto supera el máximo de caracteres";

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
