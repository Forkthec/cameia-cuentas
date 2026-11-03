package tech.cameia.cuentas.domain.model;

import java.text.Normalizer;
import java.util.Objects;

/**
 * Texto de una línea tal como el negocio lo entiende: sin espacios en los extremos y en forma
 * Unicode NFC, de modo que un mismo nombre escrito de dos maneras sea el mismo valor.
 *
 * <p>«Espacio» es lo que recorta {@code String.prototype.trim} de JavaScript en el cliente: los
 * de {@link Character#isWhitespace(int)}, los separadores de espacio de Unicode (incluido el
 * espacio duro U+00A0) y U+FEFF. Así cliente y servidor recortan igual. La longitud se cuenta
 * en puntos de código, no en unidades {@code char}, para que un carácter fuera del plano básico
 * (un emoji, una letra matemática) valga uno y no dos.</p>
 *
 * @param value texto recortado y normalizado; vacío si no había nada más que espacios
 */
public record SingleLineText(String value) {

    /** Espacio con el que se une una secuencia de espacios internos de un nombre. */
    private static final int SPACE = ' ';

    /**
     * Normaliza el texto recibido.
     *
     * @throws NullPointerException si el texto es nulo; quien admita la ausencia usa
     *                              {@link #normalize(String)}
     */
    public SingleLineText {
        value = normalize(Objects.requireNonNull(value, "El texto es obligatorio"));
    }

    /**
     * Recorta y normaliza un texto sin rechazar la ausencia.
     *
     * @param raw texto recibido; puede ser {@code null}
     * @return el texto recortado y en NFC, o {@code null} si {@code raw} era {@code null}
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return trim(Normalizer.normalize(raw, Normalizer.Form.NFC));
    }

    /**
     * Normaliza un nombre o apellido: además del recorte y la forma NFC, une cada secuencia de
     * espacios internos en un solo espacio, porque «María  José» y «María José» son el mismo
     * nombre para quien lo escribe.
     *
     * @param raw texto recibido; puede ser {@code null}
     * @return el nombre normalizado, o {@code null} si {@code raw} era {@code null}
     */
    public static String normalizeName(String raw) {
        String text = normalize(raw);
        if (text == null) {
            return null;
        }
        StringBuilder joined = new StringBuilder(text.length());
        boolean previousWasSpace = false;
        // Por puntos de código: un carácter fuera del plano básico no se parte en dos.
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            boolean space = isSpace(codePoint);
            if (!space) {
                joined.appendCodePoint(codePoint);
            } else if (!previousWasSpace) {
                joined.appendCodePoint(SPACE);
            }
            previousWasSpace = space;
            index += Character.charCount(codePoint);
        }
        return joined.toString();
    }

    /**
     * Indica la longitud del texto.
     *
     * @return cantidad de puntos de código del texto
     */
    public int length() {
        return value.codePointCount(0, value.length());
    }

    /**
     * Indica si no quedó nada tras recortar.
     *
     * @return {@code true} si el texto no tiene ningún carácter
     */
    public boolean isEmpty() {
        return value.isEmpty();
    }

    /** Quita los espacios de los extremos avanzando por puntos de código, no por {@code char}. */
    private static String trim(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.codePointAt(start))) {
            start += Character.charCount(text.codePointAt(start));
        }
        while (end > start && isSpace(text.codePointBefore(end))) {
            end -= Character.charCount(text.codePointBefore(end));
        }
        return text.substring(start, end);
    }

    /**
     * Indica si un carácter es un espacio para el recorte.
     *
     * <p>{@code Character.isWhitespace} excluye a propósito el espacio duro (U+00A0) y sus
     * variantes; por eso se suma la categoría de separadores de espacio de Unicode. U+FEFF es
     * la marca de orden de bytes, que el recorte de JavaScript también quita.</p>
     */
    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.getType(codePoint) == Character.SPACE_SEPARATOR
                || codePoint == 0xFEFF;
    }
}
