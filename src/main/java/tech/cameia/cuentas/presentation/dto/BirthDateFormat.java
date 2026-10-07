package tech.cameia.cuentas.presentation.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Restricción del contrato: el texto, si no está vacío, es una fecha real con el formato
 * {@code dd/MM/aaaa} (dos dígitos de día, dos de mes y el año con cuatro dígitos o más).
 *
 * <p>Solo decide el formato: si la persona tiene la edad exigida lo decide después la
 * política de edad del dominio. La ausencia y el texto en blanco tampoco son asunto suyo:
 * los reporta {@code @NotBlank}, para que un campo no reciba dos errores.</p>
 */
@Documented
@Constraint(validatedBy = BirthDateFormatValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface BirthDateFormat {

    /**
     * Mensaje para la persona cuando el texto no es una fecha válida.
     *
     * @return texto del criterio de aceptación
     */
    String message() default "Formato de fecha inválido.";

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
