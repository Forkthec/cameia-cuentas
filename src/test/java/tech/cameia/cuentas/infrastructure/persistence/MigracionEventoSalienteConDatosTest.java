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
 * Aplica la migración que crea la tabla de salida de eventos sobre una base que ya tiene cuentas, como ocurrirá en un
 * entorno desplegado, y no solo sobre una base vacía.
 *
 * <p>No usa Spring: migra con Flyway hasta la versión anterior, inserta cuentas y migra hasta la última. Así la prueba falla
 * si la migración altera las cuentas o genera eventos por su cuenta.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Migración de la tabla de salida de eventos con cuentas previas")
class MigracionEventoSalienteConDatosTest {

    private static final String SCHEMA = "microcuentas";

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("conserva las cuentas existentes y no crea eventos para ellas")
    void migrationV5_shouldKeepAccountsAndCreateNoEvents_whenAccountsExistBefore() throws SQLException {
        migrateTo("4");
        insertAccount("uid-previa-1", "Ana", "SHE");
        insertAccount("uid-previa-2", "Luis", "HE");

        migrateTo("5");

        assertThat(count("microcuentas.cuenta")).isEqualTo(2);
        assertThat(count("microcuentas.evento_saliente")).isZero();
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

    private void insertAccount(String firebaseUid, String firstName, String pronouns) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido, pronombres)
                    VALUES (gen_random_uuid(), '%s', '%s', 'Perez', '%s')
                    """.formatted(firebaseUid, firstName, pronouns));
        }
    }

    private int count(String table) throws SQLException {
        try (Connection connection = connect();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
