package tech.cameia.cuentas.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.infrastructure.persistence.entity.AccountEntity;

/**
 * Pruebas unitarias del traductor entre el agregado y la fila, sin Spring ni base de datos.
 */
class AccountMapperTest {

    private static final UUID ID = UUID.fromString("3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10");

    private final AccountMapper mapper = new AccountMapper();

    @Test
    @DisplayName("Una fila sin fecha de nacimiento se lee como cuenta con fecha desconocida")
    void toDomain_shouldAcceptUnknownBirthDate_whenColumnIsNull() {
        AccountEntity row = new AccountEntity(ID, "uid-legacy-1", "María José", "Gómez-Ruiz",
                null, null, null, AccountStatus.ACTIVE);

        Account account = mapper.toDomain(row);

        assertThat(account.getFirebaseUid()).isEqualTo("uid-legacy-1");
        assertThat(account.getBirthDate()).isEmpty();
    }

    @Test
    @DisplayName("Una cuenta con fecha de nacimiento conocida se lee con esa fecha")
    void toDomain_shouldKeepBirthDate_whenColumnHasValue() {
        AccountEntity row = new AccountEntity(ID, "uid-1", "María José", "Gómez-Ruiz",
                LocalDate.of(1995, 4, 12), null, Pronoun.SHE, AccountStatus.ACTIVE);

        Account account = mapper.toDomain(row);

        assertThat(account.getBirthDate()).contains(new BirthDate(LocalDate.of(1995, 4, 12)));
    }

    @Test
    @DisplayName("Una cuenta con fecha desconocida se guarda con la columna en nulo")
    void toEntity_shouldWriteNullBirthDate_whenUnknown() {
        Account account = Account.rebuild(ID, "uid-legacy-1", "María José", "Gómez-Ruiz",
                null, null, null, AccountStatus.ACTIVE);

        AccountEntity row = mapper.toEntity(account);

        assertThat(row.getFechaNacimiento()).isNull();
    }
}
