package tech.cameia.cuentas.domain.model;

import java.time.Instant;

/**
 * Valores de la aceptación de la política que comparten las pruebas.
 */
public final class PolicyAcceptanceFixture {

    /** Versión vigente que usan las pruebas. */
    public static final String VERSION = "2026-11-v1";

    /** Instante de aceptación fijo. */
    public static final Instant ACCEPTED_AT = Instant.parse("2026-11-01T00:00:00Z");

    /** Aceptación de la versión vigente en el instante fijo. */
    public static final PolicyAcceptance ACCEPTED = new PolicyAcceptance(VERSION, ACCEPTED_AT);

    private PolicyAcceptanceFixture() {
    }
}
