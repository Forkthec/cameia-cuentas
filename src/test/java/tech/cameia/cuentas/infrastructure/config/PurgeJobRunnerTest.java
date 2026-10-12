package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tech.cameia.cuentas.application.service.PurgeUnverifiedAccountsService;
import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.model.PurgeSummary;

/** Pruebas de la tarea de purga, sin Spring: el perfil y el servicio se simulan. */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("PurgeJobRunner")
class PurgeJobRunnerTest {

    private final PurgeUnverifiedAccountsService service = mock(PurgeUnverifiedAccountsService.class);
    private final PurgeJobRunner runner = new PurgeJobRunner(service);

    @Test
    @DisplayName("ejecuta la purga una sola vez al arrancar")
    void run_shouldPurgeOnce_whenStarted() {
        when(service.purge()).thenReturn(new PurgeSummary(0, 0, 0, 0, 0, false));

        runner.run(null);

        verify(service, times(1)).purge();
    }

    @Test
    @DisplayName("avisa cuando quedaron cuentas fallidas, sin hacer fallar el proceso")
    void run_shouldWarn_whenSomeAccountsFailed(CapturedOutput output) {
        when(service.purge()).thenReturn(new PurgeSummary(5, 3, 0, 2, 0, false));

        runner.run(null);

        assertThat(output.getAll()).contains("fallidas=2").contains("quedanCandidatas=false");
    }

    @Test
    @DisplayName("avisa cuando quedan candidatas sin procesar")
    void run_shouldWarn_whenCandidatesRemain(CapturedOutput output) {
        when(service.purge()).thenReturn(new PurgeSummary(1000, 1000, 0, 0, 0, true));

        runner.run(null);

        assertThat(output.getAll()).contains("quedanCandidatas=true");
    }

    @Test
    @DisplayName("no avisa cuando todo salió bien")
    void run_shouldNotWarn_whenAllSucceeded(CapturedOutput output) {
        when(service.purge()).thenReturn(new PurgeSummary(3, 2, 1, 0, 1, false));

        runner.run(null);

        assertThat(output.getAll()).doesNotContain("La purga terminó con cuentas pendientes");
    }

    @Test
    @DisplayName("deja salir la excepción para que el proceso termine con error")
    void run_shouldPropagate_whenPurgeThrows() {
        DependencyUnavailableException unavailable = new DependencyUnavailableException(new IllegalStateException("caído"));
        when(service.purge()).thenThrow(unavailable);

        assertThatThrownBy(() -> runner.run(null)).isSameAs(unavailable);
    }
}
