package tech.cameia.cuentas.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas del evento de dominio de cuenta eliminada. */
class AccountDeletedTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Instant INSTANT = Instant.parse("2026-10-10T08:00:00.123456Z");
    private static final CorrelationId CORRELATION = new CorrelationId("req-1");

    @Test
    @DisplayName("El tipo y la versión son los del contrato")
    void constants_shouldMatchContract() {
        assertThat(AccountDeleted.TYPE).isEqualTo("cuenta.eliminada");
        assertThat(AccountDeleted.VERSION).isEqualTo(1);
    }

    @Test
    @DisplayName("El instante se trunca a milisegundos")
    void constructor_shouldTruncateToMilliseconds_whenInstantHasMorePrecision() {
        AccountDeleted event = new AccountDeleted(ID, "uid-1", INSTANT, CORRELATION);

        assertThat(event.deletedAt()).isEqualTo(Instant.parse("2026-10-10T08:00:00.123Z"));
    }

    @Test
    @DisplayName("Cada componente nulo se rechaza")
    void constructor_shouldRejectNulls_whenAnyComponentIsMissing() {
        assertThatThrownBy(() -> new AccountDeleted(null, "uid-1", INSTANT, CORRELATION))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AccountDeleted(ID, null, INSTANT, CORRELATION))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AccountDeleted(ID, "uid-1", null, CORRELATION))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AccountDeleted(ID, "uid-1", INSTANT, null))
                .isInstanceOf(NullPointerException.class);
    }
}
