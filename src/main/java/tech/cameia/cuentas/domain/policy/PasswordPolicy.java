package tech.cameia.cuentas.domain.policy;

import java.util.Locale;
import java.util.Set;

import tech.cameia.cuentas.domain.exception.WeakPasswordException;
import tech.cameia.cuentas.domain.model.RawPassword;

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

    /**
     * Contraseñas rechazadas aunque cumplan la longitud exigida.
     *
     * <p>La longitud sola no basta: {@code 123456789012} tiene doce caracteres y aparece
     * en las primeras posiciones de cualquier lista de filtraciones, así que un ataque de
     * diccionario la prueba en los primeros intentos. ASVS pide justamente comprobar la
     * contraseña contra un conjunto de valores conocidos.</p>
     *
     * <p>Son solo las que superan el mínimo de doce caracteres: una más corta ya la
     * rechaza la regla de longitud, así que incluirla aquí no aportaría nada. La
     * comparación ignora mayúsculas y espacios alrededor, porque {@code Password1234} y
     * {@code password1234 } son la misma contraseña para quien la adivina.</p>
     */
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "123456789012", "1234567890123", "12345678901234", "123456789012345",
            "1234567890123456", "111111111111", "000000000000", "121212121212",
            "123123123123", "abcdefghijkl", "abcd1234abcd", "qwertyuiop12",
            "qwertyuiop123", "qwertyuiopasd", "asdfghjklzxc", "1qaz2wsx3edc",
            "password1234", "password12345", "passwordpassword", "contrasena123",
            "contrasena1234", "contrasenia123", "administrador", "administrator",
            "iloveyou1234", "letmein12345", "welcome12345", "superman1234",
            "futbol123456", "colombia1234", "bogota123456", "cameia123456");

    /**
     * Comprueba que la contraseña sea aceptable.
     *
     * <p>La longitud se mide en puntos de código, no en unidades {@code char}, para que un
     * emoji o una letra fuera del alfabeto latino cuenten como un carácter y no como dos.</p>
     *
     * @param password contraseña recibida en el registro
     * @throws WeakPasswordException si es más corta que el mínimo, más larga que el máximo
     *                               o figura entre las contraseñas conocidas
     */
    public void verify(RawPassword password) {
        String value = password.value();
        int length = value.codePointCount(0, value.length());

        if (length < MINIMUM_LENGTH) {
            throw new WeakPasswordException(
                    "La contraseña debe tener al menos " + MINIMUM_LENGTH + " caracteres");
        }
        if (length > MAXIMUM_LENGTH) {
            throw new WeakPasswordException(
                    "La contraseña no puede superar los " + MAXIMUM_LENGTH + " caracteres");
        }
        if (esConocida(value)) {
            throw new WeakPasswordException(
                    "La contraseña es demasiado común brother, cambiala si no quieres que te terminen robando la cuenta");
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
        return COMMON_PASSWORDS.contains(value.trim().toLowerCase(Locale.ROOT));
    }
}
