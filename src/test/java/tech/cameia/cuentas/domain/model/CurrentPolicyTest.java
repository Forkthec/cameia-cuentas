package tech.cameia.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.PolicyVersionOutdatedException;

/**
 * Pruebas de la versión vigente de la política: su formato y su comparación.
 */
class CurrentPolicyTest {

    private final CurrentPolicy vigente = new CurrentPolicy("2026-11-v1");

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-v1", "2026-01-v999", "2026-12-v10"})
    @DisplayName("Acepta una versión con el formato AAAA-MM-vN")
    void shouldCreate_whenFormatIsValid(String version) {
        assertThat(new CurrentPolicy(version).version()).isEqualTo(version);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2026-13-v1", "2026-00-v1", "2026-11-v0", "2026-11-v01", "2026-11-v1000", "26-11-v1",
            "sin-aceptar", "2026-11-V1", " 2026-11-v1", "2026-11-v1\n"})
    @DisplayName("Rechaza una versión que no cumple el formato")
    void shouldReject_whenFormatIsInvalid(String version) {
        assertThatThrownBy(() -> new CurrentPolicy(version))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AAAA-MM-vN");
    }

    @Test
    @DisplayName("No lanza nada si la versión recibida es la vigente")
    void requireCurrent_shouldPass_whenEqual() {
        assertThatCode(() -> vigente.requireCurrent("2026-11-v1")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2026-10-v1", "2026-11-V1", "2026-11-ｖ" + "1", "sin-aceptar", "<script>",
            "' OR 1=1 --"})
    @DisplayName("Lanza «política desactualizada» si la versión recibida es distinta de la vigente")
    void requireCurrent_shouldThrowOutdated_whenDifferent(String candidata) {
        assertThatThrownBy(() -> vigente.requireCurrent(candidata))
                .isInstanceOfSatisfying(PolicyVersionOutdatedException.class, error -> {
                    assertThat(error.getField()).isEqualTo("policyVersion");
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.POLICY_VERSION_OUTDATED);
                    assertThat(error.getMessage())
                            .isEqualTo("La política cambió. Recarga la página y vuelve a aceptarla.");
                });
    }
}
