package tech.cameia.cuentas.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.PhoneNumber;
import tech.cameia.cuentas.infrastructure.persistence.entity.AccountEntity;

/**
 * Traduce entre el agregado {@code Account} y la fila {@code AccountEntity}.
 *
 * <p>Existe para que el dominio no dependa de JPA y para que el modelo de la base pueda
 * cambiar sin arrastrar las reglas de negocio.</p>
 */
@Component
public class AccountMapper {

    /**
     * Convierte el agregado en una fila lista para guardarse.
     *
     * @param account cuenta del dominio
     * @return entidad equivalente
     */
    public AccountEntity toEntity(Account account) {
        return new AccountEntity(
                account.getId(),
                account.getFirebaseUid(),
                account.getFirstName(),
                account.getLastName(),
                account.getBirthDate().value(),
                account.getPhoneNumber().map(PhoneNumber::value).orElse(null),
                account.getPronoun().orElse(null),
                account.getStatus());
    }

    /**
     * Reconstruye el agregado a partir de una fila almacenada.
     *
     * @param entity fila leída de la base de datos
     * @return cuenta del dominio, con el estado que tenía almacenado
     */
    public Account toDomain(AccountEntity entity) {
        return Account.rebuild(
                entity.getId(),
                entity.getFirebaseUid(),
                entity.getNombre(),
                entity.getApellido(),
                new BirthDate(entity.getFechaNacimiento()),
                entity.getTelefono() == null ? null : new PhoneNumber(entity.getTelefono()),
                entity.getPronombres(),
                entity.getEstado());
    }
}
