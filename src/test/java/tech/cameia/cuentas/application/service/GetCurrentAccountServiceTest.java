package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.cuentas.domain.exception.AccountNotFoundException;
import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.infrastructure.persistence.InMemoryAccountRepository;

/**
 * Pruebas del caso de uso que lee la cuenta de quien llama.
 */
class GetCurrentAccountServiceTest {

    private static final String UID = "uid-me-1";

    private final InMemoryAccountRepository repository = spy(new InMemoryAccountRepository());
    private final GetCurrentAccountService service = new GetCurrentAccountService(repository);

    private static Account accountWith(String firebaseUid, AccountStatus status) {
        return Account.rebuild(UUID.fromString("3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10"), firebaseUid, "María José",
                "Gómez-Ruiz", new BirthDate(LocalDate.of(1995, 4, 12)), null, null, status);
    }

    @Test
    @DisplayName("Busca la cuenta solo por la identidad del encabezado")
    void find_shouldLookUpByHeaderIdentity_whenCalled() {
        repository.save(accountWith(UID, AccountStatus.ACTIVE));

        service.find(UID);

        verify(repository).findByFirebaseUid(UID);
    }

    @Test
    @DisplayName("Lanza cuenta inexistente cuando no hay fila para esa identidad")
    void find_shouldThrowNotFound_whenNoAccount() {
        assertThatThrownBy(() -> service.find("uid-sin-cuenta"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("No encontramos una cuenta para este usuario");
    }

    @Test
    @DisplayName("Una cuenta anonimizada se trata como inexistente")
    void find_shouldThrowNotFound_whenAccountIsAnonymized() {
        repository.save(accountWith(UID, AccountStatus.ANONYMIZED));

        assertThatThrownBy(() -> service.find(UID)).isInstanceOf(AccountNotFoundException.class);
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = AccountStatus.class, names = {"PENDING_VERIFICATION", "ACTIVE", "DISABLED"})
    @DisplayName("Devuelve la cuenta en cualquier estado que no sea anonimizada")
    void find_shouldReturnAccount_whenStatusIsNotAnonymized(AccountStatus status) {
        repository.save(accountWith(UID, status));

        Account found = service.find(UID);

        assertThat(found.getFirebaseUid()).isEqualTo(UID);
        assertThat(found.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest(name = "[{index}] identidad inválida")
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("Rechaza una identidad en blanco sin consultar la base")
    void find_shouldRejectIdentity_whenBlank(String firebaseUid) {
        assertThatThrownBy(() -> service.find(firebaseUid))
                .isInstanceOf(IdentityRequiredException.class)
                .hasMessage("La petición no incluye los datos que exige esta ruta")
                .extracting(error -> ((IdentityRequiredException) error).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_REQUIRED);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Rechaza una identidad nula sin consultar la base")
    void find_shouldRejectIdentity_whenNull() {
        assertThatThrownBy(() -> service.find(null)).isInstanceOf(IdentityRequiredException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Con 129 caracteres se rechaza y con 128 se consulta")
    void find_shouldLookUp_whenIdentityHas128Characters() {
        assertThatThrownBy(() -> service.find("u".repeat(129))).isInstanceOf(IdentityRequiredException.class);

        assertThatThrownBy(() -> service.find("u".repeat(128))).isInstanceOf(AccountNotFoundException.class);
        verify(repository).findByFirebaseUid("u".repeat(128));
    }

    @Test
    @DisplayName("Leer nunca escribe")
    void find_shouldNeverWrite_whenReading() {
        repository.save(accountWith(UID, AccountStatus.ACTIVE));
        clearInvocations(repository);

        service.find(UID);

        verify(repository, never()).save(any());
    }
}
