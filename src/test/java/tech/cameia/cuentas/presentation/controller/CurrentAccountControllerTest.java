package tech.cameia.cuentas.presentation.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.cuentas.application.service.GetCurrentAccountService;
import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.PhoneNumber;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.presentation.advice.ProblemDetailTestSupport;

/**
 * Contrato HTTP de {@code GET /api/v1/users/me}: campos, identidad y errores.
 */
class CurrentAccountControllerTest {

    private static final String ROUTE = "/api/v1/users/me";
    private static final String UID = "uid-me-1";
    private static final UUID ID = UUID.fromString("3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10");

    private final GetCurrentAccountService service = mock(GetCurrentAccountService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new CurrentAccountController(service))
            .setControllerAdvice(ProblemDetailTestSupport.manejadorDeErrores())
            .build();

    private static Account fullAccount() {
        return Account.rebuild(ID, UID, "María José", "Gómez-Ruiz", new BirthDate(LocalDate.of(1995, 4, 12)),
                new PhoneNumber("+573001234567"), Pronoun.SHE, AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Devuelve los ocho campos de la cuenta")
    void getMe_shouldReturnAccountFields_whenAccountExists() throws Exception {
        when(service.find(UID)).thenReturn(fullAccount());

        mockMvc.perform(get(ROUTE).header("X-User-Id", UID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.firstName").value("María José"))
                .andExpect(jsonPath("$.lastName").value("Gómez-Ruiz"))
                .andExpect(jsonPath("$.birthDate").value("1995-04-12"))
                .andExpect(jsonPath("$.phoneNumber").value("+573001234567"))
                .andExpect(jsonPath("$.pronoun").value("SHE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.plan").value("FREE"));
    }

    @Test
    @DisplayName("La respuesta con datos personales no se guarda en cachés")
    void getMe_shouldSendNoStore_whenAccountExists() throws Exception {
        when(service.find(UID)).thenReturn(fullAccount());

        mockMvc.perform(get(ROUTE).header("X-User-Id", UID))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("No expone correo, contraseña ni campos internos")
    void getMe_shouldNotExposeInternalFields_whenAccountExists() throws Exception {
        when(service.find(UID)).thenReturn(fullAccount());

        mockMvc.perform(get(ROUTE).header("X-User-Id", UID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(8)))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.firebaseUid").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    @DisplayName("El celular y el pronombre sin valor van presentes con null")
    void getMe_shouldReturnNullPhone_whenAccountHasNoPhone() throws Exception {
        when(service.find(UID)).thenReturn(Account.rebuild(ID, UID, "María José", "Gómez-Ruiz",
                new BirthDate(LocalDate.of(1995, 4, 12)), null, null, AccountStatus.PENDING_VERIFICATION));

        mockMvc.perform(get(ROUTE).header("X-User-Id", UID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasKey("phoneNumber")))
                .andExpect(jsonPath("$.phoneNumber").value(nullValue()))
                .andExpect(jsonPath("$").value(hasKey("pronoun")))
                .andExpect(jsonPath("$.pronoun").value(nullValue()))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
    }

    @Test
    @DisplayName("Una fecha de nacimiento desconocida va como null y no como error")
    void getMe_shouldReturnNullBirthDate_whenUnknown() throws Exception {
        when(service.find(UID)).thenReturn(Account.rebuild(ID, UID, "María José", "Gómez-Ruiz", null, null, null,
                AccountStatus.ACTIVE));

        mockMvc.perform(get(ROUTE).header("X-User-Id", UID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasKey("birthDate")))
                .andExpect(jsonPath("$.birthDate").value(nullValue()));
    }

    @Test
    @DisplayName("Sin X-User-Id responde 400 IDENTITY_REQUIRED sin llamar al caso de uso")
    void getMe_shouldReturn400_whenIdentityHeaderIsMissing() throws Exception {
        mockMvc.perform(get(ROUTE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));

        verify(service, never()).find(any());
    }

    @Test
    @DisplayName("Con X-User-Id en blanco responde 400 IDENTITY_REQUIRED con el mismo mensaje")
    void getMe_shouldReturn400_whenIdentityHeaderIsBlank() throws Exception {
        when(service.find("   ")).thenThrow(new IdentityRequiredException());

        mockMvc.perform(get(ROUTE).header("X-User-Id", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("La petición no incluye los datos que exige esta ruta"));
    }

    @Test
    @DisplayName("Sin cuenta responde 404 ACCOUNT_NOT_FOUND")
    void getMe_shouldReturn404_whenAccountDoesNotExist() throws Exception {
        when(service.find(UID)).thenThrow(new AccountNotFoundException());

        mockMvc.perform(get(ROUTE).header("X-User-Id", UID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value(containsString("No encontramos una cuenta")));
    }
}
