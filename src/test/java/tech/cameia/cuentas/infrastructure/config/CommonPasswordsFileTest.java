package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.WeakPasswordException;
import tech.cameia.cuentas.domain.model.RawPassword;
import tech.cameia.cuentas.domain.policy.PasswordPolicy;

/**
 * Comprueba el archivo real de contraseñas comunes que usa la aplicación, sin levantar Spring:
 * que lo acepta el cargador con el mínimo de producción y que contiene las contraseñas del
 * criterio de aceptación.
 */
class CommonPasswordsFileTest {

    private static Set<String> lista;

    @BeforeAll
    static void cargarElArchivoReal() {
        lista = CommonPasswordsLoader.load(new ClassPathResource("security/common-passwords.txt"),
                CommonPasswordsLoader.MINIMUM_ENTRIES);
    }

    @Test
    void tieneExactamente3000Entradas() {
        assertThat(lista).hasSize(3000);
    }

    @Test
    void contieneLasContraseniasDelCriterioDeAceptacion() {
        assertThat(lista).contains("123456789012", "password1234", "qwertyuiop123");
    }

    @Test
    void ningunaEntradaSuperaElMaximoDeLaPolitica() {
        assertThat(lista).allSatisfy(entrada -> assertThat(entrada.codePointCount(0, entrada.length()))
                .isBetween(12, 64));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789012", "PASSWORD1234", "  qwertyuiop123  "})
    void laPoliticaConElArchivoRealRechazaLasDelCriterio(String comun) {
        PasswordPolicy politica = new PasswordPolicy(lista);

        assertThatThrownBy(() -> politica.verify(new RawPassword(comun)))
                .isInstanceOf(WeakPasswordException.class)
                .extracting(error -> ((WeakPasswordException) error).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_TOO_COMMON);
    }

    @Test
    void unaFraseLargaPocoComunNoEstaEnLaLista() {
        assertThat(lista).doesNotContain("dos gatos duermen en la ventana");
    }
}
