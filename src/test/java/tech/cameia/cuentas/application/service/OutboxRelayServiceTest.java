package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tech.cameia.cuentas.domain.event.OutboundEvent;
import tech.cameia.cuentas.infrastructure.messaging.InMemoryEventPublisher;
import tech.cameia.cuentas.infrastructure.persistence.InMemoryOutboxRepository;

/**
 * Pruebas del relevo de la tabla de salida con dobles en memoria: la lectura, la publicación y el registro del resultado.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("OutboxRelayService")
class OutboxRelayServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T15:04:05.123Z");

    private InMemoryOutboxRepository outbox;
    private InMemoryEventPublisher publisher;
    private OutboxRelayService relay;

    @BeforeEach
    void setUp() {
        outbox = new InMemoryOutboxRepository();
        publisher = new InMemoryEventPublisher();
        relay = new OutboxRelayService(outbox, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("marca publicado el evento cuando el broker confirma")
    void relay_shouldMarkPublished_whenBrokerConfirms() {
        UUID id = outbox.seed(event(1, 0));

        boolean result = relay.relay(id);

        assertThat(result).isTrue();
        assertThat(outbox.pending()).isEmpty();
        assertThat(outbox.publishedAt(id)).isEqualTo(NOW);
        assertThat(publisher.published()).hasSize(1);
    }

    @Test
    @DisplayName("deja pendiente y suma un intento cuando el broker no confirma")
    void relay_shouldNotMarkPublished_whenBrokerDoesNotConfirm() {
        UUID id = outbox.seed(event(1, 0));
        publisher.failNext(1);

        boolean result = relay.relay(id);

        assertThat(result).isFalse();
        assertThat(outbox.pending()).hasSize(1);
        assertThat(outbox.pending().get(0).attempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("no hace nada con un evento ya publicado")
    void relay_shouldDoNothing_whenEventIsAlreadyPublished() {
        UUID id = outbox.seed(event(1, 0));
        relay.relay(id);

        boolean result = relay.relay(id);

        assertThat(result).isFalse();
        assertThat(publisher.published()).hasSize(1);
    }

    @Test
    @DisplayName("registra un error una sola vez al fallar el décimo intento")
    void relay_shouldLogErrorOnce_whenTenthAttemptFails(CapturedOutput output) {
        UUID ninth = outbox.seed(event(1, 9));
        UUID tenth = outbox.seed(event(2, 10));
        publisher.failAlways();

        relay.relay(ninth);
        assertThat(output.getOut()).contains("Evento sin publicar tras 10 intentos");
        int afterNinth = output.getOut().split("Evento sin publicar tras 10 intentos", -1).length - 1;

        relay.relay(tenth);
        int afterTenth = output.getOut().split("Evento sin publicar tras 10 intentos", -1).length - 1;

        assertThat(afterNinth).isEqualTo(1);
        assertThat(afterTenth).isEqualTo(1);
    }

    @Test
    @DisplayName("publica todos los pendientes en orden cuando el broker confirma")
    void relayPending_shouldPublishAllInOrder_whenBrokerConfirms() {
        outbox.seed(event(3, 0));
        outbox.seed(event(1, 0));
        outbox.seed(event(2, 0));

        RelaySummary summary = relay.relayPending();

        assertThat(summary).isEqualTo(new RelaySummary(3, 0, 0));
        assertThat(publisher.published()).extracting(OutboundEvent::createdAt)
                .containsExactly(at(1), at(2), at(3));
    }

    @Test
    @DisplayName("se detiene tras una sola vuelta cuando falla el lote entero")
    void relayPending_shouldStop_whenWholeBatchFails() {
        outbox.seed(event(1, 0));
        outbox.seed(event(2, 0));
        outbox.seed(event(3, 0));
        publisher.failAlways();

        RelaySummary summary = relay.relayPending();

        assertThat(summary).isEqualTo(new RelaySummary(0, 3, 3));
        assertThat(outbox.pending()).extracting(OutboundEvent::attempts).containsExactly(1, 1, 1);
    }

    @Test
    @DisplayName("procesa varios lotes cuando hay más pendientes que el tamaño del lote")
    void relayPending_shouldProcessSeveralBatches_whenMoreThanBatchSize() {
        for (int i = 1; i <= 250; i++) {
            outbox.seed(event(i, 0));
        }

        RelaySummary summary = relay.relayPending();

        assertThat(summary).isEqualTo(new RelaySummary(250, 0, 0));
        assertThat(publisher.published()).hasSize(250);
    }

    @Test
    @DisplayName("se detiene en el tope de lotes y deja el resto pendiente")
    void relayPending_shouldStopAtMaxBatches_whenMoreThanLimitArePending() {
        int total = OutboxRelayService.BATCH_SIZE * OutboxRelayService.MAX_BATCHES + 100;
        for (int i = 1; i <= total; i++) {
            outbox.seed(event(i, 0));
        }

        RelaySummary summary = relay.relayPending();

        assertThat(summary).isEqualTo(new RelaySummary(5000, 0, 100));
    }

    @Test
    @DisplayName("devuelve ceros cuando no hay pendientes")
    void relayPending_shouldReturnZeros_whenNothingIsPending() {
        RelaySummary summary = relay.relayPending();

        assertThat(summary).isEqualTo(new RelaySummary(0, 0, 0));
        assertThat(publisher.published()).isEmpty();
    }

    @Test
    @DisplayName("se detiene al terminar el lote cuando falla alguno, sin reintentar el mismo evento en la misma corrida")
    void relayPending_shouldStopAfterBatch_whenAnyEventFails() {
        outbox.seed(event(1, 0));
        outbox.seed(event(2, 0));
        publisher.failNext(1);

        RelaySummary summary = relay.relayPending();

        assertThat(summary.published()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.stillPending()).isEqualTo(1);
    }

    private static Instant at(int second) {
        return Instant.parse("2026-10-09T15:00:00Z").plusSeconds(second);
    }

    private static OutboundEvent event(int second, int attempts) {
        return new OutboundEvent(new UUID(0L, second), "cuenta.creada", 1, "uid-" + second, "{}", at(second),
                "req-" + second, attempts);
    }
}
