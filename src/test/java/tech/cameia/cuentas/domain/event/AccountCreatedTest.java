package tech.cameia.cuentas.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.EmailAddress;

/** Pruebas del evento de dominio {@code cuenta.creada}. */
class AccountCreatedTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String UID = "6f1d2c3b4a5e4f60718293a4b5c6d7e8";
    private static final EmailAddress EMAIL = new EmailAddress("ana.perez@ejemplo.test");
    private static final BirthDate BIRTH_DATE = new BirthDate(LocalDate.of(2008, 3, 15));
    private static final Instant CREATED_AT = Instant.parse("2026-10-09T15:04:05.123Z");
    private static final CorrelationId CORRELATION = new CorrelationId("req-1");

    @Test
    @DisplayName("Trunca el instante a milisegundos")
    void constructor_shouldTruncateToMillis_whenInstantHasNanos() {
        AccountCreated event = new AccountCreated(EVENT_ID, UID, EMAIL, BIRTH_DATE,
                Instant.parse("2026-10-09T15:04:05.123456789Z"), CORRELATION);

        assertThat(event.createdAt()).isEqualTo(Instant.parse("2026-10-09T15:04:05.123Z"));
    }

    @Test
    @DisplayName("Conserva los componentes y fija el tipo y la versión del contrato")
    void constructor_shouldKeepComponents_whenAllArePresent() {
        AccountCreated event = new AccountCreated(EVENT_ID, UID, EMAIL, BIRTH_DATE, CREATED_AT, CORRELATION);

        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.firebaseUid()).isEqualTo(UID);
        assertThat(event.email()).isEqualTo(EMAIL);
        assertThat(event.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(event.correlationId()).isEqualTo(CORRELATION);
        assertThat(AccountCreated.TYPE).isEqualTo("cuenta.creada");
        assertThat(AccountCreated.VERSION).isEqualTo(1);
    }

    static Stream<Arguments> missingComponents() {
        return Stream.of(
                missing("eventId", () -> new AccountCreated(null, UID, EMAIL, BIRTH_DATE, CREATED_AT, CORRELATION)),
                missing("firebaseUid", () -> new AccountCreated(EVENT_ID, null, EMAIL, BIRTH_DATE, CREATED_AT, CORRELATION)),
                missing("email", () -> new AccountCreated(EVENT_ID, UID, null, BIRTH_DATE, CREATED_AT, CORRELATION)),
                missing("birthDate", () -> new AccountCreated(EVENT_ID, UID, EMAIL, null, CREATED_AT, CORRELATION)),
                missing("createdAt", () -> new AccountCreated(EVENT_ID, UID, EMAIL, BIRTH_DATE, null, CORRELATION)),
                missing("correlationId", () -> new AccountCreated(EVENT_ID, UID, EMAIL, BIRTH_DATE, CREATED_AT, null)));
    }

    private static Arguments missing(String name, ThrowingSupplier<AccountCreated> build) {
        return Arguments.of(name, build);
    }

    @ParameterizedTest(name = "falta {0}")
    @MethodSource("missingComponents")
    @DisplayName("Rechaza un componente nulo")
    void constructor_shouldRejectNull_whenAnyComponentIsMissing(String name,
            ThrowingSupplier<AccountCreated> build) {
        assertThatThrownBy(build::get).isInstanceOf(NullPointerException.class).hasMessageContaining(name);
    }
}
