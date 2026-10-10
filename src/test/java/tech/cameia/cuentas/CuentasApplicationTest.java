package tech.cameia.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

/**
 * Pruebas de la decisión de terminar el proceso en las tareas de una sola ejecución. El método {@code main} solo arranca la
 * aplicación y delega aquí, porque una prueba no puede ejecutarlo sin terminar la JVM de Surefire.
 */
@DisplayName("CuentasApplication")
class CuentasApplicationTest {

    private static final int NOT_CALLED = -1;

    @Test
    @DisplayName("termina el proceso con el código del contexto y lo cierra cuando está activo el perfil de relevo")
    void exitWhenOneShotJob_shouldExitWithZeroAndCloseContext_whenRelayProfileIsActive() {
        GenericApplicationContext context = contextWithProfiles("local", "account-events-relay");
        AtomicInteger exitCode = new AtomicInteger(NOT_CALLED);

        CuentasApplication.exitWhenOneShotJob(context, exitCode::set);

        assertThat(exitCode.get()).isZero();
        assertThat(context.isActive()).isFalse();
    }

    @Test
    @DisplayName("no termina el proceso ni cierra el contexto cuando solo está activo el perfil local")
    void exitWhenOneShotJob_shouldDoNothing_whenOnlyLocalProfileIsActive() {
        GenericApplicationContext context = contextWithProfiles("local");
        AtomicInteger exitCode = new AtomicInteger(NOT_CALLED);

        CuentasApplication.exitWhenOneShotJob(context, exitCode::set);

        assertThat(exitCode.get()).isEqualTo(NOT_CALLED);
        assertThat(context.isActive()).isTrue();
        context.close();
    }

    private static GenericApplicationContext contextWithProfiles(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        context.refresh();
        return context;
    }
}
