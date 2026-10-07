package tech.cameia.cuentas.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Comprueba que el catálogo de códigos de error respeta la convención del servicio:
 * {@code <SUJETO>_<CAUSA>} con un vocabulario de causas cerrado, sin repeticiones.
 */
class ErrorCodeTest {

    /** Códigos que describen la operación entera y no una causa concreta. */
    private static final Set<ErrorCode> GENERICOS =
            Set.of(ErrorCode.VALIDATION_FAILED, ErrorCode.INTERNAL_ERROR);

    /** Vocabulario cerrado de causas; una causa nueva se agrega aquí junto con su spec. */
    private static final List<String> CAUSAS = List.of(
            "REQUIRED", "TOO_SHORT", "TOO_LONG", "TOO_COMMON", "INVALID_FORMAT",
            "INVALID_CHARACTERS", "INVALID_VALUE", "OUT_OF_RANGE", "IN_THE_FUTURE", "UNDERAGE",
            "NOT_FOUND", "ALREADY_REGISTERED", "NOT_ALLOWED", "NOT_ACCEPTABLE", "NOT_VERIFIED", "UNAVAILABLE");

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void todoCodigoUsaUnaCausaDelVocabularioCerrado(ErrorCode codigo) {
        if (GENERICOS.contains(codigo)) {
            return;
        }

        assertThat(codigo.name()).matches("^[A-Z]+(_[A-Z]+)+$");
        assertThat(CAUSAS).anyMatch(causa -> codigo.name().endsWith("_" + causa));
    }

    @Test
    void soloLosDosCodigosGenericosQuedanFueraDeLaForma() {
        assertThat(GENERICOS).containsExactlyInAnyOrder(ErrorCode.VALIDATION_FAILED, ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void noHayCodigosRepetidos() {
        long distintos = Arrays.stream(ErrorCode.values()).map(Enum::name).distinct().count();

        assertThat(distintos).isEqualTo(ErrorCode.values().length);
    }
}
