package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import tech.cameia.cuentas.domain.model.CurrentPolicy;

/**
 * Comprueba que la versión vigente de la política se valida al arrancar: sin una versión con el formato
 * {@code AAAA-MM-vN} la aplicación no inicia.
 */
@DisplayName("PolicyProperties y PolicyConfiguration")
class PolicyPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PolicyConfiguration.class);

    @Test
    @DisplayName("crea la versión vigente cuando el formato es válido")
    void shouldCreateCurrentPolicy_whenVersionIsValid() {
        runner.withPropertyValues("cuentas.policy.current-version=2026-11-v1")
                .run(context -> assertThat(context.getBean(CurrentPolicy.class).version()).isEqualTo("2026-11-v1"));
    }

    @Test
    @DisplayName("no arranca cuando la versión está vacía")
    void shouldFailStartup_whenBlank() {
        runner.withPropertyValues("cuentas.policy.current-version=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("no arranca cuando la propiedad falta")
    void shouldFailStartup_whenMissing() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13-v1", "2026-11-v0", "sin-aceptar"})
    @DisplayName("no arranca cuando el formato no es AAAA-MM-vN o es el valor de «sin aceptar»")
    void shouldFailStartup_whenFormatIsInvalid(String version) {
        runner.withPropertyValues("cuentas.policy.current-version=" + version)
                .run(context -> assertThat(context).hasFailed());
    }
}
