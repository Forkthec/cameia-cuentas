package tech.cameia.cuentas.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del registro de cuentas.
 *
 * @param allowlist lista de correos permitidos tal como llega de {@code SIGNUP_ALLOWLIST}: entradas separadas por punto
 *                  y coma; {@code null} o vacía deja el registro abierto. Sus entradas las valida
 *                  {@code SignupAllowlistPolicy}
 */
@ConfigurationProperties("cuentas.signup")
public record SignupProperties(String allowlist) {
}
