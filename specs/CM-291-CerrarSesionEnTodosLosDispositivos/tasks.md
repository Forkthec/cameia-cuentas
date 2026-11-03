# Tarjetas — CM-291 en `cameia-cuentas`

Cada tarjeta basta por sí sola: quien la ejecuta no necesita leer la spec ni el plan. Reglas que aplican a **todas**:

- Identificadores en inglés; Javadoc, comentarios, `@DisplayName`, mensajes, logs y OpenAPI en español. Nunca `CM-291` ni rutas de
  otros archivos en comentarios de código.
- Nombres de prueba nuevos: `metodo_shouldX_whenY` con `@DisplayName` en español. Arrange-Act-Assert, un comportamiento por prueba,
  valores literales, sin `Thread.sleep`.
- Prohibido: dependencias nuevas, tocar migraciones, `ErrorCode`, `BusinessExceptionHandler` o `.github/`, registrar tokens, correos o
  cuerpos de petición, usar Lombok, `catch` vacío.
- Comandos en PowerShell desde la raíz del repositorio. Docker Desktop encendido (Testcontainers).
- **Detenerse y reportar** (con la salida real) si: la tarjeta contradice el código, una prueba existente se rompe sin causa clara o
  hace falta algo no listado. No improvisar diseño.
- Commit al cerrar cada tarjeta con el mensaje indicado, sin `[IA-ASISTIDO]` y con el trailer `Co-Authored-By` del modelo.

---

## C-0 · Preparación y línea base (sin commit)

1. `git fetch origin`; `git status` limpio (si `mvnw.cmd` aparece modificado, es un cambio local previo de Paula: **no** se incluye;
   preguntar antes de descartarlo).
2. `git switch -c CM-291-cerrar-sesion-en-todos-los-dispositivos origin/develop` (base esperada `5ed1159` o posterior; anotar el SHA).
3. `./mvnw.cmd clean verify` → esperado `BUILD SUCCESS`, 0 fallos. Guardar el resumen en
   `C:\Users\paanm\Documents\cameia\ejecucion\evidencia\CM-291\base-cuentas.log`.
4. Si falla: detenerse y reportar; es un hallazgo de la línea base, no de esta tarea.

---

## C-1 · Revocar las sesiones en el puerto y el adaptador

**Requisitos:** REQ-291-01, 05, 06, 07, 09; MAN-02.

**Archivos:** `domain/port/FirebaseUserDirectory.java`, `infrastructure/client/FirebaseUserDirectoryAdapter.java`,
`src/test/.../infrastructure/client/InMemoryFirebaseUserDirectory.java`, `src/test/.../infrastructure/client/FirebaseUserDirectoryAdapterTest.java`.

**Puerto** (agregar al final de la interfaz, con este Javadoc):

```java
    /**
     * Revoca todas las sesiones del usuario en todos sus dispositivos.
     *
     * <p>Después de revocar, ningún dispositivo puede renovar su sesión, y los tokens emitidos antes dejan de servir en cuanto el
     * Gateway comprueba la revocación. Un usuario que ya no existe en el directorio no tiene sesiones: no es un error.</p>
     *
     * @param firebaseUid identificador del usuario
     * @throws DependencyUnavailableException si el directorio no respondió o falló de su lado
     */
    void revokeSessions(String firebaseUid);
```

**Adaptador** (después de `deleteUser`):

```java
    /**
     * Revoca las sesiones del usuario con {@code revokeRefreshTokens}.
     *
     * <p>Una credencial que ya no existe no tiene sesiones que revocar: se da por hecho, igual que al borrar.</p>
     *
     * @param firebaseUid identificador del usuario
     * @throws DependencyUnavailableException si Firebase no respondió o falló de su lado
     * @throws IllegalStateException si Firebase rechaza la revocación por cualquier otro motivo (permisos o configuración)
     */
    @Override
    public void revokeSessions(String firebaseUid) {
        try {
            firebaseAuth.revokeRefreshTokens(firebaseUid);
            logger.info("Sesiones revocadas en Firebase para el usuario {}", firebaseUid);
        } catch (FirebaseAuthException error) {
            if (AuthErrorCode.USER_NOT_FOUND.equals(error.getAuthErrorCode())) {
                logger.info("El usuario {} no existe en Firebase; no hay sesiones que revocar", firebaseUid);
                return;
            }
            throw unavailableOrRejection(error, "Firebase rechazó la revocación de las sesiones del usuario");
        }
    }
```

**Doble en memoria** (`InMemoryFirebaseUserDirectory`), siguiendo el estilo del archivo (nombres existentes en español se respetan;
lo nuevo en inglés):

```java
    private final List<String> revokedUids = new CopyOnWriteArrayList<>();
    private volatile boolean unavailableOnRevoke;
    private volatile boolean rejectOnRevoke;

    @Override
    public void revokeSessions(String firebaseUid) {
        if (unavailableOnRevoke) {
            throw new DependencyUnavailableException(new IllegalStateException("Firebase no respondió"));
        }
        if (rejectOnRevoke) {
            throw new IllegalStateException("Firebase rechazó la revocación de las sesiones del usuario");
        }
        revokedUids.add(firebaseUid);
    }

    /** @return los identificadores revocados, en orden y con repeticiones */
    public List<String> revokedUids() { return List.copyOf(revokedUids); }

    /** Hace que la siguiente revocación falle como si Firebase no respondiera. */
    public void becomeUnavailableOnRevoke() { unavailableOnRevoke = true; }

    /** Hace que la siguiente revocación falle como un rechazo de configuración. */
    public void rejectRevocations() { rejectOnRevoke = true; }
```

Y en el método `limpiar()` existente: `revokedUids.clear(); unavailableOnRevoke = false; rejectOnRevoke = false;`. Cada método nuevo
lleva Javadoc completo.

**Pruebas nuevas en `FirebaseUserDirectoryAdapterTest`** (usa el `firebaseAuth` simulado del archivo y construye las excepciones como
las existentes: `new FirebaseAuthException(ErrorCode.X, "texto", null, null, AuthErrorCode.Y o null)`):

| Método | Arrange | Assert |
|---|---|---|
| `revokeSessions_shouldCallFirebaseWithUid_whenUserExists` | nada | `verify(firebaseAuth).revokeRefreshTokens("uid-rev-1")` |
| `revokeSessions_shouldSucceed_whenUserNotFound` | `doThrow(new FirebaseAuthException(ErrorCode.NOT_FOUND, "sin usuario", null, null, AuthErrorCode.USER_NOT_FOUND))` | no lanza (`assertThatCode(...).doesNotThrowAnyException()`) |
| `revokeSessions_shouldThrowDependencyUnavailable_whenFirebaseFails` (`@ParameterizedTest` `@EnumSource(names = {"UNAVAILABLE","DEADLINE_EXCEEDED","INTERNAL","RESOURCE_EXHAUSTED"})`) | `doThrow(new FirebaseAuthException(codigo, "fallo", null, null, null))` | `DependencyUnavailableException` |
| `revokeSessions_shouldThrowDependencyUnavailable_whenConnectionFails` | `new FirebaseAuthException(ErrorCode.UNKNOWN, "conexión rechazada", new java.net.ConnectException("rechazada"), null, null)` | `DependencyUnavailableException` |
| `revokeSessions_shouldThrowIllegalState_whenFirebaseRejects` | `ErrorCode.PERMISSION_DENIED`, sin código de autenticación | `IllegalStateException` con mensaje que contiene `revocación` |
| `revokeSessions_shouldLogUidOnly` (`@ExtendWith(OutputCaptureExtension.class)` como otras pruebas del repo) | éxito con `uid-rev-log` | la salida contiene `uid-rev-log` y no contiene `Bearer` ni `@` |

`revokeRefreshTokens` devuelve `void`: usar `doThrow(...).when(firebaseAuth).revokeRefreshTokens("uid")`.

**Trampa:** `UntypedExceptionClassificationTest` debería seguir en verde (el método solo relanza lo que devuelve
`unavailableOrRejection`, ya clasificado). Si falla nombrando `FirebaseUserDirectoryAdapter#revokeSessions`, agregar
`Map.entry(CLIENT_PACKAGE + "FirebaseUserDirectoryAdapter#revokeSessions", UNEXPECTED_FAILURE)` y anotarlo en el reporte.

**Comando:** `./mvnw.cmd test "-Dtest=FirebaseUserDirectoryAdapterTest,UntypedExceptionClassificationTest,LayeredArchitectureTest"`.
**Terminado:** las 9 ejecuciones nuevas (contando los 4 valores parametrizados) en verde y las tres clases en verde.
**Commit:** `CM-291 | feat(cuentas): revocar las sesiones de un usuario en Firebase`.

**Resultado (15-oct-2026):** FirebaseUserDirectoryAdapterTest 55 (46 existentes + 9 nuevas: 6 métodos, uno con 4 valores), UntypedExceptionClassificationTest 1 y LayeredArchitectureTest 3: 59 pruebas, 0 fallos, `BUILD SUCCESS`; no hizo falta tocar la clasificación de excepciones sin código. Las pruebas nuevas entraron en un commit propio (`pruebas del adaptador que revoca las sesiones`) porque la primera ejecución no las incluía por un error del script que las escribía.

---

## C-2 · Caso de uso `RevokeAllSessionsService`

**Requisitos:** REQ-291-01, 02, 04, 05, 06; FIA-02.

**Archivo nuevo:** `src/main/java/tech/cameia/cuentas/application/service/RevokeAllSessionsService.java`.

```java
package tech.cameia.cuentas.application.service;

import org.springframework.stereotype.Service;

import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.IdentityRequiredException;
import tech.cameia.cuentas.domain.port.FirebaseUserDirectory;

/**
 * Cierra la sesión de quien llama en todos sus dispositivos.
 *
 * <p>La identidad sale solo del encabezado que pone el Gateway: nada del cuerpo, la consulta ni la ruta decide qué sesiones se
 * revocan. No lee ni escribe la base de datos, así que no abre transacción: revocar no depende del estado de la cuenta local.</p>
 */
@Service
public class RevokeAllSessionsService {

    private final FirebaseUserDirectory directory;

    /**
     * Crea el caso de uso.
     *
     * @param directory puerto del directorio de usuarios
     */
    public RevokeAllSessionsService(FirebaseUserDirectory directory) {
        this.directory = directory;
    }

    /**
     * Revoca todas las sesiones del usuario que propaga el Gateway.
     *
     * <p>Es idempotente: repetirla vuelve a revocar y no tiene otro efecto.</p>
     *
     * @param firebaseUid identificador del usuario que propaga el Gateway
     * @throws IdentityRequiredException si el identificador es nulo, está en blanco o es más largo que el de Firebase
     * @throws DependencyUnavailableException si Firebase no respondió o falló de su lado
     */
    public void revokeAll(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()
                || firebaseUid.length() > GetCurrentAccountService.MAX_FIREBASE_UID_LENGTH) {
            throw new IdentityRequiredException(); // misma regla que la consulta y la activación de la cuenta
        }
        directory.revokeSessions(firebaseUid);
    }
}
```

**Archivo nuevo:** `src/test/java/tech/cameia/cuentas/application/service/RevokeAllSessionsServiceTest.java`, con
`new InMemoryFirebaseUserDirectory()` (sin Spring):

| Método | Entrada | Esperado |
|---|---|---|
| `revokeAll_shouldRevokeCallerSessions_whenIdentityValid` | `"uid-propio"` | `revokedUids()` = `["uid-propio"]` |
| `revokeAll_shouldRejectIdentity_whenBlank` (`@ParameterizedTest` `@ValueSource(strings = {"", "   ", "\t\n"})`) | cada valor | `IdentityRequiredException`; `revokedUids()` vacío |
| `revokeAll_shouldRejectIdentity_whenNull` | `null` | `IdentityRequiredException`; vacío |
| `revokeAll_shouldRevoke_whenUidHasMaximumLength` (`@ValueSource(ints = {127, 128})`) | `"a".repeat(n)` | revocado ese valor |
| `revokeAll_shouldRejectIdentity_whenUidLongerThan128` | `"a".repeat(129)` | `IdentityRequiredException`; vacío |
| `revokeAll_shouldHaveNoOtherEffect_whenRepeated20Times` | 20 llamadas con `"uid-rep"` | `revokedUids()` tiene 20 elementos, todos `"uid-rep"`; `cantidadDeUsuarios()` igual que antes |
| `revokeAll_shouldPropagateUnavailable_whenDirectoryFails` | `becomeUnavailableOnRevoke()` | `DependencyUnavailableException` |
| `revokeAll_shouldReturnNormally_whenUserDoesNotExist` | `"uid-que-no-existe"` (el doble no valida existencia) | no lanza y queda registrado |

**Comando:** `./mvnw.cmd test "-Dtest=RevokeAllSessionsServiceTest,LayeredArchitectureTest"`.
**Commit:** `CM-291 | feat(cuentas): caso de uso para cerrar sesión en todos los dispositivos`.

**Resultado (15-oct-2026):** RevokeAllSessionsServiceTest 11 (8 métodos; con los valores parametrizados), LayeredArchitectureTest 3 y UntypedExceptionClassificationTest 1: 15 pruebas, 0 fallos, `BUILD SUCCESS`.

---

## C-3 · Controlador `POST /api/v1/auth/logout-all`

**Requisitos:** REQ-291-01 a 04, 06 a 08; IOP-01; CA-1.8.1 y CA-1.8.2 (parte de Backend).

**Archivo nuevo:** `src/main/java/tech/cameia/cuentas/presentation/controller/SessionRevocationController.java`. Modelo:
`CurrentAccountController` (mismo estilo de constantes de ejemplo, `@Parameter(hidden = true)` para `X-User-Id`).

```java
/**
 * Cierra la sesión de quien llama en todos sus dispositivos.
 *
 * <p>Exige identidad: el API Gateway valida el token de Firebase y propaga el usuario en {@code X-User-Id}. La petición no lleva
 * cuerpo; si llega uno, no se lee.</p>
 */
@RestController
@Tag(name = "Sesiones", description = "Cierre de las sesiones de quien llama")
class SessionRevocationController {

    // EJEMPLO_400 (IDENTITY_REQUIRED), EJEMPLO_405 (METHOD_NOT_ALLOWED), EJEMPLO_500 (INTERNAL_ERROR) y EJEMPLO_503
    // (DEPENDENCY_UNAVAILABLE): mismos textos que docs/errores.md, con "instance":"/api/v1/auth/logout-all" y un requestId de ejemplo.

    private final RevokeAllSessionsService service;

    SessionRevocationController(RevokeAllSessionsService service) { this.service = service; }

    /**
     * Revoca todas las sesiones del usuario que propaga el Gateway.
     *
     * @param firebaseUid identificador del usuario, emitido por el Gateway
     * @return {@code 204 No Content}, sin cuerpo
     */
    @Operation(summary = "Cierra la sesión en todos los dispositivos",
            description = "Revoca en Firebase todas las sesiones del usuario que propaga el Gateway. Los demás dispositivos "
                    + "pierden el acceso en su siguiente uso. Es idempotente. No lleva cuerpo; si llega uno, se ignora.")
    @ApiResponses({ /* 204 sin contenido; 400; 405; 500; 503 — cada uno con application/problem+json, ProblemDetail y su ejemplo */ })
    @PostMapping("/api/v1/auth/logout-all")
    ResponseEntity<Void> logoutAll(
            @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, hidden = true)
            @RequestHeader("X-User-Id") String firebaseUid) {
        service.revokeAll(firebaseUid);
        return ResponseEntity.noContent().build();
    }
}
```

Descripciones de `@ApiResponse` (literal): 204 «Sesiones revocadas; sin cuerpo»; 400 «IDENTITY_REQUIRED: falta X-User-Id, o llegó en
blanco o con más de 128 caracteres»; 405 «METHOD_NOT_ALLOWED: la ruta solo admite POST»; 500 «INTERNAL_ERROR: fallo imprevisto o
Firebase rechazó la revocación por permisos; el detalle va solo al log»; 503 «DEPENDENCY_UNAVAILABLE: Firebase no respondió; se puede
reintentar». No declarar `consumes` ni `produces`: así un cuerpo con cualquier `Content-Type` no provoca 415 y el 204 no provoca 406.

**Archivo nuevo:** `src/test/java/tech/cameia/cuentas/presentation/controller/SessionRevocationControllerTest.java`, con
`MockMvcBuilders.standaloneSetup(new SessionRevocationController(service)).setControllerAdvice(ProblemDetailTestSupport.manejadorDeErrores())`
y `service = mock(RevokeAllSessionsService.class)`. Ruta `"/api/v1/auth/logout-all"`.

| Método | Petición | Esperado |
|---|---|---|
| `logoutAll_shouldReturn204WithoutBody_whenIdentityPresent` | `post(RUTA).header("X-User-Id","uid-ctrl-1")` | 204; cuerpo vacío (`content().string("")`); sin `Content-Type`; `verify(service).revokeAll("uid-ctrl-1")` |
| `logoutAll_shouldReturn400_whenIdentityHeaderMissing` | sin encabezado | 400; `$.code` `IDENTITY_REQUIRED`; `$.detail` «La petición no incluye los datos que exige esta ruta.»; `verify(service, never()).revokeAll(any())` |
| `logoutAll_shouldReturn400_whenServiceRejectsIdentity` | `X-User-Id: "   "`; `doThrow(new IdentityRequiredException()).when(service).revokeAll("   ")` | 400 `IDENTITY_REQUIRED` |
| `logoutAll_shouldRevokeHeaderUserOnly_whenBodyNamesAnotherUser` | `X-User-Id: uid-propio`, `contentType(APPLICATION_JSON)`, cuerpo `{"firebaseUid":"uid-ajeno"}` | 204; `verify(service).revokeAll("uid-propio")`; `verifyNoMoreInteractions(service)` |
| `logoutAll_shouldRevokeHeaderUserOnly_whenQueryNamesAnotherUser` | `?uid=uid-ajeno&firebaseUid=uid-ajeno` | 204; solo `uid-propio` |
| `logoutAll_shouldIgnoreBody_whenMalformedJson` | `contentType(APPLICATION_JSON)`, cuerpo `{"x":` | 204 |
| `logoutAll_shouldIgnoreBody_whenUnexpectedContentType` | `contentType(TEXT_PLAIN)`, cuerpo `hola` | 204 |
| `logoutAll_shouldReturn503_whenDirectoryUnavailable` | `doThrow(new DependencyUnavailableException(new IllegalStateException("caído")))` | 503; `$.code` `DEPENDENCY_UNAVAILABLE`; `$.detail` «Ocurrió un error. Inténtalo de nuevo.» |
| `logoutAll_shouldReturn500_whenDirectoryRejects` | `doThrow(new IllegalStateException("Firebase rechazó la revocación de las sesiones del usuario"))` | 500; `$.code` `INTERNAL_ERROR`; el cuerpo no contiene `Firebase` ni `IllegalState` |
| `logoutAll_shouldReturn405_whenGet` | `get(RUTA).header("X-User-Id","uid")` | 405; `$.code` `METHOD_NOT_ALLOWED`; encabezado `Allow` contiene `POST` |
| `logoutAll_shouldNotEchoIdentity_whenHostileValue` | `X-User-Id: ' OR 1=1 --` | 204; cuerpo vacío |

En todas las respuestas de error comprobar `Content-Type` compatible con `application/problem+json` y `$.requestId` presente.

**Trampa:** si `ProblemDetailTestSupport.manejadorDeErrores()` no existe con ese nombre exacto, usar el que usa
`CurrentAccountControllerTest` en `origin/develop` (es la referencia).

**Comando:** `./mvnw.cmd test "-Dtest=SessionRevocationControllerTest,FrameworkErrorsTest,LayeredArchitectureTest"`.
**Commit:** `CM-291 | feat(cuentas): POST /api/v1/auth/logout-all responde 204`.

**Resultado (15-oct-2026):** SessionRevocationControllerTest 11, FrameworkErrorsTest 6, LayeredArchitectureTest 3 y UntypedExceptionClassificationTest 1: 21 pruebas, 0 fallos, `BUILD SUCCESS`. `ProblemDetailTestSupport.manejadorDeErrores()` existe con ese nombre.

---

## C-4 · Prueba de extremo a extremo

**Requisitos:** CA-1.8.1, CA-1.8.2 (parte B), REQ-291-01, concurrencia.

**Archivo nuevo:** `src/test/java/tech/cameia/cuentas/SessionRevocationEndToEndTest.java`. Copiar la cabecera de
`CurrentAccountEndToEndTest` (`@SpringBootTest(RANDOM_PORT)`, `@AutoConfigureTestRestTemplate`,
`@Testcontainers(disabledWithoutDocker = true)`, contenedor `postgres:16-alpine` con `@ServiceConnection`, `TestRestTemplate client`,
`InMemoryFirebaseUserDirectory directory` autoinyectado, `@BeforeEach` con `directory.limpiar()`).

| Método | Pasos | Esperado |
|---|---|---|
| `logoutAll_shouldRevokeSessionsAndReturn204_whenCalledWithIdentity` | `POST /api/v1/auth/logout-all` con `X-User-Id: uid-e2e-1` | 204; cuerpo nulo; `directory.revokedUids()` = `["uid-e2e-1"]` |
| `logoutAll_shouldReturn204Twice_whenRepeated` | dos `POST` iguales | 204 y 204; dos elementos |
| `logoutAll_shouldReturn400ProblemJson_whenIdentityMissing` | sin `X-User-Id` | 400; `Content-Type` contiene `application/problem+json` y `charset=UTF-8`; cuerpo contiene `IDENTITY_REQUIRED`; nada revocado |
| `logoutAll_shouldReturn503_whenFirebaseUnavailable` | `directory.becomeUnavailableOnRevoke()` | 503; `DEPENDENCY_UNAVAILABLE`; encabezado `X-Request-Id` presente |
| `logoutAll_shouldReturn204ForBoth_whenTwoConcurrentCalls` | dos hilos con `CountDownLatch(1)` que disparan a la vez (`ExecutorService` de 2) | ambos 204; dos elementos |

**Comando:** `./mvnw.cmd test "-Dtest=SessionRevocationEndToEndTest"`.
**Commit:** `CM-291 | test(cuentas): prueba de extremo a extremo del cierre de sesión`.

**Resultado (15-oct-2026):** SessionRevocationEndToEndTest 5 pruebas, 0 fallos, `BUILD SUCCESS` (PostgreSQL 16 real en Testcontainers).

---

## C-5 · Colección de Postman

**Requisitos:** R11; DES-02; CA-1.8.1, CA-1.8.2.

**Archivos:** `postman/cameia-cuentas.postman_collection.json`, `postman/local.postman_environment.json`, `postman/README.md`.

1. **Entorno:** agregar `firebaseEmulatorUrl` = `http://localhost:9099` y `firebaseProjectId` = `demo-cameia` (tipo `default`, con
   descripción en español).
2. **Script global de la colección** (evento `test`):
   - extender la exclusión de `delBroker` para que también excluya las peticiones cuya URL empiece por `firebaseEmulatorUrl`;
   - **trampa verificada:** un 204 no lleva `Content-Type`; envolver la comprobación `Content-Type con charset utf-8` y la rama de
     éxito en `if (pm.response.code !== 204) { ... }`.
3. **Carpeta nueva al final**, «Cerrar sesión en todos los dispositivos». Todas las peticiones a Cuentas comprueban
   `pm.response.responseTime < 2000`. Las de error comprueban `code` y `detail` literales.

| # | Petición | Esperado |
|---|---|---|
| L-00 | Registro nuevo (`POST {{baseUrl}}/api/v1/users`, correo `prueba+{{$timestamp}}-logout@ejemplo.test`, mismo cuerpo válido que la carpeta `Registro`) | 201; guarda `logoutUid` = `firebaseUid` |
| L-00b | Pre-request: guarda `revocadoDesde` = `Math.floor(Date.now()/1000)` y espera 1100 ms con `setTimeout` | — |
| L-01 | `POST {{baseUrl}}/api/v1/auth/logout-all`, `X-User-Id: {{logoutUid}}` | 204; cuerpo vacío |
| L-01b | `POST {{firebaseEmulatorUrl}}/identitytoolkit.googleapis.com/v1/projects/{{firebaseProjectId}}/accounts:lookup`, `Authorization: Bearer owner`, cuerpo `{"localId":["{{logoutUid}}"]}` | 200; `Number(users[0].validSince) >= Number(revocadoDesde)` |
| L-02 | Repetir L-01 | 204 |
| L-03 | `X-User-Id: uid-que-no-existe-{{$timestamp}}` | 204 |
| L-04 | Sin `X-User-Id` | 400 `IDENTITY_REQUIRED`, «La petición no incluye los datos que exige esta ruta.» |
| L-05 | `X-User-Id` de 129 `a` | 400 `IDENTITY_REQUIRED` |
| L-06 | `X-User-Id: {{logoutUid}}`, cuerpo JSON `{"firebaseUid":"uid-ajeno"}` | 204 |
| L-07 | `?uid=uid-ajeno&firebaseUid=uid-ajeno` con `X-User-Id: {{logoutUid}}` | 204 |
| L-08 | `X-User-Id: ' OR 1=1 --` | 204; cuerpo vacío |
| L-09 | `GET` sobre la ruta | 405 `METHOD_NOT_ALLOWED`; `Allow` contiene `POST` |
| L-10 | `GET {{baseUrl}}/v3/api-docs` (requiere `API_DOCUMENTATION_ENABLED=true`) | 200; `paths['/api/v1/auth/logout-all'].post.responses` tiene `204`, `400`, `405`, `500` y `503` |

Si L-01b no devuelve `validSince` en el emulador: quitar la aserción, dejar la petición con 200, y **reportarlo** (la revocación queda
demostrada por el Gateway en su colección, G-06).

4. **README:** fila de la carpeta en la tabla de archivos, tabla de las peticiones como las demás carpetas y el comando
   `--folder "Cerrar sesión en todos los dispositivos" --reporter-junit-export target/newman-logout.xml`.

**Comando** (aplicación empaquetada con emulador y base locales, como indica `postman/README.md` «Cómo correrla»):
`npx newman run postman/cameia-cuentas.postman_collection.json -e postman/local.postman_environment.json --folder "Cerrar sesión en todos los dispositivos" --reporters cli,junit --reporter-junit-export target/newman-logout.xml`
y la colección completa para comprobar que el cambio del script no rompió otra carpeta (salvo la manual de Firebase caído).
**Commit:** `CM-291 | test(cuentas): colección de Postman del cierre de sesión`.

**Resultado (15-oct-2026):** carpeta de 12 peticiones. Newman contra la imagen Docker de la aplicación, PostgreSQL 16, RabbitMQ y el emulador de Firebase: primera corrida 52 de 53 comprobaciones (falló «menos de 2 s» en L-00, 2189 ms, arranque en frío); segunda corrida sin fallos. L-01b recibe `validSince` del emulador, así que la aserción se conserva. Desviaciones menores: el correo de L-00 usa `Date.now()` y la iteración (como `Mi cuenta`) en vez de `{{$timestamp}}`, y la espera de 1,1 s de L-00b es el pre-request de L-01.

---

## C-6 · Documentación

**Archivos y cambios exactos:**

- `docs/errores.md`:
  - Párrafo «Rutas»: agregar «**cierre de sesión** es `POST /api/v1/auth/logout-all`».
  - Fila `IDENTITY_REQUIRED`: endpoints «activación, `GET /api/v1/users/me`, cierre de sesión»; pruebas: agregar
    `SessionRevocationControllerTest`, `RevokeAllSessionsServiceTest`.
  - Fila `DEPENDENCY_UNAVAILABLE`: endpoints «registro, cierre de sesión»; origen: agregar «o revocar las sesiones»; pruebas: agregar
    `SessionRevocationControllerTest`, `SessionRevocationEndToEndTest`.
  - Fila «500 `INTERNAL_ERROR` … por permisos o configuración» de «Respuestas publicadas que difieren del estándar»: «en el registro y en
    el cierre de sesión».
- `CLAUDE.md` del repo, sección 1: «Se integra con Firebase Admin SDK para consultar usuarios, actualizar sus datos y permisos,
  revocar sus sesiones y bloquear cuentas cuando corresponda.»
- `docs/bitacora-ia/11_cierre-de-sesion-en-todos-los-dispositivos_prompt.md`: el prompt armado (rol, contexto, tarea, condiciones,
  formato) de la decisión con peso humano de esta tarea (P-1 a P-3 de la spec, con lo que Paula respondió), con enlace a la spec; y su
  fila en `docs/bitacora-ia/README.md` con el formato de las existentes.

**Comando:** `./mvnw.cmd test "-Dtest=ErrorCodeDocumentationTest"`.
**Commit:** `CM-291 | docs(cuentas): catálogo de errores, CLAUDE.md y bitácora del cierre de sesión`.

---

## C-7 · Verificación final (una vez, sobre la punta; sin commit)

1. `./mvnw.cmd clean verify` → `BUILD SUCCESS`, 0 fallos. Guardar en `ejecucion/evidencia/CM-291/verify-cuentas.log`.
2. Cobertura en `target/site/jacoco/jacoco.csv` de `RevokeAllSessionsService`, `SessionRevocationController` y
   `FirebaseUserDirectoryAdapter` (líneas del método nuevo): ≥ 90 % de líneas y de ramas; cada línea o rama sin cubrir con su razón.
3. Newman completo (C-5) con la salida pegada.
4. `git grep -n "CM-291" -- src` vacío; `git diff origin/develop --stat -- pom.xml src/main/resources/db` vacío.
5. Tabla R1–R14 y tabla de atributos con resultados reales (sección 11 y 15 de la spec).
