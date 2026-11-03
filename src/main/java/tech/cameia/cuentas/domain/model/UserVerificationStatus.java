package tech.cameia.cuentas.domain.model;

/**
 * Estado del correo de un usuario según el directorio de usuarios.
 *
 * <p>{@link #NOT_FOUND} no es un error: la credencial pudo borrarse antes, y quien la consulta decide qué hacer.</p>
 */
public enum UserVerificationStatus {

    /** El usuario existe y verificó su correo. */
    VERIFIED,

    /** El usuario existe y no ha verificado su correo. */
    NOT_VERIFIED,

    /** El directorio no tiene a ese usuario. */
    NOT_FOUND
}
