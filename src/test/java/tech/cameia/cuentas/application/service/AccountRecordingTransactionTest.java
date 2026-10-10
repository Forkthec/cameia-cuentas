package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

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
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.Pronoun;

/**
 * Prueba de integración de la transacción del guardado conjunto: si el evento no puede guardarse, la cuenta tampoco
 * queda en la base de datos. Corre contra PostgreSQL real porque lo que se comprueba es la reversión de la transacción.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AccountRecordingTransactionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private AccountRecordingService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Si el evento no se puede guardar, la cuenta se deshace")
    void recordNewAccount_shouldRollBackAccount_whenEventCannotBeStored() {
        jdbc.update("""
                INSERT INTO microcuentas.evento_saliente (id, tipo, version, agregado_id, carga, id_correlacion, fecha_creacion)
                VALUES (gen_random_uuid(), 'cuenta.creada', 1, 'uid-rollback', '{}'::jsonb, 'req-0', now())
                """);
        Account account = Account.register("uid-rollback", "Ana", "Pérez", new BirthDate(LocalDate.of(2008, 3, 15)), null,
                Pronoun.SHE);

        assertThatThrownBy(() -> service.recordNewAccount(account, new EmailAddress("ana.perez@ejemplo.test"), "req-1"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM microcuentas.cuenta WHERE firebase_uid = 'uid-rollback'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Guarda la cuenta y su evento juntos cuando todo sale bien")
    void recordNewAccount_shouldPersistBoth_whenEventCanBeStored() {
        Account account = Account.register("uid-commit", "Ana", "Pérez", new BirthDate(LocalDate.of(2008, 3, 15)), null,
                Pronoun.SHE);

        service.recordNewAccount(account, new EmailAddress("ana.perez@ejemplo.test"), "req-1");

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM microcuentas.cuenta WHERE firebase_uid = 'uid-commit'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM microcuentas.evento_saliente WHERE agregado_id = 'uid-commit'", Integer.class))
                .isEqualTo(1);
    }
}
