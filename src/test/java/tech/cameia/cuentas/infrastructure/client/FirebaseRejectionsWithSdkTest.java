package tech.cameia.cuentas.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.UUID;

import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.InvalidEmailException;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.RawPassword;

/**
 * Comprueba la traducción de los rechazos de Firebase con el SDK real, no con una excepción
 * construida a mano.
 *
 * <p>Un transporte HTTP simulado devuelve el cuerpo exacto que manda Identity Toolkit y el SDK lo
 * procesa como en producción. Así se prueba lo que el adaptador recibe de verdad: para estos
 * rechazos el SDK no tiene código propio y todos llegan como {@code INVALID_ARGUMENT}.</p>
 */
class FirebaseRejectionsWithSdkTest {

    private FirebaseApp app;

    @AfterEach
    void cerrarApp() {
        if (app != null) {
            app.delete();
        }
    }

    @Test
    void unCorreoQueFirebaseRechazaEsUnCorreoInvalidoEnSuCampo() {
        FirebaseUserDirectoryAdapter adaptador = adaptadorQueResponde(400, "INVALID_EMAIL");

        assertThatThrownBy(() -> adaptador.createUser(new EmailAddress("ana..perez@correo.co"),
                new RawPassword("frase secreta larga")))
                .isInstanceOfSatisfying(InvalidEmailException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.EMAIL_INVALID_FORMAT);
                    assertThat(error.getField()).isEqualTo("email");
                })
                .hasMessage("Ingresa un correo electrónico válido.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"WEAK_PASSWORD : Password should be at least 6 characters",
        "PASSWORD_DOES_NOT_MEET_REQUIREMENTS : Missing password requirements: [Password must contain a numeric character]",
        "OPERATION_NOT_ALLOWED : Password sign-in is disabled for this project",
        "INVALID_EMAIL_DOMAIN"})
    void otroRechazoEsUnDefectoDeConfiguracionConSuCodigoEnElMensajeTecnico(String mensajeDelServicio) {
        FirebaseUserDirectoryAdapter adaptador = adaptadorQueResponde(400, mensajeDelServicio);
        String codigo = mensajeDelServicio.split(" ")[0];

        assertThatThrownBy(() -> adaptador.createUser(new EmailAddress("ana@correo.co"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(InvalidEmailException.class)
                .hasMessage("Firebase rechazó la creación del usuario [codigoDelServicio=" + codigo + "]")
                .hasMessageNotContaining("ana@correo.co");
    }

    @Test
    void unCuerpoSinCodigoLegibleSeRegistraComoDesconocido() {
        FirebaseUserDirectoryAdapter adaptador = adaptadorQueRespondeCuerpo(400, "no es json");

        assertThatThrownBy(() -> adaptador.createUser(new EmailAddress("ana@correo.co"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Firebase rechazó la creación del usuario [codigoDelServicio=desconocido]");
    }

    @Test
    void unRechazoAlBorrarConRespuestaHttpNoEsIndisponibilidad() {
        // El SDK adjunta la respuesta HTTP como causa de E/S; no es una conexión fallida.
        FirebaseUserDirectoryAdapter adaptador = adaptadorQueResponde(400, "USER_NOT_FOUND");

        assertThatThrownBy(() -> adaptador.deleteUser("uid-inexistente"))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(DependencyUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    void unErrorDelServidorSiEsIndisponibilidad(int estado) {
        FirebaseUserDirectoryAdapter adaptador = adaptadorQueResponde(estado, "BACKEND_ERROR");

        assertThatThrownBy(() -> adaptador.createUser(new EmailAddress("ana@correo.co"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    private FirebaseUserDirectoryAdapter adaptadorQueResponde(int estado, String mensajeDelServicio) {
        String cuerpo = "{\"error\":{\"code\":" + estado + ",\"message\":\"" + mensajeDelServicio
                + "\",\"errors\":[{\"message\":\"" + mensajeDelServicio + "\",\"domain\":\"global\",\"reason\":\"invalid\"}]}}";
        return adaptadorQueRespondeCuerpo(estado, cuerpo);
    }

    private FirebaseUserDirectoryAdapter adaptadorQueRespondeCuerpo(int estado, String cuerpo) {
        MockHttpTransport transporte = new MockHttpTransport.Builder()
                .setLowLevelHttpResponse(new MockLowLevelHttpResponse()
                        .setStatusCode(estado)
                        .setContentType("application/json; charset=UTF-8")
                        .setContent(cuerpo))
                .build();
        FirebaseOptions opciones = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.create(new AccessToken("token-de-prueba",
                        new Date(System.currentTimeMillis() + 3_600_000))))
                .setProjectId("demo-cameia")
                .setHttpTransport(transporte)
                .build();
        app = FirebaseApp.initializeApp(opciones, "rechazos-" + UUID.randomUUID());
        return new FirebaseUserDirectoryAdapter(FirebaseAuth.getInstance(app));
    }
}
