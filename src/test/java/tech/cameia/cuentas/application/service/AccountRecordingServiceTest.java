package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.cuentas.domain.event.AccountCreated;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.infrastructure.persistence.InMemoryAccountRepository;
import tech.cameia.cuentas.infrastructure.persistence.InMemoryOutboxRepository;

/** Pruebas del guardado conjunto de la cuenta y su evento {@code cuenta.creada}. */
class AccountRecordingServiceTest {

    private static final String UID = "6f1d2c3b4a5e4f60718293a4b5c6d7e8";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T15:04:05.123Z"), ZoneOffset.UTC);
    private static final EmailAddress EMAIL = new EmailAddress("ana.perez@ejemplo.test");

    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    private final AccountRecordingService service = new AccountRecordingService(accounts, outbox, CLOCK);

    private static Account account(String uid) {
        return Account.register(uid, "Ana", "Pérez", new BirthDate(LocalDate.of(2008, 3, 15)), null, Pronoun.SHE);
    }

    @Test
    @DisplayName("Guarda la cuenta y agrega su evento con los datos de la cuenta")
    void recordNewAccount_shouldSaveAccountAndAppendEvent_whenAccountIsNew() {
        RecordedAccount recorded = service.recordNewAccount(account(UID), EMAIL, "req-1");

        assertThat(accounts.saved()).hasSize(1);
        assertThat(recorded.account().getFirebaseUid()).isEqualTo(UID);
        assertThat(outbox.appended()).hasSize(1);
        AccountCreated event = outbox.appended().get(0);
        assertThat(event.firebaseUid()).isEqualTo(UID);
        assertThat(event.email()).isEqualTo(EMAIL);
        assertThat(event.birthDate().value()).isEqualTo(LocalDate.of(2008, 3, 15));
        assertThat(event.createdAt()).isEqualTo(Instant.parse("2026-10-09T15:04:05.123Z"));
        assertThat(event.correlationId().value()).isEqualTo("req-1");
        assertThat(event.eventId()).isEqualTo(recorded.eventId());
    }

    @Test
    @DisplayName("Usa el identificador del evento como correlación cuando falta el de la petición")
    void recordNewAccount_shouldUseEventIdAsCorrelation_whenRequestIdIsMissing() {
        RecordedAccount recorded = service.recordNewAccount(account(UID), EMAIL, null);

        assertThat(outbox.appended().get(0).correlationId().value()).isEqualTo(recorded.eventId().toString());
    }

    @Test
    @DisplayName("Falla cuando la cuenta ya tiene su evento")
    void recordNewAccount_shouldThrow_whenEventAlreadyExists() {
        service.recordNewAccount(account(UID), EMAIL, "req-1");

        assertThatThrownBy(() -> service.recordNewAccount(account(UID), EMAIL, "req-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La cuenta ya tiene su evento de cuenta creada");
        assertThat(outbox.appended()).hasSize(1);
    }
}
