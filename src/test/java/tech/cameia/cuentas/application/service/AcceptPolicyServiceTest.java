package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tech.cameia.cuentas.application.command.AcceptPolicyCommand;
import tech.cameia.cuentas.domain.exception.AccountConflictException;
import tech.cameia.cuentas.domain.exception.AccountDisabledException;
import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
import tech.cameia.cuentas.domain.exception.PolicyVersionOutdatedException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.CurrentPolicy;
import tech.cameia.cuentas.domain.model.PolicyAcceptance;
import tech.cameia.cuentas.domain.model.PolicyAcceptanceFixture;
import tech.cameia.cuentas.infrastructure.persistence.InMemoryAccountRepository;

/**
 * Prueba del caso de uso que registra la aceptación de la política: instante del reloj, idempotencia, rechazo de la
 * versión vieja, estados de cuenta y carrera entre dos peticiones.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("AcceptPolicyService")
class AcceptPolicyServiceTest {

    private static final String UID = "uid-firebase";
    private static final String VIGENTE = "2026-11-v1";
    private static final Instant AHORA = Instant.parse("2026-11-14T15:30:00Z");

    private final RepositorioDePrueba repositorio = new RepositorioDePrueba();
    private final AcceptPolicyService servicio = new AcceptPolicyService(repositorio, new CurrentPolicy(VIGENTE),
            Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("Guarda el instante del reloj cuando la cuenta no había aceptado la versión")
    void accept_shouldStoreClockInstant_whenAccountWasNotAccepted() {
        repositorio.save(cuenta(AccountStatus.ACTIVE, PolicyAcceptance.NOT_ACCEPTED_VERSION));
        repositorio.guardados = 0;

        Account resultado = servicio.accept(new AcceptPolicyCommand(UID, VIGENTE));

        assertThat(resultado.getPolicyAcceptance().version()).isEqualTo(VIGENTE);
        assertThat(resultado.getPolicyAcceptance().acceptedAt()).isEqualTo(AHORA);
        assertThat(repositorio.guardados).isEqualTo(1);
    }

    @Test
    @DisplayName("Recorta el instante a microsegundos, la precisión de la columna")
    void accept_shouldTruncateInstantToMicroseconds() {
        Instant conNanos = Instant.parse("2026-11-14T15:30:00.123456789Z");
        AcceptPolicyService conRelojFino = new AcceptPolicyService(repositorio, new CurrentPolicy(VIGENTE),
                Clock.fixed(conNanos, ZoneOffset.UTC));
        repositorio.save(cuenta(AccountStatus.ACTIVE, PolicyAcceptance.NOT_ACCEPTED_VERSION));

        Account resultado = conRelojFino.accept(new AcceptPolicyCommand(UID, VIGENTE));

        assertThat(resultado.getPolicyAcceptance().acceptedAt()).isEqualTo(Instant.parse("2026-11-14T15:30:00.123456Z"));
    }

    @Test
    @DisplayName("No escribe cuando la versión ya estaba aceptada y conserva su fecha")
    void accept_shouldNotWrite_whenVersionAlreadyAccepted() {
        repositorio.save(cuenta(AccountStatus.ACTIVE, VIGENTE));
        repositorio.guardados = 0;

        Account resultado = servicio.accept(new AcceptPolicyCommand(UID, VIGENTE));

        assertThat(resultado.getPolicyAcceptance().acceptedAt()).isEqualTo(PolicyAcceptanceFixture.ACCEPTED_AT);
        assertThat(repositorio.guardados).isZero();
    }

    @Test
    @DisplayName("Rechaza una versión distinta de la vigente sin leer la cuenta")
    void accept_shouldThrowOutdated_whenVersionIsOld() {
        repositorio.save(cuenta(AccountStatus.ACTIVE, PolicyAcceptance.NOT_ACCEPTED_VERSION));
        repositorio.guardados = 0;

        assertThatThrownBy(() -> servicio.accept(new AcceptPolicyCommand(UID, "2026-10-v1")))
                .isInstanceOf(PolicyVersionOutdatedException.class);
        assertThat(repositorio.guardados).isZero();
    }

    @Test
    @DisplayName("Responde cuenta no encontrada cuando el usuario no tiene cuenta")
    void accept_shouldThrowNotFound_whenNoAccount() {
        assertThatThrownBy(() -> servicio.accept(new AcceptPolicyCommand(UID, VIGENTE)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("Responde cuenta no encontrada cuando la cuenta está anonimizada")
    void accept_shouldThrowNotFound_whenAnonymized() {
        repositorio.save(cuenta(AccountStatus.ANONYMIZED, PolicyAcceptance.NOT_ACCEPTED_VERSION));

        assertThatThrownBy(() -> servicio.accept(new AcceptPolicyCommand(UID, VIGENTE)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("Responde cuenta deshabilitada cuando la cuenta está deshabilitada")
    void accept_shouldThrowDisabled_whenDisabled() {
        repositorio.save(cuenta(AccountStatus.DISABLED, PolicyAcceptance.NOT_ACCEPTED_VERSION));

        assertThatThrownBy(() -> servicio.accept(new AcceptPolicyCommand(UID, VIGENTE)))
                .isInstanceOf(AccountDisabledException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Exige la identidad cuando el identificador está ausente o en blanco")
    void accept_shouldThrowIdentityRequired_whenUidIsBlank(String uid) {
        assertThatThrownBy(() -> servicio.accept(new AcceptPolicyCommand(uid, VIGENTE)))
                .isInstanceOf(IdentityRequiredException.class);
    }

    @Test
    @DisplayName("Devuelve la aceptación guardada cuando otra petición igual ganó la carrera")
    void accept_shouldReturnStoredAcceptance_whenConcurrentSaveWon() {
        repositorio.save(cuenta(AccountStatus.ACTIVE, PolicyAcceptance.NOT_ACCEPTED_VERSION));
        repositorio.ganadoraEnLaCarrera = cuenta(AccountStatus.ACTIVE, VIGENTE);

        Account resultado = servicio.accept(new AcceptPolicyCommand(UID, VIGENTE));

        assertThat(resultado.getPolicyAcceptance().version()).isEqualTo(VIGENTE);
        assertThat(resultado.getPolicyAcceptance().acceptedAt()).isEqualTo(PolicyAcceptanceFixture.ACCEPTED_AT);
    }

    @Test
    @DisplayName("Pide reintentar cuando tras el conflicto la cuenta sigue sin la versión vigente")
    void accept_shouldThrowConflict_whenRereadIsStillOld() {
        repositorio.save(cuenta(AccountStatus.ACTIVE, PolicyAcceptance.NOT_ACCEPTED_VERSION));
        repositorio.ganadoraEnLaCarrera = cuenta(AccountStatus.ACTIVE, PolicyAcceptance.NOT_ACCEPTED_VERSION);

        assertThatThrownBy(() -> servicio.accept(new AcceptPolicyCommand(UID, VIGENTE)))
                .isInstanceOf(AccountConflictException.class);
    }

    @Test
    @DisplayName("Responde cuenta no encontrada cuando la cuenta desaparece tras el conflicto")
    void accept_shouldThrowNotFound_whenAccountVanishesAfterConflict() {
        repositorio.save(cuenta(AccountStatus.ACTIVE, PolicyAcceptance.NOT_ACCEPTED_VERSION));
        repositorio.desaparecerEnLaCarrera = true;

        assertThatThrownBy(() -> servicio.accept(new AcceptPolicyCommand(UID, VIGENTE)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("El log nombra la cuenta y no escribe la versión ni la fecha")
    void accept_shouldNotLogVersionOrDate(CapturedOutput salida) {
        Account guardada = repositorio.save(cuenta(AccountStatus.ACTIVE, PolicyAcceptance.NOT_ACCEPTED_VERSION));

        servicio.accept(new AcceptPolicyCommand(UID, VIGENTE));

        assertThat(salida.getAll()).contains(guardada.getId().toString())
                .doesNotContain("2026-11-v1").doesNotContain("2026-11-14");
    }

    @Test
    @DisplayName("El constructor sin reloj usa el instante real")
    void accept_shouldUseSystemClock_whenBuiltWithoutClock() {
        AcceptPolicyService conRelojReal = new AcceptPolicyService(repositorio, new CurrentPolicy(VIGENTE));
        repositorio.save(cuenta(AccountStatus.ACTIVE, PolicyAcceptance.NOT_ACCEPTED_VERSION));
        Instant antes = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        Account resultado = conRelojReal.accept(new AcceptPolicyCommand(UID, VIGENTE));

        assertThat(resultado.getPolicyAcceptance().acceptedAt()).isBetween(antes, Instant.now());
    }

    private Account cuenta(AccountStatus estado, String version) {
        Instant aceptada = PolicyAcceptance.NOT_ACCEPTED_VERSION.equals(version) ? AHORA.minusSeconds(86_400)
                : PolicyAcceptanceFixture.ACCEPTED_AT;
        return Account.rebuild(UUID.nameUUIDFromBytes(UID.getBytes()), UID, "Ana", "Pérez",
                new BirthDate(LocalDate.of(1995, 4, 12)), null, null, new PolicyAcceptance(version, aceptada), estado);
    }

    /**
     * Repositorio en memoria que cuenta los guardados y puede simular que otra petición ganó la carrera: el primer
     * guardado falla con conflicto y la relectura devuelve la cuenta de la ganadora.
     */
    private static class RepositorioDePrueba extends InMemoryAccountRepository {

        private int guardados;
        private Account ganadoraEnLaCarrera;
        private boolean desaparecerEnLaCarrera;
        private boolean oculta;

        @Override
        public Account save(Account account) {
            guardados++;
            if (ganadoraEnLaCarrera != null) {
                super.save(ganadoraEnLaCarrera);
                ganadoraEnLaCarrera = null;
                throw new AccountConflictException();
            }
            if (desaparecerEnLaCarrera) {
                desaparecerEnLaCarrera = false;
                oculta = true;
                throw new AccountConflictException();
            }
            return super.save(account);
        }

        @Override
        public Optional<Account> findByFirebaseUid(String firebaseUid) {
            return oculta ? Optional.empty() : super.findByFirebaseUid(firebaseUid);
        }
    }
}
