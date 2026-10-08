package tech.cameia.cuentas.infrastructure.client;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.model.DirectoryUser;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.RawPassword;
import tech.cameia.cuentas.domain.port.FirebaseUserDirectory;

/**
 * Directorio de usuarios en memoria para las pruebas.
 *
 * <p>Es un doble del puerto, no un simulacro del SDK: las pruebas describen lo que el
 * dominio espera del directorio, no cómo responde Firebase. Gracias a eso la suite corre
 * sin credenciales ni red.</p>
 *
 * <p>Permite provocar los fallos que el registro tiene que saber manejar: un correo ya
 * registrado, un error al escribir el plan y un error al borrar la credencial durante la
 * compensación, un error al consultar por correo y una credencial deshabilitada o sin cuenta local.</p>
 *
 * <p>Es seguro entre hilos: las pruebas de concurrencia lo comparten entre dos peticiones.</p>
 */
public class InMemoryFirebaseUserDirectory implements FirebaseUserDirectory {

    private final Map<String, String> correosPorUid = new ConcurrentHashMap<>();
    private final Map<String, String> planesPorUid = new ConcurrentHashMap<>();
    private final Map<String, String> contrasenasPorUid = new ConcurrentHashMap<>();
    private final Map<String, Instant> creacionesPorUid = new ConcurrentHashMap<>();
    private final Set<String> correosVerificados = ConcurrentHashMap.newKeySet();
    private final Set<String> deshabilitados = ConcurrentHashMap.newKeySet();
    private final AtomicInteger consultas = new AtomicInteger();
    private final AtomicInteger creaciones = new AtomicInteger();
    private final AtomicInteger borrados = new AtomicInteger();

    private volatile Clock reloj = Clock.systemUTC();
    private volatile boolean fallarAlEscribirElPlan;
    private volatile boolean indisponibleAlEscribirElPlan;
    private volatile boolean fallarAlBorrar;
    private volatile boolean fallarAlConsultar;

    @Override
    public synchronized String createUser(EmailAddress email, RawPassword password) {
        if (correosPorUid.containsValue(email.value())) {
            throw new EmailAlreadyRegisteredException();
        }
        String uid = UUID.randomUUID().toString();
        correosPorUid.put(uid, email.value());
        contrasenasPorUid.put(uid, password.value());
        creacionesPorUid.put(uid, reloj.instant());
        creaciones.incrementAndGet();
        return uid;
    }

    @Override
    public Optional<DirectoryUser> findByEmail(EmailAddress email) {
        consultas.incrementAndGet();
        if (fallarAlConsultar) {
            throw new DependencyUnavailableException(new IllegalStateException("Firebase no respondió"));
        }
        return correosPorUid.entrySet().stream()
                .filter(entrada -> entrada.getValue().equals(email.value()))
                .findFirst()
                .map(entrada -> new DirectoryUser(entrada.getKey(), creacionesPorUid.get(entrada.getKey()),
                        deshabilitados.contains(entrada.getKey())));
    }

    @Override
    public void assignFreePlanClaim(String firebaseUid) {
        if (fallarAlEscribirElPlan) {
            throw new IllegalStateException("Firebase rechazó la escritura del plan del usuario");
        }
        if (indisponibleAlEscribirElPlan) {
            throw new DependencyUnavailableException(new IllegalStateException("Firebase no respondió"));
        }
        planesPorUid.put(firebaseUid, "FREE");
    }

    @Override
    public void deleteUser(String firebaseUid) {
        if (fallarAlBorrar) {
            throw new IllegalStateException("Firebase rechazó la eliminación del usuario");
        }
        borrados.incrementAndGet();
        correosPorUid.remove(firebaseUid);
        planesPorUid.remove(firebaseUid);
        contrasenasPorUid.remove(firebaseUid);
        creacionesPorUid.remove(firebaseUid);
        correosVerificados.remove(firebaseUid);
        deshabilitados.remove(firebaseUid);
    }

    @Override
    public boolean isEmailVerified(String firebaseUid) {
        return correosVerificados.contains(firebaseUid);
    }

    /**
     * Indica si el directorio conserva una credencial.
     *
     * @param firebaseUid identificador del usuario
     * @return {@code true} si la credencial sigue existiendo
     */
    public boolean existe(String firebaseUid) {
        return correosPorUid.containsKey(firebaseUid);
    }

    /**
     * Indica cuántas credenciales conserva el directorio.
     *
     * @return número de usuarios creados y no borrados
     */
    public int cantidadDeUsuarios() {
        return correosPorUid.size();
    }

    /**
     * Devuelve el plan escrito como custom claim.
     *
     * @param firebaseUid identificador del usuario
     * @return el plan, o {@code null} si nunca se escribió
     */
    public String planDe(String firebaseUid) {
        return planesPorUid.get(firebaseUid);
    }

    /**
     * Devuelve la contraseña con la que se creó la credencial, para comprobar que llegó sin
     * recortes ni cambios.
     *
     * @param firebaseUid identificador del usuario
     * @return la contraseña recibida, o {@code null} si el usuario no existe
     */
    public String contrasenaDe(String firebaseUid) {
        return contrasenasPorUid.get(firebaseUid);
    }

    /**
     * Marca el correo de un usuario como verificado.
     *
     * @param firebaseUid identificador del usuario
     */
    public void marcarCorreoVerificado(String firebaseUid) {
        correosVerificados.add(firebaseUid);
    }

    /**
     * Cambia el reloj con el que se fecha cada credencial nueva.
     *
     * @param nuevoReloj reloj que usarán las credenciales creadas a partir de ahora
     */
    public void reloj(Clock nuevoReloj) {
        this.reloj = nuevoReloj;
    }

    /**
     * Crea una credencial que no tiene cuenta local, con la fecha de creación indicada.
     *
     * @param correo correo ya normalizado
     * @param creada instante de creación de la credencial
     * @return identificador de la credencial creada
     */
    public String crearSinCuentaLocal(String correo, Instant creada) {
        String uid = UUID.randomUUID().toString();
        correosPorUid.put(uid, correo);
        contrasenasPorUid.put(uid, "sin-contrasena");
        creacionesPorUid.put(uid, creada);
        creaciones.incrementAndGet();
        return uid;
    }

    /**
     * Deshabilita una credencial.
     *
     * @param firebaseUid identificador del usuario
     */
    public void deshabilitar(String firebaseUid) {
        deshabilitados.add(firebaseUid);
    }

    /**
     * Cuenta las consultas por correo recibidas.
     *
     * @return número de llamadas a {@code findByEmail}
     */
    public int consultas() {
        return consultas.get();
    }

    /**
     * Cuenta las credenciales creadas con éxito.
     *
     * @return número de creaciones, incluidas las borradas después
     */
    public int creaciones() {
        return creaciones.get();
    }

    /**
     * Cuenta los borrados de credenciales pedidos.
     *
     * @return número de llamadas a {@code deleteUser} que no fallaron
     */
    public int borrados() {
        return borrados.get();
    }

    /** Hace que la consulta por correo falle como si Firebase no estuviera disponible. */
    public void fallarAlConsultar() {
        this.fallarAlConsultar = true;
    }

    /** Hace que la escritura del plan falle en la siguiente llamada. */
    public void fallarAlEscribirElPlan() {
        this.fallarAlEscribirElPlan = true;
    }

    /** Hace que la escritura del plan falle como si Firebase no estuviera disponible. */
    public void quedarIndisponibleAlEscribirElPlan() {
        this.indisponibleAlEscribirElPlan = true;
    }

    /** Hace que el borrado de credenciales falle, para probar la compensación fallida. */
    public void fallarAlBorrar() {
        this.fallarAlBorrar = true;
    }

    /** Desactiva los fallos provocados, conservando lo que haya en el directorio. */
    public void dejarDeFallar() {
        this.fallarAlEscribirElPlan = false;
        this.indisponibleAlEscribirElPlan = false;
        this.fallarAlBorrar = false;
        this.fallarAlConsultar = false;
    }

    /** Vacía el directorio y desactiva los fallos provocados. */
    public void limpiar() {
        correosPorUid.clear();
        planesPorUid.clear();
        contrasenasPorUid.clear();
        correosVerificados.clear();
        creacionesPorUid.clear();
        deshabilitados.clear();
        consultas.set(0);
        creaciones.set(0);
        borrados.set(0);
        reloj = Clock.systemUTC();
        fallarAlConsultar = false;
        fallarAlEscribirElPlan = false;
        indisponibleAlEscribirElPlan = false;
        fallarAlBorrar = false;
    }
}
