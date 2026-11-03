package tech.cameia.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tech.cameia.cuentas.application.service.OutboxRelayService;
import tech.cameia.cuentas.application.service.RelaySummary;
import tech.cameia.cuentas.domain.port.EventPublisher;
import tech.cameia.cuentas.domain.port.OutboxRepository;
import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prueba de extremo a extremo del evento {@code cuenta.creada}: el registro por HTTP, PostgreSQL real y RabbitMQ real.
 *
 * <p>Comprueba que la cuenta y su evento nacen juntos, que el mensaje llega al broker con la carga del contrato, que
 * repetir el registro no publica otra vez y que un fallo de publicación no pierde el evento.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Evento cuenta.creada de punta a punta")
class AccountCreatedEventEndToEndTest {

    private static final String QUEUE = "test.cuenta-creada";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String BODY = """
            {"firstName":"Ana","lastName":"Pérez","birthDate":"15/03/2008",
             "email":"  Ana.Perez@Ejemplo.TEST ","password":"una frase muy larga 2026","pronoun":"SHE"}
            """;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static GenericContainer<?> rabbit = new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("cuentas.events.enabled", () -> "true");
        // Estas pruebas comprueban la lógica del relevo, no los tiempos: un plazo holgado evita falsos fallos con la máquina cargada
        registry.add("cuentas.events.publish-timeout", () -> "5s");
    }

    @Autowired
    private TestRestTemplate client;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private TopicExchange accountEventsExchange;

    @Autowired
    private InMemoryFirebaseUserDirectory directory;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @MockitoSpyBean
    private OutboxRepository outbox;

    @MockitoSpyBean
    private EventPublisher publisher;

    @BeforeEach
    void cleanState() {
        Queue queue = new Queue(QUEUE, false, false, false);
        rabbitAdmin.declareQueue(queue);
        Binding binding = BindingBuilder.bind(queue).to(accountEventsExchange).with("cuenta.creada");
        rabbitAdmin.declareBinding(binding);
        rabbitAdmin.purgeQueue(QUEUE, false);
        directory.limpiar();
        jdbc.update("DELETE FROM microcuentas.evento_saliente");
        jdbc.update("DELETE FROM microcuentas.cuenta");
    }

    @Test
    @DisplayName("publica cuenta.creada con la carga del contrato cuando la cuenta es nueva")
    void register_shouldPublishAccountCreated_whenAccountIsNew() throws Exception {
        ResponseEntity<String> response = register("req-e2e-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String firebaseUid = JSON.readTree(response.getBody()).get("firebaseUid").asString();
        Message message = rabbitTemplate.receive(QUEUE, 5000);
        assertThat(message).isNotNull();
        JsonNode payload = JSON.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        assertThat(payload.get("usuarioId").asString()).isEqualTo(firebaseUid);
        assertThat(payload.get("email").asString()).isEqualTo("ana.perez@ejemplo.test");
        assertThat(payload.get("fechaNacimiento").asString()).isEqualTo("2008-03-15");
        assertThat(payload.get("creadaEn").asString()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$");
        assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo("req-e2e-1");
        assertThat(jdbc.queryForObject(
                "SELECT fecha_publicacion IS NOT NULL AND carga IS NULL FROM microcuentas.evento_saliente", Boolean.class))
                .isTrue();
    }

    @Test
    @DisplayName("no escribe en el log ningún dato personal del evento")
    void register_shouldNotLogPersonalData_whenEventIsPublished(CapturedOutput output) {
        register("req-e2e-6");
        assertThat(rabbitTemplate.receive(QUEUE, 5000)).isNotNull();

        // Ningún dato personal del evento debe quedar en el log
        assertThat(output.getAll()).doesNotContain("ana.perez@ejemplo.test", "Ana.Perez", "2008-03-15", "15/03/2008");
    }

    @Test
    @DisplayName("publica un solo evento aunque se repita el registro veinte veces")
    void register_shouldPublishOnce_whenRepeatedTwentyTimes() {
        assertThat(register("req-e2e-rep-0").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        for (int repetition = 1; repetition <= 20; repetition++) {
            assertThat(register("req-e2e-rep-" + repetition).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        assertThat(rabbitTemplate.receive(QUEUE, 5000)).isNotNull();
        assertThat(rabbitTemplate.receive(QUEUE, 1000)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM microcuentas.evento_saliente", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("no publica otra vez cuando se repite el registro")
    void register_shouldNotPublishAgain_whenRegistrationIsRepeated() {
        ResponseEntity<String> first = register("req-e2e-2");
        assertThat(rabbitTemplate.receive(QUEUE, 5000)).isNotNull();

        ResponseEntity<String> second = register("req-e2e-3");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rabbitTemplate.receive(QUEUE, 2000)).isNull();
    }

    @Test
    @DisplayName("no deja cuenta, credencial ni mensaje cuando el evento no se puede guardar")
    void register_shouldLeaveNoAccountNorEvent_whenEventCannotBeStored() {
        doThrow(new IllegalStateException("fallo simulado")).when(outbox).appendAccountCreated(any());

        ResponseEntity<String> response = register("req-e2e-4");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("INTERNAL_ERROR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM microcuentas.cuenta", Integer.class)).isZero();
        assertThat(directory.cantidadDeUsuarios()).isZero();
        assertThat(rabbitTemplate.receive(QUEUE, 1000)).isNull();
    }

    @RepeatedTest(5)
    @DisplayName("publica el pendiente con el relevo cuando la publicación inmediata no se confirmó")
    void relayPending_shouldPublish_whenBrokerWasDownDuringRegistration() {
        doReturn(false).doCallRealMethod().when(publisher).publish(any());

        ResponseEntity<String> response = register("req-e2e-5");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject("SELECT intentos FROM microcuentas.evento_saliente WHERE fecha_publicacion IS NULL",
                Integer.class)).isEqualTo(1);
        assertThat(rabbitTemplate.receive(QUEUE, 1000)).isNull();

        RelaySummary summary = outboxRelayService.relayPending();

        assertThat(summary).isEqualTo(new RelaySummary(1, 0, 0));
        assertThat(rabbitTemplate.receive(QUEUE, 5000)).isNotNull();
    }

    @RepeatedTest(5)
    @DisplayName("publica el pendiente con el relevo cuando el proceso cayó después de confirmar la cuenta")
    void relayPending_shouldPublish_whenProcessStopsAfterCommit() {
        doThrow(new IllegalStateException("caída simulada del proceso")).doCallRealMethod().when(publisher).publish(any());

        ResponseEntity<String> response = register("req-e2e-7");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(rabbitTemplate.receive(QUEUE, 1000)).isNull();

        RelaySummary summary = outboxRelayService.relayPending();

        assertThat(summary).isEqualTo(new RelaySummary(1, 0, 0));
        assertThat(rabbitTemplate.receive(QUEUE, 5000)).isNotNull();
    }

    private ResponseEntity<String> register(String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Request-Id", requestId);
        return client.postForEntity("/api/v1/users", new HttpEntity<>(BODY, headers), String.class);
    }
}
