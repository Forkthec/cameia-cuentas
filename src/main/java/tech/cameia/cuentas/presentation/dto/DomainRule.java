package tech.cameia.cuentas.presentation.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.PersonName;
import tech.cameia.cuentas.domain.model.PhoneNumber;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.domain.model.RawPassword;
import tech.cameia.cuentas.domain.model.SingleLineText;
import tech.cameia.cuentas.domain.policy.PasswordPolicy;

/**
 * Restricción del contrato que aplica en el borde una regla de forma escrita en el dominio.
 *
 * <p>Las reglas de forma (caracteres del nombre, formato del correo y del celular, longitud de la
 * contraseña, opción del pronombre) se responden junto con las demás del contrato, un elemento
 * por campo y todas a la vez (REQ-RV-64). La regla sigue escrita una sola vez, en el objeto de
 * valor o la política del dominio: esta restricción la ejecuta y toma de su excepción el código y
 * el mensaje del criterio.</p>
 *
 * <p>Cada regla reporta solo sus propios códigos. La ausencia, el texto en blanco y el exceso de
 * longitud de nombre y correo los reportan {@code @NotBlank} y {@code @CodePointSize}, para que
 * un campo nunca reciba dos errores.</p>
 */
@Documented
@Constraint(validatedBy = DomainRuleValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface DomainRule {

    /**
     * Regla del dominio que se aplica al campo.
     *
     * @return regla
     */
    Rule value();

    /**
     * Mensaje de respaldo; el real es el de la excepción del dominio.
     *
     * @return texto genérico
     */
    String message() default "Valor no válido";

    /**
     * Grupos de validación; el contrato no usa grupos.
     *
     * @return grupos en los que aplica la restricción
     */
    Class<?>[] groups() default {};

    /**
     * Metadatos de la restricción; el contrato no los usa.
     *
     * @return cargas asociadas a la restricción
     */
    Class<? extends Payload>[] payload() default {};

    /** Reglas de forma del registro, con la comprobación del dominio y los códigos que reportan. */
    enum Rule {

        /** Nombre: solo letras, espacios, apóstrofo y guion. */
        FIRST_NAME(text -> requireValidName(text, PersonName.Part.FIRST_NAME),
                Set.of(ErrorCode.FIRST_NAME_INVALID_CHARACTERS)),

        /** Apellido: solo letras, espacios, apóstrofo y guion. */
        LAST_NAME(text -> requireValidName(text, PersonName.Part.LAST_NAME),
                Set.of(ErrorCode.LAST_NAME_INVALID_CHARACTERS)),

        /** Correo con forma de correo. */
        EMAIL(EmailAddress::new, Set.of(ErrorCode.EMAIL_INVALID_FORMAT)),

        /** Contraseña de 12 a 64 caracteres; la lista común se consulta después, en el dominio. */
        PASSWORD_LENGTH(text -> PasswordPolicy.requireValidLength(new RawPassword(text)),
                Set.of(ErrorCode.PASSWORD_TOO_SHORT, ErrorCode.PASSWORD_TOO_LONG)),

        /**
         * Celular válido para el país de su indicativo, en E.164. Se recorta como lo hace
         * {@link RegisterUserRequest#toCommand()}: lo que queda vacío es «sin celular».
         */
        PHONE_NUMBER(text -> {
            String phoneNumber = SingleLineText.normalize(text);
            if (!phoneNumber.isEmpty()) {
                PhoneNumber.fromInput(phoneNumber);
            }
        }, Set.of(ErrorCode.PHONE_NUMBER_INVALID_FORMAT)),

        /** Pronombre: una de las opciones del contrato. */
        PRONOUN(Pronoun::of, Set.of(ErrorCode.PRONOUN_INVALID_VALUE));

        private final Consumer<String> validation;
        private final Set<ErrorCode> reportedCodes;

        /**
         * Aplica la regla de caracteres a un nombre que cabe en el máximo. El que no cabe lo
         * reporta {@code @CodePointSize}: el objeto de valor lo rechazaría como invariante.
         */
        private static void requireValidName(String text, PersonName.Part part) {
            if (text.codePointCount(0, text.length()) <= PersonName.MAX_LENGTH) {
                new PersonName(text, part);
            }
        }

        Rule(Consumer<String> validation, Set<ErrorCode> reportedCodes) {
            this.validation = validation;
            this.reportedCodes = reportedCodes;
        }

        /**
         * Aplica la regla del dominio.
         *
         * @param text valor recibido, presente y no en blanco
         * @throws tech.cameia.cuentas.domain.exception.InvalidFieldException si el dominio lo rechaza
         */
        void requireValid(String text) {
            validation.accept(text);
        }

        /**
         * Indica si el código pertenece a esta regla y no a otra restricción del campo.
         *
         * @param code código de la excepción del dominio
         * @return {@code true} si esta regla lo reporta
         */
        boolean canReport(ErrorCode code) {
            return reportedCodes.contains(code);
        }
    }
}
