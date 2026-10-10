package tech.cameia.cuentas.application.command;

import java.time.LocalDate;

import tech.cameia.cuentas.domain.model.Pronoun;

/**
 * Datos de entrada del caso de uso de registro.
 *
 * <p>Separa el caso de uso del contrato HTTP: aquí los tipos ya son de Java, la fecha ya
 * está interpretada y los campos opcionales llegan como {@code null}. Convertir el texto
 * que envía el cliente es tarea del controlador.</p>
 *
 * <p>La contraseña viaja como texto porque de aquí pasa directo a Firebase, que es quien
 * la custodia. No se guarda ni se registra en ningún punto del recorrido.</p>
 *
 * @param firstName nombres de la persona
 * @param lastName apellidos de la persona
 * @param birthDate fecha de nacimiento
 * @param email correo con el que iniciará sesión
 * @param password contraseña elegida
 * @param phoneNumber celular en formato E.164, o {@code null} si no lo declaró
 * @param pronoun pronombres, o {@code null} si no los declaró
 * @param requestId {@code X-Request-Id} que propaga el Gateway, puede ser {@code null}; solo sirve para correlacionar
 *        el evento de cuenta creada
 */
public record RegisterUserCommand(
        String firstName,
        String lastName,
        LocalDate birthDate,
        String email,
        String password,
        String phoneNumber,
        Pronoun pronoun,
        String requestId) {
}
