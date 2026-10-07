package tech.cameia.cuentas.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.hibernate.validator.engine.HibernateConstraintViolation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.ErrorCode;

/**
 * Prueba la restricción que ejecuta en el borde las reglas de forma del dominio, sin levantar Spring.
 *
 * <p>Comprueba que cada regla reporta el código y el mensaje de la excepción del dominio, y que
 * deja a otras restricciones lo que no le corresponde (ausencia, blancos, exceso de longitud).</p>
 */
class DomainRuleValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    /** Un campo por regla, para validarlas aisladas del resto del contrato. */
    record Sample(@DomainRule(DomainRule.Rule.FIRST_NAME) String firstName,
            @DomainRule(DomainRule.Rule.LAST_NAME) String lastName,
            @DomainRule(DomainRule.Rule.EMAIL) String email,
            @DomainRule(DomainRule.Rule.PASSWORD_LENGTH) String password,
            @DomainRule(DomainRule.Rule.PHONE_NUMBER) String phoneNumber,
            @DomainRule(DomainRule.Rule.PRONOUN) String pronoun) {
    }

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "firstName   | Ana3            | FIRST_NAME_INVALID_CHARACTERS | El nombre solo puede contener letras, espacios, apóstrofo y guion.",
        "lastName    | Pérez_          | LAST_NAME_INVALID_CHARACTERS  | El apellido solo puede contener letras, espacios, apóstrofo y guion.",
        "email       | ana@@correo.co  | EMAIL_INVALID_FORMAT          | Ingresa un correo electrónico válido.",
        "password    | frase secre     | PASSWORD_TOO_SHORT            | La contraseña debe tener al menos 12 caracteres.",
        "phoneNumber | 12345           | PHONE_NUMBER_INVALID_FORMAT   | Revisa el número, no coincide con el formato del país elegido.",
        "pronoun     | he              | PRONOUN_INVALID_VALUE         | Selecciona una opción."})
    @DisplayName("Cada regla reporta el código y el mensaje del dominio")
    void validate_shouldReportDomainCodeAndMessage_whenDomainRejectsTheValue(String field, String value,
            ErrorCode code, String message) {
        Set<ConstraintViolation<Sample>> violations = validator.validate(withField(field, value));

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo(field);
            assertThat(violation.getMessage()).isEqualTo(message);
            assertThat(codeOf(violation)).isEqualTo(code);
        });
    }

    @ParameterizedTest
    @CsvSource({"password, 65", "password, 64"})
    @DisplayName("La contraseña muy larga también es una regla de forma")
    void validate_shouldReportTooLongOnlyAbove64_whenPasswordIsLong(String field, int length) {
        Set<ConstraintViolation<Sample>> violations = validator.validate(withField(field, "a".repeat(length)));

        if (length > 64) {
            assertThat(violations).singleElement()
                    .extracting(DomainRuleValidatorTest::codeOf).isEqualTo(ErrorCode.PASSWORD_TOO_LONG);
        } else {
            assertThat(violations).isEmpty();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("La ausencia y los blancos los reporta otra restricción")
    void validate_shouldReportNothing_whenValueIsMissingOrBlank(String value) {
        assertThat(validator.validate(new Sample(value, value, value, value, value, value))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"firstName", "lastName"})
    @DisplayName("Un nombre de más de 120 caracteres lo reporta la restricción de longitud")
    void validate_shouldReportNothing_whenNameIsLongerThan120(String field) {
        assertThat(validator.validate(withField(field, "a".repeat(121)))).isEmpty();
    }

    @Test
    @DisplayName("Un correo de más de 254 caracteres lo reporta la restricción de longitud")
    void validate_shouldReportNothing_whenEmailIsLongerThan254() {
        assertThat(validator.validate(withField("email", "a".repeat(250) + "@correo.co"))).isEmpty();
    }

    @Test
    @DisplayName("Un mensaje con llaves no se interpreta como plantilla")
    void validate_shouldKeepMessageLiteral_whenValueContainsTemplateSyntax() {
        // Ningún mensaje del dominio tiene llaves hoy; si alguno las tuviera, saldría tal cual.
        assertThat(validator.validate(withField("email", "${1+1}@@x"))).singleElement()
                .extracting(ConstraintViolation::getMessage).isEqualTo("Ingresa un correo electrónico válido.");
    }

    private static ErrorCode codeOf(ConstraintViolation<?> violation) {
        HibernateConstraintViolation<?> hibernate = violation.unwrap(HibernateConstraintViolation.class);
        return hibernate.getDynamicPayload(ErrorCode.class);
    }

    private static Sample withField(String field, String value) {
        return new Sample(
                field.equals("firstName") ? value : "Ana",
                field.equals("lastName") ? value : "Pérez",
                field.equals("email") ? value : "ana@correo.co",
                field.equals("password") ? value : "frase secreta larga",
                field.equals("phoneNumber") ? value : null,
                field.equals("pronoun") ? value : "SHE");
    }
}
