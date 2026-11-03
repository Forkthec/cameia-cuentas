package tech.cameia.cuentas.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.cuentas.application.command.ActivateAccountCommand;
import tech.cameia.cuentas.application.service.ActivateAccountService;
import tech.cameia.cuentas.domain.exception.AccountConflictException;
import tech.cameia.cuentas.domain.exception.AccountDisabledException;
import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.EmailNotVerifiedException;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
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

    @ParameterizedTest
    @ValueSource(strings = {"", "false", "TRUE", " true", "abc"})
    @DisplayName("Un encabezado que no es exactamente «true» llega al servicio como no verificado")
    void activate_shouldPassNotVerified_whenHeaderIsNotTrue(String valor) throws Exception {
        when(servicio.activate(any(ActivateAccountCommand.class))).thenThrow(new EmailNotVerifiedException());

        mockMvc.perform(post(RUTA).header("X-User-Id", "uid-firebase").header("X-User-Email-Verified", valor))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", containsStringIgnoringCase("charset=UTF-8")))
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.detail").value("Tu correo aún no está verificado."));

        assertThat(comandoRecibido().emailVerified()).isFalse();
    }

    @Test
    @DisplayName("Sin el encabezado de verificación el servicio recibe no verificado")
    void activate_shouldPassNotVerified_whenHeaderIsMissing() throws Exception {
        when(servicio.activate(any(ActivateAccountCommand.class))).thenThrow(new EmailNotVerifiedException());

        mockMvc.perform(post(RUTA).header("X-User-Id", "uid-firebase"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        assertThat(comandoRecibido().emailVerified()).isFalse();
    }

    @Test
    @DisplayName("El encabezado «true» llega al servicio como verificado")
    void activate_shouldPassVerified_whenHeaderIsTrue() throws Exception {
        when(servicio.activate(any(ActivateAccountCommand.class))).thenReturn(cuenta(AccountStatus.ACTIVE));

        mockMvc.perform(post(RUTA).header("X-User-Id", "uid-firebase").header("X-User-Email-Verified", "true"))
                .andExpect(status().isOk());

        assertThat(comandoRecibido().emailVerified()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"emailVerified\":true}", "{\"status\":\"ACTIVE\"}", "no es json"})
    @DisplayName("Un cuerpo que afirma la verificación se ignora")
    void activate_shouldIgnoreBody_whenItClaimsVerification(String cuerpo) throws Exception {
        when(servicio.activate(any(ActivateAccountCommand.class))).thenThrow(new EmailNotVerifiedException());

        mockMvc.perform(post(RUTA).header("X-User-Id", "uid-firebase").content(cuerpo))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        assertThat(comandoRecibido().emailVerified()).isFalse();
    }

    @Test
    @DisplayName("Una cuenta deshabilitada responde 403 ACCOUNT_DISABLED")
    void activate_shouldReturn403AccountDisabled_whenAccountIsDisabled() throws Exception {
        when(servicio.activate(any(ActivateAccountCommand.class))).thenThrow(new AccountDisabledException());

        mockMvc.perform(post(RUTA).header("X-User-Id", "uid-firebase").header("X-User-Email-Verified", "true"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
                .andExpect(jsonPath("$.title").value("Cuenta deshabilitada"))
                .andExpect(jsonPath("$.detail").value("Esta cuenta está deshabilitada."));
    }

    @Test
    @DisplayName("Un conflicto que persiste responde 409 ACCOUNT_CONFLICT")
    void activate_shouldReturn409AccountConflict_whenConflictPersists() throws Exception {
        when(servicio.activate(any(ActivateAccountCommand.class))).thenThrow(new AccountConflictException());

        mockMvc.perform(post(RUTA).header("X-User-Id", "uid-firebase").header("X-User-Email-Verified", "true"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_CONFLICT"))
                .andExpect(jsonPath("$.title").value("Conflicto de cuenta"))
                .andExpect(jsonPath("$.detail").value("La cuenta se está actualizando. Inténtalo de nuevo."));
    }

    @Test
    @DisplayName("Una identidad en blanco responde 400 IDENTITY_REQUIRED")
    void activate_shouldReturn400IdentityRequired_whenIdentityIsBlank() throws Exception {
        when(servicio.activate(any(ActivateAccountCommand.class))).thenThrow(new IdentityRequiredException());

        mockMvc.perform(post(RUTA).header("X-User-Id", "   ").header("X-User-Email-Verified", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("La petición no incluye los datos que exige esta ruta."));
    }

    @Test
    void sinCuentaLocalResponde404() throws Exception {
        when(servicio.activate(any(ActivateAccountCommand.class)))
                .thenThrow(new AccountNotFoundException());

        mockMvc.perform(post(RUTA).header("X-User-Id", "uid-sin-cuenta"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void sinIdentidadDelGatewayLaPeticionNoLlegaAlCasoDeUso() throws Exception {
        // Falta X-User-Id: el Gateway siempre lo emite en las rutas autenticadas, así que
        // una petición sin él no viene del camino previsto.
        mockMvc.perform(post(RUTA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));

        verify(servicio, never()).activate(any(ActivateAccountCommand.class));
    }

    private ActivateAccountCommand comandoRecibido() {
        ArgumentCaptor<ActivateAccountCommand> captura = ArgumentCaptor.forClass(ActivateAccountCommand.class);
        verify(servicio).activate(captura.capture());
        return captura.getValue();
    }

    private Account cuenta(AccountStatus estado) {
        return Account.rebuild(UUID.randomUUID(), "uid-firebase", "Ana", "Pérez",
                new BirthDate(LocalDate.of(1995, 4, 12)), null, null, estado);
    }
}
