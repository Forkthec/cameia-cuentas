package tech.cameia.cuentas.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Prueba la restricción de formato de la fecha de nacimiento sin levantar Spring.
 *
 * <p>Cada valor es un caso literal: las fechas imposibles, los formatos parecidos y los
 * espacios, que una lectura indulgente aceptaría y guardaría como otra fecha.</p>
 */
class BirthDateFormatValidatorTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    /** Objeto mínimo con la restricción, para validarla aislada del resto del contrato. */
    record Caso(@BirthDateFormat String fecha) {
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
    @ValueSource(strings = {"31/02/2000", "29/02/2001", "31/04/2000", "15/13/2000", "1/1/2000", "00/01/2000",
        "01/00/2000", "abc", "2000-01-01", "12-04-1995", "12/04/95", " 12/04/1995", "12/04/1995 ",
        "12/04/1995T00:00", "20000101", "12/04/1995\n", "12 /04/1995", "12/04/ 1995", "32/01/2000",
        "١٢/٠٤/١٩٩٥", "12/04/10000", "12.04.1995"})
    void unaFechaImposibleOMalEscritaTieneUnaSolaViolacionConElMensajeDelCriterio(String fecha) {
        Set<ConstraintViolation<Caso>> violaciones = validador.validate(new Caso(fecha));

        assertThat(violaciones).hasSize(1);
        assertThat(violaciones.iterator().next().getMessage()).isEqualTo("Formato de fecha inválido.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"29/02/2000", "12/04/1995", "01/01/2000", "29/02/1996", "31/12/1999", "31/01/2000"})
    void unaFechaRealConElFormatoExigidoEsValida(String fecha) {
        assertThat(validador.validate(new Caso(fecha))).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void laAusenciaYElTextoEnBlancoLosReportaOtraRestriccion(String fecha) {
        assertThat(validador.validate(new Caso(fecha))).isEmpty();
    }

    @Test
    void interpretaLaFechaDelMismoFormatoQueValida() {
        assertThat(BirthDateFormatValidator.parse("29/02/2000")).contains(LocalDate.of(2000, 2, 29));
        assertThat(BirthDateFormatValidator.parse("31/02/2000")).isEmpty();
        assertThat(BirthDateFormatValidator.parse(null)).isEmpty();
    }

    @Test
    void unAnioConSignoEsUnaFechaRealQueLuegoRechazaLaPoliticaDeEdad() {
        // El formato de año admite signo para años de más de cuatro cifras o anteriores a la
        // era común; la fecha resultante es real y la política de edad la rechaza por
        // inverosímil, así que no llega a guardarse.
        assertThat(BirthDateFormatValidator.parse("01/01/-2000")).contains(LocalDate.of(-2000, 1, 1));
    }
}
