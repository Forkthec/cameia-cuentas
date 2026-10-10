package tech.cameia.cuentas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.UnannouncedAccount;
import tech.cameia.cuentas.domain.port.AccountRepository;

/**
 * Prueba de la consulta de cuentas que nunca tuvieron su evento de cuenta creada, contra PostgreSQL real: lo que se
 * comprueba es el encuentro de la consulta nativa con el esquema (estado, fecha de nacimiento, tabla de salida y orden).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("AccountRepository.findUnannounced")
class UnannouncedAccountsQueryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM microcuentas.evento_saliente");
        jdbc.update("DELETE FROM microcuentas.cuenta");
    }

    @Test
    @DisplayName("devuelve las cuentas sin evento cuando otras ya lo tienen")
    void findUnannounced_shouldReturnAccountsWithoutEvent_whenSomeHaveIt() {
        insertAccount("uid-a", "ACTIVE", "1995-04-12", "2026-10-01T08:00:00Z");
        insertAccount("uid-b", "PENDING_VERIFICATION", "1990-01-31", "2026-10-02T08:00:00Z");
        insertAccount("uid-c", "ACTIVE", "2000-02-29", "2026-10-03T08:00:00Z");
        insertPendingEvent("uid-b");

        List<UnannouncedAccount> found = accounts.findUnannounced(10);

        assertThat(found).extracting(UnannouncedAccount::firebaseUid).containsExactly("uid-a", "uid-c");
        assertThat(found.get(0).birthDate()).isEqualTo(new BirthDate(LocalDate.of(1995, 4, 12)));
        assertThat(found.get(0).createdAt()).isEqualTo(Instant.parse("2026-10-01T08:00:00Z"));
    }

    @Test
    @DisplayName("excluye las cuentas anonimizadas")
    void findUnannounced_shouldExcludeAnonymized() {
        insertAccount("uid-activa", "ACTIVE", "1995-04-12", "2026-10-01T08:00:00Z");
        jdbc.update("""
                INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido, fecha_nacimiento, pronombres, estado,
                                                 version, fecha_creacion, fecha_actualizacion, fecha_eliminacion)
                VALUES (gen_random_uuid(), 'uid-anonima', 'Anonimizado', 'Anonimizado', '1995-04-12', NULL, 'ANONYMIZED',
                        0, '2026-10-01T09:00:00Z', now(), now())
                """);

        assertThat(accounts.findUnannounced(10)).extracting(UnannouncedAccount::firebaseUid).containsExactly("uid-activa");
    }

    @Test
    @DisplayName("excluye las cuentas sin fecha de nacimiento")
    void findUnannounced_shouldExcludeMissingBirthDate() {
        insertAccount("uid-con-fecha", "ACTIVE", "1995-04-12", "2026-10-01T08:00:00Z");
        insertAccount("uid-sin-fecha", "ACTIVE", null, "2026-10-01T09:00:00Z");

        assertThat(accounts.findUnannounced(10)).extracting(UnannouncedAccount::firebaseUid).containsExactly("uid-con-fecha");
    }

    @Test
    @DisplayName("devuelve primero la más antigua y respeta el límite")
    void findUnannounced_shouldReturnOldestFirstAndRespectLimit() {
        insertAccount("uid-nueva", "ACTIVE", "1995-04-12", "2026-10-03T08:00:00Z");
        insertAccount("uid-vieja", "ACTIVE", "1995-04-12", "2026-10-01T08:00:00Z");
        insertAccount("uid-media", "ACTIVE", "1995-04-12", "2026-10-02T08:00:00Z");

        List<UnannouncedAccount> found = accounts.findUnannounced(1);

        assertThat(found).extracting(UnannouncedAccount::firebaseUid).containsExactly("uid-vieja");
    }

    @Test
    @DisplayName("no devuelve la cuenta cuyo evento ya se publicó")
    void findUnannounced_shouldIgnorePublishedEventsToo() {
        insertAccount("uid-publicada", "ACTIVE", "1995-04-12", "2026-10-01T08:00:00Z");
        insertPublishedEvent("uid-publicada");

        assertThat(accounts.findUnannounced(10)).isEmpty();
    }

    @Test
    @DisplayName("vuelve a devolver la cuenta cuando se borró la fila de su evento")
    void findUnannounced_shouldReturnAccount_whenItsEventRowWasDeleted() {
        insertAccount("uid-republicar", "ACTIVE", "1995-04-12", "2026-10-01T08:00:00Z");
        insertPublishedEvent("uid-republicar");
        assertThat(accounts.findUnannounced(10)).isEmpty();

        jdbc.update("DELETE FROM microcuentas.evento_saliente WHERE agregado_id = 'uid-republicar'");

        assertThat(accounts.findUnannounced(10)).extracting(UnannouncedAccount::firebaseUid).containsExactly("uid-republicar");
    }

    private void insertAccount(String uid, String status, String birthDate, String createdAt) {
        jdbc.update("""
                INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido, fecha_nacimiento, pronombres, estado,
                                                 version, fecha_creacion, fecha_actualizacion)
                VALUES (gen_random_uuid(), ?, 'Ana', 'Pérez', ?::date, 'SHE', ?, 0, ?::timestamptz, now())
                """, uid, birthDate, status, createdAt);
    }

    private void insertPendingEvent(String uid) {
        jdbc.update("""
                INSERT INTO microcuentas.evento_saliente (id, tipo, version, agregado_id, carga, id_correlacion, fecha_creacion)
                VALUES (gen_random_uuid(), 'cuenta.creada', 1, ?, '{}'::jsonb, 'corr-1', now())
                """, uid);
    }

    private void insertPublishedEvent(String uid) {
        jdbc.update("""
                INSERT INTO microcuentas.evento_saliente (id, tipo, version, agregado_id, carga, id_correlacion, fecha_creacion,
                                                          fecha_publicacion)
                VALUES (gen_random_uuid(), 'cuenta.creada', 1, ?, NULL, 'corr-1', now(), now())
                """, uid);
    }
}
