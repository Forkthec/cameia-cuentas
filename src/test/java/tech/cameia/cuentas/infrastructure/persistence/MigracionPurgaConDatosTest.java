package tech.cameia.cuentas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Aplica las migraciones del índice de la purga (V6) y del evento de cuenta eliminada (V7) sobre una base que ya tiene
 * cuentas y eventos, como ocurrirá en un entorno desplegado, y no solo sobre una base vacía.
 *
 * <p>No usa Spring: migra con Flyway hasta V5, inserta una cuenta pendiente y su evento {@code cuenta.creada}, y migra
 * hasta la última versión. Así la prueba falla si alguna de las dos migraciones altera o rechaza esas filas.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Migraciones del índice de la purga y del evento de cuenta eliminada con filas previas")
class MigracionPurgaConDatosTest {

    private static final String SCHEMA = "microcuentas";

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("conservan la cuenta pendiente y su evento, y el CHECK ampliado admite la baja")
    void migrationsV6AndV7_shouldKeepExistingRowsAndAcceptDeletionEvent_whenRowsExistBefore() throws SQLException {
        migrateTo("5");
        execute("""
                INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido, pronombres)
                VALUES (gen_random_uuid(), 'uid-previa', 'Ana', 'Perez', 'SHE')
                """);
        execute("""
                INSERT INTO microcuentas.evento_saliente
                    (id, tipo, version, agregado_id, carga, id_correlacion, fecha_creacion)
                VALUES (gen_random_uuid(), 'cuenta.creada', 1, 'uid-previa', '{}'::jsonb, 'req-1', now())
                """);

        migrateTo("7");

        assertThat(count("microcuentas.cuenta WHERE estado = 'PENDING_VERIFICATION'")).isEqualTo(1);
        assertThat(count("microcuentas.evento_saliente WHERE tipo = 'cuenta.creada' AND fecha_publicacion IS NULL"))
                .isEqualTo(1);
        execute("""
                INSERT INTO microcuentas.evento_saliente
                    (id, tipo, version, agregado_id, carga, id_correlacion, fecha_creacion)
                VALUES (gen_random_uuid(), 'cuenta.eliminada', 1, 'uid-previa', '{}'::jsonb, 'req-2', now())
                """);
        assertThat(count("microcuentas.evento_saliente")).isEqualTo(2);
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .createSchemas(true)
                .target(version)
                .load()
                .migrate();
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private int count(String tableAndFilter) throws SQLException {
        try (Connection connection = connect();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + tableAndFilter)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
