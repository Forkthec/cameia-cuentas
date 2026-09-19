package tech.cameia.cuentas.presentation.advice;

/**
 * Da acceso al manejador de errores desde las pruebas de los controladores.
 *
 * <p>{@code BusinessExceptionHandler} es de alcance de paquete a propósito: nadie fuera de
 * {@code presentation.advice} debería instanciarlo. Esta clase vive en ese mismo paquete,
 * pero en el código de pruebas, y solo sirve para armar el {@code MockMvc} con el mismo
 * manejador que usa la aplicación. Sin ella, las pruebas comprobarían el formato de error
 * de un manejador distinto al real.</p>
 */
public final class ProblemDetailTestSupport {

    private ProblemDetailTestSupport() {
    }

    /**
     * Crea el manejador de errores real.
     *
     * @return manejador que traduce las excepciones a {@code application/problem+json}
     */
    public static Object manejadorDeErrores() {
        return new BusinessExceptionHandler();
    }
}
