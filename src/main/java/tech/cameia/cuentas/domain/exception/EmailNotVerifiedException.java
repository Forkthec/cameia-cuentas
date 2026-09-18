package tech.cameia.cuentas.domain.exception;

/**
 * Señala que la operación exige un correo ya verificado y el del usuario no lo está.
 *
 * <p>La verificación la certifica el token de Firebase, nunca la palabra del navegador.</p>
 */
public class EmailNotVerifiedException extends BusinessException {

    private static final String MENSAJE =
            "Primero debes verificar tu correo con el enlace que te enviamos";

    /** Crea la excepción con el mensaje que ve la persona. */
    public EmailNotVerifiedException() {
        super(MENSAJE);
    }
}
