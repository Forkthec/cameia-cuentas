package tech.cameia.cuentas.infrastructure.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.io.Resource;

import tech.cameia.cuentas.domain.model.SingleLineText;
import tech.cameia.cuentas.domain.policy.PasswordPolicy;

/**
 * Lee la lista de contraseñas comunes y comprueba que cumple lo que la política supone de ella.
 *
 * <p>Si la lista no cumple, la aplicación no arranca: una lista vacía, corta o mal escrita
 * dejaría pasar contraseñas que se creen rechazadas, y eso no se descubriría hasta que alguien
 * se registrara con una. Los mensajes de error dicen la causa y el número de línea, nunca la
 * contraseña.</p>
 */
public final class CommonPasswordsLoader {

    /** Entradas mínimas de la lista que usa la aplicación. */
    static final int MINIMUM_ENTRIES = 3000;

    private CommonPasswordsLoader() {
    }

    /**
     * Carga la lista.
     *
     * @param resource recurso UTF-8 con una contraseña por línea, en minúsculas, sin espacios
     *                 en los extremos y en forma NFC
     * @param minimumEntries mínimo de entradas exigido
     * @return conjunto inmutable de contraseñas
     * @throws IllegalStateException con la causa exacta si el recurso falta, no es UTF-8 válido,
     *                               está vacío, tiene menos entradas de las exigidas, una línea
     *                               vacía, una entrada de menos de 12 o más de 64 caracteres, con
     *                               mayúsculas, con espacios en los extremos o repetida
     */
    public static Set<String> load(Resource resource, int minimumEntries) {
        if (!resource.exists()) {
            throw new IllegalStateException("No existe la lista de contraseñas comunes: " + resource.getDescription());
        }
        Set<String> entries = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(),
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            int lineNumber = 0;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                lineNumber++;
                validate(line, lineNumber);
                if (!entries.add(line)) {
                    throw new IllegalStateException(failure("está repetida", lineNumber));
                }
            }
        } catch (CharacterCodingException error) {
            throw new IllegalStateException("La lista de contraseñas comunes no es UTF-8 válido", error);
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo leer la lista de contraseñas comunes: "
                    + resource.getDescription(), error);
        }
        if (entries.size() < minimumEntries) {
            throw new IllegalStateException("La lista de contraseñas comunes tiene " + entries.size()
                    + " entradas y se exigen al menos " + minimumEntries);
        }
        return Set.copyOf(entries);
    }

    /** Comprueba una entrada; el mensaje nombra la causa y la línea, nunca el valor. */
    private static void validate(String line, int lineNumber) {
        if (line.isEmpty()) {
            throw new IllegalStateException(failure("está vacía", lineNumber));
        }
        if (!line.equals(SingleLineText.normalize(line))) {
            // La política compara la contraseña recortada y en NFC: una entrada con espacios en
            // los extremos, con una marca de orden de bytes o en otra forma Unicode nunca coincidiría.
            throw new IllegalStateException(failure("tiene espacios en los extremos o no está en forma NFC",
                    lineNumber));
        }
        // Una entrada fuera de los límites de la contraseña nunca se consultaría: la regla de
        // longitud la rechaza antes. Que esté en la lista indica que la lista se generó mal.
        int length = line.codePointCount(0, line.length());
        if (length < PasswordPolicy.MINIMUM_LENGTH) {
            throw new IllegalStateException(failure("tiene menos de " + PasswordPolicy.MINIMUM_LENGTH + " caracteres",
                    lineNumber));
        }
        if (length > PasswordPolicy.MAXIMUM_LENGTH) {
            throw new IllegalStateException(failure("tiene más de " + PasswordPolicy.MAXIMUM_LENGTH + " caracteres",
                    lineNumber));
        }
        if (!line.equals(line.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(failure("tiene mayúsculas", lineNumber));
        }
    }

    private static String failure(String cause, int lineNumber) {
        return "La entrada de la línea " + lineNumber + " de la lista de contraseñas comunes " + cause;
    }
}
