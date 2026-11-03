package tech.cameia.cuentas.infrastructure.config;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Configuración de la publicación de eventos de cuenta, validada al arrancar.
 *
 * <p>El plazo acota cuánto puede sumar la publicación inmediata al registro: si el broker tarda más, el evento queda
 * pendiente y lo publica la tarea de relevo. Un valor fuera de rango impide el arranque en lugar de degradar el registro.</p>
 *
 * @param enabled si la publicación en RabbitMQ está encendida
 * @param publishTimeout plazo máximo de espera de la confirmación del broker, de 100 ms a 10 s
 */
@Validated
@ConfigurationProperties("cuentas.events")
public record AccountEventsProperties(
        boolean enabled,
        @NotNull @DurationMin(millis = 100) @DurationMax(seconds = 10) Duration publishTimeout) {
}
