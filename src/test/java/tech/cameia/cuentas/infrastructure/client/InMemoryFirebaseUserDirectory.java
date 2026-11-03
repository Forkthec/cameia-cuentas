package tech.cameia.cuentas.infrastructure.client;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.model.DirectoryUser;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.RawPassword;
import tech.cameia.cuentas.domain.model.UserVerificationStatus;
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
    private final Map<String, String> proveedoresPorUid = new ConcurrentHashMap<>();
    private final Set<String> correosVerificados = ConcurrentHashMap.newKeySet();
    private final Set<String> deshabilitados = ConcurrentHashMap.newKeySet();
    private final AtomicInteger consultas = new AtomicInteger();
    private final AtomicInteger creaciones = new AtomicInteger();
    private final AtomicInteger borrados = new AtomicInteger();

    private volatile Clock reloj = Clock.systemUTC();
    private volatile boolean fallarAlEscribirElPlan;
    private volatile boolean indisponibleAlEscribirElPlan;
    private volatile boolean fallarAlBorrar;
    private volatile boolean indisponibleAlBorrar;
    private volatile boolean fallarAlConsultar;
    private volatile int consultasDeCorreoRestantes = -1;
    private volatile boolean reintentoDelSdk;
    private volatile boolean respuestaPerdida;
    private volatile String uidRechazado;
    private volatile String uidIndisponible;
    private final List<String> revokedUids = new CopyOnWriteArrayList<>();
    private volatile boolean unavailableOnRevoke;
    private volatile boolean rejectOnRevoke;

    @Override
    public synchronized void createUser(String firebaseUid, EmailAddress email, RawPassword password) {
        if (correosPorUid.containsValue(email.value()) || correosPorUid.containsKey(firebaseUid)) {
            throw new EmailAlreadyRegisteredException();
        }
        crear(firebaseUid, email.value(), password.value(), reloj.instant());
        if (respuestaPerdida) {
            // Firebase creó la credencial pero la respuesta no llegó: el cliente solo ve que no respondió
            throw new DependencyUnavailableException(new IllegalStateException("Firebase no respondió"));
        }
        if (reintentoDelSdk) {
            // El SDK reintentó una creación que sí se completó: la credencial existe y llega el conflicto
            throw new EmailAlreadyRegisteredException();
        }
    }

    private void crear(String uid, String correo, String contrasena, Instant creada) {
        // La fecha se guarda antes que el correo: findByEmail no debe ver una credencial a medias
        creacionesPorUid.put(uid, creada);
        contrasenasPorUid.put(uid, contrasena);
        correosPorUid.put(uid, correo);
        creaciones.incrementAndGet();
    }

    @Override
    public synchronized Optional<DirectoryUser> findByEmail(EmailAddress email) {
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
    public synchronized Optional<EmailAddress> findEmail(String firebaseUid) {
        consultas.incrementAndGet();
        if (consultasDeCorreoRestantes == 0) {
            throw new DependencyUnavailableException(new IllegalStateException("Firebase no respondió"));
        }
        if (consultasDeCorreoRestantes > 0) {
            consultasDeCorreoRestantes--;
        }
        if (fallarAlConsultar) {
            throw new DependencyUnavailableException(new IllegalStateException("Firebase no respondió"));
        }
        return Optional.ofNullable(correosPorUid.get(firebaseUid)).map(EmailAddress::new);
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
        if (indisponibleAlBorrar) {
            throw new DependencyUnavailableException(new IllegalStateException("Firebase no respondió"));
        }
        if (fallarAlBorrar) {
            throw new IllegalStateException("Firebase rechazó la eliminación del usuario");
        }
        borrados.incrementAndGet();
        correosPorUid.remove(firebaseUid);
        planesPorUid.remove(firebaseUid);
        contrasenasPorUid.remove(firebaseUid);
        creacionesPorUid.remove(firebaseUid);
        proveedoresPorUid.remove(firebaseUid);
        correosVerificados.remove(firebaseUid);
        deshabilitados.remove(firebaseUid);
    }

    @Override
    public synchronized List<String> findUnverifiedPasswordUsersCreatedBefore(Instant limit, int max) {
        consultas.incrementAndGet();
        if (fallarAlConsultar) {
            throw new DependencyUnavailableException(new IllegalStateException("Firebase no respondió"));
        }
        return correosPorUid.keySet().stream()
                .filter(uid -> "password".equals(proveedoresPorUid.getOrDefault(uid, "password")))
                .filter(uid -> !correosVerificados.contains(uid))
                .filter(uid -> creacionesPorUid.get(uid).isBefore(limit))
                .sorted(Comparator.comparing(creacionesPorUid::get))
                .limit(max)
                .toList();
    }

    /**
     * Crea una credencial sin cuenta local que ingresa con otro proveedor, con la fecha de creación indicada.
     *
     * @param correo correo ya normalizado
     * @param creada instante de creación de la credencial
     * @param proveedor identificador del proveedor, por ejemplo {@code google.com}
     * @return identificador de la credencial creada
     */
    public String crearSinCuentaLocalConProveedor(String correo, Instant creada, String proveedor) {
        String uid = crearSinCuentaLocal(correo, creada);
        proveedoresPorUid.put(uid, proveedor);
        return uid;
    }

    @Override
    public UserVerificationStatus verificationStatus(String firebaseUid) {
        consultas.incrementAndGet();
        if (fallarAlConsultar) {
            throw new DependencyUnavailableException(new IllegalStateException("Firebase no respondió"));
        }
        if (firebaseUid.equals(uidIndisponible)) {
            throw new DependencyUnavailableException(new IllegalStateException("Firebase no respondió"));
        }
        if (firebaseUid.equals(uidRechazado)) {
            throw new IllegalStateException("Firebase rechazó la consulta del estado del correo");
        }
        if (correosVerificados.contains(firebaseUid)) {
            return UserVerificationStatus.VERIFIED;
        }
        return correosPorUid.containsKey(firebaseUid) ? UserVerificationStatus.NOT_VERIFIED : UserVerificationStatus.NOT_FOUND;
    }

    /**
     * Hace que la consulta del estado del correo de un usuario falle como si Firebase no estuviera disponible.
     *
     * @param firebaseUid identificador del usuario cuya consulta no responde
     */
    public void quedarIndisponibleAlConsultarA(String firebaseUid) {
        this.uidIndisponible = firebaseUid;
    }

    /**
     * Hace que la consulta del estado del correo de un usuario falle como un rechazo de Firebase.
     *
     * @param firebaseUid identificador del usuario cuya consulta se rechaza
     */
    public void rejectLookupOf(String firebaseUid) {
        this.uidRechazado = firebaseUid;
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
        crear(uid, correo, "sin-contrasena", creada);
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

    /**
     * Hace que {@code findEmail} falle como si Firebase no estuviera disponible después de {@code consultasExitosas}
     * consultas de correo por identificador.
     *
     * @param consultasExitosas cuántas consultas se atienden antes de empezar a fallar
     */
    public void failAfterLookups(int consultasExitosas) {
        this.consultasDeCorreoRestantes = consultasExitosas;
    }

    /** Hace que la consulta por correo falle como si Firebase no estuviera disponible. */
    public void fallarAlConsultar() {
        this.fallarAlConsultar = true;
    }

    /** Hace que la creación quede hecha pero responda conflicto, como un reintento del SDK tras perder la respuesta. */
    public void simularReintentoDelSdk() {
        this.reintentoDelSdk = true;
    }

    /** Hace que la creación quede hecha pero no responda, como cuando vence el tiempo de lectura. */
    public void perderLaRespuestaDeLaCreacion() {
        this.respuestaPerdida = true;
    }

    /** Hace que la escritura del plan falle en la siguiente llamada. */
    public void fallarAlEscribirElPlan() {
        this.fallarAlEscribirElPlan = true;
    }

    /** Hace que la escritura del plan falle como si Firebase no estuviera disponible. */
    public void quedarIndisponibleAlEscribirElPlan() {
        this.indisponibleAlEscribirElPlan = true;
    }

    /** Hace que el borrado de una credencial falle como si Firebase no estuviera disponible. */
    public void quedarIndisponibleAlBorrar() {
        this.indisponibleAlBorrar = true;
    }

    /** Hace que el borrado de credenciales falle, para probar la compensación fallida. */
    public void fallarAlBorrar() {
        this.fallarAlBorrar = true;
    }

    /** Desactiva los fallos provocados, conservando lo que haya en el directorio. */
    public void dejarDeFallar() {
        this.fallarAlEscribirElPlan = false;
        this.indisponibleAlEscribirElPlan = false;
        this.indisponibleAlBorrar = false;
        this.fallarAlBorrar = false;
        this.fallarAlConsultar = false;
        this.consultasDeCorreoRestantes = -1;
        this.reintentoDelSdk = false;
        this.respuestaPerdida = false;
    }

    /**
     * Registra la revocación de las sesiones de un usuario, o falla si se provocó el fallo.
     *
     * @param firebaseUid identificador del usuario
     */
    @Override
    public void revokeSessions(String firebaseUid) {
        if (unavailableOnRevoke) {
            throw new DependencyUnavailableException(new IllegalStateException("Firebase no respondió"));
        }
        if (rejectOnRevoke) {
            throw new IllegalStateException("Firebase rechazó la revocación de las sesiones del usuario");
        }
        revokedUids.add(firebaseUid);
    }

    /**
     * Informa de las revocaciones recibidas.
     *
     * @return los identificadores revocados, en orden y con repeticiones
     */
    public List<String> revokedUids() {
        return List.copyOf(revokedUids);
    }

    /** Hace que la revocación falle como si Firebase no respondiera. */
    public void becomeUnavailableOnRevoke() {
        unavailableOnRevoke = true;
    }

    /** Hace que la revocación falle como un rechazo de configuración. */
    public void rejectRevocations() {
        rejectOnRevoke = true;
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
        indisponibleAlBorrar = false;
        fallarAlBorrar = false;
        reintentoDelSdk = false;
        respuestaPerdida = false;
        revokedUids.clear();
        unavailableOnRevoke = false;
        rejectOnRevoke = false;
    }
}
