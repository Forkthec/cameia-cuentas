package tech.cameia.cuentas.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tech.cameia.cuentas.domain.event.OutboundEvent;
import tech.cameia.cuentas.domain.port.EventPublisher;
import tech.cameia.cuentas.domain.port.OutboxRepository;

/**
 * Publica los eventos de la tabla de salida y registra el resultado de cada intento.
 *
 * <p>No mantiene ninguna transacción mientras habla con el broker: lee el evento pendiente, lo publica y solo entonces lo
 * marca como publicado. Si dos relevos compiten, el evento puede entregarse dos veces; el contrato es de al menos una vez y
 * los consumidores son idempotentes, así que no se retiene ningún bloqueo de fila durante las llamadas de red.</p>
 */
@Service
public class OutboxRelayService {

    static final int BATCH_SIZE = 100;
    static final int MAX_BATCHES = 50;
    static final int ALERT_ATTEMPTS = 10;
    /** Tiempo máximo de una corrida: debe terminar y registrar su resumen antes del plazo de 10 minutos del Cloud Run Job. */
    static final Duration RUN_BUDGET = Duration.ofMinutes(4);

    private static final Logger logger = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxRepository outbox;
    private final EventPublisher publisher;
    private final Clock clock;

    /**
     * Crea el servicio con el reloj UTC del sistema.
     *
     * @param outbox tabla de salida de eventos
     * @param publisher publicador hacia el broker
     */
    @Autowired
    public OutboxRelayService(OutboxRepository outbox, EventPublisher publisher) {
        this(outbox, publisher, Clock.systemUTC());
    }

    /**
     * Crea el servicio con un reloj propio, para fijar la hora en las pruebas.
     *
     * @param outbox tabla de salida de eventos
     * @param publisher publicador hacia el broker
     * @param clock reloj que fija el instante de publicación
     */
    public OutboxRelayService(OutboxRepository outbox, EventPublisher publisher, Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Publica un evento concreto si sigue pendiente.
     *
     * @param eventId identificador del evento
     * @return {@code true} si esta llamada lo publicó; {@code false} si no existe, ya estaba publicado o el broker no confirmó
     */
    public boolean relay(UUID eventId) {
        return outbox.findPendingById(eventId).map(this::publishOne).orElse(false);
    }

    /**
     * Publica los pendientes, el más antiguo primero, hasta que no quede ninguno, un lote tenga algún fallo o se llegue a
     * {@link #MAX_BATCHES}.
     *
     * <p>Un lote con fallos corta la corrida: los eventos fallidos siguen siendo los más antiguos y se leerían de nuevo en
     * el lote siguiente, con lo que un mismo evento sumaría muchos intentos en una sola corrida y dispararía la alerta sin
     * motivo. La tarea de relevo vuelve a intentarlo en su próxima ejecución.</p>
     *
     * <p>Corta la corrida al pasar {@link #RUN_BUDGET} para terminar y registrar el resumen antes del plazo del Job; lo que
     * quede sin publicar se reintenta en la ejecución siguiente.</p>
     *
     * @return cuántos se publicaron, cuántos intentos fallaron y cuántos siguen pendientes
     */
    public RelaySummary relayPending() {
        int published = 0;
        int failed = 0;
        Instant deadline = clock.instant().plus(RUN_BUDGET);
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<OutboundEvent> events = outbox.findPending(BATCH_SIZE);
            if (events.isEmpty()) {
                break;
            }
            int failedInBatch = 0;
            for (OutboundEvent event : events) {
                if (!clock.instant().isBefore(deadline)) {
                    return new RelaySummary(published, failed + failedInBatch, outbox.countPending());
                }
                if (publishOne(event)) {
                    published++;
                } else {
                    failedInBatch++;
                }
            }
            failed += failedInBatch;
            if (failedInBatch > 0) {
                break;
            }
        }
        return new RelaySummary(published, failed, outbox.countPending());
    }

    /** Publica un evento y deja registrado el resultado: marcado como publicado o con un intento fallido más. */
    private boolean publishOne(OutboundEvent event) {
        if (publisher.publish(event)) {
            outbox.markPublished(event.id(), clock.instant());
            return true;
        }
        outbox.recordFailedAttempt(event.id());
        // Se avisa una sola vez, al llegar al intento número ALERT_ATTEMPTS, para no inundar el log con cada reintento
        if (event.attempts() + 1 == ALERT_ATTEMPTS) {
            logger.error("Evento sin publicar tras {} intentos [eventId={}, type={}]", ALERT_ATTEMPTS, event.id(), event.type());
        }
        return false;
    }
}
