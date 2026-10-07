package tech.cameia.cuentas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Aplica la migración que relaja el mínimo del celular sobre una base que ya tiene cuentas, como
 * ocurrirá en un entorno desplegado o en la base local de quien ya registró usuarios, y no solo
 * sobre una base vacía.
 *
 * <p>No usa Spring: migra con Flyway hasta la versión anterior, inserta filas y migra hasta la
 * última. Cada prueba tiene su propio contenedor para empezar siempre sin historia.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class MigracionTelefonoConDatosTest {

    private static final String ESQUEMA = "microcuentas";

    @Container
    PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void lasCuentasExistentesSobrevivenSinCambiosYLuegoSeAdmitenLosNumerosDeSieteDigitos() throws SQLException {
        migrarHasta("3");
        insertar("uid-colombia", "+573001234567");
        insertar("uid-quince-digitos", "+123456789012345");
        insertar("uid-sin-celular", null);
        List<String> antes = telefonos();

        migrarHasta("latest");

        assertThat(telefonos()).isEqualTo(antes);
        insertar("uid-tokelau", "+6903101");
        insertar("uid-seis-digitos", "+431234");
        assertThat(telefonos()).hasSize(5);
    }

    @Test
    void antesDeLaMigracionUnNumeroDeSieteDigitosSeRechazaba() throws SQLException {
        // Fija el defecto que corrige la migración: sin ella, el registro de un número válido
        // de Tokelau terminaba en un error interno al guardar.
        migrarHasta("3");

        assertThatThrownBy(() -> insertar("uid-tokelau", "+6903101"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_cuenta_telefono_e164");
    }

    @Test
    void despuesDeLaMigracionSiguenRechazadosLosDemasiadoCortosLosDemasiadoLargosYSinIndicativo()
            throws SQLException {
        migrarHasta("latest");

        for (String invalido : List.of("+12345", "3001234567", "+0123456", "+57 3001234567")) {
            assertThatThrownBy(() -> insertar("uid-" + invalido.hashCode(), invalido))
                    .as(invalido)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_cuenta_telefono_e164");
        }
        // Dieciséis dígitos no caben ni en la columna, de 16 caracteres con el «+»: la frena antes
        // el tamaño que la restricción.
        assertThatThrownBy(() -> insertar("uid-largo", "+1234567890123456"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("character varying(16)");
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

    private void insertar(String firebaseUid, String telefono) throws SQLException {
        try (Connection conexion = conectar();
                PreparedStatement sentencia = conexion.prepareStatement("""
                        INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido, telefono)
                        VALUES (gen_random_uuid(), ?, 'Ana', 'Perez', ?)
                        """)) {
            sentencia.setString(1, firebaseUid);
            sentencia.setString(2, telefono);
            sentencia.executeUpdate();
        }
    }

    private List<String> telefonos() throws SQLException {
        List<String> resultado = new ArrayList<>();
        try (Connection conexion = conectar();
                PreparedStatement sentencia = conexion.prepareStatement(
                        "SELECT firebase_uid || '=' || coalesce(telefono, 'NULL') FROM microcuentas.cuenta ORDER BY firebase_uid");
                ResultSet filas = sentencia.executeQuery()) {
            while (filas.next()) {
                resultado.add(filas.getString(1));
            }
        }
        return resultado;
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
