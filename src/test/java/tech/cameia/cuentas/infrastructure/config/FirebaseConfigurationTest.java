package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifica de dónde salen las credenciales de Firebase y que la variable del emulador impide
 * el arranque en un despliegue (REQ-EMU-01, REQ-EMU-02 y REQ-EMU-03 de
 * {@code specs/CM-191-EmuladorFirebaseAuth/spec.md}).
 *
 * <p>No levanta Spring: usa un {@link MockEnvironment} para fijar {@code FIREBASE_AUTH_EMULATOR_HOST},
 * {@code K_SERVICE} y el perfil sin tocar el entorno del proceso. Las credenciales por defecto de
 * Google se sustituyen por unas de mentira, de modo que el resultado no depende de que la máquina
 * que corre la prueba tenga o no una llave o {@code gcloud}.</p>
 */
class FirebaseConfigurationTest {

    private static final String PROJECT_ID = "demo-cameia";
    private static final String MISSING_KEY_PATH = "/ruta/que/no/existe/llave.json";

    /** {@code FirebaseApp} se guarda a nivel de proceso: hay que borrarlo para no contaminar otra prueba. */
    @AfterEach
    void deleteFirebaseApps() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    /** REQ-EMU-01: con el emulador y sin despliegue se usan credenciales ficticias, no las de Google. */
    @Test
    void emulatorHost_outsideDeployment_usesPlaceholderCredentials() {
        RecordingFirebaseConfiguration configuration = configurationWith("",
                new MockEnvironment().withProperty(FirebaseConfiguration.EMULATOR_HOST_VARIABLE, "localhost:9099"));

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
        RecordingFirebaseConfiguration configuration = configurationWith(MISSING_KEY_PATH,
                new MockEnvironment().withProperty(FirebaseConfiguration.EMULATOR_HOST_VARIABLE, "localhost:9099"));

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

    /** Toda llamada a Firebase tiene tiempo de espera: 5 s para conectar y 10 s para cada respuesta. */
    @Test
    void firebaseCallsHaveConnectAndReadTimeouts() {
        FirebaseApp app = configurationWith("", new MockEnvironment()).firebaseApp();

        assertThat(app.getOptions().getConnectTimeout()).isEqualTo(5_000);
        assertThat(app.getOptions().getReadTimeout()).isEqualTo(10_000);
    }

    /** Con el emulador rigen los mismos tiempos de espera: así se prueba en local lo que corre desplegado. */
    @Test
    void emulatorHost_keepsTheSameTimeouts() {
        FirebaseApp app = configurationWith("",
                new MockEnvironment().withProperty(FirebaseConfiguration.EMULATOR_HOST_VARIABLE, "localhost:9099"))
                .firebaseApp();

        assertThat(app.getOptions().getConnectTimeout()).isEqualTo(FirebaseConfiguration.CONNECT_TIMEOUT_MS);
        assertThat(app.getOptions().getReadTimeout()).isEqualTo(FirebaseConfiguration.READ_TIMEOUT_MS);
    }

    private static RecordingFirebaseConfiguration configurationWith(String keyPath, Environment environment) {
        return new RecordingFirebaseConfiguration(keyPath, environment);
    }

    /** {@link FirebaseConfiguration} que cuenta cuántas veces se piden las credenciales por defecto de Google. */
    private static final class RecordingFirebaseConfiguration extends FirebaseConfiguration {

        private int defaultCredentialsRequests;

        RecordingFirebaseConfiguration(String keyPath, Environment environment) {
            super(PROJECT_ID, keyPath, environment);
        }

        @Override
        GoogleCredentials defaultCredentials() throws IOException {
            defaultCredentialsRequests++;
            return GoogleCredentials.create(new AccessToken("credenciales-por-defecto-simuladas", null));
        }
    }
}
