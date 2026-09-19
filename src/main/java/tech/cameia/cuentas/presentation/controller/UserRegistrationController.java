package tech.cameia.cuentas.presentation.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import tech.cameia.cuentas.application.command.RegisterUserCommand;
import tech.cameia.cuentas.application.service.RegisterUserService;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.presentation.dto.RegisterUserRequest;
import tech.cameia.cuentas.presentation.dto.RegisteredUserResponse;

/**
 * Recibe el registro de una persona invitada.
 *
 * <p>Es el único endpoint de negocio que no exige identidad: quien lo llama todavía no
 * tiene cuenta, así que el API Gateway lo trata como ruta pública y borra cualquier
 * encabezado de usuario que venga del cliente.</p>
 *
 * <p>El cuerpo transporta una contraseña, así que ni este controlador ni el Gateway lo
 * registran en el log.</p>
 */
@RestController
class UserRegistrationController {

    private final RegisterUserService servicio;

    UserRegistrationController(RegisterUserService servicio) {
        this.servicio = servicio;
    }

    /**
     * Crea la credencial en Firebase y la cuenta local.
     *
     * <p>La cuenta nace pendiente de verificar el correo. El frontend continúa iniciando
     * sesión contra Firebase y pidiendo el envío del correo de verificación.</p>
     *
     * @param request datos del formulario de registro
     * @return {@code 201 Created} con el identificador de la cuenta, su estado y su plan
     */
    @PostMapping("/api/v1/users")
    ResponseEntity<RegisteredUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        Account cuenta = servicio.register(new RegisterUserCommand(
                request.firstName(),
                request.lastName(),
                request.birthDate(),
                request.email(),
                request.password(),
                request.phoneNumber(),
                request.pronoun()));

        return ResponseEntity.status(HttpStatus.CREATED).body(RegisteredUserResponse.de(cuenta));
    }
}
