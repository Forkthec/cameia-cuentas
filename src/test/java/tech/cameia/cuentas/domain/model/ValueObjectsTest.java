package tech.cameia.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.InvalidPhoneNumberException;

/**
 * Prueba de los objetos de valor del registro descritos en
 * {@code specs/CM-14-RegistroUsuario/spec.md} (REQ-CU-01 y REQ-CU-11).
 *
 * <p>Van juntos porque cada uno tiene una sola responsabilidad y pocas reglas; separarlos
 * en cinco archivos de dos pruebas cada uno no aportaría claridad.</p>
 */
class ValueObjectsTest {

    @Nested
    class CorreoElectronico {

        @Test
        void seNormalizaAMinusculasYSinEspacios() {
            EmailAddress correo = new EmailAddress("  Ana.Perez@CAMEIA.TECH ");

            assertThat(correo.value()).isEqualTo("ana.perez@cameia.tech");
        }

        @Test
        void rechazaUnValorSinArroba() {
            assertThatThrownBy(() -> new EmailAddress("ana.cameia.tech"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rechazaUnValorSinDominio() {
            assertThatThrownBy(() -> new EmailAddress("ana@cameia"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rechazaUnValorVacio() {
            assertThatThrownBy(() -> new EmailAddress("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El correo electrónico es obligatorio");
        }

        @ParameterizedTest
        @ValueSource(strings = {"  Ana@Correo.CO ", " ana@correo.co ", "﻿ANA@CORREO.CO\t"})
        void quitaLosEspaciosQueQuitaElClienteYPasaAMinusculas(String recibido) {
            assertThat(new EmailAddress(recibido).value()).isEqualTo("ana@correo.co");
        }

        @ParameterizedTest
        @ValueSource(strings = {" ", "﻿", "\t"})
        void soloEspaciosDelClienteEsObligatorio(String recibido) {
            assertThatThrownBy(() -> new EmailAddress(recibido))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El correo electrónico es obligatorio");
        }

        @Test
        void rechazaLaAusencia() {
            assertThatThrownBy(() -> new EmailAddress(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El correo electrónico es obligatorio");
        }

        @Test
        void admiteExactamente254PuntosDeCodigoYRechaza255() {
            assertThat(new EmailAddress("a".repeat(248) + "@b.com").value()).hasSize(254);
            assertThatThrownBy(() -> new EmailAddress("a".repeat(249) + "@b.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El correo electrónico es demasiado largo");
        }

        @Test
        void cuentaLaLongitudEnPuntosDeCodigoYNoEnUnidadesChar() {
            // 126 letras matemáticas son 252 unidades char pero 126 puntos de código: 132 en total.
            String correo = "𝒜".repeat(126) + "@b.com";

            assertThat(new EmailAddress(correo).value()).isEqualTo(correo);
        }

        @Test
        void dosCorreosQueSoloDifierenEnLaFormaUnicodeSonElMismo() {
            assertThat(new EmailAddress("josé@correo.co").value())
                    .isEqualTo(new EmailAddress("josé@correo.co").value());
        }

        @Test
        void unaMayusculaQueCambiaDeFormaAlPasarAMinusculasQuedaEnNfc() {
            // «İ» (I con punto) en minúsculas de Locale.ROOT es «i» + punto combinante.
            String valor = new EmailAddress("İris@correo.co").value();

            assertThat(valor).isEqualTo(java.text.Normalizer.normalize(valor, java.text.Normalizer.Form.NFC));
            assertThat(valor).isEqualTo("i̇ris@correo.co");
        }
    }

    @Nested
    class Celular {

        @Test
        void admiteUnNumeroEnFormatoE164() {
            assertThat(new PhoneNumber("+573001234567").value()).isEqualTo("+573001234567");
        }

        @Test
        void rechazaUnNumeroSinIndicativoDePais() {
            assertThatThrownBy(() -> new PhoneNumber("3001234567"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rechazaUnNumeroConSeparadores() {
            assertThatThrownBy(() -> new PhoneNumber("+57 300 123 4567"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"+573000000000", "+34612345678", "+576012345678", "+14155552671", "+573001234567"})
        void unNumeroNuevoValidoParaSuPaisSeAcepta(String numero) {
            assertThat(PhoneNumber.fromInput(numero).value()).isEqualTo(numero);
        }

        @ParameterizedTest
        @ValueSource(strings = {"12345", "+57300", "3000000000", "+57 300 000 0000", "+99912345678",
            "+5730000000000000", "+573000000000abc", "+573000000000;ext=12", "+0573000000000", "+5730000000000"})
        void unNumeroNuevoQueNoEsValidoParaSuPaisSeRechazaConSuCodigoYSinRepetirlo(String numero) {
            assertThatThrownBy(() -> PhoneNumber.fromInput(numero))
                    .isInstanceOf(InvalidPhoneNumberException.class)
                    .hasMessage("Revisa el número, no coincide con el formato del país elegido.")
                    .hasMessageNotContaining(numero)
                    .satisfies(error -> {
                        assertThat(((InvalidPhoneNumberException) error).getErrorCode())
                                .isEqualTo(ErrorCode.PHONE_NUMBER_INVALID_FORMAT);
                        assertThat(((InvalidPhoneNumberException) error).getField()).isEqualTo("phoneNumber");
                    });
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        void sinNumeroNoEsUnNumeroNuevo(String numero) {
            assertThatThrownBy(() -> PhoneNumber.fromInput(numero)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void unNumeroConElPrefijoNacionalSobranteNoEstaEnSuFormaCanonicaYSeRechaza() {
            // La librería quita el 0 nacional y da el número por válido, pero su forma canónica es
            // +447400123456: el texto recibido no es ese número tal como se guardaría.
            assertThatThrownBy(() -> PhoneNumber.fromInput("+4407400123456"))
                    .isInstanceOf(InvalidPhoneNumberException.class);
            assertThat(PhoneNumber.fromInput("+447400123456").value()).isEqualTo("+447400123456");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "  "})
        void unNumeroGuardadoNoPuedeFaltar(String numero) {
            assertThatThrownBy(() -> new PhoneNumber(numero))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El número de celular es obligatorio cuando se envía");
        }

        @Test
        void unNumeroYaGuardadoQueSoloCumpleLaFormaSeReconstruyeIgual() {
            // +57300 no existe para su país, pero cumple la forma de la base: una fila antigua
            // con ese valor se sigue leyendo; solo un número nuevo pasa por la regla del país.
            assertThat(new PhoneNumber("+5730000000").value()).isEqualTo("+5730000000");
            assertThatThrownBy(() -> PhoneNumber.fromInput("+5730000000")).isInstanceOf(InvalidPhoneNumberException.class);
        }
    }

    @Nested
    class Contrasenia {

        @Test
        void noRevelaSuValorAlConvertirseATexto() {
            RawPassword password = new RawPassword("frase secreta larga");

            assertThat(password.toString()).doesNotContain("frase secreta larga");
        }

        @Test
        void conservaLosEspaciosDelExtremo() {
            assertThat(new RawPassword(" con espacio ").value()).isEqualTo(" con espacio ");
        }

        @Test
        void rechazaUnValorVacio() {
            assertThatThrownBy(() -> new RawPassword(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class FechaDeNacimiento {

        @Test
        void exigeUnaFecha() {
            assertThatThrownBy(() -> new BirthDate(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("La fecha de nacimiento es obligatoria");
        }

        @Test
        void admiteCualquierFechaReal() {
            assertThatCode(() -> new BirthDate(java.time.LocalDate.of(1990, 2, 28)))
                    .doesNotThrowAnyException();
        }
    }
}
