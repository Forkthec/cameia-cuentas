package tech.cameia.cuentas.application.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tech.cameia.cuentas.application.command.RegisterUserCommand;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.DirectoryUser;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.PersonName;
import tech.cameia.cuentas.domain.model.PhoneNumber;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.domain.model.RawPassword;
import tech.cameia.cuentas.domain.policy.AgePolicy;
import tech.cameia.cuentas.domain.policy.PasswordPolicy;
import tech.cameia.cuentas.domain.port.AccountRepository;
import tech.cameia.cuentas.domain.port.FirebaseUserDirectory;

/**
 * Registra una cuenta nueva, o devuelve la que ya está pendiente de verificar.
 *
 * <p>Orquesta tres sistemas en este orden: valida con las políticas del dominio, consulta si el
 * correo ya tiene credencial, y si no la tiene crea la credencial en Firebase con su plan y guarda
 * la cuenta local junto con su evento {@code cuenta.creada} en una sola transacción. Validar primero
 * evita crear credenciales que luego habría que borrar.</p>
 *
 * <p>Repetir el registro de un correo cuya cuenta sigue pendiente de verificar es idempotente:
 * devuelve la misma cuenta sin escribir nada. Así, quien perdió la respuesta (red lenta, pestaña
 * cerrada) puede reintentar sin quedar bloqueado.</p>
 *
 * <p><strong>El método no es transaccional de punta a punta.</strong> Firebase no participa
 * en una transacción de base de datos, así que envolverlo daría una falsa sensación de
 * atomicidad. Si algo falla después de crear la credencial, esta clase la borra ella
 * misma; esa compensación es explícita y puede fallar, y por eso deja rastro.</p>
 */
@Service
public class RegisterUserService {

    private static final Logger logger = LoggerFactory.getLogger(RegisterUserService.class);

    /**
     * Antigüedad desde la cual una credencial sin cuenta local ya no puede ser un registro en curso.
     * El SDK de Firebase reintenta cada llamada hasta cuatro veces y no es configurable, así que una
     * llamada puede tardar unos 48 s y las cuatro de un registro unos 190 s; 5 min las cubre.
     */
    private static final Duration REGISTRO_EN_CURSO = Duration.ofSeconds(300);

    private final FirebaseUserDirectory directorio;
    private final AccountRepository repositorio;
    private final AccountRecordingService accountRecorder;
    private final AgePolicy politicaDeEdad;
    private final PasswordPolicy politicaDeContrasenia;
    private final Clock clock;

    /**
     * Crea el caso de uso con el reloj del sistema.
     *
     * @param directorio directorio de usuarios donde viven las credenciales
     * @param repositorio repositorio de cuentas locales
     * @param accountRecorder guarda la cuenta nueva junto con su evento de cuenta creada en una transacción
     * @param politicaDeEdad reglas de fecha de nacimiento
     * @param politicaDeContrasenia reglas de contraseña
     */
    @Autowired
    public RegisterUserService(FirebaseUserDirectory directorio, AccountRepository repositorio,
            AccountRecordingService accountRecorder, AgePolicy politicaDeEdad, PasswordPolicy politicaDeContrasenia) {
        this(directorio, repositorio, accountRecorder, politicaDeEdad, politicaDeContrasenia, Clock.systemUTC());
    }

    /**
     * Crea el caso de uso con un reloj propio, para fijar la hora en las pruebas.
     *
     * @param directorio directorio de usuarios donde viven las credenciales
     * @param repositorio repositorio de cuentas locales
     * @param accountRecorder guarda la cuenta nueva junto con su evento de cuenta creada en una transacción
     * @param politicaDeEdad reglas de fecha de nacimiento
     * @param politicaDeContrasenia reglas de contraseña
     * @param clock reloj con el que se mide la antigüedad de una credencial
     */
    public RegisterUserService(FirebaseUserDirectory directorio, AccountRepository repositorio,
            AccountRecordingService accountRecorder, AgePolicy politicaDeEdad, PasswordPolicy politicaDeContrasenia,
            Clock clock) {
        this.directorio = directorio;
        this.repositorio = repositorio;
        this.accountRecorder = accountRecorder;
        this.politicaDeEdad = politicaDeEdad;
        this.politicaDeContrasenia = politicaDeContrasenia;
        this.clock = clock;
    }

    /**
     * Registra la cuenta, o devuelve la que ya existe pendiente de verificar.
     *
     * @param command datos del formulario de registro
     * @return la cuenta y si esta llamada la creó; si el correo ya tenía una cuenta pendiente de
     *         verificar, es esa cuenta, sin cambios, y los demás datos del formulario se ignoran
     * @throws tech.cameia.cuentas.domain.exception.InvalidBirthDateException si la fecha
     *         de nacimiento no permite registrarse
     * @throws tech.cameia.cuentas.domain.exception.InvalidPersonNameException si el nombre o
     *         el apellido tienen caracteres que no son letras, espacios, apóstrofo ni guion
     * @throws tech.cameia.cuentas.domain.exception.InvalidPhoneNumberException si el celular
     *         no es un número válido para el país de su indicativo
     * @throws tech.cameia.cuentas.domain.exception.WeakPasswordException si la contraseña
     *         no cumple la política
     * @throws EmailAlreadyRegisteredException si el correo ya tiene una cuenta activa,
     *         bloqueada o anonimizada, una credencial deshabilitada, o una credencial sin cuenta local
     * @throws tech.cameia.cuentas.domain.exception.DependencyUnavailableException si Firebase
     *         no respondió o falló de su lado; la credencial recién creada ya quedó compensada
     * @throws IllegalStateException si Firebase rechaza la operación o falla la base de datos;
     *         en ese caso la credencial recién creada ya quedó compensada
     */
    public RegisterUserResult register(RegisterUserCommand command) {
        ValidRegistration registro = validar(command);

        Optional<DirectoryUser> existente = directorio.findByEmail(registro.email());
        if (existente.isPresent()) {
            return atenderCorreoExistente(existente.get(), false);
        }
        return registrarNueva(registro);
    }

    /** Datos del formulario ya validados; agrupa los valores para no pasar más de tres parámetros. */
    private record ValidRegistration(EmailAddress email, RawPassword password, BirthDate birthDate,
            PhoneNumber phoneNumber, PersonName firstName, PersonName lastName, Pronoun pronoun, String requestId) { }

    /** Aplica las reglas del dominio a los datos del formulario antes de tocar ningún sistema externo. */
    private ValidRegistration validar(RegisterUserCommand command) {
        EmailAddress email = new EmailAddress(command.email());
        RawPassword password = new RawPassword(command.password());
        BirthDate birthDate = new BirthDate(command.birthDate());
        PhoneNumber phoneNumber = command.phoneNumber() == null
                ? null
                : PhoneNumber.fromInput(command.phoneNumber());
        PersonName firstName = new PersonName(command.firstName(), PersonName.Part.FIRST_NAME);
        PersonName lastName = new PersonName(command.lastName(), PersonName.Part.LAST_NAME);

        politicaDeEdad.verify(birthDate);
        politicaDeContrasenia.verify(password);
        return new ValidRegistration(email, password, birthDate, phoneNumber, firstName, lastName,
                command.pronoun(), command.requestId());
    }

    /**
     * Crea la credencial con un identificador propio y completa el registro.
     *
     * <p>El identificador lo genera esta petición para reconocer su credencial. Si el conflicto
     * viene de otra petición que creó el mismo correo, se evalúa de nuevo, una sola vez. Si la
     * credencial que aparece tiene el identificador propio, el SDK de Firebase reintentó una
     * creación que sí se había completado: la credencial es de esta petición y el registro
     * continúa, en lugar de dejarla sin cuenta local.</p>
     *
     * <p>Si la creación no obtiene respuesta (por ejemplo, vence el tiempo de lectura), Firebase
     * pudo haberla completado igual. Como el identificador es propio, se intenta borrar esa
     * credencial antes de responder 503: así el reintento de la persona no choca con ella.</p>
     */
    private RegisterUserResult registrarNueva(ValidRegistration registro) {
        String firebaseUid = UUID.randomUUID().toString().replace("-", "");
        try {
            directorio.createUser(firebaseUid, registro.email(), registro.password());
        } catch (EmailAlreadyRegisteredException conflicto) {
            DirectoryUser existente = directorio.findByEmail(registro.email())
                    .orElseThrow(EmailAlreadyRegisteredException::new);
            if (!firebaseUid.equals(existente.uid())) {
                return atenderCorreoExistente(existente, true);
            }
            logger.warn("La creación de la credencial se completó tras un reintento del directorio; "
                    + "se continúa con el usuario {}", firebaseUid);
        } catch (DependencyUnavailableException sinRespuesta) {
            borrarSiQuedoCreada(firebaseUid);
            throw sinRespuesta;
        }
        return completarRegistro(firebaseUid, registro);
    }

    /**
     * Borra la credencial propia después de una creación sin respuesta.
     *
     * <p>Lo normal es que no exista y el borrado no haga nada. Si el borrado tampoco responde, no se
     * sabe si la credencial existe: se deja un aviso con el identificador y, si quedó, la concilia la
     * purga de cuentas sin verificar. No es un error de conciliación porque puede no haber nada que conciliar.</p>
     */
    private void borrarSiQuedoCreada(String firebaseUid) {
        try {
            directorio.deleteUser(firebaseUid);
        } catch (RuntimeException fallo) {
            logger.warn("No se pudo confirmar el borrado de la credencial tras una creación sin respuesta, "
                    + "uid {}; si quedó creada, la concilia la purga", firebaseUid);
        }
    }

    /** Escribe el plan y guarda la cuenta; si algo falla, borra la credencial recién creada. */
    private RegisterUserResult completarRegistro(String firebaseUid, ValidRegistration registro) {
        try {
            directorio.assignFreePlanClaim(firebaseUid);

            Account cuenta = Account.register(firebaseUid, registro.firstName().value(),
                    registro.lastName().value(), registro.birthDate(), registro.phoneNumber(),
                    registro.pronoun());
            // La fila de la cuenta y el evento cuenta.creada se guardan en una sola transacción; un fallo aquí sigue
            // compensando la credencial.
            Account guardada = accountRecorder.recordNewAccount(cuenta, registro.email(), registro.requestId()).account();

            logger.info("Cuenta registrada para el usuario {}", firebaseUid);
            return new RegisterUserResult(guardada, true);
        } catch (RuntimeException error) {
            compensar(firebaseUid);
            throw error;
        }
    }

    /**
     * Decide qué responde un registro cuando el correo ya tiene credencial.
     *
     * <p>Si la cuenta local sigue pendiente de verificar y la credencial está habilitada, se
     * devuelve esa cuenta tal cual: la contraseña no se verifica y los datos del cuerpo se
     * ignoran, porque la respuesta no entrega nada que la persona no haya recibido ya al
     * registrarse. Cualquier otro estado (activa, bloqueada, anonimizada, credencial
     * deshabilitada) es el mismo conflicto, sin distinguir cuál entre ellos.</p>
     *
     * <p>Una credencial sin cuenta local es un registro en curso si es reciente, o un residuo si
     * ya pasó {@link #REGISTRO_EN_CURSO}; en ambos casos es conflicto y el manejador decide el
     * nivel del registro con la bandera de conciliación. Tras una carrera, la credencial es
     * reciente por definición y nunca se marca para conciliar.</p>
     *
     * @param credencial credencial encontrada para el correo
     * @param trasConflicto {@code true} si viene de perder una carrera al crear la credencial
     * @return la cuenta pendiente, sin haber escrito nada
     */
    private RegisterUserResult atenderCorreoExistente(DirectoryUser credencial, boolean trasConflicto) {
        Optional<Account> fila = repositorio.findByFirebaseUid(credencial.uid());
        if (fila.isEmpty()) {
            boolean huerfana = !trasConflicto && esAntigua(credencial);
            throw new EmailAlreadyRegisteredException(credencial.uid(), huerfana);
        }
        Account cuenta = fila.get();
        if (credencial.disabled() || !cuenta.isPendingVerification()) {
            // Ni la respuesta ni el registro distinguen la causa: el estado de una cuenta ajena no se publica
            throw new EmailAlreadyRegisteredException();
        }
        logger.info("Registro repetido atendido con la cuenta pendiente del usuario {}", credencial.uid());
        return new RegisterUserResult(cuenta, false);
    }

    /**
     * Indica si la credencial lleva más de {@link #REGISTRO_EN_CURSO} creada.
     *
     * <p>Una fecha de creación en cero (Firebase no la informó) es desconocida y se toma por
     * antigua: Firebase siempre la informa, y si faltara, avisar de más es mejor que dejar la
     * credencial sin conciliar para siempre.</p>
     */
    private boolean esAntigua(DirectoryUser credencial) {
        if (credencial.createdAt().toEpochMilli() <= 0) {
            return true;
        }
        return Duration.between(credencial.createdAt(), clock.instant()).compareTo(REGISTRO_EN_CURSO) >= 0;
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
