package tech.cameia.cuentas.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        Account cuenta = Account.register("uid-firebase", "Ana", "Pérez", MAYOR_DE_EDAD, null, null);

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
        assertThatThrownBy(() -> Account.register("uid", "Ana", "Pérez", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La fecha de nacimiento es obligatoria al registrarse");
    }

    @Test
    void exigeNombresYApellidos() {
        assertThatThrownBy(() -> Account.register("uid", "  ", "Pérez", MAYOR_DE_EDAD, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Los nombres son obligatorios");

        assertThatThrownBy(() -> Account.register("uid", "Ana", null, MAYOR_DE_EDAD, null, null))
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

    private Account registrar() {
        return Account.register("uid-firebase", "Ana", "Pérez", MAYOR_DE_EDAD,
                new PhoneNumber("+573001234567"), Pronoun.SHE);
    }

    private Account reconstruirCon(AccountStatus estado) {
        return Account.rebuild(UUID.randomUUID(), "uid-firebase", "Ana", "Pérez", MAYOR_DE_EDAD,
                null, null, estado);
    }
}
