package tech.cameia.cuentas.presentation.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tech.cameia.cuentas.application.command.RegisterUserCommand;
import tech.cameia.cuentas.domain.model.Pronoun;

/**
 * Cuerpo de la solicitud de registro.
 *
 * <p>Aquí solo se comprueba la sintaxis: que los campos obligatorios vengan, que quepan en la
 * base de datos y que la fecha sea una fecha real con el formato del contrato. Las reglas de
 * negocio, como la mayoría de edad o la fuerza de la contraseña, viven en el dominio y se
 * aplican después.</p>
 *
 * <p>La contraseña no tiene límite de tamaño declarado en este punto a propósito: quien
 * decide el mínimo y el máximo es la política del dominio, y duplicar los números aquí
 * crearía dos fuentes de verdad que se desincronizan.</p>
 *
 * <p>La fecha llega como texto y no como fecha de Java: así la fecha vacía («obligatoria») y
 * la mal escrita («formato») se distinguen con sus propias restricciones, en su campo y junto
 * con los demás errores del contrato, sin depender del texto de una excepción de Jackson.</p>
 *
 * @param firstName nombres de la persona
 * @param lastName apellidos de la persona
 * @param birthDate fecha de nacimiento como texto con formato {@code dd/MM/yyyy}, por ejemplo
 *                  {@code 12/04/1995}
 * @param email correo electrónico con el que iniciará sesión
 * @param password contraseña elegida
 * @param phoneNumber celular en formato internacional, por ejemplo {@code +573001234567};
 *                    opcional
 * @param pronoun pronombres con los que pide que se le trate; obligatorio: {@code HE},
 *                {@code SHE} o {@code THEY}
 */
public record RegisterUserRequest(

        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 120, message = "Los nombres no pueden superar los 120 caracteres")
        String firstName,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 120, message = "Los apellidos no pueden superar los 120 caracteres")
        String lastName,

        @NotBlank(message = "La fecha de nacimiento es obligatoria")
        @BirthDateFormat
        String birthDate,

        @NotBlank(message = "El correo electrónico es obligatorio")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password,

        String phoneNumber,

        @NotNull(message = "Selecciona una opción.")
        Pronoun pronoun) {

    /**
     * Convierte el cuerpo ya validado en el comando del caso de uso.
     *
     * <p>Usa el mismo formato con el que se validó la fecha, así que validar y convertir no
     * pueden separarse.</p>
     *
     * @return el comando con la fecha ya interpretada
     * @throws java.util.NoSuchElementException si se llama sin haber validado el formato de
     *                                          la fecha
     */
    public RegisterUserCommand toCommand() {
        LocalDate fecha = BirthDateFormatValidator.parse(birthDate).orElseThrow();
        return new RegisterUserCommand(firstName, lastName, fecha, email, password, phoneNumber, pronoun);
    }
}
