package tech.cameia.cuentas.domain.model;

import java.util.regex.Pattern;

import tech.cameia.cuentas.domain.exception.PolicyVersionOutdatedException;

/**
 * Versión vigente de la política de tratamiento de datos, la única que se puede aceptar.
 *
 * <p>El formato es {@code AAAA-MM-vN}: año, mes de 01 a 12 y número de versión de 1 a 999 sin ceros a la
 * izquierda, por ejemplo {@code 2026-11-v1}. Una versión fuera de ese formato impide arrancar el servicio.</p>
 *
 * @param version versión vigente
 */
public record CurrentPolicy(String version) {

    private static final Pattern FORMAT = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])-v[1-9]\\d{0,2}$");

    /**
     * Crea la versión vigente comprobando su formato.
     *
     * @throws IllegalArgumentException si la versión es nula o no cumple el formato {@code AAAA-MM-vN}
     */
    public CurrentPolicy {
        if (version == null || !FORMAT.matcher(version).matches()) {
            throw new IllegalArgumentException(
                    "La versión vigente de la política debe tener el formato AAAA-MM-vN, por ejemplo 2026-11-v1");
        }
    }

    /**
     * Exige que la versión recibida sea la vigente. Distingue mayúsculas y no normaliza.
     *
     * @param candidate versión que envió la persona, ya recortada
     * @throws PolicyVersionOutdatedException si es distinta de la vigente
     */
    public void requireCurrent(String candidate) {
        if (!version.equals(candidate)) {
            throw new PolicyVersionOutdatedException();
        }
    }
}
