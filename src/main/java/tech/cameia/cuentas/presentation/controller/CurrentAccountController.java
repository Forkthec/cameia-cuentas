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

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import tech.cameia.cuentas.application.service.GetCurrentAccountService;
import tech.cameia.cuentas.presentation.dto.CurrentAccountResponse;

/**
 * Devuelve los datos de la cuenta de quien llama.
 *
 * <p>Exige identidad: el API Gateway valida el token de Firebase y propaga el usuario en
 * {@code X-User-Id}. Ningún parámetro de la consulta, de la ruta ni del cuerpo decide qué cuenta se lee.</p>
 */
@RestController
@Tag(name = "Mi cuenta", description = "Datos personales de la cuenta de quien llama")
class CurrentAccountController {

    private static final String EJEMPLO_200 = """
            {"id":"3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10","firstName":"María José","lastName":"Gómez-Ruiz",
             "birthDate":"1995-04-12","phoneNumber":"+573001234567","pronoun":"SHE","status":"ACTIVE","plan":"FREE"}""";

    private static final String EJEMPLO_400 = """
            {"type":"about:blank","title":"Petición incompleta","status":400,
             "detail":"La petición no incluye los datos que exige esta ruta","instance":"/api/v1/users/me",
             "code":"IDENTITY_REQUIRED","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_404 = """
            {"type":"about:blank","title":"Cuenta no encontrada","status":404,
             "detail":"No encontramos una cuenta para este usuario","instance":"/api/v1/users/me",
             "code":"ACCOUNT_NOT_FOUND","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_406 = """
            {"type":"about:blank","title":"Tipo de respuesta no admitido","status":406,
             "detail":"Tipo de respuesta no admitido.","instance":"/api/v1/users/me",
             "code":"MEDIA_TYPE_NOT_ACCEPTABLE","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private static final String EJEMPLO_500 = """
            {"type":"about:blank","title":"Error interno","status":500,
             "detail":"Ocurrió un error. Inténtalo de nuevo.","instance":"/api/v1/users/me",
             "code":"INTERNAL_ERROR","requestId":"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601"}""";

    private final GetCurrentAccountService service;

    /**
     * Crea el controlador.
     *
     * @param service caso de uso que lee la cuenta de quien llama
     */
    CurrentAccountController(GetCurrentAccountService service) {
        this.service = service;
    }

    /**
     * Lee la cuenta del usuario que propaga el Gateway.
     *
     * @param firebaseUid identificador del usuario, emitido por el Gateway
     * @return {@code 200 OK} con los datos de la cuenta, sin correo ni contraseña
     */
    @Operation(summary = "Consulta la cuenta de quien llama",
            description = "Lee la cuenta por el identificador que propaga el Gateway; cualquier parámetro de la "
                    + "consulta se ignora. Devuelve nombres, apellidos, fecha de nacimiento, celular, pronombres, estado "
                    + "y plan; nunca el correo ni la contraseña. Los campos sin valor van presentes con null.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Datos de la cuenta de quien llama",
                headers = @Header(name = "Cache-Control", description = "Siempre no-store: la respuesta lleva datos "
                        + "personales y no debe guardarse en cachés", schema = @Schema(type = "string", example = "no-store")),
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = CurrentAccountResponse.class),
                        examples = @ExampleObject(name = "miCuenta", value = EJEMPLO_200))),
        @ApiResponse(responseCode = "400", description = "IDENTITY_REQUIRED: falta X-User-Id, o llegó en blanco o con "
                + "más de 128 caracteres", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "sinIdentidad", value = EJEMPLO_400))),
        @ApiResponse(responseCode = "404", description = "ACCOUNT_NOT_FOUND: no hay cuenta para ese usuario, o la "
                + "cuenta fue anonimizada", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "sinCuenta", value = EJEMPLO_404))),
        @ApiResponse(responseCode = "406", description = "MEDIA_TYPE_NOT_ACCEPTABLE: el cliente solo acepta un formato "
                + "distinto de JSON", content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "formatoNoDisponible", value = EJEMPLO_406))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: fallo imprevisto; el detalle va solo al log",
                content = @Content(mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples = @ExampleObject(name = "falloInterno", value = EJEMPLO_500)))
    })
    @GetMapping(value = "/api/v1/users/me", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CurrentAccountResponse> me(
            @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, hidden = true)
            @RequestHeader("X-User-Id") String firebaseUid) {
        // Nombre, fecha de nacimiento y celular: ningún navegador ni proxy compartido debe conservar la respuesta.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CurrentAccountResponse.from(service.find(firebaseUid)));
    }
}
