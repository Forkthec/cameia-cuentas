package tech.cameia.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;

/**
 * Prueba de extremo a extremo de {@code GET /api/v1/users/me}: aplicación completa, PostgreSQL real
 * y el doble en memoria de Firebase.
 *
 * <p>Comprueba lo que las pruebas por capa no pueden: que la fila guardada llega al JSON con el
 * charset correcto, que ningún parámetro de la consulta cambia de cuenta y que las filas anteriores
 * al registro con fecha (sin fecha de nacimiento) se leen y se activan sin error.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
class CurrentAccountEndToEndTest {

    private static final String ROUTE = "/api/v1/users/me";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private TestRestTemplate client;

    @Autowired
    private InMemoryFirebaseUserDirectory directory;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanState() {
        directory.limpiar();
        jdbc.update("DELETE FROM microcuentas.cuenta");
    }

    @Test
    @DisplayName("Devuelve los datos registrados, sin correo, con charset UTF-8 y plan FREE")
    void getMe_shouldReturnStoredAccount_whenRegistered() throws Exception {
        register("""
                {"firstName":"María José","lastName":"Gómez-Ruiz","birthDate":"12/04/1995",
                 "email":"maria@cameia.tech","password":"frase secreta larga",
                 "phoneNumber":"+573001234567","pronoun":"SHE"}""");
        String uid = jdbc.queryForObject("SELECT firebase_uid FROM microcuentas.cuenta", String.class);

        ResponseEntity<String> response = getMe(uid, "");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).containsIgnoringCase("charset=UTF-8");
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.get("firstName").asString()).isEqualTo("María José");
        assertThat(body.get("lastName").asString()).isEqualTo("Gómez-Ruiz");
        assertThat(body.get("birthDate").asString()).isEqualTo("1995-04-12");
        assertThat(body.get("phoneNumber").asString()).isEqualTo("+573001234567");
        assertThat(body.get("pronoun").asString()).isEqualTo("SHE");
        assertThat(body.get("status").asString()).isEqualTo("PENDING_VERIFICATION");
        assertThat(body.get("plan").asString()).isEqualTo("FREE");
        assertThat(body.size()).isEqualTo(8);
        assertThat(response.getBody()).doesNotContain("maria@cameia.tech").doesNotContain(uid);
    }

    @Test
    @DisplayName("Ignora los parámetros de la consulta que apuntan a otra cuenta")
    void getMe_shouldIgnoreQueryIdentity_whenAnotherUidIsSent() throws Exception {
        UUID idA = insert("uid-a", "Ana", "Pérez", "1990-01-01", "ACTIVE");
        UUID idB = insert("uid-b", "Beto", "Ruiz", "1991-02-02", "ACTIVE");

        ResponseEntity<String> response = getMe("uid-a", "?firebase_uid=uid-b&id=" + idB + "&uid=uid-b");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.get("id").asString()).isEqualTo(idA.toString());
        assertThat(body.get("firstName").asString()).isEqualTo("Ana");
        assertThat(response.getBody()).doesNotContain("Beto");
    }

    @Test
    @DisplayName("Una cuenta sin fecha de nacimiento se lee con null y se activa sin error")
    void getMe_shouldReturnBirthDateNull_whenLegacyAccountHasNoBirthDate() throws Exception {
        insert("uid-legacy", "Luz", "Antigua", null, "PENDING_VERIFICATION");

        ResponseEntity<String> read = getMe("uid-legacy", "");

        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = mapper.readTree(read.getBody());
        assertThat(body.has("birthDate")).isTrue();
        assertThat(body.get("birthDate").isNull()).isTrue();

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", "uid-legacy");
        headers.add("X-User-Email-Verified", "true");
        ResponseEntity<String> activation = client.exchange("/api/v1/users/me/verification", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
        assertThat(activation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT estado FROM microcuentas.cuenta WHERE firebase_uid = 'uid-legacy'",
                String.class)).isEqualTo("ACTIVE");
    }

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @CsvSource(delimiter = '|', value = {
        "María José 😀|Núñez Ü|2000-02-29",
        "<script>alert(1)</script>|O'Neil|2000-02-29",
    })
    @DisplayName("Devuelve el texto guardado tal cual, sin interpretarlo")
    void getMe_shouldReturnUnicodeNames_whenStored(String firstName, String lastName, String birthDate)
            throws Exception {
        insert("uid-text", firstName, lastName, birthDate, "ACTIVE");

        ResponseEntity<String> response = getMe("uid-text", "");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.get("firstName").asString()).isEqualTo(firstName);
        assertThat(body.get("lastName").asString()).isEqualTo(lastName);
        assertThat(body.get("birthDate").asString()).isEqualTo(birthDate);
    }

    @Test
    @DisplayName("Una cuenta anonimizada responde 404 ACCOUNT_NOT_FOUND")
    void getMe_shouldReturn404_whenAccountIsAnonymized() {
        insert("uid-gone", "Borrada", "Borrada", null, "ANONYMIZED");

        ResponseEntity<String> response = getMe("uid-gone", "");

        assertProblem(response, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        assertThat(response.getBody()).contains("No encontramos una cuenta para este usuario");
    }

    @Test
    @DisplayName("Una identidad con forma de inyección SQL responde 404 sin eco")
    void getMe_shouldReturn404_whenIdentityLooksLikeSql() {
        insert("uid-a", "Ana", "Pérez", "1990-01-01", "ACTIVE");

        ResponseEntity<String> response = getMe("' OR 1=1 --", "");

        assertProblem(response, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        assertThat(response.getBody()).doesNotContain("OR 1=1").doesNotContain("Ana");
    }

    @Test
    @DisplayName("Una identidad de 129 caracteres responde 400 IDENTITY_REQUIRED")
    void getMe_shouldReturn400_whenIdentityIsTooLong() {
        ResponseEntity<String> response = getMe("u".repeat(129), "");

        assertProblem(response, HttpStatus.BAD_REQUEST, "IDENTITY_REQUIRED");
        assertThat(response.getBody()).contains("La petición no incluye los datos que exige esta ruta");
    }

    @Test
    @DisplayName("Sin X-User-Id responde 400 IDENTITY_REQUIRED")
    void getMe_shouldReturn400_whenIdentityHeaderIsMissing() {
        ResponseEntity<String> response = client.getForEntity(ROUTE, String.class);

        assertProblem(response, HttpStatus.BAD_REQUEST, "IDENTITY_REQUIRED");
    }

    private void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType().toString())
                .startsWith("application/problem+json").containsIgnoringCase("charset=UTF-8");
        assertThat(response.getBody()).contains("\"code\":\"" + code + "\"").contains("\"requestId\"");
    }

    private ResponseEntity<String> getMe(String uid, String query) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", uid);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return client.exchange(ROUTE + query, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void register(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = client.postForEntity("/api/v1/users", new HttpEntity<>(json, headers),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /** Inserta la fila directamente: las cuentas anteriores al registro con fecha no se pueden crear por la API. */
    private UUID insert(String uid, String firstName, String lastName, String birthDate, String status) {
        UUID id = UUID.randomUUID();
        boolean anonymized = "ANONYMIZED".equals(status);
        jdbc.update("""
                INSERT INTO microcuentas.cuenta
                    (id, firebase_uid, nombre, apellido, fecha_nacimiento, pronombres, estado, fecha_eliminacion)
                VALUES (?, ?, ?, ?, CAST(? AS date), ?, ?, CASE WHEN ? THEN now() END)""",
                id, uid, firstName, lastName, birthDate, anonymized ? null : "THEY", status, anonymized);
        return id;
    }
}
