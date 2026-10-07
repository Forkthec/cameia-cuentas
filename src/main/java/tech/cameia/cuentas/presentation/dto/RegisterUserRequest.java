package tech.cameia.cuentas.presentation.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import tech.cameia.cuentas.application.command.RegisterUserCommand;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.domain.model.SingleLineText;

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

        @Schema(description = "Nombres de la persona: letras, espacios, apóstrofo y guion", example = "María José",
                minLength = 1, maxLength = 120, requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "Los nombres son obligatorios")
        @CodePointSize(max = 120, message = "Los nombres no pueden superar los 120 caracteres")
        String firstName,

        @Schema(description = "Apellidos de la persona: letras, espacios, apóstrofo y guion", example = "Gómez-Ruiz",
                minLength = 1, maxLength = 120, requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "Los apellidos son obligatorios")
        @CodePointSize(max = 120, message = "Los apellidos no pueden superar los 120 caracteres")
        String lastName,

        @Schema(description = "Fecha de nacimiento con formato dd/MM/yyyy; debe ser una fecha real y la persona debe "
                + "tener entre 18 y 110 años cumplidos en UTC", example = "12/04/1995",
                pattern = "^\\d{2}/\\d{2}/\\d{4}$", requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "La fecha de nacimiento es obligatoria")
        @BirthDateFormat
        String birthDate,

        @Schema(description = "Correo con el que iniciará sesión; se guarda recortado y en minúsculas",
                example = "ana@correo.co", maxLength = 254, format = "email", requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "El correo electrónico es obligatorio")
        @CodePointSize(max = 254, message = "El correo no puede superar los 254 caracteres.")
        String email,

        @Schema(description = "Contraseña de 12 a 64 caracteres, sin reglas de composición; no puede ser una "
                + "contraseña común. No se recorta ni se devuelve nunca", minLength = 12, maxLength = 64,
                format = "password", requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "La contraseña es obligatoria")
        String password,

        @Schema(description = "Opcional; formato internacional E.164 sin espacios", example = "+573000000000",
                requiredMode = RequiredMode.NOT_REQUIRED)
        String phoneNumber,

        @Schema(description = "Pronombres: Él (HE), Ella (SHE) o Elle (THEY)", allowableValues = {"HE", "SHE", "THEY"},
                requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "Selecciona una opción.")
        Pronoun pronoun) {

    /**
     * Normaliza los textos antes de que se validen.
     *
     * <p>El borde valida y el comando recibe el texto ya recortado y en NFC: una sola definición
     * de «espacio» y de «carácter» para el contrato y el dominio. Nombre y apellido además unen
     * sus espacios internos repetidos. Los demás campos no se tocan: la contraseña nunca se
     * recorta, la fecha es texto exacto y el celular tiene su propia regla.</p>
     */
    public RegisterUserRequest {
        firstName = SingleLineText.normalizeName(firstName);
        lastName = SingleLineText.normalizeName(lastName);
        email = SingleLineText.normalize(email);
    }

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
        // Un celular vacío o en blanco es «sin celular»: el formulario envía el campo aunque la
        // persona no lo llene.
        String celular = SingleLineText.normalize(phoneNumber);
        String celularDeclarado = celular == null || celular.isEmpty() ? null : celular;
        return new RegisterUserCommand(firstName, lastName, fecha, email, password, celularDeclarado, pronoun);
    }
}
