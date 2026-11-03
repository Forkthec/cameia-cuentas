package tech.cameia.cuentas.infrastructure.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tech.cameia.cuentas.domain.port.FirebaseUserDirectory;

/**
 * Cubre el puerto del directorio de usuarios cuando el Admin SDK está apagado.
 *
 * <p>Las pruebas corren con {@code cuentas.firebase.enabled=false}, así que
 * {@code FirebaseConfiguration} no publica ningún bean. Sin esta configuración, cualquier
 * contexto que necesite el puerto no arrancaría.</p>
 *
 * <p>La condición {@code @ConditionalOnMissingBean} deja que una prueba concreta registre
 * su propio doble sin chocar con este.</p>
 */
@Configuration
public class FirebaseTestConfiguration {

    /**
     * Publica el doble en memoria del directorio de usuarios.
     *
     * @return directorio de usuarios que no sale de la máquina
     */
    @Bean
    @ConditionalOnMissingBean(FirebaseUserDirectory.class)
    public InMemoryFirebaseUserDirectory inMemoryFirebaseUserDirectory() {
        return new InMemoryFirebaseUserDirectory();
    }
}
