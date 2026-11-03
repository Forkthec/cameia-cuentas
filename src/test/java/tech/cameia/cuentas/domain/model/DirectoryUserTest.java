package tech.cameia.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class DirectoryUserTest {

    @Test
    void guardaLosDatosDeLaCredencial() {
        DirectoryUser usuario = new DirectoryUser("uid-1", Instant.EPOCH, true);

        assertThat(usuario.uid()).isEqualTo("uid-1");
        assertThat(usuario.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(usuario.disabled()).isTrue();
    }

    @Test
    void rechazaUnIdentificadorNulo() {
        assertThatThrownBy(() -> new DirectoryUser(null, Instant.EPOCH, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rechazaUnaFechaDeCreacionNula() {
        assertThatThrownBy(() -> new DirectoryUser("uid-1", null, false))
                .isInstanceOf(NullPointerException.class);
    }
}
