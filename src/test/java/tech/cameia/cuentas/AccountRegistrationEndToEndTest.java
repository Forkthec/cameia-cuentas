package tech.cameia.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;

/**
 * Prueba de extremo a extremo del registro descrito en
 * {@code specs/CM-14-RegistroUsuario/spec.md}.
 *
 * <p>Recorre el camino completo por HTTP: controlador, caso de uso, políticas del dominio,
 * directorio de usuarios y PostgreSQL real. Lo único simulado es Firebase, que se sustituye
 * por el doble en memoria para no necesitar credenciales.</p>
 *
 * <p>Las pruebas anteriores verifican cada pieza por separado; esta comprueba que
 * encajan: el JSON del formulario, el estado inicial en la tabla y la activación posterior
 * con los encabezados que emite el Gateway.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
class AccountRegistrationEndToEndTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TestRestTemplate cliente;

    @Autowired
    private InMemoryFirebaseUserDirectory directorio;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limpiarEstado() {
        directorio.limpiar();
        jdbcTemplate.update("DELETE FROM microcuentas.cuenta");
    }

    @Test
    void registraLaCuentaYLuegoLaActivaAlVerificarElCorreo() {
        ResponseEntity<String> registro = registrar(cuerpoValido());

        assertThat(registro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registro.getBody()).contains("PENDING_VERIFICATION").contains("FREE");
        assertThat(registro.getHeaders().getContentType().toString()).containsIgnoringCase("charset=UTF-8");

        String uid = uidGuardado();
        assertThat(estadoGuardado()).isEqualTo("PENDING_VERIFICATION");
        assertThat(directorio.planDe(uid)).isEqualTo("FREE");

        HttpHeaders encabezados = new HttpHeaders();
        encabezados.add("X-User-Id", uid);
        encabezados.add("X-User-Email-Verified", "true");
        ResponseEntity<String> activacion = cliente.exchange("/api/v1/users/me/verification",
                HttpMethod.POST, new HttpEntity<>(encabezados), String.class);

        assertThat(activacion.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(estadoGuardado()).isEqualTo("ACTIVE");
    }

    @Test
    void elSegundoRegistroConElMismoCorreoRespondeConflicto() {
        registrar(cuerpoValido());

        ResponseEntity<String> repetido = registrar(cuerpoValido());

        assertThat(repetido.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repetido.getBody()).contains("Este correo ya se encuentra registrado")
                .contains("\"code\":\"EMAIL_ALREADY_REGISTERED\"");
        assertThat(repetido.getHeaders().getContentType()).isNotNull();
        assertThat(repetido.getHeaders().getContentType().toString())
                .startsWith("application/problem+json")
                .containsIgnoringCase("charset=UTF-8");
        assertThat(repetido.getHeaders().getFirst("X-Request-Id")).isNotBlank();
        assertThat(cuentasGuardadas()).isEqualTo(1);
    }

    @Test
    void unMenorDeEdadNoDejaRastroNiEnFirebaseNiEnLaBase() {
        String menor = cuerpoValido().replace("12/04/1995", "12/04/2015");

        ResponseEntity<String> respuesta = registrar(menor);

        // Se compara el número y no la constante: Spring tiene dos para el 422, la nueva
        // UNPROCESSABLE_CONTENT y la antigua UNPROCESSABLE_ENTITY, y no son el mismo objeto.
        assertThat(respuesta.getStatusCode().value()).isEqualTo(422);
        assertThat(respuesta.getBody()).contains("Debes ser mayor de edad");
        assertThat(cuentasGuardadas()).isZero();
    }

    @Test
    void unaFechaImposibleNoDejaRastroNiEnFirebaseNiEnLaBase() {
        ResponseEntity<String> respuesta = registrar(cuerpoValido().replace("12/04/1995", "31/02/2000"));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(422);
        assertThat(respuesta.getBody()).contains("\"code\":\"BIRTH_DATE_INVALID_FORMAT\"")
                .contains("\"field\":\"birthDate\"");
        assertThat(cuentasGuardadas()).isZero();
        assertThat(directorio.cantidadDeUsuarios()).isZero();
    }

    @Test
    void sinPronombreNoDejaRastroNiEnFirebaseNiEnLaBase() {
        ResponseEntity<String> respuesta = registrar(cuerpoValido().replace(",\"pronoun\":\"SHE\"", ""));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(422);
        assertThat(respuesta.getBody()).contains("\"code\":\"PRONOUN_REQUIRED\"").contains("\"field\":\"pronoun\"");
        assertThat(cuentasGuardadas()).isZero();
        assertThat(directorio.cantidadDeUsuarios()).isZero();
    }

    @Test
    void unPronombreVacioSeTrataComoAusenteDePuntaAPunta() {
        // Comprueba que la aplicación real lee "" como ausente, no solo el MockMvc de las pruebas.
        ResponseEntity<String> respuesta = registrar(cuerpoValido().replace("\"SHE\"", "\"\""));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(422);
        assertThat(respuesta.getBody()).contains("\"code\":\"PRONOUN_REQUIRED\"");
        assertThat(cuentasGuardadas()).isZero();
    }

    @ParameterizedTest
    @EnumSource(Pronoun.class)
    void cadaPronombreSeGuardaTalCual(Pronoun pronombre) {
        String cuerpo = cuerpoValido()
                .replace("\"SHE\"", "\"" + pronombre.name() + "\"")
                .replace("ana@cameia.tech", pronombre.name().toLowerCase(java.util.Locale.ROOT) + "@cameia.tech");

        ResponseEntity<String> respuesta = registrar(cuerpo);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbcTemplate.queryForObject("SELECT pronombres FROM microcuentas.cuenta", String.class))
                .isEqualTo(pronombre.name());
    }

    @Test
    void unCorreoConEspaciosYMayusculasSeGuardaNormalizadoYBloqueaElSiguiente() {
        ResponseEntity<String> primero = registrar(cuerpoValido().replace("ana@cameia.tech", "  Ana@Correo.CO "));
        ResponseEntity<String> segundo = registrar(cuerpoValido().replace("ana@cameia.tech", "ana@correo.co"));

        assertThat(primero.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(segundo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(segundo.getBody()).contains("\"code\":\"EMAIL_ALREADY_REGISTERED\"");
        assertThat(directorio.cantidadDeUsuarios()).isEqualTo(1);
        assertThat(cuentasGuardadas()).isEqualTo(1);
    }

    @Test
    void unNombreDe120CaracteresSeGuardaCompleto() {
        ResponseEntity<String> respuesta = registrar(cuerpoValido().replace("\"Ana\"", "\"" + "ñ".repeat(120) + "\""));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbcTemplate.queryForObject("SELECT char_length(nombre) FROM microcuentas.cuenta", Integer.class))
                .isEqualTo(120);
    }

    @Test
    void unNombreEnNfdSeGuardaEnNfcYSinEspaciosSobrantes() {
        ResponseEntity<String> respuesta = registrar(cuerpoValido()
                .replace("\"Ana\"", "\"  José  Luis \"")
                .replace("\"Pérez\"", "\" Pérez \""));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbcTemplate.queryForObject("SELECT nombre FROM microcuentas.cuenta", String.class))
                .isEqualTo("José Luis");
        assertThat(jdbcTemplate.queryForObject("SELECT apellido FROM microcuentas.cuenta", String.class))
                .isEqualTo("Pérez");
    }

    @Test
    void sinVerificarElCorreoLaActivacionSeRechazaYLaCuentaSigueIgual() {
        registrar(cuerpoValido());
        String uid = uidGuardado();

        HttpHeaders encabezados = new HttpHeaders();
        encabezados.add("X-User-Id", uid);
        ResponseEntity<String> activacion = cliente.exchange("/api/v1/users/me/verification",
                HttpMethod.POST, new HttpEntity<>(encabezados), String.class);

        assertThat(activacion.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(estadoGuardado()).isEqualTo("PENDING_VERIFICATION");
    }

    private ResponseEntity<String> registrar(String cuerpo) {
        HttpHeaders encabezados = new HttpHeaders();
        encabezados.setContentType(MediaType.APPLICATION_JSON);
        return cliente.postForEntity("/api/v1/users", new HttpEntity<>(cuerpo, encabezados), String.class);
    }

    private String uidGuardado() {
        return jdbcTemplate.queryForObject("SELECT firebase_uid FROM microcuentas.cuenta", String.class);
    }

    private String estadoGuardado() {
        return jdbcTemplate.queryForObject("SELECT estado FROM microcuentas.cuenta", String.class);
    }

    private Integer cuentasGuardadas() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM microcuentas.cuenta", Integer.class);
    }

    private String cuerpoValido() {
        return """
                {"firstName":"Ana","lastName":"Pérez","birthDate":"12/04/1995",
                 "email":"ana@cameia.tech","password":"frase secreta larga",
                 "phoneNumber":"+573001234567","pronoun":"SHE"}
                """;
    }
}
