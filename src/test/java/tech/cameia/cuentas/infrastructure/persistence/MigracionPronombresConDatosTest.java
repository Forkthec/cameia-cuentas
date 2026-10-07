package tech.cameia.cuentas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Aplica la migración que restringe los pronombres sobre una base que ya tiene datos, como
 * ocurrirá en un entorno desplegado, y no solo sobre una base vacía.
 *
 * <p>No usa Spring: migra con Flyway hasta la versión anterior, inserta filas y migra hasta
 * la última. Cada prueba tiene su propio contenedor para empezar siempre sin historia.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class MigracionPronombresConDatosTest {

    private static final String ESQUEMA = "microcuentas";

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void lasFilasConPronombreValidoONuloSobrevivenALaMigracionYLuegoRigeLaRestriccion() throws SQLException {
        migrarHasta("2");
        insertar("uid-con-pronombre", "SHE");
        insertar("uid-anonimizada", null);

        migrarHasta("latest");

        assertThat(contarFilas()).isEqualTo(2);
        assertThatThrownBy(() -> insertar("uid-otro", "OTRO"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_cuenta_pronombres_valor");
    }

    @Test
    void unaFilaConUnPronombreFueraDeLaListaImpideAplicarLaMigracion() throws SQLException {
        // Es el riesgo del despliegue: por eso se confirma antes qué valores hay en staging.
        migrarHasta("2");
        insertar("uid-otro", "OTRO");

        assertThatThrownBy(() -> migrarHasta("latest"))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("ck_cuenta_pronombres_valor");
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

    private void insertar(String firebaseUid, String pronombre) throws SQLException {
        try (Connection conexion = conectar();
                PreparedStatement sentencia = conexion.prepareStatement("""
                        INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido, pronombres)
                        VALUES (gen_random_uuid(), ?, 'Ana', 'Perez', ?)
                        """)) {
            sentencia.setString(1, firebaseUid);
            sentencia.setString(2, pronombre);
            sentencia.executeUpdate();
        }
    }

    private int contarFilas() throws SQLException {
        try (Connection conexion = conectar();
                Statement sentencia = conexion.createStatement();
                ResultSet resultado = sentencia.executeQuery("SELECT count(*) FROM microcuentas.cuenta")) {
            resultado.next();
            return resultado.getInt(1);
        }
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
