package tech.cameia.cuentas.domain.exception;

/**
 * Señala que la petición no trae una identidad utilizable: el encabezado del Gateway llegó vacío,
 * solo con espacios o más largo que un identificador de Firebase.
 *
 * <p>Responde igual que el encabezado ausente: para quien llama es la misma falta, la identidad
 * que debía poner el Gateway.</p>
 */
public class IdentityRequiredException extends BusinessException {

    private static final String MESSAGE = "La petición no incluye los datos que exige esta ruta";

    /** Crea la excepción con el mensaje del encabezado ausente. */
    public IdentityRequiredException() {
        super(ErrorCode.IDENTITY_REQUIRED, MESSAGE);
    }
}
