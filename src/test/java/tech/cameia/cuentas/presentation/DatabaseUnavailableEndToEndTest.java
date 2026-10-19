package tech.cameia.cuentas.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;

/**
 * Cada forma de indisponibilidad de la base de datos (pool agotado, conexiones cortadas por el
 * servidor, base que no responde) enviada por un socket real al servicio completo con PostgreSQL real.
 *
 * <p>El pool se reduce a dos conexiones y una espera de 1,5 s para que el agotamiento sea rápido y
 * determinista. La base que no responde se simula pausando el contenedor ({@code docker pause}), que
 * conserva el puerto y deja las conexiones colgadas, a diferencia de detenerlo.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=1500"})
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class DatabaseUnavailableEndToEndTest {

    private static final String PROBLEM_JSON = "application/problem+json;charset=UTF-8";
    private static final String JSON = "Content-Type: application/json";
    private static final long MAX_MILLIS = 3_000;
    private static final String ME = "/api/v1/users/me";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CuttingDirectory directory;

    /**
     * Directorio en memoria que ejecuta una acción justo después de crear la credencial y antes de
     * guardar la cuenta, para cortar la base en ese punto del registro.
     */
    static class CuttingDirectory extends InMemoryFirebaseUserDirectory {

        volatile Runnable beforePlan;

        @Override
        public void assignFreePlanClaim(String firebaseUid) {
            Runnable action = beforePlan;
            if (action != null) {
                action.run();
            }
            super.assignFreePlanClaim(firebaseUid);
        }
    }

    @TestConfiguration
    static class CuttingDirectoryConfiguration {

        @Bean
        @Primary
        CuttingDirectory cuttingDirectory() {
            return new CuttingDirectory();
        }
    }

    @BeforeEach
    void awaitHealthyPool() throws Exception {
        directory.beforePlan = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (true) {
            try {
                jdbc.queryForObject("SELECT 1", Integer.class);
                return;
            } catch (RuntimeException notYet) {
                if (System.nanoTime() > deadline) {
                    throw notYet;
                }
                Thread.sleep(300);
            }
        }
    }

    @AfterEach
    void resumeDatabase() {
        boolean paused = Boolean.TRUE.equals(postgres.getDockerClient()
                .inspectContainerCmd(postgres.getContainerId()).exec().getState().getPaused());
        if (paused) {
            postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();
        }
    }

    private RawHttpClient raw() {
        return new RawHttpClient(port);
    }

    private RawHttpClient.Reply getMe(String uid) throws IOException {
        return raw().send("GET", ME, List.of("X-User-Id: " + uid), null);
    }

    /** Toma todas las conexiones del pool: la siguiente petición espera hasta {@code connection-timeout}. */
    private List<Connection> takeAllConnections() throws SQLException {
        List<Connection> held = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            held.add(dataSource.getConnection());
        }
        return held;
    }

    private static void release(List<Connection> held) throws SQLException {
        for (Connection connection : held) {
            connection.close();
        }
    }

    /** Pausa el contenedor y espera a que las conexiones del pool dejen de parecer recién usadas. */
    private static void pauseDatabase() throws InterruptedException {
        postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();
        // Hikari no comprueba una conexión usada hace menos de 500 ms: sin esta espera la consulta
        // se enviaría a una conexión colgada y no vencería el tiempo de espera del pool.
        Thread.sleep(800);
    }

    private static void assertDatabaseUnavailable(RawHttpClient.Reply reply) {
        assertThat(reply.status()).isEqualTo(503);
        assertThat((String) JsonPath.read(reply.body(), "$.code")).isEqualTo("DATABASE_UNAVAILABLE");
        assertThat((String) JsonPath.read(reply.body(), "$.requestId")).isNotBlank();
        assertThat(reply.headers().get("content-type")).isEqualToIgnoringCase(PROBLEM_JSON);
    }

    @Test
    @DisplayName("Con el pool agotado, GET /users/me responde 503 DATABASE_UNAVAILABLE en menos de 3 s")
    void currentAccount_shouldReturn503_whenPoolIsExhausted() throws Exception {
        List<Connection> held = takeAllConnections();
        try {
            long start = System.nanoTime();
            RawHttpClient.Reply reply = getMe("uid-cm290-a");
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertDatabaseUnavailable(reply);
            assertThat(millis).isLessThan(MAX_MILLIS);
        } finally {
            release(held);
        }
    }

    @Test
    @DisplayName("Con las conexiones cortadas por el servidor, GET /users/me responde 503")
    void currentAccount_shouldReturn503_whenServerTerminatesConnections() throws Exception {
        // Se usan las dos conexiones justo antes del corte: Hikari no revalida las usadas hace menos de 500 ms.
        List<Connection> held = takeAllConnections();
        for (Connection connection : held) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
            }
        }
        release(held);
        terminateBackends();

        assertDatabaseUnavailable(getMe("uid-cm290-a"));
    }

    @Test
    @DisplayName("Con la base en pausa responde 503 y, al reanudarla, vuelve a responder con normalidad")
    void currentAccount_shouldRecover_whenDatabaseComesBack() throws Exception {
        pauseDatabase();
        RawHttpClient.Reply down = getMe("uid-cm290-a");
        postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();

        assertDatabaseUnavailable(down);
        RawHttpClient.Reply back = awaitNot503(() -> getMe("uid-cm290-a"));
        assertThat(back.status()).isEqualTo(404);
        assertThat((String) JsonPath.read(back.body(), "$.code")).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    private static String registrationBody(String email) {
        return """
                {"firstName":"Ana","lastName":"Perez","birthDate":"12/04/1995",
                 "email":"%s","password":"frase secreta larga",
                 "phoneNumber":"+573001234567","pronoun":"SHE"}
                """.formatted(email);
    }

    static List<Arguments> databaseRoutes() {
        return List.of(
                Arguments.of("POST", "/api/v1/users", List.of(JSON), registrationBody("cm290@cameia.tech")),
                Arguments.of("GET", ME, List.of("X-User-Id: uid-cm290-b"), null),
                Arguments.of("POST", "/api/v1/users/me/verification", List.of("X-User-Id: uid-cm290-b"), null));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("databaseRoutes")
    @DisplayName("Con la base caída, las tres rutas que la usan responden 503 DATABASE_UNAVAILABLE")
    void allDatabaseRoutes_shouldReturn503_whenDatabaseIsDown(String method, String path, List<String> headers,
            String body) throws Exception {
        pauseDatabase();

        assertDatabaseUnavailable(raw().send(method, path, headers, body));
    }

    @Test
    @DisplayName("Cerrar sesión en todos los dispositivos no usa la base y responde 204 con la base caída")
    void logoutAll_shouldStayUp_whenDatabaseIsDown() throws Exception {
        pauseDatabase();

        assertThat(raw().send("POST", "/api/v1/auth/logout-all", List.of("X-User-Id: uid-cm290-b"), null).status())
                .isEqualTo(204);
    }

    @Test
    @DisplayName("Un registro con las conexiones cortadas antes de guardar responde 503 y no deja cuenta, evento ni credencial")
    void register_shouldRollBack_whenConnectionsAreTerminatedBeforeCommit() throws Exception {
        directory.limpiar();
        int accountsBefore = count("cuenta");
        int eventsBefore = count("evento_saliente");
        directory.beforePlan = () -> {
            try {
                terminateBackends();
            } catch (SQLException error) {
                throw new IllegalStateException(error);
            }
        };

        RawHttpClient.Reply reply;
        try {
            reply = raw().send("POST", "/api/v1/users", List.of(JSON), registrationBody("rollback@cameia.tech"));
        } finally {
            directory.beforePlan = null;
        }

        assertDatabaseUnavailable(reply);
        awaitHealthyPool();
        assertThat(count("cuenta")).isEqualTo(accountsBefore);
        assertThat(count("evento_saliente")).isEqualTo(eventsBefore);
        assertThat(directory.cantidadDeUsuarios()).isZero();
    }

    @Test
    @DisplayName("La ruta de salud sigue respondiendo 200 con la base caída")
    void health_shouldStayUp_whenDatabaseIsDown() throws Exception {
        pauseDatabase();

        assertThat(raw().send("GET", "/api/v1/users/health", List.of(), null).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Veinte peticiones iguales con el pool agotado responden lo mismo y no escriben nada")
    void currentAccount_shouldReturnSameAnswer_whenRepeated20Times() throws Exception {
        int accountsBefore = count("cuenta");
        int eventsBefore = count("evento_saliente");
        List<Connection> held = takeAllConnections();
        try {
            for (int i = 0; i < 20; i++) {
                assertDatabaseUnavailable(getMe("uid-cm290-a"));
            }
        } finally {
            release(held);
        }

        assertThat(count("cuenta")).isEqualTo(accountsBefore);
        assertThat(count("evento_saliente")).isEqualTo(eventsBefore);
    }

    @Test
    @DisplayName("Treinta peticiones en paralelo con el pool agotado responden 503 sin pasar de 3 s")
    void currentAccount_shouldNotFail_whenManyRequestsRunInParallel() throws Exception {
        List<Connection> held = takeAllConnections();
        ExecutorService threads = Executors.newFixedThreadPool(30);
        try {
            List<Future<long[]>> pending = IntStream.range(0, 30)
                    .mapToObj(i -> threads.submit(() -> timedStatus("uid-cm290-p" + i)))
                    .toList();
            for (Future<long[]> future : pending) {
                long[] result = future.get(20, TimeUnit.SECONDS);
                assertThat(result[0]).isEqualTo(503);
                assertThat(result[1]).isLessThan(MAX_MILLIS);
            }
        } finally {
            threads.shutdownNow();
            release(held);
        }
    }

    @Test
    @DisplayName("La respuesta y el registro no repiten el mensaje de la base ni de la librería")
    void currentAccount_shouldNotEchoDatabaseMessage(CapturedOutput output) throws Exception {
        List<Connection> held = takeAllConnections();
        RawHttpClient.Reply reply;
        try {
            reply = getMe("uid-cm290-a");
        } finally {
            release(held);
        }

        assertDatabaseUnavailable(reply);
        assertThat(reply.body()).doesNotContain("HikariPool").doesNotContain("jdbc")
                .doesNotContain("postgres").doesNotContain("org.");
        String requestId = JsonPath.read(reply.body(), "$.requestId");
        assertThat(output.getOut()).contains("code=DATABASE_UNAVAILABLE, requestId=" + requestId);
    }

    @Test
    @DisplayName("Con el pool agotado, la petición deja una sola línea ERROR y ninguna WARN")
    void currentAccount_shouldLogOneErrorLine_whenPoolIsExhausted(CapturedOutput output) throws Exception {
        int mark = output.getOut().length();
        List<Connection> held = takeAllConnections();
        try {
            assertDatabaseUnavailable(getMe("uid-cm290-a"));
        } finally {
            release(held);
        }

        assertOneErrorLineAndNoWarnings(output, mark);
    }

    @Test
    @DisplayName("Con las conexiones cortadas, la petición deja una sola línea ERROR y ninguna WARN propia")
    void currentAccount_shouldLogOneErrorLine_whenServerTerminatesConnections(CapturedOutput output)
            throws Exception {
        List<Connection> held = takeAllConnections();
        for (Connection connection : held) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
            }
        }
        release(held);
        terminateBackends();
        int mark = output.getOut().length();

        assertDatabaseUnavailable(getMe("uid-cm290-a"));

        assertOneErrorLineAndNoWarnings(output, mark);
    }

    @Test
    @DisplayName("Con la base en pausa, la petición deja una sola línea ERROR y ninguna WARN")
    void currentAccount_shouldLogOneErrorLine_whenDatabaseIsPaused(CapturedOutput output) throws Exception {
        pauseDatabase();
        int mark = output.getOut().length();

        assertDatabaseUnavailable(getMe("uid-cm290-a"));

        assertOneErrorLineAndNoWarnings(output, mark);
    }

    /**
     * Comprueba lo que la petición dejó en el registro desde {@code mark}: una sola línea ERROR (la del
     * manejador, con el código) y ninguna WARN, salvo los avisos de Hikari de que una conexión cortada se
     * descartó, que el pool emite una vez por conexión y no por petición, y que no llevan datos.
     */
    private static void assertOneErrorLineAndNoWarnings(CapturedOutput output, int mark) {
        List<String> lines = output.getOut().substring(mark).lines()
                .filter(line -> line.contains(" ERROR ") || line.contains(" WARN "))
                .filter(line -> !line.contains("com.zaxxer.hikari.pool."))
                .toList();

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains(" ERROR ").contains("code=DATABASE_UNAVAILABLE");
    }

    /** @return estado HTTP y milisegundos que tardó la respuesta */
    private long[] timedStatus(String uid) throws IOException {
        long start = System.nanoTime();
        RawHttpClient.Reply reply = getMe(uid);
        return new long[] {reply.status(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)};
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM microcuentas." + table, Integer.class);
    }

    /** Corta, con una conexión aparte, todas las conexiones que el pool tiene abiertas. */
    private static void terminateBackends() throws SQLException {
        try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()); Statement statement = admin.createStatement()) {
            statement.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                    + "WHERE datname = current_database() AND pid <> pg_backend_pid()");
        }
    }

    /** Repite la petición hasta que deje de responder 503 (Hikari tarda un instante en reponer el pool). */
    private static RawHttpClient.Reply awaitNot503(ThrowingSupplier call) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        RawHttpClient.Reply reply = call.get();
        while (reply.status() == 503 && System.nanoTime() < deadline) {
            Thread.sleep(300);
            reply = call.get();
        }
        return reply;
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        RawHttpClient.Reply get() throws Exception;
    }
}
