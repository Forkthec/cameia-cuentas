package tech.cameia.cuentas.infrastructure.client;

import java.util.Map;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.RawPassword;
import tech.cameia.cuentas.domain.port.FirebaseUserDirectory;

/**
 * Implementa el puerto {@code FirebaseUserDirectory} sobre el Admin SDK.
 *
 * <p>Es el único punto del microservicio que conoce Firebase. Su otra responsabilidad es
 * traducir los códigos de error del SDK a excepciones de negocio, para que el resto del
 * código no tenga que interpretarlos.</p>
 *
 * <p>Ninguno de sus registros incluye el correo ni la contraseña: como mucho aparece el
 * identificador del usuario, que por sí solo no identifica a nadie fuera de Firebase.</p>
 *
 * <p>No lleva {@code @Component}: lo publica {@code FirebaseConfiguration}, que solo existe
 * cuando el SDK está habilitado. Así, con Firebase apagado no queda un bean pidiendo un
 * cliente que nadie creó.</p>
 */
public class FirebaseUserDirectoryAdapter implements FirebaseUserDirectory {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseUserDirectoryAdapter.class);

    /** Nombre del custom claim que transporta el plan del usuario. */
    private static final String PLAN_CLAIM = "plan";

    /** Valor del plan gratuito. */
    private static final String FREE_PLAN = "FREE";

    private final FirebaseAuth firebaseAuth;

    /**
     * Crea el adaptador.
     *
     * @param firebaseAuth cliente de autenticación del Admin SDK
     */
    public FirebaseUserDirectoryAdapter(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    /**
     * Crea la credencial del usuario.
     *
     * <p>La credencial nace con el correo sin verificar: el enlace de verificación lo pide
     * el frontend a Firebase después de iniciar sesión.</p>
     *
     * @param email correo con el que iniciará sesión
     * @param password contraseña ya validada por la política del dominio
     * @return identificador del usuario creado
     * @throws EmailAlreadyRegisteredException si ese correo ya tiene credencial
     * @throws IllegalStateException si Firebase falla por cualquier otro motivo
     */
    @Override
    public String createUser(EmailAddress email, RawPassword password) {
        UserRecord.CreateRequest solicitud = new UserRecord.CreateRequest()
                .setEmail(email.value())
                .setPassword(password.value())
                .setEmailVerified(false);

        try {
            return firebaseAuth.createUser(solicitud).getUid();
        } catch (FirebaseAuthException error) {
            if (AuthErrorCode.EMAIL_ALREADY_EXISTS.equals(error.getAuthErrorCode())) {
                throw new EmailAlreadyRegisteredException();
            }
            throw new IllegalStateException("Firebase rechazó la creación del usuario", error);
        }
    }

    /**
     * Escribe el custom claim del plan gratuito.
     *
     * <p>El claim viaja al frontend en el siguiente token que este refresque, y de ahí al
     * resto de la plataforma a través del Gateway.</p>
     *
     * @param firebaseUid identificador del usuario
     * @throws IllegalStateException si Firebase rechaza la escritura
     */
    @Override
    public void assignFreePlanClaim(String firebaseUid) {
        try {
            firebaseAuth.setCustomUserClaims(firebaseUid, Map.of(PLAN_CLAIM, FREE_PLAN));
        } catch (FirebaseAuthException error) {
            throw new IllegalStateException("Firebase rechazó la escritura del plan del usuario", error);
        }
    }

    /**
     * Elimina la credencial del usuario.
     *
     * @param firebaseUid identificador del usuario
     * @throws IllegalStateException si Firebase rechaza el borrado; quien compensa decide
     *                               qué hacer con ese fallo
     */
    @Override
    public void deleteUser(String firebaseUid) {
        try {
            firebaseAuth.deleteUser(firebaseUid);
            logger.info("Credencial eliminada en Firebase para el usuario {}", firebaseUid);
        } catch (FirebaseAuthException error) {
            throw new IllegalStateException("Firebase rechazó la eliminación del usuario", error);
        }
    }

    /**
     * Consulta si el usuario ya verificó su correo.
     *
     * @param firebaseUid identificador del usuario
     * @return {@code true} si el correo está verificado
     * @throws IllegalStateException si el usuario no existe o Firebase no responde
     */
    @Override
    public boolean isEmailVerified(String firebaseUid) {
        try {
            return firebaseAuth.getUser(firebaseUid).isEmailVerified();
        } catch (FirebaseAuthException error) {
            throw new IllegalStateException("Firebase no pudo confirmar el estado del correo", error);
        }
    }
}
