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
 * navegable la sirve Swagger UI en {@code /swagger-ui.html}, que consume ese mismo
 * documento. Las descripciones de cada endpoint y de cada modelo se derivan
 * automáticamente del Javadoc del código gracias a therapi-runtime-javadoc.
 * </p>
 *
 * <p>La publicación depende de la variable {@code API_DOCUMENTATION_ENABLED}, apagada
 * por defecto. El perfil {@code local} la enciende y el perfil {@code prod} la fija en
 * {@code false}, de modo que producción no expone ni la interfaz ni el documento
 * OpenAPI. Este bean se construye igual en todos los perfiles; cuando springdoc está
 * apagado, sus metadatos simplemente no se sirven por HTTP.</p>
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
