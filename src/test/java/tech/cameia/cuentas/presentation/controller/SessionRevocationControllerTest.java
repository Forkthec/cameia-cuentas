package tech.cameia.cuentas.presentation.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.cuentas.application.service.RevokeAllSessionsService;
import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
import tech.cameia.cuentas.presentation.advice.ProblemDetailTestSupport;

/**
 * Contrato HTTP de {@code POST /api/v1/auth/logout-all}: respuesta sin cuerpo, identidad solo del encabezado y errores.
 */
class SessionRevocationControllerTest {

    private static final String ROUTE = "/api/v1/auth/logout-all";
    private static final String PROBLEM_JSON = "application/problem+json";

    private final RevokeAllSessionsService service = mock(RevokeAllSessionsService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SessionRevocationController(service))
            .setControllerAdvice(ProblemDetailTestSupport.manejadorDeErrores())
            .build();

    @Test
    @DisplayName("Responde 204 sin cuerpo y revoca al usuario del encabezado")
    void logoutAll_shouldReturn204WithoutBody_whenIdentityPresent() throws Exception {
        mockMvc.perform(post(ROUTE).header("X-User-Id", "uid-ctrl-1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().doesNotExist("Content-Type"));

        verify(service).revokeAll("uid-ctrl-1");
    }

    @Test
    @DisplayName("Sin X-User-Id responde 400 IDENTITY_REQUIRED y no revoca nada")
    void logoutAll_shouldReturn400_whenIdentityHeaderMissing() throws Exception {
        mockMvc.perform(post(ROUTE))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("La petición no incluye los datos que exige esta ruta."))
                .andExpect(jsonPath("$.requestId").value(notNullValue()));

        verify(service, never()).revokeAll(any());
    }

    @Test
    @DisplayName("Una identidad que el caso de uso rechaza responde 400 IDENTITY_REQUIRED")
    void logoutAll_shouldReturn400_whenServiceRejectsIdentity() throws Exception {
        doThrow(new IdentityRequiredException()).when(service).revokeAll("   ");

        mockMvc.perform(post(ROUTE).header("X-User-Id", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.requestId").value(notNullValue()));
    }

    @Test
    @DisplayName("Un cuerpo que nombra a otro usuario se ignora: solo cuenta el encabezado")
    void logoutAll_shouldRevokeHeaderUserOnly_whenBodyNamesAnotherUser() throws Exception {
        mockMvc.perform(post(ROUTE).header("X-User-Id", "uid-propio")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firebaseUid\":\"uid-ajeno\"}"))
                .andExpect(status().isNoContent());

        verify(service).revokeAll("uid-propio");
        verifyNoMoreInteractions(service);
    }

    @Test
    @DisplayName("Un parámetro de la consulta que nombra a otro usuario se ignora")
    void logoutAll_shouldRevokeHeaderUserOnly_whenQueryNamesAnotherUser() throws Exception {
        mockMvc.perform(post(ROUTE).param("uid", "uid-ajeno").param("firebaseUid", "uid-ajeno")
                        .header("X-User-Id", "uid-propio"))
                .andExpect(status().isNoContent());

        verify(service).revokeAll("uid-propio");
        verifyNoMoreInteractions(service);
    }

    @Test
    @DisplayName("Un cuerpo JSON ilegible no se lee y no provoca error")
    void logoutAll_shouldIgnoreBody_whenMalformedJson() throws Exception {
        mockMvc.perform(post(ROUTE).header("X-User-Id", "uid-propio")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"x\":"))
                .andExpect(status().isNoContent());

        verify(service).revokeAll("uid-propio");
    }

    @Test
    @DisplayName("Un Content-Type inesperado no provoca 415")
    void logoutAll_shouldIgnoreBody_whenUnexpectedContentType() throws Exception {
        mockMvc.perform(post(ROUTE).header("X-User-Id", "uid-propio")
                        .contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isNoContent());

        verify(service).revokeAll("uid-propio");
    }

    @Test
    @DisplayName("Si Firebase no responde, 503 DEPENDENCY_UNAVAILABLE con el texto genérico")
    void logoutAll_shouldReturn503_whenDirectoryUnavailable() throws Exception {
        doThrow(new DependencyUnavailableException(new IllegalStateException("caído")))
                .when(service).revokeAll("uid-propio");

        mockMvc.perform(post(ROUTE).header("X-User-Id", "uid-propio"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error. Inténtalo de nuevo."))
                .andExpect(jsonPath("$.requestId").value(notNullValue()));
    }

    @Test
    @DisplayName("Si Firebase rechaza la revocación, 500 genérico sin revelar el motivo")
    void logoutAll_shouldReturn500_whenDirectoryRejects() throws Exception {
        doThrow(new IllegalStateException("Firebase rechazó la revocación de las sesiones del usuario"))
                .when(service).revokeAll("uid-propio");

        mockMvc.perform(post(ROUTE).header("X-User-Id", "uid-propio"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.requestId").value(notNullValue()))
                .andExpect(content().string(not(containsString("Firebase"))))
                .andExpect(content().string(not(containsString("IllegalState"))));
    }

    @Test
    @DisplayName("GET sobre la ruta responde 405 con Allow: POST")
    void logoutAll_shouldReturn405_whenGet() throws Exception {
        mockMvc.perform(get(ROUTE).header("X-User-Id", "uid-propio"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.requestId").value(notNullValue()));
    }

    @Test
    @DisplayName("Un valor hostil válido en el encabezado se entrega tal cual al caso de uso y la respuesta queda vacía")
    void logoutAll_shouldReturn204WithEmptyBody_whenHostileValue() throws Exception {
        String hostile = "' OR 1=1 --";

        mockMvc.perform(post(ROUTE).header("X-User-Id", hostile))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).revokeAll(hostile);
    }

    @Test
    @DisplayName("Un valor hostil rechazado no se refleja en el cuerpo del 400")
    void logoutAll_shouldNotEchoIdentity_whenHostileValueIsRejected() throws Exception {
        String hostile = "' OR 1=1 --" + "x".repeat(120);
        doThrow(new IdentityRequiredException()).when(service).revokeAll(hostile);

        mockMvc.perform(post(ROUTE).header("X-User-Id", hostile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(content().string(not(containsString("OR 1=1"))));
    }
}
