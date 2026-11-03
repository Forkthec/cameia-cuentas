package tech.cameia.cuentas.presentation.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
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
     * Formato del contrato, el mismo con el que {@link RegisterUserRequest#toCommand(String)}
     * convierte el texto: un solo formato para validar y para convertir.
     *
     * <p>Cada parte tiene un ancho fijo y sin signo: dos dígitos de día, dos de mes y cuatro de
     * año (CA-1.1.8, dd/mm/aaaa). El patrón {@code uuuu} no sirve: admite un año con signo
     * ({@code -2000} o {@code +12345}), que pasaría como fecha real y lo rechazaría después la
     * política de edad con otro código. El año es el calendario ({@code YEAR}), no el de la era:
     * con resolución estricta, el de la era exige además la era y rechaza toda fecha.</p>
     */
    static final DateTimeFormatter FORMAT = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral('/')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendLiteral('/')
            .appendValue(ChronoField.YEAR, 4)
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

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
