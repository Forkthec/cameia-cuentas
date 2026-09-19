package tech.cameia.cuentas.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Correo electrónico con el que se crea la credencial en Firebase Auth.
 *
 * <p>El valor se normaliza a minúsculas y sin espacios alrededor, para que la unicidad
 * que impone Firebase no dependa de cómo lo escribió la persona.</p>
 *
 * <p>La validación comprueba la forma, no la existencia del buzón: eso lo resuelve el
 * correo de verificación. Se prefiere una regla simple y permisiva a una compleja, porque
 * una expresión estricta rechaza direcciones válidas y da una falsa sensación de
 * seguridad.</p>
 *
 * <p>Este valor no se guarda en la base de datos del microservicio: pertenece a Firebase.</p>
 *
 * @param value dirección normalizada
 */
public record EmailAddress(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    /** Límite práctico de longitud, alineado con lo que acepta Firebase Auth. */
    private static final int MAX_LENGTH = 254;

    /**
     * Normaliza y valida la dirección recibida.
     *
     * @throws IllegalArgumentException si es nula, está vacía, excede la longitud máxima
     *                                  o no tiene forma de correo
     */
    public EmailAddress {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El correo electrónico es obligatorio");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("El correo electrónico es demasiado largo");
        }
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("El correo electrónico no tiene un formato válido");
        }
    }
}
