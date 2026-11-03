package tech.cameia.cuentas.domain.model;

import java.util.regex.Pattern;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber;

import tech.cameia.cuentas.domain.exception.InvalidPhoneNumberException;

/**
 * Número de celular en formato E.164, por ejemplo {@code +573001234567}.
 *
 * <p>El microservicio no normaliza ni supone un país: si el número llega sin indicativo,
 * se rechaza. Adivinar el país a partir de la longitud produce números que parecen
 * válidos y no existen, y este dato se usa para contactar a una persona real.</p>
 *
 * <p>Hay dos niveles de exigencia. El constructor comprueba solo la forma E.164, que es la
 * misma de la restricción {@code ck_cuenta_telefono_e164} de la base: con él se reconstruyen
 * los números ya guardados, aunque se hayan guardado antes de la regla por país. Un número
 * nuevo entra por {@link #fromInput(String)}, que además exige que sea un número válido para su
 * país según {@code libphonenumber}, la misma librería que valida el formulario web.</p>
 *
 * @param value número en formato E.164
 */
public record PhoneNumber(String value) {

    /**
     * Forma E.164: «+» y de 6 a 15 dígitos, el primero distinto de cero. Seis es la longitud del
     * número posible más corto de cualquier país (contando el indicativo) y quince el máximo de
     * E.164.
     */
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{5,14}$");

    /**
     * Valida la forma E.164 del número.
     *
     * @throws IllegalArgumentException si es nulo, está vacío o no cumple E.164
     */
    public PhoneNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El número de celular es obligatorio cuando se envía");
        }
        value = value.trim();
        if (!E164.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "El número de celular debe incluir el indicativo del país, por ejemplo +573001234567");
        }
    }

    /**
     * Crea un número recibido de la persona.
     *
     * <p>Además de la forma E.164 exige que el número sea válido para el país de su indicativo
     * y que esté escrito en su forma canónica: sin espacios, separadores, ceros sobrantes ni
     * extensión.</p>
     *
     * @param raw número tal como llegó, ya recortado
     * @return el número validado
     * @throws IllegalArgumentException si es nulo o está vacío (quien llama decide antes si hay
     *                                  celular)
     * @throws InvalidPhoneNumberException si no es un número válido para su país
     */
    public static PhoneNumber fromInput(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("El número de celular es obligatorio cuando se envía");
        }
        if (!E164.matcher(raw).matches() || !esValidoParaSuPais(raw)) {
            throw new InvalidPhoneNumberException();
        }
        return new PhoneNumber(raw);
    }

    /** Valida con {@code libphonenumber} y exige que su forma E.164 canónica sea el mismo texto. */
    private static boolean esValidoParaSuPais(String raw) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        try {
            // El texto empieza por «+», así que no hace falta una región por defecto.
            Phonenumber.PhoneNumber numero = util.parse(raw, null);
            return util.isValidNumber(numero) && raw.equals(util.format(numero, PhoneNumberFormat.E164));
        } catch (NumberParseException fallo) {
            // Un texto que la librería no puede interpretar no es un número: se rechaza igual.
            return false;
        }
    }
}
