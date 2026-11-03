package tech.cameia.cuentas.infrastructure.messaging.payload;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonProperty;

import tech.cameia.cuentas.domain.event.AccountCreated;

/**
 * Cuerpo JSON de {@code cuenta.creada} versión 1. Los nombres de campo los fija el contrato publicado.
 * Todo valor es texto para que el formato en el cable no dependa de los valores por defecto del serializador.
 *
 * @param userId {@code firebaseUid} de la cuenta
 * @param email correo normalizado
 * @param birthDate fecha de nacimiento ISO-8601 ({@code yyyy-MM-dd})
 * @param createdAt instante UTC con milisegundos y sufijo {@code Z}
 */
public record AccountCreatedPayloadV1(
        @JsonProperty("usuarioId") String userId,
        @JsonProperty("email") String email,
        @JsonProperty("fechaNacimiento") String birthDate,
        @JsonProperty("creadaEn") String createdAt) {

    private static final DateTimeFormatter INSTANT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * Construye la carga a partir del evento de dominio.
     *
     * @param event evento de cuenta creada
     * @return la carga del evento, con fecha ISO e instante UTC con milisegundos
     */
    public static AccountCreatedPayloadV1 from(AccountCreated event) {
        return new AccountCreatedPayloadV1(event.firebaseUid(), event.email().value(),
                event.birthDate().value().toString(), INSTANT_FORMAT.format(event.createdAt()));
    }
}
