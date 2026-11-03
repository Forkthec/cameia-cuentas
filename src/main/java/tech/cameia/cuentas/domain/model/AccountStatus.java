package tech.cameia.cuentas.domain.model;

/**
 * Estados por los que pasa una cuenta local.
 *
 * <p>La lista coincide con la restricción {@code ck_cuenta_estado} de la tabla
 * {@code microcuentas.cuenta}: cualquier valor nuevo exige una migración, porque la base
 * de datos es la red de seguridad de esta máquina de estados.</p>
 */
public enum AccountStatus {

    /**
     * Cuenta recién registrada cuyo correo todavía no se ha verificado. Es el estado
     * inicial obligatorio: hasta que el correo se verifique, la plataforma no debe tratar
     * la cuenta como utilizable.
     */
    PENDING_VERIFICATION,

    /** Cuenta verificada y en uso normal. */
    ACTIVE,

    /**
     * Cuenta bloqueada por una decisión del negocio, por ejemplo un impago. No es lo
     * mismo que una cuenta sin verificar.
     */
    DISABLED,

    /**
     * Cuenta cuyos datos personales fueron borrados al ejercer el derecho al olvido. Es
     * un estado terminal.
     */
    ANONYMIZED
}
