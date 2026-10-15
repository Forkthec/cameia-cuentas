package tech.cameia.cuentas.application.service;

import org.springframework.stereotype.Service;

import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
import tech.cameia.cuentas.domain.port.FirebaseUserDirectory;

/**
 * Cierra la sesión de quien llama en todos sus dispositivos.
 *
 * <p>La identidad sale solo del encabezado que pone el Gateway: nada del cuerpo, la consulta ni la ruta decide qué sesiones se
 * revocan. No lee ni escribe la base de datos, así que no abre transacción: revocar no depende del estado de la cuenta local.</p>
 */
@Service
public class RevokeAllSessionsService {

    private final FirebaseUserDirectory directory;

    /**
     * Crea el caso de uso.
     *
     * @param directory puerto del directorio de usuarios
     */
    public RevokeAllSessionsService(FirebaseUserDirectory directory) {
        this.directory = directory;
    }

    /**
     * Revoca todas las sesiones del usuario que propaga el Gateway.
     *
     * <p>Es idempotente: repetirla vuelve a revocar y no tiene otro efecto.</p>
     *
     * @param firebaseUid identificador del usuario que propaga el Gateway
     * @throws IdentityRequiredException si el identificador es nulo, está en blanco o es más largo que el de Firebase
     * @throws DependencyUnavailableException si Firebase no respondió o falló de su lado
     */
    public void revokeAll(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()
                || firebaseUid.length() > GetCurrentAccountService.MAX_FIREBASE_UID_LENGTH) {
            throw new IdentityRequiredException(); // misma regla que la consulta y la activación de la cuenta
        }
        directory.revokeSessions(firebaseUid);
    }
}
