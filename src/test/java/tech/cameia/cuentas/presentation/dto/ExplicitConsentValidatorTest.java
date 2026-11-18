package tech.cameia.cuentas.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprueba que solo el booleano {@code true} cuenta como autorización.
 */
@DisplayName("ExplicitConsentValidator")
class ExplicitConsentValidatorTest {

    private final ExplicitConsentValidator validator = new ExplicitConsentValidator();

    @Test
    @DisplayName("acepta el booleano true")
    void isValid_shouldReturnTrue_whenValueIsBooleanTrue() {
        assertThat(validator.isValid(Boolean.TRUE, null)).isTrue();
    }

    @Test
    @DisplayName("rechaza false")
    void isValid_shouldReturnFalse_whenValueIsFalse() {
        assertThat(validator.isValid(Boolean.FALSE, null)).isFalse();
    }

    @Test
    @DisplayName("rechaza null")
    void isValid_shouldReturnFalse_whenValueIsNull() {
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @Test
    @DisplayName("rechaza el texto «true»")
    void isValid_shouldReturnFalse_whenValueIsText() {
        assertThat(validator.isValid("true", null)).isFalse();
    }

    @Test
    @DisplayName("rechaza el número 1")
    void isValid_shouldReturnFalse_whenValueIsNumber() {
        assertThat(validator.isValid(1, null)).isFalse();
    }

    @Test
    @DisplayName("rechaza un arreglo y un objeto")
    void isValid_shouldReturnFalse_whenValueIsArrayOrObject() {
        assertThat(validator.isValid(List.of(), null)).isFalse();
        assertThat(validator.isValid(Map.of(), null)).isFalse();
    }
}
