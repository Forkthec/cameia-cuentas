package tech.cameia.cuentas.domain.model;

import java.util.Optional;
import java.util.UUID;

import tech.cameia.cuentas.domain.exception.AccountDisabledException;
import tech.cameia.cuentas.domain.exception.AccountNotFoundException;

/**
 * Cuenta local de una persona registrada en la plataforma.
 *
 * <p>Es el agregado del microservicio: guarda los datos personales y el estado, y nunca el
 * correo ni la contraseña, que pertenecen a Firebase Auth. La única referencia a la
 * identidad es el {@code firebaseUid}.</p>
 *
 * <p>Una cuenta solo puede nacer con {@link #register}, que la deja en
 * {@link AccountStatus#PENDING_VERIFICATION}. No existe forma de construir una cuenta ya
 * activa: la activación pasa por {@link #activate()}, que exige que el correo esté
 * verificado.</p>
 */
public class Account {

    private final UUID id;
    private final String firebaseUid;
    private final String firstName;
    private final String lastName;
    private final BirthDate birthDate;
    private final PhoneNumber phoneNumber;
    private final Pronoun pronoun;
    private PolicyAcceptance policyAcceptance;
    private AccountStatus status;
    private final Long version;

    private Account(UUID id, String firebaseUid, String firstName, String lastName,
            BirthDate birthDate, PhoneNumber phoneNumber, Pronoun pronoun, PolicyAcceptance policyAcceptance,
            AccountStatus status, Long version) {
        this.id = id;
        this.firebaseUid = firebaseUid;
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
        this.pronoun = pronoun;
        this.policyAcceptance = policyAcceptance;
        this.status = status;
        this.version = version;
    }

    /**
     * Crea la cuenta de alguien que acaba de registrarse.
     *
     * @param firebaseUid identificador del usuario recién creado en Firebase Auth
     * @param firstName nombres, obligatorios
     * @param lastName apellidos, obligatorios
     * @param birthDate fecha de nacimiento ya validada por la política de edad
     * @param phoneNumber celular, o {@code null} si no lo declaró
     * @param pronoun pronombres, o {@code null} si no los declaró
     * @param policyAcceptance versión de la política que la persona acepta al registrarse y el instante de la aceptación
     * @return cuenta nueva en estado {@code PENDING_VERIFICATION}
     * @throws IllegalArgumentException si falta el identificador de Firebase, los nombres,
     *                                  los apellidos, la fecha de nacimiento o la aceptación de la política
     */
    public static Account register(String firebaseUid, String firstName, String lastName,
            BirthDate birthDate, PhoneNumber phoneNumber, Pronoun pronoun, PolicyAcceptance policyAcceptance) {
        // El registro siempre exige la fecha: solo las cuentas antiguas pueden carecer de ella al reconstruirse.
        if (birthDate == null) {
            throw new IllegalArgumentException("La fecha de nacimiento es obligatoria al registrarse");
        }
        if (policyAcceptance == null) {
            throw new IllegalArgumentException("La aceptación de la política es obligatoria al registrarse");
        }
        return new Account(
                UUID.randomUUID(),
                exigirTexto(firebaseUid, "El identificador de Firebase es obligatorio"),
                exigirTexto(firstName, "Los nombres son obligatorios"),
                exigirTexto(lastName, "Los apellidos son obligatorios"),
                birthDate,
                phoneNumber,
                pronoun,
                policyAcceptance,
                AccountStatus.PENDING_VERIFICATION,
                null);
    }

    /**
     * Reconstruye una cuenta ya guardada.
     *
     * <p>La usa el adaptador de persistencia al leer una fila; no aplica las reglas de
     * creación porque los datos ya pasaron por ellas.</p>
     *
     * @param id identificador de la cuenta
     * @param firebaseUid identificador del usuario en Firebase Auth
     * @param firstName nombres
     * @param lastName apellidos
     * @param birthDate fecha de nacimiento, o {@code null} si la cuenta es anterior a que el registro la exigiera
     * @param phoneNumber celular, o {@code null}
     * @param pronoun pronombres, o {@code null}
     * @param policyAcceptance política aceptada y cuándo; la versión {@code sin-aceptar} marca a las cuentas anteriores
     * @param status estado almacenado
     * @return cuenta con el estado que tenía en la base de datos
     */
    public static Account rebuild(UUID id, String firebaseUid, String firstName, String lastName,
            BirthDate birthDate, PhoneNumber phoneNumber, Pronoun pronoun, PolicyAcceptance policyAcceptance,
            AccountStatus status) {
        return new Account(id, firebaseUid, firstName, lastName, birthDate, phoneNumber, pronoun, policyAcceptance,
                status, null);
    }

    /**
     * Reconstruye una cuenta ya guardada recordando la versión de la fila que se leyó.
     *
     * <p>Quien guarda la cuenta compara esa versión con la de la fila: si otra escritura la cambió entre la lectura y el
     * guardado, el guardado se rechaza en lugar de pisar el cambio ajeno.</p>
     *
     * @param id identificador de la cuenta
     * @param firebaseUid identificador del usuario en Firebase Auth
     * @param firstName nombres
     * @param lastName apellidos
     * @param birthDate fecha de nacimiento, o {@code null} si la cuenta es anterior a que el registro la exigiera
     * @param phoneNumber celular, o {@code null}
     * @param pronoun pronombres, o {@code null}
     * @param policyAcceptance política aceptada y cuándo; la versión {@code sin-aceptar} marca a las cuentas anteriores
     * @param status estado almacenado
     * @param version versión de la fila en el momento de leerla
     * @return cuenta con el estado y la versión que tenía en la base de datos
     */
    public static Account rebuild(UUID id, String firebaseUid, String firstName, String lastName,
            BirthDate birthDate, PhoneNumber phoneNumber, Pronoun pronoun, PolicyAcceptance policyAcceptance,
            AccountStatus status, Long version) {
        return new Account(id, firebaseUid, firstName, lastName, birthDate, phoneNumber, pronoun, policyAcceptance,
                status, version);
    }

    /**
     * Guarda la aceptación de una versión de la política.
     *
     * <p>Aceptar la versión que ya tiene no cambia nada: la fecha de la primera aceptación se conserva.</p>
     *
     * @param acceptance versión vigente y el instante actual
     * @return {@code true} si la aceptación cambió y hay que guardar la cuenta
     * @throws AccountDisabledException si la cuenta está deshabilitada
     * @throws AccountNotFoundException si la cuenta está anonimizada
     */
    public boolean acceptPolicy(PolicyAcceptance acceptance) {
        if (status == AccountStatus.DISABLED) {
            throw new AccountDisabledException();
        }
        if (status == AccountStatus.ANONYMIZED) {
            // No se revela que la cuenta existió: se responde igual que si no hubiera cuenta
            throw new AccountNotFoundException();
        }
        if (policyAcceptance.version().equals(acceptance.version())) {
            return false;
        }
        policyAcceptance = acceptance;
        return true;
    }

    /**
     * Marca la cuenta como verificada y lista para usarse.
     *
     * <p>Es idempotente: activar una cuenta que ya está activa no cambia nada ni falla, de
     * modo que el frontend puede reintentar la operación sin efectos raros.</p>
     *
     * @throws AccountDisabledException si la cuenta está deshabilitada; verificar un correo
     *                                  no revierte esa decisión
     * @throws AccountNotFoundException si la cuenta fue anonimizada; se responde igual que si
     *                                  no existiera, para no revelar que existió
     */
    public void activate() {
        if (status == AccountStatus.ACTIVE) {
            return;
        }
        if (status == AccountStatus.DISABLED) {
            throw new AccountDisabledException();
        }
        if (status == AccountStatus.ANONYMIZED) {
            // No se revela que la cuenta existió: se responde igual que si no hubiera cuenta
            throw new AccountNotFoundException();
        }
        status = AccountStatus.ACTIVE;
    }

    /**
     * Indica si la cuenta sigue esperando la verificación del correo.
     *
     * @return {@code true} mientras el correo no se haya verificado
     */
    public boolean isPendingVerification() {
        return status == AccountStatus.PENDING_VERIFICATION;
    }

    /** @return identificador de la cuenta */
    public UUID getId() {
        return id;
    }

    /** @return identificador del usuario en Firebase Auth */
    public String getFirebaseUid() {
        return firebaseUid;
    }

    /** @return nombres de la persona */
    public String getFirstName() {
        return firstName;
    }

    /** @return apellidos de la persona */
    public String getLastName() {
        return lastName;
    }

    /** @return fecha de nacimiento declarada; vacía en las cuentas anteriores a que el registro la exigiera */
    public Optional<BirthDate> getBirthDate() {
        return Optional.ofNullable(birthDate);
    }

    /** @return celular declarado, vacío si no lo dio */
    public Optional<PhoneNumber> getPhoneNumber() {
        return Optional.ofNullable(phoneNumber);
    }

    /** @return pronombres declarados, vacíos si no los dio */
    public Optional<Pronoun> getPronoun() {
        return Optional.ofNullable(pronoun);
    }

    /** @return política aceptada y cuándo; la versión {@code sin-aceptar} indica que la persona no aceptó ninguna */
    public PolicyAcceptance getPolicyAcceptance() {
        return policyAcceptance;
    }

    /** @return estado actual de la cuenta */
    public AccountStatus getStatus() {
        return status;
    }

    /** @return versión de la fila al leerla; vacía en una cuenta que aún no se guardó */
    public Optional<Long> getVersion() {
        return Optional.ofNullable(version);
    }

    private static String exigirTexto(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.trim();
    }
}
