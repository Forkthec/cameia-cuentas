package tech.cameia.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;

/**
 * Prueba de concurrencia del registro: dos peticiones idénticas que llegan a la vez.
 *
 * <p>Lo que se garantiza es que nunca se duplica la cuenta ni la credencial, y que ninguna de las
 * dos peticiones termina en un error genérico. No se asume cuál de las dos gana la carrera: una
 * crea la cuenta (201) y la otra recibe la cuenta pendiente (200) o, si la primera todavía no la
 * guardó, el conflicto de correo repetido (409).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
class RegistroConcurrenteEndToEndTest {

    private static final int VUELTAS = 20;

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
    void dosRegistrosSimultaneosNuncaDanError500NiDuplican() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            for (int vuelta = 0; vuelta < VUELTAS; vuelta++) {
                String cuerpo = cuerpoConCorreo("concurrente" + vuelta + "@cameia.tech");

                List<Integer> estados = dosRegistrosALaVez(hilos, cuerpo);

                assertThat(estados).as("vuelta %d", vuelta).containsOnlyOnce(201);
                assertThat(estados).as("vuelta %d", vuelta).doesNotContain(500, 503);
                assertThat(estados.stream().filter(estado -> estado != 201)).as("vuelta %d", vuelta)
                        .allMatch(estado -> estado == 200 || estado == 409);
                assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM microcuentas.cuenta", Integer.class))
                        .as("vuelta %d", vuelta).isEqualTo(vuelta + 1);
                assertThat(directorio.cantidadDeUsuarios()).as("vuelta %d", vuelta).isEqualTo(vuelta + 1);
            }
        } finally {
            hilos.shutdownNow();
        }
    }

    /** Lanza dos registros idénticos soltando a la vez a los dos hilos y devuelve sus estados HTTP. */
    private List<Integer> dosRegistrosALaVez(ExecutorService hilos, String cuerpo) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> pendientes = new ArrayList<>();
        for (int hilo = 0; hilo < 2; hilo++) {
            pendientes.add(hilos.submit(() -> {
                salida.await();
                return registrar(cuerpo);
            }));
        }
        salida.countDown();

        List<Integer> estados = new ArrayList<>();
        for (Future<Integer> pendiente : pendientes) {
            estados.add(pendiente.get(30, TimeUnit.SECONDS));
        }
        return estados;
    }

    private int registrar(String cuerpo) {
        HttpHeaders encabezados = new HttpHeaders();
        encabezados.setContentType(MediaType.APPLICATION_JSON);
        return cliente.postForEntity("/api/v1/users", new HttpEntity<>(cuerpo, encabezados), String.class)
                .getStatusCode().value();
    }

    private String cuerpoConCorreo(String correo) {
        return """
                {"firstName":"Ana","lastName":"Pérez","birthDate":"12/04/1995",
                 "email":"%s","password":"frase secreta larga",
                 "phoneNumber":"+573001234567","pronoun":"SHE"}
                """.formatted(correo);
    }
}
