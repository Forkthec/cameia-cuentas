package tech.cameia.cuentas.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pruebas del identificador de correlación: reutiliza el {@code X-Request-Id} solo cuando es seguro y, si no, usa el
 * identificador del evento.
 */
class CorrelationIdTest {

    private static final UUID FALLBACK = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @ParameterizedTest
    @ValueSource(strings = {"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601", "req.1_A-b", "a"})
    @DisplayName("Conserva el identificador de la petición cuando es seguro")
    void fromRequestIdOrElse_shouldKeepRequestId_whenValid(String requestId) {
        assertThat(CorrelationId.fromRequestIdOrElse(requestId, FALLBACK).value()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("Conserva el identificador de la petición cuando mide exactamente 64 caracteres")
    void fromRequestIdOrElse_shouldKeepRequestId_whenExactly64Characters() {
        String requestId = "a".repeat(64);

        assertThat(CorrelationId.fromRequestIdOrElse(requestId, FALLBACK).value()).isEqualTo(requestId);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t", "\n", "abc def", "<script>", "ñandú", "a%b", "a/b"})
    @DisplayName("Usa el identificador del evento cuando el de la petición falta o no es seguro")
    void fromRequestIdOrElse_shouldUseFallback_whenUnsafe(String requestId) {
        assertThat(CorrelationId.fromRequestIdOrElse(requestId, FALLBACK).value())
                .isEqualTo("11111111-1111-4111-8111-111111111111");
    }

    @Test
    @DisplayName("Usa el identificador del evento cuando el de la petición mide 65 caracteres")
    void fromRequestIdOrElse_shouldUseFallback_whenRequestIdHas65Characters() {
        assertThat(CorrelationId.fromRequestIdOrElse("a".repeat(65), FALLBACK).value())
                .isEqualTo(FALLBACK.toString());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"a b", "", "a/b"})
    @DisplayName("El constructor rechaza un valor que no cumple el patrón")
    void constructor_shouldReject_whenValueIsInvalid(String value) {
        assertThatThrownBy(() -> new CorrelationId(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
