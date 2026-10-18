package tech.cameia.cuentas.presentation.advice;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Atiende el despacho de error del contenedor ({@code /error}) para que lo que Tomcat reporta con
 * {@code sendError} (por ejemplo un {@code TRACE}) salga con la forma común del servicio y no con el
 * cuerpo por defecto de Spring Boot ({@code timestamp}, {@code error}, {@code path}).
 *
 * <p>No arma la respuesta: lanza la excepción del framework que corresponde al estado original y la
 * resuelve {@link BusinessExceptionHandler}, de modo que el {@code requestId}, el {@code charset} y el
 * registro salen del mismo lugar que el resto de errores.</p>
 */
@Hidden
@RestController
class ContainerErrorController implements ErrorController {

    /**
     * Traduce el estado que reportó el contenedor a la excepción del framework equivalente.
     *
     * <p>Sin estado original (alguien pidió {@code /error} directamente) se responde como una ruta
     * inexistente. La ruta original no se pasa a la excepción: puede traer una codificación inválida
     * ({@code %zz}) que no debe llegar a la respuesta ni al registro.</p>
     *
     * <p>Un estado sin código propio (por ejemplo 413 o 503) termina en el error genérico; si el
     * contenedor informó la excepción que lo causó, viaja como causa para que el registro la conserve.</p>
     *
     * @param request petición del despacho de error, con el estado original como atributo
     * @throws Exception la excepción del framework que resuelve {@link BusinessExceptionHandler}
     */
    @RequestMapping("/error")
    void error(HttpServletRequest request) throws Exception {
        Integer status = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String method = request.getMethod();
        switch (status == null ? 0 : status) {
            case 0, 404 -> throw new NoResourceFoundException(HttpMethod.GET, "/", "");
            case 405 -> throw new HttpRequestMethodNotSupportedException(method);
            case 406 -> throw new HttpMediaTypeNotAcceptableException("");
            case 415 -> throw new HttpMediaTypeNotSupportedException("");
            // Un 400 del contenedor es un dato del cliente que no se pudo leer: sale como cuerpo ilegible.
            case 400 -> throw new HttpMessageNotReadableException("", (HttpInputMessage) null);
            // Se conserva la excepción original del contenedor (si la hay) como causa, para que el registro del 500 la muestre.
            default -> throw new IllegalStateException("Error del contenedor sin código propio: estado=" + status,
                    request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable cause ? cause : null);
        }
    }
}
