package tech.cameia.cuentas.domain.model;

import java.util.regex.Pattern;

/**
 * Número de celular en formato E.164, por ejemplo {@code +573001234567}.
 *
 * <p>El microservicio no normaliza ni supone un país: si el número llega sin indicativo,
 * se rechaza. Adivinar el país a partir de la longitud produce números que parecen
 * válidos y no existen, y este dato se usa para contactar a una persona real.</p>
 *
 * <p>La expresión es la misma que la restricción {@code ck_cuenta_telefono_e164} de la
 * base de datos, para que un valor aceptado aquí nunca sea rechazado al guardarlo.</p>
 *
 * @param value número en formato E.164
 */
public record PhoneNumber(String value) {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    /**
     * Valida el formato del número recibido.
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
}
