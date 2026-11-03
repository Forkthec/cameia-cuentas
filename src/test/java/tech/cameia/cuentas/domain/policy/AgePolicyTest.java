package tech.cameia.cuentas.domain.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.InvalidBirthDateException;
import tech.cameia.cuentas.domain.exception.InvalidBirthDateException.Reason;
import tech.cameia.cuentas.domain.model.BirthDate;

/**
 * Prueba de las reglas de fecha de nacimiento descritas en
 * {@code specs/CM-14-RegistroUsuario/spec.md} (REQ-CU-08 a REQ-CU-10), que provienen del
 * criterio de aceptación CA-1.1.3.
 *
 * <p>Usa un reloj fijo en UTC porque el caso más delicado, cumplir 18 años justo hoy,
 * depende del día exacto: con el reloj del sistema la prueba solo fallaría el día del
 * cumpleaños de la fecha elegida.</p>
 */
class AgePolicyTest {

    /** Día desde el que se evalúa todo el conjunto de pruebas. */
    private static final LocalDate HOY = LocalDate.of(2026, 9, 18);

    private final AgePolicy policy = new AgePolicy(
            Clock.fixed(HOY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));

    @Test
    void permiteRegistrarseAQuienCumpleDieciochoHoy() {
        BirthDate cumpleHoy = new BirthDate(HOY.minusYears(18));

        assertThatCode(() -> policy.verify(cumpleHoy)).doesNotThrowAnyException();
    }

    @Test
    void permiteRegistrarseAQuienYaEsMayorDeEdad() {
        BirthDate treintaAnios = new BirthDate(HOY.minusYears(30));

        assertThatCode(() -> policy.verify(treintaAnios)).doesNotThrowAnyException();
    }

    @Test
    void rechazaAQuienCumpleDieciochoManiana() {
        BirthDate cumpleManiana = new BirthDate(HOY.minusYears(18).plusDays(1));

        assertThatThrownBy(() -> policy.verify(cumpleManiana))
                .isInstanceOf(InvalidBirthDateException.class)
                .hasMessage("Debes ser mayor de edad.")
                .extracting(excepcion -> ((InvalidBirthDateException) excepcion).getReason())
                .isEqualTo(Reason.UNDERAGE);
    }

    @Test
    void rechazaUnaFechaFuturaConMensajePropio() {
        BirthDate futura = new BirthDate(HOY.plusDays(1));

        assertThatThrownBy(() -> policy.verify(futura))
                .isInstanceOf(InvalidBirthDateException.class)
                .hasMessage("Fecha de nacimiento inválida.")
                .extracting(excepcion -> ((InvalidBirthDateException) excepcion).getReason())
                .isEqualTo(Reason.IN_THE_FUTURE);
    }

    @Test
    void rechazaUnaEdadImplausible() {
        BirthDate ciento_once = new BirthDate(HOY.minusYears(111));

        assertThatThrownBy(() -> policy.verify(ciento_once))
                .isInstanceOf(InvalidBirthDateException.class)
                .hasMessage("Verifica tu fecha de nacimiento.")
                .extracting(excepcion -> ((InvalidBirthDateException) excepcion).getReason())
                .isEqualTo(Reason.IMPLAUSIBLE);
    }

    @Test
    void cadaCausaDeFechaInvalidaLlevaSuCodigo() {
        assertThatThrownBy(() -> policy.verify(new BirthDate(HOY.minusYears(18).plusDays(1))))
                .isInstanceOf(InvalidBirthDateException.class)
                .extracting(excepcion -> ((InvalidBirthDateException) excepcion).getErrorCode())
                .isEqualTo(ErrorCode.BIRTH_DATE_UNDERAGE);
        assertThatThrownBy(() -> policy.verify(new BirthDate(HOY.plusDays(1))))
                .isInstanceOf(InvalidBirthDateException.class)
                .extracting(excepcion -> ((InvalidBirthDateException) excepcion).getErrorCode())
                .isEqualTo(ErrorCode.BIRTH_DATE_IN_THE_FUTURE);
        assertThatThrownBy(() -> policy.verify(new BirthDate(HOY.minusYears(111))))
                .isInstanceOf(InvalidBirthDateException.class)
                .extracting(excepcion -> ((InvalidBirthDateException) excepcion).getErrorCode())
                .isEqualTo(ErrorCode.BIRTH_DATE_OUT_OF_RANGE);
    }

    @Test
    void admiteExactamenteCientoDiezAnios() {
        BirthDate limite = new BirthDate(HOY.minusYears(110));

        assertThatCode(() -> policy.verify(limite)).doesNotThrowAnyException();
    }

    @Test
    void laEdadSeCalculaEnUtcYNoEnLaZonaDelServidor() {
        // A las 03:00 UTC del 18/09 en Bogotá (UTC-5) todavía es 17/09: quien cumple 18
        // el 18/09 debe poder registrarse igual, porque la regla está fijada en UTC.
        Clock relojUtc = Clock.fixed(Instant.parse("2026-09-18T03:00:00Z"), ZoneOffset.UTC);
        AgePolicy policyUtc = new AgePolicy(relojUtc);
        BirthDate cumpleHoy = new BirthDate(LocalDate.of(2008, 9, 18));

        assertThatCode(() -> policyUtc.verify(cumpleHoy)).doesNotThrowAnyException();
    }

    /**
     * Límites de cada regla con el día de hoy fijado en cada fila. La columna del resultado es
     * {@code ACEPTA} o el motivo del rechazo.
     */
    @ParameterizedTest(name = "[{index}] nacido {0}, hoy {1} -> {2}")
    @CsvSource({
        // Mayoría de edad: cumple 18 hoy, mañana, y el mismo día de nacimiento.
        "2008-10-06, 2026-10-06, ACEPTA",
        "2008-10-07, 2026-10-06, UNDERAGE",
        "2026-10-06, 2026-10-06, UNDERAGE",
        // Tope de 110 años: cumple 111 mañana (todavía 110) y hoy.
        "1915-10-07, 2026-10-06, ACEPTA",
        "1915-10-06, 2026-10-06, IMPLAUSIBLE",
        // Fecha futura: mañana.
        "2026-10-07, 2026-10-06, IN_THE_FUTURE",
        // Nacido un 29 de febrero: en un año no bisiesto cumple el 1 de marzo, no el 28 de febrero.
        "2000-02-29, 2018-02-28, UNDERAGE",
        "2000-02-29, 2018-03-01, ACEPTA",
        // Cambio de año: nacido el 31/12 y el 01/01.
        "2000-12-31, 2018-12-31, ACEPTA",
        "2000-12-31, 2018-12-30, UNDERAGE",
        "2000-01-01, 2017-12-31, UNDERAGE",
        "2000-01-01, 2018-01-01, ACEPTA"})
    void cadaLimiteDeEdadSeEvaluaConElDiaExacto(LocalDate nacimiento, LocalDate hoy, String esperado) {
        AgePolicy politica = new AgePolicy(Clock.fixed(hoy.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
        BirthDate fecha = new BirthDate(nacimiento);

        if ("ACEPTA".equals(esperado)) {
            assertThatCode(() -> politica.verify(fecha)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> politica.verify(fecha))
                    .isInstanceOf(InvalidBirthDateException.class)
                    .extracting(excepcion -> ((InvalidBirthDateException) excepcion).getReason())
                    .isEqualTo(Reason.valueOf(esperado));
        }
    }

    @Test
    void entreLasSieteYLaMedianocheDeColombiaYaEsElDiaSiguienteEnUtc() {
        // 20:30 del 6 de octubre en Bogotá son las 01:30 del 7 en UTC: quien cumple 18 el 7
        // ya puede registrarse, aunque en Colombia todavía sea el día anterior.
        AgePolicy politica = new AgePolicy(Clock.fixed(Instant.parse("2026-10-07T01:30:00Z"), ZoneOffset.UTC));

        assertThatCode(() -> politica.verify(new BirthDate(LocalDate.of(2008, 10, 7)))).doesNotThrowAnyException();
    }
}
