package tech.cameia.cuentas.domain.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tech.cameia.cuentas.domain.exception.WeakPasswordException;
import tech.cameia.cuentas.domain.model.RawPassword;

/**
 * Prueba de la política de contraseñas descrita en
 * {@code specs/CM-14-RegistroUsuario/spec.md} (REQ-CU-11b), que sigue OWASP ASVS.
 */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void rechazaUnaContraseniaDeOnceCaracteres() {
        assertThatThrownBy(() -> policy.verify(new RawPassword("a".repeat(11))))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessage("La contraseña debe tener al menos 12 caracteres");
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
                .hasMessage("La contraseña no puede superar los 64 caracteres");
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
    void elValorNuncaAparaceEnElMensajeDeError() {
        String secreta = "corta";

        assertThatThrownBy(() -> policy.verify(new RawPassword(secreta)))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageNotContaining(secreta);
    }
}
