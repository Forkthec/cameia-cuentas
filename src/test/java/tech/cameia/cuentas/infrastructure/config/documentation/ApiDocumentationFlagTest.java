package tech.cameia.cuentas.infrastructure.config.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * Prueba del interruptor de documentación descrito en
 * {@code docs/specs/documentacion-api.md}.
 *
 * <p>Resuelve las propiedades de los archivos versionados aplicando el mismo orden de
 * precedencia que usa Spring Boot: la variable de entorno gana sobre
 * {@code application.properties}, y el archivo del perfil gana sobre ambos. Lo que se
 * verifica es qué valor toman {@code springdoc.api-docs.enabled} y
 * {@code springdoc.swagger-ui.enabled} en cada entorno, porque son los que deciden si
 * el contrato de la API se publica.</p>
 *
 * <p>No se levanta un contexto de Spring a propósito. Al hacerlo, la aplicación importa
 * el archivo {@code .env} de la máquina del desarrollador, que está en
 * {@code .gitignore} y puede definir la variable con cualquier valor; la prueba daría
 * resultados distintos según el equipo. Por la misma razón no se usa el perfil
 * {@code local}, cuyo archivo de propiedades tampoco está versionado.</p>
 *
 * <p>La comprobación de extremo a extremo, con la aplicación arriba y los códigos HTTP
 * reales, está en la tabla de casos de éxito de la spec.</p>
 */
class ApiDocumentationFlagTest {

	private static final String PROPIEDAD_DOCUMENTO = "springdoc.api-docs.enabled";

	private static final String PROPIEDAD_INTERFAZ = "springdoc.swagger-ui.enabled";

	private static final String VARIABLE = "API_DOCUMENTATION_ENABLED";

	/**
	 * Arma un resolutor con la configuración base y, opcionalmente, la del perfil y la
	 * variable de entorno, respetando la precedencia de Spring Boot.
	 *
	 * @param perfil nombre del perfil cuyo archivo se superpone, o {@code null} para un
	 *               entorno que no declara archivo propio
	 * @param valorVariable valor de {@code API_DOCUMENTATION_ENABLED}, o {@code null} si
	 *                      el entorno no la define
	 * @return resolutor listo para consultar propiedades ya resueltas
	 * @throws IOException si algún archivo de propiedades no se puede leer
	 */
	private PropertySourcesPropertyResolver resolutor(String perfil, String valorVariable) throws IOException {
		MutablePropertySources fuentes = new MutablePropertySources();
		fuentes.addLast(new ResourcePropertySource(new ClassPathResource("application.properties")));
		if (valorVariable != null) {
			fuentes.addFirst(new MapPropertySource("variablesDeEntorno", Map.of(VARIABLE, valorVariable)));
		}
		if (perfil != null) {
			fuentes.addFirst(new ResourcePropertySource(
					new ClassPathResource("application-" + perfil + ".properties")));
		}
		return new PropertySourcesPropertyResolver(fuentes);
	}

	@Test
	void sinVariableLaDocumentacionQuedaApagada() throws IOException {
		// Un entorno que no declara nada hereda el valor base de application.properties.
		// El defecto seguro es no publicar el contrato.
		PropertySourcesPropertyResolver ambiente = resolutor(null, null);

		assertThat(ambiente.getProperty(PROPIEDAD_INTERFAZ)).isEqualTo("false");
		assertThat(ambiente.getProperty(PROPIEDAD_DOCUMENTO)).isEqualTo("false");
	}

	@Test
	void laVariableEnciendeLaInterfazYElDocumentoALaVez() throws IOException {
		// Es el camino de desarrollo: un solo interruptor gobierna los dos recursos.
		PropertySourcesPropertyResolver ambiente = resolutor(null, "true");

		assertThat(ambiente.getProperty(PROPIEDAD_INTERFAZ)).isEqualTo("true");
		assertThat(ambiente.getProperty(PROPIEDAD_DOCUMENTO)).isEqualTo("true");
	}

	@Test
	void laVariableEnFalseApagaLosDosRecursos() throws IOException {
		PropertySourcesPropertyResolver ambiente = resolutor(null, "false");

		assertThat(ambiente.getProperty(PROPIEDAD_INTERFAZ)).isEqualTo("false");
		assertThat(ambiente.getProperty(PROPIEDAD_DOCUMENTO)).isEqualTo("false");
	}

	@Test
	void produccionApagaLaInterfazYElDocumento() throws IOException {
		PropertySourcesPropertyResolver ambiente = resolutor("prod", null);

		assertThat(ambiente.getProperty(PROPIEDAD_INTERFAZ)).isEqualTo("false");
		assertThat(ambiente.getProperty(PROPIEDAD_DOCUMENTO)).isEqualTo("false");
	}

	@Test
	void produccionIgnoraLaVariableEncendida() throws IOException {
		// El valor de application-prod.properties es literal, no un default. Una
		// variable mal puesta en el despliegue no puede exponer el contrato.
		PropertySourcesPropertyResolver ambiente = resolutor("prod", "true");

		assertThat(ambiente.getProperty(PROPIEDAD_INTERFAZ)).isEqualTo("false");
		assertThat(ambiente.getProperty(PROPIEDAD_DOCUMENTO)).isEqualTo("false");
	}
}
