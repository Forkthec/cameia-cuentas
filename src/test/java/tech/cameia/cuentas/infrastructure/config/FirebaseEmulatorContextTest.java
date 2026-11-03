package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifica, con el contexto de Spring y la {@link FirebaseConfiguration} real, que la variable del
 * emulador de Firebase Auth solo deja arrancar fuera de un despliegue (REQ-EMU-01 y REQ-EMU-02 de
 * {@code specs/CM-191-EmuladorFirebaseAuth/spec.md}).
 *
 * <p>Complementa a {@link FirebaseConfigurationTest}: allí se prueba la clase sola; aquí, que la
 * guardia corre de verdad cuando Spring construye los beans. Usa {@link ApplicationContextRunner}
 * con solo esta configuración, así que no necesita PostgreSQL ni Testcontainers. La variable del
 * emulador, {@code K_SERVICE} y el perfil se pasan como propiedades, que {@code Environment}
 * resuelve igual que las variables de entorno que inyecta el despliegue.</p>
 */
class FirebaseEmulatorContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FirebaseConfiguration.class)
            .withPropertyValues("cuentas.firebase.project-id=demo-cameia");

    /** {@code FirebaseApp} se guarda a nivel de proceso: hay que borrarlo para no contaminar otra prueba. */
    @AfterEach
    void deleteFirebaseApps() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    /** REQ-EMU-02: con la variable del emulador y K_SERVICE, el contexto no arranca. */
    @Test
    void emulatorHost_onCloudRun_failsStartup() {
        runner.withPropertyValues("FIREBASE_AUTH_EMULATOR_HOST=localhost:9099", "K_SERVICE=cameia-cuentas")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("FIREBASE_AUTH_EMULATOR_HOST")
                            .hasMessageContaining("despliegue");
                });
    }

    /** REQ-EMU-02: con la variable del emulador y el perfil prod, el contexto tampoco arranca. */
    @Test
    void emulatorHost_withProdProfile_failsStartup() {
        runner.withPropertyValues("FIREBASE_AUTH_EMULATOR_HOST=localhost:9099", "spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("FIREBASE_AUTH_EMULATOR_HOST");
                });
    }

    /**
     * REQ-EMU-01: con la variable y sin despliegue, el contexto arranca sin llave de cuenta de
     * servicio ni {@code gcloud}, con la ruta de la llave apuntando a un archivo que no existe (el
     * caso de Docker), y Firebase queda inicializado con el ID de proyecto configurado.
     */
    @Test
    void emulatorHost_outsideDeployment_startsWithoutGoogleCredentials() {
        runner.withPropertyValues("FIREBASE_AUTH_EMULATOR_HOST=localhost:9099",
                        "cuentas.firebase.key-path=/ruta/que/no/existe/llave.json")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FirebaseAuth.class);
                    assertThat(context.getBean(FirebaseApp.class).getOptions().getProjectId())
                            .isEqualTo("demo-cameia");
                });
    }
}
