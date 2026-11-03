package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

/**
 * Comprueba que las variables {@code SPRING_RABBITMQ_*} escritas en el archivo {@code .env} llegan a la configuración del
 * broker. Spring solo enlaza esos nombres de forma relajada cuando son variables de entorno reales; desde un archivo de
 * propiedades hace falta un marcador explícito, y sin él la aplicación publica en {@code localhost:5672} sin avisar.
 */
@SpringBootTest(classes = RabbitPropertiesBindingTest.BindingOnly.class,
        properties = "spring.config.import=classpath:env-de-prueba.properties")
@DisplayName("Propiedades del broker leídas desde el archivo .env")
class RabbitPropertiesBindingTest {

    @Autowired
    private RabbitProperties rabbitProperties;

    @Test
    @DisplayName("host, puerto, credenciales, host virtual y TLS salen de las variables del archivo")
    void properties_shouldReadBrokerFromEnvFile_whenImported() {
        assertThat(rabbitProperties.getHost()).isEqualTo("broker.ejemplo.test");
        assertThat(rabbitProperties.getPort()).isEqualTo(5999);
        assertThat(rabbitProperties.getUsername()).isEqualTo("usuario-de-prueba");
        assertThat(rabbitProperties.getPassword()).isEqualTo("clave-de-prueba");
        assertThat(rabbitProperties.getVirtualHost()).isEqualTo("/qa");
        assertThat(rabbitProperties.getSsl().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("los plazos de conexión y de canal siguen siendo los del servicio")
    void properties_shouldKeepTimeouts_whenEnvFileIsImported() {
        assertThat(rabbitProperties.getConnectionTimeout()).isEqualTo(Duration.ofMillis(500));
        assertThat(rabbitProperties.getChannelRpcTimeout()).isEqualTo(Duration.ofMillis(500));
    }

    /** Contexto mínimo: solo enlaza las propiedades del broker, sin conectarse a nada. */
    @Configuration
    @EnableConfigurationProperties(RabbitProperties.class)
    static class BindingOnly {
    }
}
