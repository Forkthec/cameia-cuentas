package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tech.cameia.cuentas.application.command.ActivateAccountCommand;
import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.EmailNotVerifiedException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.port.AccountRepository;
import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;

/**
 * Prueba del caso de uso de activación descrito en
 * {@code specs/CM-14-RegistroUsuario/spec.md} (REQ-CU-13 y REQ-CU-14).
 */
class ActivateAccountServiceTest {

    private static final String UID = "uid-firebase";

    private final InMemoryFirebaseUserDirectory directorio = new InMemoryFirebaseUserDirectory();
    private final RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
    private final ActivateAccountService servicio = new ActivateAccountService(repositorio, directorio);

    @Test
    void activaLaCuentaCuandoElGatewayConfirmaElCorreoVerificado() {
        repositorio.con(cuentaPendiente());

        Account activada = servicio.activate(new ActivateAccountCommand(UID, true));

        assertThat(activada.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void consultaAFirebaseCuandoElGatewayNoPropagaElEncabezado() {
        repositorio.con(cuentaPendiente());
        directorio.marcarCorreoVerificado(UID);

        Account activada = servicio.activate(new ActivateAccountCommand(UID, null));

        assertThat(activada.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void rechazaCuandoElCorreoSigueSinVerificarse() {
        repositorio.con(cuentaPendiente());

        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand(UID, false)))
                .isInstanceOf(EmailNotVerifiedException.class);
        assertThat(repositorio.buscar(UID).getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    void noSeFiaDeUnEncabezadoEnFalsoSiFirebaseDiceQueSiEstaVerificado() {
        // El encabezado solo se cree cuando afirma que sí. Un false, o su ausencia, se
        // contrasta con Firebase: puede ser un Gateway que todavía no lo propaga.
        repositorio.con(cuentaPendiente());
        directorio.marcarCorreoVerificado(UID);

        Account activada = servicio.activate(new ActivateAccountCommand(UID, false));

        assertThat(activada.getStatus()).isEqualTo(AccountStatus.ACTIVE);
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
    void fallaCuandoElUsuarioNoTieneCuentaLocal() {
        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand("uid-sin-cuenta", true)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void unaCuentaBloqueadaNoSeActivaAlVerificarElCorreo() {
        repositorio.con(Account.rebuild(UUID.randomUUID(), UID, "Ana", "Pérez",
                new BirthDate(LocalDate.of(1995, 4, 12)), null, null, AccountStatus.DISABLED));

        assertThatThrownBy(() -> servicio.activate(new ActivateAccountCommand(UID, true)))
                .isInstanceOf(IllegalStateException.class);
    }

    private Account cuentaPendiente() {
        return Account.register(UID, "Ana", "Pérez", new BirthDate(LocalDate.of(1995, 4, 12)), null, null);
    }

    /** Repositorio en memoria que cuenta cuántas veces se guardó. */
    private static class RepositorioEnMemoria implements AccountRepository {

        private final Map<String, Account> cuentas = new HashMap<>();
        private int guardados;

        void con(Account cuenta) {
            cuentas.put(cuenta.getFirebaseUid(), cuenta);
        }

        Account buscar(String firebaseUid) {
            return cuentas.get(firebaseUid);
        }

        @Override
        public Account save(Account account) {
            guardados++;
            cuentas.put(account.getFirebaseUid(), account);
            return account;
        }

        @Override
        public Optional<Account> findByFirebaseUid(String firebaseUid) {
            return Optional.ofNullable(cuentas.get(firebaseUid));
        }
    }
}
