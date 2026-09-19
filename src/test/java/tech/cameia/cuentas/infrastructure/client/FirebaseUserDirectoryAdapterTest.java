package tech.cameia.cuentas.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import com.google.firebase.ErrorCode;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;

import org.junit.jupiter.api.Test;

import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.RawPassword;

/**
 * Prueba del adaptador descrito en {@code specs/CM-14-RegistroUsuario/spec.md}
 * (REQ-CU-02, REQ-CU-03 y REQ-CU-07).
 *
 * <p>Simula el cliente del Admin SDK porque lo que se verifica es la traducción: qué hace
 * el adaptador con cada respuesta de Firebase. Comprobar que Firebase crea usuarios es
 * trabajo de Firebase, no de esta suite, y exigiría credenciales reales.</p>
 */
class FirebaseUserDirectoryAdapterTest {

    private static final String UID = "uid-firebase";

    private final FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
    private final FirebaseUserDirectoryAdapter adaptador = new FirebaseUserDirectoryAdapter(firebaseAuth);

    @Test
    void devuelveElIdentificadorDelUsuarioCreado() throws Exception {
        UserRecord creado = mock(UserRecord.class);
        when(creado.getUid()).thenReturn(UID);
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(creado);

        String uid = adaptador.createUser(new EmailAddress("ana@cameia.tech"),
                new RawPassword("frase secreta larga"));

        assertThat(uid).isEqualTo(UID);
    }

    @Test
    void traduceElCorreoRepetidoASuExcepcionDeNegocio() throws Exception {
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class)))
                .thenThrow(errorDeFirebase(AuthErrorCode.EMAIL_ALREADY_EXISTS));

        assertThatThrownBy(() -> adaptador.createUser(new EmailAddress("ana@cameia.tech"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Este correo ya se encuentra registrado");
    }

    @Test
    void cualquierOtroErrorDeFirebaseNoSeConfundeConUnCorreoRepetido() throws Exception {
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class)))
                .thenThrow(errorDeFirebase(AuthErrorCode.INVALID_ID_TOKEN));

        assertThatThrownBy(() -> adaptador.createUser(new EmailAddress("ana@cameia.tech"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void escribeElPlanGratuitoComoCustomClaim() throws Exception {
        adaptador.assignFreePlanClaim(UID);

        verify(firebaseAuth).setCustomUserClaims(eq(UID), eq(Map.of("plan", "FREE")));
    }

    @Test
    void informaElEstadoDelCorreo() throws Exception {
        UserRecord usuario = mock(UserRecord.class);
        when(usuario.isEmailVerified()).thenReturn(true);
        when(firebaseAuth.getUser(anyString())).thenReturn(usuario);

        assertThat(adaptador.isEmailVerified(UID)).isTrue();
    }

    @Test
    void elMensajeDeErrorNoRevelaLaContrasenia() throws Exception {
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class)))
                .thenThrow(errorDeFirebase(AuthErrorCode.UNAUTHORIZED_CONTINUE_URL));

        assertThatThrownBy(() -> adaptador.createUser(new EmailAddress("ana@cameia.tech"),
                new RawPassword("frase secreta larga")))
                .hasMessageNotContaining("frase secreta larga")
                .hasMessageNotContaining("ana@cameia.tech");
    }

    /**
     * Construye el error tal como lo lanza el SDK.
     *
     * <p>No se simula: {@code getAuthErrorCode()} no admite simulacro, y usar la excepción
     * real garantiza que la traducción se prueba contra el tipo que llega en producción.</p>
     *
     * @param codigo código de error de autenticación que reporta Firebase
     * @return excepción equivalente a la del SDK
     */
    private FirebaseAuthException errorDeFirebase(AuthErrorCode codigo) {
        return new FirebaseAuthException(ErrorCode.INVALID_ARGUMENT, "error simulado de Firebase",
                null, null, codigo);
    }
}
