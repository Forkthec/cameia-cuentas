package tech.cameia.cuentas.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.cuentas.application.command.RegisterUserCommand;
import tech.cameia.cuentas.application.service.RegisterUserService;
import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.InvalidBirthDateException;
import tech.cameia.cuentas.domain.exception.InvalidPersonNameException;
import tech.cameia.cuentas.domain.model.PersonName;
import tech.cameia.cuentas.domain.exception.InvalidBirthDateException.Reason;
import tech.cameia.cuentas.domain.exception.WeakPasswordException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.infrastructure.config.JacksonConfiguration;
import tech.cameia.cuentas.presentation.advice.ProblemDetailTestSupport;
import tools.jackson.databind.json.JsonMapper;

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
            .setMessageConverters(convertidorComoLaAplicacion())
            .build();

    /**
     * Lee el JSON con las mismas reglas que la aplicación (enumerados vacíos como ausentes) y
     * escribe los errores igual que ella: el mixin publica {@code code} y {@code requestId}
     * en el nivel superior del documento de error.
     */
    private static JacksonJsonHttpMessageConverter convertidorComoLaAplicacion() {
        JsonMapper.Builder constructor = JsonMapper.builder()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
        JacksonConfiguration.aplicarReglas(constructor);
        return new JacksonJsonHttpMessageConverter(constructor.build());
    }

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
    void cientoVeintiunEspaciosSonUnNombreVacioYNoUnoLargo() throws Exception {
        // Los espacios se recortan antes de validar: el campo queda vacío y solo es obligatorio.
        String nombreLargoEnBlanco = cuerpoValido().replace("\"Ana\"", "\"" + " ".repeat(121) + "\"");

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(nombreLargoEnBlanco))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("firstName"))
                .andExpect(jsonPath("$.errors[0].code").value("FIRST_NAME_REQUIRED"));
    }

    @Test
    void losTextosLleganRecortadosYEnNfcAlCasoDeUso() throws Exception {
        when(servicio.register(any(RegisterUserCommand.class))).thenReturn(cuentaCreada());
        ArgumentCaptor<RegisterUserCommand> comando = ArgumentCaptor.forClass(RegisterUserCommand.class);
        String cuerpo = cuerpoValido()
                .replace("\"Ana\"", "\"  Jose\u0301  \"")
                .replace("\"Pérez\"", "\"\u00A0Gómez  Ruiz\u00A0\"")
                .replace("\"ana@cameia.tech\"", "\"  Ana@Correo.CO \"");

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());

        verify(servicio).register(comando.capture());
        assertThat(comando.getValue().firstName()).isEqualTo("Jos\u00E9");
        assertThat(comando.getValue().lastName()).isEqualTo("Gómez Ruiz");
        // El correo se pasa a minúsculas en el dominio, no en el contrato.
        assertThat(comando.getValue().email()).isEqualTo("Ana@Correo.CO");
    }

    @ParameterizedTest(name = "[{index}] {0} = vacío")
    @MethodSource("textosSoloConEspacios")
    void unTextoQueSoloTieneEspaciosEsObligatorio(String campo, String codigo, String valorJson) throws Exception {
        String cuerpo = cuerpoConCampo(campo, valorJson);

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value(campo))
                .andExpect(jsonPath("$.errors[0].code").value(codigo));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    static Stream<Arguments> textosSoloConEspacios() {
        // El tabulador y el salto de línea van como escapes de JSON; el resto, como el carácter real.
        String[] valores = {"\"\"", "\"   \"", "\"\\t\"", "\"\\n\"", "\"\u00A0\"", "\"\uFEFF\"", "null"};
        Stream.Builder<Arguments> casos = Stream.builder();
        for (String valor : valores) {
            casos.add(Arguments.of("firstName", "FIRST_NAME_REQUIRED", valor));
            casos.add(Arguments.of("lastName", "LAST_NAME_REQUIRED", valor));
            casos.add(Arguments.of("email", "EMAIL_REQUIRED", valor));
        }
        return casos.build();
    }

    @ParameterizedTest(name = "[{index}] {0} con {1} caracteres -> {2}")
    @MethodSource("limitesDeNombreYApellido")
    void elNombreYElApellidoAdmitenExactamente120CaracteresYRechazan121(String campo, int cantidad, int estado,
            String texto) throws Exception {
        when(servicio.register(any(RegisterUserCommand.class))).thenReturn(cuentaCreada());

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConCampo(campo, "\"" + texto + "\"")))
                .andExpect(status().is(estado));
    }

    static Stream<Arguments> limitesDeNombreYApellido() {
        Stream.Builder<Arguments> casos = Stream.builder();
        for (String campo : new String[] {"firstName", "lastName"}) {
            casos.add(Arguments.of(campo, 119, 201, "ñ".repeat(119)));
            casos.add(Arguments.of(campo, 120, 201, "ñ".repeat(120)));
            casos.add(Arguments.of(campo, 121, 422, "ñ".repeat(121)));
            casos.add(Arguments.of(campo, 120, 201, "a".repeat(119) + "e\u0301"));
            casos.add(Arguments.of(campo, 120, 201, "\uD835\uDC9C".repeat(120)));
            casos.add(Arguments.of(campo, 121, 422, "\uD835\uDC9C".repeat(121)));
        }
        return casos.build();
    }

    @Test
    void unNombreDe121CaracteresTieneSuCodigoYSuMensaje() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConCampo("lastName", "\"" + "ñ".repeat(121) + "\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("lastName"))
                .andExpect(jsonPath("$.errors[0].code").value("LAST_NAME_TOO_LONG"));
    }

    @ParameterizedTest(name = "[{index}] correo de {0} caracteres -> {1}")
    @CsvSource({"253, 201", "254, 201", "255, 422"})
    void elCorreoAdmiteExactamente254PuntosDeCodigo(int cantidad, int estado) throws Exception {
        when(servicio.register(any(RegisterUserCommand.class))).thenReturn(cuentaCreada());
        String correo = "a".repeat(cantidad - 6) + "@b.com";

        var resultado = mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConCampo("email", "\"" + correo + "\"")))
                .andExpect(status().is(estado));
        if (estado == 422) {
            resultado.andExpect(jsonPath("$.errors.length()").value(1))
                    .andExpect(jsonPath("$.errors[0].field").value("email"))
                    .andExpect(jsonPath("$.errors[0].code").value("EMAIL_TOO_LONG"))
                    .andExpect(jsonPath("$.errors[0].message").value("El correo no puede superar los 254 caracteres."));
        }
    }

    @Test
    void laContraseniaNoSeRecortaNiSeNormalizaAlLlegarAlCasoDeUso() throws Exception {
        when(servicio.register(any(RegisterUserCommand.class))).thenReturn(cuentaCreada());
        ArgumentCaptor<RegisterUserCommand> comando = ArgumentCaptor.forClass(RegisterUserCommand.class);

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConCampo("password", "\"  frase secreta larga e\u0301  \"")))
                .andExpect(status().isCreated());

        verify(servicio).register(comando.capture());
        assertThat(comando.getValue().password()).isEqualTo("  frase secreta larga e\u0301  ");
    }

    @Test
    void unaContraseniaDeDoceEspaciosEsObligatoria() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConCampo("password", "\"" + " ".repeat(12) + "\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_REQUIRED"));
    }

    @Test
    void variosCamposQueIncumplenReglasDevuelvenUnSoloMensajePorCampo() throws Exception {
        String cuerpo = cuerpoValido()
                .replace("\"Ana\"", "\"   \"")
                .replace("\"Pérez\"", "\"" + "a".repeat(121) + "\"")
                .replace("\"ana@cameia.tech\"", "\"   \"");

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[?(@.field=='firstName')].code")
                        .value(org.hamcrest.Matchers.contains("FIRST_NAME_REQUIRED")))
                .andExpect(jsonPath("$.errors[?(@.field=='lastName')].code")
                        .value(org.hamcrest.Matchers.contains("LAST_NAME_TOO_LONG")))
                .andExpect(jsonPath("$.errors[?(@.field=='email')].code")
                        .value(org.hamcrest.Matchers.contains("EMAIL_REQUIRED")));
    }

    @Test
    void unCorreoLargoSinArrobaSoloReportaLaLongitud() throws Exception {
        // El formato lo decide el dominio después; el borde responde primero por la longitud.
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConCampo("email", "\"" + "a".repeat(255) + "\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value("EMAIL_TOO_LONG"));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    @Test
    void unaFechaIsoSeRechazaPorFormatoEnSuCampo() throws Exception {
        String fechaISO = cuerpoValido().replace("12/04/1995", "1995-04-12");

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(fechaISO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"))
                .andExpect(jsonPath("$.errors[0].code").value("BIRTH_DATE_INVALID_FORMAT"));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"31/02/2000", "29/02/2001", "31/04/2000", "15/13/2000", "1/1/2000", "00/01/2000",
        "01/00/2000", "abc", "2000-01-01", "12-04-1995", "12/04/95", " 12/04/1995", "12/04/1995 "})
    void unaFechaImposibleSeRechazaEnElCampoDeLaFecha(String fecha) throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoConFecha(fecha)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"))
                .andExpect(jsonPath("$.errors[0].code").value("BIRTH_DATE_INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[0].message").value("Formato de fecha inválido."));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "\"birthDate\":\"\"", "\"birthDate\":\"   \"", "\"birthDate\":null", "\"sinFecha\":1"})
    void unaFechaVaciaEnBlancoNulaOAusenteEsObligatoriaYNoDeFormato(String fragmento) throws Exception {
        String cuerpo = cuerpoValido().replace("\"birthDate\":\"12/04/1995\"", fragmento);

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"))
                .andExpect(jsonPath("$.errors[0].code").value("BIRTH_DATE_REQUIRED"));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"20000101", "true", "12.5"})
    void unaFechaQueLlegaComoNumeroOBooleanoSeLeeComoTextoYFallaPorFormato(String valor) throws Exception {
        String cuerpo = cuerpoValido().replace("\"12/04/1995\"", valor);

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"))
                .andExpect(jsonPath("$.errors[0].code").value("BIRTH_DATE_INVALID_FORMAT"));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "{}", "[\"12/04/1995\"]"})
    void unaFechaQueLlegaComoArregloUObjetoEsUnCuerpoIlegible(String valor) throws Exception {
        String cuerpo = cuerpoValido().replace("\"12/04/1995\"", valor);

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors").doesNotExist());

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"29/02/2000", "12/04/1995", "01/01/2000", "29/02/1996"})
    void unaFechaValidaLlegaAlCasoDeUsoComoFecha(String fecha) throws Exception {
        when(servicio.register(any(RegisterUserCommand.class))).thenReturn(cuentaCreada());
        ArgumentCaptor<RegisterUserCommand> comando = ArgumentCaptor.forClass(RegisterUserCommand.class);

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoConFecha(fecha)))
                .andExpect(status().isCreated());

        verify(servicio).register(comando.capture());
        assertThat(comando.getValue().birthDate())
                .isEqualTo(LocalDate.parse(fecha, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"sinPronombre\":1", "\"pronoun\":null", "\"pronoun\":\"\"", "\"pronoun\":\"   \""})
    void faltarElPronombreSeRechazaEnSuCampo(String fragmento) throws Exception {
        String cuerpo = cuerpoValido().replace("\"pronoun\":\"SHE\"", fragmento);

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("pronoun"))
                .andExpect(jsonPath("$.errors[0].code").value("PRONOUN_REQUIRED"))
                .andExpect(jsonPath("$.errors[0].message").value("Selecciona una opción."));

        verify(servicio, never()).register(any(RegisterUserCommand.class));
    }

    @ParameterizedTest
    @EnumSource(Pronoun.class)
    void cadaPronombreValidoLlegaAlCasoDeUso(Pronoun pronombre) throws Exception {
        when(servicio.register(any(RegisterUserCommand.class))).thenReturn(cuentaCreada());
        ArgumentCaptor<RegisterUserCommand> comando = ArgumentCaptor.forClass(RegisterUserCommand.class);

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoValido().replace("\"SHE\"", "\"" + pronombre.name() + "\"")))
                .andExpect(status().isCreated());

        verify(servicio).register(comando.capture());
        assertThat(comando.getValue().pronoun()).isEqualTo(pronombre);
    }

    @ParameterizedTest
    @EnumSource(PersonName.Part.class)
    void unNombreOApellidoConCaracteresNoAdmitidosSeSenialaEnSuCampo(PersonName.Part parte) throws Exception {
        when(servicio.register(any(RegisterUserCommand.class))).thenThrow(new InvalidPersonNameException(parte));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoValido()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value(parte.field()))
                .andExpect(jsonPath("$.errors[0].code").value(parte.code().name()))
                .andExpect(jsonPath("$.errors[0].message").value(parte.message()));
    }

    @Test
    void laContraseniaComunSeSenialaEnSuCampoConElTextoDelCriterio() throws Exception {
        when(servicio.register(any(RegisterUserCommand.class)))
                .thenThrow(new WeakPasswordException(ErrorCode.PASSWORD_TOO_COMMON,
                        "Esta contraseña es demasiado común, elige otra."));

        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoValido()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_TOO_COMMON"))
                .andExpect(jsonPath("$.errors[0].message").value("Esta contraseña es demasiado común, elige otra."));
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

    /**
     * Cuerpo válido con un campo de texto reemplazado.
     *
     * @param campo nombre del campo del contrato
     * @param valorJson valor tal como va en el JSON, con sus comillas o {@code null}
     */
    private String cuerpoConCampo(String campo, String valorJson) {
        return cuerpoValido().replaceFirst("\"" + campo + "\":\"[^\"]*\"",
                java.util.regex.Matcher.quoteReplacement("\"" + campo + "\":" + valorJson));
    }

    private String cuerpoConFecha(String fecha) {
        return cuerpoValido().replace("12/04/1995", fecha);
    }

    private String cuerpoValido() {
        return """
                {"firstName":"Ana","lastName":"Pérez","birthDate":"12/04/1995",
                 "email":"ana@cameia.tech","password":"frase secreta larga",
                 "phoneNumber":"+573001234567","pronoun":"SHE"}
                """;
    }
}
