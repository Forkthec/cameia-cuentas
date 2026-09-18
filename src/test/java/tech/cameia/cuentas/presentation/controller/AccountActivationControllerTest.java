package tech.cameia.cuentas.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.cuentas.application.command.ActivateAccountCommand;
import tech.cameia.cuentas.application.service.ActivateAccountService;
import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.EmailNotVerifiedException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.presentation.advice.ProblemDetailTestSupport;

/**
 * Prueba del contrato HTTP de la activación descrito en
 * {@code specs/CM-14-RegistroUsuario/spec.md} (REQ-CU-13).
 */
class AccountActivationControllerTest {

    private static final String RUTA = "/api/v1/users/me/verification";

    private final ActivateAccountService servicio = mock(ActivateAccountService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AccountActivationController(servicio))
            .setControllerAdvice(ProblemDetailTestSupport.manejadorDeErrores())
            .build();

    @Test
    void laActivacionDevuelveLaCuentaActiva() throws Exception {
        when(servicio.activate(any(ActivateAccountCommand.class))).thenReturn(cuenta(AccountStatus.ACTIVE));

        mockMvc.perform(post(RUTA)
                        .header("X-User-Id", "uid-firebase")
                        .header("X-User-Email-Verified", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void funcionaSinElEncabezadoDeVerificacionDelGateway() throws Exception {
        when(servicio.activate(any(ActivateAccountCommand.class))).thenReturn(cuenta(AccountStatus.ACTIVE));

        mockMvc.perform(post(RUTA).header("X-User-Id", "uid-firebase"))
                .andExpect(status().isOk());
    }

    @Test
    void sinCorreoVerificadoRespondeProhibido() throws Exception {
        when(servicio.activate(any(ActivateAccountCommand.class)))
                .thenThrow(new EmailNotVerifiedException());

        mockMvc.perform(post(RUTA).header("X-User-Id", "uid-firebase"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("verificar tu correo")));
    }

    @Test
    void sinCuentaLocalResponde404() throws Exception {
        when(servicio.activate(any(ActivateAccountCommand.class)))
                .thenThrow(new AccountNotFoundException());

        mockMvc.perform(post(RUTA).header("X-User-Id", "uid-sin-cuenta"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinIdentidadDelGatewayLaPeticionNoLlegaAlCasoDeUso() throws Exception {
        // Falta X-User-Id: el Gateway siempre lo emite en las rutas autenticadas, así que
        // una petición sin él no viene del camino previsto.
        mockMvc.perform(post(RUTA)).andExpect(status().isBadRequest());
    }

    private Account cuenta(AccountStatus estado) {
        return Account.rebuild(UUID.randomUUID(), "uid-firebase", "Ana", "Pérez",
                new BirthDate(LocalDate.of(1995, 4, 12)), null, null, estado);
    }
}
