package tech.cameia.cuentas.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.cuentas.application.command.AcceptPolicyCommand;
import tech.cameia.cuentas.application.service.AcceptPolicyService;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.CurrentPolicy;
import tech.cameia.cuentas.domain.model.PolicyAcceptance;
import tech.cameia.cuentas.presentation.advice.ProblemDetailTestSupport;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato HTTP de {@code POST /api/v1/users/me/policy-acceptance}: respuesta, identidad, validación del cuerpo y
 * campos que no se obedecen.
 */
@DisplayName("PolicyAcceptanceController")
class PolicyAcceptanceControllerTest {

    private static final String RUTA = "/api/v1/users/me/policy-acceptance";
    private static final String UID = "uid-policy-1";
    private static final Instant ACEPTADA = Instant.parse("2026-11-14T15:30:00Z");

    private final AcceptPolicyService servicio = mock(AcceptPolicyService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PolicyAcceptanceController(servicio, new CurrentPolicy("2026-11-v1")))
            .setControllerAdvice(ProblemDetailTestSupport.manejadorDeErrores())
            .setValidator(ProblemDetailTestSupport.validadorConPolitica("2026-11-v1"))
            .setMessageConverters(convertidorComoLaAplicacion())
            .build();

    /** Escribe los errores igual que la aplicación: {@code code} y {@code requestId} en el nivel superior. */
    private static JacksonJsonHttpMessageConverter convertidorComoLaAplicacion() {
        return new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build());
    }

    private static Account cuentaAceptada() {
        return Account.rebuild(UUID.randomUUID(), UID, "Ana", "Pérez", new BirthDate(LocalDate.of(1995, 4, 12)), null,
                null, new PolicyAcceptance("2026-11-v1", ACEPTADA), AccountStatus.ACTIVE);
    }

    private void prepararServicio() {
        when(servicio.accept(any(AcceptPolicyCommand.class))).thenReturn(cuentaAceptada());
    }

    @Test
    @DisplayName("Responde 200 con la versión aceptada, su fecha y la versión vigente")
    void accept_shouldReturn200WithAcceptance() throws Exception {
        prepararServicio();

        mockMvc.perform(post(RUTA).header("X-User-Id", UID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersion\":\"2026-11-v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedVersion").value("2026-11-v1"))
                .andExpect(jsonPath("$.acceptedAt").value("2026-11-14T15:30:00Z"))
                .andExpect(jsonPath("$.currentVersion").value("2026-11-v1"));
    }

    @Test
    @DisplayName("La respuesta no se guarda en cachés")
    void accept_shouldReturnNoStore() throws Exception {
        prepararServicio();

        mockMvc.perform(post(RUTA).header("X-User-Id", UID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersion\":\"2026-11-v1\"}"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("Responde 400 IDENTITY_REQUIRED cuando falta X-User-Id")
    void accept_shouldReturn400_whenUserIdHeaderIsMissing() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersion\":\"2026-11-v1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
        verify(servicio, never()).accept(any(AcceptPolicyCommand.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"policyVersion\":null}", "{\"policyVersion\":\"\"}",
        "{\"policyVersion\":\"  \"}"})
    @DisplayName("Pide la versión cuando falta, es nula, está vacía o en blanco")
    void accept_shouldReturnPolicyVersionRequired_whenBlank(String cuerpo) throws Exception {
        mockMvc.perform(post(RUTA).header("X-User-Id", UID).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("policyVersion"))
                .andExpect(jsonPath("$.errors[0].code").value("POLICY_VERSION_REQUIRED"))
                .andExpect(jsonPath("$.errors[0].message").value("Recarga la página y vuelve a aceptar la política."));
        verify(servicio, never()).accept(any(AcceptPolicyCommand.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"2026-10-v1\"", "\"2026-11-V1\"", "\"sin-aceptar\"", "\"2026-11-v1aaaaaaaaaaa\"",
        "\"<script>\"", "\"' OR 1=1 --\"", "123"})
    @DisplayName("Rechaza una versión distinta de la vigente con POLICY_VERSION_OUTDATED")
    void accept_shouldReturnPolicyVersionOutdated_whenOld(String valor) throws Exception {
        mockMvc.perform(post(RUTA).header("X-User-Id", UID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersion\":" + valor + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("policyVersion"))
                .andExpect(jsonPath("$.errors[0].code").value("POLICY_VERSION_OUTDATED"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("La política cambió. Recarga la página y vuelve a aceptarla."));
        verify(servicio, never()).accept(any(AcceptPolicyCommand.class));
    }

    @Test
    @DisplayName("Un arreglo en la versión se responde como cuerpo ilegible, sin lista de errores")
    void accept_shouldReturnBodyInvalidFormat_whenPolicyVersionIsArray() throws Exception {
        mockMvc.perform(post(RUTA).header("X-User-Id", UID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersion\":[\"2026-11-v1\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("Ignora la identidad que venga en el cuerpo: la cuenta sale del encabezado")
    void accept_shouldIgnoreBodyIdentity() throws Exception {
        prepararServicio();
        ArgumentCaptor<AcceptPolicyCommand> comando = ArgumentCaptor.forClass(AcceptPolicyCommand.class);

        mockMvc.perform(post(RUTA).header("X-User-Id", UID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersion\":\"2026-11-v1\",\"firebaseUid\":\"otro-uid\"}"))
                .andExpect(status().isOk());

        verify(servicio).accept(comando.capture());
        assertThat(comando.getValue().firebaseUid()).isEqualTo(UID);
    }

    @Test
    @DisplayName("Ignora la fecha que venga en el cuerpo: el instante lo pone el servicio")
    void accept_shouldIgnoreAcceptedAtInBody() throws Exception {
        prepararServicio();
        ArgumentCaptor<AcceptPolicyCommand> comando = ArgumentCaptor.forClass(AcceptPolicyCommand.class);

        mockMvc.perform(post(RUTA).header("X-User-Id", UID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersion\":\"2026-11-v1\",\"acceptedAt\":\"2000-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedAt").value("2026-11-14T15:30:00Z"));

        verify(servicio).accept(comando.capture());
        assertThat(comando.getValue().policyVersion()).isEqualTo("2026-11-v1");
    }
}
