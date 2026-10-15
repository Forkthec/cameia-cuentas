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

import tech.cameia.cuentas.application.service.RevokeAllSessionsService;

/**
 * Cierra la sesión de quien llama en todos sus dispositivos.
 *
 * <p>Exige identidad: el API Gateway valida el token de Firebase y propaga el usuario en {@code X-User-Id}. La petición no lleva
 * cuerpo; si llega uno, no se lee.</p>
 */
@RestController
@Tag(name = "Sesiones", description = "Cierre de las sesiones de quien llama")
class SessionRevocationController {

    private static final String EJEMPLO_400 = """
            {"type":"about:blank","title":"Petición incompleta","status":400,
             "detail":"La petición no incluye los datos que exige esta ruta.","instance":"/api/v1/auth/logout-all",
             "code":"IDENTITY_REQUIRED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_405 = """
            {"type":"about:blank","title":"Método no permitido","status":405,
             "detail":"Método no permitido.","instance":"/api/v1/auth/logout-all",
             "code":"METHOD_NOT_ALLOWED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_500 = """
            {"type":"about:blank","title":"Error interno","status":500,
             "detail":"Ocurrió un error. Inténtalo de nuevo.","instance":"/api/v1/auth/logout-all",
             "code":"INTERNAL_ERROR","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_503 = """
            {"type":"about:blank","title":"Servicio no disponible","status":503,
             "detail":"Ocurrió un error. Inténtalo de nuevo.","instance":"/api/v1/auth/logout-all",
             "code":"DEPENDENCY_UNAVAILABLE","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private final RevokeAllSessionsService service;

    /**
     * Crea el controlador.
     *
     * @param service caso de uso que revoca las sesiones de quien llama
     */
    SessionRevocationController(RevokeAllSessionsService service) {
        this.service = service;
    }

    /**
     * Revoca todas las sesiones del usuario que propaga el Gateway.
     *
     * @param firebaseUid identificador del usuario, emitido por el Gateway
     * @return {@code 204 No Content}, sin cuerpo
     */
    @Operation(summary = "Cierra la sesión en todos los dispositivos",
            description = "Revoca en Firebase todas las sesiones del usuario que propaga el Gateway. Los demás dispositivos "
                    + "pierden el acceso en su siguiente uso. Es idempotente. No lleva cuerpo; si llega uno, se ignora.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Sesiones revocadas; sin cuerpo"),
        @ApiResponse(responseCode = "400", description = "IDENTITY_REQUIRED: falta X-User-Id, o llegó en blanco o con "
                + "más de 128 caracteres", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "sinIdentidad", value = EJEMPLO_400))),
        @ApiResponse(responseCode = "405", description = "METHOD_NOT_ALLOWED: la ruta solo admite POST",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "metodoNoPermitido", value = EJEMPLO_405))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: fallo imprevisto o Firebase rechazó la "
                + "revocación por permisos; el detalle va solo al log", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "falloInterno", value = EJEMPLO_500))),
        @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE: Firebase no respondió; se puede "
                + "reintentar", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "firebaseNoDisponible", value = EJEMPLO_503)))
    })
    @PostMapping("/api/v1/auth/logout-all")
    ResponseEntity<Void> logoutAll(
            @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, hidden = true)
            @RequestHeader("X-User-Id") String firebaseUid) {
        service.revokeAll(firebaseUid);
        return ResponseEntity.noContent().build();
    }
}
