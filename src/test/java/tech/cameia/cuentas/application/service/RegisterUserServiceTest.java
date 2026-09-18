package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tech.cameia.cuentas.application.command.RegisterUserCommand;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.exception.InvalidBirthDateException;
import tech.cameia.cuentas.domain.exception.WeakPasswordException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.domain.policy.AgePolicy;
import tech.cameia.cuentas.domain.policy.PasswordPolicy;
import tech.cameia.cuentas.domain.port.AccountRepository;
import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;

/**
 * Prueba del caso de uso de registro descrito en
 * {@code specs/CM-14-RegistroUsuario/spec.md} (REQ-CU-02 a REQ-CU-07).
 *
 * <p>Usa dobles en memoria de los dos puertos: lo que se verifica es el orden de las
 * llamadas y qué pasa cuando una falla, no el comportamiento de Firebase ni de
 * PostgreSQL.</p>
 */
class RegisterUserServiceTest {

    private final InMemoryFirebaseUserDirectory directorio = new InMemoryFirebaseUserDirectory();
    private final RepositorioEnMemoria repositorio = new RepositorioEnMemoria();

    private RegisterUserService servicio;

    @BeforeEach
    void prepararServicio() {
        servicio = new RegisterUserService(directorio, repositorio, new AgePolicy(), new PasswordPolicy());
    }

    @Test
    void registraLaCuentaPendienteDeVerificarYConPlanGratuito() {
        Account cuenta = servicio.register(comando());

        assertThat(cuenta.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(cuenta.getFirstName()).isEqualTo("Ana");
        assertThat(directorio.existe(cuenta.getFirebaseUid())).isTrue();
        assertThat(directorio.planDe(cuenta.getFirebaseUid())).isEqualTo("FREE");
        assertThat(repositorio.guardadas).hasSize(1);
    }

    @Test
    void rechazaUnCorreoYaRegistradoSinGuardarNada() {
        servicio.register(comando());

        assertThatThrownBy(() -> servicio.register(comando()))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Este correo ya se encuentra registrado");
        assertThat(repositorio.guardadas).hasSize(1);
    }

    @Test
    void noTocaFirebaseCuandoLaFechaDeNacimientoNoPermiteRegistrarse() {
        RegisterUserCommand menorDeEdad = new RegisterUserCommand("Ana", "Pérez",
                LocalDate.now().minusYears(15), "ana@cameia.tech", "frase secreta larga",
                null, null);

        assertThatThrownBy(() -> servicio.register(menorDeEdad))
                .isInstanceOf(InvalidBirthDateException.class);
        assertThat(repositorio.guardadas).isEmpty();
    }

    @Test
    void noTocaFirebaseCuandoLaContraseniaEsDebil() {
        RegisterUserCommand contraseniaCorta = new RegisterUserCommand("Ana", "Pérez",
                LocalDate.of(1995, 4, 12), "ana@cameia.tech", "corta", null, null);

        assertThatThrownBy(() -> servicio.register(contraseniaCorta))
                .isInstanceOf(WeakPasswordException.class);
        assertThat(repositorio.guardadas).isEmpty();
    }

    @Test
    void borraLaCredencialCuandoFallaLaEscrituraDelPlan() {
        directorio.fallarAlEscribirElPlan();

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(IllegalStateException.class);

        assertThat(repositorio.guardadas).isEmpty();
        // La credencial se borró, así que el correo vuelve a estar libre: el segundo
        // intento no choca con "este correo ya se encuentra registrado".
        directorio.dejarDeFallar();
        assertThat(servicio.register(comando()).getFirebaseUid()).isNotBlank();
    }

    @Test
    void borraLaCredencialCuandoFallaLaBaseDeDatos() {
        repositorio.fallarAlGuardar();

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(IllegalStateException.class);

        assertThat(repositorio.guardadas).isEmpty();
        assertThat(directorio.existe(repositorio.ultimoUid)).isFalse();
    }

    @Test
    void siLaCompensacionTambienFallaElRegistroSigueFallando() {
        repositorio.fallarAlGuardar();
        directorio.fallarAlBorrar();

        // El registro propaga el fallo original, no el del borrado: quien llama necesita
        // saber que el registro no se completó. La credencial huérfana queda en el log.
        assertThatThrownBy(() -> servicio.register(comando()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL");
    }

    private RegisterUserCommand comando() {
        return new RegisterUserCommand("Ana", "Pérez", LocalDate.of(1995, 4, 12),
                "ana@cameia.tech", "frase secreta larga", "+573001234567", Pronoun.SHE);
    }

    /** Repositorio en memoria que puede simular un fallo de la base de datos. */
    private static class RepositorioEnMemoria implements AccountRepository {

        private final Map<String, Account> guardadas = new HashMap<>();
        private boolean fallar;
        private String ultimoUid;

        @Override
        public Account save(Account account) {
            ultimoUid = account.getFirebaseUid();
            if (fallar) {
                throw new IllegalStateException("PostgreSQL no está disponible");
            }
            guardadas.put(account.getFirebaseUid(), account);
            return account;
        }

        @Override
        public Optional<Account> findByFirebaseUid(String firebaseUid) {
            return Optional.ofNullable(guardadas.get(firebaseUid));
        }

        void fallarAlGuardar() {
            this.fallar = true;
        }
    }
}
