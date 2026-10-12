package tech.cameia.cuentas.infrastructure.messaging.payload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.cuentas.domain.event.AccountDeleted;
import tech.cameia.cuentas.domain.event.CorrelationId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del cuerpo JSON de {@code cuenta.eliminada} versión 1: el texto exacto y su coincidencia con el JSON Schema
 * publicado (sin una librería de validación de esquemas, solo con lo que el esquema declara).
 */
class AccountDeletedPayloadV1Test {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path SCHEMA = Path.of("docs/eventos/cuenta-eliminada-v1.schema.json");

    private static AccountDeleted event(String uid, Instant deletedAt) {
        return new AccountDeleted(UUID.fromString("11111111-1111-4111-8111-111111111111"), uid, deletedAt,
                new CorrelationId("req-1"));
    }

    private static String serialize(AccountDeleted event) {
        return JSON.writeValueAsString(AccountDeletedPayloadV1.from(event));
    }

    private static JsonNode schema() throws IOException {
        return JSON.readTree(Files.readString(SCHEMA));
    }

    private static final AccountDeleted SAMPLE = event("uid-1", Instant.parse("2026-10-10T08:00:00.123456Z"));

    @Test
    @DisplayName("Serializa el texto exacto del contrato, con el instante truncado a milisegundos")
    void serialize_shouldWriteExactText_whenEventIsSample() {
        assertThat(serialize(SAMPLE)).isEqualTo("{\"usuarioId\":\"uid-1\",\"eliminadaEn\":\"2026-10-10T08:00:00.123Z\"}");
    }

    @Test
    @DisplayName("Escribe siempre los milisegundos aunque sean cero")
    void from_shouldWriteMilliseconds_whenInstantIsMidnight() {
        assertThat(AccountDeletedPayloadV1.from(event("uid-1", Instant.parse("2026-12-31T00:00:00Z"))).deletedAt())
                .isEqualTo("2026-12-31T00:00:00.000Z");
    }

    @Test
    @DisplayName("La carga tiene exactamente dos campos y ningún dato personal")
    void serialize_shouldHaveExactlyTwoFields() {
        assertThat(JSON.readTree(serialize(SAMPLE)).propertyNames()).containsExactlyInAnyOrder("usuarioId", "eliminadaEn");
    }

    @Test
    @DisplayName("Las claves serializadas son las propiedades y las obligatorias del esquema")
    void payload_shouldHaveExactlyTheSchemaProperties_whenSerialized() throws IOException {
        JsonNode schema = schema();
        List<String> serialized = new ArrayList<>(JSON.readTree(serialize(SAMPLE)).propertyNames());
        List<String> declared = new ArrayList<>(schema.get("properties").propertyNames());
        List<String> required = new ArrayList<>();
        schema.get("required").forEach(name -> required.add(name.asString()));

        assertThat(serialized).containsExactlyInAnyOrderElementsOf(declared).containsExactlyInAnyOrderElementsOf(required);
        assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Cada valor cumple el patrón y el largo de su propiedad en el esquema, incluido el uid de 128 caracteres")
    void payload_shouldMatchSchemaConstraints_whenSerialized() throws IOException {
        JsonNode properties = schema().get("properties");

        for (AccountDeleted event : List.of(SAMPLE, event("a".repeat(128), SAMPLE.deletedAt()))) {
            JsonNode payload = JSON.readTree(serialize(event));
            for (String name : payload.propertyNames()) {
                String value = payload.get(name).asString();
                JsonNode property = properties.get(name);
                if (property.has("pattern")) {
                    assertThat(Pattern.compile(property.get("pattern").asString()).matcher(value).find())
                            .as("el campo %s cumple su patrón", name).isTrue();
                }
                assertThat(value.length()).as("el campo %s respeta su largo mínimo", name)
                        .isGreaterThanOrEqualTo(property.has("minLength") ? property.get("minLength").asInt() : 0);
                assertThat(value.length()).as("el campo %s respeta su largo máximo", name)
                        .isLessThanOrEqualTo(property.has("maxLength") ? property.get("maxLength").asInt() : Integer.MAX_VALUE);
            }
        }
    }

    @Test
    @DisplayName("La carga del evento de ejemplo coincide con el ejemplo del esquema salvo el uid y el instante de la muestra")
    void payload_shouldHaveSameShapeAsSchemaExample_whenSerialized() throws IOException {
        JsonNode example = schema().get("examples").get(0);
        AccountDeleted fromExample = event(example.get("usuarioId").asString(), Instant.parse(example.get("eliminadaEn").asString()));

        assertThat(JSON.readTree(serialize(fromExample))).isEqualTo(example);
    }
}
