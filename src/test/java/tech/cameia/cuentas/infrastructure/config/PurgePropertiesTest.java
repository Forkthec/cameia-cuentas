package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

/**
 * Comprueba que el tope de la purga se valida al arrancar: fuera del rango de 1 a 1000 la aplicación no inicia.
 */
@DisplayName("PurgeProperties")
class PurgePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesOnlyConfiguration.class);

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1001", "100000"})
    @DisplayName("rechaza el arranque con un tope fuera de rango")
    void properties_shouldRejectStartup_whenMaxIsOutOfRange(String max) {
        runner.withPropertyValues("cuentas.purge.max-accounts-per-run=" + max)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "999", "1000"})
    @DisplayName("arranca con un tope dentro del rango")
    void properties_shouldStart_whenMaxIsInRange(String max) {
        runner.withPropertyValues("cuentas.purge.max-accounts-per-run=" + max)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("trae el valor configurado")
    void properties_shouldBindTheConfiguredValue() {
        runner.withPropertyValues("cuentas.purge.max-accounts-per-run=250")
                .run(context -> assertThat(context.getBean(PurgeProperties.class).maxAccountsPerRun()).isEqualTo(250));
    }

    /** Registra solo las propiedades, sin el resto de la configuración, para probar su validación aislada. */
    @EnableConfigurationProperties(PurgeProperties.class)
    static class PropertiesOnlyConfiguration {
    }
}
