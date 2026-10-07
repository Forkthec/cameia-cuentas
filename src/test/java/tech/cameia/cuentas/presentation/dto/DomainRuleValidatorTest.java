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

    private static ValidatorFactory fabrica;
    private static Validator validador;

    /** Un campo por regla, para validarlas aisladas del resto del contrato. */
    record Caso(@DomainRule(DomainRule.Rule.FIRST_NAME) String nombre,
            @DomainRule(DomainRule.Rule.LAST_NAME) String apellido,
            @DomainRule(DomainRule.Rule.EMAIL) String correo,
            @DomainRule(DomainRule.Rule.PASSWORD_LENGTH) String contrasenia,
            @DomainRule(DomainRule.Rule.PHONE_NUMBER) String celular,
            @DomainRule(DomainRule.Rule.PRONOUN) String pronombre) {
    }

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarFabrica() {
        fabrica.close();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "nombre      | Ana3            | FIRST_NAME_INVALID_CHARACTERS | El nombre solo puede contener letras, espacios, apóstrofo y guion.",
        "apellido    | Pérez_          | LAST_NAME_INVALID_CHARACTERS  | El apellido solo puede contener letras, espacios, apóstrofo y guion.",
        "correo      | ana@@correo.co  | EMAIL_INVALID_FORMAT          | Ingresa un correo electrónico válido.",
        "contrasenia | frase secre     | PASSWORD_TOO_SHORT            | La contraseña debe tener al menos 12 caracteres.",
        "celular     | 12345           | PHONE_NUMBER_INVALID_FORMAT   | Revisa el número, no coincide con el formato del país elegido.",
        "pronombre   | he              | PRONOUN_INVALID_VALUE         | Selecciona una opción."})
    void cadaReglaReportaElCodigoYElMensajeDelDominio(String campo, String valor, ErrorCode codigo, String mensaje) {
        Set<ConstraintViolation<Caso>> violaciones = validador.validate(conCampo(campo, valor));

        assertThat(violaciones).singleElement().satisfies(violacion -> {
            assertThat(violacion.getPropertyPath().toString()).isEqualTo(campo);
            assertThat(violacion.getMessage()).isEqualTo(mensaje);
            assertThat(codigoDe(violacion)).isEqualTo(codigo);
        });
    }

    @ParameterizedTest
    @CsvSource({"contrasenia, 65", "contrasenia, 64"})
    void laContraseniaMuyLargaTambienEsDeForma(String campo, int largo) {
        Set<ConstraintViolation<Caso>> violaciones = validador.validate(conCampo(campo, "a".repeat(largo)));

        if (largo > 64) {
            assertThat(violaciones).singleElement()
                    .extracting(DomainRuleValidatorTest::codigoDe).isEqualTo(ErrorCode.PASSWORD_TOO_LONG);
        } else {
            assertThat(violaciones).isEmpty();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void laAusenciaYLosBlancosLosReportaOtraRestriccion(String valor) {
        assertThat(validador.validate(new Caso(valor, valor, valor, valor, valor, valor))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombre", "apellido"})
    void unNombreDeMasDe120LoReportaLaRestriccionDeLongitud(String campo) {
        assertThat(validador.validate(conCampo(campo, "a".repeat(121)))).isEmpty();
    }

    @Test
    void unCorreoDeMasDe254LoReportaLaRestriccionDeLongitud() {
        assertThat(validador.validate(conCampo("correo", "a".repeat(250) + "@correo.co"))).isEmpty();
    }

    @Test
    void unMensajeConLlavesNoSeInterpretaComoPlantilla() {
        // Ningún mensaje del dominio tiene llaves hoy; si alguno las tuviera, saldría tal cual.
        assertThat(validador.validate(conCampo("correo", "${1+1}@@x"))).singleElement()
                .extracting(ConstraintViolation::getMessage).isEqualTo("Ingresa un correo electrónico válido.");
    }

    private static ErrorCode codigoDe(ConstraintViolation<?> violacion) {
        HibernateConstraintViolation<?> hibernate = violacion.unwrap(HibernateConstraintViolation.class);
        return hibernate.getDynamicPayload(ErrorCode.class);
    }

    private static Caso conCampo(String campo, String valor) {
        return new Caso(
                campo.equals("nombre") ? valor : "Ana",
                campo.equals("apellido") ? valor : "Pérez",
                campo.equals("correo") ? valor : "ana@correo.co",
                campo.equals("contrasenia") ? valor : "frase secreta larga",
                campo.equals("celular") ? valor : null,
                campo.equals("pronombre") ? valor : "SHE");
    }
}
