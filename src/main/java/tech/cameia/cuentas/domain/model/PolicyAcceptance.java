package tech.cameia.cuentas.domain.model;

import java.time.Instant;
import java.util.Optional;

/**
 * Versión de la política de tratamiento de datos que aceptó la persona y el instante en que la aceptó.
 *
 * <p>Las cuentas creadas antes de que existiera la aceptación guardan la versión {@link #NOT_ACCEPTED_VERSION}
 * y la fecha de su creación: no aceptaron ninguna versión y la aplicación les pide aceptar la vigente.</p>
 *
 * @param version versión aceptada, de 1 a 20 caracteres
 * @param acceptedAt instante UTC de la aceptación
 */
public record PolicyAcceptance(String version, Instant acceptedAt) {

    /** Versión que marca a las cuentas que nunca aceptaron una política. */
    public static final String NOT_ACCEPTED_VERSION = "sin-aceptar";

    /** Largo máximo de la versión: el de la columna {@code politica_version}. */
    public static final int MAX_VERSION_LENGTH = 20;

    /**
     * Crea la aceptación comprobando sus invariantes.
     *
     * @throws IllegalArgumentException si la versión es nula, está en blanco o supera 20 caracteres, o si falta la fecha
     */
    public PolicyAcceptance {
        if (version == null || version.isBlank()
                || version.codePointCount(0, version.length()) > MAX_VERSION_LENGTH) {
            throw new IllegalArgumentException("La versión aceptada debe tener de 1 a 20 caracteres");
        }
        if (acceptedAt == null) {
            throw new IllegalArgumentException("La fecha de aceptación es obligatoria");
        }
    }

    /** @return {@code true} si la persona aceptó alguna versión */
    public boolean isAccepted() {
        return !NOT_ACCEPTED_VERSION.equals(version);
    }

    /** @return la fecha de aceptación, o vacío si la cuenta nunca aceptó una versión */
    public Optional<Instant> acceptedAtIfAccepted() {
        return isAccepted() ? Optional.of(acceptedAt) : Optional.empty();
    }
}
