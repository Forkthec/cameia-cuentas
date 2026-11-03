package tech.cameia.cuentas.infrastructure.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.Pronoun;

/**
 * Fila de la tabla {@code cuenta}.
 *
 * <p>No es el modelo de dominio: existe para hablar con JPA y sus nombres siguen la
 * convención de la base de datos, en español. La traducción hacia
 * {@code tech.cameia.cuentas.domain.model.Account} la hace el mapeador de este paquete.</p>
 *
 * <p>El esquema {@code microcuentas} no se declara aquí porque lo fija
 * {@code hibernate.default_schema}; repetirlo en cada entidad obligaría a cambiar código
 * para mover el esquema.</p>
 *
 * <p>Las marcas de tiempo las escribe esta clase y no un disparador de la base de datos,
 * porque las reglas del proyecto viven en el código. Las restricciones de la tabla siguen
 * ahí como red de seguridad.</p>
 */
@Entity
@Table(name = "cuenta")
public class AccountEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "firebase_uid", nullable = false, length = 128)
    private String firebaseUid;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "apellido", nullable = false, length = 120)
    private String apellido;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "telefono", length = 16)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(name = "pronombres", length = 60)
    private Pronoun pronombres;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 24)
    private AccountStatus estado;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @Column(name = "fecha_eliminacion")
    private Instant fechaEliminacion;

    /** Constructor que exige JPA. */
    protected AccountEntity() {
    }

    /**
     * Crea la fila a partir de los datos ya validados por el dominio.
     *
     * @param id identificador de la cuenta
     * @param firebaseUid identificador del usuario en Firebase Auth
     * @param nombre nombres de la persona
     * @param apellido apellidos de la persona
     * @param fechaNacimiento fecha de nacimiento
     * @param telefono celular en formato E.164, o {@code null}
     * @param pronombres pronombres declarados, o {@code null}
     * @param estado estado de la cuenta
     */
    public AccountEntity(UUID id, String firebaseUid, String nombre, String apellido,
            LocalDate fechaNacimiento, String telefono, Pronoun pronombres, AccountStatus estado) {
        this.id = id;
        this.firebaseUid = firebaseUid;
        this.nombre = nombre;
        this.apellido = apellido;
        this.fechaNacimiento = fechaNacimiento;
        this.telefono = telefono;
        this.pronombres = pronombres;
        this.estado = estado;
    }

    /**
     * Cambia el estado de la fila.
     *
     * @param estado estado que decidió el dominio
     */
    public void setEstado(AccountStatus estado) {
        this.estado = estado;
    }

    /** Fija las marcas de tiempo al insertar. */
    @PrePersist
    void alInsertar() {
        Instant ahora = Instant.now();
        this.fechaCreacion = ahora;
        this.fechaActualizacion = ahora;
    }

    /** Actualiza la marca de modificación. */
    @PreUpdate
    void alActualizar() {
        this.fechaActualizacion = Instant.now();
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
    public String getNombre() {
        return nombre;
    }

    /** @return apellidos de la persona */
    public String getApellido() {
        return apellido;
    }

    /** @return fecha de nacimiento */
    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    /** @return celular en formato E.164, o {@code null} */
    public String getTelefono() {
        return telefono;
    }

    /** @return pronombres declarados, o {@code null} */
    public Pronoun getPronombres() {
        return pronombres;
    }

    /** @return estado de la cuenta */
    public AccountStatus getEstado() {
        return estado;
    }

    /** @return versión para el bloqueo optimista */
    public Long getVersion() {
        return version;
    }

    /** @return instante de creación de la fila */
    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    /** @return instante de la última modificación */
    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    /** @return instante de anonimización, o {@code null} si la cuenta sigue vigente */
    public Instant getFechaEliminacion() {
        return fechaEliminacion;
    }
}
