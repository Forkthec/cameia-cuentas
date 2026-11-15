package tech.cameia.cuentas.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.SignupNotAllowedException;
import tech.cameia.cuentas.domain.model.EmailAddress;

/**
 * Prueba la lista de correos permitidos para el registro, con los casos de la §5 de
 * {@code specs/CM-360-ListaRegistroPermitido/spec.md}.
 */
class SignupAllowlistPolicyTest {

    /** Lista del equipo, con espacios alrededor de una entrada como los dejaría quien la escribe a mano. */
    private final SignupAllowlistPolicy listaDelEquipo = new SignupAllowlistPolicy(List.of(
            "paulamunoz@unicauca.edu.co", " juanvela@unicauca.edu.co ", "juangomezmu@unicauca.edu.co"));

    private final SignupAllowlistPolicy dominioCompleto = new SignupAllowlistPolicy(List.of("@unicauca.edu.co"));

    @ParameterizedTest
    @ValueSource(strings = {
        "juangomezmu@unicauca.edu.co",
        "juangomezmu+q5a@unicauca.edu.co",
        "JuanGomezMu+Q5A@UNICAUCA.edu.co",
        "juangomezmu+@unicauca.edu.co",
        "juangomezmu+a+b@unicauca.edu.co",
        "juanvela@unicauca.edu.co"
    })
    void permiteLaBaseSusSubdireccionesYMayusculas(String correo) {
        assertThatCode(() -> listaDelEquipo.verify(new EmailAddress(correo))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "otro@unicauca.edu.co",
        "juangomezmu@gmail.com",
        "juangomezmu@mail.unicauca.edu.co",
        "juangomezmux@unicauca.edu.co",
        "+juangomezmu@unicauca.edu.co"
    })
    void rechazaLoQueNoEstaEnLaLista(String correo) {
        assertThatThrownBy(() -> listaDelEquipo.verify(new EmailAddress(correo)))
                .isInstanceOf(SignupNotAllowedException.class)
                .hasMessage("No es posible crear una cuenta con este correo.")
                .extracting(error -> ((SignupNotAllowedException) error).getErrorCode())
                .isEqualTo(ErrorCode.SIGNUP_NOT_ALLOWED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cualquiera+x@unicauca.edu.co", "Ana@Unicauca.Edu.Co"})
    void elDominioCompletoPermiteCualquierCorreoDeEseDominio(String correo) {
        assertThatCode(() -> dominioCompleto.verify(new EmailAddress(correo))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@mail.unicauca.edu.co", "ana@gmail.com", "ana@unicauca.edu.co.evil.com"})
    void elDominioCompletoNoPermiteSubdominiosNiOtrosDominios(String correo) {
        assertThatThrownBy(() -> dominioCompleto.verify(new EmailAddress(correo)))
                .isInstanceOf(SignupNotAllowedException.class);
    }

    @Test
    void mezclaCorreosBaseYDominios() {
        SignupAllowlistPolicy mixta = new SignupAllowlistPolicy(List.of("ana@gmail.com", "@unicauca.edu.co"));

        assertThatCode(() -> mixta.verify(new EmailAddress("ana+1@gmail.com"))).doesNotThrowAnyException();
        assertThatCode(() -> mixta.verify(new EmailAddress("luis@unicauca.edu.co"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> mixta.verify(new EmailAddress("luis@gmail.com")))
                .isInstanceOf(SignupNotAllowedException.class);
    }

    @Test
    void listaNulaDejaElRegistroAbierto() {
        SignupAllowlistPolicy abierta = new SignupAllowlistPolicy(null);

        assertThat(abierta.isOpen()).isTrue();
        assertThatCode(() -> abierta.verify(new EmailAddress("quien@sea.com"))).doesNotThrowAnyException();
    }

    @Test
    void listaSinEntradasDejaElRegistroAbierto() {
        SignupAllowlistPolicy abierta = new SignupAllowlistPolicy(Arrays.asList("", "  ", null));

        assertThat(abierta.isOpen()).isTrue();
        assertThatCode(() -> abierta.verify(new EmailAddress("quien@sea.com"))).doesNotThrowAnyException();
    }

    @Test
    void listaConEntradasNoEstaAbierta() {
        assertThat(listaDelEquipo.isOpen()).isFalse();
        assertThat(dominioCompleto.isOpen()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "paulamunoz",
        "a@b@c.co",
        "juan+x@unicauca.edu.co",
        "@unicauca",
        "@@unicauca.edu.co",
        "juan vela@unicauca.edu.co"
    })
    void unaEntradaMalFormadaImpideArrancarSinMostrarSuValor(String entrada) {
        List<String> entradas = List.of("ana@gmail.com", entrada);

        assertThatThrownBy(() -> new SignupAllowlistPolicy(entradas))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIGNUP_ALLOWLIST")
                .hasMessageContaining("entrada 2")
                .hasMessageNotContaining(entrada);
    }

    @Test
    void unaArrobaSolaImpideArrancar() {
        List<String> entradas = List.of("@");

        assertThatThrownBy(() -> new SignupAllowlistPolicy(entradas))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entrada 1");
    }
}
