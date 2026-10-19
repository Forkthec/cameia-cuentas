package tech.cameia.cuentas.domain.model;

import tech.cameia.cuentas.domain.exception.IdentityRequiredException;

/**
 * Identificador del usuario en Firebase Authentication, tal como lo propaga el Gateway en
 * {@code X-User-Id}. Reúne la comprobación que antes se repetía en cada caso de uso.
 *
 * @param value identificador ya comprobado
 */
public record FirebaseUid(String value) {

    /** Largo máximo de un identificador de Firebase; es el de la columna {@code firebase_uid}. */
    public static final int MAX_LENGTH = 128;

    /**
     * Convierte la identidad que llega del Gateway o rechaza la petición si falta o no es válida.
     *
     * <p>Un identificador de Firebase es ASCII, así que contar unidades UTF-16 equivale a contar puntos de código.</p>
     *
     * @param raw valor del encabezado {@code X-User-Id}
     * @return la identidad comprobada
     * @throws IdentityRequiredException si es nula, está en blanco, mide más de 128 caracteres o trae caracteres de control
     */
    public static FirebaseUid required(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_LENGTH || raw.chars().anyMatch(Character::isISOControl)) {
            throw new IdentityRequiredException();
        }
        return new FirebaseUid(raw);
    }
}
