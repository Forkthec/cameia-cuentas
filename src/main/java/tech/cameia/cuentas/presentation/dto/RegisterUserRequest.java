package tech.cameia.cuentas.presentation.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tech.cameia.cuentas.domain.model.Pronoun;

/**
 * Cuerpo de la solicitud de registro.
 *
 * <p>Aquí solo se comprueba la sintaxis: que los campos obligatorios vengan y que quepan
 * en la base de datos. Las reglas de negocio, como la mayoría de edad o la fuerza de la
 * contraseña, viven en el dominio y se aplican después.</p>
 *
 * <p>La contraseña no tiene límite de tamaño declarado en este punto a propósito: quien
 * decide el máximo es la política del dominio, y duplicar el número aquí crearía dos
 * fuentes de verdad que se desincronizan.</p>
 *
 * @param firstName nombres de la persona
 * @param lastName apellidos de la persona
 * @param birthDate fecha de nacimiento en formato {@code DD/MM/YYYY}
 * @param email correo electrónico con el que iniciará sesión
 * @param password contraseña elegida
 * @param phoneNumber celular en formato internacional, por ejemplo {@code +573001234567};
 *                    opcional
 * @param pronoun pronombres con los que pide que se le trate; opcional
 */
public record RegisterUserRequest(

        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 120, message = "Los nombres no pueden superar los 120 caracteres")
        String firstName,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 120, message = "Los apellidos no pueden superar los 120 caracteres")
        String lastName,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
        LocalDate birthDate,

        @NotBlank(message = "El correo electrónico es obligatorio")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password,

        String phoneNumber,

        Pronoun pronoun) {
}
