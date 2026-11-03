package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.cuentas.domain.event.AccountDeleted;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.infrastructure.persistence.InMemoryAccountRepository;
import tech.cameia.cuentas.infrastructure.persistence.InMemoryOutboxRepository;

/** Pruebas de la lógica del borrado de una cuenta pendiente y de su evento, sin Spring ni base de datos. */
class PendingAccountDeletionServiceTest {

    private static final String UID = "uid-pendiente";
    private static final Instant NOW = Instant.parse("2026-10-10T08:00:00.123Z");

    private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
    private final InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    private final PendingAccountDeletionService service = new PendingAccountDeletionService(accounts, outbox,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("Si borró la fila, guarda un evento de cuenta eliminada con el uid y la hora del reloj")
    void deletePending_shouldRecordEvent_whenRowDeleted() {
        accounts.save(Account.register(UID, "Ana", "Pérez", new BirthDate(LocalDate.of(1995, 4, 12)), null, null));

        boolean deleted = service.deletePending(UID);

        assertThat(deleted).isTrue();
        assertThat(accounts.findByFirebaseUid(UID)).isEmpty();
        assertThat(outbox.appendedDeletions()).singleElement().satisfies(event -> {
            assertThat(event.firebaseUid()).isEqualTo(UID);
            assertThat(event.deletedAt()).isEqualTo(NOW);
            assertThat(event.correlationId().value()).isEqualTo(event.eventId().toString());
        });
        assertThat(AccountDeleted.TYPE).isEqualTo("cuenta.eliminada");
    }

    @Test
    @DisplayName("Si no borró nada, no guarda ningún evento")
    void deletePending_shouldNotRecordEvent_whenNothingDeleted() {
        boolean deleted = service.deletePending("uid-que-no-existe");

        assertThat(deleted).isFalse();
        assertThat(outbox.appendedDeletions()).isEmpty();
    }

    @Test
    @DisplayName("Una cuenta que ya no está pendiente no se borra ni genera evento")
    void deletePending_shouldNotDeleteNorRecord_whenAccountIsActive() {
        accounts.save(Account.rebuild(UUID.randomUUID(), UID, "Ana", "Pérez",
                new BirthDate(LocalDate.of(1995, 4, 12)), null, null, AccountStatus.ACTIVE));

        boolean deleted = service.deletePending(UID);

        assertThat(deleted).isFalse();
        assertThat(accounts.findByFirebaseUid(UID)).isPresent();
        assertThat(outbox.appendedDeletions()).isEmpty();
    }
}
