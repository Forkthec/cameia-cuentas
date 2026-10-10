package tech.cameia.cuentas.infrastructure.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Impide que el perfil de producción arranque con la configuración del broker incompleta.
 *
 * <p>Spring Boot deja el texto {@code ${VARIABLE}} tal cual cuando la variable no existe, así que sin esta verificación un
 * despliegue al que se le olvide {@code SPRING_RABBITMQ_*} arrancaría sin error y dejaría todos los eventos pendientes.
 * El mensaje nombra solo las variables que faltan, nunca sus valores.</p>
 */
@Component
@Profile("prod")
public class BrokerSettingsGuard {

    private static final String UNRESOLVED_MARKER = "${";

    /**
     * Comprueba al crear el componente que el broker está configurado: basta la dirección completa
     * ({@code SPRING_RABBITMQ_ADDRESSES}, que lleva usuario y contraseña) o, si no hay dirección, host, usuario y contraseña.
     *
     * @param properties propiedades del broker ya enlazadas
     * @throws IllegalStateException si alguna está vacía, en blanco o sin resolver
     */
    public BrokerSettingsGuard(RabbitProperties properties) {
        if (hasAddresses(properties)) {
            return;
        }
        List<String> missing = new ArrayList<>();
        addIfMissing(missing, "SPRING_RABBITMQ_HOST", properties.getHost());
        addIfMissing(missing, "SPRING_RABBITMQ_USERNAME", properties.getUsername());
        addIfMissing(missing, "SPRING_RABBITMQ_PASSWORD", properties.getPassword());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Falta la configuración del broker de eventos: defina SPRING_RABBITMQ_ADDRESSES "
                    + "o bien " + String.join(", ", missing));
        }
    }

    private static boolean hasAddresses(RabbitProperties properties) {
        List<String> addresses = properties.getAddresses();
        return addresses != null && !addresses.isEmpty() && addresses.stream().noneMatch(BrokerSettingsGuard::isMissing);
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank() || value.contains(UNRESOLVED_MARKER);
    }

    private static void addIfMissing(List<String> missing, String variable, String value) {
        if (isMissing(value)) {
            missing.add(variable);
        }
    }
}
