package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;

import tech.cameia.cuentas.application.service.AccountCreatedBackfillService;
import tech.cameia.cuentas.application.service.BackfillSummary;
import tech.cameia.cuentas.application.service.OutboxRelayService;
import tech.cameia.cuentas.application.service.RelaySummary;
import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;

/**
 * Pruebas de la tarea de relevo de eventos de cuenta con el perfil {@code account-events-relay}: se ejecuta una vez al
 * arrancar, no abre puerto HTTP y usa plazos holgados con el broker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(AccountEventsRelayJobRunner.PROFILE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("AccountEventsRelayJobRunner")
class AccountEventsRelayJobRunnerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @MockitoSpyBean(reset = MockReset.NONE)
    private AccountCreatedBackfillService backfillService;

    @MockitoSpyBean(reset = MockReset.NONE)
    private OutboxRelayService relayService;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AccountEventsProperties properties;

    @Test
    @DisplayName("registra los eventos faltantes y luego publica los pendientes, una vez cada uno, al arrancar")
    void run_shouldBackfillThenRelayOnce_whenProfileIsActive() {
        InOrder order = inOrder(backfillService, relayService);

        order.verify(backfillService, times(1)).enqueueMissing();
        order.verify(relayService, times(1)).relayPending();
    }

    @Test
    @DisplayName("no abre servidor web con el perfil activo")
    void context_shouldHaveNoWebServer_whenProfileIsActive() {
        assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
    }

    @Test
    @DisplayName("usa un plazo de publicación de 5 s con el perfil activo")
    void properties_shouldUseLongerTimeouts_whenProfileIsActive() {
        assertThat(properties.publishTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("publica igual los pendientes y termina con error cuando el directorio no está disponible")
    void run_shouldRelayPendingAndStillFail_whenDirectoryIsUnavailable() {
        AccountCreatedBackfillService failing = mock(AccountCreatedBackfillService.class);
        DependencyUnavailableException unavailable = new DependencyUnavailableException(new IllegalStateException("caído"));
        when(failing.enqueueMissing()).thenThrow(unavailable);
        OutboxRelayService relay = mock(OutboxRelayService.class);
        when(relay.relayPending()).thenReturn(new RelaySummary(1, 0, 0));
        AccountEventsRelayJobRunner runner = new AccountEventsRelayJobRunner(failing, relay);

        assertThatThrownBy(() -> runner.run(null)).isSameAs(unavailable);

        verify(relay, times(1)).relayPending();
    }

    @Test
    @DisplayName("publica igual los pendientes y termina con error cuando el directorio rechaza la consulta")
    void run_shouldRelayPendingAndStillFail_whenDirectoryRejects() {
        AccountCreatedBackfillService failing = mock(AccountCreatedBackfillService.class);
        IllegalStateException rejected = new IllegalStateException("rechazado");
        when(failing.enqueueMissing()).thenThrow(rejected);
        OutboxRelayService relay = mock(OutboxRelayService.class);
        when(relay.relayPending()).thenReturn(new RelaySummary(1, 0, 0));
        AccountEventsRelayJobRunner runner = new AccountEventsRelayJobRunner(failing, relay);

        assertThatThrownBy(() -> runner.run(null)).isSameAs(rejected);

        verify(relay, times(1)).relayPending();
    }

    @Test
    @DisplayName("no oculta una falla del relevo cuando el directorio sí respondió")
    void run_shouldPropagate_whenRelayFails() {
        AccountCreatedBackfillService backfill = mock(AccountCreatedBackfillService.class);
        when(backfill.enqueueMissing()).thenReturn(new BackfillSummary(0, 0, false));
        OutboxRelayService relay = mock(OutboxRelayService.class);
        when(relay.relayPending()).thenThrow(new IllegalStateException("base caída"));
        AccountEventsRelayJobRunner runner = new AccountEventsRelayJobRunner(backfill, relay);

        assertThatThrownBy(() -> runner.run(null)).isInstanceOf(IllegalStateException.class).hasMessage("base caída");
    }
}
