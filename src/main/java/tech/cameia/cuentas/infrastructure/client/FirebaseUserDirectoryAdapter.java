package tech.cameia.cuentas.infrastructure.client;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.firebase.ErrorCode;
import com.google.firebase.IncomingHttpResponse;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.exception.InvalidEmailException;
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

    /** Código de Identity Toolkit para un correo que no considera válido. */
    private static final String CORREO_INVALIDO = "INVALID_EMAIL";

    /** Valor cuando la respuesta de Firebase no trae un código legible. */
    private static final String DESCONOCIDO = "desconocido";

    /** El código es el comienzo del mensaje: mayúsculas y guiones bajos. */
    private static final Pattern CODIGO_DEL_SERVICIO = Pattern.compile("^[A-Z][A-Z_]*");

    private static final JsonMapper JSON = JsonMapper.builder().build();

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
     * @throws InvalidEmailException si Firebase rechaza el correo ({@code INVALID_EMAIL})
     * @throws DependencyUnavailableException si Firebase no respondió o falló de su lado
     * @throws IllegalStateException si Firebase rechaza la creación por cualquier otro motivo
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
            String codigoDelServicio = codigoDelServicio(error);
            if (CORREO_INVALIDO.equals(codigoDelServicio)) {
                // EmailAddress es permisiva a propósito y Firebase podría ser más estricta. No se
                // conoce un correo que pase la regla propia y Firebase rechace (el emulador los
                // acepta todos), así que es una defensa. Para la persona es un correo inválido
                // (CA-1.1.20); el aviso deja ver en el log que las dos reglas difieren.
                logger.warn("Firebase rechazó como inválido un correo que pasó la validación propia");
                throw InvalidEmailException.invalidFormat();
            }
            // Cualquier otro rechazo (una política de contraseñas o un proveedor deshabilitado en
            // Firebase) es un defecto de configuración que la persona no puede corregir: el código
            // del servicio va al log para diagnosticarlo.
            throw indisponibleOEnRechazo(error,
                    "Firebase rechazó la creación del usuario [codigoDelServicio=" + codigoDelServicio + "]");
        }
    }

    /**
     * Código de error que devolvió Identity Toolkit, leído del cuerpo de la respuesta.
     *
     * <p>El SDK no tiene código propio para varios rechazos: {@code INVALID_EMAIL},
     * {@code WEAK_PASSWORD}, {@code PASSWORD_DOES_NOT_MEET_REQUIREMENTS} u
     * {@code OPERATION_NOT_ALLOWED} llegan todos como {@code INVALID_ARGUMENT} sin código de
     * autenticación. El servicio los distingue en {@code error.message}, que empieza por el código
     * documentado y puede seguir con un detalle ({@code WEAK_PASSWORD : …}). Solo se devuelve el
     * código, nunca el detalle.</p>
     *
     * @return el código, o {@code desconocido} si la respuesta no lo trae
     */
    static String codigoDelServicio(FirebaseAuthException error) {
        IncomingHttpResponse respuesta = error.getHttpResponse();
        if (respuesta == null || respuesta.getContent() == null) {
            return DESCONOCIDO;
        }
        try {
            String mensaje = JSON.readTree(respuesta.getContent()).path("error").path("message").asString("");
            Matcher codigo = CODIGO_DEL_SERVICIO.matcher(mensaje);
            return codigo.find() ? codigo.group() : DESCONOCIDO;
        } catch (JacksonException ilegible) {
            return DESCONOCIDO;
        }
    }

    /**
     * Escribe el custom claim del plan gratuito.
     *
     * <p>El claim viaja al frontend en el siguiente token que este refresque, y de ahí al
     * resto de la plataforma a través del Gateway.</p>
     *
     * @param firebaseUid identificador del usuario
     * @throws DependencyUnavailableException si Firebase no respondió o falló de su lado
     * @throws IllegalStateException si Firebase rechaza la escritura por cualquier otro motivo
     */
    @Override
    public void assignFreePlanClaim(String firebaseUid) {
        try {
            firebaseAuth.setCustomUserClaims(firebaseUid, Map.of(PLAN_CLAIM, FREE_PLAN));
        } catch (FirebaseAuthException error) {
            throw indisponibleOEnRechazo(error, "Firebase rechazó la escritura del plan del usuario");
        }
    }

    /**
     * Elimina la credencial del usuario.
     *
     * @param firebaseUid identificador del usuario
     * @throws DependencyUnavailableException si Firebase no respondió o falló de su lado
     * @throws IllegalStateException si Firebase rechaza el borrado por cualquier otro motivo;
     *                               quien compensa decide qué hacer con ese fallo
     */
    @Override
    public void deleteUser(String firebaseUid) {
        try {
            firebaseAuth.deleteUser(firebaseUid);
            logger.info("Credencial eliminada en Firebase para el usuario {}", firebaseUid);
        } catch (FirebaseAuthException error) {
            throw indisponibleOEnRechazo(error, "Firebase rechazó la eliminación del usuario");
        }
    }

    /**
     * Separa la indisponibilidad de Firebase de un rechazo.
     *
     * <p>Cuenta como indisponibilidad lo que la persona resuelve reintentando: el servicio no
     * respondió a tiempo, respondió que no está disponible, falló de su lado, o la conexión
     * misma falló (el SDK lo informa con una causa de E/S y el código {@code UNKNOWN} cuando
     * la conexión es rechazada, o {@code DEADLINE_EXCEEDED} cuando se agota el tiempo).
     * Cualquier otro error es un rechazo de un dato que pasó la validación propia: un
     * defecto, no algo pasajero.</p>
     *
     * <p>La causa de E/S solo cuenta si no hubo respuesta: ante un rechazo HTTP (un 400 o un 404)
     * el SDK también adjunta una {@code HttpResponseException}, que es de E/S, y confundirla con
     * una conexión fallida respondía 503 «inténtalo de nuevo» a un rechazo que no se resuelve
     * reintentando.</p>
     *
     * @param error excepción del SDK
     * @param rechazo texto técnico para el log si no es indisponibilidad
     * @return la excepción que se debe lanzar
     */
    private static RuntimeException indisponibleOEnRechazo(FirebaseAuthException error, String rechazo) {
        ErrorCode codigo = error.getErrorCode();
        boolean indisponible = codigo == ErrorCode.UNAVAILABLE
                || codigo == ErrorCode.DEADLINE_EXCEEDED
                || codigo == ErrorCode.INTERNAL
                || (error.getHttpResponse() == null && error.getCause() instanceof IOException);
        return indisponible ? new DependencyUnavailableException(error) : new IllegalStateException(rechazo, error);
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
