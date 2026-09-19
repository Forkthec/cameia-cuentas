package tech.cameia.cuentas.domain.policy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;

import tech.cameia.cuentas.domain.exception.InvalidBirthDateException;
import tech.cameia.cuentas.domain.exception.InvalidBirthDateException.Reason;
import tech.cameia.cuentas.domain.model.BirthDate;

/**
 * Decide si una fecha de nacimiento permite registrarse.
 *
 * <p>Reúne las tres reglas del criterio de aceptación CA-1.1.3 que dependen de la fecha
 * en sí. Cada una tiene su propio mensaje porque significan cosas distintas para quien se
 * registra: no es lo mismo ser menor de edad que haberse equivocado al escribir el año.</p>
 *
 * <p>La edad se calcula en UTC y no en la zona horaria del servidor: el resultado debe ser
 * el mismo en una máquina local, en la integración continua y en Cloud Run.</p>
 */
public class AgePolicy {

    /** Edad mínima para registrarse, en años cumplidos. */
    private static final int MINIMUM_AGE = 18;

    /** Edad por encima de la cual la fecha se considera un error de digitación. */
    private static final int IMPLAUSIBLE_AGE = 110;

    private final Clock clock;

    /** Crea la política sobre el reloj UTC del sistema. */
    public AgePolicy() {
        this(Clock.systemUTC());
    }

    /**
     * Crea la política sobre un reloj explícito.
     *
     * @param clock reloj del que se toma la fecha actual; las pruebas usan uno fijo para
     *              poder comprobar el día exacto del cumpleaños número 18
     */
    public AgePolicy(Clock clock) {
        this.clock = clock.withZone(ZoneOffset.UTC);
    }

    /**
     * Comprueba que la fecha de nacimiento permita crear la cuenta.
     *
     * @param birthDate fecha declarada en el registro
     * @throws InvalidBirthDateException si la fecha está en el futuro, si la persona aún
     *                                   no cumple 18 años o si la edad resultante no es
     *                                   plausible
     */
    public void verify(BirthDate birthDate) {
        LocalDate today = LocalDate.now(clock);
        LocalDate value = birthDate.value();

        if (value.isAfter(today)) {
            throw new InvalidBirthDateException(Reason.IN_THE_FUTURE, "Fecha de nacimiento inválida");
        }

        int age = Period.between(value, today).getYears();

        if (age > IMPLAUSIBLE_AGE) {
            throw new InvalidBirthDateException(Reason.IMPLAUSIBLE,
                    "La fecha de nacimiento no es plausible, por favor verifícala");
        }
        if (age < MINIMUM_AGE) {
            throw new InvalidBirthDateException(Reason.UNDERAGE, "Debes ser mayor de edad");
        }
    }
}
