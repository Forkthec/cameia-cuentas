package tech.cameia.cuentas.domain.model;

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
    THEY
}
