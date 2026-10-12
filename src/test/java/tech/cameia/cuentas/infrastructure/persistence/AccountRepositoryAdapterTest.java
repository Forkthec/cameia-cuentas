package tech.cameia.cuentas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

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

import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.PhoneNumber;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.domain.port.AccountRepository;
import tech.cameia.cuentas.infrastructure.persistence.repository.AccountJpaRepository;

/**
 * Prueba de integración del adaptador de persistencia descrito en
 * {@code specs/CM-14-RegistroUsuario/spec.md} (REQ-CU-04 y REQ-CU-16).
 *
 * <p>Corre contra PostgreSQL real porque lo que se verifica no es el código del
 * repositorio sino su encuentro con el esquema: el mapeo de columnas, el valor inicial de
 * la versión y las restricciones de la tabla. Un doble en memoria no probaría nada de
 * eso.</p>
 *
 * <p>Se omite si no hay Docker, igual que el resto de pruebas con contenedores.</p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AccountRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private AccountRepository repositorio;

    @Autowired
    private AccountJpaRepository repositorioJpa;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void guardaYRecuperaUnaCuentaConTodosSusDatos() {
        Account guardada = repositorio.save(cuentaDe("uid-completo"));

        Optional<Account> recuperada = repositorio.findByFirebaseUid("uid-completo");

        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().getId()).isEqualTo(guardada.getId());
        assertThat(recuperada.get().getFirstName()).isEqualTo("Ana");
        assertThat(recuperada.get().getLastName()).isEqualTo("Pérez");
        assertThat(recuperada.get().getBirthDate()).contains(new BirthDate(LocalDate.of(1995, 4, 12)));
        assertThat(recuperada.get().getPhoneNumber()).contains(new PhoneNumber("+573001234567"));
        assertThat(recuperada.get().getPronoun()).contains(Pronoun.SHE);
        assertThat(recuperada.get().getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    void unaCuentaNuevaSeGuardaEsperandoLaVerificacionDelCorreo() {
        repositorio.save(cuentaDe("uid-pendiente"));

        assertThat(repositorioJpa.findByFirebaseUid("uid-pendiente"))
                .get()
                .satisfies(fila -> {
                    assertThat(fila.getEstado()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
                    assertThat(fila.getVersion()).isNotNull();
                    assertThat(fila.getFechaCreacion()).isNotNull();
                    assertThat(fila.getFechaActualizacion()).isNotNull();
                    assertThat(fila.getFechaEliminacion()).isNull();
                });
    }

    @Test
    void elCelularYLosPronombresSeGuardanVaciosCuandoNoSeDeclaran() {
        Account sinOpcionales = Account.register("uid-minimo", "Luis", "Gómez",
                new BirthDate(LocalDate.of(1990, 1, 1)), null, null);

        repositorio.save(sinOpcionales);

        Optional<Account> recuperada = repositorio.findByFirebaseUid("uid-minimo");
        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().getPhoneNumber()).isEmpty();
        assertThat(recuperada.get().getPronoun()).isEmpty();
    }

    @Test
    void activarLaCuentaActualizaLaFilaExistenteEnVezDeInsertarOtra() {
        Account cuenta = repositorio.save(cuentaDe("uid-activable"));
        cuenta.activate();

        repositorio.save(cuenta);

        assertThat(repositorio.findByFirebaseUid("uid-activable"))
                .get()
                .extracting(Account::getStatus)
                .isEqualTo(AccountStatus.ACTIVE);
        assertThat(repositorioJpa.count()).isPositive();
    }

    @Test
    void laBaseRechazaDosCuentasParaElMismoUsuarioDeFirebase() {
        repositorio.save(cuentaDe("uid-duplicado"));
        Account otra = cuentaDe("uid-duplicado");

        // Se salta el adaptador a propósito: comprueba que la unicidad la impone la base
        // de datos y no la lógica de la aplicación.
        assertThatThrownBy(() -> {
            repositorioJpa.saveAndFlush(new tech.cameia.cuentas.infrastructure.persistence.entity.AccountEntity(
                    otra.getId(), otra.getFirebaseUid(), otra.getFirstName(), otra.getLastName(),
                    otra.getBirthDate().orElseThrow().value(), null, null, otra.getStatus()));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void buscarUnUsuarioSinCuentaDevuelveVacio() {
        assertThat(repositorio.findByFirebaseUid("uid-inexistente")).isEmpty();
    }

    @Test
    @DisplayName("Solo lista las cuentas pendientes creadas antes del límite, excluyendo el instante exacto")
    void findPendingVerificationCreatedBefore_shouldReturnOnlyOldPendingAccounts_whenMixedStates() {
        jdbc.update("DELETE FROM microcuentas.cuenta");
        Instant now = Instant.parse("2026-10-10T08:00:00Z");
        insertAt("uid-169h", "PENDING_VERIFICATION", now.minus(169, ChronoUnit.HOURS));
        insertAt("uid-168h", "PENDING_VERIFICATION", now.minus(168, ChronoUnit.HOURS));
        insertAt("uid-10h", "PENDING_VERIFICATION", now.minus(10, ChronoUnit.HOURS));
        insertAt("uid-activa", "ACTIVE", now.minus(300, ChronoUnit.HOURS));
        insertAt("uid-deshabilitada", "DISABLED", now.minus(300, ChronoUnit.HOURS));

        List<String> uids = repositorio.findPendingVerificationCreatedBefore(now.minus(168, ChronoUnit.HOURS), 10);

        assertThat(uids).containsExactly("uid-169h");
    }

    @Test
    @DisplayName("Respeta el máximo y devuelve las más antiguas primero")
    void findPendingVerificationCreatedBefore_shouldReturnOldestFirst_whenMoreThanMax() {
        jdbc.update("DELETE FROM microcuentas.cuenta");
        Instant now = Instant.parse("2026-10-10T08:00:00Z");
        insertAt("uid-b", "PENDING_VERIFICATION", now.minus(200, ChronoUnit.HOURS));
        insertAt("uid-c", "PENDING_VERIFICATION", now.minus(180, ChronoUnit.HOURS));
        insertAt("uid-a", "PENDING_VERIFICATION", now.minus(220, ChronoUnit.HOURS));

        List<String> uids = repositorio.findPendingVerificationCreatedBefore(now.minus(168, ChronoUnit.HOURS), 2);

        assertThat(uids).containsExactly("uid-a", "uid-b");
    }

    @Test
    @DisplayName("Sin cuentas pendientes viejas devuelve una lista vacía")
    void findPendingVerificationCreatedBefore_shouldReturnEmpty_whenNothingMatches() {
        jdbc.update("DELETE FROM microcuentas.cuenta");

        assertThat(repositorio.findPendingVerificationCreatedBefore(Instant.parse("2026-10-10T08:00:00Z"), 5)).isEmpty();
    }

    @Test
    @DisplayName("Devuelve solo los usuarios que tienen cuenta, en cualquier estado")
    void findExistingFirebaseUids_shouldReturnOnlyUsersWithAccount_whenSomeHaveNone() {
        jdbc.update("DELETE FROM microcuentas.cuenta");
        Instant now = Instant.parse("2026-10-10T08:00:00Z");
        insertAt("uid-pendiente", "PENDING_VERIFICATION", now);
        insertAt("uid-deshabilitada", "DISABLED", now);

        assertThat(repositorio.findExistingFirebaseUids(List.of("uid-pendiente", "uid-deshabilitada", "uid-sin-cuenta")))
                .containsExactlyInAnyOrder("uid-pendiente", "uid-deshabilitada");
    }

    @Test
    @DisplayName("Una lista vacía devuelve un conjunto vacío sin consultar la base")
    void findExistingFirebaseUids_shouldReturnEmpty_whenListIsEmpty() {
        assertThat(repositorio.findExistingFirebaseUids(List.of())).isEmpty();
    }

    private void insertAt(String firebaseUid, String status, Instant createdAt) {
        // fecha_actualizacion debe ser >= fecha_creacion (ck_cuenta_fecha_actualizacion)
        jdbc.update("""
                INSERT INTO microcuentas.cuenta (id, firebase_uid, nombre, apellido, estado, pronombres,
                                                 fecha_creacion, fecha_actualizacion)
                VALUES (gen_random_uuid(), ?, 'Ana', 'Perez', ?, 'SHE', ?, ?)
                """, firebaseUid, status, Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private Account cuentaDe(String firebaseUid) {
        return Account.register(firebaseUid, "Ana", "Pérez",
                new BirthDate(LocalDate.of(1995, 4, 12)),
                new PhoneNumber("+573001234567"), Pronoun.SHE);
    }
}
