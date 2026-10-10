package tech.cameia.cuentas.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.google.firebase.ErrorCode;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserMetadata;
import com.google.firebase.auth.UserRecord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.model.DirectoryUser;
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
    void creaLaCredencialEnFirebase() throws Exception {
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(mock(UserRecord.class));

        adaptador.createUser(UID, new EmailAddress("ana@cameia.tech"), new RawPassword("frase secreta larga"));

        verify(firebaseAuth).createUser(any(UserRecord.CreateRequest.class));
    }

    @Test
    void traduceElIdentificadorRepetidoALaMismaExcepcionQueElCorreoRepetido() throws Exception {
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class)))
                .thenThrow(errorDeFirebase(AuthErrorCode.UID_ALREADY_EXISTS));

        assertThatThrownBy(() -> adaptador.createUser(UID, new EmailAddress("ana@cameia.tech"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void traduceElCorreoRepetidoASuExcepcionDeNegocio() throws Exception {
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class)))
                .thenThrow(errorDeFirebase(AuthErrorCode.EMAIL_ALREADY_EXISTS));

        assertThatThrownBy(() -> adaptador.createUser(UID, new EmailAddress("ana@cameia.tech"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Ese correo ya tiene una cuenta.");
    }

    @Test
    void cualquierOtroErrorDeFirebaseNoSeConfundeConUnCorreoRepetido() throws Exception {
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class)))
                .thenThrow(errorDeFirebase(AuthErrorCode.INVALID_ID_TOKEN));

        assertThatThrownBy(() -> adaptador.createUser(UID, new EmailAddress("ana@cameia.tech"),
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

        assertThatThrownBy(() -> adaptador.createUser(UID, new EmailAddress("ana@cameia.tech"),
                new RawPassword("frase secreta larga")))
                .hasMessageNotContaining("frase secreta larga")
                .hasMessageNotContaining("ana@cameia.tech");
    }

    @Test
    void borrarUnaCredencialQueNoExisteNoEsUnError() throws Exception {
        doThrow(errorDeFirebase(AuthErrorCode.USER_NOT_FOUND)).when(firebaseAuth).deleteUser(UID);

        assertThatCode(() -> adaptador.deleteUser(UID)).doesNotThrowAnyException();
    }

    @Test
    void eliminaLaCredencialDelUsuario() throws Exception {
        adaptador.deleteUser(UID);

        verify(firebaseAuth).deleteUser(UID);
    }

    @Test
    void siNoPuedeConsultarElCorreoFallaSinInventarUnEstado() throws Exception {
        when(firebaseAuth.getUser(anyString()))
                .thenThrow(new FirebaseAuthException(ErrorCode.NOT_FOUND, "sin usuario", null, null,
                        AuthErrorCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> adaptador.isEmailVerified(UID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Firebase no pudo confirmar el estado del correo");
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"UNAVAILABLE", "DEADLINE_EXCEEDED", "INTERNAL"})
    void firebaseNoDisponibleAlCrearElUsuarioEsIndisponibilidad(ErrorCode codigo) throws Exception {
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class)))
                .thenThrow(new FirebaseAuthException(codigo, "fallo del servidor", null, null, null));

        assertThatThrownBy(() -> adaptador.createUser(UID, new EmailAddress("ana@cameia.tech"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessage("Ocurrió un error. Inténtalo de nuevo.")
                .hasCauseInstanceOf(FirebaseAuthException.class);
    }

    @Test
    void unaConexionRechazadaEsIndisponibilidad() throws Exception {
        // Así informa el SDK una conexión rechazada: código UNKNOWN con una causa de E/S.
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class)))
                .thenThrow(new FirebaseAuthException(ErrorCode.UNKNOWN, "conexión rechazada",
                        new IOException("Connection refused"), null, null));

        assertThatThrownBy(() -> adaptador.createUser(UID, new EmailAddress("ana@cameia.tech"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void unErrorDesconocidoSinCausaDeEntradaYSalidaNoEsIndisponibilidad() throws Exception {
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class)))
                .thenThrow(new FirebaseAuthException(ErrorCode.UNKNOWN, "respuesta inesperada", null, null, null));

        assertThatThrownBy(() -> adaptador.createUser(UID, new EmailAddress("ana@cameia.tech"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unDatoQueFirebaseRechazaNoEsIndisponibilidad() throws Exception {
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class)))
                .thenThrow(new FirebaseAuthException(ErrorCode.INVALID_ARGUMENT, "correo inválido", null, null,
                        AuthErrorCode.INVALID_ID_TOKEN));

        assertThatThrownBy(() -> adaptador.createUser(UID, new EmailAddress("ana@cameia.tech"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void firebaseNoDisponibleAlEscribirElPlanOAlBorrarEsIndisponibilidad() throws Exception {
        FirebaseAuthException caido = new FirebaseAuthException(ErrorCode.UNAVAILABLE, "caído", null, null, null);
        doThrow(caido).when(firebaseAuth).setCustomUserClaims(eq(UID), any());
        doThrow(caido).when(firebaseAuth).deleteUser(UID);

        assertThatThrownBy(() -> adaptador.assignFreePlanClaim(UID)).isInstanceOf(DependencyUnavailableException.class);
        assertThatThrownBy(() -> adaptador.deleteUser(UID)).isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void unRechazoAlEscribirElPlanOAlBorrarSigueSiendoUnFalloImprevisto() throws Exception {
        FirebaseAuthException rechazo = new FirebaseAuthException(ErrorCode.PERMISSION_DENIED, "sin permisos", null, null,
                null);
        doThrow(rechazo).when(firebaseAuth).setCustomUserClaims(eq(UID), any());
        doThrow(rechazo).when(firebaseAuth).deleteUser(UID);

        assertThatThrownBy(() -> adaptador.assignFreePlanClaim(UID))
                .isInstanceOf(IllegalStateException.class).hasMessage("Firebase rechazó la escritura del plan del usuario");
        assertThatThrownBy(() -> adaptador.deleteUser(UID))
                .isInstanceOf(IllegalStateException.class).hasMessage("Firebase rechazó la eliminación del usuario");
    }

    @Test
    void devuelveLaCredencialDelCorreoExistente() throws Exception {
        UserRecord usuario = mock(UserRecord.class);
        UserMetadata metadatos = mock(UserMetadata.class);
        when(usuario.getUid()).thenReturn(UID);
        when(usuario.getUserMetadata()).thenReturn(metadatos);
        when(metadatos.getCreationTimestamp()).thenReturn(1_000L);
        when(usuario.isDisabled()).thenReturn(true);
        when(firebaseAuth.getUserByEmail("ana@cameia.tech")).thenReturn(usuario);

        Optional<DirectoryUser> encontrada = adaptador.findByEmail(new EmailAddress("ana@cameia.tech"));

        assertThat(encontrada).contains(new DirectoryUser(UID, Instant.ofEpochMilli(1_000L), true));
    }

    @Test
    void findEmail_shouldReturnNormalizedEmail_whenUserExists() throws Exception {
        UserRecord usuario = mock(UserRecord.class);
        when(usuario.getEmail()).thenReturn("Ana.Perez@Ejemplo.test");
        when(firebaseAuth.getUser(UID)).thenReturn(usuario);

        assertThat(adaptador.findEmail(UID)).contains(new EmailAddress("ana.perez@ejemplo.test"));
    }

    @Test
    void findEmail_shouldBeEmpty_whenUserNotFound() throws Exception {
        when(firebaseAuth.getUser(UID)).thenThrow(errorDeFirebase(AuthErrorCode.USER_NOT_FOUND));

        assertThat(adaptador.findEmail(UID)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void findEmail_shouldBeEmpty_whenUserHasNoEmail(String correo) throws Exception {
        UserRecord usuario = mock(UserRecord.class);
        when(usuario.getEmail()).thenReturn(correo);
        when(firebaseAuth.getUser(UID)).thenReturn(usuario);

        assertThat(adaptador.findEmail(UID)).isEmpty();
    }

    @Test
    void findEmail_shouldBeEmpty_whenStoredEmailIsNotValid() throws Exception {
        UserRecord usuario = mock(UserRecord.class);
        when(usuario.getEmail()).thenReturn("sin-arroba");
        when(firebaseAuth.getUser(UID)).thenReturn(usuario);

        assertThat(adaptador.findEmail(UID)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"UNAVAILABLE", "DEADLINE_EXCEEDED", "INTERNAL"})
    void findEmail_shouldThrowDependencyUnavailable_whenFirebaseIsUnavailable(ErrorCode codigo) throws Exception {
        when(firebaseAuth.getUser(UID))
                .thenThrow(new FirebaseAuthException(codigo, "fallo del servidor", null, null, null));

        assertThatThrownBy(() -> adaptador.findEmail(UID)).isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void findEmail_shouldThrowTechnicalFailure_whenFirebaseRejectsTheQuery() throws Exception {
        when(firebaseAuth.getUser(UID))
                .thenThrow(new FirebaseAuthException(ErrorCode.PERMISSION_DENIED, "sin permiso", null, null, null));

        assertThatThrownBy(() -> adaptador.findEmail(UID))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void unCorreoSinCredencialDevuelveVacio() throws Exception {
        when(firebaseAuth.getUserByEmail(anyString()))
                .thenThrow(new FirebaseAuthException(ErrorCode.NOT_FOUND, "sin usuario", null, null,
                        AuthErrorCode.USER_NOT_FOUND));

        assertThat(adaptador.findByEmail(new EmailAddress("ana@cameia.tech"))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"UNAVAILABLE", "DEADLINE_EXCEEDED", "INTERNAL"})
    void laIndisponibilidadAlConsultarEsUnFalloDeDependencia(ErrorCode codigo) throws Exception {
        when(firebaseAuth.getUserByEmail(anyString()))
                .thenThrow(new FirebaseAuthException(codigo, "fallo del servidor", null, null, null));

        assertThatThrownBy(() -> adaptador.findByEmail(new EmailAddress("ana@cameia.tech")))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void unErrorDeEntradaYSalidaSinRespuestaAlConsultarEsUnFalloDeDependencia() throws Exception {
        when(firebaseAuth.getUserByEmail(anyString()))
                .thenThrow(new FirebaseAuthException(ErrorCode.UNKNOWN, "conexión rechazada",
                        new IOException("Connection refused"), null, null));

        assertThatThrownBy(() -> adaptador.findByEmail(new EmailAddress("ana@cameia.tech")))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void laCuotaAgotadaEsUnFalloDeDependencia() throws Exception {
        FirebaseAuthException cuota = new FirebaseAuthException(ErrorCode.RESOURCE_EXHAUSTED, "cuota agotada",
                null, null, null);
        when(firebaseAuth.getUserByEmail(anyString())).thenThrow(cuota);
        when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenThrow(cuota);
        doThrow(cuota).when(firebaseAuth).setCustomUserClaims(eq(UID), any());

        assertThatThrownBy(() -> adaptador.findByEmail(new EmailAddress("ana@cameia.tech")))
                .isInstanceOf(DependencyUnavailableException.class);
        assertThatThrownBy(() -> adaptador.createUser(UID, new EmailAddress("ana@cameia.tech"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(DependencyUnavailableException.class);
        assertThatThrownBy(() -> adaptador.assignFreePlanClaim(UID))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void cualquierOtroRechazoAlConsultarEsUnFalloTecnicoSinElCorreo() throws Exception {
        when(firebaseAuth.getUserByEmail(anyString()))
                .thenThrow(new FirebaseAuthException(ErrorCode.PERMISSION_DENIED, "sin permiso", null, null, null));

        assertThatThrownBy(() -> adaptador.findByEmail(new EmailAddress("ana@cameia.tech")))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(DependencyUnavailableException.class)
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
