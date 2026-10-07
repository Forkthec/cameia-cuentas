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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.cuentas.application.command.RegisterUserCommand;
import tech.cameia.cuentas.application.service.RegisterUserService;
import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.exception.ErrorCode;
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
                .andExpect(jsonPath("$.detail").value("Este correo ya se encuentra registrado"))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void elMenorDeEdadRecibeSuMensajeEnElCampoDeLaFecha() throws Exception {
        when(servicio.register(any(RegisterUserCommand.class)))
                .thenThrow(new InvalidBirthDateException(Reason.UNDERAGE, "Debes ser mayor de edad"));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoValido()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Revisa los campos marcados."))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"))
                .andExpect(jsonPath("$.errors[0].code").value("BIRTH_DATE_UNDERAGE"))
                .andExpect(jsonPath("$.errors[0].message").value("Debes ser mayor de edad"));
    }

    @Test
    void laContraseniaDebilSeSenialaEnSuCampo() throws Exception {
        when(servicio.register(any(RegisterUserCommand.class)))
                .thenThrow(new WeakPasswordException(ErrorCode.PASSWORD_TOO_SHORT,
                        "La contraseña debe tener al menos 12 caracteres"));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoValido()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_TOO_SHORT"))
                .andExpect(jsonPath("$.errors[0].message").value("La contraseña debe tener al menos 12 caracteres"));
    }

    @Test
    void firebaseNoDisponibleResponde503ConSuCodigoYSinLaCausa() throws Exception {
        when(servicio.register(any(RegisterUserCommand.class)))
                .thenThrow(new DependencyUnavailableException(new java.io.IOException("Connection refused")));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoValido()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error. Inténtalo de nuevo."))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Connection refused"))));
    }

    @Test
    void faltarUnCampoObligatorioNoLlegaAlCasoDeUso() throws Exception {
        String sinNombres = """
                {"lastName":"Pérez","birthDate":"12/04/1995","email":"ana@cameia.tech",
                 "password":"frase secreta larga"}
                """;

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(sinNombres))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Revisa los campos marcados."))
                .andExpect(jsonPath("$.errors[?(@.field=='firstName')].code")
                        .value(org.hamcrest.Matchers.contains("FIRST_NAME_REQUIRED")))
                .andExpect(jsonPath("$.errors[?(@.field=='firstName')].message")
                        .value(org.hamcrest.Matchers.contains("Los nombres son obligatorios")));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("restriccionesDelContrato")
    void cadaRestriccionDelContratoDevuelveSuCodigoYSuMensaje(String campo, String codigo, String mensaje,
            String cuerpo) throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value(campo))
                .andExpect(jsonPath("$.errors[0].code").value(codigo))
                .andExpect(jsonPath("$.errors[0].message").value(mensaje));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    static Stream<Arguments> restriccionesDelContrato() {
        String valido = """
                {"firstName":"Ana","lastName":"Pérez","birthDate":"12/04/1995",
                 "email":"ana@cameia.tech","password":"frase secreta larga",
                 "phoneNumber":"+573001234567","pronoun":"SHE"}
                """;
        return Stream.of(
                Arguments.of("firstName", "FIRST_NAME_TOO_LONG", "Los nombres no pueden superar los 120 caracteres",
                        valido.replace("\"Ana\"", "\"" + "a".repeat(121) + "\"")),
                Arguments.of("lastName", "LAST_NAME_TOO_LONG", "Los apellidos no pueden superar los 120 caracteres",
                        valido.replace("\"Pérez\"", "\"" + "b".repeat(121) + "\"")),
                Arguments.of("lastName", "LAST_NAME_REQUIRED", "Los apellidos son obligatorios",
                        valido.replace("\"Pérez\"", "null")),
                Arguments.of("birthDate", "BIRTH_DATE_REQUIRED", "La fecha de nacimiento es obligatoria",
                        valido.replace("\"birthDate\":\"12/04/1995\",", "")),
                Arguments.of("email", "EMAIL_REQUIRED", "El correo electrónico es obligatorio",
                        valido.replace("\"ana@cameia.tech\"", "\"\"")),
                Arguments.of("password", "PASSWORD_REQUIRED", "La contraseña es obligatoria",
                        valido.replace("\"frase secreta larga\"", "\"   \"")));
    }

    @Test
    void variosCamposInvalidosDevuelvenUnElementoPorCampo() throws Exception {
        String camposEnBlanco = cuerpoValido()
                .replace("\"Ana\"", "\"   \"")
                .replace("\"Pérez\"", "\"\"")
                .replace("\"ana@cameia.tech\"", "\" \"");

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(camposEnBlanco))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[?(@.field=='firstName')].code")
                        .value(org.hamcrest.Matchers.contains("FIRST_NAME_REQUIRED")))
                .andExpect(jsonPath("$.errors[?(@.field=='lastName')].code")
                        .value(org.hamcrest.Matchers.contains("LAST_NAME_REQUIRED")))
                .andExpect(jsonPath("$.errors[?(@.field=='email')].code")
                        .value(org.hamcrest.Matchers.contains("EMAIL_REQUIRED")));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    @Test
    void unCampoQueIncumpleDosRestriccionesTieneUnSoloElemento() throws Exception {
        // Una cadena vacía de más de 120 caracteres no existe; para que el mismo campo falle
        // en dos restricciones a la vez se usan 121 espacios: incumple @NotBlank y @Size.
        String nombreLargoEnBlanco = cuerpoValido().replace("\"Ana\"", "\"" + " ".repeat(121) + "\"");

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(nombreLargoEnBlanco))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("firstName"));
    }

    @Test
    void unaFechaConOtroFormatoDaErrorDeFormatoYNoDeMayoriaDeEdad() throws Exception {
        String fechaISO = cuerpoValido().replace("12/04/1995", "1995-04-12");

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(fechaISO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID_FORMAT"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("DD/MM/AAAA")));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    @Test
    void unPronombreFueraDeLaListaSeRechaza() throws Exception {
        String pronombreInvalido = cuerpoValido().replace("\"SHE\"", "\"OTRO\"");

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(pronombreInvalido))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID_FORMAT"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("OTRO"))));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    @Test
    void elCelularSinIndicativoResponde422ConCodigoDeRespaldoYSinElValor() throws Exception {
        // El objeto de valor del celular aún no tiene excepción propia: sale con el código
        // de respaldo y el texto del dominio, nunca con el número recibido.
        when(servicio.register(any(RegisterUserCommand.class)))
                .thenAnswer(invocacion -> new tech.cameia.cuentas.domain.model.PhoneNumber("3109998877"));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoValido()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID_VALUE"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("3109998877"))));
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
