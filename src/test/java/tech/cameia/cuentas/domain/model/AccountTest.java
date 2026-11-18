package tech.cameia.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.cameia.cuentas.domain.exception.AccountDisabledException;
import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.ErrorCode;

/**
 * Prueba del agregado descrito en {@code specs/CM-14-RegistroUsuario/spec.md}
 * (REQ-CU-04, REQ-CU-13 y REQ-CU-14).
 */
class AccountTest {

    private static final BirthDate MAYOR_DE_EDAD = new BirthDate(LocalDate.of(1995, 4, 12));

    @Test
    void unaCuentaNuevaNaceEsperandoLaVerificacionDelCorreo() {
        Account cuenta = registrar();

        assertThat(cuenta.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(cuenta.isPendingVerification()).isTrue();
        assertThat(cuenta.getId()).isNotNull();
    }

    @Test
    void elCelularYLosPronombresSonOpcionales() {
        Account cuenta = Account.register("uid-firebase", "Ana", "Pérez", MAYOR_DE_EDAD, null, null, PolicyAcceptanceFixture.ACCEPTED);

        assertThat(cuenta.getPhoneNumber()).isEmpty();
        assertThat(cuenta.getPronoun()).isEmpty();
    }

    @Test
    void conservaLosDatosDeclarados() {
        Account cuenta = registrar();

        assertThat(cuenta.getFirstName()).isEqualTo("Ana");
        assertThat(cuenta.getLastName()).isEqualTo("Pérez");
        assertThat(cuenta.getFirebaseUid()).isEqualTo("uid-firebase");
        assertThat(cuenta.getBirthDate()).contains(MAYOR_DE_EDAD);
        assertThat(cuenta.getPhoneNumber()).contains(new PhoneNumber("+573001234567"));
        assertThat(cuenta.getPronoun()).contains(Pronoun.SHE);
    }

    @Test
    void register_shouldReject_whenBirthDateIsMissing() {
        assertThatThrownBy(() -> Account.register("uid", "Ana", "Pérez", null, null, null, PolicyAcceptanceFixture.ACCEPTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La fecha de nacimiento es obligatoria al registrarse");
    }

    @Test
    void exigeNombresYApellidos() {
        assertThatThrownBy(() -> Account.register("uid", "  ", "Pérez", MAYOR_DE_EDAD, null, null, PolicyAcceptanceFixture.ACCEPTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Los nombres son obligatorios");

        assertThatThrownBy(() -> Account.register("uid", "Ana", null, MAYOR_DE_EDAD, null, null, PolicyAcceptanceFixture.ACCEPTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Los apellidos son obligatorios");
    }

    @Test
    void laVerificacionDelCorreoActivaLaCuenta() {
        Account cuenta = registrar();

        cuenta.activate();

        assertThat(cuenta.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void activarUnaCuentaYaActivaNoFalla() {
        Account cuenta = registrar();
        cuenta.activate();

        assertThatCode(cuenta::activate).doesNotThrowAnyException();
        assertThat(cuenta.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Una cuenta deshabilitada no se activa al verificar el correo")
    void activate_shouldThrowAccountDisabled_whenDisabled() {
        Account deshabilitada = reconstruirCon(AccountStatus.DISABLED);

        assertThatThrownBy(deshabilitada::activate)
                .isInstanceOf(AccountDisabledException.class)
                .extracting(error -> ((AccountDisabledException) error).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_DISABLED);
        assertThat(deshabilitada.getStatus()).isEqualTo(AccountStatus.DISABLED);
    }

    @Test
    @DisplayName("Una cuenta anonimizada responde como si no existiera al verificar el correo")
    void activate_shouldThrowAccountNotFound_whenAnonymized() {
        Account anonimizada = reconstruirCon(AccountStatus.ANONYMIZED);

        assertThatThrownBy(anonimizada::activate).isInstanceOf(AccountNotFoundException.class);
        assertThat(anonimizada.getStatus()).isEqualTo(AccountStatus.ANONYMIZED);
    }

    @Test
    @DisplayName("El registro conserva la aceptación de la política que recibe")
    void register_shouldKeepPolicyAcceptance() {
        Account cuenta = registrar();

        assertThat(cuenta.getPolicyAcceptance()).isEqualTo(PolicyAcceptanceFixture.ACCEPTED);
    }

    @Test
    @DisplayName("El registro exige la aceptación de la política")
    void register_shouldReject_whenPolicyAcceptanceIsNull() {
        assertThatThrownBy(() -> Account.register("uid", "Ana", "Pérez", MAYOR_DE_EDAD, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La aceptación de la política es obligatoria al registrarse");
    }

    @Test
    @DisplayName("Aceptar la versión que ya tiene no cambia nada y conserva la fecha de la primera aceptación")
    void acceptPolicy_shouldReturnFalseAndKeepDate_whenSameVersion() {
        Account cuenta = registrar();

        boolean cambio = cuenta.acceptPolicy(new PolicyAcceptance("2026-11-v1", Instant.parse("2026-12-01T00:00:00Z")));

        assertThat(cambio).isFalse();
        assertThat(cuenta.getPolicyAcceptance()).isEqualTo(PolicyAcceptanceFixture.ACCEPTED);
    }

    @Test
    @DisplayName("Aceptar una versión distinta reemplaza la aceptación guardada")
    void acceptPolicy_shouldReplace_whenDifferentVersion() {
        Account cuenta = Account.rebuild(UUID.randomUUID(), "uid-firebase", "Ana", "Pérez", MAYOR_DE_EDAD, null, null,
                new PolicyAcceptance(PolicyAcceptance.NOT_ACCEPTED_VERSION, Instant.parse("2026-10-01T00:00:00Z")),
                AccountStatus.ACTIVE);
        PolicyAcceptance nueva = new PolicyAcceptance("2026-11-v1", Instant.parse("2026-11-14T15:30:00Z"));

        boolean cambio = cuenta.acceptPolicy(nueva);

        assertThat(cambio).isTrue();
        assertThat(cuenta.getPolicyAcceptance()).isEqualTo(nueva);
    }

    @Test
    @DisplayName("Una cuenta pendiente de verificar puede aceptar la política")
    void acceptPolicy_shouldAccept_whenAccountIsPending() {
        Account cuenta = reconstruirCon(AccountStatus.PENDING_VERIFICATION);
        PolicyAcceptance nueva = new PolicyAcceptance("2026-12-v2", Instant.parse("2026-12-01T00:00:00Z"));

        assertThat(cuenta.acceptPolicy(nueva)).isTrue();
        assertThat(cuenta.getPolicyAcceptance()).isEqualTo(nueva);
    }

    @Test
    @DisplayName("Una cuenta deshabilitada no puede aceptar la política")
    void acceptPolicy_shouldThrowDisabled_whenAccountIsDisabled() {
        Account deshabilitada = reconstruirCon(AccountStatus.DISABLED);

        assertThatThrownBy(() -> deshabilitada.acceptPolicy(PolicyAcceptanceFixture.ACCEPTED))
                .isInstanceOf(AccountDisabledException.class);
        assertThat(deshabilitada.getPolicyAcceptance()).isEqualTo(PolicyAcceptanceFixture.ACCEPTED);
    }

    @Test
    @DisplayName("Una cuenta anonimizada responde como si no existiera al aceptar la política")
    void acceptPolicy_shouldThrowNotFound_whenAccountIsAnonymized() {
        Account anonimizada = reconstruirCon(AccountStatus.ANONYMIZED);

        assertThatThrownBy(() -> anonimizada.acceptPolicy(PolicyAcceptanceFixture.ACCEPTED))
                .isInstanceOf(AccountNotFoundException.class);
    }

    private Account registrar() {
        return Account.register("uid-firebase", "Ana", "Pérez", MAYOR_DE_EDAD,
                new PhoneNumber("+573001234567"), Pronoun.SHE, PolicyAcceptanceFixture.ACCEPTED);
    }

    private Account reconstruirCon(AccountStatus estado) {
        return Account.rebuild(UUID.randomUUID(), "uid-firebase", "Ana", "Pérez", MAYOR_DE_EDAD,
                null, null, PolicyAcceptanceFixture.ACCEPTED, estado);
    }
}
