package tech.cameia.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Prueba la única definición de «espacio» y de «carácter» del servicio, sin Spring.
 *
 * <p>Los caracteres especiales se escriben con escapes de Java para que ningún editor los
 * cambie al guardar el archivo.</p>
 */
class SingleLineTextTest {

    @ParameterizedTest(name = "[{index}] se recorta a {1}")
    @CsvSource(delimiter = '|', value = {
        "'  Ana  '|Ana",
        "'\u00A0Ana\u00A0'|Ana",
        "'\tAna\n'|Ana",
        "'\uFEFFAna'|Ana",
        "'\u2003Ana\u3000'|Ana",
        "'\r\nAna\u000B'|Ana"})
    void recortaLosEspaciosQueRecortaElCliente(String recibido, String esperado) {
        assertThat(SingleLineText.normalize(recibido)).isEqualTo(esperado);
        assertThat(new SingleLineText(recibido).value()).isEqualTo(esperado);
    }

    @Test
    void unEspacioDeAnchoCeroNoEsUnEspacioYSeConserva() {
        assertThat(SingleLineText.normalize("\u200BAna")).isEqualTo("\u200BAna");
    }

    @Test
    void conservaLosEspaciosInternosAlNormalizarUnTexto() {
        assertThat(SingleLineText.normalize("María  José")).isEqualTo("María  José");
    }

    @ParameterizedTest(name = "[{index}] el nombre queda {1}")
    @CsvSource(delimiter = '|', value = {
        "'María  José'|María José",
        "'Ana\u00A0\u00A0Luz'|Ana Luz",
        "'  Ana \t Luz  '|Ana Luz",
        "'Ana Luz'|Ana Luz",
        "'Ana\u200B Luz'|Ana\u200B Luz"})
    void unNombreUneLosEspaciosInternosRepetidosEnUno(String recibido, String esperado) {
        assertThat(SingleLineText.normalizeName(recibido)).isEqualTo(esperado);
    }

    @Test
    void unNombreConUnCaracterFueraDelPlanoBasicoNoSeParte() {
        assertThat(SingleLineText.normalizeName("𝒜  𝒜")).isEqualTo("𝒜 𝒜");
    }

    @Test
    void normalizaAFormaNfcYCuentaUnCaracterCompuestoComoUno() {
        SingleLineText texto = new SingleLineText("e\u0301");

        assertThat(texto.value()).isEqualTo("é");
        assertThat(texto.length()).isEqualTo(1);
    }

    @Test
    void cuentaUnCaracterFueraDelPlanoBasicoComoUno() {
        SingleLineText texto = new SingleLineText("𝒜");

        assertThat(texto.length()).isEqualTo(1);
        assertThat(texto.value().length()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\u00A0", "\uFEFF\t\n"})
    void soloEspaciosQuedaVacio(String recibido) {
        assertThat(new SingleLineText(recibido).isEmpty()).isTrue();
        assertThat(new SingleLineText(recibido).length()).isZero();
        assertThat(SingleLineText.normalizeName(recibido)).isEmpty();
    }

    @Test
    void laAusenciaSeConservaAlNormalizar() {
        assertThat(SingleLineText.normalize(null)).isNull();
        assertThat(SingleLineText.normalizeName(null)).isNull();
    }

    @Test
    void elObjetoDeValorNoAdmiteLaAusencia() {
        assertThatThrownBy(() -> new SingleLineText(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void unTextoNoVacioNoEstaVacio() {
        assertThat(new SingleLineText(" a ").isEmpty()).isFalse();
    }
}
