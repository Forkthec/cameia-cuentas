package tech.cameia.cuentas.presentation.controller;

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
 * @see tech.cameia.cuentas.presentation.dto.HealthResponse
 */
@RestController
class HealthController {

	/**
	 * Confirma que la aplicación está atendiendo peticiones HTTP.
	 *
	 * <p>Devuelve siempre el mismo cuerpo. Si el proceso está caído o saturado, el
	 * cliente no recibe respuesta y esa ausencia es la señal de fallo.</p>
	 *
	 * @return {@code 200 OK} con el estado {@code "UP"}
	 */
	@GetMapping("/health")
	ResponseEntity<HealthResponse> health() {
		return ResponseEntity.ok(HealthResponse.up());
	}
}
