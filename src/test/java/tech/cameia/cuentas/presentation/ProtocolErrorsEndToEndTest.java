package tech.cameia.cuentas.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Peticiones que un navegador normal no produce (formularios mal codificados, encabezados con
 * caracteres de control, métodos raros) enviadas por un socket real al servicio completo.
 *
 * <p>Cada caso comprueba que la respuesta sale con la forma común del servicio: estado, {@code code},
 * {@code requestId} y {@code application/problem+json} con {@code charset}; nunca un {@code 500} sin
 * formato ni la página por defecto de Spring o de Tomcat.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class ProtocolErrorsEndToEndTest {

    private static final String FORM = "Content-Type: application/x-www-form-urlencoded";
    private static final String PROBLEM_JSON = "application/problem+json;charset=UTF-8";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private RawHttpClient raw() {
        return new RawHttpClient(port);
    }

    private static String codeOf(RawHttpClient.Reply reply) {
        return JsonPath.read(reply.body(), "$.code");
    }

    private static void assertCommonShape(RawHttpClient.Reply reply, int status, String code) {
        assertThat(reply.status()).isEqualTo(status);
        assertThat(codeOf(reply)).isEqualTo(code);
        assertThat((String) JsonPath.read(reply.body(), "$.requestId")).isNotBlank();
        assertThat(reply.headers().get("content-type")).isEqualToIgnoringCase(PROBLEM_JSON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
    @DisplayName("Un formulario mal codificado en un método no admitido responde 405")
    void formMethod_shouldReturn405_whenFormIsMalformed(String method) throws IOException {
        RawHttpClient.Reply reply = raw().send(method, "/api/v1/users", List.of(FORM), "a=%zz");

        assertCommonShape(reply, 405, "METHOD_NOT_ALLOWED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
    @DisplayName("Un formulario mal codificado en una ruta inexistente responde 404")
    void formMethod_shouldReturn404_whenRouteDoesNotExist(String method) throws IOException {
        RawHttpClient.Reply reply = raw().send(method, "/ruta-inexistente", List.of(FORM), "a=%zz");

        assertCommonShape(reply, 404, "ROUTE_NOT_FOUND");
    }

    @Test
    @DisplayName("Un formulario bien formado en el registro sigue respondiendo 415")
    void register_shouldStillReturn415_whenFormIsWellFormed() throws IOException {
        RawHttpClient.Reply reply = raw().send("POST", "/api/v1/users", List.of(FORM), "a=1");

        assertCommonShape(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("Un JSON mal formado en el registro sigue respondiendo 422")
    void register_shouldStillReturn422_whenJsonIsMalformed() throws IOException {
        RawHttpClient.Reply reply = raw().send("POST", "/api/v1/users",
                List.of("Content-Type: application/json"), "{\"a\":");

        assertCommonShape(reply, 422, "REQUEST_BODY_INVALID_FORMAT");
    }

    @Test
    @DisplayName("Las rutas sin cuerpo conservan su respuesta con un formulario mal codificado")
    void noBodyRoutes_shouldKeepTheirAnswer_whenFormIsMalformed() throws IOException {
        RawHttpClient.Reply logout = raw().send("POST", "/api/v1/auth/logout-all",
                List.of(FORM, "X-User-Id: uid-cm335-a"), "a=%zz");
        RawHttpClient.Reply verification = raw().send("POST", "/api/v1/users/me/verification",
                List.of(FORM, "X-User-Id: uid-cm335-a"), "a=%zz");

        assertThat(logout.status()).isEqualTo(204);
        assertCommonShape(verification, 404, "ACCOUNT_NOT_FOUND");
    }

    @Test
    @DisplayName("Un formulario mal codificado en el registro responde 415")
    void register_shouldReturn415_whenFormIsMalformed() throws IOException {
        RawHttpClient.Reply reply = raw().send("POST", "/api/v1/users", List.of(FORM), "a=%zz");

        assertCommonShape(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
        assertThat((String) JsonPath.read(reply.body(), "$.detail")).isEqualTo("Tipo de contenido no admitido.");
        assertThat(reply.body()).doesNotContain("zz");
    }

    @Test
    @DisplayName("Un formulario con el cuerpo vacío en el registro responde 415")
    void register_shouldReturn415_whenFormBodyIsEmpty() throws IOException {
        RawHttpClient.Reply reply = raw().send("POST", "/api/v1/users", List.of(FORM), "");

        assertCommonShape(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
    }

    @Test
    @DisplayName("Repetir 20 veces el formulario mal codificado da la misma respuesta y no crea cuentas")
    void register_shouldReturnSameAnswer_whenRepeated20Times() throws IOException {
        Long before = jdbc.queryForObject("SELECT count(*) FROM microcuentas.cuenta", Long.class);

        for (int i = 0; i < 20; i++) {
            assertCommonShape(raw().send("POST", "/api/v1/users", List.of(FORM), "a=%zz"), 415,
                    "MEDIA_TYPE_NOT_ALLOWED");
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM microcuentas.cuenta", Long.class)).isEqualTo(before);
    }

    @Test
    @DisplayName("50 formularios mal codificados en paralelo responden todos 415")
    void register_shouldNotFail_whenManyMalformedRequestsRunInParallel() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(50);
        try {
            List<Future<RawHttpClient.Reply>> replies = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                replies.add(pool.submit(() -> raw().send("POST", "/api/v1/users", List.of(FORM), "a=%zz")));
            }
            for (Future<RawHttpClient.Reply> reply : replies) {
                assertCommonShape(reply.get(30, TimeUnit.SECONDS), 415, "MEDIA_TYPE_NOT_ALLOWED");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("El valor del formulario no se devuelve ni se registra")
    void register_shouldNotEchoOrLogValue(CapturedOutput output) throws IOException {
        RawHttpClient.Reply reply = raw().send("POST", "/api/v1/users", List.of(FORM), "a=%zzSECRETO");

        assertCommonShape(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
        assertThat(reply.body()).doesNotContain("SECRETO");
        assertThat(output.getAll()).contains("WARN", "status=415", "code=MEDIA_TYPE_NOT_ALLOWED", "requestId=")
                .doesNotContain("SECRETO");
    }

    @Test
    @DisplayName("Un multipart roto se registra como WARN con su código y sin el texto de la excepción")
    void register_shouldLogOnlyCode_whenMultipartIsMalformed(CapturedOutput output) throws IOException {
        RawHttpClient.Reply reply = raw().send("POST", "/api/v1/users",
                List.of("Content-Type: multipart/form-data"), "SECRETO");

        assertCommonShape(reply, 415, "REQUEST_MULTIPART_INVALID_FORMAT");
        assertThat(output.getAll()).contains("WARN", "status=415", "code=REQUEST_MULTIPART_INVALID_FORMAT",
                "requestId=").doesNotContain("SECRETO").doesNotContain("no multipart boundary");
    }

    @Test
    @DisplayName("Un formulario de 3 MB en el registro responde 415 sin crear cuentas")
    void register_shouldReturn415_whenFormIs3Megabytes() throws IOException {
        Long before = jdbc.queryForObject("SELECT count(*) FROM microcuentas.cuenta", Long.class);

        RawHttpClient.Reply reply = raw().send("POST", "/api/v1/users", List.of(FORM),
                "a=" + "x".repeat(3 * 1024 * 1024));

        assertCommonShape(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM microcuentas.cuenta", Long.class)).isEqualTo(before);
    }

    @Test
    @DisplayName("Un formulario con 10 050 parámetros en el registro responde 415")
    void register_shouldReturn415_whenFormHasMoreParametersThanTheContainerAllows() throws IOException {
        StringBuilder form = new StringBuilder();
        for (int i = 0; i < 10_050; i++) {
            form.append(i == 0 ? "" : "&").append('p').append(i).append("=1");
        }

        RawHttpClient.Reply reply = raw().send("POST", "/api/v1/users", List.of(FORM), form.toString());

        assertCommonShape(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "POST /api/v1/users", "POST /api/v1/users/me/verification", "POST /api/v1/auth/logout-all",
        "GET /api/v1/users/me", "GET /api/v1/users/health", "PUT /api/v1/users", "GET /ruta-inexistente"})
    @DisplayName("Un multipart sin boundary responde 415 con su código propio en cualquier ruta")
    void route_shouldReturn415_whenMultipartHasNoBoundary(String routeSpec) throws IOException {
        String[] parts = routeSpec.split(" ");

        RawHttpClient.Reply reply = raw().send(parts[0], parts[1],
                List.of("Content-Type: multipart/form-data", "X-User-Id: uid-cm335-a"), "x");

        assertCommonShape(reply, 415, "REQUEST_MULTIPART_INVALID_FORMAT");
    }

    @Test
    @DisplayName("Un Accept ilegible responde 406 con cuerpo")
    void accept_shouldReturn406WithBody_whenAcceptIsUnreadable() throws IOException {
        RawHttpClient.Reply reply = raw().send("GET", "/api/v1/users/health", List.of("Accept: ;;;"), null);

        assertCommonShape(reply, 406, "MEDIA_TYPE_NOT_ACCEPTABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {";;;", "*/*;q=abc"})
    @DisplayName("Un Accept ilegible no quita el cuerpo a un 422 de validación")
    void accept_shouldKeepBody_whenValidationFailsWithUnreadableAccept(String accept) throws IOException {
        RawHttpClient.Reply reply = raw().send("POST", "/api/v1/users",
                List.of("Content-Type: application/json", "Accept: " + accept), "{}");

        assertCommonShape(reply, 422, "VALIDATION_FAILED");
        assertThat((Object) JsonPath.read(reply.body(), "$.errors")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {";;;", "*/*;q=abc"})
    @DisplayName("Un Accept ilegible no quita el cuerpo a un 415 de tipo de contenido")
    void accept_shouldKeepBody_whenContentTypeIsNotAllowedWithUnreadableAccept(String accept) throws IOException {
        RawHttpClient.Reply reply = raw().send("POST", "/api/v1/users",
                List.of("Content-Type: text/plain", "Accept: " + accept), "x");

        assertCommonShape(reply, 415, "MEDIA_TYPE_NOT_ALLOWED");
    }

    @ParameterizedTest
    @ValueSource(strings = {";;;", "*/*;q=abc"})
    @DisplayName("Un Accept ilegible no quita el cuerpo a un 404 de ruta inexistente")
    void accept_shouldKeepBody_whenRouteDoesNotExistWithUnreadableAccept(String accept) throws IOException {
        RawHttpClient.Reply reply = raw().send("GET", "/ruta-inexistente", List.of("Accept: " + accept), null);

        assertCommonShape(reply, 404, "ROUTE_NOT_FOUND");
    }

    @Test
    @DisplayName("TRACE responde 405 con la forma común")
    void trace_shouldReturn405WithCommonShape() throws IOException {
        RawHttpClient.Reply reply = raw().send("TRACE", "/api/v1/users/me", List.of(), null);

        assertCommonShape(reply, 405, "METHOD_NOT_ALLOWED");
        assertThat(reply.headers()).containsKey("x-request-id");
    }

    @Test
    @DisplayName("TRACE conserva el encabezado Allow que envía el contenedor")
    void trace_shouldKeepAllowHeader_whenContainerSentIt() throws IOException {
        RawHttpClient.Reply reply = raw().send("TRACE", "/api/v1/users/me", List.of(), null);

        assertThat(reply.status()).isEqualTo(405);
        assertThat(reply.headers().get("allow")).isNotBlank();
    }

    @Test
    @DisplayName("Pedir /error directamente responde 404 con la forma común")
    void error_shouldReturnCommonShape_whenRouteIsRequestedDirectly() throws IOException {
        RawHttpClient.Reply reply = raw().send("GET", "/error", List.of(), null);

        assertCommonShape(reply, 404, "ROUTE_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET /api/v1/users/me", "POST /api/v1/users/me/verification", "POST /api/v1/auth/logout-all"})
    @DisplayName("Una identidad con un tabulador responde 400 con IDENTITY_REQUIRED")
    void identity_shouldReturn400_whenHeaderHasTab(String routeSpec) throws IOException {
        String[] parts = routeSpec.split(" ");

        RawHttpClient.Reply reply = raw().send(parts[0], parts[1], List.of("X-User-Id: uid\tx"), null);

        assertCommonShape(reply, 400, "IDENTITY_REQUIRED");
    }

    @Test
    @DisplayName("Una identidad con un control C1 (0x85) responde 400 con IDENTITY_REQUIRED")
    void identity_shouldReturn400_whenHeaderHasC1Control() throws IOException {
        RawHttpClient.Reply reply = raw().send("GET", "/api/v1/users/me", List.of("X-User-Id: uid\u0085x"), null);

        assertCommonShape(reply, 400, "IDENTITY_REQUIRED");
    }
}
