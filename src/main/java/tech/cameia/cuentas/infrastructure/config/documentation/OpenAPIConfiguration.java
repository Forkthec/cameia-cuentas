package tech.cameia.cuentas.infrastructure.config.documentation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * Configuración de la documentación OpenAPI del microservicio de Cuentas.
 *
 * <p>Define los metadatos generales (título, versión y descripción) que springdoc
 * publica como documento OpenAPI en {@code /v3/api-docs}. La interfaz de referencia
 * navegable la sirve Scalar en {@code /scalar}, que consume ese mismo documento. Las
 * descripciones de cada endpoint y de cada modelo se derivan automáticamente del
 * Javadoc del código gracias a therapi-runtime-javadoc.
 * </p>
 */
@Configuration 
public class OpenAPIConfiguration {
    @Bean
    /**
     * Construye los metadatos para la documentacion del maravilloso microservicio de entrevista :)
     * @return documento/clase OpenAPI con documentación general
     */
    OpenAPI cuentaOpenAPI(){
        return new OpenAPI().info(
            new Info()
                .title("CAMEIA - Microservicio de Cuentas 🐒")
                .version("v1")
                .description("API interna del backend para gestionar todo el proceso de billing" 
                            +"de membresias y acceso a funcionalidades premium a travez de Wompi.")
            );
    } 
}
