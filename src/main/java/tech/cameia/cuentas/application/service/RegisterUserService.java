package tech.cameia.cuentas.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tech.cameia.cuentas.application.command.RegisterUserCommand;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.PhoneNumber;
import tech.cameia.cuentas.domain.model.RawPassword;
import tech.cameia.cuentas.domain.policy.AgePolicy;
import tech.cameia.cuentas.domain.policy.PasswordPolicy;
import tech.cameia.cuentas.domain.port.AccountRepository;
import tech.cameia.cuentas.domain.port.FirebaseUserDirectory;

/**
 * Registra una cuenta nueva.
 *
 * <p>Orquesta tres sistemas en este orden: valida con las políticas del dominio, crea la
 * credencial en Firebase con su plan, y guarda la cuenta local. Validar primero evita
 * crear credenciales que luego habría que borrar.</p>
 *
 * <p><strong>El método no es transaccional de punta a punta.</strong> Firebase no participa
 * en una transacción de base de datos, así que envolverlo daría una falsa sensación de
 * atomicidad. Si algo falla después de crear la credencial, esta clase la borra ella
 * misma; esa compensación es explícita y puede fallar, y por eso deja rastro.</p>
 */
@Service
public class RegisterUserService {

    private static final Logger logger = LoggerFactory.getLogger(RegisterUserService.class);

    private final FirebaseUserDirectory directorio;
    private final AccountRepository repositorio;
    private final AgePolicy politicaDeEdad;
    private final PasswordPolicy politicaDeContrasenia;

    /**
     * Crea el caso de uso.
     *
     * @param directorio directorio de usuarios donde viven las credenciales
     * @param repositorio repositorio de cuentas locales
     * @param politicaDeEdad reglas de fecha de nacimiento
     * @param politicaDeContrasenia reglas de contraseña
     */
    public RegisterUserService(FirebaseUserDirectory directorio, AccountRepository repositorio,
            AgePolicy politicaDeEdad, PasswordPolicy politicaDeContrasenia) {
        this.directorio = directorio;
        this.repositorio = repositorio;
        this.politicaDeEdad = politicaDeEdad;
        this.politicaDeContrasenia = politicaDeContrasenia;
    }

    /**
     * Registra la cuenta y devuelve su estado inicial.
     *
     * @param command datos del formulario de registro
     * @return la cuenta creada, pendiente de verificar el correo
     * @throws tech.cameia.cuentas.domain.exception.InvalidBirthDateException si la fecha
     *         de nacimiento no permite registrarse
     * @throws tech.cameia.cuentas.domain.exception.WeakPasswordException si la contraseña
     *         no cumple la política
     * @throws tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException si el
     *         correo ya tiene credencial
     * @throws IllegalStateException si falla Firebase o la base de datos; en ese caso la
     *         credencial recién creada ya quedó compensada
     */
    public Account register(RegisterUserCommand command) {
        EmailAddress email = new EmailAddress(command.email());
        RawPassword password = new RawPassword(command.password());
        BirthDate birthDate = new BirthDate(command.birthDate());
        PhoneNumber phoneNumber = command.phoneNumber() == null
                ? null
                : new PhoneNumber(command.phoneNumber());

        politicaDeEdad.verify(birthDate);
        politicaDeContrasenia.verify(password);

        String firebaseUid = directorio.createUser(email, password);

        try {
            directorio.assignFreePlanClaim(firebaseUid);

            Account cuenta = Account.register(firebaseUid, command.firstName(), command.lastName(),
                    birthDate, phoneNumber, command.pronoun());
            Account guardada = repositorio.save(cuenta);

            logger.info("Cuenta registrada para el usuario {}", firebaseUid);
            return guardada;
        } catch (RuntimeException error) {
            compensar(firebaseUid);
            throw error;
        }
    }

    /**
     * Borra la credencial recién creada cuando el registro no pudo completarse.
     *
     * <p>Sin esto, el correo quedaría ocupado en Firebase por una cuenta que no existe y
     * la persona no podría volver a intentarlo. Si el borrado también falla, se registra
     * el identificador para conciliarlo a mano: es el único rastro que queda, y no
     * identifica a nadie fuera de Firebase.</p>
     *
     * @param firebaseUid identificador de la credencial a borrar
     */
    private void compensar(String firebaseUid) {
        try {
            directorio.deleteUser(firebaseUid);
        } catch (RuntimeException fallo) {
            logger.error("Quedó una credencial sin cuenta local en Firebase, uid {}; requiere "
                    + "conciliación manual", firebaseUid, fallo);
        }
    }
}
