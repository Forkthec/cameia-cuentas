package tech.cameia.cuentas.domain.port;

import java.util.Optional;

import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.exception.InvalidEmailException;
import tech.cameia.cuentas.domain.model.DirectoryUser;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.RawPassword;

/**
 * Operaciones que el registro necesita del directorio de usuarios.
 *
 * <p>Detrás está Firebase Auth, pero el dominio no lo sabe: aquí no aparecen tipos ni
 * códigos de error del SDK. El adaptador traduce esos códigos a las excepciones de
 * negocio de este paquete.</p>
 *
 * <p>Las credenciales viven exclusivamente al otro lado de este puerto. El microservicio
 * no almacena el correo ni la contraseña.</p>
 */
public interface FirebaseUserDirectory {

    /**
     * Crea la credencial de un usuario nuevo con el identificador que elige el llamador.
     *
     * <p>El llamador genera el identificador para poder reconocer su propia credencial: si el
     * directorio reintenta por su cuenta una creación que sí se completó y responde que el correo ya
     * existe, la credencial que tiene ese identificador es suya y no de otra petición.</p>
     *
     * @param firebaseUid identificador que tendrá el usuario
     * @param email correo con el que iniciará sesión
     * @param password contraseña ya validada por la política del dominio
     * @throws EmailAlreadyRegisteredException si ese correo o ese identificador ya tienen credencial
     */
    void createUser(String firebaseUid, EmailAddress email, RawPassword password);

    /**
     * Busca la credencial que tiene un correo.
     *
     * @param email correo ya normalizado
     * @return la credencial, o vacío si ese correo no tiene ninguna
     * @throws DependencyUnavailableException si el directorio no respondió o falló de su lado
     * @throws InvalidEmailException si el directorio rechaza el correo como inválido
     */
    Optional<DirectoryUser> findByEmail(EmailAddress email);

    /**
     * Lee el correo de un usuario, para los eventos que se emiten después de la petición de registro.
     *
     * @param firebaseUid identificador del usuario
     * @return el correo normalizado, o vacío si el usuario no existe, no tiene correo o el que tiene no es válido
     * @throws DependencyUnavailableException si el directorio no respondió o falló de su lado
     */
    Optional<EmailAddress> findEmail(String firebaseUid);

    /**
     * Marca al usuario con el plan gratuito.
     *
     * <p>Escribe el custom claim {@code plan} con el valor {@code FREE}. Ese claim todavía
     * no respalda ningún derecho ni cuota: mientras no exista la spec de planes, ningún
     * microservicio debe usarlo para autorizar nada.</p>
     *
     * @param firebaseUid identificador del usuario
     */
    void assignFreePlanClaim(String firebaseUid);

    /**
     * Elimina la credencial de un usuario.
     *
     * <p>El registro la usa para compensar: si la cuenta local no se pudo guardar, la
     * credencial no debe quedar huérfana ocupando el correo.</p>
     *
     * @param firebaseUid identificador del usuario
     */
    void deleteUser(String firebaseUid);

    /**
     * Consulta si el usuario ya verificó su correo.
     *
     * @param firebaseUid identificador del usuario
     * @return {@code true} si el correo está verificado
     */
    boolean isEmailVerified(String firebaseUid);
}
