package tech.cameia.cuentas.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tech.cameia.cuentas.domain.model.CurrentPolicy;

/**
 * Publica la versión vigente de la política de tratamiento de datos como un bean de dominio.
 *
 * <p>Un valor en blanco o con un formato distinto de {@code AAAA-MM-vN} impide arrancar el servicio: es
 * mejor no iniciar que aceptar a personas contra una versión que nadie publicó. Si la variable
 * {@code CAMEIA_POLICY_VERSION} no existe, se usa el valor por defecto de {@code application.properties}; por eso, al
 * publicar una versión nueva hay que definirla, o el servicio seguirá con la anterior.</p>
 */
@Configuration
@EnableConfigurationProperties(PolicyProperties.class)
public class PolicyConfiguration {

    /**
     * Crea la versión vigente a partir de la propiedad {@code cuentas.policy.current-version}.
     *
     * @param properties propiedades ya validadas como no vacías
     * @return versión vigente con su formato comprobado
     * @throws IllegalArgumentException si el formato no es {@code AAAA-MM-vN}; el contexto no arranca
     */
    @Bean
    CurrentPolicy currentPolicy(PolicyProperties properties) {
        return new CurrentPolicy(properties.currentVersion());
    }
}
