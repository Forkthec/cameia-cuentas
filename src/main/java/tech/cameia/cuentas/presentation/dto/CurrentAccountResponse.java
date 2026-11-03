package tech.cameia.cuentas.presentation.dto;

import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.PhoneNumber;

/**
 * Datos de la cuenta de quien llama.
 *
 * <p>No incluye correo ni contraseña: son de Firebase y la historia los excluye. El plan es siempre
 * {@code FREE} hasta que existan los planes de pago.</p>
 *
 * @param id identificador de la cuenta local
 * @param firstName nombres de la persona
 * @param lastName apellidos de la persona
 * @param birthDate fecha de nacimiento en ISO-8601, o {@code null} si la cuenta es anterior a que el registro la exigiera
 * @param phoneNumber celular en formato E.164, o {@code null} si no lo declaró
 * @param pronoun pronombres declarados, o {@code null} si no los declaró
 * @param status estado de la cuenta
 * @param plan plan de la cuenta; siempre {@code FREE}
 */
public record CurrentAccountResponse(
        @Schema(description = "Identificador de la cuenta local", example = "3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10")
        UUID id,
        @Schema(description = "Nombres de la persona", example = "María José")
        String firstName,
        @Schema(description = "Apellidos de la persona", example = "Gómez-Ruiz")
        String lastName,
        @Schema(description = "Fecha de nacimiento en formato ISO-8601 (aaaa-mm-dd); nula en las cuentas anteriores "
                + "a que el registro la exigiera", example = "1995-04-12", format = "date", nullable = true)
        LocalDate birthDate,
        @Schema(description = "Celular en formato internacional E.164; nulo si no lo declaró", example = "+573001234567",
                nullable = true)
        String phoneNumber,
        @Schema(description = "Pronombres declarados; nulo si no los declaró", example = "SHE",
                allowableValues = {"HE", "SHE", "THEY"}, nullable = true)
        String pronoun,
        @Schema(description = "Estado de la cuenta", example = "ACTIVE",
                allowableValues = {"PENDING_VERIFICATION", "ACTIVE", "DISABLED"})
        String status,
        @Schema(description = "Plan de la cuenta; siempre FREE hasta que existan los planes de pago", example = "FREE",
                allowableValues = {"FREE"})
        String plan) {

    /** Plan de toda cuenta mientras no existan los planes de pago. */
    private static final String FREE_PLAN = "FREE";

    /**
     * Construye la respuesta a partir de la cuenta leída.
     *
     * @param account cuenta de quien llama
     * @return respuesta lista para serializarse, con {@code null} en lo que la cuenta no tiene
     */
    public static CurrentAccountResponse from(Account account) {
        return new CurrentAccountResponse(
                account.getId(),
                account.getFirstName(),
                account.getLastName(),
                account.getBirthDate().map(BirthDate::value).orElse(null),
                account.getPhoneNumber().map(PhoneNumber::value).orElse(null),
                account.getPronoun().map(Enum::name).orElse(null),
                account.getStatus().name(),
                FREE_PLAN);
    }
}
