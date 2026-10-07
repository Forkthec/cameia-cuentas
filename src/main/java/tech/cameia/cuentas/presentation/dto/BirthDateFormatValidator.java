package tech.cameia.cuentas.presentation.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Optional;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida el formato de la fecha de nacimiento sin decidir nada sobre la edad.
 *
 * <p>La interpretación es estricta: {@code 31/02/2000} se rechaza en vez de convertirse en
 * el último día de febrero, que es lo que haría una lectura indulgente y lo que guardaría
 * una fecha que la persona nunca escribió.</p>
 */
public class BirthDateFormatValidator implements ConstraintValidator<BirthDateFormat, String> {

    /**
     * Formato del contrato, el mismo con el que {@link RegisterUserRequest#toCommand()}
     * convierte el texto: un solo formato para validar y para convertir.
     *
     * <p>El año se escribe {@code uuuu} (año calendario) y no {@code yyyy} (año de la era):
     * con resolución estricta, {@code yyyy} exige además la era y rechaza toda fecha.</p>
     */
    static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT)
            .withLocale(Locale.ROOT);

    /**
     * Indica si el texto es aceptable para esta restricción.
     *
     * @param value texto recibido en {@code birthDate}
     * @param context contexto de Bean Validation
     * @return {@code true} si está ausente, en blanco (lo reporta otra restricción) o es una
     *         fecha real en el formato exigido
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // La ausencia o el texto en blanco la reporta @NotBlank: así un campo no tiene dos errores.
        return value == null || value.isBlank() || parse(value).isPresent();
    }

    /**
     * Interpreta el texto con el formato del contrato.
     *
     * @param text texto recibido; puede ser {@code null}
     * @return la fecha, o vacío si el texto falta o no es una fecha real en el formato exigido
     */
    static Optional<LocalDate> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(text, FORMAT));
        } catch (DateTimeParseException fallo) {
            // Un texto mal formado es un resultado esperado, no un fallo: se informa como vacío.
            return Optional.empty();
        }
    }
}
