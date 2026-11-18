package tech.cameia.cuentas.domain.policy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tech.cameia.cuentas.domain.exception.InvalidEmailException;
import tech.cameia.cuentas.domain.exception.SignupNotAllowedException;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.SingleLineText;

/**
 * Decide si un correo puede registrarse según la lista de correos permitidos.
 *
 * <p>Sirve para cerrar el registro de un entorno, como preproducción, a un grupo conocido de
 * personas (CM-360). Cada entrada de la lista es un correo base ({@code usuario@dominio}) o un
 * dominio completo ({@code @dominio}). Una lista vacía deja el registro abierto, que es el
 * comportamiento de producción.</p>
 *
 * <p>El correo se compara ya normalizado por {@link EmailAddress} (recorte, NFC y minúsculas) y
 * sin lo que va desde el primer {@code +} de la parte local: {@code ana+prueba@x.co} se compara
 * como {@code ana@x.co}. Un dominio de la lista acepta solo ese dominio exacto, no sus
 * subdominios.</p>
 *
 * <p>Las entradas se validan al crear la política: una entrada mal formada impide arrancar el
 * servicio, porque un error de tipeo dejaría el registro abierto o cerrado sin que nadie lo
 * note. El mensaje del error cita la posición de la entrada y nunca su valor, que es un
 * correo.</p>
 */
public class SignupAllowlistPolicy {

    /** Nombre de la variable de entorno, para que el error de arranque diga dónde corregir. */
    private static final String VARIABLE = "SIGNUP_ALLOWLIST";

    private static final char ARROBA = '@';

    private static final char MAS = '+';

    /** Parte local de relleno con la que se valida una entrada de dominio completo. */
    private static final String PARTE_LOCAL_DE_PRUEBA = "x";

    private final Set<String> correosBase;

    private final Set<String> dominios;

    /**
     * Crea la política a partir de las entradas de la lista.
     *
     * @param entradas entradas ya separadas; se recortan y las vacías se descartan. {@code null}
     *                 o una lista sin entradas deja el registro abierto
     * @throws IllegalArgumentException si una entrada no es un correo base sin {@code +} en la
     *                                  parte local ni un dominio completo; la aplicación no arranca
     */
    public SignupAllowlistPolicy(List<String> entradas) {
        Set<String> bases = new HashSet<>();
        Set<String> dominiosPermitidos = new HashSet<>();
        if (entradas != null) {
            int posicion = 0;
            for (String entrada : entradas) {
                posicion++;
                String recortada = entrada == null ? "" : SingleLineText.normalize(entrada);
                if (recortada.isEmpty()) {
                    continue;
                }
                if (recortada.charAt(0) == ARROBA) {
                    dominiosPermitidos.add(dominio(recortada, posicion));
                } else {
                    bases.add(correoBase(recortada, posicion));
                }
            }
        }
        this.correosBase = Set.copyOf(bases);
        this.dominios = Set.copyOf(dominiosPermitidos);
    }

    /**
     * Indica si el registro está abierto a cualquier correo.
     *
     * @return {@code true} si la lista no tiene entradas
     */
    public boolean isOpen() {
        return correosBase.isEmpty() && dominios.isEmpty();
    }

    /**
     * Comprueba que el correo pueda registrarse.
     *
     * @param email correo del registro, ya normalizado
     * @throws SignupNotAllowedException si la lista tiene entradas y el correo no coincide con
     *                                   ningún correo base ni con ningún dominio de ella
     */
    public void verify(EmailAddress email) {
        if (isOpen()) {
            return;
        }
        String valor = email.value();
        int arroba = valor.indexOf(ARROBA);
        String parteLocal = valor.substring(0, arroba);
        String dominio = valor.substring(arroba + 1);
        int mas = parteLocal.indexOf(MAS);
        String base = (mas >= 0 ? parteLocal.substring(0, mas) : parteLocal) + ARROBA + dominio;
        if (correosBase.contains(base) || dominios.contains(dominio)) {
            return;
        }
        throw new SignupNotAllowedException();
    }

    /** Valida una entrada {@code usuario@dominio} y la devuelve normalizada. */
    private static String correoBase(String entrada, int posicion) {
        String valor = normalizar(entrada, posicion);
        if (valor.substring(0, valor.indexOf(ARROBA)).indexOf(MAS) >= 0) {
            throw entradaInvalida(posicion);
        }
        return valor;
    }

    /** Valida una entrada {@code @dominio} y devuelve el dominio normalizado, sin la arroba. */
    private static String dominio(String entrada, int posicion) {
        String valor = normalizar(PARTE_LOCAL_DE_PRUEBA + entrada, posicion);
        return valor.substring(valor.indexOf(ARROBA) + 1);
    }

    /** Aplica la forma y la normalización de {@link EmailAddress}, la única definición de correo del servicio. */
    private static String normalizar(String candidato, int posicion) {
        try {
            return new EmailAddress(candidato).value();
        } catch (InvalidEmailException | IllegalArgumentException invalida) {
            throw entradaInvalida(posicion);
        }
    }

    private static IllegalArgumentException entradaInvalida(int posicion) {
        return new IllegalArgumentException(VARIABLE + ": la entrada " + posicion
                + " no es un correo base (usuario@dominio, sin +) ni un dominio completo (@dominio)");
    }
}
