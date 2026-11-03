package tech.cameia.cuentas.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.cameia.cuentas.presentation.dto.HealthResponse;

/**
 * Expone la señal de disponibilidad del microservicio de Cuentas.
 *
 * <p>Lo consultan la instrucción {@code HEALTHCHECK} de la imagen de contenedor y las
 * sondas de la plataforma de despliegue para decidir si el proceso puede recibir
 * tráfico. Por eso el endpoint no depende de la base de datos ni de ninguna
 * integración externa: una respuesta lenta o fallida por causas ajenas al proceso
 * provocaría reinicios innecesarios.</p>
 *
 * <p>Es el único endpoint del servicio que no exige el contrato de entrada del API
 * Gateway, porque no lee ni modifica datos de la cuenta.</p>
 *
 * <p>La ruta lleva el prefijo {@code /api/v1/users} porque el API Gateway reenvía la
 * ruta completa, sin reescribirla: una sonda en {@code /health} quedaría inalcanzable
 * a través del Gateway.</p>
 *
 * @see tech.cameia.cuentas.presentation.dto.HealthResponse
 */
@RestController
class HealthController {

	private static final String EJEMPLO_406 = """
			{"type":"about:blank","title":"Tipo de respuesta no admitido","status":406,
			 "detail":"Tipo de respuesta no admitido.","instance":"/api/v1/users/health",
			 "code":"MEDIA_TYPE_NOT_ACCEPTABLE","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

	private static final String EJEMPLO_415 = """
			{"type":"about:blank","title":"Tipo de contenido no admitido","status":415,
			 "detail":"Tipo de contenido no admitido.","instance":"/api/v1/users/health",
			 "code":"MEDIA_TYPE_NOT_ALLOWED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

	private static final String EJEMPLO_413 = """
			{"type":"about:blank","title":"Cuerpo demasiado grande","status":413,
			 "detail":"Cuerpo de la petición demasiado grande.","instance":"/api/v1/users/health",
			 "code":"REQUEST_BODY_TOO_LARGE","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

	/**
	 * Confirma que la aplicación está atendiendo peticiones HTTP.
	 *
	 * <p>Devuelve siempre el mismo cuerpo. Si el proceso está caído o saturado, el
	 * cliente no recibe respuesta y esa ausencia es la señal de fallo.</p>
	 *
	 * @return {@code 200 OK} con el estado {@code "UP"}
	 */
	@Operation(summary = "Comprueba que el servicio atiende peticiones",
			description = "Señal de disponibilidad sin identidad: no lee la base de datos ni ninguna integración.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "El servicio está arriba"),
		@ApiResponse(responseCode = "406", description = "MEDIA_TYPE_NOT_ACCEPTABLE: el cliente solo acepta un "
				+ "formato distinto de JSON, o su Accept no se puede interpretar", content = @Content(mediaType = "application/problem+json",
						schema = @Schema(implementation = ProblemDetail.class),
						examples = @ExampleObject(name = "formatoNoDisponible", value = EJEMPLO_406))),
		@ApiResponse(responseCode = "415", description = "MEDIA_TYPE_NOT_ALLOWED: el tipo de contenido no se admite. "
				+ "REQUEST_MULTIPART_INVALID_FORMAT: un multipart/* que no se pudo leer, por ejemplo sin boundary",
				content = @Content(mediaType = "application/problem+json",
						schema = @Schema(implementation = ProblemDetail.class),
						examples = @ExampleObject(name = "tipoNoAdmitido", value = EJEMPLO_415))),
		@ApiResponse(responseCode = "413", description = "REQUEST_BODY_TOO_LARGE: un multipart, o el cuerpo, supera el tamaño admitido (1 MiB por parte y 10 MiB por petición)",
				content = @Content(mediaType = "application/problem+json",
						schema = @Schema(implementation = ProblemDetail.class),
						examples = @ExampleObject(name = "cuerpoDemasiadoGrande", value = EJEMPLO_413)))
	})
	@GetMapping("/api/v1/users/health")
	ResponseEntity<HealthResponse> health() {
		return ResponseEntity.ok(HealthResponse.up());
	}
}
