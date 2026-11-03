package tech.cameia.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.InvalidPersonNameException;

/**
 * Prueba la regla de caracteres de nombres y apellidos sin Spring.
 *
 * <p>Los caracteres que un editor podría cambiar (espacios especiales, combinantes, guiones
 * largos) se escriben con escapes de Java.</p>
 */
class PersonNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"Ana3", "Pérez_", "---", "'", "’", "Ana.", "Ana@", "<script>", "12345",
        "Ana–Luz", "Ana😀", "Ana\u0000", "Ana\u200BLuz", "Ana/Luz", "Jr."})
    void unNombreConCaracteresNoAdmitidosSeRechazaConElCodigoDelNombre(String nombre) {
        assertThatThrownBy(() -> new PersonName(nombre, PersonName.Part.FIRST_NAME))
                .isInstanceOf(InvalidPersonNameException.class)
                .hasMessage("El nombre solo puede contener letras, espacios, apóstrofo y guion.")
                .satisfies(error -> {
                    InvalidPersonNameException rechazo = (InvalidPersonNameException) error;
                    assertThat(rechazo.getErrorCode()).isEqualTo(ErrorCode.FIRST_NAME_INVALID_CHARACTERS);
                    assertThat(rechazo.getField()).isEqualTo("firstName");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pérez_", "Ana3", "---"})
    void unApellidoConCaracteresNoAdmitidosSeRechazaConElCodigoDelApellido(String apellido) {
        assertThatThrownBy(() -> new PersonName(apellido, PersonName.Part.LAST_NAME))
                .isInstanceOf(InvalidPersonNameException.class)
                .hasMessage("El apellido solo puede contener letras, espacios, apóstrofo y guion.")
                .satisfies(error -> {
                    InvalidPersonNameException rechazo = (InvalidPersonNameException) error;
                    assertThat(rechazo.getErrorCode()).isEqualTo(ErrorCode.LAST_NAME_INVALID_CHARACTERS);
                    assertThat(rechazo.getField()).isEqualTo("lastName");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"María José", "O'Neill", "O’Neill", "Gómez-Ruiz", "Müller", "Muñoz", "A", "B",
        "Ñandú", "李", "Åsa", "Zoë", "Ана", "שרה"})
    void unNombreSoloConLetrasEspaciosApostrofoYGuionSeAcepta(String nombre) {
        assertThat(new PersonName(nombre, PersonName.Part.FIRST_NAME).value()).isEqualTo(nombre);
    }

    @ParameterizedTest(name = "[{index}] {0} queda {1}")
    @CsvSource(delimiter = '|', value = {
        "'María  José'|María José",
        "'  Ana  '|Ana",
        "'Jose\u0301'|José",
        "'\u00A0Ana\u00A0Luz\u00A0'|Ana Luz"})
    void seGuardaRecortadoEnNfcYConLosEspaciosInternosUnidos(String recibido, String esperado) {
        assertThat(new PersonName(recibido, PersonName.Part.LAST_NAME).value()).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-Ana", "Ana-", "Ana--Luz", "Ana ’ -", "'Ana'"})
    void laReglaSoloMiraLosCaracteresYNoSuPosicion(String nombre) {
        // Con al menos una letra y solo caracteres admitidos, la posición del guion o del
        // apóstrofo no se valida: el criterio no lo pide.
        assertThat(new PersonName(nombre, PersonName.Part.FIRST_NAME).value()).isEqualTo(nombre);
    }

    @Test
    void admiteExactamente120Letras() {
        assertThat(new PersonName("a".repeat(120), PersonName.Part.FIRST_NAME).value()).hasSize(120);
    }

    @Test
    void unaLetraQueSoloExisteConAcentoCombinanteSeAcepta() {
        // «g» con tilde combinante no tiene forma precompuesta: sigue siendo letra + marca en NFC.
        assertThat(new PersonName("g\u0303", PersonName.Part.FIRST_NAME).value()).isEqualTo("g\u0303");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\u00A0"})
    void laAusenciaEsUnaInvarianteDefensiva(String nombre) {
        assertThatThrownBy(() -> new PersonName(nombre, PersonName.Part.FIRST_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre es obligatorio");
    }

    @Test
    void masDe120LetrasEsUnaInvarianteDefensiva() {
        assertThatThrownBy(() -> new PersonName("a".repeat(121), PersonName.Part.FIRST_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre supera 120 caracteres");
    }

    @Test
    void unNombreLargoConCaracteresNoAdmitidosNoTardaEnEvaluarse() {
        // La expresión no tiene grupos anidados: un texto adverso de 120 caracteres se evalúa en lineal.
        String adverso = "a".repeat(119) + "!";

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> new PersonName(adverso, PersonName.Part.FIRST_NAME))
                .isInstanceOf(InvalidPersonNameException.class);
        assertThat(System.nanoTime() - inicio).isLessThan(1_000_000_000L);
    }

    @ParameterizedTest
    @EnumSource(PersonName.Part.class)
    void cadaParteLlevaUnCodigoDeCaracteresNoAdmitidos(PersonName.Part parte) {
        assertThat(parte.code().name()).endsWith("_INVALID_CHARACTERS");
        assertThat(parte.message()).endsWith("solo puede contener letras, espacios, apóstrofo y guion.");
    }
}
