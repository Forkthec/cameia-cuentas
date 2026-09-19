package tech.cameia.cuentas.domain.exception;

/**
 * Raíz de las excepciones de negocio del microservicio.
 *
 * <p>Separa un fallo previsto por las reglas del dominio, que el cliente puede corregir,
 * de un fallo técnico. La capa de presentación traduce cada subclase a su estado HTTP y a
 * un documento Problem Details; ninguna de ellas debe incluir datos sensibles en su
 * mensaje, porque ese texto llega al usuario.</p>
 */
public abstract class BusinessException extends RuntimeException {

    /**
     * Crea la excepción con el mensaje que verá el usuario.
     *
     * @param mensaje texto en español, sin credenciales ni datos de la petición
     */
    protected BusinessException(String mensaje) {
        super(mensaje);
    }
}
