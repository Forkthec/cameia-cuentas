package tech.cameia.cuentas.infrastructure.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tech.cameia.cuentas.domain.policy.SignupAllowlistPolicy;

/**
 * Publica la lista de correos permitidos para el registro como una política de dominio.
 *
 * <p>Las entradas se separan por punto y coma, no por coma, porque el despliegue continuo pasa las
 * variables con {@code --set-env-vars}, que usa la coma para separar una variable de otra (CM-360).
 * El separador es una restricción del despliegue, no una regla de negocio; por eso se aplica aquí y
 * la política recibe la lista ya separada.</p>
 */
@Configuration
@EnableConfigurationProperties(SignupProperties.class)
public class SignupAllowlistConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SignupAllowlistConfiguration.class);

    private static final String SEPARADOR = ";";

    /**
     * Crea la política a partir de la propiedad {@code cuentas.signup.allowlist}.
     *
     * @param properties propiedades del registro; la lista puede faltar
     * @return política abierta si la lista no tiene entradas, o restringida a ellas
     * @throws IllegalArgumentException si una entrada no es un correo base ni un dominio; el contexto no arranca
     */
    @Bean
    SignupAllowlistPolicy signupAllowlistPolicy(SignupProperties properties) {
        String lista = properties.allowlist();
        List<String> entradas = lista == null ? List.of() : List.of(lista.split(SEPARADOR, -1));
        SignupAllowlistPolicy politica = new SignupAllowlistPolicy(entradas);
        if (!politica.isOpen()) {
            // Solo el hecho, sin las entradas: la lista contiene correos
            logger.info("Registro restringido a la lista de correos permitidos");
        }
        return politica;
    }
}
