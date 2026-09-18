package tech.cameia.cuentas.domain.model;

/**
 * Contraseña tal como la escribió la persona, de camino a Firebase Auth.
 *
 * <p>Este valor nunca se guarda ni se registra: solo viaja desde el controlador hasta el
 * adaptador de Firebase, que es quien custodia las credenciales. Por eso
 * {@link #toString()} devuelve un texto fijo, para que un log descuidado o el volcado de
 * una excepción no filtren la contraseña.</p>
 *
 * <p>El valor no se recorta: un espacio al inicio o al final forma parte de la contraseña
 * que la persona eligió. Las reglas de longitud las aplica
 * {@code tech.cameia.cuentas.domain.policy.PasswordPolicy}.</p>
 *
 * @param value contraseña sin transformar
 */
public record RawPassword(String value) {

    /**
     * Comprueba que llegó una contraseña.
     *
     * @throws IllegalArgumentException si es nula o está vacía
     */
    public RawPassword {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("La contraseña es obligatoria");
        }
    }

    /**
     * Oculta el valor en cualquier representación de texto.
     *
     * @return un marcador fijo, nunca la contraseña
     */
    @Override
    public String toString() {
        return "RawPassword[oculta]";
    }
}
