# Spec — CM-297: `GET /api/v1/users/me` devuelve los datos de la cuenta de quien llama

- **Tarea:** **CM-297**, subtarea de CM-14 en el Sprint 2 (creada el 10-oct-2026). Es la tarea propia de este endpoint: la «CM-42» que
  citaban documentos anteriores no existe en Jira y CM-36 es la tarea de las validaciones del registro, no de esta consulta.
- **Repositorio:** `cameia-cuentas`. Escrita contra `origin/develop` `69a1fb6`; se ejecuta en la cola de Cuentas después de la última capa de CM-279
  (`CM-279-postman-eventos-cuenta`), porque comparte `docs/errores.md`, la colección de Postman y `AccountMapper` con esa cadena.
- **Backlog vigente:** `09102026_01_Backlog_v6.xlsx`. HU-1.1, Notas técnicas: «Lo guardado se verifica con GET /api/v1/users/me → 200 OK (id, nombres,
  apellidos, fecha de nacimiento, celular, pronombre, estado y plan; sin correo ni contraseña)». HU-1.5 (MVP si alcanza) usa el mismo endpoint:
  CA-1.5.1 (200 leyendo `cuenta` por el `firebase_uid` del token), CA-1.5.2 (`phoneNumber: null`), CA-1.5.4 (`?firebase_uid=<otro>` → 200 con los datos
  del dueño del token; el parámetro se ignora). HU-1.10 (acceso con Google, MVP si alcanza) cita `GET /api/v1/users/me` → 200 (CA-1.10.2, la
  cuenta se encuentra por el mismo `firebase_uid`) y → 404 sin fila en `cuenta` (CA-1.10.4).
- **Estado:** `LISTA PARA EJECUTAR` (sección 15). PENDIENTES no bloqueantes en la sección 14.
- **Atributos de calidad:** seguridad (SEG-01, SEG-02, SEG-04), privacidad (REST-0003), desempeño (DES-02), fiabilidad (FIA-01), interacción (AC-0006),
  interoperabilidad (IOP-01), mantenibilidad (MAN-01).

## 1. Contexto y defectos comprobados

| # | Hallazgo | Dónde (`develop` `69a1fb6`) | Cómo se ve | Esperado |
|---|---|---|---|---|
| D1 | `GET /api/v1/users/me` no existe | `git grep -n "users/me" -- src` solo muestra `POST /api/v1/users/me/verification` | `GET` → 405 `METHOD_NOT_ALLOWED` (la ruta existe para `POST …/verification`, no para `GET /me`) o 404 `ROUTE_NOT_FOUND` | 200 con la cuenta |
| D2 | Una cuenta con `fecha_nacimiento` nula rompe toda lectura | `AccountMapper.toDomain` hace `new BirthDate(entity.getFechaNacimiento())` y `BirthDate` rechaza `null`; `toEntity` hace `account.getBirthDate().value()` | La columna admite nulo y hay cuentas anteriores sin fecha (lo reconoce la spec de CM-279 Cuentas). Leerlas lanza `IllegalArgumentException` → 500 `INTERNAL_ERROR` (también en la activación y en el registro repetido) | La cuenta se lee con fecha desconocida: `birthDate: null` |

## 2. Alcance y PR

| PR | Rama | Base | Qué entrega | Tamaño |
|---|---|---|---|---|
| 0 | `CM-297-spec-consulta-de-mi-cuenta` | punta de `CM-279-postman-eventos-cuenta` | solo spec, plan y tarjetas (no caben con el código: ≈ 455 líneas) | ≈ 455 |
| 1 | `CM-297-consulta-de-mi-cuenta` | PR 0 | fecha desconocida en `Account` y `AccountMapper`; caso de uso, controlador, DTO, excepción de identidad en blanco; OpenAPI; `docs/errores.md`; pruebas; Postman | 922 |

Si al cerrar el PR 1 pasa de 800, la tarjeta de Postman (T-36.6) sale a una capa propia `CM-297-postman-consulta-de-mi-cuenta`. Fuera de alcance: sección 13.

## 3. Trazabilidad

| Fuente | Literal | Requisito | Prueba | Postman (carpeta «Mi cuenta») |
|---|---|---|---|---|
| HU-1.1 Notas técnicas | 200 (id, nombres, apellidos, fecha de nacimiento, celular, pronombre, estado y plan; sin correo ni contraseña) | REQ-36-01, 02 | `CurrentAccountControllerTest.getMe_shouldReturnAccountFields_whenAccountExists`; `CurrentAccountEndToEndTest.getMe_shouldReturnStoredAccount_whenRegistered` | `M-01 · 200 mi cuenta` |
| CA-1.5.1 | 200 leyendo `cuenta` por el `firebase_uid` del token | REQ-36-03 | `GetCurrentAccountServiceTest.find_shouldLookUpByHeaderIdentity_whenCalled` | `M-01` |
| CA-1.5.2 | 200 con `phoneNumber: null` | REQ-36-02 | `CurrentAccountControllerTest.getMe_shouldReturnNullPhone_whenAccountHasNoPhone` | `M-02 · 200 sin celular` |
| CA-1.5.4 | `?firebase_uid=<otro>` → 200 con los datos del dueño del token; el parámetro se ignora | REQ-36-04 | `CurrentAccountEndToEndTest.getMe_shouldIgnoreQueryIdentity_whenAnotherUidIsSent` | `M-03 · 200 ignora firebase_uid de otra cuenta` |
| CA-1.10.4 (Google) | 404 sin fila en `cuenta` | REQ-36-05 | `GetCurrentAccountServiceTest.find_shouldThrowNotFound_whenNoAccount` | `M-04 · 404 sin cuenta` |
| RT-02 | identidad del Gateway | REQ-36-06 | `CurrentAccountControllerTest.getMe_shouldReturn400_whenIdentityHeaderIsMissing` y `…_whenIdentityHeaderIsBlank` | `M-05 · 400 sin X-User-Id`, `M-06 · 400 X-User-Id en blanco` |
| RT-05 | sin detalles internos | REQ-36-08 | `CurrentAccountEndToEndTest.getMe_shouldReturnBirthDateNull_whenLegacyAccountHasNoBirthDate` | — (la cubre el E2E; la fila no se puede crear por la API) |

## 4. Requisitos (EARS)

- **REQ-36-01.** Cuando llegue `GET /api/v1/users/me` con `X-User-Id` de una cuenta existente, Cuentas debe responder **200**, `Content-Type`
  `application/json;charset=UTF-8`, con exactamente estos campos: `id` (UUID de la cuenta), `firstName`, `lastName`, `birthDate` (`yyyy-MM-dd` o
  `null`), `phoneNumber` (E.164 o `null`), `pronoun` (`HE`, `SHE`, `THEY` o `null`), `status` (`PENDING_VERIFICATION`, `ACTIVE`, `DISABLED`) y `plan`
  (`FREE`).
- **REQ-36-02.** La respuesta no debe incluir correo, contraseña, `firebaseUid`, `version`, fechas de creación, actualización o eliminación, ni ningún
  otro campo. Los campos sin valor van presentes con `null` (no se omiten).
- **REQ-36-03 (RT-03).** La cuenta se busca solo por el `firebase_uid` = `X-User-Id`. Ningún dato de la ruta, del parámetro de consulta ni del cuerpo
  interviene.
- **REQ-36-04 (CA-1.5.4).** Cualquier parámetro de consulta (`firebase_uid`, `id`, `uid`) se ignora y la respuesta es la de REQ-36-01.
- **REQ-36-05.** Sin fila en `cuenta` para ese `firebase_uid`, o con la cuenta en `ANONYMIZED`, Cuentas debe responder **404** `ACCOUNT_NOT_FOUND`
  con `detail` «No encontramos una cuenta para este usuario» (texto publicado del código).
- **REQ-36-06.** Sin `X-User-Id`, o con él vacío, solo con espacios o de más de 128 caracteres, Cuentas debe responder **400** `IDENTITY_REQUIRED` con
  `detail` «La petición no incluye los datos que exige esta ruta», sin consultar la base.
- **REQ-36-07.** `plan` es siempre `FREE`: es el único plan que existe hasta HU-8.2 y Cuentas aún no lo guarda (igual que la respuesta del registro).
- **REQ-36-08 (D2).** Una cuenta con `fecha_nacimiento` nula se lee y se devuelve con `birthDate: null`; ninguna lectura ni escritura de esa cuenta
  responde 500 por la fecha. El registro sigue exigiendo la fecha (no cambia).
- **REQ-36-09.** La operación es de solo lectura (`@Transactional(readOnly = true)`) y no registra datos personales; un rechazo se registra una vez con
  `code` y `requestId`.

## 5. Matriz de validación

| Entrada | Caso (literal) | Estado | `code` | Mensaje | Prueba |
|---|---|---|---|---|---|
| `X-User-Id` | ausente | 400 | `IDENTITY_REQUIRED` | «La petición no incluye los datos que exige esta ruta» | `getMe_shouldReturn400_whenIdentityHeaderIsMissing` |
| ídem | `""`, `"   "`, `"\t"` | 400 | `IDENTITY_REQUIRED` | ídem | `GetCurrentAccountServiceTest.find_shouldRejectIdentity_whenBlankOrTooLong` (parametrizada) |
| ídem | 128 caracteres (`"u".repeat(128)`) sin cuenta | 404 | `ACCOUNT_NOT_FOUND` | «No encontramos una cuenta para este usuario» | ídem (límite n) |
| ídem | 129 caracteres | 400 | `IDENTITY_REQUIRED` | ídem | ídem (n+1) |
| ídem | `"' OR 1=1 --"` | 404 | `ACCOUNT_NOT_FOUND` | ídem (consulta parametrizada) | `CurrentAccountEndToEndTest.getMe_shouldReturn404_whenIdentityLooksLikeSql` |
| consulta | `?firebase_uid=otro&id=…` | 200 | — | datos propios | REQ-36-04 |
| `Accept` | `application/xml` | 406 | `MEDIA_TYPE_NOT_ACCEPTABLE` | existente | `M-08 · 406 si solo acepta XML` |
| `Accept` | `lo///malo` (mal formado) | 406 con cuerpo vacío, sin `code` ni `requestId` | — | existente en todas las rutas; **PENDIENTE de Paula** (tarea transversal para responder `MEDIA_TYPE_NOT_ACCEPTABLE` también con un `Accept` que no se puede interpretar) | sonda con petición real; sin prueba en el repo |
| método | `PUT`, `DELETE`, `POST /api/v1/users/me` | 405 | `METHOD_NOT_ALLOWED` | existente | `FrameworkErrorsTest` (existente) |
| cuerpo | un cuerpo en el `GET` | se ignora | — | — | justificación: `GET` sin `@RequestBody` |

`X-User-Id` lo pone el Gateway con el `uid` de Firebase (≤ 128 caracteres, la columna `firebase_uid VARCHAR(128)`). La validación de blanco y largo
existe para quien llama sin pasar por el Gateway.

## 6. Base de datos

Sin migración. Lee `microcuentas.cuenta` por `firebase_uid` (único e indexado por su restricción `UNIQUE`). Se corrige solo el mapeo de
`fecha_nacimiento` nula (D2); la columna sigue admitiendo nulo, como hoy.

## 7. Casos borde

| Grupo | Caso | Resultado | Prueba |
|---|---|---|---|
| Presencia | celular nulo; pronombre nulo (cuentas anteriores a la regla de pronombres); fecha nula | 200 con `null` en ese campo | controlador y E2E |
| Estado | `PENDING_VERIFICATION` | 200 con ese `status` (por el Gateway, el 403 `EMAIL_NOT_VERIFIED` llega antes) | `GetCurrentAccountServiceTest.find_shouldReturnAccount_whenStatusIsNotAnonymized` (parametrizada: `PENDING_VERIFICATION`, `ACTIVE`, `DISABLED`) |
| Estado | `ANONYMIZED` | 404 `ACCOUNT_NOT_FOUND` | `GetCurrentAccountServiceTest.find_shouldThrowNotFound_whenAccountIsAnonymized` |
| Propiedad | `?firebase_uid=` de otra cuenta existente | 200 con la propia | REQ-36-04 |
| Propiedad | `X-User-Id` de una cuenta que no existe | 404 | REQ-36-05 |
| Texto | nombre con tildes, ñ, ü y emoji (`"María José 😀"` guardado en la base) | 200, devuelto igual, UTF-8 | `CurrentAccountEndToEndTest.getMe_shouldReturnUnicodeNames_whenStored` |
| Texto | nombre `"<script>alert(1)</script>"` guardado | 200 como texto JSON (sin interpretarlo) | ídem (segundo caso) |
| Fechas | `fecha_nacimiento` `2000-02-29` | `"2000-02-29"` | ídem |
| Concurrencia | lectura mientras otra petición activa la cuenta | 200 con el estado confirmado en ese momento (lectura sin bloqueo, `READ COMMITTED`) | justificación: solo lectura |
| Dependencia lenta o caída | PostgreSQL no responde | 500 `INTERNAL_ERROR` con traza en `ERROR`, comprobado con la base detenida (417 ms); con la base colgada el pool de Hikari espera 30 s (valor por defecto). Una dependencia caída es un fallo previsible y debería responder 503 `DEPENDENCY_UNAVAILABLE`; afecta a todas las rutas de Cuentas, así que queda fuera de esta tarea: **PENDIENTE de Paula** (decidir la tarea transversal) | `BusinessExceptionHandlerTest` (existente para el 500); sin Firebase en esta operación |
| Carga | cabecera `X-User-Id` de 10 000 caracteres | 400 con HTML de Tomcat (límite de 8 KB de `server.max-http-request-header-size`), respuesta fuera del manejador de errores y sin `code` ni `requestId`; la regla de n+1 (129 caracteres) solo cubre los valores que llegan al controlador. Existe en todas las rutas de Cuentas y solo es alcanzable sin pasar por el Gateway: **PENDIENTE de Paula** (¿tarea transversal de errores del contenedor?) | sonda con petición real; sin prueba en el repo |

## 8. Errores

| Código | HTTP | Mensaje | Excepción | Cambio |
|---|---|---|---|---|
| `ACCOUNT_NOT_FOUND` | 404 | «No encontramos una cuenta para este usuario» | `AccountNotFoundException` | Se reutiliza; su fila de `docs/errores.md` suma `GET /api/v1/users/me` |
| `IDENTITY_REQUIRED` | 400 | «La petición no incluye los datos que exige esta ruta» | `ServletRequestBindingException` (ausente) y la nueva `IdentityRequiredException` (en blanco o de más de 128) | Nueva excepción del mismo código; misma respuesta |

Rutas al 500 cerradas: fecha nula (D2, REQ-36-08); estado `ANONYMIZED` (404); `Optional` vacío (404). Queda solo el fallo de infraestructura.

## 9. Contrato

```json
{
  "id": "3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10",
  "firstName": "María José",
  "lastName": "Gómez-Ruiz",
  "birthDate": "1995-04-12",
  "phoneNumber": "+573001234567",
  "pronoun": "SHE",
  "status": "ACTIVE",
  "plan": "FREE"
}
```

- `birthDate` va en ISO-8601 (`yyyy-MM-dd`): es la forma estándar de una fecha en JSON y la del evento `cuenta.creada`; la presentación `dd/mm/aaaa`
  es de la interfaz (RT-07). El registro la recibe como `dd/mm/aaaa` porque así la escribe la persona.
- OpenAPI completo: `@Tag`, `@Operation`, `@Parameter` (`X-User-Id`, oculto para el cliente final porque lo pone el Gateway), `@ApiResponse` 200 (con
  ejemplo), 400, 404, 406 y 500 con `application/problem+json`; `@Schema` en cada campo del DTO (descripción, ejemplo, `nullable`, valores permitidos).
- Aviso a Frontend en `comunicaciones/10102026_v1_roles-objetivo-cupo-y-mi-cuenta-para-frontend.md` (el correo se toma del token de Firebase en el
  cliente: CA-1.5.1 lo muestra, pero HU-1.1 prohíbe devolverlo).

## 10. Seguridad

| Control | Aplica | Cómo | Prueba |
|---|---|---|---|
| ASVS 8.2.2 / OWASP API1 | Sí | Solo `X-User-Id`; parámetros de consulta ignorados | REQ-36-04 (E2E con dos cuentas) |
| ASVS 15.3.1 / OWASP API3 | Sí | DTO con 8 campos; sin correo, `firebaseUid` ni fechas internas | `getMe_shouldNotExposeInternalFields_whenAccountExists` (afirma el conjunto exacto de claves) |
| ASVS 14.2.1 | Sí | Ningún dato sensible en la URL; la identidad viaja en cabecera | revisión |
| ASVS 1.2.4 | Sí | Spring Data con parámetro (`findByFirebaseUid`) | `getMe_shouldReturn404_whenIdentityLooksLikeSql` |
| ASVS 4.1.1 | Sí | `charset=UTF-8` | `getMe_shouldReturnAccountFields_whenAccountExists` afirma el `Content-Type` |
| ASVS 14.3 / OWASP API8 | Sí | La respuesta 200 lleva `Cache-Control: no-store`: nombre, fecha de nacimiento y celular no se guardan en cachés del navegador ni de proxies compartidos | `getMe_shouldSendNoStore_whenAccountExists` |
| ASVS 8.2.1 / OWASP API5 | Sí | La ruta exige identidad del Gateway; el Gateway enruta `/api/v1/users/**` a Cuentas | 400 sin identidad |
| SEG-02 | Sí | dos cuentas A y B: B nunca ve datos de A | E2E de REQ-36-04 |
| SEG-04 / REST-0003 | Sí | Sin registro en el éxito; el rechazo registra `code` y `requestId` (el uid en el registro de rechazo es una mejora transversal **PENDIENTE de Paula**), nunca nombres, fecha ni celular | `BusinessExceptionHandlerTest` (existente) y revisión del diff |
| OWASP API4, API6, API7, API8, API9, API10 | No aplica / sin cambio | Lectura de una fila; sin flujo sensible, URL de entrada ni integración; ruta bajo `/api/v1/users` documentada en OpenAPI | — |

## 11. Atributos de calidad

| Atributo | Métrica | Límite | Cómo | Prueba |
|---|---|---|---|---|
| AC-0003 | SEG-01, SEG-02 | 100 % | sección 10 | E2E y Newman |
| AC-0003 | SEG-04 | 0 datos personales en logs | sin log de éxito | revisión y prueba del registro |
| REST-0003 | datos mínimos | solo los 8 campos de HU-1.1 | DTO | `getMe_shouldNotExposeInternalFields_whenAccountExists` |
| AC-0002 | DES-02 | p95 ≤ 2 s | una consulta por índice único | Newman `responseTime < 2000` |
| AC-0004 | FIA-01 | sin escritura | `readOnly` | `GetCurrentAccountServiceTest` (`verify(repository, never()).save(any())`) |
| AC-0006 | INT | `code` estable en 400 y 404 | catálogo | controlador |
| IOP-01 | OpenAPI | endpoint y DTO documentados | anotaciones | `OpenApiDocumentationTest` si existe; si no, `/v3/api-docs` en Newman (`M-09`) |
| MAN-01 | ≥ 90 % | lo nuevo y lo modificado | | JaCoCo |

## 12. Decisiones

| # | Decisión | Porqué | Alternativas descartadas |
|---|---|---|---|
| D36-1 | Registrar el trabajo en CM-297 | Es la subtarea creada para este endpoint (de CM-14, Sprint 2, 10-oct-2026); CM-42 no existe | Usar CM-36 (es la tarea de las validaciones del registro) o inventar una clave (prohibido) |
| D36-2 | `ANONYMIZED` → 404 `ACCOUNT_NOT_FOUND`; `DISABLED` → 200 | Una cuenta anonimizada ya no tiene datos personales que mostrar, y `docs/errores.md` ya decide 404 para la anonimizada en la activación; la deshabilitada es de su dueño y el Gateway la corta en el token | 404 para `DISABLED` (sin CA que lo pida) |
| D36-3 | `birthDate` en ISO-8601 | Estándar de JSON y del evento `cuenta.creada`; formato de pantalla es de la interfaz (RT-07) | `dd/mm/aaaa` (mezcla presentación y contrato) |
| D36-4 | Fecha desconocida se lee como `null` (D2) | Hoy una cuenta así responde 500 en toda lectura; la columna admite nulo y la spec de CM-279 acepta que existen | Rechazar con un código propio (la persona no puede corregirlo desde esta pantalla); migrar datos (no hay de dónde sacar la fecha) |
| D36-5 | Blanco o > 128 en `X-User-Id` → 400 `IDENTITY_REQUIRED` con una excepción de negocio nueva del mismo código | Valida la presencia (R2) con el mismo contrato que el encabezado ausente | 404 (trataría una entrada inválida como «no existe») |
| D36-6 | `Account.getBirthDate()` devuelve `Optional<BirthDate>`, como `getPhoneNumber` y `getPronoun` | El tipo dice que puede faltar; los dos llamadores se ajustan | Dejar `null` sin decirlo en el tipo |

## 13. Fuera de alcance y hallazgos con destino

| Tema | Destino |
|---|---|
| Correo en «Mi cuenta» (CA-1.5.1) | `cameia-web`, desde el token de Firebase (aviso a Frontend) |
| `PUT /api/v1/users/me` (HU-1.6), `DELETE` (HU-1.9), suscripción (HU-8.x) | Sus historias |
| La activación no valida `X-User-Id` en blanco y responde 400 en lugar de 401 sin identidad | CM-179 (la tarea que toca la activación); el 400 frente a 401 es una decisión ya enviada a Vela |
| `ACCOUNT_NOT_FOUND` sin punto final en su texto | Texto publicado: no se cambia sin decisión (se anota para Vela con el resto de textos) |

## 14. Pendientes (no bloquean)

| # | Qué | De quién | Qué depende |
|---|---|---|---|
| P36-2 | Newman completo contra el jar: exige el `.env` y el emulador de Firebase para arrancar la app | Paula Andrea Muñoz Delgado | Solo la evidencia de Newman; las pruebas automáticas no dependen de él |

## 15. Línea base, lista de verificación y dictamen

**Línea base:** `origin/develop` `69a1fb6`: `./mvnw.cmd -B clean verify` → BUILD SUCCESS, 713 pruebas, 0 fallos (9-oct-2026, `cuentas-specs-v6`).
La base de ejecución es la punta de `CM-279-postman-eventos-cuenta` (875 pruebas, 0 fallos, según la cola el 9-oct por la noche): `clean verify` ahí antes de la primera tarjeta.

| # | Comprobación | Comando o petición | Esperado |
|---|---|---|---|
| V1 | Contrato | `CurrentAccountControllerTest` | 8 claves exactas; `Content-Type` con charset |
| V2 | HU-1.1 de punta a punta | `CurrentAccountEndToEndTest.getMe_shouldReturnStoredAccount_whenRegistered` | 200 con los datos registrados |
| V3 | CA-1.5.2 | Newman `M-02` | `phoneNumber` `null` |
| V4 | CA-1.5.4 (adversarial: otra cuenta) | Newman `M-03` con `?firebase_uid={{uidOtra}}` | datos propios, nunca los de la otra |
| V5 | 404 | Newman `M-04` | 404 `ACCOUNT_NOT_FOUND` |
| V6 | Identidad (adversarial) | Newman `M-05`, `M-06` y `X-User-Id` de 129 caracteres | 400 `IDENTITY_REQUIRED` |
| V7 | Inyección (adversarial) | `X-User-Id: ' OR 1=1 --` | 404, sin 500 ni eco |
| V8 | D2 | `CurrentAccountEndToEndTest.getMe_shouldReturnBirthDateNull_whenLegacyAccountHasNoBirthDate` y la activación de esa cuenta (`AccountActivationEndToEnd` o prueba de servicio) | 200 en ambas, nunca 500 |
| V9 | Ningún 500 previsible | toda la carpeta de Newman | 0 respuestas 5xx |
| V10 | Catálogo | `ErrorCodeDocumentationTest`, `UntypedExceptionClassificationTest` | verde |
| V11 | Arquitectura | `LayeredArchitectureTest` | verde |
| V12 | R10 | JaCoCo | ≥ 90 % líneas y ramas en `GetCurrentAccountService`, `CurrentAccountController`, `CurrentAccountResponse`, `IdentityRequiredException`, `AccountMapper`, `Account` |
| V13 | R1/R8 y tamaño | sin `CM-` en código; `--shortstat` ≤ 800 | |

**Regla → evidencia prevista:** R1/R8 → V13 y OpenAPI; R2 → sección 5 y V6; R3 → sección 8 y V9; R4 → no aplica (sin migración; D2 corrige el
mapeo); R5 → sección 7; R6 → sección 10, V4 y V7; R7 → D36-5, D36-6 y V11; R9 → tarjetas; R10 → V12; R11 → T-36.6; R12 → sección 3; R13 → sección 11;
R14 → PENDIENTES en la sección 14, sin push.

**Dictamen: `LISTA PARA EJECUTAR`.** Contraste hecho con `AccountActivationController`, `ActivateAccountService`, `AccountNotFoundException`,
`BusinessExceptionHandler.peticionIncompleta` y `cuentaInexistente`, `AccountMapper:25-50`, `BirthDate`, `Account.rebuild`, `RegisteredUserResponse`
(plan `FREE`), `V1__esquema_inicial_cuenta.sql` y la colección `postman/cameia-cuentas.postman_collection.json` de la cadena.
