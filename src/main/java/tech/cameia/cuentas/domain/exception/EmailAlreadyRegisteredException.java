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

    private static final String MENSAJE = "Ese correo ya tiene una cuenta.";

    private final String firebaseUid;
    private final boolean requiresReconciliation;

    /** Crea la excepción con el mensaje que ve la persona que se registra. */
    public EmailAlreadyRegisteredException() {
        this(null, false);
    }

    /**
     * Crea la excepción para una credencial que existe pero no tiene cuenta local.
     *
     * <p>El identificador es interno: no sale en la respuesta y solo sirve para el registro
     * del error.</p>
     *
     * @param firebaseUid identificador de la credencial, o {@code null} si no aplica
     * @param requiresReconciliation {@code true} si la credencial lleva tanto tiempo sin
     *        cuenta que ya no puede ser un registro en curso y alguien debe conciliarla
     */
    public EmailAlreadyRegisteredException(String firebaseUid, boolean requiresReconciliation) {
        super(ErrorCode.EMAIL_ALREADY_REGISTERED, MENSAJE);
        this.firebaseUid = firebaseUid;
        this.requiresReconciliation = requiresReconciliation;
    }

    /**
     * Identificador interno de la credencial sin cuenta local.
     *
     * @return el identificador, o {@code null} si el conflicto no viene de una credencial sin cuenta
     */
    public String getFirebaseUid() {
        return firebaseUid;
    }

    /**
     * Indica si la credencial sin cuenta local debe conciliarse a mano.
     *
     * @return {@code true} si el conflicto se registra como error y no como aviso
     */
    public boolean requiresReconciliation() {
        return requiresReconciliation;
    }
}
