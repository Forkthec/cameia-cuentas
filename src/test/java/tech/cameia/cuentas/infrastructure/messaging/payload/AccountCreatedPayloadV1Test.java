package tech.cameia.cuentas.infrastructure.messaging.payload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.cuentas.domain.event.AccountCreated;
import tech.cameia.cuentas.domain.event.CorrelationId;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del cuerpo JSON de {@code cuenta.creada} versión 1: el texto exacto y su coincidencia con el JSON Schema
 * publicado (sin una librería de validación de esquemas, solo con lo que el esquema declara).
 */
class AccountCreatedPayloadV1Test {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path SCHEMA = Path.of("docs/eventos/cuenta-creada-v1.schema.json");
    private static final String UID = "6f1d2c3b4a5e4f60718293a4b5c6d7e8";

    private static AccountCreated event(LocalDate birthDate, Instant createdAt) {
        return new AccountCreated(UUID.fromString("11111111-1111-4111-8111-111111111111"), UID,
                new EmailAddress("ana.perez@ejemplo.test"), new BirthDate(birthDate), createdAt, new CorrelationId("req-1"));
    }

    private static String serialize(AccountCreated event) {
        return JSON.writeValueAsString(AccountCreatedPayloadV1.from(event));
    }

    private static JsonNode schema() throws IOException {
        return JSON.readTree(Files.readString(SCHEMA));
    }

    private static final AccountCreated SAMPLE = event(LocalDate.of(2008, 3, 15), Instant.parse("2026-10-09T15:04:05.123Z"));

    @Test
    @DisplayName("Serializa el texto exacto del contrato")
    void serialize_shouldWriteExactText_whenEventIsSample() {
        assertThat(serialize(SAMPLE)).isEqualTo(
                "{\"usuarioId\":\"6f1d2c3b4a5e4f60718293a4b5c6d7e8\",\"email\":\"ana.perez@ejemplo.test\","
                        + "\"fechaNacimiento\":\"2008-03-15\",\"creadaEn\":\"2026-10-09T15:04:05.123Z\"}");
    }

    @Test
    @DisplayName("Conserva el 29 de febrero de un año bisiesto")
    void from_shouldKeepLeapDay_whenBornOnFebruary29() {
        assertThat(AccountCreatedPayloadV1.from(event(LocalDate.of(2008, 2, 29), SAMPLE.createdAt())).birthDate())
                .isEqualTo("2008-02-29");
    }

    @Test
    @DisplayName("Escribe siempre los milisegundos aunque sean cero")
    void from_shouldWriteMilliseconds_whenInstantIsMidnight() {
        assertThat(AccountCreatedPayloadV1.from(event(LocalDate.of(2008, 3, 15), Instant.parse("2026-12-31T00:00:00Z")))
                .createdAt()).isEqualTo("2026-12-31T00:00:00.000Z");
    }

    @Test
    @DisplayName("La carga tiene exactamente cuatro campos")
    void serialize_shouldHaveExactlyFourFields() {
        assertThat(JSON.readTree(serialize(SAMPLE)).propertyNames()).containsExactlyInAnyOrder(
                "usuarioId", "email", "fechaNacimiento", "creadaEn");
    }

    @Test
    @DisplayName("La carga del evento de ejemplo es igual al ejemplo del esquema")
    void payload_shouldMatchSchemaExample_whenSerialized() throws IOException {
        assertThat(JSON.readTree(serialize(SAMPLE))).isEqualTo(schema().get("examples").get(0));
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
    @DisplayName("Cada valor cumple el patrón y el largo máximo de su propiedad en el esquema")
    void payload_shouldMatchSchemaPatterns_whenSerialized() throws IOException {
        JsonNode properties = schema().get("properties");
        JsonNode payload = JSON.readTree(serialize(SAMPLE));

        for (String name : payload.propertyNames()) {
            String value = payload.get(name).asString();
            JsonNode property = properties.get(name);
            if (property.has("pattern")) {
                assertThat(Pattern.compile(property.get("pattern").asString()).matcher(value).find())
                        .as("el campo %s cumple su patrón", name).isTrue();
            }
            if (property.has("maxLength")) {
                assertThat(value.length()).as("el campo %s respeta su largo máximo", name)
                        .isLessThanOrEqualTo(property.get("maxLength").asInt());
            }
        }
    }

    @Test
    @DisplayName("Un correo con tildes sale tal cual y el esquema lo admite como correo internacional")
    void payload_shouldDeclareIdnEmail_whenEmailHasNonAsciiCharacters() throws IOException {
        AccountCreated unicode = new AccountCreated(UUID.fromString("11111111-1111-4111-8111-111111111111"), UID,
                new EmailAddress("josé.ñandú@ejemplo.test"), new BirthDate(LocalDate.of(2008, 3, 15)),
                Instant.parse("2026-10-09T15:04:05.123Z"), new CorrelationId("req-1"));

        JsonNode payload = JSON.readTree(serialize(unicode));

        assertThat(payload.get("email").asString()).isEqualTo("josé.ñandú@ejemplo.test");
        assertThat(schema().get("properties").get("email").get("format").asString()).isEqualTo("idn-email");
    }
}
