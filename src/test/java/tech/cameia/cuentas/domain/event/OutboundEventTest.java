package tech.cameia.cuentas.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Pruebas de la fila pendiente de la tabla de salida. */
class OutboundEventTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String UID = "6f1d2c3b4a5e4f60718293a4b5c6d7e8";
    private static final String PAYLOAD = "{\"usuarioId\":\"6f1d2c3b4a5e4f60718293a4b5c6d7e8\"}";
    private static final Instant CREATED_AT = Instant.parse("2026-10-09T15:04:05.123Z");

    @Test
    @DisplayName("Conserva todos los componentes, incluidos los intentos")
    void constructor_shouldKeepComponents_whenAllArePresent() {
        OutboundEvent event = new OutboundEvent(ID, "cuenta.creada", 1, UID, PAYLOAD, CREATED_AT, "req-1", 2);

        assertThat(event.id()).isEqualTo(ID);
        assertThat(event.type()).isEqualTo("cuenta.creada");
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.aggregateId()).isEqualTo(UID);
        assertThat(event.payloadJson()).isEqualTo(PAYLOAD);
        assertThat(event.createdAt()).isEqualTo(CREATED_AT);
        assertThat(event.correlationId()).isEqualTo("req-1");
        assertThat(event.attempts()).isEqualTo(2);
    }

    static Stream<Arguments> missingComponents() {
        return Stream.of(
                missing("id", () -> new OutboundEvent(null, "t", 1, UID, PAYLOAD, CREATED_AT, "c", 0)),
                missing("type", () -> new OutboundEvent(ID, null, 1, UID, PAYLOAD, CREATED_AT, "c", 0)),
                missing("aggregateId", () -> new OutboundEvent(ID, "t", 1, null, PAYLOAD, CREATED_AT, "c", 0)),
                missing("payloadJson", () -> new OutboundEvent(ID, "t", 1, UID, null, CREATED_AT, "c", 0)),
                missing("createdAt", () -> new OutboundEvent(ID, "t", 1, UID, PAYLOAD, null, "c", 0)),
                missing("correlationId", () -> new OutboundEvent(ID, "t", 1, UID, PAYLOAD, CREATED_AT, null, 0)));
    }

    private static Arguments missing(String name, ThrowingSupplier<OutboundEvent> build) {
        return Arguments.of(name, build);
    }

    @ParameterizedTest(name = "falta {0}")
    @MethodSource("missingComponents")
    @DisplayName("Rechaza un componente obligatorio nulo")
    void constructor_shouldRejectNull_whenRequiredComponentIsMissing(String name,
            ThrowingSupplier<OutboundEvent> build) {
        assertThatThrownBy(build::get).isInstanceOf(NullPointerException.class).hasMessageContaining(name);
    }
}
