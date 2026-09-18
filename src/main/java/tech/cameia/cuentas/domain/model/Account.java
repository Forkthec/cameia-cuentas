package tech.cameia.cuentas.domain.model;

import java.util.Optional;
import java.util.UUID;

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
    private AccountStatus status;

    private Account(UUID id, String firebaseUid, String firstName, String lastName,
            BirthDate birthDate, PhoneNumber phoneNumber, Pronoun pronoun, AccountStatus status) {
        this.id = id;
        this.firebaseUid = firebaseUid;
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
        this.pronoun = pronoun;
        this.status = status;
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
     * @return cuenta nueva en estado {@code PENDING_VERIFICATION}
     * @throws IllegalArgumentException si falta el identificador de Firebase, los nombres
     *                                  o los apellidos
     */
    public static Account register(String firebaseUid, String firstName, String lastName,
            BirthDate birthDate, PhoneNumber phoneNumber, Pronoun pronoun) {
        return new Account(
                UUID.randomUUID(),
                exigirTexto(firebaseUid, "El identificador de Firebase es obligatorio"),
                exigirTexto(firstName, "Los nombres son obligatorios"),
                exigirTexto(lastName, "Los apellidos son obligatorios"),
                birthDate,
                phoneNumber,
                pronoun,
                AccountStatus.PENDING_VERIFICATION);
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
     * @param birthDate fecha de nacimiento
     * @param phoneNumber celular, o {@code null}
     * @param pronoun pronombres, o {@code null}
     * @param status estado almacenado
     * @return cuenta con el estado que tenía en la base de datos
     */
    public static Account rebuild(UUID id, String firebaseUid, String firstName, String lastName,
            BirthDate birthDate, PhoneNumber phoneNumber, Pronoun pronoun, AccountStatus status) {
        return new Account(id, firebaseUid, firstName, lastName, birthDate, phoneNumber, pronoun, status);
    }

    /**
     * Marca la cuenta como verificada y lista para usarse.
     *
     * <p>Es idempotente: activar una cuenta que ya está activa no cambia nada ni falla, de
     * modo que el frontend puede reintentar la operación sin efectos raros.</p>
     *
     * @throws IllegalStateException si la cuenta está bloqueada o anonimizada; verificar
     *                               un correo no revierte ninguna de esas dos decisiones
     */
    public void activate() {
        if (status == AccountStatus.ACTIVE) {
            return;
        }
        if (status != AccountStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException(
                    "Una cuenta en estado " + status + " no se activa al verificar el correo");
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

    /** @return fecha de nacimiento declarada */
    public BirthDate getBirthDate() {
        return birthDate;
    }

    /** @return celular declarado, vacío si no lo dio */
    public Optional<PhoneNumber> getPhoneNumber() {
        return Optional.ofNullable(phoneNumber);
    }

    /** @return pronombres declarados, vacíos si no los dio */
    public Optional<Pronoun> getPronoun() {
        return Optional.ofNullable(pronoun);
    }

    /** @return estado actual de la cuenta */
    public AccountStatus getStatus() {
        return status;
    }

    private static String exigirTexto(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.trim();
    }
}
