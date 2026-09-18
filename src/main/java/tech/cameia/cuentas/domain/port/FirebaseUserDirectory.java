package tech.cameia.cuentas.domain.port;

import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
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
     * Crea la credencial de un usuario nuevo.
     *
     * @param email correo con el que iniciará sesión
     * @param password contraseña ya validada por la política del dominio
     * @return identificador del usuario creado
     * @throws EmailAlreadyRegisteredException si ese correo ya tiene una credencial
     */
    String createUser(EmailAddress email, RawPassword password);

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
