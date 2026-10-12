package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tech.cameia.cuentas.application.command.ActivateAccountCommand;
import tech.cameia.cuentas.domain.exception.AccountConflictException;
import tech.cameia.cuentas.domain.exception.AccountDisabledException;
import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.EmailNotVerifiedException;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.UnannouncedAccount;
import tech.cameia.cuentas.domain.port.AccountRepository;

/**
 * Prueba del caso de uso de activación descrito en
 * {@code specs/CM-14-RegistroUsuario/spec.md} (REQ-CU-13 y REQ-CU-14).
 */
@ExtendWith(OutputCaptureExtension.class)
class ActivateAccountServiceTest {

    private static final String UID = "uid-firebase";

    private final RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
    private final ActivateAccountService servicio = new ActivateAccountService(repositorio);

    @Test
    void activaLaCuentaCuandoElGatewayConfirmaElCorreoVerificado() {
        repositorio.con(cuentaPendiente());

        Account activada = servicio.activate(new ActivateAccountCommand(UID, true));

        assertThat(activada.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Sin la afirmación del Gateway se rechaza sin consultar a Firebase")
    void activate_shouldRejectWithoutCallingFirebase_whenHeaderIsNotTrue() {
        // El constructor ya no recibe el directorio de Firebase: el servicio no puede consultarlo
        repositorio.con(cuentaPendiente());

        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand(UID, false)))
                .isInstanceOf(EmailNotVerifiedException.class);
        assertThat(repositorio.buscar(UID).getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    void rechazaCuandoElCorreoSigueSinVerificarse() {
        repositorio.con(cuentaPendiente());

        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand(UID, false)))
                .isInstanceOf(EmailNotVerifiedException.class);
        assertThat(repositorio.buscar(UID).getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    void activarDosVecesNoFallaNiVuelveAGuardar() {
        repositorio.con(cuentaPendiente());
        servicio.activate(new ActivateAccountCommand(UID, true));
        int guardadosTrasLaPrimera = repositorio.guardados;

        Account segunda = servicio.activate(new ActivateAccountCommand(UID, true));

        assertThat(segunda.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(repositorio.guardados).isEqualTo(guardadosTrasLaPrimera);
    }

    @Test
    @DisplayName("Una cuenta sin fecha de nacimiento se activa sin error")
    void activate_shouldActivate_whenBirthDateIsUnknown() {
        repositorio.con(Account.rebuild(UUID.randomUUID(), UID, "Ana", "Pérez", null, null, null,
                AccountStatus.PENDING_VERIFICATION));

        Account activada = servicio.activate(new ActivateAccountCommand(UID, true));

        assertThat(activada.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(activada.getBirthDate()).isEmpty();
    }

    @Test
    void fallaCuandoElUsuarioNoTieneCuentaLocal() {
        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand("uid-sin-cuenta", true)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void unaCuentaBloqueadaNoSeActivaAlVerificarElCorreo() {
        repositorio.con(cuentaEn(AccountStatus.DISABLED));

        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand(UID, true)))
                .isInstanceOf(AccountDisabledException.class);
    }

    @Test
    @DisplayName("Una cuenta anonimizada responde igual que una cuenta inexistente")
    void activate_shouldAnswerLikeMissingAccount_whenAnonymized() {
        repositorio.con(cuentaEn(AccountStatus.ANONYMIZED));
        String mensajeSinCuenta = catchThrowable(
                () -> servicio.activate(new ActivateAccountCommand("uid-sin-cuenta", true))).getMessage();

        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand(UID, true)))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage(mensajeSinCuenta);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("Una identidad en blanco se rechaza sin leer la base")
    void activate_shouldRejectBlankIdentityWithoutReadingDatabase_whenBlank(String uid) {
        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand(uid, true)))
                .isInstanceOf(IdentityRequiredException.class);
        assertThat(repositorio.lecturas).isZero();
    }

    @Test
    @DisplayName("Una identidad nula o de 129 caracteres se rechaza sin leer la base")
    void activate_shouldRejectIdentityWithoutReadingDatabase_whenNullOrTooLong() {
        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand(null, true)))
                .isInstanceOf(IdentityRequiredException.class);
        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand("a".repeat(129), true)))
                .isInstanceOf(IdentityRequiredException.class);
        assertThat(repositorio.lecturas).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {127, 128})
    @DisplayName("Una identidad de 127 o 128 caracteres pasa la validación")
    void activate_shouldPassIdentityValidation_whenAtOrBelowTheLimit(int largo) {
        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand("a".repeat(largo), true)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("Una identidad de 128 caracteres Unicode pasa la validación")
    void activate_shouldPassIdentityValidation_whenUnicodeAtTheLimit() {
        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand("ñ".repeat(128), true)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("Si otra petición activó la cuenta primero, se devuelve la cuenta activa")
    void activate_shouldReturnActiveAccount_whenLosingTheRaceToAnotherActivation() {
        repositorio.con(cuentaPendiente());
        repositorio.failFirstSaveWithConflict(cuentaEn(AccountStatus.ACTIVE));

        Account resultado = servicio.activate(new ActivateAccountCommand(UID, true));

        assertThat(resultado.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Si tras el conflicto la cuenta sigue pendiente, se pide reintentar")
    void activate_shouldThrowConflict_whenRereadIsStillPending() {
        repositorio.con(cuentaPendiente());
        repositorio.failFirstSaveWithConflict(cuentaPendiente());

        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand(UID, true)))
                .isInstanceOf(AccountConflictException.class);
    }

    @Test
    @DisplayName("Un rechazo no escribe nombre ni correo en el log")
    void activate_shouldNotLogPersonalData_whenRejected(CapturedOutput salida) {
        repositorio.con(cuentaPendiente());

        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand(UID, false)))
                .isInstanceOf(EmailNotVerifiedException.class);

        assertThat(salida.getAll()).doesNotContain("Ana").doesNotContain("@");
    }

    private Account cuentaEn(AccountStatus estado) {
        return Account.rebuild(UUID.randomUUID(), UID, "Ana", "Pérez", new BirthDate(LocalDate.of(1995, 4, 12)),
                null, null, estado);
    }

    private Account cuentaPendiente() {
        return Account.register(UID, "Ana", "Pérez", new BirthDate(LocalDate.of(1995, 4, 12)), null, null);
    }

    /** Repositorio en memoria que cuenta cuántas veces se guardó. */
    private static class RepositorioEnMemoria implements AccountRepository {

        @Override
        public boolean deletePendingByFirebaseUid(String firebaseUid) {
            return false;
        }

        @Override
        public List<UnannouncedAccount> findUnannounced(int limit) {
            return List.of();
        }

        private final Map<String, Account> cuentas = new HashMap<>();
        private int guardados;
        private int lecturas;
        private Account relecturaTrasConflicto;

        void con(Account cuenta) {
            cuentas.put(cuenta.getFirebaseUid(), cuenta);
        }

        /** El primer guardado lanza el conflicto de versión y deja {@code relectura} como la cuenta almacenada. */
        void failFirstSaveWithConflict(Account relectura) {
            relecturaTrasConflicto = relectura;
        }

        Account buscar(String firebaseUid) {
            return cuentas.get(firebaseUid);
        }

        @Override
        public Account save(Account account) {
            if (relecturaTrasConflicto != null) {
                cuentas.put(account.getFirebaseUid(), relecturaTrasConflicto);
                relecturaTrasConflicto = null;
                throw new AccountConflictException();
            }
            guardados++;
            cuentas.put(account.getFirebaseUid(), account);
            return account;
        }

        @Override
        public Optional<Account> findByFirebaseUid(String firebaseUid) {
            lecturas++;
            return Optional.ofNullable(cuentas.get(firebaseUid));
        }
    }
}
