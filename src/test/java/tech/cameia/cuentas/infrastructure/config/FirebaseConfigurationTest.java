package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Map;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifica de dónde salen las credenciales de Firebase y que la variable del emulador impide
 * el arranque en un despliegue (REQ-EMU-01, REQ-EMU-02 y REQ-EMU-03 de
 * {@code specs/CM-191-EmuladorFirebaseAuth/spec.md}), además de las guardias de CM-188
 * (REQ-EMC-C03 a REQ-EMC-C05 de {@code specs/CM-188-correcciones/spec.md}).
 *
 * <p>No levanta Spring: usa un {@link MockEnvironment} para fijar {@code FIREBASE_AUTH_EMULATOR_HOST},
 * {@code K_SERVICE} y el perfil sin tocar el entorno del proceso. Cuando la prueba necesita que la
 * variable esté en el entorno del proceso, que es lo único que lee el Admin SDK, la pone en una
 * fuente {@code systemEnvironment} simulada ({@link #withProcessVariables}). Las credenciales por defecto de
 * Google se sustituyen por unas de mentira, de modo que el resultado no depende de que la máquina
 * que corre la prueba tenga o no una llave o {@code gcloud}.</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class FirebaseConfigurationTest {

    private static final String PROJECT_ID = "demo-cameia";
    private static final String REAL_PROJECT_ID = "cameia-2d8b5";
    private static final String EMULATOR_HOST = "localhost:9099";
    private static final String MISSING_KEY_PATH = "/ruta/que/no/existe/llave.json";

    /** {@code FirebaseApp} se guarda a nivel de proceso: hay que borrarlo para no contaminar otra prueba. */
    @AfterEach
    void deleteFirebaseApps() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    /** REQ-EMU-01: con el emulador y sin despliegue se usan credenciales ficticias, no las de Google. */
    @Test
    void emulatorHost_outsideDeployment_usesPlaceholderCredentials() {
        RecordingFirebaseConfiguration configuration = configurationWith("", emulatorInProcess());

        FirebaseApp app = configuration.firebaseApp();

        assertThat(configuration.defaultCredentialsRequests).isZero();
        assertThat(app.getOptions().getProjectId()).isEqualTo(PROJECT_ID);
    }

    /**
     * REQ-EMU-01: con el emulador se ignora la ruta de la llave, aunque tenga valor. Es el caso de
     * Docker, donde la ruta nunca está vacía. Si se leyera, el archivo inexistente haría fallar el
     * arranque.
     */
    @Test
    void emulatorHost_withKeyPath_doesNotReadTheKeyFile() {
        RecordingFirebaseConfiguration configuration = configurationWith(MISSING_KEY_PATH, emulatorInProcess());

        FirebaseApp app = configuration.firebaseApp();

        assertThat(app).isNotNull();
        assertThat(configuration.defaultCredentialsRequests).isZero();
    }

    /** REQ-EMU-02: en Cloud Run (K_SERVICE) la variable impide el arranque y no se inicializa nada. */
    @Test
    void emulatorHost_onCloudRun_failsStartup() {
        RecordingFirebaseConfiguration configuration = configurationWith("",
                new MockEnvironment()
                        .withProperty(FirebaseConfiguration.EMULATOR_HOST_VARIABLE, "localhost:9099")
                        .withProperty("K_SERVICE", "cameia-cuentas"));

        assertThatThrownBy(configuration::firebaseApp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FirebaseConfiguration.EMULATOR_HOST_VARIABLE)
                .hasMessageContaining("despliegue");
        assertThat(FirebaseApp.getApps()).isEmpty();
    }

    /** REQ-EMU-02: con el perfil prod la variable también impide el arranque, aunque falte K_SERVICE. */
    @Test
    void emulatorHost_withProdProfile_failsStartup() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(FirebaseConfiguration.EMULATOR_HOST_VARIABLE, "localhost:9099");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(configurationWith("", environment)::firebaseApp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FirebaseConfiguration.EMULATOR_HOST_VARIABLE);
        assertThat(FirebaseApp.getApps()).isEmpty();
    }

    /** REQ-EMU-02: una variable definida pero vacía también cuenta; no deja pasar el despliegue. */
    @Test
    void emptyEmulatorHost_onCloudRun_failsStartup() {
        RecordingFirebaseConfiguration configuration = configurationWith("",
                new MockEnvironment()
                        .withProperty(FirebaseConfiguration.EMULATOR_HOST_VARIABLE, "")
                        .withProperty("K_SERVICE", "cameia-cuentas"));

        assertThatThrownBy(configuration::firebaseApp).isInstanceOf(IllegalStateException.class);
        assertThat(FirebaseApp.getApps()).isEmpty();
    }

    /** REQ-EMU-03: sin la variable y sin ruta de llave se piden las credenciales por defecto, como siempre. */
    @Test
    void noEmulatorHost_withoutKeyPath_usesDefaultCredentials() {
        RecordingFirebaseConfiguration configuration = configurationWith("", new MockEnvironment());

        configuration.firebaseApp();

        assertThat(configuration.defaultCredentialsRequests).isEqualTo(1);
    }

    /** REQ-EMU-03: sin la variable y con ruta de llave se lee el archivo, como siempre. */
    @Test
    void noEmulatorHost_withKeyPath_readsTheKeyFile() {
        RecordingFirebaseConfiguration configuration = configurationWith(MISSING_KEY_PATH, new MockEnvironment());

        assertThatThrownBy(configuration::firebaseApp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se pudieron leer las credenciales de Firebase");
        assertThat(configuration.defaultCredentialsRequests).isZero();
    }

    /** REQ-EMU-03: una variable vacía fuera de un despliegue no activa el emulador. */
    @Test
    void emptyEmulatorHost_outsideDeployment_usesDefaultCredentials() {
        RecordingFirebaseConfiguration configuration = configurationWith("",
                new MockEnvironment().withProperty(FirebaseConfiguration.EMULATOR_HOST_VARIABLE, ""));

        configuration.firebaseApp();

        assertThat(configuration.defaultCredentialsRequests).isEqualTo(1);
    }

    /**
     * REQ-EMU-03: la ruta normal de producción (Cloud Run, sin la variable) no se ve afectada por la
     * guardia. Es la prueba de que la guardia no rompe el despliegue real.
     */
    @Test
    void noEmulatorHost_onCloudRun_startsWithDefaultCredentials() {
        RecordingFirebaseConfiguration configuration = configurationWith("",
                new MockEnvironment().withProperty("K_SERVICE", "cameia-cuentas"));

        configuration.firebaseApp();

        assertThat(configuration.defaultCredentialsRequests).isEqualTo(1);
        assertThat(FirebaseApp.getApps()).hasSize(1);
    }

    /**
     * REQ-EMU-02 con CM-188: la variable en el entorno del proceso, aunque Spring no la tenga como
     * propiedad propia, también impide el arranque en un despliegue.
     */
    @Test
    void emulatorHost_inProcessEnvironment_onCloudRun_failsStartup() {
        MockEnvironment environment = withProcessVariables(new MockEnvironment(), Map.of(
                FirebaseConfiguration.EMULATOR_HOST_VARIABLE, EMULATOR_HOST, "K_SERVICE", "cameia-cuentas"));

        assertThatThrownBy(configurationWith("", environment)::firebaseApp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("despliegue");
        assertThat(FirebaseApp.getApps()).isEmpty();
    }

    /** REQ-EMC-C04: si la variable solo la ve Spring (-D, argumento o YAML), el arranque falla. */
    @Test
    void emulatorHost_onlyInSpring_failsStartup() {
        RecordingFirebaseConfiguration configuration = configurationWith("",
                new MockEnvironment().withProperty(FirebaseConfiguration.EMULATOR_HOST_VARIABLE, EMULATOR_HOST));

        assertThatThrownBy(configuration::firebaseApp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FirebaseConfiguration.EMULATOR_HOST_VARIABLE)
                .hasMessageContaining("variable de entorno del sistema operativo");
        assertThat(FirebaseApp.getApps()).isEmpty();
    }

    /** REQ-EMC-C04: si Spring y el proceso la tienen con valores distintos, el arranque falla. */
    @Test
    void emulatorHost_differentInSpringAndProcess_failsStartup() {
        MockEnvironment environment = withProcessVariables(
                new MockEnvironment().withProperty(FirebaseConfiguration.EMULATOR_HOST_VARIABLE, "otro-host:9099"),
                Map.of(FirebaseConfiguration.EMULATOR_HOST_VARIABLE, EMULATOR_HOST));

        assertThatThrownBy(configurationWith("", environment)::firebaseApp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("variable de entorno del sistema operativo");
        assertThat(FirebaseApp.getApps()).isEmpty();
    }

    /** REQ-EMC-C03: con el emulador, un ID de proyecto que no es de demostración impide el arranque. */
    @Test
    void emulatorHost_withRealProjectId_failsStartup() {
        RecordingFirebaseConfiguration configuration =
                new RecordingFirebaseConfiguration(REAL_PROJECT_ID, "", emulatorInProcess());

        assertThatThrownBy(configuration::firebaseApp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIREBASE_PROJECT_ID")
                .hasMessageContaining(FirebaseConfiguration.EMULATOR_HOST_VARIABLE)
                .hasMessageContaining(REAL_PROJECT_ID);
        assertThat(FirebaseApp.getApps()).isEmpty();
    }

    /** REQ-EMC-C03: sin el emulador, un ID de proyecto real sigue arrancando como siempre. */
    @Test
    void noEmulatorHost_withRealProjectId_startsWithDefaultCredentials() {
        RecordingFirebaseConfiguration configuration =
                new RecordingFirebaseConfiguration(REAL_PROJECT_ID, "", new MockEnvironment());

        FirebaseApp app = configuration.firebaseApp();

        assertThat(configuration.defaultCredentialsRequests).isEqualTo(1);
        assertThat(app.getOptions().getProjectId()).isEqualTo(REAL_PROJECT_ID);
    }

    /** REQ-EMC-C05: al arrancar en modo emulador, el log anuncia el ID de proyecto. */
    @Test
    void emulatorHost_logsTheProjectId(CapturedOutput output) {
        configurationWith("", emulatorInProcess()).firebaseApp();

        assertThat(output).contains("'" + PROJECT_ID + "'");
    }

    private static RecordingFirebaseConfiguration configurationWith(String keyPath, MockEnvironment environment) {
        return new RecordingFirebaseConfiguration(PROJECT_ID, keyPath, environment);
    }

    /** @return entorno con la variable del emulador en el entorno del proceso, fuera de un despliegue */
    private static MockEnvironment emulatorInProcess() {
        return withProcessVariables(new MockEnvironment(),
                Map.of(FirebaseConfiguration.EMULATOR_HOST_VARIABLE, EMULATOR_HOST));
    }

    /**
     * Añade la fuente {@code systemEnvironment}, la que en ejecución envuelve {@code System.getenv()}.
     *
     * @param environment entorno de prueba
     * @param variables variables de entorno simuladas del sistema operativo
     * @return el mismo entorno, para encadenar
     */
    private static MockEnvironment withProcessVariables(MockEnvironment environment, Map<String, Object> variables) {
        environment.getPropertySources().addLast(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        return environment;
    }

    /** {@link FirebaseConfiguration} que cuenta cuántas veces se piden las credenciales por defecto de Google. */
    private static final class RecordingFirebaseConfiguration extends FirebaseConfiguration {

        private int defaultCredentialsRequests;

        RecordingFirebaseConfiguration(String projectId, String keyPath, MockEnvironment environment) {
            super(projectId, keyPath, environment);
        }

        @Override
        GoogleCredentials defaultCredentials() throws IOException {
            defaultCredentialsRequests++;
            return GoogleCredentials.create(new AccessToken("credenciales-por-defecto-simuladas", null));
        }
    }
}
