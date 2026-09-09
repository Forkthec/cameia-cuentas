package tech.cameia.cuentas.infrastructure.config.documentation;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.scalar.maven.webmvc.ScalarWebMvcAutoConfiguration;

/**
 * Activa la interfaz de referencia de API de Scalar.
 *
 * <p>La biblioteca {@code scalar-webmvc} se publica como autoconfiguración de Spring
 * Boot 3 y su clase está anotada con {@code @Configuration} en lugar de
 * {@code @AutoConfiguration}. Spring Boot 4 ignora esas entradas del archivo de
 * autoconfiguración, así que se importa la clase de forma explícita para registrar el
 * controlador que sirve Scalar.</p>
 */
@Configuration 
@Import(ScalarWebMvcAutoConfiguration.class)
public class ScalarConfiguration {
    
}
