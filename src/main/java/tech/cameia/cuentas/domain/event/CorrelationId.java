package tech.cameia.cuentas.domain.event;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador que une un evento con la petición HTTP que lo originó.
 *
 * <p>Reutiliza el {@code X-Request-Id} que pone el Gateway cuando es seguro propagarlo (el mismo patrón que acepta el
 * manejador de errores); si no, usa el identificador del evento, así que todo evento tiene siempre un valor de
 * correlación.</p>
 *
 * @param value valor de correlación, de 1 a 64 caracteres de {@code [A-Za-z0-9._-]}
 */
public record CorrelationId(String value) {

    /** Largo máximo, igual al de la columna {@code id_correlacion}. */
    public static final int MAX_LENGTH = 64;

    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9._-]{1," + MAX_LENGTH + "}$");

    /**
     * Comprueba que el valor tiene la forma permitida.
     *
     * @throws IllegalArgumentException si el valor es nulo o no cumple el patrón permitido
     */
    public CorrelationId {
        if (value == null || !ALLOWED.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "El identificador de correlación debe tener de 1 a " + MAX_LENGTH + " caracteres de [A-Za-z0-9._-]");
        }
    }

    /**
     * Elige el identificador de la petición si es válido y, si no, el de respaldo.
     *
     * @param requestId {@code X-Request-Id} tal como llegó, puede ser {@code null}
     * @param fallback identificador del evento que se usa cuando el encabezado falta o no es seguro
     * @return el identificador de correlación
     */
    public static CorrelationId fromRequestIdOrElse(String requestId, UUID fallback) {
        // Un encabezado hostil o mal formado nunca llega a la tabla de salida ni a los registros: se descarta sin error.
        if (requestId != null && ALLOWED.matcher(requestId).matches()) {
            return new CorrelationId(requestId);
        }
        return new CorrelationId(fallback.toString());
    }
}
