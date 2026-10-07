package tech.cameia.cuentas.presentation.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

import jakarta.validation.constraints.NotBlank;

import tech.cameia.cuentas.application.command.RegisterUserCommand;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.domain.model.SingleLineText;

/**
 * Cuerpo de la solicitud de registro.
 *
 * <p>Aquí se comprueba la forma de cada campo, y todos los campos inválidos se responden a la vez,
 * un elemento por campo (REQ-RV-64): que los obligatorios vengan, que quepan en la base de datos,
 * que la fecha sea una fecha real con el formato del contrato, y las reglas de forma del dominio
 * (caracteres del nombre, formato del correo y del celular, longitud de la contraseña, opción del
 * pronombre), que {@link DomainRule} ejecuta sin copiarlas. Las reglas de negocio (edad, contraseña
 * común, correo repetido) se aplican después, en el dominio y de una en una (D3).</p>
 *
 * <p>La fecha y el pronombre llegan como texto y no como fecha ni enumerado de Java: así el valor
 * vacío («obligatorio») y el mal escrito («formato» u «opción no válida») se distinguen con sus
 * propias restricciones, en su campo y junto con los demás errores del contrato, sin depender del
 * texto de una excepción de Jackson.</p>
 *
 * @param firstName nombres de la persona
 * @param lastName apellidos de la persona
 * @param birthDate fecha de nacimiento como texto con formato dd/mm/aaaa, por ejemplo
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
        @NotBlank(message = "Ingresa tu nombre.")
        @CodePointSize(max = 120, message = "El nombre no puede superar los 120 caracteres.")
        @DomainRule(DomainRule.Rule.FIRST_NAME)
        String firstName,

        @Schema(description = "Apellidos de la persona: letras, espacios, apóstrofo y guion", example = "Gómez-Ruiz",
                minLength = 1, maxLength = 120, requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "Ingresa tu apellido.")
        @CodePointSize(max = 120, message = "El apellido no puede superar los 120 caracteres.")
        @DomainRule(DomainRule.Rule.LAST_NAME)
        String lastName,

        @Schema(description = "Fecha de nacimiento con formato dd/mm/aaaa; debe ser una fecha real y la persona debe "
                + "tener entre 18 y 110 años cumplidos en UTC", example = "12/04/1995",
                pattern = "^\\d{2}/\\d{2}/\\d{4}$", requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "Ingresa tu fecha de nacimiento.")
        @BirthDateFormat
        String birthDate,

        @Schema(description = "Correo con el que iniciará sesión; se guarda recortado y en minúsculas",
                example = "ana@correo.co", maxLength = 254, format = "email", requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "Ingresa tu correo electrónico.")
        @CodePointSize(max = 254, message = "El correo no puede superar los 254 caracteres.")
        @DomainRule(DomainRule.Rule.EMAIL)
        String email,

        @Schema(description = "Contraseña de 12 a 64 caracteres, sin reglas de composición; no puede ser una "
                + "contraseña común. No se recorta ni se devuelve nunca", minLength = 12, maxLength = 64,
                format = "password", requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "Ingresa tu contraseña.")
        @DomainRule(DomainRule.Rule.PASSWORD_LENGTH)
        String password,

        @Schema(description = "Opcional; formato internacional E.164 sin espacios", example = "+573000000000",
                requiredMode = RequiredMode.NOT_REQUIRED)
        @DomainRule(DomainRule.Rule.PHONE_NUMBER)
        String phoneNumber,

        @Schema(description = "Pronombres: Él (HE), Ella (SHE) o Elle (THEY)", allowableValues = {"HE", "SHE", "THEY"},
                requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "Selecciona una opción.")
        @DomainRule(DomainRule.Rule.PRONOUN)
        String pronoun) {

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
     * @return el comando con la fecha y el pronombre ya interpretados
     * @throws java.util.NoSuchElementException si se llama sin haber validado el formato de
     *                                          la fecha
     * @throws tech.cameia.cuentas.domain.exception.InvalidPronounException si se llama sin haber
     *                                          validado el pronombre
     */
    public RegisterUserCommand toCommand() {
        LocalDate parsedBirthDate = BirthDateFormatValidator.parse(birthDate).orElseThrow();
        // Un celular vacío o en blanco es «sin celular»: el formulario envía el campo aunque la
        // persona no lo llene.
        String trimmedPhoneNumber = SingleLineText.normalize(phoneNumber);
        String declaredPhoneNumber =
                trimmedPhoneNumber == null || trimmedPhoneNumber.isEmpty() ? null : trimmedPhoneNumber;
        return new RegisterUserCommand(firstName, lastName, parsedBirthDate, email, password, declaredPhoneNumber,
                Pronoun.of(pronoun));
    }
}
