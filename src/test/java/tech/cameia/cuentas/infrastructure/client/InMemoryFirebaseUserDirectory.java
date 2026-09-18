package tech.cameia.cuentas.infrastructure.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
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
 * compensación.</p>
 */
public class InMemoryFirebaseUserDirectory implements FirebaseUserDirectory {

    private final Map<String, String> correosPorUid = new HashMap<>();
    private final Map<String, String> planesPorUid = new HashMap<>();
    private final Set<String> correosVerificados = new HashSet<>();

    private boolean fallarAlEscribirElPlan;
    private boolean fallarAlBorrar;

    @Override
    public String createUser(EmailAddress email, RawPassword password) {
        if (correosPorUid.containsValue(email.value())) {
            throw new EmailAlreadyRegisteredException();
        }
        String uid = UUID.randomUUID().toString();
        correosPorUid.put(uid, email.value());
        return uid;
    }

    @Override
    public void assignFreePlanClaim(String firebaseUid) {
        if (fallarAlEscribirElPlan) {
            throw new IllegalStateException("Firebase rechazó la escritura del plan del usuario");
        }
        planesPorUid.put(firebaseUid, "FREE");
    }

    @Override
    public void deleteUser(String firebaseUid) {
        if (fallarAlBorrar) {
            throw new IllegalStateException("Firebase rechazó la eliminación del usuario");
        }
        correosPorUid.remove(firebaseUid);
        planesPorUid.remove(firebaseUid);
        correosVerificados.remove(firebaseUid);
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
     * Devuelve el plan escrito como custom claim.
     *
     * @param firebaseUid identificador del usuario
     * @return el plan, o {@code null} si nunca se escribió
     */
    public String planDe(String firebaseUid) {
        return planesPorUid.get(firebaseUid);
    }

    /**
     * Marca el correo de un usuario como verificado.
     *
     * @param firebaseUid identificador del usuario
     */
    public void marcarCorreoVerificado(String firebaseUid) {
        correosVerificados.add(firebaseUid);
    }

    /** Hace que la escritura del plan falle en la siguiente llamada. */
    public void fallarAlEscribirElPlan() {
        this.fallarAlEscribirElPlan = true;
    }

    /** Hace que el borrado de credenciales falle, para probar la compensación fallida. */
    public void fallarAlBorrar() {
        this.fallarAlBorrar = true;
    }

    /** Vacía el directorio y desactiva los fallos provocados. */
    public void limpiar() {
        correosPorUid.clear();
        planesPorUid.clear();
        correosVerificados.clear();
        fallarAlEscribirElPlan = false;
        fallarAlBorrar = false;
    }
}
