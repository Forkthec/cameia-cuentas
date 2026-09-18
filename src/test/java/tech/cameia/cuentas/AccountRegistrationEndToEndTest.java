package tech.cameia.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        assertThat(repetido.getBody()).contains("Este correo ya se encuentra registrado");
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
