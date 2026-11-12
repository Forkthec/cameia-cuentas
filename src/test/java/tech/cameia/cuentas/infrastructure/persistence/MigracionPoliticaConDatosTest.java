package tech.cameia.cuentas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Aplica la migración de la aceptación de la política sobre una base que ya tiene cuentas, como
 * ocurrirá en los entornos desplegados.
 *
 * <p>No usa Spring: migra con Flyway hasta la versión anterior, inserta tres cuentas (pendiente,
 * activa y anonimizada) y migra hasta la última. Cada prueba tiene su propio contenedor.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class MigracionPoliticaConDatosTest {

    private static final String ESQUEMA = "microcuentas";

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    /** Versión de fila y fecha de actualización de cada cuenta antes de migrar. */
    private final Map<String, String> antes = new HashMap<>();

    @BeforeEach
    void prepararCuentasAnteriores() throws SQLException {
        migrarHasta("7");
        insertar("uid-pendiente", "PENDING_VERIFICATION", "2026-10-01T10:00:00Z");
        insertar("uid-activa", "ACTIVE", "2026-10-02T11:00:00Z");
        insertar("uid-anonimizada", "ANONYMIZED", "2026-10-03T12:00:00Z");
        antes.putAll(versionYActualizacion());
        migrarHasta("latest");
    }

    @Test
    @DisplayName("Las cuentas anteriores quedan con la versión «sin-aceptar»")
    void migrate_shouldSetNotAcceptedVersion_whenAccountExisted() throws SQLException {
        assertThat(consultar("SELECT politica_version FROM microcuentas.cuenta"))
                .hasSize(3)
                .containsOnlyKeys("uid-pendiente", "uid-activa", "uid-anonimizada")
                .allSatisfy((uid, version) -> assertThat(version).isEqualTo("sin-aceptar"));
    }

    @Test
    @DisplayName("La fecha de aceptación de las cuentas anteriores es la de su creación")
    void migrate_shouldUseCreationDate_whenAccountExisted() throws SQLException {
        assertThat(consultar("SELECT (politica_aceptada_en = fecha_creacion)::text FROM microcuentas.cuenta"))
                .allSatisfy((uid, iguales) -> assertThat(iguales).isEqualTo("true"));
    }

    @Test
    @DisplayName("Migrar no cambia la versión de la fila ni la fecha de actualización")
    void migrate_shouldKeepRowVersionAndUpdatedAt_whenMigrating() throws SQLException {
        assertThat(versionYActualizacion()).isEqualTo(antes);
    }

    private void migrarHasta(String version) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(ESQUEMA)
                .defaultSchema(ESQUEMA)
                .createSchemas(true)
                .target(version)
                .load()
                .migrate();
    }

    private void insertar(String firebaseUid, String estado, String creadaEn) throws SQLException {
        // Una cuenta anonimizada exige fecha de eliminación y pronombres nulos.
        boolean anonimizada = "ANONYMIZED".equals(estado);
        try (Connection conexion = conectar();
                PreparedStatement sentencia = conexion.prepareStatement("""
                        INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido, pronombres, estado,
                                                         fecha_creacion, fecha_actualizacion, fecha_eliminacion)
                        VALUES (gen_random_uuid(), ?, 'Ana', 'Perez', ?, ?, ?, ?, ?)
                        """)) {
            Timestamp creada = Timestamp.from(Instant.parse(creadaEn));
            sentencia.setString(1, firebaseUid);
            sentencia.setString(2, anonimizada ? null : "SHE");
            sentencia.setString(3, estado);
            sentencia.setTimestamp(4, creada);
            sentencia.setTimestamp(5, creada);
            sentencia.setTimestamp(6, anonimizada ? creada : null);
            sentencia.executeUpdate();
        }
    }

    private Map<String, String> versionYActualizacion() throws SQLException {
        return consultar("SELECT version || '|' || fecha_actualizacion::text FROM microcuentas.cuenta");
    }

    /** Ejecuta una consulta de una columna y la devuelve por {@code firebase_uid}. */
    private Map<String, String> consultar(String select) throws SQLException {
        String sql = select.replaceFirst("SELECT ", "SELECT firebase_uid, ");
        Map<String, String> filas = new HashMap<>();
        try (Connection conexion = conectar();
                Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery(sql)) {
            while (resultado.next()) {
                filas.put(resultado.getString(1), resultado.getString(2));
            }
        }
        return filas;
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
