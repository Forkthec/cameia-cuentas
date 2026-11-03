package tech.cameia.cuentas.infrastructure.messaging.payload;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonProperty;

import tech.cameia.cuentas.domain.event.AccountDeleted;

/**
 * Cuerpo JSON de {@code cuenta.eliminada} versión 1. Los nombres de campo los fija el contrato publicado.
 * Todo valor es texto para que el formato en el cable no dependa de los valores por defecto del serializador.
 *
 * @param userId {@code firebaseUid} de la cuenta eliminada
 * @param deletedAt instante UTC con milisegundos y sufijo {@code Z}
 */
public record AccountDeletedPayloadV1(
        @JsonProperty("usuarioId") String userId,
        @JsonProperty("eliminadaEn") String deletedAt) {

    private static final DateTimeFormatter INSTANT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * Construye la carga a partir del evento de dominio.
     *
     * @param event evento de cuenta eliminada
     * @return la carga del evento, con el instante UTC con milisegundos
     */
    public static AccountDeletedPayloadV1 from(AccountDeleted event) {
        return new AccountDeletedPayloadV1(event.firebaseUid(), INSTANT_FORMAT.format(event.deletedAt()));
    }
}
