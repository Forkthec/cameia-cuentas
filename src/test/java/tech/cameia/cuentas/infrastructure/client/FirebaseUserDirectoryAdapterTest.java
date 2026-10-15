package tech.cameia.cuentas.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.firebase.ErrorCode;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.ExportedUserRecord;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.ListUsersPage;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserMetadata;
import com.google.firebase.auth.UserRecord;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.model.DirectoryUser;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.RawPassword;
import tech.cameia.cuentas.domain.model.UserVerificationStatus;

/**
 * Prueba del adaptador descrito en {@code specs/CM-14-RegistroUsuario/spec.md}
 * (REQ-CU-02, REQ-CU-03 y REQ-CU-07).
 *
 * <p>Simula el cliente del Admin SDK porque lo que se verifica es la traducción: qué hace
 * el adaptador con cada respuesta de Firebase. Comprobar que Firebase crea usuarios es
 * trabajo de Firebase, no de esta suite, y exigiría credenciales reales.</p>
 */
@ExtendWith(OutputCaptureExtension.class)
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
    @DisplayName("Informa VERIFIED cuando el correo está verificado")
    void verificationStatus_shouldReturnVerified_whenEmailVerified() throws Exception {
        UserRecord usuario = mock(UserRecord.class);
        when(usuario.isEmailVerified()).thenReturn(true);
        when(firebaseAuth.getUser(anyString())).thenReturn(usuario);

        assertThat(adaptador.verificationStatus(UID)).isEqualTo(UserVerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("Informa NOT_VERIFIED cuando el correo no está verificado")
    void verificationStatus_shouldReturnNotVerified_whenEmailNotVerified() throws Exception {
        UserRecord usuario = mock(UserRecord.class);
        when(usuario.isEmailVerified()).thenReturn(false);
        when(firebaseAuth.getUser(anyString())).thenReturn(usuario);

        assertThat(adaptador.verificationStatus(UID)).isEqualTo(UserVerificationStatus.NOT_VERIFIED);
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

    private static final Instant LIMIT = Instant.parse("2026-10-03T08:00:00Z");
    private static final long OLD_MILLIS = LIMIT.minusSeconds(3600).toEpochMilli();

    private static ExportedUserRecord listedUser(String uid, boolean verified, long createdMillis, String... providers) {
        ExportedUserRecord user = mock(ExportedUserRecord.class);
        UserMetadata metadata = mock(UserMetadata.class);
        UserInfo[] info = new UserInfo[providers.length];
        for (int i = 0; i < providers.length; i++) {
            info[i] = mock(UserInfo.class);
            when(info[i].getProviderId()).thenReturn(providers[i]);
        }
        when(user.getUid()).thenReturn(uid);
        when(user.isEmailVerified()).thenReturn(verified);
        when(user.getProviderData()).thenReturn(info);
        when(user.getUserMetadata()).thenReturn(metadata);
        when(metadata.getCreationTimestamp()).thenReturn(createdMillis);
        return user;
    }

    private static ListUsersPage page(String nextToken, ExportedUserRecord... users) {
        ListUsersPage page = mock(ListUsersPage.class);
        when(page.getValues()).thenReturn(List.of(users));
        when(page.getNextPageToken()).thenReturn(nextToken);
        return page;
    }

    @Test
    @DisplayName("Lista solo a quien tiene un único proveedor password, sin verificar y creado antes del límite")
    void findUnverifiedPasswordUsersCreatedBefore_shouldFilterByProviderVerificationAndAge() throws Exception {
        doReturn(page("",
                listedUser("viejo-sin-verificar", false, OLD_MILLIS, "password"),
                listedUser("viejo-verificado", true, OLD_MILLIS, "password"),
                listedUser("reciente", false, LIMIT.plusSeconds(60).toEpochMilli(), "password"),
                listedUser("exacto-en-el-limite", false, LIMIT.toEpochMilli(), "password"),
                listedUser("de-google", false, OLD_MILLIS, "google.com"),
                listedUser("password-y-google", false, OLD_MILLIS, "password", "google.com"),
                listedUser("sin-proveedor", false, OLD_MILLIS))).when(firebaseAuth).listUsers(null, 1000);

        assertThat(adaptador.findUnverifiedPasswordUsersCreatedBefore(LIMIT, 1000)).containsExactly("viejo-sin-verificar");
    }

    @Test
    @DisplayName("No lista a quien Firebase no informa la fecha de creación: una edad desconocida no autoriza borrar")
    void findUnverifiedPasswordUsersCreatedBefore_shouldSkip_whenCreationDateIsUnknown() throws Exception {
        doReturn(page("",
                listedUser("sin-fecha", false, 0L, "password"),
                listedUser("viejo-sin-verificar", false, OLD_MILLIS, "password"))).when(firebaseAuth).listUsers(null, 1000);

        assertThat(adaptador.findUnverifiedPasswordUsersCreatedBefore(LIMIT, 1000)).containsExactly("viejo-sin-verificar");
    }

    @Test
    @DisplayName("Recorre las páginas con el token de la anterior hasta que se acaban")
    void findUnverifiedPasswordUsersCreatedBefore_shouldFollowPageTokens_whenSeveralPages() throws Exception {
        doReturn(page("token-2", listedUser("uno", false, OLD_MILLIS, "password"))).when(firebaseAuth).listUsers(null, 1000);
        doReturn(page("", listedUser("dos", false, OLD_MILLIS, "password"))).when(firebaseAuth).listUsers("token-2", 1000);

        assertThat(adaptador.findUnverifiedPasswordUsersCreatedBefore(LIMIT, 1000)).containsExactly("uno", "dos");
    }

    @Test
    @DisplayName("Se detiene al reunir el máximo sin pedir más páginas")
    void findUnverifiedPasswordUsersCreatedBefore_shouldStopAtMax_whenMoreAreAvailable() throws Exception {
        doReturn(page("token-2",
                listedUser("uno", false, OLD_MILLIS, "password"),
                listedUser("dos", false, OLD_MILLIS, "password"),
                listedUser("tres", false, OLD_MILLIS, "password"))).when(firebaseAuth).listUsers(null, 1000);

        assertThat(adaptador.findUnverifiedPasswordUsersCreatedBefore(LIMIT, 2)).containsExactly("uno", "dos");
        verify(firebaseAuth, never()).listUsers("token-2", 1000);
    }

    @Test
    @DisplayName("Un directorio vacío devuelve una lista vacía")
    void findUnverifiedPasswordUsersCreatedBefore_shouldReturnEmpty_whenDirectoryIsEmpty() throws Exception {
        doReturn(page("")).when(firebaseAuth).listUsers(null, 1000);

        assertThat(adaptador.findUnverifiedPasswordUsersCreatedBefore(LIMIT, 1000)).isEmpty();
    }

    @Test
    @DisplayName("Firebase caído al listar es indisponibilidad")
    void findUnverifiedPasswordUsersCreatedBefore_shouldThrowDependencyUnavailable_whenFirebaseIsDown() throws Exception {
        when(firebaseAuth.listUsers(null, 1000))
                .thenThrow(new FirebaseAuthException(ErrorCode.UNAVAILABLE, "servicio caído", null, null, null));

        assertThatThrownBy(() -> adaptador.findUnverifiedPasswordUsersCreatedBefore(LIMIT, 1000))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    @DisplayName("Un rechazo al listar por otra razón es un fallo imprevisto")
    void findUnverifiedPasswordUsersCreatedBefore_shouldThrowIllegalState_whenRejectedForOtherReason() throws Exception {
        when(firebaseAuth.listUsers(null, 1000)).thenThrow(errorDeFirebase(AuthErrorCode.UNAUTHORIZED_CONTINUE_URL));

        assertThatThrownBy(() -> adaptador.findUnverifiedPasswordUsersCreatedBefore(LIMIT, 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Firebase rechazó el listado de usuarios");
    }

    @Test
    @DisplayName("Informa NOT_FOUND cuando el usuario ya no existe en Firebase")
    void verificationStatus_shouldReturnNotFound_whenUserDoesNotExist() throws Exception {
        when(firebaseAuth.getUser(anyString()))
                .thenThrow(new FirebaseAuthException(ErrorCode.NOT_FOUND, "sin usuario", null, null,
                        AuthErrorCode.USER_NOT_FOUND));

        assertThat(adaptador.verificationStatus(UID)).isEqualTo(UserVerificationStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Firebase caído es indisponibilidad, no un estado")
    void verificationStatus_shouldThrowDependencyUnavailable_whenFirebaseIsDown() throws Exception {
        when(firebaseAuth.getUser(anyString()))
                .thenThrow(new FirebaseAuthException(ErrorCode.UNAVAILABLE, "servicio caído", null, null, null));

        assertThatThrownBy(() -> adaptador.verificationStatus(UID))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasCauseInstanceOf(FirebaseAuthException.class);
    }

    @Test
    @DisplayName("Un rechazo por otra razón es un fallo imprevisto con mensaje técnico sin datos")
    void verificationStatus_shouldThrowIllegalState_whenRejectedForOtherReason() throws Exception {
        when(firebaseAuth.getUser(anyString())).thenThrow(errorDeFirebase(AuthErrorCode.UNAUTHORIZED_CONTINUE_URL));

        assertThatThrownBy(() -> adaptador.verificationStatus(UID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Firebase rechazó la consulta del estado del correo")
                .hasMessageNotContaining(UID);
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

    @Test
    @DisplayName("Revocar las sesiones llama a Firebase con el identificador del usuario")
    void revokeSessions_shouldCallFirebaseWithUid_whenUserExists() throws Exception {
        adaptador.revokeSessions("uid-rev-1");

        verify(firebaseAuth).revokeRefreshTokens("uid-rev-1");
    }

    @Test
    @DisplayName("Un usuario que ya no existe en Firebase no tiene sesiones: no es un error")
    void revokeSessions_shouldSucceed_whenUserNotFound() throws Exception {
        doThrow(new FirebaseAuthException(ErrorCode.NOT_FOUND, "sin usuario", null, null, AuthErrorCode.USER_NOT_FOUND))
                .when(firebaseAuth).revokeRefreshTokens("uid-rev-2");

        assertThatCode(() -> adaptador.revokeSessions("uid-rev-2")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"UNAVAILABLE", "DEADLINE_EXCEEDED", "INTERNAL", "RESOURCE_EXHAUSTED"})
    @DisplayName("Si Firebase no responde o falla de su lado, la revocación es indisponibilidad")
    void revokeSessions_shouldThrowDependencyUnavailable_whenFirebaseFails(ErrorCode codigo) throws Exception {
        doThrow(new FirebaseAuthException(codigo, "fallo", null, null, null))
                .when(firebaseAuth).revokeRefreshTokens("uid-rev-3");

        assertThatThrownBy(() -> adaptador.revokeSessions("uid-rev-3")).isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    @DisplayName("Una conexión rechazada al revocar es indisponibilidad")
    void revokeSessions_shouldThrowDependencyUnavailable_whenConnectionFails() throws Exception {
        doThrow(new FirebaseAuthException(ErrorCode.UNKNOWN, "conexión rechazada",
                new java.net.ConnectException("rechazada"), null, null))
                .when(firebaseAuth).revokeRefreshTokens("uid-rev-4");

        assertThatThrownBy(() -> adaptador.revokeSessions("uid-rev-4")).isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    @DisplayName("Un rechazo de Firebase por permisos al revocar es un fallo imprevisto")
    void revokeSessions_shouldThrowIllegalState_whenFirebaseRejects() throws Exception {
        doThrow(new FirebaseAuthException(ErrorCode.PERMISSION_DENIED, "sin permisos", null, null, null))
                .when(firebaseAuth).revokeRefreshTokens("uid-rev-5");

        assertThatThrownBy(() -> adaptador.revokeSessions("uid-rev-5"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revocación");
    }

    @Test
    @DisplayName("El log de la revocación lleva el identificador y ningún token ni correo")
    void revokeSessions_shouldLogUidOnly(CapturedOutput salida) {
        adaptador.revokeSessions("uid-rev-log");

        String linea = salida.getOut().lines()
                .filter(l -> l.contains("FirebaseUserDirectoryAdapter") && l.contains("uid-rev-log"))
                .findFirst().orElseThrow();

        assertThat(linea).endsWith("Sesiones revocadas en Firebase para el usuario uid-rev-log")
                .doesNotContain("Bearer").doesNotContain("@");
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
