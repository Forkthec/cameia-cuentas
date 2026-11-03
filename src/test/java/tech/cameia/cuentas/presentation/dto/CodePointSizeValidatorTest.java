package tech.cameia.cuentas.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Prueba la restricción de longitud en puntos de código, con el máximo de 5 y textos que
 * ocupan una o dos unidades {@code char} por carácter.
 */
class CodePointSizeValidatorTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    /** Objeto mínimo con la restricción. */
    record Caso(@CodePointSize(max = 5) String texto) {
    }

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarFabrica() {
        fabrica.close();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abcd", "abcde", "𝒜𝒜𝒜𝒜𝒜",
        "ñññññ"})
    void hastaElMaximoEsValido(String texto) {
        assertThat(validador.validate(new Caso(texto))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdef", "𝒜𝒜𝒜𝒜𝒜𝒜"})
    void unoMasQueElMaximoEsInvalido(String texto) {
        assertThat(validador.validate(new Caso(texto))).hasSize(1);
    }
}
