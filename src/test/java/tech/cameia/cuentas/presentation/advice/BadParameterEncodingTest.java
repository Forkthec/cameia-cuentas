package tech.cameia.cuentas.presentation.advice;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Comprueba cuándo el rechazo de Tomcat es un formulario mal codificado del cliente. */
class BadParameterEncodingTest {

    @Test
    @DisplayName("Reconoce la excepción de Tomcat")
    void matches_shouldBeTrue_whenTomcatException() {
        assertThat(BadParameterEncoding.matches(new InvalidParameterException("x"))).isTrue();
    }

    @Test
    @DisplayName("La encuentra dentro de la cadena de causas")
    void matches_shouldFindCause_whenWrapped() {
        Throwable wrapped = new RuntimeException("a", new RuntimeException("b", new InvalidParameterException("x")));

        assertThat(BadParameterEncoding.matches(wrapped)).isTrue();
    }

    @Test
    @DisplayName("Otra IllegalStateException no cuenta")
    void matches_shouldBeFalse_whenOtherIllegalState() {
        assertThat(BadParameterEncoding.matches(new IllegalStateException("x"))).isFalse();
    }

    @Test
    @DisplayName("Una excepción nula no cuenta")
    void matches_shouldBeFalse_whenNull() {
        assertThat(BadParameterEncoding.matches(null)).isFalse();
    }

    @Test
    @DisplayName("Una cadena de causas con ciclo termina")
    void matches_shouldStop_whenCauseChainHasCycle() {
        CyclicException first = new CyclicException();
        CyclicException second = new CyclicException();
        first.cause = second;
        second.cause = first;

        assertThat(BadParameterEncoding.matches(first)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "POST|application/x-www-form-urlencoded",
        "PUT|application/x-www-form-urlencoded",
        "PATCH|application/x-www-form-urlencoded",
        "DELETE|application/x-www-form-urlencoded",
        "POST|Application/X-WWW-Form-Urlencoded; charset=UTF-8",
        "delete|application/x-www-form-urlencoded"})
    @DisplayName("Un método con cuerpo y un formulario es un formulario")
    void fromFormBody_shouldBeTrue(String method, String contentType) {
        assertThat(BadParameterEncoding.fromFormBody(method, contentType)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
        "GET|application/x-www-form-urlencoded",
        "POST|application/json",
        "POST|NULL",
        "NULL|application/x-www-form-urlencoded"})
    @DisplayName("Sin cuerpo de formulario no lo es")
    void fromFormBody_shouldBeFalse(String method, String contentType) {
        assertThat(BadParameterEncoding.fromFormBody(method, contentType)).isFalse();
    }

    /** Excepción cuya causa se asigna después, para armar un ciclo. */
    private static final class CyclicException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private transient Throwable cause;

        @Override
        public synchronized Throwable getCause() {
            return cause;
        }
    }
}
