package tech.cameia.cuentas.domain.exception;

/**
 * Señala que el correo ya tiene una credencial en el directorio de usuarios.
 *
 * <p>El mensaje es el que exige el criterio de aceptación CA-1.1.2 y llega tal cual al
 * modal de registro. Se acepta que revele si un correo está registrado: es una decisión
 * de producto documentada en la spec, y el control compensatorio contra el abuso es el
 * límite de peticiones que se aplica antes del Gateway.</p>
 */
public class EmailAlreadyRegisteredException extends BusinessException {

    private static final String MENSAJE = "Este correo ya se encuentra registrado";

    /** Crea la excepción con el mensaje que ve la persona que se registra. */
    public EmailAlreadyRegisteredException() {
        super(MENSAJE);
    }
}
