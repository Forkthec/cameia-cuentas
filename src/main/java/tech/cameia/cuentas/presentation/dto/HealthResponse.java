package tech.cameia.cuentas.presentation.dto;

/**
 * Respuesta del endpoint de salud del microservicio de Cuentas.
 *
 * <p>Es el contrato público de {@code GET /health} y su única razón de existir es
 * evitar que el controlador devuelva un mapa sin forma declarada. Al ser un tipo
 * explícito, springdoc publica su esquema en {@code /v3/api-docs} y Scalar lo muestra
 * en {@code /scalar} junto con esta descripción.</p>
 *
 * <p>Solo informa que el proceso atendió la petición HTTP. No comprueba la conexión a
 * PostgreSQL ni el estado de Firebase, Wompi o RabbitMQ.</p>
 *
 * @param status estado de la aplicación; constante {@code "UP"} mientras el proceso
 *               responde peticiones
 */
public record HealthResponse(String status) {

    /** Único valor de estado que expone el endpoint cuando la aplicación responde. */
    private static final String STATUS_UP = "UP";

    /**
     * Construye la respuesta que representa una aplicación en funcionamiento.
     *
     * @return respuesta con estado {@code "UP"}
     */
    public static HealthResponse up() {
        return new HealthResponse(STATUS_UP);
    }
}
