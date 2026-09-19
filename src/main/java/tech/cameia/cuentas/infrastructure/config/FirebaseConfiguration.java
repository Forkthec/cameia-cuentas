package tech.cameia.cuentas.infrastructure.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;

import tech.cameia.cuentas.domain.port.FirebaseUserDirectory;
import tech.cameia.cuentas.infrastructure.client.FirebaseUserDirectoryAdapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
 */
@Configuration
@ConditionalOnProperty(name = "cuentas.firebase.enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseConfiguration {

    /** Nombre de la instancia, para no depender de la aplicación por defecto del SDK. */
    private static final String APP_NAME = "cameia-cuentas";

    private final String projectId;
    private final String keyPath;

    /**
     * Recibe la configuración del proyecto de Firebase.
     *
     * @param projectId identificador del proyecto en Firebase Console
     * @param keyPath ruta al JSON de la cuenta de servicio; vacío en Cloud Run, donde se
     *                usan las credenciales por defecto del entorno
     */
    public FirebaseConfiguration(
            @Value("${cuentas.firebase.project-id}") String projectId,
            @Value("${cuentas.firebase.key-path:}") String keyPath) {
        this.projectId = projectId;
        this.keyPath = keyPath;
    }

    /**
     * Crea la instancia del SDK.
     *
     * @return aplicación de Firebase lista para usarse
     * @throws IllegalStateException si las credenciales no se pueden leer
     */
    @Bean
    public FirebaseApp firebaseApp() {
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

    private GoogleCredentials resolverCredenciales() throws IOException {
        if (keyPath == null || keyPath.isBlank()) {
            return GoogleCredentials.getApplicationDefault();
        }
        try (InputStream archivo = new FileInputStream(keyPath)) {
            return GoogleCredentials.fromStream(archivo);
        }
    }
}
