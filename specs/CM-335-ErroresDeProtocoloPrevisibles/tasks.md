# Tarjetas · CM-335 Errores de protocolo previsibles en Cuentas

**Reglas para todas las tarjetas:** identificadores y nombres de prueba nuevos en inglés (`method_shouldX_whenY`) con `@DisplayName` en español; Javadoc en todo lo nuevo; sin `CM-335` en comentarios ni rutas a otros archivos; sin dependencias nuevas; sin tocar migraciones ni `.github/`; nunca registrar valores de parámetros, cuerpos ni mensajes de la excepción de Tomcat. Escapes `\uXXXX` en Java: verificar con `git diff` que no se convirtieron en el carácter real. Si algo no coincide con la tarjeta, detenerse y reportar a Paula con la salida real. Docker Desktop encendido. Estado de la spec: **aprobada por Paula; implementada.**

---

## T0 · Preparación

1. `git fetch`; rama `CM-335-errores-de-protocolo-previsibles` desde `origin/develop`; anotar el SHA.
2. Línea base: `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS` con las pruebas de Testcontainers ejecutadas (no omitidas). Si falla, detenerse.
3. `ls docs/adr`: la spec dice 0004; si ya existe, usar el siguiente y avisar a Paula.

---

## T1 · Filtro de formularios apagado y cliente de pruebas con sockets (REQ-01, REQ-09)

**Reproducción primero (falla antes del arreglo):** crear `src/test/java/tech/cameia/cuentas/presentation/RawHttpClient.java`:

```java
/**
 * Cliente HTTP de pruebas que escribe la petición byte a byte en un socket. Existe porque
 * {@code java.net.URI} rechaza {@code %zz} y {@code TestRestTemplate} normaliza los encabezados:
 * aquí la petición mala llega tal cual al servidor.
 */
final class RawHttpClient {
    /** Respuesta reducida a lo que las pruebas comprueban. */
    record Reply(int status, Map<String, String> headers, String body) { }

    private final int port;
    RawHttpClient(int port) { this.port = port; }

    /**
     * @param method  método HTTP, por ejemplo {@code PATCH}
     * @param target  ruta y consulta tal como se escriben en la línea de petición, sin validar
     * @param headers encabezados completos {@code Nombre: valor}; se escriben en ISO-8859-1
     * @param body    cuerpo o {@code null}
     */
    Reply send(String method, String target, List<String> headers, String body) throws IOException { /* ... */ }
}
```

Esqueleto de `send`: abrir `new Socket("localhost", port)`; escribir `METHOD target HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n` + cada encabezado + `Content-Length` (en bytes del cuerpo) + `\r\n\r\n` + cuerpo, todo con `StandardCharsets.ISO_8859_1`; leer hasta el cierre; separar línea de estado, encabezados (mapa con clave en minúsculas) y cuerpo. Si la respuesta usa `Transfer-Encoding: chunked`, decodificar los fragmentos. **Trampa medida:** el largo de cada fragmento va en **bytes** y el cuerpo trae tildes: leer toda la respuesta como `ISO_8859_1` para trocear los fragmentos y convertir el cuerpo ya decodificado a UTF-8 con `new String(cuerpo.getBytes(ISO_8859_1), UTF_8)`; si se lee como UTF-8, `Integer.parseInt` falla con `NumberFormatException`.

Prueba `src/test/java/tech/cameia/cuentas/presentation/ProtocolErrorsEndToEndTest.java` (forma de `FrameworkErrorsTest`: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Testcontainers(disabledWithoutDocker = true)`, `@ServiceConnection`; el puerto con `@LocalServerPort`). Cada caso lee `code` y `requestId` del cuerpo con `ObjectMapper`:

| Prueba | Petición | Antes del arreglo | Después |
|---|---|---|---|
| `formMethod_shouldReturn405_whenFormIsMalformed` (`@ParameterizedTest`: `PUT`, `PATCH`, `DELETE`) | `/api/v1/users`, `Content-Type: application/x-www-form-urlencoded`, cuerpo `a=%zz` | 500 sin `code` | 405 `METHOD_NOT_ALLOWED`, `requestId` presente |
| `formMethod_shouldReturn404_whenRouteDoesNotExist` (3 métodos) | `/ruta-inexistente`, `a=%zz` | 500 | 404 `ROUTE_NOT_FOUND` |
| `register_shouldStillReturn415_whenFormIsWellFormed` | `POST /api/v1/users`, `a=1` | 415 | 415 |
| `register_shouldStillReturn422_whenJsonIsMalformed` | `POST /api/v1/users`, `Content-Type: application/json`, `{"a":` | 422 `REQUEST_BODY_INVALID_FORMAT` | igual |
| `noBodyRoutes_shouldKeepTheirAnswer_whenFormIsMalformed` | `POST /api/v1/auth/logout-all` con `X-User-Id: uid-cm335-a` y `a=%zz`; `POST /api/v1/users/me/verification` igual | 204 y 404 `ACCOUNT_NOT_FOUND` | igual |

Las dos primeras filas deben **fallar** con 500 antes del cambio: pegar esa salida en el resultado de la tarjeta.

**Arreglo:** en `src/main/resources/application.properties`, junto a la configuración de MVC si existe o al final del bloque web:

```properties
# Cuentas no recibe formularios. El filtro de Spring los lee antes de que exista un manejador
# de errores y un formulario mal codificado terminaba en un 500 sin formato.
spring.mvc.formcontent.filter.enabled=false
```

**Trampas:** si el 500 persiste, comprobar con `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_BOOT_WEB_SERVLET=DEBUG` que `formContentFilter` ya no aparece en «Mapping filters» y detenerse. `Connection: close` evita que el socket espere; `Content-Length` debe ser en bytes.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest=ProtocolErrorsEndToEndTest`.

**Commit:** `CM-335 | fix(cuentas): apagar el filtro de formularios de Spring y cubrirlo con pruebas de punta a punta`.

---

## T2 · Formulario mal codificado en el registro (REQ-02, REQ-03, REQ-04)

**Reproducción primero:** agregar a `ProtocolErrorsEndToEndTest` (fallan con 500 antes):

| Prueba | Petición | Esperado |
|---|---|---|
| `register_shouldReturn415_whenFormIsMalformed` | `POST /api/v1/users`, formulario `a=%zz` | 415 `MEDIA_TYPE_NOT_ALLOWED`, `detail` «Tipo de contenido no admitido.», `requestId`, cuerpo sin `zz` |
| `register_shouldReturn415_whenFormBodyIsEmpty` | `POST /api/v1/users`, `Content-Type` de formulario, cuerpo vacío | 415 (prueba de control: ya pasa antes del arreglo, medido) |
| `register_shouldReturnSameAnswer_whenRepeated20Times` | 20 veces el primer caso | 20 × 415 con el mismo `code`; `SELECT count(*) FROM microcuentas.cuenta` igual antes y después |
| `register_shouldNotFail_whenManyMalformedRequestsRunInParallel` | 50 hilos con el primer caso | 50 × 415 |
| `register_shouldNotEchoOrLogValue` | formulario `a=%zzSECRETO` con `OutputCaptureExtension` | el registro contiene `MEDIA_TYPE_NOT_ALLOWED` y no contiene `SECRETO`; el cuerpo tampoco |

**Código nuevo** `presentation/advice/BadParameterEncoding.java` (paquete-privada):

```java
/**
 * Reconoce el rechazo del contenedor ante un parámetro mal codificado ({@code %zz}).
 *
 * <p>La excepción es de Tomcat; se compara el nombre de su clase para que la capa de presentación
 * no dependa del contenedor. Se busca también en la cadena de causas por si un componente la envolvió.</p>
 */
final class BadParameterEncoding {
    private static final String TOMCAT_CLASS = "org.apache.tomcat.util.http.InvalidParameterException";
    private static final String FORM_MEDIA_TYPE = "application/x-www-form-urlencoded";
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final int MAX_CAUSES = 32;

    private BadParameterEncoding() { }

    /** @return {@code true} si la excepción o alguna de sus causas es el rechazo de Tomcat */
    static boolean matches(Throwable error) {
        int steps = 0;
        for (Throwable t = error; t != null && steps < MAX_CAUSES; t = t.getCause(), steps++) {
            if (TOMCAT_CLASS.equals(t.getClass().getName())) return true;
        }
        return false;
    }

    /** @return {@code true} si el parámetro malo venía del cuerpo de un formulario */
    static boolean fromFormBody(String method, String contentType) {
        return method != null && BODY_METHODS.contains(method.toUpperCase(Locale.ROOT))
                && contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith(FORM_MEDIA_TYPE);
    }
}
```

`presentation/advice/BusinessExceptionHandler.java`, método `falloInterno(Exception error)` (hoy sin petición): agregar el parámetro `HttpServletRequest request` y, al inicio:

```java
        if (BadParameterEncoding.matches(error)
                && BadParameterEncoding.fromFormBody(request.getMethod(), request.getContentType())) {
            // Un formulario mal codificado es un dato del cliente: se responde como el formulario bien formado.
            ProblemDetail problema = rechazo(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido",
                    "Tipo de contenido no admitido.", ErrorCode.MEDIA_TYPE_NOT_ALLOWED, "causa=formulario-mal-codificado");
            return problema;
        }
```

Los handlers vecinos que devuelven `ResponseEntity` (`HttpMediaTypeNotSupportedException`, línea ~382) agregan además el encabezado `Accept`; aquí basta `ProblemDetail`, que Spring convierte a `application/problem+json`. Verificar que la respuesta lleva `Content-Type: application/problem+json;charset=UTF-8` (la prueba lo comprueba). Actualizar el Javadoc: «Cualquier otro fallo, salvo un formulario mal codificado». Importar `jakarta.servlet.http.HttpServletRequest`. **Cambia la firma de `falloInterno`:** las dos llamadas existentes de `BusinessExceptionHandlerTest` (`manejador.falloInterno(new IllegalStateException("x"))`, líneas ~178 y ~189) dejan de compilar; pasarles `new MockHttpServletRequest()` como segundo argumento.

**Multipart mal formado (REQ-10).** Reproducción primero en `ProtocolErrorsEndToEndTest` (falla con 500 antes): `register_shouldReturn415_whenMultipartHasNoBoundary` (`POST /api/v1/users`, `Content-Type: multipart/form-data`, cuerpo `x`) → 415 `REQUEST_MULTIPART_INVALID_FORMAT`. Código en `BusinessExceptionHandler` (importar `org.springframework.web.multipart.MultipartException`, que es de Spring y no de Tomcat):

```java
    /** Cuentas no recibe multipart: uno roto es un tipo de contenido no admitido, no un fallo del servicio. */
    @ExceptionHandler(MultipartException.class)
    ProblemDetail multipartMalFormado(MultipartException error, HttpServletRequest request) {
        String tipo = request.getContentType();
        if (tipo != null && tipo.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return rechazo(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido",
                    "Tipo de contenido no admitido.", ErrorCode.REQUEST_MULTIPART_INVALID_FORMAT, "causa=multipart-mal-formado");
        }
        return falloInterno(error, request);
    }
```

Un `MaxUploadSizeExceededException` (hija de `MultipartException`) conserva su tratamiento actual si lo tuviera. Pruebas en `BusinessExceptionHandlerTest`: `multipart_shouldReturn415_whenMultipartContentType` y `multipart_shouldReturn500_whenNotMultipartContentType` (`new MultipartException("x")` con `application/json`).

**Código nuevo (lo pide CM-306):** en `domain/exception/ErrorCode.java`, junto a `REQUEST_BODY_INVALID_FORMAT`: `/** Un multipart de la petición no se pudo leer (por ejemplo, sin boundary). */ REQUEST_MULTIPART_INVALID_FORMAT,`. `ErrorCodeDocumentationTest` exige su fila en `docs/errores.md` (ver T6) y una prueba nombrada en esa fila.


**Cobertura de CM-306 (la tarea de Jira que este requisito absorbe: «Cuentas: responder con un código propio y un 4xx a las peticiones mal formadas por tipo de contenido»).** Sus criterios piden tres cosas que esta tarjeta debe cumplir además del manejador: (1) *una prueba por cada ruta afectada*: `register_shouldReturn415_whenMultipartHasNoBoundary` pasa a ser `@ParameterizedTest` con `POST /api/v1/users`, `POST /api/v1/users/me/verification`, `POST /api/v1/auth/logout-all`, `GET /api/v1/users/me` y `GET /api/v1/users/health` (el contenedor parte el multipart antes de elegir ruta, así que todas lo sufren; **medido el 17-oct sobre `develop`: las cinco dan 500, y también `PUT /api/v1/users` y una ruta inexistente**, con `boundary` responden normal); (2) *OpenAPI*: agregar `@ApiResponse(responseCode = "415", description = "MEDIA_TYPE_NOT_ALLOWED: …")` con el esquema de error y un ejemplo con `code` en los cuatro controladores (hoy ninguno documenta el 415; `CurrentAccountController` solo documenta el 406) y una prueba en la de OpenAPI vigente que lo compruebe (`apiDocs_shouldDocument415_whenRequested`: el documento contiene `MEDIA_TYPE_NOT_ALLOWED` en cada una de esas rutas); (3) *aviso a Frontend* en una línea al abrir el PR («Cuentas: un `multipart/form-data` mal formado responde 415 `MEDIA_TYPE_NOT_ALLOWED`; sin códigos nuevos»). **Estado en Jira:** CM-306 ya está en el Sprint 3 y Paula la enlazó con CM-335; pide «un código nuevo», y el 415 usa el código nuevo `REQUEST_MULTIPART_INVALID_FORMAT`, así que se cumple tal cual; CM-306 es solo de Cuentas y CM-335 suma Perfil.

**`Accept` ilegible (REQ-11).** Reproducción primero en `ProtocolErrorsEndToEndTest`: `accept_shouldReturn406WithBody_whenAcceptIsUnreadable` (`GET /api/v1/users/health`, encabezado `Accept: ;;;`) → 406 `MEDIA_TYPE_NOT_ACCEPTABLE`, `Content-Type: application/problem+json;charset=UTF-8` y `requestId` (hoy: 406 con `Content-Length: 0`). Arreglo en `BusinessExceptionHandler.responseTypeNotAcceptable`: devolver `ResponseEntity<ProblemDetail>` con `contentType(new MediaType("application", "problem+json", StandardCharsets.UTF_8))`, como ya hace el manejador de `HttpMediaTypeNotSupportedException` de la misma clase (sin ese tipo explícito Spring no puede negociar el cuerpo cuando el `Accept` no se puede interpretar). Las pruebas existentes de 406 (`FrameworkErrorsTest`) deben seguir verdes.

**Pruebas unitarias.**

`src/test/java/tech/cameia/cuentas/presentation/advice/BadParameterEncodingTest.java`:

| Prueba | Entrada | Esperado |
|---|---|---|
| `matches_shouldBeTrue_whenTomcatException` | `new org.apache.tomcat.util.http.InvalidParameterException("x")` | `true` |
| `matches_shouldFindCause_whenWrapped` | `new RuntimeException("a", new RuntimeException("b", tomcat))` | `true` |
| `matches_shouldBeFalse_whenOtherIllegalState` | `new IllegalStateException("x")` | `false` |
| `matches_shouldBeFalse_whenNull` | `null` | `false` |
| `matches_shouldStop_whenCauseChainHasCycle` | excepción que es su propia causa a través de otra | termina y devuelve `false` |
| `fromFormBody_shouldBeTrue` (`@CsvSource`) | `POST`/`PUT`/`PATCH`/`DELETE` con `application/x-www-form-urlencoded` y con `Application/X-WWW-Form-Urlencoded; charset=UTF-8` | `true` |
| `fromFormBody_shouldBeFalse` (`@CsvSource`) | `GET` con formulario; `POST` con `application/json`; `POST` con `null`; método `null` | `false` |

`BusinessExceptionHandlerTest` (ampliar con el estilo de la clase):

| Prueba | Entrada | Esperado |
|---|---|---|
| `invalidParameter_shouldReturn415_whenFormBody` (`@ValueSource` `POST`, `PUT`, `PATCH`, `DELETE`) | método con formulario y la excepción de Tomcat | 415, `code` `MEDIA_TYPE_NOT_ALLOWED`, `requestId` |
| `invalidParameter_shouldReturn415_whenFormContentTypeHasCharsetAndUppercase` | `Application/X-WWW-Form-Urlencoded; charset=UTF-8` | 415 |
| `invalidParameter_shouldReturn500_whenNotFormBody` | `GET` con la excepción de Tomcat | 500 `INTERNAL_ERROR` (no alcanzable hoy, spec REQ-03) |
| `illegalState_shouldReturn500_whenNotInvalidParameter` | `new IllegalStateException("x")` | 500 `INTERNAL_ERROR` |
| `invalidParameter_shouldNotEchoMessage` | excepción con mensaje `Parameter [a] with value [%zz]` | el cuerpo no contiene `%zz` |

**Trampas:** `InvalidParameterException` tiene constructor público `(String)` y `tomcat-embed-core` ya está en el classpath de pruebas. `presentation` no importa Tomcat en `main`. Comprobar que `LayeredArchitectureTest` sigue en verde y que el resto de `BusinessExceptionHandlerTest` no depende de la firma anterior de `falloInterno`.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest="BadParameterEncodingTest,BusinessExceptionHandlerTest,FrameworkErrorsTest,ProtocolErrorsEndToEndTest,LayeredArchitectureTest"`.

**Commit:** `CM-335 | fix(cuentas): responder 415 a un formulario mal codificado en el registro`.

---

## T3 · Errores del contenedor con la forma común (REQ-06, REQ-07)

**Medir primero (sin cambiar código):** arrancar la aplicación y anotar en el resultado de la tarjeta la respuesta de `curl -i -X TRACE http://localhost:8091/api/v1/users/me`: estado, tipo de contenido, cuerpo y si trae `Allow`.

**Reproducción primero:** en `ProtocolErrorsEndToEndTest`:

| Prueba | Petición | Antes | Después |
|---|---|---|---|
| `trace_shouldReturn405WithCommonShape` | `TRACE /api/v1/users/me` | 405 sin `code` | 405, `code` `METHOD_NOT_ALLOWED`, `requestId`, `Content-Type: application/problem+json;charset=UTF-8`, `X-Request-Id` |
| `trace_shouldKeepAllowHeader_whenContainerSentIt` | ídem | — | si la medición mostró `Allow`, la respuesta lo conserva; si no, la prueba se omite con `Assumptions` y se reporta a Paula |
| `error_shouldReturnCommonShape_whenRouteIsRequestedDirectly` | `GET /error` | — | 404 `ROUTE_NOT_FOUND` con la forma común |

**Código nuevo** `presentation/advice/ContainerErrorController.java`:

```java
/**
 * Atiende el despacho de error del contenedor ({@code /error}) para que lo que Tomcat reporta con
 * {@code sendError} (por ejemplo un {@code TRACE}) salga con la forma común del servicio y no con el
 * cuerpo por defecto de Spring Boot ({@code timestamp}, {@code error}, {@code path}).
 *
 * <p>No arma la respuesta: lanza la excepción del framework que corresponde al estado original y la
 * resuelve {@link BusinessExceptionHandler}, de modo que el {@code requestId}, el {@code charset} y el
 * registro salen del mismo lugar que el resto de errores.</p>
 */
@Hidden
@RestController
class ContainerErrorController implements ErrorController {

    /** Atiende el despacho de error del contenedor; no es un endpoint del contrato. */
    @RequestMapping("/error")
    void error(HttpServletRequest request) throws Exception {
        Integer status = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String method = request.getMethod();   // el método original de un TRACE es TRACE
        switch (status == null ? 0 : status) {
            case 0, 404 -> throw new NoResourceFoundException(HttpMethod.valueOf(method), "/", "");  // 0 = sin error original (GET /error directo); sin la ruta: puede traer %zz
            case 405 -> throw new HttpRequestMethodNotSupportedException(method);
            case 406 -> throw new HttpMediaTypeNotAcceptableException("");
            case 415 -> throw new HttpMediaTypeNotSupportedException("");
            case 400 -> throw new HttpMessageNotReadableException("", (HttpInputMessage) null);  // dato del cliente: 422 REQUEST_BODY_INVALID_FORMAT
            default -> throw new IllegalStateException("Error del contenedor sin código propio: estado=" + status);
        }
    }
}
```

Completar con los constructores reales de Spring 7 (comprobar la firma de cada excepción con el IDE o `javap` y detenerse si no coincide: verificado con `javap` en Spring 7.0.9: `NoResourceFoundException` solo tiene `(HttpMethod, String resourcePath, String reason)` (el de dos argumentos **no existe**), `HttpRequestMethodNotSupportedException(String)`, `HttpMediaTypeNotAcceptableException(String)` y `HttpMediaTypeNotSupportedException(String)` existen; si el método original no es un `HttpMethod` conocido, usar `HttpMethod.valueOf`). `ErrorController` es `org.springframework.boot.webmvc.error.ErrorController` (Spring Boot 4.1.1). `@Hidden` es de `io.swagger.v3.oas.annotations`.

**Pruebas unitarias** `ContainerErrorControllerTest` (instanciar el controlador con `MockHttpServletRequest` y atributos `jakarta.servlet.error.status_code`; para comprobar la respuesta completa, `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new BusinessExceptionHandler())` si el manejador es instanciable así en las pruebas existentes — copiar de `BusinessExceptionHandlerTest`):

| Prueba | Entrada | Esperado |
|---|---|---|
| `error_shouldMapStatus` (`@CsvSource`) | `404,ROUTE_NOT_FOUND`; `405,METHOD_NOT_ALLOWED`; `406,MEDIA_TYPE_NOT_ACCEPTABLE`; `415,MEDIA_TYPE_NOT_ALLOWED`; `400,REQUEST_BODY_INVALID_FORMAT`; `413,INTERNAL_ERROR`; `503,INTERNAL_ERROR` | `code` y estado del manejador (el 400 sale como 422) |
| `error_shouldReturn404_whenNoOriginalStatus` | sin atributos | 404 `ROUTE_NOT_FOUND`, registro `WARN` |
| `error_shouldKeepRequestId_whenHeaderIsValid` | `X-Request-Id: abc-1` | `requestId` `abc-1` |
| `error_shouldNotLeakPath_whenOriginalUriIsMalformed` | `request_uri` `/x/%zz` | el cuerpo no contiene `%zz` |

Prueba de OpenAPI: buscar el nombre real con `grep -rln "api-docs" src/test` y agregar `apiDocs_shouldNotListErrorRoute_whenRequested` (el documento no contiene `/error`).

**Clasificar la excepción sin código (obligatorio):** la rama `default` lanza una `IllegalStateException`, y `UntypedExceptionClassificationTest` (en `architecture/`) falla hasta que se agregue a su mapa `CLASSIFICATION` la entrada `Map.entry("tech.cameia.cuentas.presentation.advice.ContainerErrorController#error", UNEXPECTED_FAILURE)`. Medido: sin ella, `clean verify` falla. Agregar `UntypedExceptionClassificationTest` al comando de la tarjeta. **Medido:** con este diseño el `instance` de la respuesta queda `/error` (no la ruta original) y el `Allow` del contenedor se conserva. **Trampas:** si el arranque falla con «Ambiguous mapping» para `/error`, dos controladores compiten; detenerse y reportar. `HttpServlet.doTrace` no interviene: es Tomcat el que responde 405 (`allowTrace=false`). El método original en el despacho de error de un `TRACE` puede ser `TRACE`: si `HttpMethod.valueOf` no lo acepta, usar `new HttpRequestMethodNotSupportedException(method)` (acepta texto) y para 404 pasar `HttpMethod.GET`.

**Comando:** `./mvnw.cmd -B -ntp test "-Dtest=ContainerErrorControllerTest,BusinessExceptionHandlerTest,FrameworkErrorsTest,ProtocolErrorsEndToEndTest,LayeredArchitectureTest,UntypedExceptionClassificationTest"`.

**Commit:** `CM-335 | fix(cuentas): dar la forma común a los errores del contenedor, como TRACE`.

---

## T4 · Caracteres de control en la identidad (REQ-05)

**Reproducción primero** `src/test/java/tech/cameia/cuentas/domain/model/FirebaseUidTest.java` (nueva):

| Prueba | Entrada | Esperado |
|---|---|---|
| `required_shouldThrow_whenControlCharacter` (`@MethodSource`) | `"uid\t1"`, `"\u0085"`, `"\u009f"`, `"a\u0000"`, `"\n"`, `"\u007f"` | `IdentityRequiredException` con `getErrorCode()` igual a `ErrorCode.IDENTITY_REQUIRED` |
| `required_shouldAccept_whenPrintable` | `"uid-123_ABC"`, `"uid-ñ"`, `"a".repeat(128)` | devuelve la identidad con el mismo `value()` |
| `required_shouldThrow_whenNullBlankOrTooLong` | `null`, `""`, `"   "`, `"a".repeat(129)` | `IdentityRequiredException` |

Punta a punta, en `ProtocolErrorsEndToEndTest`: `identity_shouldReturn400_whenHeaderHasTab` (`@ParameterizedTest` con `GET /api/v1/users/me`, `POST /api/v1/users/me/verification` y `POST /api/v1/auth/logout-all`; encabezado `X-User-Id: uid<TAB>x` escrito con `\t`) → 400 `IDENTITY_REQUIRED` en las tres; y `identity_shouldReturn400_whenHeaderHasC1Control` con el byte `0x85` en una de ellas.

**Código nuevo** `domain/model/FirebaseUid.java`:

```java
/**
 * Identificador del usuario en Firebase Authentication, tal como lo propaga el Gateway en
 * {@code X-User-Id}. Reúne la comprobación que antes se repetía en cada caso de uso.
 *
 * @param value identificador ya comprobado
 */
public record FirebaseUid(String value) {

    /** Largo máximo de un identificador de Firebase; es el de la columna {@code firebase_uid}. */
    public static final int MAX_LENGTH = 128;

    /**
     * Convierte la identidad que llega del Gateway o rechaza la petición si falta o no es válida.
     *
     * <p>Un identificador de Firebase es ASCII, así que contar unidades UTF-16 equivale a contar puntos de código.</p>
     *
     * @param raw valor del encabezado {@code X-User-Id}
     * @return la identidad comprobada
     * @throws IdentityRequiredException si es nula, está en blanco, mide más de 128 caracteres o trae caracteres de control
     */
    public static FirebaseUid required(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_LENGTH || raw.chars().anyMatch(Character::isISOControl)) {
            throw new IdentityRequiredException();
        }
        return new FirebaseUid(raw);
    }
}
```

**Usos** (antes → después), conservando el orden de las demás comprobaciones de cada servicio:

- `GetCurrentAccountService.find`: borrar la constante `MAX_FIREBASE_UID_LENGTH` y el `if`; `FirebaseUid uid = FirebaseUid.required(firebaseUid);` y `repository.findByFirebaseUid(uid.value())`. Actualizar el Javadoc (`@throws IdentityRequiredException si … o trae caracteres de control`).
- `ActivateAccountService` (línea ~60) y `RevokeAllSessionsService` (línea ~39): reemplazar el `if (... GetCurrentAccountService.MAX_FIREBASE_UID_LENGTH) throw …` por `FirebaseUid.required(firebaseUid)`.

**Trampas:** antes de borrar la constante, `grep -rn MAX_FIREBASE_UID_LENGTH src` (hoy solo la usan los tres servicios). Las pruebas existentes de los tres servicios deben seguir verdes sin cambios. No usar `Character.isWhitespace`.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest="FirebaseUidTest,GetCurrentAccountServiceTest,ActivateAccountServiceTest,RevokeAllSessionsServiceTest,CurrentAccountControllerTest,AccountActivationControllerTest,SessionRevocationControllerTest,ProtocolErrorsEndToEndTest,LayeredArchitectureTest"`.

**Commit:** `CM-335 | fix(cuentas): rechazar caracteres de control en la identidad`.

---

## T5 · Postman (spec §13)

Agregar a `postman/cameia-cuentas.postman_collection.json` la carpeta «Errores de protocolo» con C-01, C-02 y C-03 (cuerpo en modo `raw` con el encabezado `Content-Type: application/x-www-form-urlencoded` escrito a mano; `TRACE` como método personalizado). Cada petición comprueba estado, `code`, `requestId` y `Content-Type` con `charset=UTF-8`. Actualizar la lista de carpetas de `postman/README.md`.

Correr: levantar el jar empaquetado con la base de `docker compose`; `newman run postman/cameia-cuentas.postman_collection.json -e postman/local.postman_environment.json --folder "Errores de protocolo"`; guardar la salida en `ejecucion/evidencia/CM-335/`. Si Newman no envía lo esperado, aplicar la trampa de la spec §13 y reportar.

**Commit:** `CM-335 | test(cuentas): colección de Postman de los errores de protocolo`.

---

## T6 · Documentos (REQ-08)

1. `docs/errores.md`: en la fila de `MEDIA_TYPE_NOT_ALLOWED`, agregar «también un formulario mal codificado en `POST /api/v1/users`»; fila nueva de `REQUEST_MULTIPART_INVALID_FORMAT` (415, cualquier ruta con un `multipart/*` mal formado, `BusinessExceptionHandler` (`MultipartException`), pruebas `BusinessExceptionHandlerTest.multipart_shouldReturn415_whenMultipartContentType` y `ProtocolErrorsEndToEndTest.register_shouldReturn415_whenMultipartHasNoBoundary`); en la de `MEDIA_TYPE_NOT_ACCEPTABLE`, «incluido un `Accept` ilegible»; en la de `METHOD_NOT_ALLOWED`, «incluido `TRACE`»; en la de `IDENTITY_REQUIRED`, «o con caracteres de control»; una línea sobre `/error` junto al respaldo del framework. En la tabla de la sección «Respuestas publicadas que difieren del estándar», filas nuevas con el contenido de la spec §12 (qué petición, qué responde Tomcat, qué hace el Gateway) y la fila de D-3 («Cuentas no lee parámetros de consulta; el primero que se agregue usa `REQUEST_QUERY_INVALID_FORMAT`, definido en Perfil»).
2. `docs/adr/0004-errores-de-protocolo-previsibles.md` (formato del ADR 0003: Estado, Contexto, Decisión, Consecuencias, Alternativas descartadas) con D-1 a D-7 de la spec.
3. `docs/bitacora-ia/`: entrada `NN_errores-de-protocolo-previsibles_prompt.md` con el siguiente número del `README.md` (solo el prompt armado) y su fila en la tabla, con salida `../../specs/CM-335-ErroresDeProtocoloPrevisibles/spec.md`. La decisión humana (HITL) queda `PENDIENTE (Paula)`.
4. `CLAUDE.md` del repo: revisar si algo cambia (rutas, pendientes) y dejar constancia.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest="ErrorCodeDocumentationTest"`.

**Commit:** `CM-335 | docs(cuentas): catálogo de errores, ADR 0004 y bitácora`.

---

## T7 · Cierre (una vez, sobre la punta)

1. `./mvnw.cmd -B -ntp clean verify` completo; cobertura por clase tocada (≥ 90 % de líneas y ramas) desde `target/site/jacoco/jacoco.csv`; cada línea o rama sin cubrir con su razón.
2. Newman (T5) contra el jar empaquetado y la base real.
3. Peticiones reales a cada fila del §2 de la spec (E-1 a E-10) con el jar empaquetado (los bytes `0x85`/`0x9f` de E-6 se envían con un socket crudo, no con `curl`: en Windows `curl` los reescribe y la petición deja de ser mala); guardar la salida en `ejecucion/evidencia/CM-335/`. Ninguna debe dar 500 ni la forma por defecto de Spring; las de §12 deben dar el 400 HTML documentado.
4. Lista de verificación de la spec (V-01 a V-13), fila por fila.
5. `ejecucion/evidencia/CM-335/resumen.md`: lo hecho por tarjeta, cobertura por clase, hallazgos, líneas o ramas sin cubrir, commits y la tabla regla R1–R14 → evidencia con salida real. No marcar «cumple» lo que no se ejecutó.
6. Sin push ni PR: se pregunta a Paula con el resumen.

---

## Resultados de la ejecución

Línea base en `develop` 13055eb: `clean verify` con 1109 pruebas, 0 fallos, 0 omitidas. Punta: `clean verify` con 1212 pruebas, 0 fallos, 0 omitidas.

| Tarjeta | Antes del arreglo | Después | Comando |
|---|---|---|---|
| T1 | 6 de 9 fallan con 500 (formulario `a=%zz` con `PUT`, `PATCH`, `DELETE`) | 9 de 9 pasan | `-Dtest=ProtocolErrorsEndToEndTest` |
| T2 | 11 fallos y 1 error: formulario y `multipart` en 500, `Accept` ilegible sin cuerpo | 94 pruebas de la tarjeta y 196 con los controladores y OpenAPI, todas verdes | comando de la tarjeta |
| T3 | `GET /error` responde 500; `TRACE` conserva `Allow` pero sin la forma común | 99 pruebas verdes | comando de la tarjeta |
| T4 | `X-User-Id` con tabulador o `0x85` responde 404 o 204 | 127 pruebas verdes | comando de la tarjeta |
| T5 | — | Newman: 6 peticiones, 57 aserciones, 0 fallos (incluye el `multipart` sin `boundary` y el `Accept` ilegible); la colección completa sin la carpeta de eventos, que necesita el broker: 56 peticiones, 330 aserciones, 0 fallos | `newman run … --folder "Errores de protocolo"` |
| T6 | — | `ErrorCodeDocumentationTest` verde; ADR 0004 y entrada 12 de la bitácora | `-Dtest=ErrorCodeDocumentationTest` |

Desviaciones: el `Content-Type` de todo error se fija en `application/problem+json;charset=UTF-8` desde `resolverRequestId`, no solo en el 406, para que un `Accept` ilegible no deje sin cuerpo a un 422, 415, 404 o 405 (cubierto por pruebas de punta a punta); el 406 está documentado en OpenAPI en las cinco rutas. La fila de `REQUEST_MULTIPART_INVALID_FORMAT` de `docs/errores.md` se agregó en el commit de T2 (no en T6) para que `ErrorCodeDocumentationTest` siguiera verde en cada commit. El `HealthController` no tenía anotaciones de OpenAPI; se le agregó `@Operation` y las respuestas 200 y 415 para documentar el 415 de las cinco rutas.

