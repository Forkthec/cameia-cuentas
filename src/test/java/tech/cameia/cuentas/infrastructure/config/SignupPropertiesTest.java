package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import tech.cameia.cuentas.domain.exception.SignupNotAllowedException;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.policy.SignupAllowlistPolicy;

/**
 * Comprueba que la lista de correos permitidos se lee de {@code cuentas.signup.allowlist}, se separa por punto y coma
 * y se valida al arrancar (REQ-LRP-01 a REQ-LRP-05 de CM-360).
 */
@DisplayName("SignupProperties y SignupAllowlistConfiguration")
class SignupPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SignupAllowlistConfiguration.class);

    @Test
    @DisplayName("deja el registro abierto cuando la propiedad falta")
    void shouldBeOpen_whenPropertyIsMissing() {
        runner.run(context -> assertThat(context.getBean(SignupAllowlistPolicy.class).isOpen()).isTrue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " ; ", ";;"})
    @DisplayName("deja el registro abierto cuando la lista está vacía o solo tiene separadores")
    void shouldBeOpen_whenListHasNoEntries(String lista) {
        runner.withPropertyValues("cuentas.signup.allowlist=" + lista)
                .run(context -> assertThat(context.getBean(SignupAllowlistPolicy.class).isOpen()).isTrue());
    }

    @Test
    @DisplayName("separa por punto y coma y recorta los espacios de cada entrada")
    void shouldSplitBySemicolon_whenEntriesHaveSpaces() {
        runner.withPropertyValues("cuentas.signup.allowlist=a@x.co; b@y.co ;")
                .run(context -> {
                    SignupAllowlistPolicy politica = context.getBean(SignupAllowlistPolicy.class);
                    assertThat(politica.isOpen()).isFalse();
                    assertThatCode(() -> politica.verify(new EmailAddress("a@x.co"))).doesNotThrowAnyException();
                    assertThatCode(() -> politica.verify(new EmailAddress("b+1@y.co"))).doesNotThrowAnyException();
                    assertThatThrownBy(() -> politica.verify(new EmailAddress("c@x.co")))
                            .isInstanceOf(SignupNotAllowedException.class);
                });
    }

    @Test
    @DisplayName("una coma no separa entradas: la lista con coma no arranca")
    void shouldFailStartup_whenCommaIsUsedAsSeparator() {
        runner.withPropertyValues("cuentas.signup.allowlist=a@x.co,b@y.co")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("no arranca cuando una entrada está mal formada")
    void shouldFailStartup_whenAnEntryIsInvalid() {
        runner.withPropertyValues("cuentas.signup.allowlist=a@x.co;paulamunoz")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("SIGNUP_ALLOWLIST")
                        .hasMessageNotContaining("paulamunoz"));
    }
}
