package tech.cameia.cuentas.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.InvalidEmailException;

/**
 * Correo electrónico con el que se crea la credencial en Firebase Auth.
 *
 * <p>El valor se guarda sin espacios en los extremos (los mismos que recorta el cliente), en
 * forma Unicode NFC y en minúsculas, para que la unicidad que impone Firebase no dependa de
 * cómo lo escribió la persona. El límite es de 254 puntos de código.</p>
 *
 * <p>La validación comprueba la forma, no la existencia del buzón: eso lo resuelve el
 * correo de verificación. Se prefiere una regla simple y permisiva a una compleja, porque
 * una expresión estricta rechaza direcciones válidas y da una falsa sensación de
 * seguridad. Sí se rechazan los caracteres invisibles (separadores, de control y de formato,
 * como el espacio duro o el espacio de ancho cero): ninguna dirección los admite y el
 * directorio de usuarios los rechazaría después con un error que la persona no entiende.</p>
 *
 * <p>Este valor no se guarda en la base de datos del microservicio: pertenece a Firebase.</p>
 *
 * @param value dirección normalizada
 */
public record EmailAddress(String value) {

    /** Un carácter admitido en cada parte: no es arroba, ni espacio, ni invisible. */
    private static final String VISIBLE = "[^@\\s\\p{Z}\\p{Cc}\\p{Cf}";

    private static final Pattern FORMAT = Pattern.compile(
            "^" + VISIBLE + "]+@" + VISIBLE + ".]+(\\." + VISIBLE + ".]+)+$");

    /** Límite práctico de longitud, alineado con lo que acepta Firebase Auth. */
    private static final int MAX_LENGTH = 254;

    /**
     * Normaliza y valida la dirección recibida.
     *
     * @throws IllegalArgumentException si es nula o está vacía (el contrato HTTP la rechaza antes)
     * @throws InvalidEmailException si excede la longitud máxima o no tiene forma de correo
     */
    public EmailAddress {
        SingleLineText recortado = value == null ? null : new SingleLineText(value);
        if (recortado == null || recortado.isEmpty()) {
            throw new IllegalArgumentException("El correo electrónico es obligatorio");
        }
        // Se vuelve a normalizar tras pasar a minúsculas: algunas mayúsculas (como «İ») cambian
        // de forma al convertirlas y dejan de estar en NFC.
        value = SingleLineText.normalize(recortado.value().toLowerCase(Locale.ROOT));
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new InvalidEmailException(ErrorCode.EMAIL_TOO_LONG, "El correo no puede superar los 254 caracteres.");
        }
        if (!FORMAT.matcher(value).matches()) {
            throw InvalidEmailException.createInvalidFormat();
        }
    }
}
