package tech.cameia.cuentas.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.cuentas.presentation.dto.HealthResponse;

/**
 * Prueba unitaria del endpoint de salud descrito en
 * {@code specs/CM-103-EndpointSalud/spec.md}.
 *
 * <p>Usa {@code standaloneSetup} en lugar de {@code @WebMvcTest} porque el endpoint no
 * tiene colaboradores: no necesita contexto de Spring, base de datos ni configuración
 * externa. Así la prueba verifica el contrato HTTP sin depender del arranque de la
 * aplicación.</p>
 */
class HealthControllerTest {

	private HealthController controller;

	private MockMvc mockMvc;

	@BeforeEach
	void prepararControlador() {
		controller = new HealthController();
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void devuelveEstadoUpConCodigo200() {
		ResponseEntity<HealthResponse> respuesta = controller.health();

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isEqualTo(new HealthResponse("UP"));
	}

	@Test
	void serializaElEstadoComoJsonEnLaRutaDeSalud() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void noExponeCamposAdicionalesEnLaRespuesta() throws Exception {
		String cuerpo = mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(cuerpo).isEqualTo("{\"status\":\"UP\"}");
	}

	@Test
	void rechazaMetodosDistintosDeGet() throws Exception {
		mockMvc.perform(post("/health"))
				.andExpect(status().isMethodNotAllowed());
	}
}
