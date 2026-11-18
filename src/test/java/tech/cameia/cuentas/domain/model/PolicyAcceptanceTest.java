package tech.cameia.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del objeto de valor que guarda la política aceptada y su fecha.
 */
class PolicyAcceptanceTest {

    private static final Instant AHORA = Instant.parse("2026-11-14T15:30:00Z");

    @Test
    @DisplayName("Se crea con una versión y una fecha válidas")
    void shouldCreate_whenVersionAndDateAreValid() {
        PolicyAcceptance aceptacion = new PolicyAcceptance("2026-11-v1", AHORA);

        assertThat(aceptacion.version()).isEqualTo("2026-11-v1");
        assertThat(aceptacion.acceptedAt()).isEqualTo(AHORA);
        assertThat(aceptacion.isAccepted()).isTrue();
    }

    @Test
    @DisplayName("Rechaza una versión nula")
    void shouldReject_whenVersionIsNull() {
        assertThatThrownBy(() -> new PolicyAcceptance(null, AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La versión aceptada debe tener de 1 a 20 caracteres");
    }

    @Test
    @DisplayName("Rechaza una versión formada solo por espacios")
    void shouldReject_whenVersionIsBlank() {
        assertThatThrownBy(() -> new PolicyAcceptance("   ", AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La versión aceptada debe tener de 1 a 20 caracteres");
    }

    @Test
    @DisplayName("Rechaza una versión de más de 20 caracteres")
    void shouldReject_whenVersionIsLongerThan20() {
        assertThatThrownBy(() -> new PolicyAcceptance("a".repeat(21), AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Acepta una versión de exactamente 20 caracteres")
    void shouldAccept_whenVersionIsExactly20() {
        assertThatCode(() -> new PolicyAcceptance("a".repeat(20), AHORA)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Cuenta los caracteres por puntos de código y no por unidades de UTF-16")
    void shouldCountCodePoints_whenVersionHasSupplementaryCharacters() {
        // 20 caracteres de fuera del plano básico ocupan 40 unidades de UTF-16 pero son 20 puntos de código.
        assertThatCode(() -> new PolicyAcceptance("😀".repeat(20), AHORA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new PolicyAcceptance("😀".repeat(21), AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza una fecha nula")
    void shouldReject_whenDateIsNull() {
        assertThatThrownBy(() -> new PolicyAcceptance("2026-11-v1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La fecha de aceptación es obligatoria");
    }

    @Test
    @DisplayName("La versión «sin-aceptar» indica que la persona no aceptó ninguna")
    void isAccepted_shouldBeFalse_whenNotAcceptedVersion() {
        assertThat(new PolicyAcceptance(PolicyAcceptance.NOT_ACCEPTED_VERSION, AHORA).isAccepted()).isFalse();
    }

    @Test
    @DisplayName("No expone fecha de aceptación si la persona no aceptó ninguna versión")
    void acceptedAtIfAccepted_shouldBeEmpty_whenNotAccepted() {
        assertThat(new PolicyAcceptance(PolicyAcceptance.NOT_ACCEPTED_VERSION, AHORA).acceptedAtIfAccepted())
                .isEmpty();
    }

    @Test
    @DisplayName("Expone la fecha de aceptación cuando la persona aceptó una versión")
    void acceptedAtIfAccepted_shouldReturnDate_whenAccepted() {
        assertThat(new PolicyAcceptance("2026-11-v1", AHORA).acceptedAtIfAccepted()).contains(AHORA);
    }
}
