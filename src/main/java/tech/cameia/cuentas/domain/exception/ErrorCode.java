package tech.cameia.cuentas.domain.exception;

/**
 * Códigos de error estables que el servicio devuelve en el miembro {@code code} de cada
 * respuesta de error.
 *
 * <p>Un código nunca se renombra ni se reutiliza una vez publicado. La forma es
 * {@code <SUJETO>_<CAUSA>} con un vocabulario de causas cerrado; {@code VALIDATION_FAILED} e
 * {@code INTERNAL_ERROR} son las dos excepciones, porque describen la operación entera y no
 * una causa. Ningún fallo previsible debe terminar en uno de esos dos: cada uno tiene su
 * código propio.</p>
 */
public enum ErrorCode {

    /** La petición tiene campos inválidos; el detalle de cada uno va en la lista {@code errors}. */
    VALIDATION_FAILED,

    /** Falló algo imprevisto que la persona no puede corregir. */
    INTERNAL_ERROR,

    /** Un objeto de valor rechazó un dato sin indicar el campo; respaldo temporal hasta que cada uno tenga su código. */
    REQUEST_INVALID_VALUE,

    /** Falta el nombre. */
    FIRST_NAME_REQUIRED,

    /** El nombre supera el máximo de caracteres. */
    FIRST_NAME_TOO_LONG,

    /** Falta el apellido. */
    LAST_NAME_REQUIRED,

    /** El apellido supera el máximo de caracteres. */
    LAST_NAME_TOO_LONG,

    /** Falta la fecha de nacimiento. */
    BIRTH_DATE_REQUIRED,

    /** La fecha de nacimiento está en el futuro. */
    BIRTH_DATE_IN_THE_FUTURE,

    /** La persona aún no cumple la edad mínima. */
    BIRTH_DATE_UNDERAGE,

    /** La fecha implica una edad que ninguna persona alcanza. */
    BIRTH_DATE_OUT_OF_RANGE,

    /** Falta el correo. */
    EMAIL_REQUIRED,

    /** El correo ya tiene una cuenta. */
    EMAIL_ALREADY_REGISTERED,

    /** Falta la contraseña. */
    PASSWORD_REQUIRED,

    /** La contraseña tiene menos caracteres que el mínimo. */
    PASSWORD_TOO_SHORT,

    /** La contraseña supera el máximo de caracteres. */
    PASSWORD_TOO_LONG,

    /** La contraseña figura entre las más comunes. */
    PASSWORD_TOO_COMMON,

    /** El cuerpo de la petición no se puede interpretar. */
    REQUEST_BODY_INVALID_FORMAT,

    /** Falta el encabezado de identidad que pone el Gateway. */
    IDENTITY_REQUIRED,

    /** El usuario no tiene cuenta local. */
    ACCOUNT_NOT_FOUND,

    /** El correo aún no está verificado. */
    EMAIL_NOT_VERIFIED,

    /** Firebase no está disponible; la persona puede reintentar. */
    DEPENDENCY_UNAVAILABLE,

    /** La ruta solicitada no existe. */
    ROUTE_NOT_FOUND,

    /** El método HTTP no está permitido en esa ruta. */
    METHOD_NOT_ALLOWED,

    /** El tipo de contenido de la petición no se admite. */
    MEDIA_TYPE_NOT_ALLOWED
}
