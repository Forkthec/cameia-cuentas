package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.PurgeSummary;
import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;
import tech.cameia.cuentas.infrastructure.persistence.InMemoryAccountRepository;
import tech.cameia.cuentas.infrastructure.persistence.InMemoryOutboxRepository;

/** Pruebas de la purga de cuentas sin verificar, sin Spring ni base de datos. */
@ExtendWith(OutputCaptureExtension.class)
class PurgeUnverifiedAccountsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T08:00:00Z");
    private static final Instant OLD = NOW.minus(169, ChronoUnit.HOURS);

    private final List<String> order = new ArrayList<>();
    private final InMemoryFirebaseUserDirectory directory = new InMemoryFirebaseUserDirectory();
    private final InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository() {
        @Override
        public boolean deletePendingByFirebaseUid(String firebaseUid) {
            order.add("fila:credencialExistia=" + directory.existe(firebaseUid));
            return super.deletePendingByFirebaseUid(firebaseUid);
        }
    };

    private PurgeUnverifiedAccountsService service(int max) {
        PendingAccountDeletionService deletion = new PendingAccountDeletionService(accounts, outbox,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new PurgeUnverifiedAccountsService(accounts, directory, deletion, max, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** Crea la credencial y una cuenta pendiente con la antigüedad indicada; devuelve el uid. */
    private String pendingAccount(String email, Instant createdAt) {
        String uid = directory.crearSinCuentaLocal(email, createdAt);
        accounts.save(Account.register(uid, "Ana", "Pérez", new BirthDate(LocalDate.of(1995, 4, 12)), null, null));
        accounts.seedCreatedAt(uid, createdAt);
        return uid;
    }

    @Test
    @DisplayName("Pide las candidatas de más de 168 horas y una más que el tope")
    void purge_shouldAskForCandidatesOlderThan168Hours_whenRun() {
        service(5).purge();

        assertThat(accounts.requestedLimit()).isEqualTo(Instant.parse("2026-10-03T08:00:00Z"));
        assertThat(accounts.requestedMax()).isEqualTo(6);
    }

    @Test
    @DisplayName("Borra primero la credencial, después la fila, y guarda el evento")
    void purge_shouldDeleteCredentialBeforeRowAndRecordEvent_whenNotVerified() {
        String uid = pendingAccount("ana@correo.co", OLD);

        PurgeSummary summary = service(10).purge();

        assertThat(order).containsExactly("fila:credencialExistia=false");
        assertThat(directory.existe(uid)).isFalse();
        assertThat(accounts.findByFirebaseUid(uid)).isEmpty();
        assertThat(outbox.appendedDeletions()).singleElement().satisfies(event -> {
            assertThat(event.firebaseUid()).isEqualTo(uid);
            assertThat(event.deletedAt()).isEqualTo(NOW);
        });
        assertThat(summary).isEqualTo(new PurgeSummary(1, 1, 0, 0, 0, false));
    }

    @Test
    @DisplayName("Si el correo ya está verificado, activa la cuenta en lugar de borrarla")
    void purge_shouldActivateInsteadOfDeleting_whenAlreadyVerified() {
        String uid = pendingAccount("ana@correo.co", OLD);
        directory.marcarCorreoVerificado(uid);

        PurgeSummary summary = service(10).purge();

        assertThat(accounts.findByFirebaseUid(uid)).get().extracting(Account::getStatus).isEqualTo(AccountStatus.ACTIVE);
        assertThat(directory.existe(uid)).isTrue();
        assertThat(directory.borrados()).isZero();
        assertThat(outbox.appendedDeletions()).isEmpty();
        assertThat(summary).isEqualTo(new PurgeSummary(1, 0, 1, 0, 0, false));
    }

    @Test
    @DisplayName("Si la credencial ya no existe, borra solo la fila y guarda el evento")
    void purge_shouldDeleteOnlyRow_whenCredentialIsGone() {
        String uid = pendingAccount("ana@correo.co", OLD);
        directory.deleteUser(uid);
        int deletionsBefore = directory.borrados();

        PurgeSummary summary = service(10).purge();

        assertThat(directory.borrados()).isEqualTo(deletionsBefore);
        assertThat(accounts.findByFirebaseUid(uid)).isEmpty();
        assertThat(outbox.appendedDeletions()).hasSize(1);
        assertThat(summary.deleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un rechazo en una cuenta se cuenta como fallida y las demás siguen")
    void purge_shouldContinue_whenOneAccountIsRejected() {
        String first = pendingAccount("uno@correo.co", OLD.minusSeconds(30));
        String second = pendingAccount("dos@correo.co", OLD.minusSeconds(20));
        String third = pendingAccount("tres@correo.co", OLD.minusSeconds(10));
        directory.rejectLookupOf(second);

        PurgeSummary summary = service(10).purge();

        assertThat(summary).isEqualTo(new PurgeSummary(3, 2, 0, 1, 0, false));
        assertThat(accounts.findByFirebaseUid(first)).isEmpty();
        assertThat(accounts.findByFirebaseUid(second)).isPresent();
        assertThat(accounts.findByFirebaseUid(third)).isEmpty();
    }

    @Test
    @DisplayName("Si fallan todas las cuentas revisadas, registra un ERROR que apunta a los permisos de Firebase")
    void purge_shouldLogError_whenEveryReviewedAccountFails(CapturedOutput output) {
        String uid = pendingAccount("ana@correo.co", OLD);
        directory.rejectLookupOf(uid);

        PurgeSummary summary = service(10).purge();

        assertThat(summary).isEqualTo(new PurgeSummary(1, 0, 0, 1, 0, false));
        assertThat(output.getAll()).contains("ERROR").contains("Todas las cuentas revisadas fallaron");
    }

    @Test
    @DisplayName("Si solo falla una de varias cuentas, no registra el ERROR de fallo total")
    void purge_shouldNotLogError_whenOnlySomeAccountsFail(CapturedOutput output) {
        pendingAccount("uno@correo.co", OLD.minusSeconds(30));
        String second = pendingAccount("dos@correo.co", OLD.minusSeconds(20));
        directory.rejectLookupOf(second);

        service(10).purge();

        assertThat(output.getAll()).doesNotContain("Todas las cuentas revisadas fallaron");
    }

    @Test
    @DisplayName("Si la cuenta se activó entre la consulta y el borrado, registra el error y no publica evento")
    void purge_shouldLogErrorAndSkipEvent_whenRowWasActivatedMeanwhile(CapturedOutput output) {
        pendingAccount("ana@correo.co", OLD);
        accounts.failNextDeleteAsNoLongerPending();

        PurgeSummary summary = service(10).purge();

        assertThat(summary).isEqualTo(new PurgeSummary(1, 0, 0, 1, 0, false));
        assertThat(outbox.appendedDeletions()).isEmpty();
        assertThat(output.getAll()).contains("Cuenta activa sin credencial tras la purga");
    }

    @Test
    @DisplayName("Sin candidatas devuelve ceros y solo consulta al directorio el listado de huérfanas")
    void purge_shouldReturnZeros_whenNoCandidates() {
        PurgeSummary summary = service(10).purge();

        assertThat(summary).isEqualTo(new PurgeSummary(0, 0, 0, 0, 0, false));
        // La única consulta es el listado de credenciales huérfanas: ninguna cuenta se consulta una por una
        assertThat(directory.consultas()).isEqualTo(1);
        assertThat(directory.borrados()).isZero();
    }

    @Test
    @DisplayName("Procesa como máximo el tope e indica si quedan candidatas")
    void purge_shouldRespectLimit_whenMoreCandidatesThanMax() {
        for (int i = 0; i < 4; i++) {
            pendingAccount("c" + i + "@correo.co", OLD.minusSeconds(100 - i));
        }

        PurgeSummary limited = service(3).purge();

        assertThat(limited.reviewed()).isEqualTo(3);
        assertThat(limited.candidatesLeft()).isTrue();
        assertThat(accounts.saved()).hasSize(1);
    }

    @Test
    @DisplayName("Con exactamente el tope de candidatas no queda ninguna pendiente")
    void purge_shouldNotReportLeftovers_whenCandidatesEqualMax() {
        for (int i = 0; i < 3; i++) {
            pendingAccount("c" + i + "@correo.co", OLD.minusSeconds(100 - i));
        }

        PurgeSummary summary = service(3).purge();

        assertThat(summary.reviewed()).isEqualTo(3);
        assertThat(summary.candidatesLeft()).isFalse();
    }

    @Test
    @DisplayName("El log de un fallo no lleva el nombre ni el correo de la persona")
    void purge_shouldNotLogPersonalData_whenFailing(CapturedOutput output) {
        InMemoryAccountRepository failing = new InMemoryAccountRepository() {
            @Override
            public boolean deletePendingByFirebaseUid(String firebaseUid) {
                throw new IllegalStateException("fallo con ana@correo.co");
            }
        };
        String uid = directory.crearSinCuentaLocal("ana@correo.co", OLD);
        failing.save(Account.register(uid, "Ana", "Pérez", new BirthDate(LocalDate.of(1995, 4, 12)), null, null));
        failing.seedCreatedAt(uid, OLD);
        PurgeUnverifiedAccountsService service = new PurgeUnverifiedAccountsService(failing, directory,
                new PendingAccountDeletionService(failing, outbox, Clock.fixed(NOW, ZoneOffset.UTC)), 10,
                Clock.fixed(NOW, ZoneOffset.UTC));

        PurgeSummary summary = service.purge();

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(output.getAll()).contains("IllegalStateException").doesNotContain("@").doesNotContain("Ana");
    }

    @Test
    @DisplayName("Si el directorio no responde, se detiene, relanza y deja intacta la cuenta que falta")
    void purge_shouldStopAndRethrow_whenDirectoryIsUnavailable() {
        String first = pendingAccount("uno@correo.co", OLD.minusSeconds(30));
        String second = pendingAccount("dos@correo.co", OLD.minusSeconds(20));
        String third = pendingAccount("tres@correo.co", OLD.minusSeconds(10));
        directory.quedarIndisponibleAlConsultarA(second);

        assertThatThrownBy(() -> service(10).purge()).isInstanceOf(DependencyUnavailableException.class);

        assertThat(accounts.findByFirebaseUid(first)).isEmpty();
        assertThat(accounts.findByFirebaseUid(second)).isPresent();
        assertThat(accounts.findByFirebaseUid(third)).isPresent();
        assertThat(directory.existe(third)).isTrue();
    }

    @Test
    @DisplayName("Una credencial sin cuenta, sin verificar y de más de 168 horas se borra sin evento")
    void purge_shouldDeleteOrphanCredential_whenUnverifiedOldPasswordUserHasNoAccount() {
        String uid = directory.crearSinCuentaLocal("huerfana@correo.co", OLD);

        PurgeSummary summary = service(10).purge();

        assertThat(directory.existe(uid)).isFalse();
        assertThat(summary).isEqualTo(new PurgeSummary(0, 0, 0, 0, 1, false));
        assertThat(outbox.appendedDeletions()).isEmpty();
    }

    @Test
    @DisplayName("Una credencial huérfana con el correo verificado se conserva")
    void purge_shouldKeepOrphanCredential_whenEmailIsVerified() {
        String uid = directory.crearSinCuentaLocal("huerfana@correo.co", OLD);
        directory.marcarCorreoVerificado(uid);

        PurgeSummary summary = service(10).purge();

        assertThat(directory.existe(uid)).isTrue();
        assertThat(summary.orphanCredentialsDeleted()).isZero();
    }

    @Test
    @DisplayName("Una credencial huérfana de menos de 168 horas se conserva")
    void purge_shouldKeepOrphanCredential_whenYoungerThan168Hours() {
        String uid = directory.crearSinCuentaLocal("huerfana@correo.co", NOW.minus(10, ChronoUnit.HOURS));

        service(10).purge();

        assertThat(directory.existe(uid)).isTrue();
    }

    @Test
    @DisplayName("Una credencial con cuenta local no es huérfana y se conserva")
    void purge_shouldKeepCredential_whenAccountExists() {
        String uid = directory.crearSinCuentaLocal("con-cuenta@correo.co", OLD);
        accounts.save(Account.rebuild(java.util.UUID.randomUUID(), uid, "Ana", "Pérez",
                new BirthDate(LocalDate.of(1995, 4, 12)), null, null, AccountStatus.DISABLED));

        PurgeSummary summary = service(10).purge();

        assertThat(directory.existe(uid)).isTrue();
        assertThat(summary.orphanCredentialsDeleted()).isZero();
    }

    @Test
    @DisplayName("Una credencial de otro proveedor sin cuenta local no se toca")
    void purge_shouldKeepOrphanCredential_whenProviderIsNotPassword() {
        String uid = directory.crearSinCuentaLocalConProveedor("google@correo.co", OLD, "google.com");

        PurgeSummary summary = service(10).purge();

        assertThat(directory.existe(uid)).isTrue();
        assertThat(summary.orphanCredentialsDeleted()).isZero();
    }

    @Test
    @DisplayName("Si el directorio no responde al listar huérfanas, relanza la indisponibilidad")
    void purge_shouldRethrow_whenDirectoryIsUnavailableListingOrphans() {
        directory.fallarAlConsultar();

        assertThatThrownBy(() -> service(10).purge()).isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    @DisplayName("El resumen se registra con el texto del contrato")
    void purge_shouldLogSummary_whenFinished(CapturedOutput output) {
        pendingAccount("ana@correo.co", OLD);

        service(10).purge();

        assertThat(output.getAll()).contains("Purga de cuentas sin verificar: revisadas=1, eliminadas=1, activadas=0, "
                + "fallidas=0, credencialesHuerfanas=0, quedanCandidatas=false");
    }
}
