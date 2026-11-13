package tech.cameia.cuentas.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import tech.cameia.cuentas.application.command.AcceptPolicyCommand;
import tech.cameia.cuentas.application.service.AcceptPolicyService;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.CurrentPolicy;
import tech.cameia.cuentas.presentation.dto.PolicyAcceptanceRequest;
import tech.cameia.cuentas.presentation.dto.PolicyAcceptanceResponse;

/**
 * Registra que quien llama aceptó la versión vigente de la política de tratamiento de datos.
 *
 * <p>Exige identidad: el API Gateway valida el token de Firebase y propaga el usuario en {@code X-User-Id}. La
 * cuenta sale solo de ese encabezado y el instante, del reloj del servicio: ningún campo del cuerpo, salvo la
 * versión, decide nada.</p>
 */
@RestController
@Tag(name = "Políticas", description = "Aceptación de la política de tratamiento de datos")
class PolicyAcceptanceController {

    private static final String EJEMPLO_200 = """
            {"acceptedVersion":"2026-11-v1","acceptedAt":"2026-11-14T15:30:00Z","currentVersion":"2026-11-v1"}""";

    private static final String EJEMPLO_400 = """
            {"type":"about:blank","title":"Petición incompleta","status":400,
             "detail":"La petición no incluye los datos que exige esta ruta.","instance":"/api/v1/users/me/policy-acceptance",
             "code":"IDENTITY_REQUIRED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_404 = """
            {"type":"about:blank","title":"Cuenta no encontrada","status":404,
             "detail":"No encontramos una cuenta para este usuario.","instance":"/api/v1/users/me/policy-acceptance",
             "code":"ACCOUNT_NOT_FOUND","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_409 = """
            {"type":"about:blank","title":"Conflicto de cuenta","status":409,
             "detail":"La cuenta se está actualizando. Inténtalo de nuevo.","instance":"/api/v1/users/me/policy-acceptance",
             "code":"ACCOUNT_CONFLICT","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_406 = """
            {"type":"about:blank","title":"Tipo de respuesta no admitido","status":406,
             "detail":"Tipo de respuesta no admitido.","instance":"/api/v1/users/me/policy-acceptance",
             "code":"MEDIA_TYPE_NOT_ACCEPTABLE","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_415 = """
            {"type":"about:blank","title":"Tipo de contenido no admitido","status":415,
             "detail":"Tipo de contenido no admitido.","instance":"/api/v1/users/me/policy-acceptance",
             "code":"MEDIA_TYPE_NOT_ALLOWED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_413 = """
            {"type":"about:blank","title":"Cuerpo demasiado grande","status":413,
             "detail":"Cuerpo de la petición demasiado grande.","instance":"/api/v1/users/me/policy-acceptance",
             "code":"REQUEST_BODY_TOO_LARGE","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_500 = """
            {"type":"about:blank","title":"Error interno","status":500,
             "detail":"Ocurrió un error. Inténtalo de nuevo.","instance":"/api/v1/users/me/policy-acceptance",
             "code":"INTERNAL_ERROR","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_503_BASE = """
            {"type":"about:blank","title":"Servicio no disponible","status":503,
             "detail":"Ocurrió un error. Inténtalo de nuevo.","instance":"/api/v1/users/me/policy-acceptance",
             "code":"DATABASE_UNAVAILABLE","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_403_DESHABILITADA = """
            {"type":"about:blank","title":"Cuenta deshabilitada","status":403,
             "detail":"Esta cuenta está deshabilitada.","instance":"/api/v1/users/me/policy-acceptance",
             "code":"ACCOUNT_DISABLED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_422 = """
            {"type":"about:blank","title":"Datos no válidos","status":422,
             "detail":"Revisa los campos marcados.","instance":"/api/v1/users/me/policy-acceptance",
             "code":"VALIDATION_FAILED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601",
             "errors":[{"field":"policyVersion","code":"POLICY_VERSION_OUTDATED",
                        "message":"La política cambió. Recarga la página y vuelve a aceptarla."}]}""";

    private final AcceptPolicyService servicio;
    private final CurrentPolicy currentPolicy;

    /**
     * Crea el controlador.
     *
     * @param servicio caso de uso que registra la aceptación
     * @param currentPolicy versión vigente de la política, para responder con ella
     */
    PolicyAcceptanceController(AcceptPolicyService servicio, CurrentPolicy currentPolicy) {
        this.servicio = servicio;
        this.currentPolicy = currentPolicy;
    }

    /**
     * Registra la aceptación de la versión vigente.
     *
     * <p>Es idempotente: repetirla con la misma versión responde 200 con la fecha de la primera aceptación, sin
     * escribir de nuevo.</p>
     *
     * @param request versión que la persona aceptó
     * @param firebaseUid identificador del usuario, emitido por el Gateway
     * @return {@code 200 OK} con la versión aceptada, su fecha y la versión vigente
     */
    @Operation(summary = "Acepta la política de tratamiento de datos",
            description = "Guarda en la cuenta la versión vigente de la política y el instante UTC de la primera "
                    + "aceptación. Es idempotente: repetirla con la misma versión responde 200 con la fecha original. "
                    + "Una versión distinta de la vigente se rechaza con 422 POLICY_VERSION_OUTDATED. Cualquier campo "
                    + "del cuerpo distinto de policyVersion se ignora.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Aceptación guardada (también al repetir la llamada)",
                headers = @Header(name = "Cache-Control", description = "Siempre no-store",
                        schema = @Schema(type = "string", example = "no-store")),
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = PolicyAcceptanceResponse.class),
                        examples = @ExampleObject(name = "aceptada", value = EJEMPLO_200))),
        @ApiResponse(responseCode = "400", description = "IDENTITY_REQUIRED: falta X-User-Id, o llegó en blanco o con "
                + "más de 128 caracteres", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "sinIdentidad", value = EJEMPLO_400))),
        @ApiResponse(responseCode = "403", description = "ACCOUNT_DISABLED: la cuenta está deshabilitada",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "cuentaDeshabilitada", value = EJEMPLO_403_DESHABILITADA))),
        @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND: no hay cuenta para ese usuario, o la "
                + "cuenta fue anonimizada", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "sinCuenta", value = EJEMPLO_404))),
        @ApiResponse(responseCode = "409", description = "ACCOUNT_CONFLICT: otra petición cambió la cuenta al mismo "
                + "tiempo; se puede reintentar", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "conflicto", value = EJEMPLO_409))),
        @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED con errors[].code: POLICY_VERSION_REQUIRED "
                + "(falta la versión), POLICY_VERSION_OUTDATED (la versión no es la vigente); o "
                + "REQUEST_BODY_INVALID_FORMAT sin errors cuando el cuerpo no se puede interpretar",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "versionVieja", value = EJEMPLO_422))),
        @ApiResponse(responseCode = "406", description = "MEDIA_TYPE_NOT_ACCEPTABLE: el cliente solo acepta un "
                + "formato distinto de JSON, o su Accept no se puede interpretar",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "formatoNoDisponible", value = EJEMPLO_406))),
        @ApiResponse(responseCode = "415", description = "MEDIA_TYPE_NOT_ALLOWED: el tipo de contenido no se admite, "
                + "o es un formulario mal codificado. REQUEST_MULTIPART_INVALID_FORMAT: un multipart/* que no se pudo leer, "
                + "por ejemplo sin boundary", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "tipoNoAdmitido", value = EJEMPLO_415))),
        @ApiResponse(responseCode = "413", description = "REQUEST_BODY_TOO_LARGE: un multipart, o el cuerpo, supera el "
                + "tamaño admitido (1 MiB por parte y 10 MiB por petición)",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "cuerpoDemasiadoGrande", value = EJEMPLO_413))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: fallo imprevisto; el detalle va solo al log",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "falloInterno", value = EJEMPLO_500))),
        @ApiResponse(responseCode = "503", description = "DATABASE_UNAVAILABLE: la base de datos no está disponible; "
                + "la persona puede reintentar", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "baseNoDisponible", value = EJEMPLO_503_BASE)))
    })
    @PostMapping(value = "/api/v1/users/me/policy-acceptance", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<PolicyAcceptanceResponse> accept(@Valid @RequestBody PolicyAcceptanceRequest request,
            @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, hidden = true)
            @RequestHeader("X-User-Id") String firebaseUid) {
        Account cuenta = servicio.accept(new AcceptPolicyCommand(firebaseUid, request.policyVersion()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(PolicyAcceptanceResponse.from(cuenta.getPolicyAcceptance(), currentPolicy));
    }
}
