package tech.cameia.cuentas.infrastructure.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.ConnectionFactoryCustomizer;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import tech.cameia.cuentas.domain.port.EventPublisher;
import tech.cameia.cuentas.infrastructure.messaging.publisher.DisabledEventPublisher;
import tech.cameia.cuentas.infrastructure.messaging.publisher.RabbitEventPublisher;

/**
 * Declara el exchange de eventos de cuenta y el publicador que lo usa.
 *
 * <p>Con {@code cuentas.events.enabled=false} el publicador es uno inerte que deja los eventos pendientes: así las
 * pruebas y los entornos sin broker arrancan sin conectarse a RabbitMQ. Con el perfil {@code prod} no se admite.</p>
 */
@Configuration
@EnableConfigurationProperties(AccountEventsProperties.class)
public class AccountEventsMessagingConfiguration {

    /**
     * Exchange de tipo tema, durable y sin borrado automático, donde se publica cada evento con su tipo como clave.
     *
     * @return el exchange {@code cuentas.events}
     */
    @Bean
    public TopicExchange accountEventsExchange() {
        return new TopicExchange("cuentas.events", true, false);
    }

    /**
     * Acota el saludo inicial del protocolo AMQP al mismo plazo que la conexión.
     *
     * <p>Sin este ajuste, un broker que acepta la conexión TCP pero no responde retiene la publicación los 10 segundos
     * que el cliente usa por defecto, y el registro no puede esperar tanto (DES-02).</p>
     *
     * @param properties propiedades {@code spring.rabbitmq.*} ya enlazadas
     * @return el ajuste de la fábrica de conexiones
     */
    @Bean
    public ConnectionFactoryCustomizer handshakeTimeoutCustomizer(RabbitProperties properties) {
        return factory -> factory.setHandshakeTimeout((int) properties.getConnectionTimeout().toMillis());
    }

    /**
     * Evita el aviso duplicado de la plantilla cuando el broker devuelve un mensaje sin cola de destino.
     *
     * <p>La devolución ya se trata en el publicador, que la cuenta como no publicada y la registra una vez; sin un callback
     * la plantilla escribe además «Returned message but no callback available» por cada devolución.</p>
     *
     * @return el ajuste de la plantilla
     */
    @Bean
    public RabbitTemplateCustomizer returnedMessageCustomizer() {
        return template -> template.setReturnsCallback(returned -> { });
    }

    /**
     * Publicador inerte para cuando los eventos están apagados.
     *
     * <p>No se admite con el perfil {@code prod}: apagar los eventos en un despliegue dejaría todos pendientes sin que
     * nadie lo note.</p>
     *
     * @param environment entorno de Spring, del que se leen los perfiles activos
     * @return un publicador que nunca confirma
     * @throws IllegalStateException si el perfil {@code prod} está activo
     */
    @Bean
    @ConditionalOnProperty(name = "cuentas.events.enabled", havingValue = "false")
    public EventPublisher disabledEventPublisher(Environment environment) {
        if (environment.matchesProfiles("prod")) {
            throw new IllegalStateException("EVENTS_ENABLED=false no se admite con el perfil 'prod': los eventos "
                    + "quedarían sin publicar. Quite la variable o póngala en true");
        }
        return new DisabledEventPublisher();
    }

    /**
     * Publicador de eventos hacia RabbitMQ.
     *
     * @param rabbitTemplate plantilla de Spring AMQP
     * @param properties plazo de espera de la confirmación
     * @return el publicador real
     */
    @Bean
    @ConditionalOnProperty(name = "cuentas.events.enabled", havingValue = "true", matchIfMissing = true)
    public EventPublisher eventPublisher(RabbitTemplate rabbitTemplate, AccountEventsProperties properties) {
        return new RabbitEventPublisher(rabbitTemplate, properties.publishTimeout());
    }
}
