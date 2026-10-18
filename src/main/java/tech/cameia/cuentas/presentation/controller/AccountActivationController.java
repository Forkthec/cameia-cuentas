package tech.cameia.cuentas.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import tech.cameia.cuentas.application.command.ActivateAccountCommand;
import tech.cameia.cuentas.application.service.ActivateAccountService;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.presentation.dto.ActivatedAccountResponse;

/**
 * Activa la cuenta de quien ya verificó su correo.
 *
 * <p>Exige identidad: el API Gateway valida el token de Firebase y propaga el usuario en
 * {@code X-User-Id}. Este servicio nunca recibe ni interpreta el token.</p>
 *
 * <p>La verificación del correo sale solo del encabezado {@code X-User-Email-Verified}, que
 * el Gateway escribe a partir del claim {@code email_verified} del token. Solo el texto exacto
 * {@code true} cuenta; su ausencia o cualquier otro valor equivale a «no verificado».</p>
 */
@RestController
@Tag(name = "Activación", description = "Activación de la cuenta tras verificar el correo")
class AccountActivationController {

    private static final String EJEMPLO_200 = """
            {"id":"3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10","status":"ACTIVE"}""";

    private static final String EJEMPLO_400 = """
            {"type":"about:blank","title":"Petición incompleta","status":400,
             "detail":"La petición no incluye los datos que exige esta ruta.","instance":"/api/v1/users/me/verification",
             "code":"IDENTITY_REQUIRED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_403_CORREO = """
            {"type":"about:blank","title":"Correo sin verificar","status":403,
             "detail":"Tu correo aún no está verificado.","instance":"/api/v1/users/me/verification",
             "code":"EMAIL_NOT_VERIFIED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_403_DESHABILITADA = """
            {"type":"about:blank","title":"Cuenta deshabilitada","status":403,
             "detail":"Esta cuenta está deshabilitada.","instance":"/api/v1/users/me/verification",
             "code":"ACCOUNT_DISABLED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_404 = """
            {"type":"about:blank","title":"Cuenta no encontrada","status":404,
             "detail":"No encontramos una cuenta para este usuario.","instance":"/api/v1/users/me/verification",
             "code":"ACCOUNT_NOT_FOUND","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_409 = """
            {"type":"about:blank","title":"Conflicto de cuenta","status":409,
             "detail":"La cuenta se está actualizando. Inténtalo de nuevo.","instance":"/api/v1/users/me/verification",
             "code":"ACCOUNT_CONFLICT","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_406 = """
            {"type":"about:blank","title":"Tipo de respuesta no admitido","status":406,
             "detail":"Tipo de respuesta no admitido.","instance":"/api/v1/users/me/verification",
             "code":"MEDIA_TYPE_NOT_ACCEPTABLE","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_415 = """
            {"type":"about:blank","title":"Tipo de contenido no admitido","status":415,
             "detail":"Tipo de contenido no admitido.","instance":"/api/v1/users/me/verification",
             "code":"MEDIA_TYPE_NOT_ALLOWED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_500 = """
            {"type":"about:blank","title":"Error interno","status":500,
             "detail":"Ocurrió un error. Inténtalo de nuevo.","instance":"/api/v1/users/me/verification",
             "code":"INTERNAL_ERROR","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private final ActivateAccountService servicio;

    AccountActivationController(ActivateAccountService servicio) {
        this.servicio = servicio;
    }

    /**
     * Pasa la cuenta a activa tras la verificación del correo.
     *
     * <p>Es idempotente: repetir la llamada sobre una cuenta ya activa responde lo mismo
     * sin volver a escribir nada.</p>
     *
     * @param firebaseUid identificador del usuario, emitido por el Gateway
     * @param emailVerified lo que el Gateway afirma sobre el correo; solo {@code true} activa la cuenta
     * @return {@code 200 OK} con el identificador de la cuenta y su estado
     */
    @Operation(summary = "Activa la cuenta tras verificar el correo",
            description = "Pasa la cuenta de PENDING_VERIFICATION a ACTIVE cuando el Gateway afirma que el correo "
                    + "está verificado. Es idempotente: repetirla sobre una cuenta activa responde 200 sin escribir, aunque el Gateway no afirme la verificación. "
                    + "El cuerpo de la petición se ignora.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cuenta activa (también al repetir la llamada)",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = ActivatedAccountResponse.class),
                        examples = @ExampleObject(name = "cuentaActiva", value = EJEMPLO_200))),
        @ApiResponse(responseCode = "400", description = "IDENTITY_REQUIRED: falta X-User-Id, o llegó en blanco o con "
                + "más de 128 caracteres", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "sinIdentidad", value = EJEMPLO_400))),
        @ApiResponse(responseCode = "403", description = "EMAIL_NOT_VERIFIED: la cuenta está pendiente y el Gateway no afirmó la "
                + "verificación del correo; ACCOUNT_DISABLED: la cuenta está deshabilitada",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = {
                            @ExampleObject(name = "correoSinVerificar", value = EJEMPLO_403_CORREO),
                            @ExampleObject(name = "cuentaDeshabilitada", value = EJEMPLO_403_DESHABILITADA)
                        })),
        @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND: no hay cuenta para ese usuario, o la "
                + "cuenta fue anonimizada", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "sinCuenta", value = EJEMPLO_404))),
        @ApiResponse(responseCode = "409", description = "ACCOUNT_CONFLICT: otra petición cambió la cuenta al mismo "
                + "tiempo; se puede reintentar", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "conflicto", value = EJEMPLO_409))),
        @ApiResponse(responseCode = "406", description = "MEDIA_TYPE_NOT_ACCEPTABLE: el cliente solo acepta un "
                + "formato distinto de JSON, o su Accept no se puede interpretar", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "formatoNoDisponible", value = EJEMPLO_406))),
        @ApiResponse(responseCode = "415", description = "MEDIA_TYPE_NOT_ALLOWED: el tipo de contenido no se admite, "
                + "o es un formulario mal codificado. REQUEST_MULTIPART_INVALID_FORMAT: un multipart/* que no se pudo leer, "
                + "por ejemplo sin boundary", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "tipoNoAdmitido", value = EJEMPLO_415))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: fallo imprevisto; el detalle va solo al log",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "falloInterno", value = EJEMPLO_500)))
    })
    @PostMapping("/api/v1/users/me/verification")
    ResponseEntity<ActivatedAccountResponse> activate(
            @Parameter(name = "X-User-Id", in = ParameterIn.HEADER,
                    description = "Lo pone el Gateway a partir del token; el navegador no lo envía")
            @RequestHeader("X-User-Id") String firebaseUid,
            @Parameter(name = "X-User-Email-Verified", in = ParameterIn.HEADER,
                    description = "Lo pone el Gateway a partir del token; el navegador no lo envía. Solo `true` activa la cuenta")
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified) {

        // Solo el texto exacto «true» que escribe el Gateway cuenta; cualquier otro valor es «no verificado»
        Account cuenta = servicio.activate(new ActivateAccountCommand(firebaseUid, "true".equals(emailVerified)));
        return ResponseEntity.ok(ActivatedAccountResponse.de(cuenta));
    }
}
