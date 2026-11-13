# Spec — Aceptación de la política de tratamiento de datos en Cuentas

- **Tarea:** CM-367, subtarea de CM-365 («Políticas de la aplicación y autorización del tratamiento de datos al registrarse»), Sprint 5. Responsable: Paula Andrea Muñoz Delgado (Product Owner y Backend en la entrega).
- **Historia y criterio:** HU-1.1 (registro), CA-1.1.44 (autorización del tratamiento de datos sin marcar), backlog v11.3, hoja HE-01. El texto de los criterios 1 a 7 de CM-365 en Jira es el requisito; este documento no agrega criterios.
- **Base:** `origin/develop` de `cameia-cuentas` en `c94f20c` (12-nov-2026). Siguiente migración libre: `V8`.
- **Estado:** LISTA PARA EJECUTAR. Aprobada por Paula el 12-nov-2026. Contacto del responsable del tratamiento para los textos: `forktech.team@gmail.com` (dato de Paula, 12-nov).

## 1. Objetivo

La persona solo puede crear una cuenta si autoriza de forma expresa el tratamiento de sus datos (Ley 1581 de 2012). Cuentas comprueba esa autorización y la versión de la política aceptada, y las guarda con la fecha de aceptación en el mismo guardado que crea la cuenta. Además, informa la versión aceptada y la vigente, y permite aceptar una versión nueva. Los textos de la política viven en `cameia-web`. Cuentas solo guarda la versión.

## 2. Alcance

| Dentro | Fuera (con destino) |
|---|---|
| Criterios 3 a 7 de CM-365: registro con `dataAuthorization` y `policyVersion`, migración, `policyAcceptance` en `GET /api/v1/users/me`, `POST /api/v1/users/me/policy-acceptance`, versión vigente por configuración, registros sin datos personales, OpenAPI, `docs/errores.md`, Postman y pruebas | Criterio 1 (textos): parte de Product Owner de esta misma subtarea, §13. **PENDIENTE (Paula)**; no bloquea el backend |
| | Criterio 2 y el aviso de versión nueva en la web: CM-368 (Frontend, Ana Sofía Arango) |
| | Bloquear en el servidor el uso de la aplicación a quien no aceptó la versión vigente: el criterio 5 lo resuelve en la web. Si se quiere en el servidor, es una tarea nueva que decide Paula (Perfil y Entrevista tendrían que consultar a Cuentas) |
| | Cookies, consentimiento de marketing, canal de derechos del titular e inscripción ante la SIC (fuera de alcance de CM-365) |
| | El Gateway no cambia: la ruta `Path=/api/v1/users/**` ya envía la ruta nueva a Cuentas y la trata como autenticada (`cameia-gateway` `application.yml:33`, `FirebaseAuthGlobalFilter.java:183-193`) |
| | Firebase no cambia: la migración solo toca `microcuentas.cuenta` y el registro sigue creando la credencial igual |

## 3. Hechos confirmados, inferencias y decisiones

| # | Afirmación | Tipo | Fuente |
|---|---|---|---|
| H-1 | `POST /api/v1/users` no recibe ni guarda la autorización hoy | Hecho | `RegisterUserRequest.java` en `c94f20c`; Jira CM-365 |
| H-2 | Los errores de validación del registro ya salen todos a la vez, un elemento por campo, 422 `VALIDATION_FAILED` | Hecho | `BusinessExceptionHandler.camposInvalidos`, `FIELD_ERROR_CODES` |
| H-3 | Un `InvalidFieldException` del dominio sale como 422 con su campo, su código y su mensaje | Hecho | `BusinessExceptionHandler.invalidDomainField` (línea 198) |
| H-4 | La ruta nueva no necesita cambios en el Gateway, que además bloquea a quien no verificó el correo, salvo en `/me/verification` | Hecho | `FirebaseAuthGlobalFilter.java:55, 183-193` |
| H-5 | La migración siguiente es `V8`; ninguna rama abierta de Cuentas trae una `V8` | Hecho | `git ls-tree origin/develop src/main/resources/db/migration`; `origin/CM-188-correcciones` solo borra migraciones; la spec de CM-296 no nombra `V8` |
| H-6 | El ADR 0014 de `cameia-entrevista` aprobó a Groq con retención cero y sin entrenamiento | Hecho (se cita) | `repos/cameia-entrevista/docs/adr/0014-reintentos-y-respaldo-de-la-ia.md` |
| I-1 | Un registro repetido sobre una cuenta pendiente (200, ADR 0002) no cambia la aceptación guardada | Inferencia: el ADR 0002 dice «sin crear ni cambiar nada». La cuenta pendiente creada con esta versión ya la tiene. Una pendiente anterior a `V8` aceptará al iniciar sesión tras verificar | ADR 0002 |
| I-2 | `POST /me/policy-acceptance` sobre una cuenta deshabilitada responde 403 `ACCOUNT_DISABLED` y sobre una anonimizada o inexistente 404 `ACCOUNT_NOT_FOUND` | Inferencia: el mismo criterio que la activación (`Account.activate`) | `Account.java`, `ActivateAccountService.java` |
| I-3 | `policyVersion` se recorta (espacios al inicio y al final) antes de comparar, como los demás textos del registro | Inferencia: `SingleLineText.normalize` ya es la definición única de «espacio» del contrato | `RegisterUserRequest` |
| I-4 | Un `policyVersion` de más de 20 caracteres responde `POLICY_VERSION_OUTDATED`, sin código propio | Inferencia: la versión vigente tiene como máximo 12 caracteres (§6.1), así que todo valor más largo es distinto de ella. El criterio define solo tres códigos | CM-365 criterio 3 |
| I-5 | El evento `cuenta.creada` no cambia | Inferencia: el criterio no lo pide y su contrato v1 está publicado | `docs/eventos/cuenta-creada-v1.md` |
| D-1 | Mensaje de `POLICY_VERSION_REQUIRED`: «Recarga la página y vuelve a aceptar la política.» | Decisión de Paula, 12-nov-2026 | Chat |
| D-2 | En `GET /me`, una cuenta con versión `sin-aceptar` devuelve `acceptedAt: null`. La base conserva la fecha de creación, como pide el criterio 4 | Decisión de Paula, 12-nov-2026 | Chat |
| D-3 | Solo el valor JSON `true` cuenta como autorización. El texto `"true"`, el número `1`, un arreglo o un objeto responden `DATA_AUTHORIZATION_REQUIRED` en el campo, junto con los demás errores | Decisión de Paula, 12-nov-2026 | Chat |
| D-4 | La versión vigente tiene el valor por defecto `2026-11-v1` en `application.properties`, y `CAMEIA_POLICY_VERSION` lo cambia. No hace falta una tarea de DevOps. Cambiar de versión exige un PR de Backend coordinado con la web | Decisión de Paula, 12-nov-2026. Alternativa descartada: solo la variable, que obligaba a esperar una tarea de DevOps antes de desplegar | Chat |
| D-5 | El PR de Cuentas se abre en borrador y se fusiona el mismo día que el de Frontend (CM-368), para que el registro de la web no falle con 422 entre los dos despliegues | Decisión de Paula, 12-nov-2026. Alternativas descartadas: aceptar por unos días el registro sin los campos (código temporal que incumple el criterio 3) y fusionar Cuentas primero (rompe el registro en preproducción) | Chat |

## 4. Requisitos (EARS)

### 4.1 Registro (`POST /api/v1/users`, criterio 3)

- **REQ-POL-01.** El sistema deberá aceptar en el cuerpo del registro los campos `dataAuthorization` y `policyVersion`, además de los que ya existen.
- **REQ-POL-02.** Si `dataAuthorization` falta, es `null`, es `false` o es cualquier valor distinto del JSON `true` (D-3), el sistema deberá responder 422 `VALIDATION_FAILED` con un elemento `{"field":"dataAuthorization","code":"DATA_AUTHORIZATION_REQUIRED","message":"Acepta la política de tratamiento de datos para continuar."}`.
- **REQ-POL-03.** Si `policyVersion` falta, es `null`, está vacía o solo tiene espacios, el sistema deberá responder 422 con `{"field":"policyVersion","code":"POLICY_VERSION_REQUIRED","message":"Recarga la página y vuelve a aceptar la política."}` (D-1).
- **REQ-POL-04.** Si `policyVersion`, ya recortada, es distinta de la versión vigente (§6), el sistema deberá responder 422 con `{"field":"policyVersion","code":"POLICY_VERSION_OUTDATED","message":"La política cambió. Recarga la página y vuelve a aceptarla."}`. La comparación distingue mayúsculas: `2026-11-V1` es distinta de `2026-11-v1`.
- **REQ-POL-05.** Si hay errores en varios campos (los nuevos y los existentes), el sistema deberá devolverlos todos en la misma respuesta, un elemento por campo, en el orden en que Spring los informe. A cada campo le corresponde un solo elemento.
- **REQ-POL-06.** Cuando el registro crea la cuenta (201), el sistema deberá guardar `politica_version` = versión vigente y `politica_aceptada_en` = instante del reloj del servicio en UTC, en el mismo guardado (la misma transacción) que inserta la fila de la cuenta.
- **REQ-POL-07.** Cuando el registro devuelve una cuenta pendiente ya existente (200, ADR 0002), el sistema no deberá cambiar su versión ni su fecha de aceptación (I-1). Los campos nuevos se validan igual: sin autorización, la respuesta es 422.
- **REQ-POL-08.** Si el registro falla después de validar (Firebase caído, base caída, correo repetido), el sistema no deberá dejar una aceptación guardada sin cuenta. Hoy no queda ninguna fila, y la aceptación vive en la misma fila.

### 4.2 Persistencia (criterio 4)

- **REQ-POL-10.** La migración `V8__aceptacion_politica_cuenta.sql` deberá agregar a `microcuentas.cuenta` la columna `politica_version VARCHAR(20) NOT NULL` y la columna `politica_aceptada_en TIMESTAMPTZ NOT NULL`, con la restricción `ck_cuenta_politica_version_no_vacia CHECK (btrim(politica_version) <> '')` y un comentario por columna.
- **REQ-POL-11.** Al aplicar `V8`, cada fila existente deberá quedar con `politica_version = 'sin-aceptar'` y `politica_aceptada_en = fecha_creacion`, sin cambiar ninguna otra columna (`version` y `fecha_actualizacion` incluidas).
- **REQ-POL-12.** Ningún `INSERT` sin las dos columnas deberá poder guardarse. Una violación de `NOT NULL` o del `CHECK` es un defecto del sistema, no una entrada del cliente: no puede ocurrir por la API porque el dominio lo impide (§7).

### 4.3 Consulta y aceptación (criterio 5)

- **REQ-POL-20.** `GET /api/v1/users/me` deberá agregar a su respuesta el objeto `policyAcceptance` con `acceptedVersion` (texto), `acceptedAt` (fecha y hora ISO-8601 en UTC, o `null`) y `currentVersion` (texto). El resto de la respuesta no cambia.
- **REQ-POL-21.** Si la versión guardada es `sin-aceptar`, `acceptedAt` deberá ser `null` y `acceptedVersion` deberá ser `"sin-aceptar"` (D-2).
- **REQ-POL-22.** `POST /api/v1/users/me/policy-acceptance` con cuerpo `{"policyVersion":"<vigente>"}` deberá guardar, para la cuenta del `X-User-Id`, la versión vigente con el instante actual, y responder 200 con el mismo objeto `policyAcceptance` del REQ-POL-20.
- **REQ-POL-23.** Si la cuenta ya tiene guardada la versión vigente, el sistema deberá responder 200 con la fecha ya guardada, sin escribir en la base (ni `version` ni `fecha_actualizacion` cambian).
- **REQ-POL-24.** Si `policyVersion` falta, está vacía o es distinta de la vigente, el sistema deberá responder igual que en REQ-POL-03 y REQ-POL-04.
- **REQ-POL-25.** La identidad sale solo de `X-User-Id`. Sin ese encabezado, el sistema deberá responder 400 `IDENTITY_REQUIRED` (contrato vigente). Con un valor que no cumple `FirebaseUid.required`, también 400 `IDENTITY_REQUIRED`.
- **REQ-POL-26.** Sin cuenta, o con la cuenta anonimizada, el sistema deberá responder 404 `ACCOUNT_NOT_FOUND`. Con la cuenta deshabilitada, 403 `ACCOUNT_DISABLED` (I-2). Una cuenta pendiente o activa puede aceptar.
- **REQ-POL-27.** Si dos aceptaciones de la misma cuenta llegan a la vez y una pierde por concurrencia (`@Version`), el sistema deberá releer la cuenta. Si ya tiene la versión vigente, responde 200 con la fecha guardada. Si no la tiene, responde 409 `ACCOUNT_CONFLICT`.
- **REQ-POL-28.** Las respuestas 200 de `GET /me` (ya lo hace) y de `POST /me/policy-acceptance` deberán llevar `Cache-Control: no-store` y `Content-Type: application/json`.

### 4.4 Configuración (criterios 3 y 5)

- **REQ-POL-30.** La versión vigente deberá leerse de la propiedad `cuentas.policy.current-version`, que toma `${CAMEIA_POLICY_VERSION:2026-11-v1}` (D-4).
- **REQ-POL-31.** Si la versión vigente falta, está en blanco, no cumple `^\d{4}-(0[1-9]|1[0-2])-v[1-9]\d{0,2}$` o es `sin-aceptar`, el servicio no deberá arrancar. El error de arranque nombra la propiedad y no muestra ningún secreto.

### 4.5 Privacidad (criterio 6)

- **REQ-POL-40.** Ningún registro del servidor deberá contener la versión aceptada ni la fecha de aceptación junto a un dato personal. El único registro nuevo, de nivel INFO, dice «Aceptación de la política guardada para la cuenta {id}», donde `{id}` es el UUID interno de la cuenta, sin versión ni fecha.

### 4.6 Documentación (criterio 7)

- **REQ-POL-50.** OpenAPI de los tres endpoints con los campos nuevos, sus límites, ejemplos y cada respuesta de error.
- **REQ-POL-51.** `docs/errores.md` con los tres códigos nuevos (la prueba `ErrorCodeDocumentationTest` lo exige).
- **REQ-POL-52.** Colección Postman con los casos de los criterios 3 y 5 (§11).
- **REQ-POL-53.** `CLAUDE.md` del repo (§5, tabla `cuenta` y lista de migraciones), `.env.example` y un ADR `0006-aceptacion-de-la-politica.md` con las decisiones D-1 a D-4 e I-1 a I-4.

## 5. Contrato

### 5.1 `POST /api/v1/users` (campos nuevos)

| Campo | Tipo en el contrato | Obligatorio | Regla | Ejemplo |
|---|---|---|---|---|
| `dataAuthorization` | booleano | Sí | Solo el JSON `true` es válido (D-3) | `true` |
| `policyVersion` | texto, 1 a 20 caracteres | Sí | Se recorta; debe ser igual a la versión vigente | `"2026-11-v1"` |

Respuesta 201 y 200: sin cambios (`RegisteredUserResponse`).

### 5.2 `GET /api/v1/users/me` (campo nuevo)

```json
"policyAcceptance": {"acceptedVersion": "2026-11-v1", "acceptedAt": "2026-11-14T15:30:00Z", "currentVersion": "2026-11-v1"}
```

Cuenta anterior a `V8`: `{"acceptedVersion":"sin-aceptar","acceptedAt":null,"currentVersion":"2026-11-v1"}`.

### 5.3 `POST /api/v1/users/me/policy-acceptance` (nuevo)

- Encabezados: `X-User-Id` (obligatorio), `X-Request-Id`.
- Cuerpo: `{"policyVersion":"2026-11-v1"}`. Los campos no esperados se ignoran (RT-01).
- 200: el objeto `policyAcceptance` de §5.2, como raíz de la respuesta.
- Errores: 400 `IDENTITY_REQUIRED`; 403 `ACCOUNT_DISABLED`; 404 `ACCOUNT_NOT_FOUND`; 409 `ACCOUNT_CONFLICT`; 422 `VALIDATION_FAILED` (con `POLICY_VERSION_REQUIRED` o `POLICY_VERSION_OUTDATED`) o `REQUEST_BODY_INVALID_FORMAT` (cuerpo ilegible); 406, 413 y 415 de protocolo (ADR 0004); 503 `DATABASE_UNAVAILABLE` (ADR 0005); 500 `INTERNAL_ERROR` solo para lo imprevisto.

Es un cambio aditivo: Frontend (CM-368) lo consume. El aviso a Frontend se redacta en la fase 3.

## 6. Versión vigente

### 6.1 Formato

`AAAA-MM-vN`: año de 4 dígitos, mes de `01` a `12` y `v` seguida de un número de 1 a 999 sin ceros a la izquierda. Expresión: `^\d{4}-(0[1-9]|1[0-2])-v[1-9]\d{0,2}$`. Largo máximo: 12 caracteres, menor que los 20 de la columna. `sin-aceptar` no cumple el formato, así que nunca puede ser la vigente.

### 6.2 Dónde vive

- `CurrentPolicy` (objeto de valor en `domain.model`) valida el formato y compara. Es la única definición de la regla.
- `PolicyProperties` (`infrastructure.config`, `@ConfigurationProperties("cuentas.policy")`, `@Validated`) exige `@NotBlank`. La configuración crea el `CurrentPolicy`: un formato inválido lanza `IllegalArgumentException` al crear el contexto y el servicio no arranca (fail-fast).
- **Trampa:** `.env.example` no puede traer `CAMEIA_POLICY_VERSION=` vacía, porque Spring importa `.env` y una línea vacía anula el valor por defecto, y el servicio no arranca. Se escribe comentada: `# CAMEIA_POLICY_VERSION=2026-11-v1`.

## 7. Matriz de validación por campo (R2)

| Campo | Entrada | Código | HTTP | Mensaje | Prueba |
|---|---|---|---|---|---|
| `dataAuthorization` | ausente | `DATA_AUTHORIZATION_REQUIRED` | 422 | Acepta la política de tratamiento de datos para continuar. | `register_shouldReturnDataAuthorizationRequired_whenFieldIsMissing` |
| | `null` | ídem | 422 | ídem | `…_whenFieldIsNull` |
| | `false` | ídem | 422 | ídem | `…_whenFieldIsFalse` |
| | `"true"` (texto) | ídem | 422 | ídem | `…_whenFieldIsTextTrue` |
| | `1` | ídem | 422 | ídem | `…_whenFieldIsNumberOne` |
| | `[]` y `{}` | ídem | 422 | ídem | `…_whenFieldIsArrayOrObject` (parametrizada) |
| | `true` | — | 201 | — | `register_shouldStorePolicyAcceptance_whenAuthorizationIsGiven` |
| `policyVersion` | ausente, `null`, `""`, `"   "` | `POLICY_VERSION_REQUIRED` | 422 | Recarga la página y vuelve a aceptar la política. | `register_shouldReturnPolicyVersionRequired_whenBlank` (parametrizada, 4 casos) |
| | `"2026-10-v1"` | `POLICY_VERSION_OUTDATED` | 422 | La política cambió. Recarga la página y vuelve a aceptarla. | `register_shouldReturnPolicyVersionOutdated_whenVersionIsNotCurrent` (parametrizada, valor `"2026-10-v1"`) |
| | `"2026-11-V1"` | ídem | 422 | ídem | `register_shouldReturnPolicyVersionOutdated_whenVersionIsNotCurrent` (valor `"2026-11-V1"`) |
| | `"sin-aceptar"` | ídem | 422 | ídem | `register_shouldReturnPolicyVersionOutdated_whenVersionIsNotCurrent` (valor `"sin-aceptar"`) |
| | 21 caracteres (`"2026-11-v1aaaaaaaaaaa"`) | ídem | 422 | ídem | `register_shouldReturnPolicyVersionOutdated_whenVersionIsNotCurrent` (valor de 21 caracteres) |
| | `"<script>"` y `"' OR 1=1 --"` | ídem | 422 | ídem | `register_shouldReturnPolicyVersionOutdated_whenVersionIsNotCurrent` (valores hostiles) |
| | `" 2026-11-v1 "` | — | 201 | — | `register_shouldAcceptTrimmedVersion` |
| | `123` (número) | Jackson lo convierte a `"123"`: `POLICY_VERSION_OUTDATED` | 422 | ídem | `register_shouldReturnPolicyVersionOutdated_whenVersionIsNotCurrent` (valor `123`) |
| | `["2026-11-v1"]` | `REQUEST_BODY_INVALID_FORMAT`, sin `errors` (comportamiento vigente de un arreglo en un texto) | 422 | Revisa el formato de los datos enviados. | `register_shouldReturnBodyInvalidFormat_whenPolicyVersionIsArray` |
| Varios | sin los dos campos y con el correo mal escrito | 3 elementos: `dataAuthorization`, `policyVersion`, `email` | 422 | los de cada campo | `register_shouldReturnAllFieldErrorsAtOnce_whenPolicyFieldsAndEmailAreInvalid` |

Las mismas filas de `policyVersion` valen para `POST /me/policy-acceptance` (pruebas en `PolicyAcceptanceControllerTest`).

## 8. Base de datos (R4)

| Columna | Tipo | Restricción | Coincide con |
|---|---|---|---|
| `politica_version` | `VARCHAR(20)` | `NOT NULL`, `ck_cuenta_politica_version_no_vacia` | `@Column(length = 20)`, `PolicyAcceptance.MAX_VERSION_LENGTH = 20`, `@Schema(maxLength = 20)` |
| `politica_aceptada_en` | `TIMESTAMPTZ` | `NOT NULL` | `Instant` en UTC |

- Sin índice: ninguna consulta filtra por estas columnas. Si más adelante se cuentan las cuentas por versión, se agrega con su migración.
- El `CHECK` de «no vacío» aplica a la versión. A una fecha no le aplica un largo: su garantía es el `NOT NULL`.
- La anonimización y la purga no cambian: la versión y la fecha no son datos personales y se van con la fila cuando se borra.
- La migración se escribe en cuatro pasos (agregar como nulas, rellenar, `SET NOT NULL`, `CHECK`) para poder aplicarla sobre una tabla con filas. Una sola transacción de Flyway: si falla, no queda nada a medias.

## 9. Casos borde (R5)

| Caso | Aplica | Dato literal y resultado | Prueba |
|---|---|---|---|
| Nulos y ausentes | Sí | §7 | §7 |
| Límites de largo (n−1, n, n+1) | Sí | La vigente válida tiene como máximo 12 caracteres. 19, 20 y 21 caracteres son distintos de la vigente → `OUTDATED`; con 20, en `PolicyAcceptance` el objeto se crea; con 21, `IllegalArgumentException` | `PolicyAcceptanceTest.shouldReject_whenVersionIsLongerThan20` y `shouldAccept_whenVersionIsExactly20` |
| Unicode | Sí | `"2026-11-ｖ1"` (v de ancho completo) → `OUTDATED`; no se normaliza a ASCII | `CurrentPolicyTest.requireCurrent_shouldThrowOutdated_whenDifferent` (valor de ancho completo) |
| Fechas y zonas | Sí | `acceptedAt` es un `Instant` (UTC). Con el reloj fijo `2026-11-14T15:30:00Z`, la respuesta dice `"2026-11-14T15:30:00Z"` | `AcceptPolicyServiceTest.accept_shouldStoreClockInstant_whenAccountWasNotAccepted` |
| Duplicados e idempotencia | Sí | Segunda aceptación igual → 200, misma `acceptedAt`, sin `save` | `accept_shouldNotWrite_whenVersionAlreadyAccepted`; E2E `acceptPolicy_shouldKeepFirstDate_whenRepeated` |
| Concurrencia y doble envío | Sí | Dos `POST` a la vez → los dos 200 con la misma fecha | `accept_shouldReturnStoredAcceptance_whenConcurrentSaveWon`; E2E `acceptPolicy_shouldAnswer200Twice_whenSentConcurrently` |
| Falla a medias | Sí | Base caída durante la aceptación → 503 `DATABASE_UNAVAILABLE` (manejador vigente), sin cambio | Cubierto por `DatabaseUnavailableEndToEndTest`; se agrega una petición a la ruta nueva |
| Dependencia caída | No | La aceptación no llama a Firebase ni a Rabbit | — |
| Colecciones vacías o llenas | No | No hay colecciones | — |
| Recurso ajeno | Sí | Solo `X-User-Id`; un `firebaseUid` en el cuerpo se ignora | `accept_shouldIgnoreBodyIdentity` |
| Transición de estado inválida | Sí | Deshabilitada → 403; anonimizada → 404 | `AccountTest.acceptPolicy_shouldThrow…` |
| Carga grande | Sí | Cuerpo de más de 10 MiB → 413 (vigente) | Vigente |
| Entradas de ataque | Sí | §7, filas hostiles | §7 |
| Migración sobre datos | Sí | Base con 3 filas (pendiente, activa y anonimizada) → `sin-aceptar` con su `fecha_creacion`; `version` sin cambio | `MigracionPoliticaConDatosTest` |
| Configuración inválida | Sí | `""`, `"2026-13-v1"`, `"2026-11-v0"`, `"sin-aceptar"` → no arranca | `PolicyPropertiesTest` |

## 10. Errores (R3) y catálogo

| Código nuevo | HTTP | Campo | Mensaje | Origen |
|---|---|---|---|---|
| `DATA_AUTHORIZATION_REQUIRED` | 422 | `dataAuthorization` | Acepta la política de tratamiento de datos para continuar. | Restricción `@ExplicitConsent` |
| `POLICY_VERSION_REQUIRED` | 422 | `policyVersion` | Recarga la página y vuelve a aceptar la política. | `@NotBlank` |
| `POLICY_VERSION_OUTDATED` | 422 | `policyVersion` | La política cambió. Recarga la página y vuelve a aceptarla. | `PolicyVersionOutdatedException` (dominio) por `@CurrentPolicyVersion` |

Rutas al 500 genérico revisadas: los tipos raros de `dataAuthorization` no llegan a Jackson como error porque el campo es `Object`; un arreglo en `policyVersion` sale como `REQUEST_BODY_INVALID_FORMAT` (vigente); la configuración inválida impide arrancar, así que no hay petición que falle por ella; una violación de restricción de base no puede ocurrir por la API (dominio). Ninguna ruta previsible llega al 500.

## 11. Postman (R11)

Carpeta nueva «Políticas» en `postman/cameia-cuentas.postman_collection.json`, con prueba de estado, `Content-Type` y forma de `errors[]` en cada petición:

1. Registro sin `dataAuthorization` ni `policyVersion` → 422 con los dos elementos.
2. Registro con `dataAuthorization: false` → 422 `DATA_AUTHORIZATION_REQUIRED` y el mensaje literal.
3. Registro con `dataAuthorization: "true"` → 422 `DATA_AUTHORIZATION_REQUIRED`.
4. Registro con `policyVersion: "   "` → 422 `POLICY_VERSION_REQUIRED`.
5. Registro con `policyVersion: "2026-10-v1"` → 422 `POLICY_VERSION_OUTDATED` y el mensaje literal.
6. Registro válido → 201 (correo con marca de tiempo, como la carpeta «Registro»).
7. `GET /me` de esa cuenta → 200 con `policyAcceptance.acceptedVersion == currentVersion` y `acceptedAt` no nulo.
8. `POST /me/policy-acceptance` con la vigente → 200; se guarda `acceptedAt`.
9. Repetir el 8 → 200 con la misma `acceptedAt`.
10. `POST /me/policy-acceptance` con `"2026-10-v1"` → 422 `POLICY_VERSION_OUTDATED`.
11. `POST /me/policy-acceptance` sin `X-User-Id` → 400 `IDENTITY_REQUIRED`.
12. `POST /me/policy-acceptance` de un `X-User-Id` sin cuenta → 404 `ACCOUNT_NOT_FOUND`.

Las peticiones existentes de la carpeta «Registro» suman `"dataAuthorization": true, "policyVersion": "{{policyVersion}}"`. La variable `policyVersion` va en `local.postman_environment.json` con `2026-11-v1`.

## 12. Seguridad (R6), código (R7), documentación (R8), pruebas (R9) y cobertura (R10)

- **ASVS 5.0.0 nivel 1:** 8.2.2 (autorización por objeto: identidad solo de `X-User-Id`; prueba `accept_shouldIgnoreBodyIdentity`); 2.2.1 (validación de entrada en el borde, con una lista de valores aceptados: §7); 4.1.1 (`Content-Type` con charset en errores: manejador vigente, `ProblemDetailTestSupport`). Verificados contra `cameia-infra/docs/seguridad/matriz-asvs-nivel1.md` (líneas 54, 72 y 119). La matriz no trae requisitos de nivel 1 en V16 (registros): la privacidad de los registros se respalda con AC-0003 SEG-04 y el criterio 6 (`accept_shouldNotLogVersionOrDate` con `OutputCaptureExtension`).
- **OWASP API Top 10 (2023):** API1 (autorización por objeto, la misma prueba de 8.2.2); API3 (asignación masiva: el cuerpo no puede fijar `acceptedAt` ni la cuenta; `accept_shouldIgnoreAcceptedAtInBody`); API8 (configuración: fail-fast de la versión, `PolicyPropertiesTest`).
- **Código lean:** se reutilizan `InvalidFieldException`, el manejador, `FIELD_ERROR_CODES`, el patrón de `DomainRuleValidator` (carga dinámica del código), el reloj inyectado y el patrón de reintento por concurrencia de `ActivateAccountService`. Lo nuevo y por qué: `ExplicitConsent` (Bean Validation no trae «solo el JSON `true`»; `@AssertTrue` acepta `null` y obliga a tipar `Boolean`, que deja pasar el texto `"true"`); `CurrentPolicyVersion` (`DomainRule` es estático y esta regla necesita la versión configurada).
- **Deuda que ya hay:** `Account.register` y `Account.rebuild` superan los 3 parámetros (6 y 9) y esta tarea agrega uno más. Pasarlos a un objeto comando cambiaría 18 archivos de prueba que esta CM no necesita tocar. Queda anotado para CM-296 (alinear el estándar, parte 2).
- **Pruebas por capa y cobertura:** §14 y `plan.md`. Meta: ≥ 90 % de líneas y ramas en las clases nuevas y modificadas, medidas con JaCoCo.

## 13. Parte de Product Owner (criterio 1) — PENDIENTE (Paula)

No la escribe la IA. Paula redacta los tres textos (política de tratamiento de datos personales, aviso de privacidad y términos y condiciones), con versión `2026-11-v1` y fecha de vigencia. Una persona con criterio legal los revisa y se entregan a Frontend en Markdown. Los textos viven en `cameia-web`; Cuentas solo guarda la versión. La política lista como mínimo:

- responsable y contacto;
- datos que se recogen;
- finalidad de cada dato, incluido el celular (CA-1.1.44);
- derechos del titular (consultar, actualizar, rectificar, suprimir y revocar) y cómo ejercerlos;
- tiempo de conservación;
- tratamiento de menores;
- encargados externos: Firebase y Google Cloud (cuentas y alojamiento), Google (modelo de IA principal), Groq (respaldo, con retención cero y sin entrenamiento: ADR 0014 de `cameia-entrevista`) y AssemblyAI (transcripción; conserva el archivo 2 días).

No bloquea el backend: Cuentas solo necesita que la versión sea `2026-11-v1`. Antes de publicar, la versión del texto y la del servidor deben coincidir.

**Cómo se hace (decisión de Paula, 12-nov-2026):**
- **Borrador guiado.** La IA arma los tres textos con la estructura que exige la Ley 1581 de 2012 y lo que pide CM-365, usando solo datos ciertos del proyecto. Lo que no se sabe queda `PENDIENTE (Paula)`; por ejemplo, el responsable y su contacto. Como modelo de estructura se usa la política de privacidad que el equipo ya conoce, validada por un abogado (página de Juan Diego Gomez: `https://www.joyeriakob.com/informacion/privacidad`); se toma su orden y sus secciones, no su texto. El borrador declara el uso de IA y queda solo en local hasta que Paula diga.
- **Revisión.** Paula pide la revisión al docente de Proyecto 2, o a quien él indique. Si no responde antes del 18-nov, los textos se publican con la nota «Texto académico, sin revisión de un abogado» y Paula, como Product Owner, deja el cambio anotado en CM-365.

## 14. Atributos de calidad de CAMEIA (R13)

| Atributo (anexo) | Métrica | Cómo lo cumple | Prueba |
|---|---|---|---|
| AC-0001 Adecuación funcional de la IA | — | No aplica: la tarea no usa IA | — |
| AC-0002 Desempeño (DES-02: p95 de operaciones JSON propias ≤ 2 s) | p95 ≤ 2 s | Aceptación: una lectura y como máximo una escritura por clave única; el registro suma dos columnas al mismo `INSERT` | `AcceptPolicyEndToEndTest.acceptPolicy_shouldAnswerWithin2Seconds` (20 peticiones seguidas, máximo < 2 s) |
| AC-0003 Seguridad (SEG-04: cero datos sensibles en telemetría) | 0 registros con versión o fecha junto a un dato personal | REQ-POL-40 | `accept_shouldNotLogVersionOrDate`, `register_shouldNotLogPolicyVersion` |
| AC-0004 Fiabilidad (FIA-01: el fallo conserva el último estado confirmado) | 0 aceptaciones sin cuenta | Aceptación en la misma fila y transacción que la cuenta | `register_shouldStorePolicyAcceptanceInSameRow` (la política queda en la misma fila de la cuenta) y `RegisterUserServiceTest.register_shouldNotTouchFirebase_whenPolicyVersionIsNotCurrent` (una versión no vigente no crea credencial) |
| AC-0004 Fiabilidad (FIA-02: cero efectos adicionales al repetir) | 0 escrituras al repetir | REQ-POL-23 y 27 | `accept_shouldNotWrite_whenVersionAlreadyAccepted`, prueba concurrente E2E |
| AC-0005 Mantenibilidad (MAN-01) | Cobertura ≥ 90 % de lo tocado; `LayeredArchitectureTest` en verde | Regla en el dominio, borde solo de forma | JaCoCo, ArchUnit |
| AC-0006 Interoperabilidad (IOP-01: contratos documentados) | Contrato aditivo y documentado | Campos nuevos sin cambiar los existentes; OpenAPI completa | `OpenApiPolicyContractTest` (los tres endpoints traen los campos y códigos) |

Los ID de las métricas se toman de las specs aprobadas de CM-279 (DES-02, SEG-04, FIA-01, FIA-02) y CM-179 (MAN-01, IOP-01).

## 15. Lista de verificación de la CM

| Fila | Se demuestra con | Esperado |
|---|---|---|
| Criterio 3 (tres códigos, todos a la vez) | `UserRegistrationControllerTest` §7; Postman 1–5 | 422 con elementos exactos |
| Criterio 4 (columnas, `sin-aceptar`, mismo guardado) | `CuentaSchemaMigrationTest`, `MigracionPoliticaConDatosTest`, `AccountRegistrationEndToEndTest.register_shouldStorePolicyAcceptanceInSameRow` | Columnas, restricción y relleno |
| Criterio 5 (`GET` y `POST`, idempotente) | `CurrentAccountEndToEndTest`, `AcceptPolicyEndToEndTest`; Postman 7–12 | §5 |
| Criterio 6 (registros) | Pruebas de salida de registro | Sin versión ni fecha |
| Criterio 7 (documentación) | `OpenApiPolicyContractTest`, `ErrorCodeDocumentationTest`, Newman | En verde |
| R1–R14 | `plan.md` §«Regla → evidencia» | Cada fila con su comando |
| Adversariales | Recurso ajeno, doble envío, texto hostil, configuración inválida | §9 |
| Verificación única | `./mvnw.cmd clean verify` y Newman sobre la punta | 0 fallos; cobertura ≥ 90 % |

## 16. Preguntas

| Pregunta | Estado |
|---|---|
| Mensaje de `POLICY_VERSION_REQUIRED` | Respondida por Paula, 12-nov (D-1) |
| `acceptedAt` de las cuentas `sin-aceptar` | Respondida por Paula, 12-nov (D-2) |
| Solo el JSON `true` | Respondida por Paula, 12-nov (D-3) |
| Valor por defecto de la versión en el código o solo variable | Respondida por Paula, 12-nov (D-4) |
| Despliegue con la web | Respondida por Paula, 12-nov (D-5) |
| Revisión legal de los textos | Respondida por Paula, 12-nov: docente de Proyecto 2, con nota de respaldo (§13) |
| Responsable del tratamiento y su contacto | **PENDIENTE (Paula)**: se completa en el borrador de los textos (§13) |
