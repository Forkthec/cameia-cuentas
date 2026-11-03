package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;

/**
 * Comprueba que el plazo de publicación se valida al arrancar: fuera del rango de 100 ms a 10 s la aplicación no inicia.
 */
@DisplayName("AccountEventsProperties")
class AccountEventsPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesOnlyConfiguration.class);

    @ParameterizedTest
    @ValueSource(strings = {"50ms", "99ms", "11s", "10001ms"})
    @DisplayName("rechaza el arranque con un plazo fuera de rango")
    void properties_shouldRejectStartup_whenTimeoutIsOutOfRange(String timeout) {
        runner.withPropertyValues("cuentas.events.enabled=true", "cuentas.events.publish-timeout=" + timeout)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"100ms", "500ms", "10s"})
    @DisplayName("arranca con un plazo dentro del rango")
    void properties_shouldStart_whenTimeoutIsInRange(String timeout) {
        runner.withPropertyValues("cuentas.events.enabled=true", "cuentas.events.publish-timeout=" + timeout)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("rechaza el arranque cuando falta el plazo")
    void properties_shouldRejectStartup_whenTimeoutIsMissing() {
        runner.withPropertyValues("cuentas.events.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    /** Registra solo las propiedades, sin la mensajería, para probar su validación aislada. */
    @org.springframework.boot.context.properties.EnableConfigurationProperties(AccountEventsProperties.class)
    static class PropertiesOnlyConfiguration {
    }
}
