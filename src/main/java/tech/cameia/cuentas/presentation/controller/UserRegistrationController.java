package tech.cameia.cuentas.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
    @Operation(summary = "Registra una cuenta nueva",
            description = "Valida el formulario, crea la credencial en Firebase con el plan gratuito y guarda la "
                    + "cuenta pendiente de verificar el correo. Los errores siguen el formato application/problem+json "
                    + "con code y requestId; los de validación agregan errors, un elemento por campo.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cuenta creada, pendiente de verificar el correo y con plan FREE"),
        @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_REGISTERED: el correo ya tiene una cuenta",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED con errors[].code: FIRST_NAME_REQUIRED, "
                + "FIRST_NAME_TOO_LONG, FIRST_NAME_INVALID_CHARACTERS, LAST_NAME_REQUIRED, LAST_NAME_TOO_LONG, "
                + "LAST_NAME_INVALID_CHARACTERS, BIRTH_DATE_REQUIRED, "
                + "BIRTH_DATE_INVALID_FORMAT, BIRTH_DATE_IN_THE_FUTURE, BIRTH_DATE_UNDERAGE, BIRTH_DATE_OUT_OF_RANGE, "
                + "EMAIL_REQUIRED, EMAIL_TOO_LONG, PASSWORD_REQUIRED, PASSWORD_TOO_SHORT, PASSWORD_TOO_LONG, PASSWORD_TOO_COMMON, "
                + "PRONOUN_REQUIRED; o REQUEST_BODY_INVALID_FORMAT y REQUEST_INVALID_VALUE sin errors",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: fallo imprevisto; el detalle va solo al log",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE: Firebase no respondió; se puede "
                + "reintentar", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/api/v1/users")
    ResponseEntity<RegisteredUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        Account cuenta = servicio.register(request.toCommand());

        return ResponseEntity.status(HttpStatus.CREATED).body(RegisteredUserResponse.de(cuenta));
    }
}
