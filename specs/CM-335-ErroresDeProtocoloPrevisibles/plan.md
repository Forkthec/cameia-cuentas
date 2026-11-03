# Plan · CM-335 Errores de protocolo previsibles en Cuentas

Spec: `spec.md` (misma carpeta). Rama `CM-335-errores-de-protocolo-previsibles` desde `origin/develop`. Un PR; sin dependencias.

## 1. Enfoque

1. **Configuración:** apagar `FormContentFilter` (elimina la excepción que ocurre antes del manejador).
2. **Manejador:** `InvalidParameterException` en un formulario → 415. La detección por nombre de clase vive en una clase pequeña, `BadParameterEncoding`, dentro del manejador existente `BusinessExceptionHandler`.
3. **Contenedor:** `ContainerErrorController` convierte lo que Tomcat reporta con `sendError` (caso `TRACE`) en la excepción del framework correspondiente, y la resuelve el manejador existente.
4. **Identidad:** objeto de valor `FirebaseUid` que reúne la comprobación hoy repetida en tres servicios y le suma los caracteres de control.
5. **Pruebas con petición real, Postman y documentos.**

## 2. Reutilización

| Se reutiliza | Por qué |
|---|---|
| `BusinessExceptionHandler` (`falloInterno`, `rechazo`, `resolverRequestId`) | Ya arma la forma común, el `requestId`, el `charset` y el registro |
| Handlers de `HttpRequestMethodNotSupportedException`, `NoResourceFoundException`, `HttpMediaTypeNotSupportedException`, `HttpMediaTypeNotAcceptableException` | El controlador de errores del contenedor solo lanza esas excepciones; no repite respuestas |
| `IdentityRequiredException` | Ya produce el 400 `IDENTITY_REQUIRED` |
| Patrón de pruebas de `FrameworkErrorsTest` (`SpringBootTest` + Testcontainers + `TestRestTemplate`) | Mismo contexto completo |
| `TestRestTemplate` | **No** sirve para las pruebas nuevas: usa `java.net.URI`, que rechaza `%zz`, y no envía bytes crudos. Por eso se crea `RawHttpClient` (sockets), una clase de prueba de unas 60 líneas |

No se crea excepción de negocio ni código nuevo (spec §10, §15 D-3).

## 3. Archivos

Bajo `src/main/java/tech/cameia/cuentas/` salvo indicación.

| Acción | Ruta |
|---|---|
| modificar | `src/main/resources/application.properties` (`spring.mvc.formcontent.filter.enabled=false`) |
| crear | `presentation/advice/BadParameterEncoding.java` |
| modificar | `presentation/advice/BusinessExceptionHandler.java` (`falloInterno`; manejador nuevo de `MultipartException`; `responseTypeNotAcceptable` devuelve `ResponseEntity` con `problem+json`) |
| modificar | `domain/exception/ErrorCode.java` (+`REQUEST_MULTIPART_INVALID_FORMAT`) |
| crear | `presentation/advice/ContainerErrorController.java` |
| crear | `domain/model/FirebaseUid.java` |
| modificar (prueba) | `src/test/java/tech/cameia/cuentas/architecture/UntypedExceptionClassificationTest.java` (clasificar `ContainerErrorController#error`) y `BusinessExceptionHandlerTest` (firma nueva de `falloInterno`) |
| modificar | `application/service/GetCurrentAccountService.java`, `ActivateAccountService.java`, `RevokeAllSessionsService.java` (usan `FirebaseUid.required`; se elimina la constante duplicada) |
| pruebas | ver `tasks.md` |
| modificar | `docs/errores.md`, `postman/cameia-cuentas.postman_collection.json`, `postman/README.md`, `docs/bitacora-ia/README.md` |
| crear | `docs/adr/0004-errores-de-protocolo-previsibles.md`, entrada de la bitácora de IA |

No se tocan: migraciones, eventos, `.github/`, DTO, ni la lógica de los controladores. Sí se toca la **documentación OpenAPI** de los cuatro controladores (`UserRegistrationController`, `AccountActivationController`, `SessionRevocationController`, `CurrentAccountController`): una `@ApiResponse` 415 en cada uno (CM-306).

## 4. Commits (uno por capacidad, en orden de lectura)

| Tarjeta | Commit |
|---|---|
| T1 | `CM-335 \| fix(cuentas): apagar el filtro de formularios de Spring y cubrirlo con pruebas de punta a punta` |
| T2 | `CM-335 \| fix(cuentas): responder 415 a un formulario mal codificado en el registro` |
| T3 | `CM-335 \| fix(cuentas): dar la forma común a los errores del contenedor, como TRACE` |
| T4 | `CM-335 \| fix(cuentas): rechazar caracteres de control en la identidad` |
| T5 | `CM-335 \| test(cuentas): colección de Postman de los errores de protocolo` |
| T6 | `CM-335 \| docs(cuentas): catálogo de errores, ADR 0004 y bitácora` |

La prueba que reproduce el defecto se escribe antes del arreglo dentro de cada tarjeta y entra en el mismo commit.

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| Dos `ErrorController` | Spring Boot solo crea el suyo si no existe otro; T3 comprueba el arranque y `GET /error` |
| `Allow` se pierde en el despacho de error de Tomcat o no se puede reconstruir | T3 lo mide antes y después. Si se pierde, se reporta a Paula (un 405 sin `Allow` incumple la RFC 9110) en vez de improvisar |
| `LayeredArchitectureTest` | `FirebaseUid` va en `domain/model` sin dependencias de Spring; `ContainerErrorController` en `presentation`; se corre en cada tarjeta |
| `GetCurrentAccountServiceTest` y vecinas fallan por la constante eliminada | Se busca con `grep -rn MAX_FIREBASE_UID_LENGTH src` antes de borrarla (hoy solo la usan los tres servicios) |
| Newman reescribe `%zz` o no envía `TRACE` | Spec §13 |
| Pruebas con Testcontainers sin Docker | `disabledWithoutDocker = true` las omite en silencio: Docker encendido y un `*Test` omitido no cuenta como verde |
