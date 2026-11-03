package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tech.cameia.cuentas.domain.event.AccountCreated;
import tech.cameia.cuentas.domain.event.OutboundEvent;
import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.UnannouncedAccount;
import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;
import tech.cameia.cuentas.infrastructure.persistence.InMemoryAccountRepository;
import tech.cameia.cuentas.infrastructure.persistence.InMemoryOutboxRepository;

/**
 * Pruebas de la carga inicial de eventos con dobles en memoria: qué cuentas reciben su evento, con qué instante y qué pasa
 * cuando el directorio de usuarios falla o no conoce a la persona.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("AccountCreatedBackfillService")
class AccountCreatedBackfillServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-01T08:00:00Z");
    private static final BirthDate BIRTH_DATE = new BirthDate(LocalDate.of(1995, 4, 12));

    private InMemoryAccountRepository accounts;
    private InMemoryOutboxRepository outbox;
    private InMemoryFirebaseUserDirectory directory;
    private AccountCreatedBackfillService service;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        outbox = new InMemoryOutboxRepository();
        directory = new InMemoryFirebaseUserDirectory();
        service = new AccountCreatedBackfillService(accounts, outbox, directory);
    }

    @Test
    @DisplayName("agrega el evento con el instante de creación de la cuenta y el correo del directorio")
    void enqueueMissing_shouldAppendEventWithAccountCreationInstant_whenEmailExists() {
        String uid = directory.crearSinCuentaLocal("ana@cameia.tech", CREATED_AT);
        accounts.seedUnannounced(new UnannouncedAccount(uid, BIRTH_DATE, CREATED_AT));

        BackfillSummary summary = service.enqueueMissing();

        assertThat(summary).isEqualTo(new BackfillSummary(1, 0, false));
        assertThat(outbox.appended()).hasSize(1);
        AccountCreated event = outbox.appended().get(0);
        assertThat(event.createdAt()).isEqualTo(CREATED_AT);
        assertThat(event.firebaseUid()).isEqualTo(uid);
        assertThat(event.email().value()).isEqualTo("ana@cameia.tech");
        assertThat(event.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(event.correlationId().value()).isEqualTo(event.eventId().toString());
    }

    @Test
    @DisplayName("omite la cuenta cuando el usuario no existe en el directorio y avisa sin el correo")
    void enqueueMissing_shouldSkip_whenUserIsMissingInDirectory(CapturedOutput output) {
        accounts.seedUnannounced(new UnannouncedAccount("uid-huerfano", BIRTH_DATE, CREATED_AT));

        BackfillSummary summary = service.enqueueMissing();

        assertThat(summary).isEqualTo(new BackfillSummary(0, 1, false));
        assertThat(outbox.appended()).isEmpty();
        assertThat(output.getOut()).contains("Cuentas omitidas por no existir su usuario en el directorio")
                .contains("uid-huerfano").contains("omitidas=1");
    }

    @Test
    @DisplayName("escribe un solo aviso con hasta diez ejemplos cuando se omiten muchas cuentas")
    void enqueueMissing_shouldWarnOnceWithTenExamples_whenManyAreSkipped(CapturedOutput output) {
        for (int i = 1; i <= 12; i++) {
            accounts.seedUnannounced(new UnannouncedAccount("uid-huerfano-" + i, BIRTH_DATE, CREATED_AT));
        }

        BackfillSummary summary = service.enqueueMissing();

        assertThat(summary).isEqualTo(new BackfillSummary(0, 12, false));
        String log = output.getOut();
        assertThat(log.split("Cuentas omitidas por no existir su usuario", -1)).hasSize(2);
        assertThat(log).contains("omitidas=12").contains("uid-huerfano-10").doesNotContain("uid-huerfano-11");
    }

    @Test
    @DisplayName("no duplica el evento aunque la carga inicial se ejecute veinte veces")
    void enqueueMissing_shouldNotDuplicate_whenRunTwentyTimes() {
        String uid = directory.crearSinCuentaLocal("ana@cameia.tech", CREATED_AT);
        accounts.seedUnannounced(new UnannouncedAccount(uid, BIRTH_DATE, CREATED_AT));

        BackfillSummary first = service.enqueueMissing();
        for (int repetition = 1; repetition <= 20; repetition++) {
            assertThat(service.enqueueMissing().enqueued()).isZero();
        }

        assertThat(first.enqueued()).isEqualTo(1);
        assertThat(outbox.appended()).hasSize(1);
    }

    @Test
    @DisplayName("se detiene y conserva lo agregado cuando el directorio no responde")
    void enqueueMissing_shouldStop_whenDirectoryIsUnavailable() {
        String first = directory.crearSinCuentaLocal("ana@cameia.tech", CREATED_AT);
        String second = directory.crearSinCuentaLocal("luis@cameia.tech", CREATED_AT.plusSeconds(1));
        accounts.seedUnannounced(new UnannouncedAccount(first, BIRTH_DATE, CREATED_AT));
        accounts.seedUnannounced(new UnannouncedAccount(second, BIRTH_DATE, CREATED_AT.plusSeconds(1)));
        directory.failAfterLookups(1);

        assertThatThrownBy(() -> service.enqueueMissing()).isInstanceOf(DependencyUnavailableException.class);

        assertThat(outbox.appended()).extracting(AccountCreated::firebaseUid).containsExactly(first);
    }

    @Test
    @DisplayName("no duplica eventos cuando se ejecuta dos veces")
    void enqueueMissing_shouldNotDuplicate_whenRunTwice() {
        String uid = directory.crearSinCuentaLocal("ana@cameia.tech", CREATED_AT);
        accounts.seedUnannounced(new UnannouncedAccount(uid, BIRTH_DATE, CREATED_AT));
        service.enqueueMissing();

        BackfillSummary second = service.enqueueMissing();

        assertThat(second).isEqualTo(new BackfillSummary(0, 0, false));
        assertThat(outbox.appended()).hasSize(1);
    }

    @Test
    @DisplayName("registra un evento nuevo cuando se borró la fila del evento ya publicado")
    void enqueueMissing_shouldRepublish_whenPublishedMarkerIsDeleted() {
        String uid = directory.crearSinCuentaLocal("ana@cameia.tech", CREATED_AT);
        accounts.seedUnannounced(new UnannouncedAccount(uid, BIRTH_DATE, CREATED_AT));
        service.enqueueMissing();
        OutboundEvent first = outbox.pending().get(0);
        outbox.markPublished(first.id(), CREATED_AT.plusSeconds(5));
        assertThat(service.enqueueMissing()).isEqualTo(new BackfillSummary(0, 0, false));

        outbox.deleteEventRowOf(uid);
        BackfillSummary summary = service.enqueueMissing();

        assertThat(summary).isEqualTo(new BackfillSummary(1, 0, false));
        List<OutboundEvent> pending = outbox.pending();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).id()).isNotEqualTo(first.id());
        assertThat(pending.get(0).aggregateId()).isEqualTo(uid);
    }

    @Test
    @DisplayName("avisa que quedan cuentas por registrar cuando se llega al límite de la corrida")
    void enqueueMissing_shouldReportMoreRemain_whenLimitReached() {
        for (int i = 1; i <= AccountCreatedBackfillService.MAX_ACCOUNTS; i++) {
            String uid = directory.crearSinCuentaLocal("persona" + i + "@cameia.tech", CREATED_AT);
            accounts.seedUnannounced(new UnannouncedAccount(uid, BIRTH_DATE, CREATED_AT.plusSeconds(i)));
        }

        BackfillSummary summary = service.enqueueMissing();

        assertThat(summary).isEqualTo(new BackfillSummary(AccountCreatedBackfillService.MAX_ACCOUNTS, 0, true));
    }

    @Test
    @DisplayName("avisa que quedan cuentas por registrar cuando las huérfanas llenan el lote y no registra ninguna")
    void enqueueMissing_shouldReportMoreRemain_whenOrphansFillTheBatch() {
        for (int i = 1; i <= AccountCreatedBackfillService.MAX_ACCOUNTS; i++) {
            accounts.seedUnannounced(new UnannouncedAccount("huerfana" + i, BIRTH_DATE, CREATED_AT.plusSeconds(i)));
        }

        BackfillSummary summary = service.enqueueMissing();

        assertThat(summary).isEqualTo(new BackfillSummary(0, AccountCreatedBackfillService.MAX_ACCOUNTS, true));
    }

    @Test
    @DisplayName("devuelve ceros cuando ninguna cuenta carece de evento")
    void enqueueMissing_shouldReturnZeros_whenNothingIsMissing() {
        assertThat(service.enqueueMissing()).isEqualTo(new BackfillSummary(0, 0, false));
    }
}
