package tech.cameia.cuentas.infrastructure.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Cubre el puerto del publicador cuando los eventos están apagados.
 *
 * <p>Las pruebas corren con {@code cuentas.events.enabled=false}, así que la configuración de mensajería no publica ningún
 * bean. Sin esta configuración, cualquier contexto que necesite el puerto no arrancaría. La condición
 * {@code @ConditionalOnProperty} deja que una prueba con RabbitMQ real (que enciende los eventos) use el publicador verdadero.</p>
 */
@Configuration
public class InMemoryEventPublisherConfiguration {

    /**
     * Publica el doble en memoria del publicador de eventos.
     *
     * @return publicador que no sale de la máquina
     */
    @Bean
    @ConditionalOnProperty(name = "cuentas.events.enabled", havingValue = "false")
    public InMemoryEventPublisher inMemoryEventPublisher() {
        return new InMemoryEventPublisher();
    }
}
