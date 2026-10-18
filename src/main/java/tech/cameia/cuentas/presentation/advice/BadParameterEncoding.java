package tech.cameia.cuentas.presentation.advice;

import java.util.Locale;
import java.util.Set;

/**
 * Reconoce el rechazo del contenedor ante un parámetro mal codificado ({@code %zz}).
 *
 * <p>La excepción es de Tomcat; se compara el nombre de su clase para que la capa de presentación
 * no dependa del contenedor. Se busca también en la cadena de causas por si un componente la envolvió.</p>
 */
final class BadParameterEncoding {

    private static final String TOMCAT_CLASS = "org.apache.tomcat.util.http.InvalidParameterException";
    private static final String FORM_MEDIA_TYPE = "application/x-www-form-urlencoded";
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final int MAX_CAUSES = 32;

    private BadParameterEncoding() {
    }

    /**
     * Indica si el fallo es el rechazo de Tomcat ante un parámetro mal codificado. Spring suele
     * envolver esa excepción, por eso se recorre la cadena de causas, con un tope para no girar
     * en una cadena cíclica.
     *
     * @param error excepción a examinar; puede ser {@code null}
     * @return {@code true} si la excepción o alguna de sus causas es el rechazo de Tomcat
     */
    static boolean matches(Throwable error) {
        int steps = 0;
        for (Throwable current = error; current != null && steps < MAX_CAUSES; current = current.getCause(), steps++) {
            if (TOMCAT_CLASS.equals(current.getClass().getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Indica si el parámetro malo venía del cuerpo de un formulario y no de la consulta de la URL:
     * el primer caso es un tipo de contenido que Cuentas no admite (415) y el segundo, una petición
     * que sigue su camino normal. Tomcat solo lee el cuerpo como formulario en los métodos con
     * cuerpo y con el tipo {@code application/x-www-form-urlencoded}, sin importar mayúsculas ni
     * parámetros como {@code charset}.
     *
     * @param method método HTTP de la petición; puede ser {@code null}
     * @param contentType tipo de contenido de la petición; puede ser {@code null}
     * @return {@code true} si el parámetro malo venía del cuerpo de un formulario
     */
    static boolean fromFormBody(String method, String contentType) {
        return method != null && BODY_METHODS.contains(method.toUpperCase(Locale.ROOT))
                && contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith(FORM_MEDIA_TYPE);
    }
}
