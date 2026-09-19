package tech.cameia.cuentas.domain.exception;

/**
 * Señala que no existe cuenta local para el usuario indicado.
 *
 * <p>Ocurre, por ejemplo, si alguien tiene credencial en Firebase pero su registro en
 * Cuentas nunca se completó. El mensaje no menciona correos ni identificadores: no aporta
 * nada a quien lo lee y sí a quien sondea el sistema.</p>
 */
public class AccountNotFoundException extends BusinessException {

    private static final String MENSAJE = "No encontramos una cuenta para este usuario";

    /** Crea la excepción con el mensaje que ve la persona. */
    public AccountNotFoundException() {
        super(MENSAJE);
    }
}
