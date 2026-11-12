package tech.cameia.cuentas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

import jakarta.validation.constraints.NotBlank;

import tech.cameia.cuentas.domain.model.SingleLineText;

/**
 * Cuerpo de la solicitud que registra la aceptación de la política.
 *
 * <p>Solo lleva la versión que la persona vio: la cuenta sale del encabezado del Gateway y el instante, del reloj
 * del servicio. Cualquier otro campo del cuerpo se ignora.</p>
 *
 * @param policyVersion versión de la política que la persona aceptó; debe ser la vigente
 */
public record PolicyAcceptanceRequest(
        @Schema(description = "Versión de la política aceptada, con formato AAAA-MM-vN; debe ser la vigente",
                example = "2026-11-v1", minLength = 1, maxLength = 20, requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "Recarga la página y vuelve a aceptar la política.")
        @CurrentPolicyVersion
        String policyVersion) {

    /** Recorta y normaliza la versión antes de validarla, igual que en el registro. */
    public PolicyAcceptanceRequest {
        policyVersion = SingleLineText.normalize(policyVersion);
    }
}
