package tech.cameia.cuentas.presentation.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.cuentas.domain.model.CurrentPolicy;
import tech.cameia.cuentas.domain.model.PolicyAcceptance;

/**
 * Estado de la aceptación de la política de tratamiento de datos de una cuenta.
 *
 * @param acceptedVersion versión que la persona aceptó, o {@code sin-aceptar} si nunca aceptó una
 * @param acceptedAt instante UTC de la aceptación, o {@code null} si nunca aceptó una versión
 * @param currentVersion versión vigente; si es distinta de la aceptada, la web pide aceptarla
 */
public record PolicyAcceptanceResponse(
        @Schema(description = "Versión aceptada; sin-aceptar si la persona nunca aceptó una", example = "2026-11-v1",
                maxLength = 20)
        String acceptedVersion,
        @Schema(description = "Instante UTC de la aceptación; nulo si nunca aceptó una versión",
                example = "2026-11-14T15:30:00Z", format = "date-time", nullable = true)
        Instant acceptedAt,
        @Schema(description = "Versión vigente; si es distinta de la aceptada, la web pide aceptarla",
                example = "2026-11-v1")
        String currentVersion) {

    /**
     * Construye la respuesta a partir de la aceptación guardada y la versión vigente.
     *
     * @param acceptance aceptación guardada en la cuenta
     * @param currentPolicy versión vigente de la política
     * @return respuesta lista para serializarse; la fecha va en {@code null} si nunca aceptó
     */
    public static PolicyAcceptanceResponse from(PolicyAcceptance acceptance, CurrentPolicy currentPolicy) {
        return new PolicyAcceptanceResponse(acceptance.version(), acceptance.acceptedAtIfAccepted().orElse(null),
                currentPolicy.version());
    }
}
