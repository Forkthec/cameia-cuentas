package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import tech.cameia.cuentas.application.service.PurgeUnverifiedAccountsService;

/**
 * Pruebas de la tarea de purga con el perfil {@code purge-job}: la purga corre una vez al arrancar y no se abre puerto HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(PurgeJobRunner.PROFILE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PurgeJobRunner con el perfil activo")
class PurgeJobProfileTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @MockitoSpyBean(reset = MockReset.NONE)
    private PurgeUnverifiedAccountsService service;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("ejecuta la purga una vez al arrancar")
    void run_shouldPurgeOnce_whenProfileIsActive() {
        verify(service, times(1)).purge();
    }

    @Test
    @DisplayName("no abre servidor web con el perfil activo")
    void context_shouldHaveNoWebServer_whenProfileIsActive() {
        assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
    }
}
