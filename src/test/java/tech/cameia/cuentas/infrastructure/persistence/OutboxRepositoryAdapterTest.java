package tech.cameia.cuentas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.cuentas.domain.event.AccountCreated;
import tech.cameia.cuentas.domain.event.CorrelationId;
import tech.cameia.cuentas.domain.event.OutboundEvent;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.port.OutboxRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prueba de integración del adaptador de la tabla de salida contra PostgreSQL real: lo que se comprueba es el encuentro del
 * código con el esquema (carga {@code jsonb}, unicidad por cuenta, orden de los pendientes y borrado de la carga).
 *
 * <p>Se omite si no hay Docker, igual que el resto de pruebas con contenedores.</p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OutboxRepositoryAdapterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String UID = "6f1d2c3b4a5e4f60718293a4b5c6d7e8";
    private static final Instant BASE_TIME = Instant.parse("2026-10-09T15:04:05.100Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTable() {
        jdbc.update("DELETE FROM microcuentas.evento_saliente");
    }

    private static AccountCreated event(String eventId, String uid, Instant createdAt) {
        return new AccountCreated(UUID.fromString(eventId), uid, new EmailAddress("ana.perez@ejemplo.test"),
                new BirthDate(LocalDate.of(2008, 3, 15)), createdAt, new CorrelationId("req-1"));
    }

    private static AccountCreated event(String eventId, String uid) {
        return event(eventId, uid, Instant.parse("2026-10-09T15:04:05.123Z"));
    }

    @Test
    @DisplayName("Guarda la carga exacta y las columnas del evento nuevo")
    void appendAccountCreated_shouldStoreExactPayload_whenEventIsNew() throws Exception {
        boolean stored = outbox.appendAccountCreated(event("11111111-1111-4111-8111-111111111111", UID));

        assertThat(stored).isTrue();
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT tipo, version, agregado_id, carga::text AS carga, id_correlacion, intentos, fecha_publicacion,
                       fecha_creacion
                  FROM microcuentas.evento_saliente WHERE id = '11111111-1111-4111-8111-111111111111'
                """);
        JsonNode expected = JSON.readTree("""
                {"usuarioId":"6f1d2c3b4a5e4f60718293a4b5c6d7e8","email":"ana.perez@ejemplo.test",
                 "fechaNacimiento":"2008-03-15","creadaEn":"2026-10-09T15:04:05.123Z"}
                """);
        assertThat(JSON.readTree((String) row.get("carga"))).isEqualTo(expected);
        assertThat(row.get("tipo")).isEqualTo("cuenta.creada");
        assertThat(((Number) row.get("version")).intValue()).isEqualTo(1);
        assertThat(row.get("agregado_id")).isEqualTo(UID);
        assertThat(row.get("id_correlacion")).isEqualTo("req-1");
        assertThat(((Number) row.get("intentos")).intValue()).isZero();
        assertThat(row.get("fecha_publicacion")).isNull();
        assertThat(((Timestamp) row.get("fecha_creacion")).toInstant()).isEqualTo(Instant.parse("2026-10-09T15:04:05.123Z"));
    }

    @Test
    @DisplayName("Devuelve false y no duplica la fila cuando la cuenta ya tiene su evento")
    void appendAccountCreated_shouldReturnFalse_whenAccountAlreadyHasEvent() {
        outbox.appendAccountCreated(event("11111111-1111-4111-8111-111111111111", UID));

        boolean second = outbox.appendAccountCreated(event("22222222-2222-4222-8222-222222222222", UID));

        assertThat(second).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM microcuentas.evento_saliente", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Devuelve los pendientes del más antiguo al más reciente y respeta el límite")
    void findPending_shouldReturnOldestFirst_whenSeveralArePending() {
        outbox.appendAccountCreated(event("11111111-1111-4111-8111-111111111111", "uid1", BASE_TIME));
        outbox.appendAccountCreated(event("33333333-3333-4333-8333-333333333333", "uid3", BASE_TIME.plusMillis(200)));
        outbox.appendAccountCreated(event("22222222-2222-4222-8222-222222222222", "uid2", BASE_TIME.plusMillis(100)));

        List<OutboundEvent> all = outbox.findPending(10);
        List<OutboundEvent> limited = outbox.findPending(2);

        assertThat(all).extracting(OutboundEvent::aggregateId).containsExactly("uid1", "uid2", "uid3");
        assertThat(limited).extracting(OutboundEvent::aggregateId).containsExactly("uid1", "uid2");
        assertThat(all.get(0).type()).isEqualTo("cuenta.creada");
        assertThat(all.get(0).version()).isEqualTo(1);
        assertThat(all.get(0).correlationId()).isEqualTo("req-1");
        assertThat(all.get(0).attempts()).isZero();
        assertThat(JSON.readTree(all.get(0).payloadJson()).get("usuarioId").asString()).isEqualTo("uid1");
    }

    @Test
    @DisplayName("No devuelve los eventos ya publicados")
    void findPending_shouldSkipPublished_whenOneIsPublished() {
        outbox.appendAccountCreated(event("11111111-1111-4111-8111-111111111111", "uid1", BASE_TIME));
        outbox.appendAccountCreated(event("22222222-2222-4222-8222-222222222222", "uid2", BASE_TIME.plusMillis(100)));
        outbox.markPublished(UUID.fromString("11111111-1111-4111-8111-111111111111"), BASE_TIME.plusSeconds(1));

        assertThat(outbox.findPending(10)).extracting(OutboundEvent::aggregateId).containsExactly("uid2");
    }

    @Test
    @DisplayName("Al publicar borra la carga y guarda el instante")
    void markPublished_shouldClearPayload_whenPending() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        outbox.appendAccountCreated(event(id.toString(), UID));
        Instant publishedAt = Instant.parse("2026-10-09T15:04:06.000Z");

        boolean marked = outbox.markPublished(id, publishedAt);

        assertThat(marked).isTrue();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT carga::text AS carga, fecha_publicacion FROM microcuentas.evento_saliente WHERE id = ?", id);
        assertThat(row.get("carga")).isNull();
        assertThat(((Timestamp) row.get("fecha_publicacion")).toInstant()).isEqualTo(publishedAt);
    }

    @Test
    @DisplayName("Marcar como publicado dos veces devuelve false la segunda")
    void markPublished_shouldReturnFalse_whenAlreadyPublished() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        outbox.appendAccountCreated(event(id.toString(), UID));
        outbox.markPublished(id, Instant.parse("2026-10-09T15:04:06.000Z"));

        assertThat(outbox.markPublished(id, Instant.parse("2026-10-09T15:04:07.000Z"))).isFalse();
    }

    @Test
    @DisplayName("Marcar como publicado un evento desconocido devuelve false")
    void markPublished_shouldReturnFalse_whenUnknownId() {
        assertThat(outbox.markPublished(UUID.fromString("99999999-9999-4999-8999-999999999999"), BASE_TIME)).isFalse();
    }

    @Test
    @DisplayName("Cada fallo suma un intento al evento pendiente")
    void recordFailedAttempt_shouldIncrementAttempts_whenPending() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        outbox.appendAccountCreated(event(id.toString(), UID));

        outbox.recordFailedAttempt(id);
        outbox.recordFailedAttempt(id);

        assertThat(jdbc.queryForObject("SELECT intentos FROM microcuentas.evento_saliente WHERE id = ?", Integer.class, id))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("Un fallo sobre un evento ya publicado no cambia sus intentos")
    void recordFailedAttempt_shouldNotChangePublished_whenAlreadyPublished() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        outbox.appendAccountCreated(event(id.toString(), UID));
        outbox.markPublished(id, Instant.parse("2026-10-09T15:04:06.000Z"));

        outbox.recordFailedAttempt(id);

        assertThat(jdbc.queryForObject("SELECT intentos FROM microcuentas.evento_saliente WHERE id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    @DisplayName("Buscar por id devuelve el evento pendiente y nada si ya se publicó o no existe")
    void findPendingById_shouldBeEmpty_whenPublished() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        outbox.appendAccountCreated(event(id.toString(), UID));

        assertThat(outbox.findPendingById(id)).get().extracting(OutboundEvent::aggregateId).isEqualTo(UID);
        outbox.markPublished(id, Instant.parse("2026-10-09T15:04:06.000Z"));

        assertThat(outbox.findPendingById(id)).isEmpty();
        assertThat(outbox.findPendingById(UUID.fromString("99999999-9999-4999-8999-999999999999"))).isEmpty();
    }

    @Test
    @DisplayName("Cuenta solo los pendientes")
    void countPending_shouldCountOnlyPending() {
        outbox.appendAccountCreated(event("11111111-1111-4111-8111-111111111111", "uid1", BASE_TIME));
        outbox.appendAccountCreated(event("22222222-2222-4222-8222-222222222222", "uid2", BASE_TIME.plusMillis(100)));
        outbox.markPublished(UUID.fromString("11111111-1111-4111-8111-111111111111"), BASE_TIME.plusSeconds(1));

        assertThat(outbox.countPending()).isEqualTo(1);
    }
}
