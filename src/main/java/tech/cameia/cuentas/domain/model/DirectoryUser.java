package tech.cameia.cuentas.domain.model;

import java.time.Instant;

/**
 * Credencial de un usuario en el directorio.
 *
 * @param uid identificador del usuario
 * @param createdAt instante en que se creó la credencial
 * @param disabled {@code true} si la credencial está deshabilitada
 */
public record DirectoryUser(String uid, Instant createdAt, boolean disabled) { }
