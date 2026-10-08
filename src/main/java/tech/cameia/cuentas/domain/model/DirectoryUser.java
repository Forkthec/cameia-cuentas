package tech.cameia.cuentas.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Credencial de un usuario en el directorio.
 *
 * @param uid identificador del usuario
 * @param createdAt instante en que se creó la credencial
 * @param disabled {@code true} si la credencial está deshabilitada
 */
public record DirectoryUser(String uid, Instant createdAt, boolean disabled) {

    /** Exige identificador y fecha de creación: el directorio siempre los informa. */
    public DirectoryUser {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
