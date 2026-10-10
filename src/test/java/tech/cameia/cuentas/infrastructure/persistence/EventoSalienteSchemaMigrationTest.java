package tech.cameia.cuentas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.cuentas.domain.event.CorrelationId;
import tech.cameia.cuentas.domain.model.UnannouncedAccount;

/**
 * Prueba de integración del esquema de la tabla de salida {@code evento_saliente}.
 *
 * <p>Levanta PostgreSQL 16 real: el arranque aplica las migraciones con Flyway y Hibernate valida el mapeo.
 * Cada prueba viola una restricción de la base con SQL literal y comprueba el nombre de la restricción, porque son la
 * red de seguridad para cuando el error esté en el código. Se omite si no hay Docker.</p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EventoSalienteSchemaMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String EVENT_ID = "11111111-1111-4111-8111-111111111111";
    private static final String CREATED = "2026-10-09T15:04:05.123Z";
    private static final String PAYLOAD = "{\"usuarioId\":\"uid-1\"}";

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTable() {
        jdbc.update("DELETE FROM microcuentas.evento_saliente");
    }

    @Test
    @DisplayName("Una fila pendiente válida se guarda con cero intentos")
    void insert_shouldSucceed_whenRowIsPending() {
        insert(EVENT_ID, "cuenta.creada", 1, "uid-1", "'" + PAYLOAD + "'", "req-1", CREATED, null);

        Integer attempts = jdbc.queryForObject(
                "SELECT intentos FROM microcuentas.evento_saliente WHERE id = ?::uuid", Integer.class, EVENT_ID);
        assertThat(attempts).isZero();
    }

    @Test
    @DisplayName("Un tipo desconocido se rechaza")
    void insert_shouldFail_whenTypeIsUnknown() {
        assertViolation(() -> insert(EVENT_ID, "cuenta.borrada", 1, "uid-1", "'" + PAYLOAD + "'", "req-1", CREATED, null),
                "ck_evento_saliente_tipo", "23514");
    }

    @Test
    @DisplayName("La versión cero se rechaza")
    void insert_shouldFail_whenVersionIsZero() {
        assertViolation(() -> insert(EVENT_ID, "cuenta.creada", 0, "uid-1", "'" + PAYLOAD + "'", "req-1", CREATED, null),
                "ck_evento_saliente_version", "23514");
    }

    @Test
    @DisplayName("Un agregado en blanco se rechaza")
    void insert_shouldFail_whenAggregateIsBlank() {
        assertViolation(() -> insert(EVENT_ID, "cuenta.creada", 1, "   ", "'" + PAYLOAD + "'", "req-1", CREATED, null),
                "ck_evento_saliente_agregado_id", "23514");
    }

    @Test
    @DisplayName("Una correlación con espacios se rechaza")
    void insert_shouldFail_whenCorrelationHasSpaces() {
        assertViolation(() -> insert(EVENT_ID, "cuenta.creada", 1, "uid-1", "'" + PAYLOAD + "'", "req 1", CREATED, null),
                "ck_evento_saliente_id_correlacion", "23514");
    }

    @Test
    @DisplayName("Una correlación de 65 caracteres no cabe en la columna")
    void insert_shouldFail_whenCorrelationHas65Characters() {
        assertViolation(() -> insert(EVENT_ID, "cuenta.creada", 1, "uid-1", "'" + PAYLOAD + "'", "a".repeat(65), CREATED, null),
                "value too long", "22001");
    }

    @Test
    @DisplayName("Los intentos negativos se rechazan")
    void insert_shouldFail_whenAttemptsAreNegative() {
        assertViolation(() -> jdbc.update("""
                INSERT INTO microcuentas.evento_saliente
                    (id, tipo, version, agregado_id, carga, id_correlacion, fecha_creacion, intentos)
                VALUES (?::uuid, 'cuenta.creada', 1, 'uid-1', '{"usuarioId":"uid-1"}', 'req-1', ?::timestamptz, -1)
                """, EVENT_ID, CREATED), "ck_evento_saliente_intentos", "23514");
    }

    @Test
    @DisplayName("Una fila pendiente sin carga se rechaza")
    void insert_shouldFail_whenPendingRowHasNoPayload() {
        assertViolation(() -> insert(EVENT_ID, "cuenta.creada", 1, "uid-1", "NULL", "req-1", CREATED, null),
                "ck_evento_saliente_carga_pendiente", "23514");
    }

    @Test
    @DisplayName("Una fila publicada que conserva la carga se rechaza")
    void insert_shouldFail_whenPublishedRowKeepsPayload() {
        assertViolation(() -> insert(EVENT_ID, "cuenta.creada", 1, "uid-1", "'" + PAYLOAD + "'", "req-1", CREATED, CREATED),
                "ck_evento_saliente_carga_pendiente", "23514");
    }

    @Test
    @DisplayName("Una publicación anterior a la creación se rechaza")
    void insert_shouldFail_whenPublishedBeforeCreated() {
        assertViolation(() -> insert(EVENT_ID, "cuenta.creada", 1, "uid-1", "NULL", "req-1", CREATED,
                "2026-10-09T15:04:04.123Z"), "ck_evento_saliente_fecha_publicacion", "23514");
    }

    @Test
    @DisplayName("Dos eventos del mismo tipo para la misma cuenta se rechazan")
    void insert_shouldFail_whenSameTypeAndAggregateRepeat() {
        insert(EVENT_ID, "cuenta.creada", 1, "uid-1", "'" + PAYLOAD + "'", "req-1", CREATED, null);

        assertViolation(() -> insert("22222222-2222-4222-8222-222222222222", "cuenta.creada", 1, "uid-1",
                "'" + PAYLOAD + "'", "req-2", CREATED, null), "uq_evento_saliente_tipo_agregado", "23505");
    }

    @Test
    @DisplayName("Una carga que no es JSON se rechaza")
    void insert_shouldFail_whenPayloadIsNotJson() {
        assertViolation(() -> insert(EVENT_ID, "cuenta.creada", 1, "uid-1", "'no es json'", "req-1", CREATED, null),
                "json", "22P02");
    }

    @Test
    @DisplayName("Los largos de columna coinciden con los límites del dominio")
    void columnLengths_shouldMatchDomainLimits() {
        Map<String, Integer> lengths = lengthsOf("evento_saliente");

        assertThat(lengths.get("agregado_id")).isEqualTo(lengthsOf("cuenta").get("firebase_uid")).isEqualTo(128);
        assertThat(lengths.get("agregado_id")).isEqualTo(UnannouncedAccount.FIREBASE_UID_MAX_LENGTH);
        assertThat(lengths.get("id_correlacion")).isEqualTo(CorrelationId.MAX_LENGTH).isEqualTo(64);
    }

    @Test
    @DisplayName("Existe el índice parcial de eventos pendientes")
    void pendingIndex_shouldExist_whenMigrationApplied() {
        List<String> definitions = jdbc.queryForList("""
                SELECT indexdef FROM pg_indexes
                 WHERE schemaname = 'microcuentas' AND indexname = 'ix_evento_saliente_pendiente'
                """, String.class);

        assertThat(definitions).hasSize(1);
        assertThat(definitions.get(0)).contains("(fecha_creacion)").contains("WHERE (fecha_publicacion IS NULL)");
    }

    private void insert(String id, String type, int version, String aggregateId, String payloadLiteral,
            String correlation, String createdAt, String publishedAt) {
        jdbc.update("""
                INSERT INTO microcuentas.evento_saliente
                    (id, tipo, version, agregado_id, carga, id_correlacion, fecha_creacion, fecha_publicacion)
                VALUES (?::uuid, ?, ?, ?, %s::jsonb, ?, ?::timestamptz, ?::timestamptz)
                """.formatted(payloadLiteral), id, type, version, aggregateId, correlation, createdAt, publishedAt);
    }

    private Map<String, Integer> lengthsOf(String table) {
        return jdbc.query("""
                SELECT column_name, character_maximum_length FROM information_schema.columns
                 WHERE table_schema = 'microcuentas' AND table_name = ? AND character_maximum_length IS NOT NULL
                """, rs -> {
            Map<String, Integer> result = new java.util.HashMap<>();
            while (rs.next()) {
                result.put(rs.getString(1), rs.getInt(2));
            }
            return result;
        }, table);
    }

    private static void assertViolation(Runnable action, String expectedText, String sqlState) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(expectedText)
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(sqlState));
    }
}
