package tech.cameia.cuentas.domain.policy;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.WeakPasswordException;
import tech.cameia.cuentas.domain.model.RawPassword;
import tech.cameia.cuentas.domain.model.SingleLineText;

/**
 * Comprueba que una contraseña cumpla la política de seguridad del proyecto.
 *
 * <p>Sigue el estándar OWASP ASVS y exige dos cosas: longitud suficiente y que no sea una
 * contraseña conocida. Se admite cualquier carácter, porque obligar a mezclar mayúsculas,
 * dígitos y símbolos empuja a las personas hacia contraseñas cortas y predecibles, del
 * estilo {@code Password1!}, más fáciles de adivinar que una frase larga.</p>
 *
 * <p>La política vive aquí y no en Firebase porque Firebase solo exige seis caracteres, y
 * ese mínimo no cumple el estándar.</p>
 */
public class PasswordPolicy {

    /** Longitud mínima exigida, en caracteres. */
    private static final int MINIMUM_LENGTH = 12;

    /** Longitud máxima admitida; por encima se rechaza en vez de recortar. */
    private static final int MAXIMUM_LENGTH = 64;

    /** Texto del criterio de aceptación para una contraseña común; nunca repite la contraseña. */
    private static final String COMMON_PASSWORD_MESSAGE = "Esta contraseña es demasiado común, elige otra.";

    /**
     * Contraseñas rechazadas aunque cumplan la longitud exigida, en minúsculas.
     *
     * <p>La longitud sola no basta: {@code 123456789012} tiene doce caracteres y aparece en
     * las primeras posiciones de cualquier lista de filtraciones, así que un ataque de
     * diccionario la prueba en los primeros intentos. ASVS pide justamente comprobar la
     * contraseña contra un conjunto de valores conocidos.</p>
     */
    private final Set<String> commonPasswords;

    /**
     * Crea la política con su lista de contraseñas comunes.
     *
     * <p>La comparación ignora mayúsculas y espacios alrededor, porque {@code Password1234} y
     * {@code password1234 } son la misma contraseña para quien la adivina. La lista que usa la
     * aplicación se carga de un recurso versionado; la ensambla la configuración.</p>
     *
     * @param commonPasswords contraseñas rechazadas aunque cumplan la longitud, ya en minúsculas
     */
    public PasswordPolicy(Set<String> commonPasswords) {
        this.commonPasswords = Set.copyOf(commonPasswords);
    }

    /**
     * Comprueba que la contraseña sea aceptable.
     *
     * <p>La longitud se mide en puntos de código de la forma NFC, no en unidades {@code char},
     * para que un emoji o una letra fuera del alfabeto latino cuenten como un carácter y no
     * como dos, y para que una letra con tilde cuente igual se escriba como se escriba.</p>
     *
     * @param password contraseña recibida en el registro
     * @throws WeakPasswordException si es más corta que el mínimo, más larga que el máximo
     *                               o figura entre las contraseñas conocidas
     */
    public void verify(RawPassword password) {
        String value = password.value();
        // La longitud se mide sobre la forma NFC para que un acento escrito con carácter combinante
        // cuente igual que el mismo acento precompuesto; la contraseña en sí no se modifica.
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        int length = normalized.codePointCount(0, normalized.length());

        if (length < MINIMUM_LENGTH) {
            throw new WeakPasswordException(ErrorCode.PASSWORD_TOO_SHORT,
                    "La contraseña debe tener al menos " + MINIMUM_LENGTH + " caracteres.");
        }
        if (length > MAXIMUM_LENGTH) {
            throw new WeakPasswordException(ErrorCode.PASSWORD_TOO_LONG,
                    "La contraseña no puede superar los " + MAXIMUM_LENGTH + " caracteres.");
        }
        if (esConocida(value)) {
            throw new WeakPasswordException(ErrorCode.PASSWORD_TOO_COMMON, COMMON_PASSWORD_MESSAGE);
        }
    }

    /**
     * Indica si la contraseña figura entre las conocidas.
     *
     * @param value contraseña tal como la escribió la persona
     * @return {@code true} si coincide con una de la lista, ignorando mayúsculas y
     *         espacios alrededor
     */
    private boolean esConocida(String value) {
        // Mismo recorte y misma forma Unicode que el resto del servicio: un espacio duro alrededor
        // o un acento combinante no convierten una contraseña común en otra.
        return commonPasswords.contains(SingleLineText.normalize(value).toLowerCase(Locale.ROOT));
    }
}
