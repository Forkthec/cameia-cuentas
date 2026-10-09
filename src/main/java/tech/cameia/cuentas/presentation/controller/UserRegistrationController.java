package tech.cameia.cuentas.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

import tech.cameia.cuentas.application.service.RegisterUserResult;
import tech.cameia.cuentas.application.service.RegisterUserService;
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

    private static final String EJEMPLO_409 = """
            {"type":"about:blank","title":"Correo ya registrado","status":409,
             "detail":"Ese correo ya tiene una cuenta.","instance":"/api/v1/users",
             "code":"EMAIL_ALREADY_REGISTERED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601",
             "errors":[{"field":"email","code":"EMAIL_ALREADY_REGISTERED","message":"Ese correo ya tiene una cuenta."}]}""";

    private static final String EJEMPLO_422 = """
            {"type":"about:blank","title":"Datos no válidos","status":422,
             "detail":"Revisa los campos marcados.","instance":"/api/v1/users",
             "code":"VALIDATION_FAILED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601",
             "errors":[{"field":"email","code":"EMAIL_INVALID_FORMAT","message":"Ingresa un correo electrónico válido."},
                       {"field":"password","code":"PASSWORD_TOO_SHORT","message":"La contraseña debe tener al menos 12 caracteres."}]}""";

    private static final String EJEMPLO_500 = """
            {"type":"about:blank","title":"Error interno","status":500,
             "detail":"Ocurrió un error. Inténtalo de nuevo.","instance":"/api/v1/users",
             "code":"INTERNAL_ERROR","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_503 = """
            {"type":"about:blank","title":"Servicio no disponible","status":503,
             "detail":"Ocurrió un error. Inténtalo de nuevo.","instance":"/api/v1/users",
             "code":"DEPENDENCY_UNAVAILABLE","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private final RegisterUserService servicio;

    UserRegistrationController(RegisterUserService servicio) {
        this.servicio = servicio;
    }

    /**
     * Crea la credencial en Firebase y la cuenta local, o devuelve la cuenta pendiente que ya existe.
     *
     * <p>La cuenta nace pendiente de verificar el correo. El frontend continúa iniciando
     * sesión contra Firebase y pidiendo el envío del correo de verificación. Si el correo ya
     * tiene una cuenta pendiente de verificar, la respuesta es {@code 200 OK} con la misma
     * cuenta y el mismo cuerpo, sin crear ni cambiar nada.</p>
     *
     * @param request datos del formulario de registro
     * @return {@code 201 Created} (cuenta nueva) o {@code 200 OK} (la ya existente) con el
     *         identificador de la cuenta, su estado y su plan
     */
    @Operation(summary = "Registra una cuenta nueva o devuelve la pendiente de verificación",
            description = "Valida el formulario, crea la credencial en Firebase con el plan gratuito y guarda la "
                    + "cuenta pendiente de verificar el correo (201). Si el correo ya tiene una cuenta pendiente de "
                    + "verificar, devuelve esa cuenta sin cambios (200) y no verifica la contraseña ni usa los demás "
                    + "datos del cuerpo. Los errores siguen el formato application/problem+json con code y requestId; "
                    + "los de validación y el de correo repetido agregan errors, un elemento por campo.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cuenta creada, pendiente de verificar el correo y con plan FREE"),
        @ApiResponse(responseCode = "200", description = "El correo ya tenía una cuenta pendiente de verificar: "
                + "se devuelve la misma cuenta, con el mismo cuerpo que el 201"),
        @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_REGISTERED: el correo ya tiene una cuenta "
                + "activa, bloqueada o anonimizada, o una credencial sin cuenta; trae errors[0] con field email",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "correoRepetido", value = EJEMPLO_409))),
        @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED con errors[].code: FIRST_NAME_REQUIRED, "
                + "FIRST_NAME_TOO_LONG, FIRST_NAME_INVALID_CHARACTERS, LAST_NAME_REQUIRED, LAST_NAME_TOO_LONG, "
                + "LAST_NAME_INVALID_CHARACTERS, BIRTH_DATE_REQUIRED, "
                + "BIRTH_DATE_INVALID_FORMAT, BIRTH_DATE_IN_THE_FUTURE, BIRTH_DATE_UNDERAGE, BIRTH_DATE_OUT_OF_RANGE, "
                + "EMAIL_REQUIRED, EMAIL_TOO_LONG, EMAIL_INVALID_FORMAT, PASSWORD_REQUIRED, PASSWORD_TOO_SHORT, "
                + "PASSWORD_TOO_LONG, PASSWORD_TOO_COMMON, "
                + "PRONOUN_REQUIRED, PRONOUN_INVALID_VALUE, PHONE_NUMBER_INVALID_FORMAT; o REQUEST_BODY_INVALID_FORMAT sin "
                + "errors cuando el cuerpo no se puede interpretar",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "variosCamposInvalidos", value = EJEMPLO_422))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: fallo imprevisto; el detalle va solo al log",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "falloInterno", value = EJEMPLO_500))),
        @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE: Firebase no respondió; se puede "
                + "reintentar", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "firebaseNoDisponible", value = EJEMPLO_503)))
    })
    @PostMapping("/api/v1/users")
    ResponseEntity<RegisteredUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        RegisterUserResult resultado = servicio.register(request.toCommand());
        HttpStatus estado = resultado.created() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(estado).body(RegisteredUserResponse.de(resultado.account()));
    }
}
