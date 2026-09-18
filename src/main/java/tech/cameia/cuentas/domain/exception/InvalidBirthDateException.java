package tech.cameia.cuentas.domain.exception;

/**
 * Señala que la fecha de nacimiento impide registrar la cuenta.
 *
 * <p>Los criterios de aceptación exigen un mensaje distinto por cada causa, así que el
 * motivo viaja con la excepción: quien la traduce a HTTP no tiene que deducirlo del
 * texto.</p>
 */
public class InvalidBirthDateException extends BusinessException {

    /** Causa concreta del rechazo. */
    public enum Reason {

        /** La persona todavía no cumple 18 años. */
        UNDERAGE,

        /** La fecha está en el futuro, así que es un dato erróneo. */
        IN_THE_FUTURE,

        /** La fecha implica una edad que ninguna persona alcanza. */
        IMPLAUSIBLE
    }

    private final transient Reason reason;

    /**
     * Crea la excepción con su motivo y el mensaje que verá el usuario.
     *
     * @param reason causa del rechazo
     * @param mensaje texto en español que el modal de registro muestra tal cual
     */
    public InvalidBirthDateException(Reason reason, String mensaje) {
        super(mensaje);
        this.reason = reason;
    }

    /**
     * Indica por qué se rechazó la fecha.
     *
     * @return motivo del rechazo
     */
    public Reason getReason() {
        return reason;
    }
}
