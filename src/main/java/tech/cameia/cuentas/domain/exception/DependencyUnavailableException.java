package tech.cameia.cuentas.domain.exception;

/**
 * Señala que una dependencia externa (el directorio de usuarios) no respondió o falló de su
 * lado, así que la operación no se pudo completar y la persona puede reintentar.
 *
 * <p>Se distingue de un fallo imprevisto porque la causa está fuera del servicio y es
 * pasajera: el cliente recibe {@code 503} en lugar de {@code 500}. El mensaje es genérico a
 * propósito; la causa técnica viaja como {@link #getCause()} y solo llega al log.</p>
 */
public class DependencyUnavailableException extends BusinessException {

    private static final String MENSAJE = "Ocurrió un error. Inténtalo de nuevo.";

    /**
     * Crea la excepción conservando la causa técnica.
     *
     * @param causa error de la dependencia, para el log
     */
    public DependencyUnavailableException(Throwable causa) {
        super(ErrorCode.DEPENDENCY_UNAVAILABLE, MENSAJE);
        initCause(causa);
    }
}
