# CM-335 · Errores de protocolo previsibles en Cuentas

| Dato | Valor |
|---|---|
| Tarea Jira | CM-335 (tarea, Sprint 3), responsable Paula Andrea Muñoz Delgado |
| Repositorio | `cameia-cuentas`, base `origin/develop` = `13055eb` (se vuelve a anotar el SHA al preparar la rama) |
| Depende de | Nada sin fusionar |
| Estado | **APROBADA por Paula Andrea Muñoz Delgado el 19 de octubre de 2026.** Incorpora las decisiones del 19-oct (multipart grande → 413, `instance` con la ruta original, un solo registro por petición, documentar las rutas que no leen el cuerpo) |
| Estimación | 4 a 5 h de Backend (configuración, manejador, controlador de errores del contenedor, identidad, pruebas con petición real, Postman y documentos). Se informa a Vela |
| Servicios hermanos | `cameia-perfil` y `cameia-gateway` tienen su propia spec de la misma tarea; cada una se entiende sola |

## 1. Objetivo

Que ninguna entrada malformada que un cliente pueda enviar a Cuentas termine en el 500 genérico, en la forma por defecto de Spring (`timestamp`, `status`, `error`, `path`, sin `code` ni `requestId`) o en un HTML de Tomcat cuando la aplicación puede evitarlo:

1. Un formulario mal codificado (`a=%zz`) en un `PUT`, `PATCH` o `DELETE` (cualquier ruta, incluso inexistente) o en `POST /api/v1/users`.
2. Un `X-User-Id` con tabulación o con otro carácter de control.
3. Un `TRACE`.

Lo que Tomcat rechaza antes de que la aplicación vea la petición (400 HTML) no se puede cambiar desde el código: se documenta (§12).

## 2. Evidencia (peticiones reales del 17-oct-2026)

Registros en `ejecucion/evidencia/CM-335/` de la carpeta raíz de trabajo: `60` (filtro encendido), `64` (filtro apagado), `67` (métodos), `69` (filtros activos), `10-cuentas-app.log` (trazas). Cuentas empaquetada desde `develop` `13055eb`, PostgreSQL 16 desechable y emulador de Firebase Auth.

Rutas de Cuentas: `POST /api/v1/users`, `GET /api/v1/users/me`, `GET /api/v1/users/health`, `POST /api/v1/users/me/verification` y `POST /api/v1/auth/logout-all`. Ninguna declara `@RequestParam`: Cuentas no lee parámetros de consulta.

| # | Petición | Resultado hoy | Causa |
|---|---|---|---|
| E-1 | `PUT`, `PATCH` o `DELETE` con `Content-Type: application/x-www-form-urlencoded` y cuerpo `a=%zz`, en `/api/v1/users`, `/users/me`, `/users/me/verification`, `/auth/logout-all` y en una ruta inexistente | 500 con la forma por defecto de Spring | `FormContentFilter` lee el cuerpo y lanza `HttpMessageNotReadableException` («Could not decode HTTP form payload», causa `URLDecoder`) en el filtro, antes del manejador |
| E-2 | Lo mismo con el filtro apagado (`SPRING_MVC_FORMCONTENT_FILTER_ENABLED=false`) | 405 `METHOD_NOT_ALLOWED` o 404 `ROUTE_NOT_FOUND` con la forma común | El filtro era la única causa de E-1 |
| E-3 | `POST /api/v1/users` con formulario `a=%zz` | 500 `INTERNAL_ERROR`, **con el filtro encendido y con el apagado** | Al leer el `@RequestBody`, Tomcat lanza `InvalidParameterException` (hija de `IllegalStateException`); el manejador general la trata como imprevista (`10-cuentas-app.log`, línea 539) |
| E-4 | `POST /api/v1/users` con formulario `a=1` | 415 `MEDIA_TYPE_NOT_ALLOWED` | Sin problema |
| E-5 | `POST /api/v1/auth/logout-all` y `POST /users/me/verification` con formulario `a=%zz` | 204 y 404 `ACCOUNT_NOT_FOUND` | Sin problema: no leen cuerpo |
| E-6 | `X-User-Id` con TAB o `0x85` | Se acepta como identidad (404 `ACCOUNT_NOT_FOUND`) | Las tres comprobaciones de identidad solo validan nulo, blanco y largo |
| E-7 | `X-User-Id` ausente, vacío o de 129 caracteres | 400 `IDENTITY_REQUIRED` | Contrato vigente de Cuentas (Perfil responde 401; la diferencia es una decisión del equipo ya registrada, no de esta tarea) |
| E-8 | `X-User-Id` con `0x07` o `0x7f` | 400 HTML de Tomcat | Tomcat lo rechaza antes de la aplicación (§12) |
| E-9 | `TRACE` | 405 con la forma por defecto de Spring | Tomcat responde 405 con `sendError` y Spring Boot lo muestra con su `BasicErrorController` |
| E-10 | `?x=%zz` en `GET /users/me`, `GET /users/health` y `POST /users` (JSON) | Respuesta normal de la ruta | Nadie lee el parámetro |

Otras rutas de filtro: Cuentas no tiene filtros ni interceptores propios. Los filtros activos son `characterEncodingFilter`, `formContentFilter` y `requestContextFilter` (`69`); solo `formContentFilter` lee el cuerpo.

## 3. Requisitos (EARS)

- **REQ-01** El sistema debe tener `spring.mvc.formcontent.filter.enabled=false` en `application.properties`. Cuentas no recibe formularios: ninguna ruta los consume.
- **REQ-02** Cuando una `InvalidParameterException` de Tomcat llegue al manejador general con un método `POST`, `PUT`, `PATCH` o `DELETE` y un `Content-Type` que empiece por `application/x-www-form-urlencoded` (sin distinguir mayúsculas), el sistema debe responder 415 con `code` `MEDIA_TYPE_NOT_ALLOWED`, `title` «Tipo de contenido no admitido» y `detail` «Tipo de contenido no admitido.», igual que un formulario bien formado.
- **REQ-03** El sistema debe reconocer la excepción por el nombre de su clase (`org.apache.tomcat.util.http.InvalidParameterException`) en ella o en su cadena de causas (máximo 32 pasos, como las demás recorridas del manejador), sin importar clases de Tomcat en `presentation`. Toda otra `IllegalStateException`, y la misma excepción fuera del caso de REQ-02, siguen siendo un fallo imprevisto (500 `INTERNAL_ERROR`): hoy ningún camino de Cuentas lee un parámetro de consulta, así que ese segundo caso no es alcanzable (§15, D-3).
- **REQ-04** El sistema debe registrar el rechazo de REQ-02 en `WARN` con `status`, `code` y `requestId`, nunca con el valor del parámetro, el cuerpo ni el mensaje de la excepción de Tomcat (trae el nombre y el valor).
- **REQ-05** Una identidad (`X-User-Id`) nula, en blanco, de más de 128 caracteres o con algún carácter de control ISO (`Character.isISOControl`: U+0000–U+001F y U+007F–U+009F) debe responder 400 `IDENTITY_REQUIRED` con el mensaje que ya usa Cuentas («La petición no incluye los datos que exige esta ruta.»), igual que el encabezado ausente. La regla vive en un solo lugar del dominio y la usan los tres casos de uso que hoy la repiten.
- **REQ-06** El sistema debe responder todo error que Tomcat reporte con `sendError` y llegue a la ruta `/error` con la forma común (el `Allow` del contenedor se conserva e `instance` lleva la ruta original de la petición, no `/error`; si la ruta original no es una referencia válida, por ejemplo con una codificación inválida, rige `/error`; decisión de Paula, 19-oct, P-2, que reemplaza la aceptación del 17-oct): `application/problem+json;charset=UTF-8`, `code`, `requestId` y el encabezado `X-Request-Id`. Mapeo por estado original: 404 → `ROUTE_NOT_FOUND`, 405 → `METHOD_NOT_ALLOWED`, 406 → `MEDIA_TYPE_NOT_ACCEPTABLE`, 415 → `MEDIA_TYPE_NOT_ALLOWED`, 400 → 422 `REQUEST_BODY_INVALID_FORMAT` (un 400 es un dato del cliente; decisión de Paula, 17-oct, Q-4: se iguala a Perfil en el estado 422 y se usa el código del contrato de Cuentas que ya existe, porque `REQUEST_INVALID_VALUE` es aquí un respaldo que no debe emitirse), 413 → 413 `REQUEST_BODY_TOO_LARGE` (REQ-12), cualquier otro → `INTERNAL_ERROR` con el estado original en el registro `ERROR`. Un `TRACE` responde 405 `METHOD_NOT_ALLOWED`. El controlador no arma la respuesta: lanza la excepción del framework que corresponde al estado y la resuelve el manejador del servicio, de modo que el `requestId`, el `charset` y el registro salen del mismo lugar que el resto de errores.
- **REQ-07** La ruta `/error` no debe aparecer en OpenAPI; una petición directa `GET /error` (sin error original) responde 404 `ROUTE_NOT_FOUND` con la forma común y se registra en `WARN` (decisión de Paula, 17-oct, Q-2).
- **REQ-08** El sistema debe documentar en `docs/errores.md` los casos nuevos, la cobertura de `/error` y los rechazos de Tomcat anteriores a la aplicación (§12), y registrar la decisión en un ADR.
- **REQ-09** Con el filtro apagado, el sistema no debe cambiar ninguna respuesta existente: `POST /users` con un formulario bien formado sigue en 415 y con JSON ilegible sigue en 422 `REQUEST_BODY_INVALID_FORMAT`.
- **REQ-10** Cuando llegue una `MultipartException` al manejador y el `Content-Type` empiece por `multipart/` (sin distinguir mayúsculas), el sistema debe responder 415 con el código nuevo `REQUEST_MULTIPART_INVALID_FORMAT` («Tipo de contenido no admitido.») y registrar `WARN` con `status`, `code` y `requestId`, sin el mensaje de la excepción. Con otro `Content-Type`, sigue siendo un fallo imprevisto (500). Decisión de Paula, 17-oct (Q-1, opción 2): cierra aquí lo que D-25 dejaba para CM-306. Aplica a todas las rutas (el contenedor parte el multipart antes de elegir ruta) y cumple la tarea CM-306 (que Paula ya enlazó con CM-335 en el Sprint 3): una prueba por ruta, el 415 documentado en OpenAPI y aviso a Frontend (tarjeta T2). Medido: hoy `POST /api/v1/users` con `Content-Type: multipart/form-data` sin `boundary` responde 500.
- **REQ-12** Cuando una parte de un `multipart` pase de 1 MiB o la petición pase de 10 MiB (`spring.servlet.multipart.max-file-size=1MB` y `max-request-size=10MB`, declarados en `application.properties` y no heredados del valor por defecto de Spring), el sistema debe responder 413 `REQUEST_BODY_TOO_LARGE` («Cuerpo de la petición demasiado grande.») en `application/problem+json;charset=UTF-8`, con `requestId`, y registrar `WARN` con `status`, `code` y `requestId`, nunca `ERROR`. `MaxUploadSizeExceededException` se atrapa antes que `MultipartException`, que la absorbía como un 415 engañoso; un fallo de lectura que no es de tamaño sigue siendo 415 `REQUEST_MULTIPART_INVALID_FORMAT`. El detalle no cita el límite ni lo recibido, y no quedan archivos temporales en disco. Un 413 que reporta el contenedor sale igual. Decisión de Paula, 19-oct (CM-335-C1, opción B). Medido: 0,5 MB conserva su respuesta de siempre; 1,5 MB y 11 MB dan 413.
- **REQ-13** Una petición registra su error una sola vez. Cuando el contenedor la despacha a `/error` después de que la aplicación ya la rechazó (por ejemplo un cuerpo troceado con un trozo mal formado), el despacho responde con el `requestId` que ya quedó en el registro y no escribe otra línea. Decisión de Paula, 19-oct (P-1/P-2). Medido: antes había dos avisos con identificadores distintos.
- **REQ-11** Cuando la petición traiga un `Accept` que no se pueda interpretar (por ejemplo `;;;`), el sistema debe responder 406 `MEDIA_TYPE_NOT_ACCEPTABLE` con la forma común y `Content-Type: application/problem+json;charset=UTF-8`, y no con el cuerpo vacío de hoy (medido: `GET /api/v1/users/me` y `GET /api/v1/users/health` responden 406 sin cuerpo). Decisión de Paula, 17-oct (Q-3, opción 1).

## 4. Contrato

Cambio aditivo para Frontend: casos que pasan de 500 a códigos que ya existían y dos códigos nuevos: `REQUEST_MULTIPART_INVALID_FORMAT` (415), que pide la tarea CM-306, y `REQUEST_BODY_TOO_LARGE` (413).

| Petición | Antes | Después |
|---|---|---|
| `PUT`/`PATCH`/`DELETE` con formulario `a=%zz` | 500 sin `code` | La respuesta normal de la ruta: 405 `METHOD_NOT_ALLOWED` o 404 `ROUTE_NOT_FOUND` |
| `POST /api/v1/users` con formulario `a=%zz` | 500 `INTERNAL_ERROR` | 415 `MEDIA_TYPE_NOT_ALLOWED` |
| `X-User-Id` con TAB o control | Acepta la identidad | 400 `IDENTITY_REQUIRED` |
| `TRACE` | 405 sin `code` | 405 `METHOD_NOT_ALLOWED` |
| `Content-Type: multipart/form-data` sin `boundary` (cualquier ruta) | 500 `INTERNAL_ERROR` | 415 `REQUEST_MULTIPART_INVALID_FORMAT` |
| `multipart` con una parte de más de 1 MiB o una petición de más de 10 MiB | 415 `REQUEST_MULTIPART_INVALID_FORMAT` (engañoso) | 413 `REQUEST_BODY_TOO_LARGE` |
| Cuerpo troceado con un trozo mal formado | 422 con `instance` = `/error` y dos avisos | 422 `REQUEST_BODY_INVALID_FORMAT` con `instance` = la ruta pedida y un solo aviso |

Frontend consume Cuentas a través del Gateway, que borra los `X-User-*` del cliente y usa el `uid` del token: ninguno de estos casos lo produce la interfaz. Aviso a Frontend al abrir el PR: dos códigos nuevos (`REQUEST_MULTIPART_INVALID_FORMAT`, 415, y `REQUEST_BODY_TOO_LARGE`, 413) y la ruta original en `instance`; ninguna acción.

## 5. Validación de entrada y casos de prueba con datos literales

| Entrada | Caso | Valor literal | Resultado | Prueba |
|---|---|---|---|---|
| Formulario | `PUT`, `PATCH`, `DELETE` en `/api/v1/users` | `a=%zz` | 405 `METHOD_NOT_ALLOWED` | `ProtocolErrorsEndToEndTest.formMethod_shouldReturn405_whenFormIsMalformed` (3 métodos) |
| | `PUT`, `PATCH`, `DELETE` en ruta inexistente | `a=%zz` | 404 `ROUTE_NOT_FOUND` | `…formMethod_shouldReturn404_whenRouteDoesNotExist` (3 métodos) |
| | `POST /api/v1/users` | `a=%zz` | 415 `MEDIA_TYPE_NOT_ALLOWED`, `requestId` | `…register_shouldReturn415_whenFormIsMalformed` |
| | `Content-Type` con `charset` y mayúsculas | `Application/X-WWW-Form-Urlencoded; charset=UTF-8` | 415 | `BusinessExceptionHandlerTest.invalidParameter_shouldReturn415_whenFormContentTypeHasCharsetAndUppercase` |
| | Formulario bien formado | `a=1` en `POST /users` | 415 (sin cambio) | `…register_shouldStillReturn415_whenFormIsWellFormed` |
| | JSON ilegible | `{"a":` en `POST /users` | 422 `REQUEST_BODY_INVALID_FORMAT` (sin cambio) | `…register_shouldStillReturn422_whenJsonIsMalformed` |
| | Sin cuerpo | `Content-Type` de formulario y cuerpo vacío | sin excepción; 415 | `…register_shouldReturn415_whenFormBodyIsEmpty` |
| | Rutas que no leen cuerpo | `POST /auth/logout-all`, `POST /users/me/verification` con `a=%zz` | 204 y 404 `ACCOUNT_NOT_FOUND` (sin cambio) | `…noBodyRoutes_shouldKeepTheirAnswer_whenFormIsMalformed` |
| Identidad | control | `"uid\t1"`, `"\u0085"`, `"\u009f"`, `"a\u0000"`, `"\n"`, `"\u007f"` | 400 `IDENTITY_REQUIRED` | `FirebaseUidTest.required_shouldThrow_whenControlCharacter` |
| | válidas | `"uid-123_ABC"`, `"uid-ñ"`, 128 caracteres | se aceptan | `FirebaseUidTest.required_shouldAccept_whenPrintable` |
| | nula, blanca, 129 | `null`, `""`, `"   "`, `"a".repeat(129)` | 400 (sin cambio) | `FirebaseUidTest.required_shouldThrow_whenNullBlankOrTooLong` |
| | con TAB por HTTP | encabezado `X-User-Id: uid<TAB>x` en `GET /users/me`, `POST /users/me/verification` y `POST /auth/logout-all` | 400 `IDENTITY_REQUIRED` en las tres | `…identity_shouldReturn400_whenHeaderHasTab` (3 rutas) |
| Método | `TRACE` | `/api/v1/users/me` | 405 `METHOD_NOT_ALLOWED` con `requestId` | `…trace_shouldReturn405WithCommonShape` |
| `/error` | sin error original | `GET /error` | 404 `ROUTE_NOT_FOUND`, registro `WARN` | `ContainerErrorControllerTest.error_shouldReturn404_whenNoOriginalStatus` |
| | estados | 404, 405, 406, 415, 400, 413, 503 | ver REQ-06 (400 → 422 `REQUEST_BODY_INVALID_FORMAT`; 413 → 413 `REQUEST_BODY_TOO_LARGE`; 503 → `INTERNAL_ERROR`) | `ContainerErrorControllerTest.error_shouldMapStatus` (7 casos) |
| | `instance` y registro único | despacho de error con ruta original; ruta que no es una referencia; error ya registrado | ruta original / `/error` / sin segunda línea con el mismo `requestId` | `ContainerErrorControllerTest.error_shouldPointInstanceToOriginalPath_whenContainerReportedIt`, `…KeepDispatchPath…`, `…ReuseRequestIdAndNotLogAgain…`, `…NotLogErrorAgain…`; `ProtocolErrorsEndToEndTest.register_shouldAnswerOnceWithOriginalPath_whenChunkedBodyIsMalformed` |
| Multipart grande | 0,5 MB; 1,5 MB; 11 MB; 1 MiB y 1 MiB + 1 byte; 11 partes de menos de 1 MiB | `POST /api/v1/users` con `multipart` | 415 `MEDIA_TYPE_NOT_ALLOWED`; 413; 413; 415 y 413; 413 (sin archivos temporales) | `ProtocolErrorsEndToEndTest.register_shouldKeepMediaTypeAnswer_whenMultipartIsHalfMegabyte`, `…shouldReturn413_whenMultipartPartIsTooLarge`, `…shouldAcceptExactlyOneMebibyte_andRejectOneByteMore`, `…shouldReturn413_whenMultipartRequestIsTooLarge`; `BusinessExceptionHandlerTest.multipart_shouldReturn413_whenSizeLimitIsExceeded`, `…shouldPickSizeHandler…` |
| Multipart | sin `boundary` | `POST /api/v1/users` con `Content-Type: multipart/form-data`, cuerpo `x` | 415 `REQUEST_MULTIPART_INVALID_FORMAT` | `ProtocolErrorsEndToEndTest.register_shouldReturn415_whenMultipartHasNoBoundary` |
| | con tipo multipart / otro tipo | `new MultipartException("x")` | 415 / 500 | `BusinessExceptionHandlerTest.multipart_shouldReturn415_whenMultipartContentType`, `…shouldReturn500_whenNotMultipartContentType` |
| `Accept` | ilegible | `Accept: ;;;` en `GET /api/v1/users/health` | 406 `MEDIA_TYPE_NOT_ACCEPTABLE` con cuerpo `problem+json` | `ProtocolErrorsEndToEndTest.accept_shouldReturn406WithBody_whenAcceptIsUnreadable` |
| Manejador | otra `IllegalStateException` | `new IllegalStateException("x")` | 500 `INTERNAL_ERROR` | `BusinessExceptionHandlerTest.illegalState_shouldReturn500_whenNotInvalidParameter` |
| | excepción envuelta | `RuntimeException` con causa `InvalidParameterException` y formulario | 415 | `BadParameterEncodingTest.matches_shouldFindCause_whenWrapped` |
| | no repite el valor | formulario `a=%zzSECRETO` | cuerpo y registro sin `SECRETO` | `…register_shouldNotEchoOrLogValue` |

## 6. Base de datos

Sin migración ni cambio de esquema. La columna `cuenta.firebase_uid` es `VARCHAR(128)`, el mismo largo que valida el código.

## 7. Casos borde (estándar §C)

| Grupo | Caso | Tratamiento |
|---|---|---|
| Nulos y vacíos | Formulario con cuerpo vacío | Sin parámetros que decodificar: no hay excepción; 415 normal |
| Unicode | Identidad con `ñ` o con letras acentuadas | Se acepta; solo se rechazan caracteres de control. Prueba `required_shouldAccept_whenPrintable` |
| Idempotencia | Repetir la misma petición mala 20 veces | La misma respuesta y ninguna fila nueva. Prueba `…register_shouldReturnSameAnswer_whenRepeated20Times` |
| Concurrencia | 50 peticiones malas en paralelo | Todas 415; ninguna 5xx. Prueba `…register_shouldNotFail_whenManyMalformedRequestsRunInParallel` |
| Falla a medias | Formulario malo en el registro | El rechazo ocurre al leer el cuerpo, antes del caso de uso: no se crea cuenta ni credencial. Prueba: la tabla `cuenta` queda sin filas nuevas |
| Registro | Valor malo en el formulario | El registro no contiene el valor. Prueba `…register_shouldNotEchoOrLogValue` |
| No aplica | Fechas y zonas, dependencia caída, colecciones | Estos caminos no escriben ni llaman a Firebase |

## 8. Seguridad (ASVS nivel 1 y OWASP API Top 10)

| Control | Qué hace | Prueba |
|---|---|---|
| ASVS 4.1.1 / API8 | Todo error nuevo lleva `charset=UTF-8` y la forma común | Las pruebas de §5 y `FrameworkErrorsTest` existente |
| ASVS 2.2.1 y 2.2.2 / API10 | La entrada mal formada se rechaza en el servidor con un código y ningún valor recibido ni mensaje de librería se refleja (la matriz de ASVS 5.0.0 de `cameia-infra` no trae un requisito propio de «no reflejar el valor»; se cubre con OWASP API10) | `…register_shouldNotEchoOrLogValue` |
| ASVS 8.2.2 / API2 | Una identidad con caracteres de control no es identidad (evita falsear líneas de registro) | `FirebaseUidTest`, `…identity_shouldReturn400_whenHeaderHasTab` |
| API7 (configuración) | `/error` ya no muestra la forma por defecto de Spring con `path` | `ContainerErrorControllerTest` |
| API4 | La petición mala se rechaza antes de tocar la base o Firebase | Falla a medias de §7 |

## 9. Atributos de calidad de CAMEIA

La tarea no pertenece a una épica con atributos declarados; se aplican los transversales (anexo `Anexo_Restricciones_Atributos_Calidad`):

| Atributo | Métrica | Límite | Cómo | Prueba |
|---|---|---|---|---|
| AC-0002 (disponibilidad) | DES-04 | 5xx < 1 % | Toda entrada malformada de §2 responde 4xx | Todas las de §5; recorrido real con Newman y las peticiones de §13 |
| AC-0003 (seguridad) | SEG-04 | Sin datos personales en logs | REQ-04 | `…register_shouldNotEchoOrLogValue` |
| AC-0004 (fiabilidad) | FIA-01 | Ningún fallo deja cuentas a medias | El rechazo es anterior al caso de uso | Falla a medias de §7 |
| AC-0006 (interoperabilidad) | INT | Error con `code` y mensaje claro | REQ-02, REQ-05, REQ-06 | pruebas de §5 |
| Transversal | MAN-01 | ≥ 90 % de líneas y ramas en lo nuevo o modificado | JaCoCo | `./mvnw.cmd clean verify` |
| Transversal | IOP-01 | OpenAPI completo | `/error` no aparece; las rutas documentan el 415 (`MEDIA_TYPE_NOT_ALLOWED` en el formulario mal codificado y `REQUEST_MULTIPART_INVALID_FORMAT` en el `multipart` mal formado, CM-306) | `OpenApiDocumentationTest` o la prueba de OpenAPI vigente (T3 busca su nombre real) |

## 10. Errores

Dos códigos nuevos (`REQUEST_MULTIPART_INVALID_FORMAT` y `REQUEST_BODY_TOO_LARGE`) y casos nuevos de códigos existentes:

| Código | HTTP | Mensaje | Caso nuevo |
|---|---|---|---|
| `MEDIA_TYPE_NOT_ALLOWED` | 415 | «Tipo de contenido no admitido.» | formulario mal codificado en `POST /users` |
| `REQUEST_MULTIPART_INVALID_FORMAT` | 415 | «Tipo de contenido no admitido.» | **Código nuevo** (lo pide CM-306): `multipart/form-data` mal formado en cualquier ruta |
| `REQUEST_BODY_TOO_LARGE` | 413 | «Cuerpo de la petición demasiado grande.» | **Código nuevo** (decisión del 19-oct): `multipart` con una parte de más de 1 MiB o una petición de más de 10 MiB; la causa `TOO_LARGE` se agrega al vocabulario cerrado de causas |
| `METHOD_NOT_ALLOWED` | 405 | «Método no permitido.» | `TRACE`, y `PUT`/`PATCH`/`DELETE` con formulario malo |
| `IDENTITY_REQUIRED` | 400 | «La petición no incluye los datos que exige esta ruta.» | identidad con caracteres de control |
| `INTERNAL_ERROR` | 500 | «Ocurrió un error. Inténtalo de nuevo.» | solo lo imprevisto |

La regla «todo error previsible tiene excepción de negocio» se cumple con una salvedad documentada: el rechazo de un formulario mal codificado no es una regla del dominio sino del protocolo; se traduce en el manejador, igual que `HttpMessageNotReadableException`. `IdentityRequiredException` ya existe y se reutiliza.

## 11. Pruebas, cobertura y comandos

| Capa | Pruebas |
|---|---|
| Dominio | `FirebaseUidTest` (nueva) |
| Aplicación | `GetCurrentAccountServiceTest`, `ActivateAccountServiceTest`, `RevokeAllSessionsServiceTest` (existentes, deben seguir verdes con la regla compartida) |
| Presentación (unitarias) | `BadParameterEncodingTest` (nueva), `BusinessExceptionHandlerTest` (se amplía), `ContainerErrorControllerTest` (nueva) |
| Punta a punta | `ProtocolErrorsEndToEndTest` (nueva, `RANDOM_PORT`, Testcontainers, cliente de sockets `RawHttpClient`) |
| Contrato | prueba de OpenAPI vigente (una prueba nueva) y `ErrorCodeDocumentationTest` (existente) |

Clases a medir: `BadParameterEncoding`, `ContainerErrorController`, `BusinessExceptionHandler` (métodos nuevos), `FirebaseUid`, `GetCurrentAccountService`, `ActivateAccountService`, `RevokeAllSessionsService`. Comandos: Docker encendido; `./mvnw.cmd -B -ntp clean verify`; `target/site/jacoco/jacoco.csv`; Newman con la carpeta nueva (§13). En Cuentas las pruebas de punta a punta se llaman `*Test` y las corre Surefire con Testcontainers, no Failsafe.

## 12. Lo que la aplicación no puede cambiar (se documenta)

Tomcat rechaza con 400 y una página HTML, antes de la aplicación: `%zz`, `%00` o `%2F` en la **ruta**, una URL de unos 9000 bytes o más, encabezados que suman más de unos 8 KB o pasan de 100 en total (medido: 24 de 350 bytes, o 99 más `Host` y `Connection`) y un `X-User-Id` con bytes `0x00`–`0x08`, `0x0A`–`0x1F` o `0x7F`. `CONNECT` responde 501 HTML. Se escribe en `docs/errores.md`, en la sección de limitaciones conocidas, con la razón y con la salida del Gateway (que evita la mayoría porque borra los `X-User-*` del cliente y usa el `uid` del token).

## 13. Postman y Newman

Carpeta nueva «Errores de protocolo» en `postman/cameia-cuentas.postman_collection.json`:

| # | Petición | Espera |
|---|---|---|
| C-01 | `PATCH /api/v1/users` con encabezado `Content-Type: application/x-www-form-urlencoded` y cuerpo crudo `a=%zz` | 405 `METHOD_NOT_ALLOWED`, `requestId`, `charset=UTF-8` |
| C-02 | `POST /api/v1/users` con formulario crudo `a=%zz` | 415 `MEDIA_TYPE_NOT_ALLOWED` |
| C-03 | `TRACE /api/v1/users/me` (método personalizado) | 405 `METHOD_NOT_ALLOWED` |

Se corre con `postman/local.postman_environment.json` contra el jar empaquetado y la base de `docker compose`, filtrando con `--folder "Errores de protocolo"`. Cada petición comprueba estado, `code`, `requestId` y `Content-Type` con `charset=UTF-8`. **Trampa:** si Newman reescribe `%zz` como `%25zz` o no envía `TRACE`, se anota la salida real, se prueba la petición con `curl` y se informa a Paula; el recorrido real lo cubre `ProtocolErrorsEndToEndTest`. Actualizar la lista de carpetas de `postman/README.md`.

## 14. Línea base

Antes de editar: `git fetch`, rama `CM-335-errores-de-protocolo-previsibles` desde `origin/develop` y `./mvnw.cmd -B -ntp clean verify` en verde. Si falla, detenerse y reportar.

## 15. Decisiones

| # | Decisión | Por qué | Descartado | Quién |
|---|---|---|---|---|
| D-1 | Un formulario mal codificado responde 415 | Es lo que ya responde un formulario bien formado; el problema es el tipo de contenido | Un código de consulta o de cuerpo nuevo | Paula, 17-oct |
| D-2 | Apagar `FormContentFilter` | Cuentas no recibe formularios; la excepción ocurre dentro del filtro, antes del manejador | Un filtro propio que la capture (más código, mismo resultado) | Backend, aceptado por Paula |
| D-3 | Cuentas **no** agrega `REQUEST_QUERY_INVALID_FORMAT` | Ninguna ruta lee parámetros de consulta: el código no se podría producir (código muerto). Cuando se agregue el primer `@RequestParam`, se usa el código de Perfil con el mismo texto | Agregarlo ya, por uniformidad de vocabulario | Backend; Paula lo confirmó el 17-oct |
| D-4 | La identidad con caracteres de control responde 400 `IDENTITY_REQUIRED`, igual que hoy la identidad ausente | No cambia el contrato; el 400 de Cuentas frente al 401 de Perfil es una decisión de equipo anterior | Cambiar Cuentas a 401 | Paula, 17-oct |
| D-5 | `ContainerErrorController` reemplaza al `BasicErrorController` | Cubre `TRACE` y cualquier `sendError` futuro, y deja la forma común aun si alguien reactiva el filtro | Dejar `TRACE` como limitación documentada | Paula, 17-oct |
| D-6 | Objeto de valor `FirebaseUid` en `domain/model`, con `required`, usado por los tres casos de uso | Hoy la misma comprobación está escrita tres veces (tercera repetición: se extrae) y Perfil ya tiene la misma clase | Agregar la comprobación de controles en cada servicio | Backend |
| D-7 | Reconocer la excepción por el nombre de su clase | `presentation` no importa Tomcat (instrucción de Paula) | Capturar `IllegalStateException` (atraparía excepciones ajenas) | Backend, aceptado por Paula |

## 16. Fuera de alcance

El 400 HTML de Tomcat (§12); el límite de tamaño del cuerpo JSON (30 MB se aceptan y se procesan; anotado en `docs/errores.md` con su destino, la tarea del tope de tamaño del cuerpo) y las rutas que no leen el cuerpo (`POST /users/me/verification` y `POST /auth/logout-all` ignoran un formulario `%zz` o `text/plain`; se documenta, no se rechaza, decisión del 19-oct, P-3); el 401 frente al 400 de la identidad ausente; el 500 por violación de restricción de base de datos (decisión vigente de la spec de validaciones del registro).

## 17. Preguntas

- D-3 (código de consulta en Cuentas) quedó resuelta el 17-oct: no se agrega. Cuando se agregue el primer `@RequestParam`, se reabre.
- Respondidas en la revisión de la especificación (17-oct): **Q-1 = opción 2** (REQ-10, el `multipart` se arregla aquí; con un código nuevo, `REQUEST_MULTIPART_INVALID_FORMAT`; CM-306, ya en el Sprint 3 y enlazada con CM-335, queda cubierta), **Q-2 = opción 1** (REQ-07), **Q-3 = opción 1** (REQ-11), **Q-4 = opción 1** (REQ-06). **Q-5** (`instance` = `/error` en el `TRACE`): resuelta el 19-oct (P-2): `instance` lleva la ruta original (REQ-06).
- Respondidas el 19-oct: ADR 0004 aprobado; CM-335-C1 = opción B (REQ-12); P-1 = `REQUEST_BODY_INVALID_FORMAT` también para el cuerpo troceado mal formado; P-2 = ruta original en `instance` (REQ-06, REQ-13); P-3 = documentar las rutas que no leen el cuerpo (§16).
- Respondidas el 17-oct: D-1, D-4, D-5.

## 18. Lista de verificación

| # | Qué | Cómo | Esperado |
|---|---|---|---|
| V-01 | REQ-01 | `PUT`/`PATCH`/`DELETE` con `a=%zz` por HTTP real | 405 o 404 con `code`, no 500 |
| V-02 | REQ-02 | `POST /users` con `a=%zz` | 415 |
| V-03 | REQ-03 | `BadParameterEncodingTest`; `IllegalStateException` ajena | 500 sólo la ajena |
| V-04 | REQ-04 | Captura de salida | Sin valores |
| V-05 | REQ-05 | `FirebaseUidTest`, TAB por HTTP en las tres rutas | 400 `IDENTITY_REQUIRED` |
| V-06 | REQ-06 | `TRACE` y `ContainerErrorControllerTest` | 405 con `requestId` |
| V-07 | REQ-07 | Prueba de OpenAPI y `GET /error` | `/error` fuera; 500 común |
| V-08 | REQ-08 | `ErrorCodeDocumentationTest`, lectura de `docs/errores.md` | Verde; nota de §12 presente |
| V-09 | REQ-09 | Pruebas de «sin cambio» de §5 | Verde |
| V-10 | Cobertura | `jacoco.csv` | ≥ 90 % por clase tocada |
| V-11 | Newman | 3 peticiones | 0 fallos |
| V-12 | Adversarial | Formulario con `charset`; 50 en paralelo; `X-User-Id` con el byte `0x85` **enviado por socket crudo** (`curl` en Windows reescribe ese byte y no sirve); formulario vacío | 415, sin 5xx, 400, 415 |
| V-13 | Regresión | Suite completa y `LayeredArchitectureTest` | Verde |
| V-14 | REQ-10 (CM-306) | `multipart/form-data` sin `boundary` por HTTP real en las cinco rutas y en una ruta inexistente | 415 `REQUEST_MULTIPART_INVALID_FORMAT` (hoy 500 en todas, medido el 17-oct) |
| V-15 | REQ-10 (CM-306) | Prueba de OpenAPI | El 415 está documentado en los cuatro controladores |
| V-16 | REQ-11 | `Accept: ;;;` por HTTP real | 406 con cuerpo `problem+json` |
| V-17 | REQ-12 | `multipart` de 0,5 MB, 1,5 MB y 11 MB; 1 MiB y 1 MiB + 1 byte; 11 partes de menos de 1 MiB; archivos temporales | 415 de siempre / 413 / 413 / 415 y 413 / 413; ninguno queda en disco |
| V-18 | REQ-13 | Cuerpo troceado `zz
abc
0

` por socket crudo con `X-Request-Id` propio | 422 con `instance` = `/api/v1/users` y una sola línea de registro con ese `requestId` |
