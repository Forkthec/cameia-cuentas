package tech.cameia.cuentas.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import tech.cameia.cuentas.domain.exception.AccountConflictException;
import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.infrastructure.persistence.entity.AccountEntity;
import tech.cameia.cuentas.infrastructure.persistence.mapper.AccountMapper;
import tech.cameia.cuentas.infrastructure.persistence.repository.AccountJpaRepository;
import tech.cameia.cuentas.infrastructure.persistence.repository.AccountRepositoryAdapter;

/** Comprueba cómo el adaptador traduce los fallos del guardado, sin Spring ni base de datos. */
class AccountRepositoryAdapterConflictTest {

    private static final String UID = "uid-conflicto";

    private final AccountJpaRepository jpa = mock(AccountJpaRepository.class);
    private final AccountMapper mapeador = new AccountMapper();
    private final AccountRepositoryAdapter adaptador = new AccountRepositoryAdapter(jpa, mapeador);

    private Account cuenta() {
        return Account.rebuild(UUID.randomUUID(), UID, "Ana", "Pérez", null, null,
                Pronoun.SHE, AccountStatus.PENDING_VERIFICATION);
    }

    private void rowExists() {
        when(jpa.findByFirebaseUid(UID)).thenReturn(Optional.of(mapeador.toEntity(cuenta())));
    }

    @Test
    @DisplayName("Un conflicto de versión al guardar se traduce a ACCOUNT_CONFLICT")
    void save_shouldThrowAccountConflict_whenVersionChanged() {
        rowExists();
        when(jpa.save(any())).thenThrow(new ObjectOptimisticLockingFailureException(AccountEntity.class, "id"));

        assertThatThrownBy(() -> adaptador.save(cuenta()))
                .isInstanceOf(AccountConflictException.class)
                .extracting(error -> ((AccountConflictException) error).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_CONFLICT);
    }

    @Test
    @DisplayName("Un fallo distinto del conflicto de versión sale sin traducir")
    void save_shouldPropagateOtherFailures_whenNotAVersionConflict() {
        rowExists();
        DataIntegrityViolationException otro = new DataIntegrityViolationException("restriccion");
        when(jpa.save(any())).thenThrow(otro);

        assertThatThrownBy(() -> adaptador.save(cuenta())).isSameAs(otro);
        assertThat(otro).isNotInstanceOf(AccountConflictException.class);
    }
}
