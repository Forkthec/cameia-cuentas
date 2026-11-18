package tech.cameia.cuentas.infrastructure.config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la política de tratamiento de datos.
 *
 * @param currentVersion versión vigente, la única que se puede aceptar; su formato lo valida {@code CurrentPolicy}
 */
@Validated
@ConfigurationProperties("cuentas.policy")
public record PolicyProperties(@NotBlank String currentVersion) {
}
