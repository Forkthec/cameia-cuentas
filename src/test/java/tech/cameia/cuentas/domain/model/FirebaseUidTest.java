package tech.cameia.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;

/** Comprueba qué identidades del Gateway acepta el objeto de valor. */
class FirebaseUidTest {

    static Stream<String> controlCharacters() {
        return Stream.of("uid\t1", "\u0085", "\u009f", "a\u0000", "\n", "\u007f");
    }

    static Stream<String> printableIdentities() {
        return Stream.of("uid-123_ABC", "uid-ñ", "a".repeat(128));
    }

    @ParameterizedTest
    @MethodSource("controlCharacters")
    @DisplayName("Rechaza una identidad con caracteres de control")
    void required_shouldThrow_whenControlCharacter(String raw) {
        assertThatThrownBy(() -> FirebaseUid.required(raw))
                .isInstanceOfSatisfying(IdentityRequiredException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.IDENTITY_REQUIRED));
    }

    @ParameterizedTest
    @MethodSource("printableIdentities")
    @DisplayName("Acepta una identidad imprimible de hasta 128 caracteres")
    void required_shouldAccept_whenPrintable(String raw) {
        assertThat(FirebaseUid.required(raw).value()).isEqualTo(raw);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Rechaza una identidad nula o en blanco")
    void required_shouldThrow_whenNullOrBlank(String raw) {
        assertThatThrownBy(() -> FirebaseUid.required(raw)).isInstanceOf(IdentityRequiredException.class);
    }

    @Test
    @DisplayName("Rechaza una identidad de 129 caracteres y acepta la de 128")
    void required_shouldThrow_whenTooLong() {
        assertThat(FirebaseUid.required("a".repeat(128)).value()).hasSize(128);
        assertThatThrownBy(() -> FirebaseUid.required("a".repeat(129))).isInstanceOf(IdentityRequiredException.class);
    }
}
