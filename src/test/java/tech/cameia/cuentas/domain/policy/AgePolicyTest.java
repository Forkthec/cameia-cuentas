package tech.cameia.cuentas.domain.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

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
                .hasMessage("Debes ser mayor de edad")
                .extracting(excepcion -> ((InvalidBirthDateException) excepcion).getReason())
                .isEqualTo(Reason.UNDERAGE);
    }

    @Test
    void rechazaUnaFechaFuturaConMensajePropio() {
        BirthDate futura = new BirthDate(HOY.plusDays(1));

        assertThatThrownBy(() -> policy.verify(futura))
                .isInstanceOf(InvalidBirthDateException.class)
                .hasMessage("Fecha de nacimiento inválida")
                .extracting(excepcion -> ((InvalidBirthDateException) excepcion).getReason())
                .isEqualTo(Reason.IN_THE_FUTURE);
    }

    @Test
    void rechazaUnaEdadImplausible() {
        BirthDate ciento_once = new BirthDate(HOY.minusYears(111));

        assertThatThrownBy(() -> policy.verify(ciento_once))
                .isInstanceOf(InvalidBirthDateException.class)
                .extracting(excepcion -> ((InvalidBirthDateException) excepcion).getReason())
                .isEqualTo(Reason.IMPLAUSIBLE);
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
}
