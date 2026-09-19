package tech.cameia.cuentas.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tech.cameia.cuentas.domain.policy.AgePolicy;
import tech.cameia.cuentas.domain.policy.PasswordPolicy;

/**
 * Publica las políticas del dominio como beans.
 *
 * <p>Las políticas viven en {@code domain} y no llevan anotaciones de Spring: son reglas
 * de negocio y deben poder instanciarse y probarse con {@code new}, sin contexto. Que
 * estén disponibles para inyección es una necesidad de la infraestructura, así que se
 * declara aquí.</p>
 */
@Configuration
public class DomainPolicyConfiguration {

    /**
     * Publica las reglas de fecha de nacimiento.
     *
     * @return política sobre el reloj UTC del sistema
     */
    @Bean
    public AgePolicy agePolicy() {
        return new AgePolicy();
    }

    /**
     * Publica las reglas de contraseña.
     *
     * @return política según OWASP ASVS
     */
    @Bean
    public PasswordPolicy passwordPolicy() {
        return new PasswordPolicy();
    }
}
