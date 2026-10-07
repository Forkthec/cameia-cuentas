package tech.cameia.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.InvalidPronounException;

/**
 * Prueba la lectura del pronombre tal como lo escribe el contrato (CA-1.1.15 y CA-1.1.43).
 */
class PronounTest {

    @ParameterizedTest
    @EnumSource(Pronoun.class)
    void cadaOpcionSeLeeConSuNombreExacto(Pronoun opcion) {
        assertThat(Pronoun.fromContract(opcion.name())).isEqualTo(opcion);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OTRO", "he", "She", " THEY", "1", "0", "true", "ÉL"})
    void cualquierOtroTextoNoEsUnaOpcion(String valor) {
        assertThatThrownBy(() -> Pronoun.fromContract(valor))
                .isInstanceOfSatisfying(InvalidPronounException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRONOUN_INVALID_VALUE);
                    assertThat(error.getField()).isEqualTo("pronoun");
                })
                .hasMessage("Selecciona una opción.");
    }
}
