package tech.cameia.cuentas.domain.model;

import java.time.LocalDate;

/**
 * Fecha de nacimiento declarada en el registro.
 *
 * <p>El tipo solo garantiza que hay una fecha real: convertir el texto
 * {@code DD/MM/YYYY} que envía el cliente es tarea de la capa de presentación, y decidir
 * si esa fecha permite registrarse es tarea de
 * {@code tech.cameia.cuentas.domain.policy.AgePolicy}.</p>
 *
 * @param value fecha de nacimiento
 */
public record BirthDate(LocalDate value) {

    /**
     * Comprueba que llegó una fecha.
     *
     * @throws IllegalArgumentException si es nula
     */
    public BirthDate {
        if (value == null) {
            throw new IllegalArgumentException("La fecha de nacimiento es obligatoria");
        }
    }
}
