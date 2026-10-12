package tech.cameia.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.cuentas.application.service.PurgeUnverifiedAccountsService;
import tech.cameia.cuentas.domain.model.PurgeSummary;
import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;

/**
 * Prueba de extremo a extremo de la purga de cuentas sin verificar: registro por HTTP, envejecimiento de la fila, purga
 * sobre PostgreSQL real y nuevo registro con el mismo correo. Lo único simulado es Firebase.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
class PurgeEndToEndTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TestRestTemplate client;

    @Autowired
    private InMemoryFirebaseUserDirectory directory;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PurgeUnverifiedAccountsService purge;

    @BeforeEach
    void cleanState() {
        directory.limpiar();
        jdbc.update("DELETE FROM microcuentas.evento_saliente");
        jdbc.update("DELETE FROM microcuentas.cuenta");
    }

    @Test
    @DisplayName("Una cuenta de 169 horas sin verificar se borra con su credencial y su evento, y el correo queda libre")
    void purge_shouldDeleteAccountCredentialAndRecordEvent_whenPendingForMoreThan168Hours() {
        register("purga@correo.co");
        String uid = onlyUid();
        age(uid, 169);

        PurgeSummary summary = purge.purge();

        assertThat(summary.deleted()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM microcuentas.cuenta WHERE firebase_uid = ?", uid)).isZero();
        assertThat(directory.existe(uid)).isFalse();
        assertThat(count("""
                SELECT count(*) FROM microcuentas.evento_saliente
                 WHERE tipo = 'cuenta.eliminada' AND agregado_id = ? AND fecha_publicacion IS NULL
                """, uid)).isEqualTo(1);
        assertThat(register("purga@correo.co").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Una cuenta de 167 horas se conserva")
    void purge_shouldKeepAccount_whenPendingForLessThan168Hours() {
        register("reciente@correo.co");
        String uid = onlyUid();
        age(uid, 167);

        PurgeSummary summary = purge.purge();

        assertThat(summary.reviewed()).isZero();
        assertThat(count("SELECT count(*) FROM microcuentas.cuenta WHERE firebase_uid = ?", uid)).isEqualTo(1);
    }

    @Test
    @DisplayName("Dos purgas seguidas: la segunda no encuentra nada")
    void purge_shouldFindNothing_whenRunTwice() {
        register("purga@correo.co");
        age(onlyUid(), 169);

        purge.purge();
        PurgeSummary second = purge.purge();

        assertThat(second.reviewed()).isZero();
    }

    @Test
    @DisplayName("Una cuenta vieja cuyo correo ya se verificó se activa y no genera evento")
    void purge_shouldActivateAccount_whenCredentialIsVerified() {
        register("verificada@correo.co");
        String uid = onlyUid();
        age(uid, 169);
        directory.marcarCorreoVerificado(uid);

        PurgeSummary summary = purge.purge();

        assertThat(summary.activated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM microcuentas.cuenta WHERE firebase_uid = ?", String.class, uid))
                .isEqualTo("ACTIVE");
        assertThat(count("SELECT count(*) FROM microcuentas.evento_saliente WHERE tipo = 'cuenta.eliminada'", null)).isZero();
        assertThat(directory.existe(uid)).isTrue();
    }

    private ResponseEntity<String> register(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"firstName":"Ana","lastName":"Pérez","birthDate":"12/04/1995",
                 "email":"%s","password":"frase secreta larga",
                 "phoneNumber":"+573001234567","pronoun":"SHE"}
                """.formatted(email);
        return client.postForEntity("/api/v1/users", new HttpEntity<>(body, headers), String.class);
    }

    private String onlyUid() {
        return jdbc.queryForObject("SELECT firebase_uid FROM microcuentas.cuenta", String.class);
    }

    private void age(String uid, int hours) {
        jdbc.update("""
                UPDATE microcuentas.cuenta
                   SET fecha_creacion = now() - make_interval(hours => ?),
                       fecha_actualizacion = now() - make_interval(hours => ?)
                 WHERE firebase_uid = ?
                """, hours, hours, uid);
    }

    private int count(String sql, String parameter) {
        return parameter == null
                ? jdbc.queryForObject(sql, Integer.class)
                : jdbc.queryForObject(sql, Integer.class, parameter);
    }
}
