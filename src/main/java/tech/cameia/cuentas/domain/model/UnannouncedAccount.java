package tech.cameia.cuentas.domain.model;

import java.time.Instant;

/**
 * Fila de cuenta que nunca tuvo su evento de cuenta creada: las cuentas anteriores a que existieran los eventos, o aquellas
 * cuyo evento se borró para republicarlo.
 *
 * @param firebaseUid identificador del usuario en Firebase Auth, de hasta {@value #FIREBASE_UID_MAX_LENGTH} caracteres
 * @param birthDate fecha de nacimiento declarada en el registro
 * @param createdAt instante en que se creó la cuenta, que será el de su evento
 */
public record UnannouncedAccount(String firebaseUid, BirthDate birthDate, Instant createdAt) {

    /** Largo máximo del identificador de Firebase; coincide con la columna {@code firebase_uid} y con {@code agregado_id}. */
    public static final int FIREBASE_UID_MAX_LENGTH = 128;
}
