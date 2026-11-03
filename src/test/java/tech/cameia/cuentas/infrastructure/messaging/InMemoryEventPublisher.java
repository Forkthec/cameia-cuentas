package tech.cameia.cuentas.infrastructure.messaging;

import java.util.ArrayList;
import java.util.List;

import tech.cameia.cuentas.domain.event.OutboundEvent;
import tech.cameia.cuentas.domain.port.EventPublisher;

/**
 * Doble de prueba del publicador: guarda los eventos que se le piden publicar y puede simular que el broker no los confirma.
 */
public class InMemoryEventPublisher implements EventPublisher {

    private final List<OutboundEvent> published = new ArrayList<>();
    private int failuresLeft;

    /**
     * Hace que las próximas {@code n} publicaciones devuelvan {@code false}.
     *
     * @param n cantidad de publicaciones que fallarán
     */
    public void failNext(int n) {
        this.failuresLeft = n;
    }

    /**
     * Hace que todas las publicaciones siguientes fallen.
     */
    public void failAlways() {
        this.failuresLeft = Integer.MAX_VALUE;
    }

    @Override
    public boolean publish(OutboundEvent event) {
        if (failuresLeft > 0) {
            failuresLeft--;
            return false;
        }
        published.add(event);
        return true;
    }

    /**
     * Eventos confirmados hasta ahora, en el orden en que se publicaron.
     *
     * @return copia de la lista de eventos publicados
     */
    public List<OutboundEvent> published() {
        return List.copyOf(published);
    }
}
