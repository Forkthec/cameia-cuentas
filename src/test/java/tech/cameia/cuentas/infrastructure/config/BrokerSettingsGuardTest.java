package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Comprueba que el perfil de producción no arranca con la configuración del broker incompleta, sin mostrar valores.
 */
@DisplayName("BrokerSettingsGuard")
class BrokerSettingsGuardTest {

    private static final String UNRESOLVED = "${SPRING_RABBITMQ_PASSWORD}";

    private static RabbitProperties settings(String host, String username, String password) {
        RabbitProperties properties = new RabbitProperties();
        properties.setHost(host);
        properties.setUsername(username);
        properties.setPassword(password);
        return properties;
    }

    @Test
    @DisplayName("acepta host, usuario y contraseña definidos")
    void new_shouldAccept_whenAllSettingsAreDefined() {
        assertThatCode(() -> new BrokerSettingsGuard(settings("broker.ejemplo.test", "usuario", "clave")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "host=[{0}]")
    @ValueSource(strings = {"", "   ", "${SPRING_RABBITMQ_HOST}"})
    @DisplayName("rechaza un host vacío, en blanco o sin resolver y nombra la variable")
    void new_shouldReject_whenHostIsMissing(String host) {
        assertThatThrownBy(() -> new BrokerSettingsGuard(settings(host, "usuario", "clave")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_RABBITMQ_HOST");
    }

    @ParameterizedTest(name = "usuario=[{0}]")
    @ValueSource(strings = {"", "   ", "${SPRING_RABBITMQ_USERNAME}"})
    @DisplayName("rechaza un usuario vacío, en blanco o sin resolver y nombra la variable")
    void new_shouldReject_whenUsernameIsMissing(String username) {
        assertThatThrownBy(() -> new BrokerSettingsGuard(settings("broker.ejemplo.test", username, "clave")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_RABBITMQ_USERNAME");
    }

    @ParameterizedTest(name = "contraseña=[{0}]")
    @ValueSource(strings = {"", "   ", UNRESOLVED})
    @DisplayName("rechaza una contraseña vacía, en blanco o sin resolver y nombra la variable")
    void new_shouldReject_whenPasswordIsMissing(String password) {
        assertThatThrownBy(() -> new BrokerSettingsGuard(settings("broker.ejemplo.test", "usuario", password)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_RABBITMQ_PASSWORD");
    }

    @Test
    @DisplayName("acepta la dirección completa del broker aunque no haya host, usuario ni contraseña")
    void new_shouldAccept_whenAddressesAreDefined() {
        RabbitProperties properties = settings("${SPRING_RABBITMQ_HOST}", "${SPRING_RABBITMQ_USERNAME}", UNRESOLVED);
        properties.setAddresses(List.of("amqps://usuario:clave@broker.ejemplo.test:5671/vhost"));

        assertThatCode(() -> new BrokerSettingsGuard(properties)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "dirección=[{0}]")
    @ValueSource(strings = {"", "   ", "${SPRING_RABBITMQ_ADDRESSES}"})
    @DisplayName("rechaza una dirección vacía o sin resolver cuando tampoco hay host, usuario y contraseña")
    void new_shouldReject_whenAddressesAreMissing(String address) {
        RabbitProperties properties = settings("${SPRING_RABBITMQ_HOST}", "usuario", "clave");
        properties.setAddresses(List.of(address));

        assertThatThrownBy(() -> new BrokerSettingsGuard(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_RABBITMQ_ADDRESSES");
    }

    @Test
    @DisplayName("rechaza host, usuario y contraseña nulos y los nombra a los tres")
    void new_shouldReject_whenSettingsAreNull() {
        RabbitProperties properties = settings(null, null, null);

        assertThatThrownBy(() -> new BrokerSettingsGuard(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_RABBITMQ_HOST")
                .hasMessageContaining("SPRING_RABBITMQ_USERNAME")
                .hasMessageContaining("SPRING_RABBITMQ_PASSWORD");
    }

    @Test
    @DisplayName("el mensaje no incluye el valor de la dirección")
    void new_shouldNotLeakAddress_whenSettingIsRejected() {
        RabbitProperties properties = settings("", "usuario", "clave");
        properties.setAddresses(List.of("${secreto-sin-resolver}"));

        assertThatThrownBy(() -> new BrokerSettingsGuard(properties)).hasMessageNotContaining("secreto-sin-resolver");
    }

    @Test
    @DisplayName("el mensaje de error no incluye el valor de la contraseña")
    void new_shouldNotLeakValue_whenSettingIsRejected() {
        assertThatThrownBy(() -> new BrokerSettingsGuard(settings("   ", "usuario", "clave-secreta")))
                .hasMessageNotContaining("clave-secreta");
    }

    @Test
    @DisplayName("solo existe con el perfil prod")
    void guard_shouldBeActiveOnlyInProd_whenAnnotated() {
        assertThat(BrokerSettingsGuard.class.getAnnotation(Profile.class).value()).containsExactly("prod");
    }

    @Test
    @DisplayName("el perfil prod no arranca cuando faltan las variables del broker")
    void context_shouldNotStart_whenBrokerSettingsAreMissingInProd() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(GuardOnly.class)
                .web(WebApplicationType.NONE)
                .profiles("prod");

        assertThatThrownBy(builder::run).hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("SPRING_RABBITMQ_HOST");
    }

    /** Contexto mínimo: solo enlaza las propiedades del broker y aplica la verificación. */
    @Configuration
    @EnableConfigurationProperties(RabbitProperties.class)
    @Import(BrokerSettingsGuard.class)
    static class GuardOnly {
    }
}
