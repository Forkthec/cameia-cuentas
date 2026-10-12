package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
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

import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.domain.port.AccountRepository;

/**
 * Prueba de integración de la transacción del borrado: si el evento no puede guardarse, la cuenta tampoco se borra.
 * Corre contra PostgreSQL real porque lo que se comprueba es la reversión de la transacción.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PendingAccountDeletionTransactionTest {

    private static final String UID = "uid-purga";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private PendingAccountDeletionService service;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM microcuentas.evento_saliente");
        jdbc.update("DELETE FROM microcuentas.cuenta");
    }

    private void saveAccount(AccountStatus status) {
        accounts.save(Account.rebuild(UUID.randomUUID(), UID, "Ana", "Pérez", new BirthDate(LocalDate.of(1995, 4, 12)),
                null, Pronoun.SHE, status));
    }

    private int accountRows() {
        return jdbc.queryForObject("SELECT count(*) FROM microcuentas.cuenta WHERE firebase_uid = ?", Integer.class, UID);
    }

    private int deletionEvents() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM microcuentas.evento_saliente WHERE tipo = 'cuenta.eliminada' AND agregado_id = ?",
                Integer.class, UID);
    }

    @Test
    @DisplayName("Borra la fila y guarda el evento juntos cuando la cuenta sigue pendiente")
    void deletePending_shouldDeleteRowAndStoreEvent_whenAccountIsPending() {
        saveAccount(AccountStatus.PENDING_VERIFICATION);

        boolean deleted = service.deletePending(UID);

        assertThat(deleted).isTrue();
        assertThat(accountRows()).isZero();
        assertThat(deletionEvents()).isEqualTo(1);
    }

    @Test
    @DisplayName("Si el evento no se puede guardar, la fila se conserva")
    void deletePending_shouldKeepRow_whenEventInsertFails() {
        saveAccount(AccountStatus.PENDING_VERIFICATION);
        jdbc.update("""
                INSERT INTO microcuentas.evento_saliente (id, tipo, version, agregado_id, carga, id_correlacion, fecha_creacion)
                VALUES (gen_random_uuid(), 'cuenta.eliminada', 1, ?, '{}'::jsonb, 'req-0', now())
                """, UID);

        assertThatThrownBy(() -> service.deletePending(UID)).isInstanceOf(IllegalStateException.class);

        assertThat(accountRows()).isEqualTo(1);
        assertThat(deletionEvents()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una cuenta activa no se borra y no genera evento")
    void deletePending_shouldNotDelete_whenAccountIsActive() {
        saveAccount(AccountStatus.ACTIVE);

        boolean deleted = service.deletePending(UID);

        assertThat(deleted).isFalse();
        assertThat(accountRows()).isEqualTo(1);
        assertThat(deletionEvents()).isZero();
    }
}
