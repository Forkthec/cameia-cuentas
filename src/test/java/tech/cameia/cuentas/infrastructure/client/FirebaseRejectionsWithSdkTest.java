package tech.cameia.cuentas.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.UUID;

import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
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

    private static final String UID = "uid-firebase";

    private FirebaseApp app;
    private MockLowLevelHttpRequest request;

    @AfterEach
    void closeApp() {
        if (app != null) {
            app.delete();
        }
    }

    @Test
    @DisplayName("Un correo que Firebase rechaza es un correo inválido en su campo")
    void createUser_shouldThrowInvalidEmail_whenFirebaseRejectsTheEmail() {
        FirebaseUserDirectoryAdapter adapter = adapterRespondingWith(400, "INVALID_EMAIL");

        assertThatThrownBy(() -> adapter.createUser(UID, new EmailAddress("ana..perez@correo.co"),
                new RawPassword("frase secreta larga")))
                .isInstanceOfSatisfying(InvalidEmailException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.EMAIL_INVALID_FORMAT);
                    assertThat(error.getField()).isEqualTo("email");
                })
                .hasMessage("Ingresa un correo electrónico válido.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMAIL_EXISTS", "DUPLICATE_LOCAL_ID"})
    @DisplayName("Tanto el correo como el identificador repetidos son un conflicto, y el identificador viaja en la petición")
    void createUser_shouldThrowEmailAlreadyRegistered_whenFirebaseReportsEitherConflict(String serviceCode)
            throws Exception {
        FirebaseUserDirectoryAdapter adapter = adapterRespondingWith(400, serviceCode);

        assertThatThrownBy(() -> adapter.createUser(UID, new EmailAddress("ana@correo.co"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(request.getContentAsString()).contains("\"localId\":\"" + UID + "\"");
    }

    @Test
    @DisplayName("Un correo que Firebase rechaza al consultar es un correo inválido en su campo")
    void findByEmail_shouldThrowInvalidEmail_whenFirebaseRejectsTheEmail() {
        FirebaseUserDirectoryAdapter adapter = adapterRespondingWith(400, "INVALID_EMAIL");

        assertThatThrownBy(() -> adapter.findByEmail(new EmailAddress("ana..perez@correo.co")))
                .isInstanceOfSatisfying(InvalidEmailException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.EMAIL_INVALID_FORMAT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WEAK_PASSWORD : Password should be at least 6 characters",
        "PASSWORD_DOES_NOT_MEET_REQUIREMENTS : Missing password requirements: [Password must contain a numeric character]",
        "OPERATION_NOT_ALLOWED : Password sign-in is disabled for this project",
        "INVALID_EMAIL_DOMAIN"})
    @DisplayName("Otro rechazo es un defecto de configuración con su código en el mensaje técnico")
    void createUser_shouldThrowIllegalStateWithServiceCode_whenFirebaseRejectsForAnotherReason(String serviceMessage) {
        FirebaseUserDirectoryAdapter adapter = adapterRespondingWith(400, serviceMessage);
        String code = serviceMessage.split(" ")[0];

        assertThatThrownBy(() -> adapter.createUser(UID, new EmailAddress("ana@correo.co"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(InvalidEmailException.class)
                .hasMessage("Firebase rechazó la creación del usuario [serviceCode=" + code + "]")
                .hasMessageNotContaining("ana@correo.co");
    }

    @Test
    @DisplayName("Un cuerpo sin código legible se registra como desconocido")
    void createUser_shouldReportUnknownCode_whenResponseBodyHasNoReadableCode() {
        FirebaseUserDirectoryAdapter adapter = adapterRespondingWithBody(400, "no es json");

        assertThatThrownBy(() -> adapter.createUser(UID, new EmailAddress("ana@correo.co"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Firebase rechazó la creación del usuario [serviceCode=desconocido]");
    }

    @Test
    @DisplayName("Borrar una credencial que no existe no es un error")
    void deleteUser_shouldNotThrow_whenTheUserDoesNotExist() {
        FirebaseUserDirectoryAdapter adapter = adapterRespondingWith(400, "USER_NOT_FOUND");

        assertThatCode(() -> adapter.deleteUser("uid-inexistente")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un rechazo al borrar con respuesta HTTP no es indisponibilidad")
    void deleteUser_shouldNotThrowDependencyUnavailable_whenFirebaseAnswersWithHttpRejection() {
        // El SDK adjunta la respuesta HTTP como causa de E/S; no es una conexión fallida.
        FirebaseUserDirectoryAdapter adapter = adapterRespondingWith(403, "PERMISSION_DENIED");

        assertThatThrownBy(() -> adapter.deleteUser("uid-inexistente"))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(DependencyUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    @DisplayName("Un error del servidor de Firebase sí es indisponibilidad")
    void createUser_shouldThrowDependencyUnavailable_whenFirebaseAnswersWithServerError(int status) {
        FirebaseUserDirectoryAdapter adapter = adapterRespondingWith(status, "BACKEND_ERROR");

        assertThatThrownBy(() -> adapter.createUser(UID, new EmailAddress("ana@correo.co"),
                new RawPassword("frase secreta larga")))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    private FirebaseUserDirectoryAdapter adapterRespondingWith(int status, String serviceMessage) {
        String body = "{\"error\":{\"code\":" + status + ",\"message\":\"" + serviceMessage
                + "\",\"errors\":[{\"message\":\"" + serviceMessage + "\",\"domain\":\"global\",\"reason\":\"invalid\"}]}}";
        return adapterRespondingWithBody(status, body);
    }

    private FirebaseUserDirectoryAdapter adapterRespondingWithBody(int status, String body) {
        request = new MockLowLevelHttpRequest().setResponse(new MockLowLevelHttpResponse()
                .setStatusCode(status)
                .setContentType("application/json; charset=UTF-8")
                .setContent(body));
        MockHttpTransport transport = new MockHttpTransport.Builder().setLowLevelHttpRequest(request).build();
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.create(new AccessToken("token-de-prueba",
                        new Date(System.currentTimeMillis() + 3_600_000))))
                .setProjectId("demo-cameia")
                .setHttpTransport(transport)
                .build();
        app = FirebaseApp.initializeApp(options, "rechazos-" + UUID.randomUUID());
        return new FirebaseUserDirectoryAdapter(FirebaseAuth.getInstance(app));
    }
}
