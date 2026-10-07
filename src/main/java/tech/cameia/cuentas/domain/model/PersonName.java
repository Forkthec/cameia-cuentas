package tech.cameia.cuentas.domain.model;

import java.util.regex.Pattern;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.InvalidPersonNameException;

/**
 * Nombres o apellidos de una persona: solo letras, espacios, apóstrofo y guion.
 *
 * <p>Se admite cualquier letra de Unicode, no solo el alfabeto latino, y también las marcas
 * combinantes: hay letras que solo existen con un acento combinante y no tienen forma
 * precompuesta. El apóstrofo se admite recto ({@code '}) y tipográfico ({@code ’}), porque
 * los teclados de los teléfonos escriben el segundo. Debe haber al menos una letra: un
 * nombre hecho solo de guiones o apóstrofos no es un nombre.</p>
 *
 * <p>El texto se guarda recortado, en NFC y con los espacios internos repetidos unidos en
 * uno. La ausencia y la longitud las rechaza antes el contrato HTTP; aquí se exigen otra vez
 * como invariante, con una excepción técnica, porque el objeto no debe poder existir sin
 * ellas.</p>
 */
public final class PersonName {

    /** Parte del nombre completo; lleva el código, el campo del contrato y el texto del rechazo. */
    public enum Part {

        /** Nombres. */
        FIRST_NAME(ErrorCode.FIRST_NAME_INVALID_CHARACTERS, "firstName",
                "El nombre solo puede contener letras, espacios, apóstrofo y guion."),

        /** Apellidos. */
        LAST_NAME(ErrorCode.LAST_NAME_INVALID_CHARACTERS, "lastName",
                "El apellido solo puede contener letras, espacios, apóstrofo y guion.");

        private final ErrorCode code;
        private final String field;
        private final String message;

        Part(ErrorCode code, String field, String message) {
            this.code = code;
            this.field = field;
            this.message = message;
        }

        /**
         * Indica el código del rechazo.
         *
         * @return código estable de la causa
         */
        public ErrorCode code() {
            return code;
        }

        /**
         * Indica el campo del contrato.
         *
         * @return nombre del campo en el JSON
         */
        public String field() {
            return field;
        }

        /**
         * Indica el texto para la persona.
         *
         * @return mensaje del criterio de aceptación
         */
        public String message() {
            return message;
        }
    }

    /** Máximo de caracteres, el mismo de la columna de la base. */
    private static final int MAX_LENGTH = 120;

    /**
     * Letras Unicode, marcas combinantes, espacio, apóstrofo recto y tipográfico y guion. Es una
     * sola clase de caracteres repetida, sin grupos anidados, así que se evalúa en tiempo lineal.
     */
    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{M} '’-]+$");

    /** Al menos una letra. */
    private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");

    private final String value;

    /**
     * Normaliza y valida una parte del nombre.
     *
     * @param raw texto recibido
     * @param part parte del nombre, que decide el código y el texto del rechazo
     * @throws IllegalArgumentException si falta, queda vacío o supera 120 caracteres
     * @throws InvalidPersonNameException si tiene un carácter no admitido o ninguna letra
     */
    public PersonName(String raw, Part part) {
        String text = SingleLineText.normalizeName(raw);
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (text.codePointCount(0, text.length()) > MAX_LENGTH) {
            throw new IllegalArgumentException("El nombre supera 120 caracteres");
        }
        if (!ALLOWED.matcher(text).matches() || !HAS_LETTER.matcher(text).find()) {
            throw new InvalidPersonNameException(part);
        }
        this.value = text;
    }

    /**
     * Devuelve el nombre normalizado.
     *
     * @return texto recortado, en NFC y con los espacios internos unidos
     */
    public String value() {
        return value;
    }
}
