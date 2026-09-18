package tech.cameia.cuentas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Test
    void guardaYRecuperaUnaCuentaConTodosSusDatos() {
        Account guardada = repositorio.save(cuentaDe("uid-completo"));

        Optional<Account> recuperada = repositorio.findByFirebaseUid("uid-completo");

        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().getId()).isEqualTo(guardada.getId());
        assertThat(recuperada.get().getFirstName()).isEqualTo("Ana");
        assertThat(recuperada.get().getLastName()).isEqualTo("Pérez");
        assertThat(recuperada.get().getBirthDate()).isEqualTo(new BirthDate(LocalDate.of(1995, 4, 12)));
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
                    otra.getBirthDate().value(), null, null, otra.getStatus()));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void buscarUnUsuarioSinCuentaDevuelveVacio() {
        assertThat(repositorio.findByFirebaseUid("uid-inexistente")).isEmpty();
    }

    private Account cuentaDe(String firebaseUid) {
        return Account.register(firebaseUid, "Ana", "Pérez",
                new BirthDate(LocalDate.of(1995, 4, 12)),
                new PhoneNumber("+573001234567"), Pronoun.SHE);
    }
}
