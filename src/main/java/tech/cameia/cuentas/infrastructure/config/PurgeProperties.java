package tech.cameia.cuentas.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la purga de cuentas sin verificar, validada al arrancar.
 *
 * <p>El tope acota cuánto trabajo hace una ejecución de la purga y cuántas llamadas a Firebase suma. Un valor fuera de
 * rango impide el arranque en lugar de dejar una purga sin freno o que nunca avanza.</p>
 *
 * @param maxAccountsPerRun cuentas como máximo por ejecución, de 1 a 1000
 */
@Validated
@ConfigurationProperties("cuentas.purge")
public record PurgeProperties(@Min(1) @Max(1000) int maxAccountsPerRun) {
}
