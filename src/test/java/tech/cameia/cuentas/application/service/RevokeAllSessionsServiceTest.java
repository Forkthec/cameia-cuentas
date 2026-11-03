package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;

/**
 * Pruebas del caso de uso que cierra la sesión de quien llama en todos sus dispositivos.
 *
 * <p>Usan el directorio en memoria, sin Spring: lo que se comprueba es qué identificador llega al directorio y que
 * repetir la operación no tiene otro efecto.</p>
 */
class RevokeAllSessionsServiceTest {

    private final InMemoryFirebaseUserDirectory directory = new InMemoryFirebaseUserDirectory();
    private final RevokeAllSessionsService service = new RevokeAllSessionsService(directory);

    @Test
    @DisplayName("Revoca las sesiones del usuario que propaga el Gateway")
    void revokeAll_shouldRevokeCallerSessions_whenIdentityValid() {
        service.revokeAll("uid-propio");

        assertThat(directory.revokedUids()).containsExactly("uid-propio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    @DisplayName("Rechaza la identidad vacía o en blanco sin tocar el directorio")
    void revokeAll_shouldRejectIdentity_whenBlank(String uid) {
        assertThatThrownBy(() -> service.revokeAll(uid)).isInstanceOf(IdentityRequiredException.class);

        assertThat(directory.revokedUids()).isEmpty();
    }

    @Test
    @DisplayName("Rechaza la identidad nula sin tocar el directorio")
    void revokeAll_shouldRejectIdentity_whenNull() {
        assertThatThrownBy(() -> service.revokeAll(null)).isInstanceOf(IdentityRequiredException.class);

        assertThat(directory.revokedUids()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {127, 128})
    @DisplayName("Acepta un identificador de hasta 128 caracteres")
    void revokeAll_shouldRevoke_whenUidHasMaximumLength(int length) {
        String uid = "a".repeat(length);

        service.revokeAll(uid);

        assertThat(directory.revokedUids()).containsExactly(uid);
    }

    @Test
    @DisplayName("Rechaza un identificador de 129 caracteres sin tocar el directorio")
    void revokeAll_shouldRejectIdentity_whenUidLongerThan128() {
        assertThatThrownBy(() -> service.revokeAll("a".repeat(129))).isInstanceOf(IdentityRequiredException.class);

        assertThat(directory.revokedUids()).isEmpty();
    }

    @Test
    @DisplayName("Repetir la operación 20 veces no tiene otro efecto que revocar de nuevo")
    void revokeAll_shouldHaveNoOtherEffect_whenRepeated20Times() {
        int usersBefore = directory.cantidadDeUsuarios();

        for (int i = 0; i < 20; i++) {
            service.revokeAll("uid-rep");
        }

        assertThat(directory.revokedUids()).hasSize(20).containsOnly("uid-rep");
        assertThat(directory.cantidadDeUsuarios()).isEqualTo(usersBefore);
    }

    @Test
    @DisplayName("Propaga la indisponibilidad del directorio")
    void revokeAll_shouldPropagateUnavailable_whenDirectoryFails() {
        directory.becomeUnavailableOnRevoke();

        assertThatThrownBy(() -> service.revokeAll("uid-propio")).isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    @DisplayName("Un usuario que no existe en el directorio no es un error")
    void revokeAll_shouldReturnNormally_whenUserDoesNotExist() {
        assertThatCode(() -> service.revokeAll("uid-que-no-existe")).doesNotThrowAnyException();

        assertThat(directory.revokedUids()).containsExactly("uid-que-no-existe");
    }
}
