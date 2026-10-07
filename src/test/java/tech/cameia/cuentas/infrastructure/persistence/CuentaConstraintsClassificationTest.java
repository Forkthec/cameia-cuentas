package tech.cameia.cuentas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Comprueba que toda restricción de la tabla de cuentas está clasificada y que una
 * violación no deja el valor de la columna en el log.
 *
 * <p>Las restricciones de la tabla son invariantes internas: la validación del contrato y
 * del dominio actúa antes de llegar a la base, así que ninguna se viola por una entrada de
 * la persona. Si una migración agrega una restricción nueva, esta prueba falla hasta que
 * alguien decida si es alcanzable y si necesita un código de error propio.</p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class CuentaConstraintsClassificationTest {

    /** Restricciones revisadas; todas son invariantes internas sin código propio. */
    private static final Set<String> CLASIFICADAS = Set.of(
            "cuenta_pkey", "uq_cuenta_firebase_uid", "ck_cuenta_nombre", "ck_cuenta_apellido",
            "ck_cuenta_estado", "ck_cuenta_version", "ck_cuenta_telefono_e164",
            "ck_cuenta_pronombres_no_vacio", "ck_cuenta_fecha_actualizacion",
            "ck_cuenta_fecha_eliminacion", "ck_cuenta_anonimizacion");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transaccion;

    @Test
    void todaRestriccionDeLaTablaDeCuentasEstaClasificada() {
        Set<String> reales = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid = 'microcuentas.cuenta'::regclass",
                String.class));

        assertThat(reales)
                .as("Hay una restricción sin clasificar: agrégala a la clasificación de la spec y decide si "
                        + "necesita código propio")
                .containsExactlyInAnyOrderElementsOf(CLASIFICADAS);
    }

    @Test
    void unaViolacionDeRestriccionNoDejaLosValoresDeLaFilaEnElLog(CapturedOutput salida) {
        // Por Hibernate, como llega una escritura real; con parámetros, para que la línea de SQL
        // que se imprime en el perfil local no lleve los valores.
        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> entityManager.createNativeQuery(
                "INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido, telefono) "
                        + "VALUES (gen_random_uuid(), ?1, ?2, ?3, ?4)")
                .setParameter(1, "uid-restriccion")
                .setParameter(2, "Zoraida")
                .setParameter(3, "Quintero")
                .setParameter(4, "3001234567")
                .executeUpdate()))
                // El EntityManager directo no pasa por la traducción de Spring: llega la de Hibernate.
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);

        assertThat(salida.getOut())
                .doesNotContain("Zoraida")
                .doesNotContain("Quintero")
                .doesNotContain("3001234567")
                .doesNotContain("Failing row");
    }
}