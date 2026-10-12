package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import tech.cameia.cuentas.domain.event.OutboundEvent;
import tech.cameia.cuentas.domain.port.EventPublisher;
import tech.cameia.cuentas.infrastructure.messaging.publisher.DisabledEventPublisher;

/** Pruebas del publicador inerte que usa la aplicación cuando los eventos están apagados. */
@DisplayName("AccountEventsMessagingConfiguration con los eventos apagados")
class AccountEventsMessagingConfigurationTest {

    private final AccountEventsMessagingConfiguration configuration = new AccountEventsMessagingConfiguration();

    @Test
    @DisplayName("sin perfil prod entrega un publicador que nunca confirma, para que el evento siga pendiente")
    void disabledEventPublisher_shouldNeverConfirm_whenNotProd() {
        EventPublisher publisher = configuration.disabledEventPublisher(new MockEnvironment());
        OutboundEvent event = new OutboundEvent(UUID.randomUUID(), "cuenta.creada", 1, "uid-1", "{}", Instant.now(),
                "req-1", 0);

        assertThat(publisher).isInstanceOf(DisabledEventPublisher.class);
        assertThat(publisher.publish(event)).isFalse();
    }

    @Test
    @DisplayName("con el perfil prod no arranca y explica por qué")
    void disabledEventPublisher_shouldRejectStartup_whenProdProfileIsActive() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> configuration.disabledEventPublisher(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EVENTS_ENABLED=false no se admite con el perfil 'prod'");
    }
}
