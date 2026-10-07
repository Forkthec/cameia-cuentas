package tech.cameia.cuentas.domain.model;

import tech.cameia.cuentas.domain.exception.InvalidPronounException;

/**
 * Pronombres con los que una persona pide que se le trate.
 *
 * <p>El formulario de registro ofrece tres opciones: Él, Ella y Elle. Los identificadores
 * van en inglés por la convención de idioma del proyecto, y es ese nombre el que se
 * guarda en la columna {@code pronombres}.</p>
 *
 * <p>El registro lo exige; la cuenta guarda {@code null} solo después de la anonimización,
 * que vacía el dato.</p>
 */
public enum Pronoun {

    /** Él. */
    HE,

    /** Ella. */
    SHE,

    /** Elle. */
    THEY;

    /**
     * Lee el pronombre tal como lo escribe el contrato.
     *
     * <p>La comparación es exacta: {@code he} u {@code Otro} no son una opción, y un número
     * nunca se lee como la posición de la opción en la lista.</p>
     *
     * @param value texto recibido, ya sin ausencia ni blancos (eso lo reporta el contrato)
     * @return el pronombre elegido
     * @throws InvalidPronounException si el texto no es exactamente una de las opciones
     */
    public static Pronoun fromContract(String value) {
        for (Pronoun opcion : values()) {
            if (opcion.name().equals(value)) {
                return opcion;
            }
        }
        throw new InvalidPronounException();
    }
}
