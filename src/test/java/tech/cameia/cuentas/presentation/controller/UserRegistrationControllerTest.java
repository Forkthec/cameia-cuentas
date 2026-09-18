package tech.cameia.cuentas.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.cuentas.application.command.RegisterUserCommand;
import tech.cameia.cuentas.application.service.RegisterUserService;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.exception.InvalidBirthDateException;
import tech.cameia.cuentas.domain.exception.InvalidBirthDateException.Reason;
import tech.cameia.cuentas.domain.exception.WeakPasswordException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.presentation.advice.ProblemDetailTestSupport;

/**
 * Prueba del contrato HTTP del registro descrito en
 * {@code specs/CM-14-RegistroUsuario/spec.md} (REQ-CU-01, REQ-CU-05, REQ-CU-07 y
 * REQ-CU-12).
 *
 * <p>El caso de uso está simulado: aquí se verifica la traducción entre JSON y dominio,
 * los códigos de estado y el formato de los errores, no la lógica del registro, que tiene
 * su propia prueba.</p>
 */
class UserRegistrationControllerTest {

    private static final String RUTA = "/api/v1/users";

    private final RegisterUserService servicio = mock(RegisterUserService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new UserRegistrationController(servicio))
            .setControllerAdvice(ProblemDetailTestSupport.manejadorDeErrores())
            .build();

    @Test
    void elRegistroExitosoDevuelveCreadoConElEstadoYElPlan() throws Exception {
        when(servicio.register(any(RegisterUserCommand.class))).thenReturn(cuentaCreada());

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firebaseUid").value("uid-firebase"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.plan").value("FREE"));
    }

    @Test
    void laRespuestaNoDevuelveElCorreoNiLaContrasenia() throws Exception {
        when(servicio.register(any(RegisterUserCommand.class))).thenReturn(cuentaCreada());

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoValido()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("frase secreta larga"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("ana@cameia.tech"))));
    }

    @Test
    void elCorreoRepetidoDevuelveConflictoConElMensajeDelCriterio() throws Exception {
        when(servicio.register(any(RegisterUserCommand.class)))
                .thenThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoValido()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Este correo ya se encuentra registrado"));
    }

    @Test
    void elMenorDeEdadRecibeSuMensajeEnElCampoDeLaFecha() throws Exception {
        when(servicio.register(any(RegisterUserCommand.class)))
                .thenThrow(new InvalidBirthDateException(Reason.UNDERAGE, "Debes ser mayor de edad"));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoValido()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Debes ser mayor de edad"))
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"));
    }

    @Test
    void laContraseniaDebilSeSenialaEnSuCampo() throws Exception {
        when(servicio.register(any(RegisterUserCommand.class)))
                .thenThrow(new WeakPasswordException("La contraseña debe tener al menos 12 caracteres"));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoValido()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void faltarUnCampoObligatorioNoLlegaAlCasoDeUso() throws Exception {
        String sinNombres = """
                {"lastName":"Pérez","birthDate":"12/04/1995","email":"ana@cameia.tech",
                 "password":"frase secreta larga"}
                """;

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(sinNombres))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("firstName"));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    @Test
    void unaFechaConOtroFormatoDaErrorDeFormatoYNoDeMayoriaDeEdad() throws Exception {
        String fechaISO = cuerpoValido().replace("12/04/1995", "1995-04-12");

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(fechaISO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("DD/MM/AAAA")));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    @Test
    void unPronombreFueraDeLaListaSeRechaza() throws Exception {
        String pronombreInvalido = cuerpoValido().replace("\"SHE\"", "\"OTRO\"");

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(pronombreInvalido))
                .andExpect(status().isUnprocessableEntity());

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    private Account cuentaCreada() {
        return Account.rebuild(UUID.randomUUID(), "uid-firebase", "Ana", "Pérez",
                new BirthDate(LocalDate.of(1995, 4, 12)), null, null,
                tech.cameia.cuentas.domain.model.AccountStatus.PENDING_VERIFICATION);
    }

    private String cuerpoValido() {
        return """
                {"firstName":"Ana","lastName":"Pérez","birthDate":"12/04/1995",
                 "email":"ana@cameia.tech","password":"frase secreta larga",
                 "phoneNumber":"+573001234567","pronoun":"SHE"}
                """;
    }
}
