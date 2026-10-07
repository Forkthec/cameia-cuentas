package tech.cameia.cuentas.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.WeakPasswordException;
import tech.cameia.cuentas.domain.model.RawPassword;

/**
 * Prueba de la política de contraseñas descrita en
 * {@code specs/CM-14-RegistroUsuario/spec.md} (REQ-CU-11b), que sigue OWASP ASVS.
 */
class PasswordPolicyTest {

    /** Lista de prueba con las tres contraseñas comunes del criterio de aceptación. */
    static final Set<String> COMUNES = Set.of("123456789012", "password1234", "qwertyuiop123");

    private final PasswordPolicy policy = new PasswordPolicy(COMUNES);

    @Test
    void rechazaUnaContraseniaDeOnceCaracteres() {
        assertThatThrownBy(() -> policy.verify(new RawPassword("a".repeat(11))))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessage("La contraseña debe tener al menos 12 caracteres.");
    }

    @Test
    void admiteExactamenteDoceCaracteres() {
        assertThatCode(() -> policy.verify(new RawPassword("a".repeat(12))))
                .doesNotThrowAnyException();
    }

    @Test
    void admiteExactamenteSesentaYCuatroCaracteres() {
        assertThatCode(() -> policy.verify(new RawPassword("a".repeat(64))))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaMasDeSesentaYCuatroCaracteresEnVezDeRecortar() {
        assertThatThrownBy(() -> policy.verify(new RawPassword("a".repeat(65))))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessage("La contraseña no puede superar los 64 caracteres.");
    }

    @Test
    void admiteEspaciosYAcentosSinExigirMayusculasNiSimbolos() {
        assertThatCode(() -> policy.verify(new RawPassword("mi frase secreta con ñ")))
                .doesNotThrowAnyException();
    }

    @Test
    void cuentaUnEmojiComoUnSoloCaracter() {
        // "🔒" ocupa dos unidades char en Java. Contando char, esta contraseña de once
        // caracteres pasaría por doce y la política quedaría por debajo del mínimo real.
        assertThatThrownBy(() -> policy.verify(new RawPassword("🔒" + "a".repeat(10))))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    @DisplayName("La longitud se comprueba sin la lista de contraseñas comunes")
    void requireValidLength_shouldCheckOnlyLength_whenCalledWithoutTheCommonList() {
        // El contrato HTTP la aplica junto con las demás reglas de forma; la lista se consulta después.
        assertThatThrownBy(() -> PasswordPolicy.requireValidLength(new RawPassword("frase secre")))
                .isInstanceOfSatisfying(WeakPasswordException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_TOO_SHORT));
        assertThatThrownBy(() -> PasswordPolicy.requireValidLength(new RawPassword("a".repeat(65))))
                .isInstanceOfSatisfying(WeakPasswordException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_TOO_LONG));
        assertThatCode(() -> PasswordPolicy.requireValidLength(new RawPassword("password1234"))).doesNotThrowAnyException();
    }

    @Test
    void rechazaUnaContraseniaConocidaAunqueCumplaLaLongitud() {
        // Doce caracteres, así que pasa la regla de longitud; un ataque de diccionario la
        // prueba en los primeros intentos.
        assertThatThrownBy(() -> policy.verify(new RawPassword("123456789012")))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("demasiado común");
    }

    @Test
    void laListaDeConocidasIgnoraMayusculasYEspacios() {
        assertThatThrownBy(() -> policy.verify(new RawPassword("  Password1234 ")))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("demasiado común");
    }

    @Test
    void admiteUnaFraseLargaQueNoEstaEnLaLista() {
        assertThatCode(() -> policy.verify(new RawPassword("dos gatos duermen en la ventana")))
                .doesNotThrowAnyException();
    }

    @Test
    void laContraseniaCortaLlevaElCodigoDeContraseniaMuyCorta() {
        assertThatThrownBy(() -> policy.verify(new RawPassword("corta")))
                .isInstanceOf(WeakPasswordException.class)
                .extracting(excepcion -> ((WeakPasswordException) excepcion).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_TOO_SHORT);
    }

    @Test
    void laContraseniaLargaLlevaElCodigoDeContraseniaMuyLarga() {
        assertThatThrownBy(() -> policy.verify(new RawPassword("a".repeat(65))))
                .isInstanceOf(WeakPasswordException.class)
                .extracting(excepcion -> ((WeakPasswordException) excepcion).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_TOO_LONG);
    }

    @Test
    void laContraseniaConocidaLlevaElCodigoDeContraseniaComun() {
        assertThatThrownBy(() -> policy.verify(new RawPassword("123456789012")))
                .isInstanceOf(WeakPasswordException.class)
                .extracting(excepcion -> ((WeakPasswordException) excepcion).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_TOO_COMMON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789012", "password1234", "qwertyuiop123", "PASSWORD1234", "  password1234  "})
    void unaContraseniaComunRecibeElMensajeLiteralDelCriterioSinRepetirla(String comun) {
        assertThatThrownBy(() -> policy.verify(new RawPassword(comun)))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessage("Esta contraseña es demasiado común, elige otra.")
                .hasMessageNotContaining("brother")
                .hasMessageNotContaining(comun.trim())
                .extracting(excepcion -> ((WeakPasswordException) excepcion).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_TOO_COMMON);
    }

    @Test
    void unaLetraConAcentoCombinanteCuentaComoUnCaracter() {
        // "e" + acento combinante doce veces: 24 puntos de código escritos, 12 en NFC.
        assertThatCode(() -> policy.verify(new RawPassword("e\u0301".repeat(12)))).doesNotThrowAnyException();
        assertThatCode(() -> policy.verify(new RawPassword("e\u0301".repeat(64)))).doesNotThrowAnyException();
    }

    @Test
    void elLimiteEnNfcRechazaOnceYSesentaYCincoLetrasConAcentoCombinante() {
        assertThatThrownBy(() -> policy.verify(new RawPassword("e\u0301".repeat(11))))
                .extracting(excepcion -> ((WeakPasswordException) excepcion).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_TOO_SHORT);
        assertThatThrownBy(() -> policy.verify(new RawPassword("e\u0301".repeat(65))))
                .extracting(excepcion -> ((WeakPasswordException) excepcion).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_TOO_LONG);
    }

    @Test
    void laContraseniaNoSeModificaAlMedirla() {
        RawPassword original = new RawPassword("e\u0301".repeat(12));

        policy.verify(original);

        assertThat(original.value()).isEqualTo("e\u0301".repeat(12));
    }

    @Test
    void unaContraseniaParecidaQueNoEstaEnLaListaSeAcepta() {
        assertThatCode(() -> policy.verify(new RawPassword("password12345"))).doesNotThrowAnyException();
    }

    @Test
    void conLaListaVaciaSoloRigeLaLongitud() {
        PasswordPolicy sinLista = new PasswordPolicy(Set.of());

        assertThatCode(() -> sinLista.verify(new RawPassword("123456789012"))).doesNotThrowAnyException();
    }

    @Test
    void unaContraseniaComunRodeadaDeEspaciosDurosOConAcentoCombinanteSigueSiendoComun() {
        PasswordPolicy conAcento = new PasswordPolicy(Set.of("contraseñacomún"));

        assertThatThrownBy(() -> policy.verify(new RawPassword("\u00A0password1234\u00A0")))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessage("Esta contraseña es demasiado común, elige otra.");
        assertThatThrownBy(() -> conAcento.verify(new RawPassword("contrasen\u0303acomu\u0301n")))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessage("Esta contraseña es demasiado común, elige otra.");
    }

    @Test
    void laListaRecibidaSeCopiaYNoCambiaSiQuienLaEntregoLaModifica() {
        Set<String> lista = new HashSet<>(Set.of("123456789012"));
        PasswordPolicy copiada = new PasswordPolicy(lista);

        lista.clear();

        assertThatThrownBy(() -> copiada.verify(new RawPassword("123456789012")))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void elValorNuncaAparaceEnElMensajeDeError() {
        String secreta = "corta";

        assertThatThrownBy(() -> policy.verify(new RawPassword(secreta)))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageNotContaining(secreta);
    }
}
