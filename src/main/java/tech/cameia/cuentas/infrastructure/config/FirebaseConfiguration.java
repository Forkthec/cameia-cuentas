package tech.cameia.cuentas.infrastructure.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;

import tech.cameia.cuentas.domain.port.FirebaseUserDirectory;
import tech.cameia.cuentas.infrastructure.client.FirebaseUserDirectoryAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Inicializa el Firebase Admin SDK, que es quien custodia las credenciales de los usuarios.
 *
 * <p>Falla en el arranque si las credenciales no son válidas. Es deliberado: un
 * microservicio de cuentas que arranca sin poder hablar con Firebase aceptaría registros
 * que nunca podría completar, y el fallo aparecería recién en la primera petición de un
 * usuario real.</p>
 *
 * <p>En desarrollo las credenciales salen del archivo JSON que indica
 * {@code FIREBASE_KEY_PATH}. En Cloud Run no hay archivo: las Application Default
 * Credentials vienen del servidor de metadatos con la identidad de la cuenta de servicio,
 * así que basta con no definir esa variable.</p>
 *
 * <p>Con {@code cuentas.firebase.enabled=false} no se crea ningún bean. Esa es la vía por
 * la que las pruebas corren sin credenciales.</p>
 *
 * <p><b>Emulador de Firebase Auth.</b> Con {@code FIREBASE_AUTH_EMULATOR_HOST} definida, el
 * Admin SDK habla con el emulador local en vez de con Google: detecta la variable por sí solo,
 * leyéndola del entorno del proceso. Aun así {@link FirebaseOptions} exige unas credenciales
 * no nulas, así que aquí se le entregan unas ficticias, y se comprueba antes que
 * {@code FIREBASE_KEY_PATH}: dentro de Docker esa ruta nunca está vacía (apunta a un archivo
 * vacío si no hay llave real) y leerla fallaría. El emulador emite tokens sin firma y el SDK
 * los acepta mientras la variable esté definida. Por eso, en un despliegue (Cloud Run, o perfil
 * {@code prod}) la variable impide el arranque: Cuentas no puede ignorarla, porque el SDK la
 * lee directamente del entorno.</p>
 */
@Configuration
@ConditionalOnProperty(name = "cuentas.firebase.enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseConfiguration {

    /** Variable de entorno con la dirección del emulador de Firebase Auth. */
    static final String EMULATOR_HOST_VARIABLE = "FIREBASE_AUTH_EMULATOR_HOST";

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfiguration.class);

    /** Nombre de la instancia, para no depender de la aplicación por defecto del SDK. */
    private static final String APP_NAME = "cameia-cuentas";

    /** Variable que Cloud Run inyecta en cada servicio desplegado. */
    private static final String CLOUD_RUN_SERVICE_VARIABLE = "K_SERVICE";

    private static final String DEPLOY_PROFILE = "prod";

    private final String projectId;
    private final String keyPath;
    private final Environment environment;

    /**
     * Recibe la configuración del proyecto de Firebase.
     *
     * @param projectId identificador del proyecto en Firebase Console
     * @param keyPath ruta al JSON de la cuenta de servicio; vacío en Cloud Run, donde se
     *                usan las credenciales por defecto del entorno
     * @param environment entorno de Spring, del que se leen la variable del emulador,
     *                    {@code K_SERVICE} y los perfiles activos
     */
    public FirebaseConfiguration(
            @Value("${cuentas.firebase.project-id}") String projectId,
            @Value("${cuentas.firebase.key-path:}") String keyPath,
            Environment environment) {
        this.projectId = projectId;
        this.keyPath = keyPath;
        this.environment = environment;
    }

    /**
     * Crea la instancia del SDK.
     *
     * @return aplicación de Firebase lista para usarse
     * @throws IllegalStateException si las credenciales no se pueden leer, o si la variable del
     *                               emulador está definida en un despliegue
     */
    @Bean
    public FirebaseApp firebaseApp() {
        rejectEmulatorInDeployment();
        try {
            FirebaseOptions opciones = FirebaseOptions.builder()
                    .setCredentials(resolverCredenciales())
                    .setProjectId(projectId)
                    .build();

            return FirebaseApp.getApps().stream()
                    .filter(app -> APP_NAME.equals(app.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(opciones, APP_NAME));
        } catch (IOException error) {
            throw new IllegalStateException(
                    "No se pudieron leer las credenciales de Firebase; el microservicio no puede atender registros",
                    error);
        }
    }

    /**
     * Expone el cliente de autenticación.
     *
     * @param firebaseApp instancia del SDK
     * @return cliente con el que se crean y consultan usuarios
     */
    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    /**
     * Publica el adaptador que implementa el puerto del dominio.
     *
     * @param firebaseAuth cliente de autenticación del Admin SDK
     * @return implementación del directorio de usuarios sobre Firebase
     */
    @Bean
    public FirebaseUserDirectory firebaseUserDirectory(FirebaseAuth firebaseAuth) {
        return new FirebaseUserDirectoryAdapter(firebaseAuth);
    }

    /**
     * Falla el arranque si la variable del emulador está definida en un despliegue. Cuenta como
     * definida aunque su valor esté vacío: es el criterio más conservador.
     *
     * @throws IllegalStateException si la variable existe y hay {@code K_SERVICE} o el perfil
     *                               {@code prod}
     */
    private void rejectEmulatorInDeployment() {
        if (environment.containsProperty(EMULATOR_HOST_VARIABLE) && isDeployment()) {
            throw new IllegalStateException(EMULATOR_HOST_VARIABLE + " apunta al emulador de "
                    + "Firebase Auth, que emite tokens sin firma, y no puede estar definida en un "
                    + "despliegue (" + CLOUD_RUN_SERVICE_VARIABLE + " definida o perfil '"
                    + DEPLOY_PROFILE + "' activo)");
        }
    }

    /**
     * @return {@code true} si existe {@code K_SERVICE} (Cloud Run la inyecta) o está activo el
     *         perfil {@code prod}
     */
    private boolean isDeployment() {
        return environment.containsProperty(CLOUD_RUN_SERVICE_VARIABLE)
                || environment.matchesProfiles(DEPLOY_PROFILE);
    }

    /**
     * Elige las credenciales en este orden: el emulador, las credenciales por defecto de
     * Google si no hay ruta de llave, y el JSON de la ruta. El emulador va primero porque
     * dentro de Docker la ruta nunca está vacía.
     *
     * @return credenciales ficticias con el emulador; si no, las de Google o las del archivo
     * @throws IOException si no hay credenciales por defecto o no se puede leer el archivo
     */
    private GoogleCredentials resolverCredenciales() throws IOException {
        String emulatorHost = environment.getProperty(EMULATOR_HOST_VARIABLE);
        if (emulatorHost != null && !emulatorHost.isBlank()) {
            logger.warn("{} definida ({}): Firebase usa el emulador con credenciales ficticias y "
                    + "acepta tokens sin firma. Solo para desarrollo local.",
                    EMULATOR_HOST_VARIABLE, emulatorHost);
            return GoogleCredentials.create(new AccessToken("emulador-sin-credenciales", null));
        }
        if (keyPath == null || keyPath.isBlank()) {
            return defaultCredentials();
        }
        try (InputStream archivo = new FileInputStream(keyPath)) {
            return GoogleCredentials.fromStream(archivo);
        }
    }

    /**
     * Credenciales por defecto de Google. Es un método aparte para poder sustituirlas en una
     * prueba y comprobar de dónde salen las credenciales sin depender de la máquina que la
     * ejecuta.
     *
     * @return las credenciales por defecto del entorno (servidor de metadatos de Cloud Run)
     * @throws IOException si el entorno no tiene ninguna
     */
    GoogleCredentials defaultCredentials() throws IOException {
        return GoogleCredentials.getApplicationDefault();
    }
}
