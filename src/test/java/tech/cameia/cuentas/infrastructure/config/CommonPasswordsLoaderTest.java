package tech.cameia.cuentas.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Prueba el cargador de la lista de contraseñas comunes sin Spring: cada causa por la que la
 * aplicación no debe arrancar y que ningún mensaje repite una contraseña.
 */
class CommonPasswordsLoaderTest {

    private static final Resource LISTA_DE_PRUEBA = new ClassPathResource("security/common-passwords-test.txt");

    @Test
    void unaListaValidaSeCargaCompleta() {
        Set<String> lista = CommonPasswordsLoader.load(LISTA_DE_PRUEBA, 20);

        assertThat(lista).hasSize(20).contains("123456789012", "password1234", "qwertyuiop123", "contraseñacomún");
    }

    @Test
    void elConjuntoCargadoNoSePuedeModificar() {
        Set<String> lista = CommonPasswordsLoader.load(LISTA_DE_PRUEBA, 20);

        assertThatThrownBy(() -> lista.add("otra-contrasena")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void conMenosEntradasDeLasExigidasNoArranca() {
        assertThatThrownBy(() -> CommonPasswordsLoader.load(LISTA_DE_PRUEBA, 21))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La lista de contraseñas comunes tiene 20 entradas y se exigen al menos 21");
    }

    @Test
    void unaLineaVaciaNoArranca() {
        assertThatThrownBy(() -> CommonPasswordsLoader.load(recurso("123456789012\n\npassword1234\n"), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("línea 2")
                .hasMessageContaining("está vacía");
    }

    @Test
    void unaEntradaDeOnceCaracteresNoArrancaNiRevelaLaEntrada() {
        assertThatThrownBy(() -> CommonPasswordsLoader.load(recurso("123456789012\ncortaonce11\n"), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("línea 2")
                .hasMessageContaining("menos de 12 caracteres")
                .hasMessageNotContaining("cortaonce11");
    }

    @Test
    void unaEntradaDe65CaracteresNoArrancaNiRevelaLaEntrada() {
        String larga = "a".repeat(65);

        assertThatThrownBy(() -> CommonPasswordsLoader.load(recurso("a".repeat(64) + "\n" + larga + "\n"), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("línea 2")
                .hasMessageContaining("más de 64 caracteres")
                .hasMessageNotContaining(larga);
    }

    @Test
    void unaEntradaDe64CaracteresSeAdmite() {
        assertThat(CommonPasswordsLoader.load(recurso("a".repeat(64) + "\n"), 1)).containsExactly("a".repeat(64));
    }

    @Test
    void unaEntradaConMayusculasNoArranca() {
        assertThatThrownBy(() -> CommonPasswordsLoader.load(recurso("Password1234\n"), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("línea 1")
                .hasMessageContaining("mayúsculas")
                .hasMessageNotContaining("Password1234");
    }

    @Test
    void unaEntradaRepetidaNoArranca() {
        assertThatThrownBy(() -> CommonPasswordsLoader.load(recurso("123456789012\npassword1234\n123456789012\n"), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("línea 3")
                .hasMessageContaining("repetida")
                .hasMessageNotContaining("123456789012");
    }

    @Test
    void unaEntradaConEspaciosEnLosExtremosNoArranca() {
        assertThatThrownBy(() -> CommonPasswordsLoader.load(recurso("password1234 \n"), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("línea 1")
                .hasMessageContaining("espacios en los extremos");
    }

    @Test
    void unArchivoConMarcaDeOrdenDeBytesNoArranca() {
        // La marca de orden de bytes quedaría pegada a la primera entrada y nunca coincidiría.
        assertThatThrownBy(() -> CommonPasswordsLoader.load(recurso("\uFEFF123456789012\n"), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("línea 1");
    }

    @Test
    void unaEntradaEnFormaNfdNoArranca() {
        assertThatThrownBy(() -> CommonPasswordsLoader.load(recurso("contrasen\u0303acomu\u0301n\n"), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forma NFC");
    }

    @Test
    void unArchivoQueNoEsUtf8NoArranca() {
        Resource latin1 = new ByteArrayResource("contraseñacomún\n".getBytes(StandardCharsets.ISO_8859_1));

        assertThatThrownBy(() -> CommonPasswordsLoader.load(latin1, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La lista de contraseñas comunes no es UTF-8 válido");
    }

    @Test
    void unArchivoVacioNoArranca() {
        assertThatThrownBy(() -> CommonPasswordsLoader.load(recurso(""), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La lista de contraseñas comunes tiene 0 entradas y se exigen al menos 1");
    }

    @Test
    void unRecursoInexistenteNoArranca() {
        assertThatThrownBy(() -> CommonPasswordsLoader.load(new ClassPathResource("security/no-existe.txt"), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No existe la lista de contraseñas comunes");
    }

    @Test
    void unRecursoQueFallaAlLeerseNoArranca() {
        Resource roto = new ByteArrayResource(new byte[0]) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("disco no disponible");
            }
        };

        assertThatThrownBy(() -> CommonPasswordsLoader.load(roto, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se pudo leer la lista de contraseñas comunes")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void aceptaFinesDeLineaDeWindows() {
        assertThat(CommonPasswordsLoader.load(recurso("123456789012\r\npassword1234\r\n"), 2))
                .containsExactlyInAnyOrder("123456789012", "password1234");
    }

    private static Resource recurso(String contenido) {
        return new ByteArrayResource(contenido.getBytes(StandardCharsets.UTF_8));
    }
}
