# Plan — CM-179-VerificacionCorreo (cameia-cuentas)

Base: `develop` después de fusionar los PR 1A a 3 de CM-36 y el PR de CM-251 (pregunta 13 de la spec; se revalidan rutas y líneas al empezar: las que se citan son de `908112c`). Estado: pendiente de aprobación de Paula. Este plan contiene el **bloque 1** (activación robusta, un PR, ≈ 4 h, ≈ 450 líneas con pruebas). Los planes de los bloques 2 (purga) y 4 (modo de tarea) se agregan antes de ejecutarlos; el bloque 3 (Gateway) tiene su plan en `cameia-gateway/specs/CM-179-VerificacionCorreo/`.

## 1. Cómo se aborda el bloque 1

1. **Dominio:** tres excepciones de negocio nuevas con su código (`AccountDisabledException`, `AccountConflictException`, `InvalidIdentityException`); `Account.activate()` deja de lanzar `IllegalStateException`: `DISABLED` lanza `AccountDisabledException` y `ANONYMIZED` lanza `AccountNotFoundException` (no revela que existió).
2. **Persistencia:** `AccountRepositoryAdapter.save` traduce el conflicto de versión de JPA a `AccountConflictException`, de modo que la capa de aplicación no conoce JPA.
3. **Aplicación:** `ActivateAccountService` pierde su dependencia de Firebase y su `@Transactional` (decisión D3). Orden: validar la identidad → buscar la cuenta (404) → exigir el token verificado (403) → si ya está `ACTIVE`, devolverla sin escribir → activar y guardar; si el guardado pierde la carrera, releer y devolver 200 si ya está `ACTIVE`, o 409.
4. **Presentación:** el controlador recibe `X-User-Email-Verified` como texto y solo `true` cuenta como verificado (cualquier otro valor, incluido `abc`, es «no verificado»: nunca un 500 por conversión de tipo); el manejador traduce las tres excepciones nuevas.
5. **Documentación:** OpenAPI de la ruta con todas sus respuestas.

## 2. Archivos (paquete base `src/main/java/tech/cameia/cuentas`)

| Acción | Archivo | Qué |
|---|---|---|
| Crear | `domain/exception/AccountDisabledException.java`, `AccountConflictException.java`, `InvalidIdentityException.java` | Excepciones con su `ErrorCode` |
| Modificar | `domain/exception/ErrorCode.java` | `ACCOUNT_DISABLED`, `ACCOUNT_CONFLICT`, `IDENTITY_INVALID_VALUE` |
| Modificar | `domain/exception/EmailNotVerifiedException.java` | Texto «Tu correo aún no está verificado.» (pregunta 3) |
| Modificar | `domain/model/Account.java` | `activate()` con excepciones tipadas |
| Modificar | `infrastructure/persistence/repository/AccountRepositoryAdapter.java` | Traduce el conflicto de versión |
| Modificar | `application/command/ActivateAccountCommand.java` | `(String firebaseUid, boolean emailVerified)` |
| Modificar | `application/service/ActivateAccountService.java` | Token estricto; sin Firebase; sin `@Transactional`; conflicto |
| Modificar | `presentation/controller/AccountActivationController.java` | Encabezado como texto; OpenAPI |
| Modificar | `presentation/advice/BusinessExceptionHandler.java` | Tres manejadores nuevos |
| Pruebas | `ActivateAccountServiceTest`, `AccountTest`, `AccountActivationControllerTest`, `BusinessExceptionHandlerTest`, `AccountRepositoryAdapterTest`, `AccountRegistrationEndToEndTest` | Ver `tasks.md` |
| No se tocan | `FirebaseUserDirectory` y su adaptador (el método `isEmailVerified` sigue existiendo; lo reemplaza el bloque 2), `pom.xml`, migraciones, `.github/**` | |

## 3. Reutiliza / por qué se crea algo

Se reutilizan `BusinessException`, `EmailNotVerifiedException`, `AccountNotFoundException`, `ErrorCode`, el manejador y sus ayudantes `problema` y `campo`, `ProblemDetailTestSupport`, el repositorio en memoria de `ActivateAccountServiceTest`, `AccountRegistrationEndToEndTest` y los dobles de Firebase. Se crean tres excepciones porque no existe ninguna para esos casos y cada una necesita un código y un estado propios; `InvalidIdentityException` no puede ser `IllegalArgumentException` (ese manejador desaparece en CM-36, PR 6).

## 4. Decisiones técnicas y alternativas descartadas

Spec, sección 11 (D1 a D7). El plan agrega: el conflicto de versión solo puede ocurrir en un servicio **sin** transacción propia (con `@Transactional` el fallo sale al confirmar, fuera del método); como `AccountRepositoryAdapter.save` vuelve a leer la fila antes de guardar, el conflicto real solo ocurre si otra escritura cambia la fila entre su lectura y su guardado; por eso la prueba determinista del conflicto usa un repositorio simulado y la de punta a punta solo exige «nunca 500».

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| Quitar `@Transactional` cambia el comportamiento de lectura/escritura | Solo hay una escritura (guardar); las lecturas no requieren transacción (`open-in-view=false`); la prueba E2E de activación completa lo cubre |
| Un cliente que hoy envía `X-User-Email-Verified: false` y confía en Firebase deja de activar | Es el defecto que corrige CA-1.2.6; el Gateway siempre envía el valor del token; aviso a Frontend |
| Cambio de texto del 403 (pregunta 3) rompe pruebas de Frontend | Aditivo en código, cambio de texto avisado |
| El conflicto de versión se traduce también cuando lo causa otra escritura distinta de la activación | `save` solo se usa en registro y activación; ambos aceptan el 409 reintentable |
| `Account.activate()` cambia de excepción: otros llamadores | `git grep -n "\.activate()"`: solo el servicio de activación; la purga (bloque 2) lo usará con el mismo contrato |

## 6. Matriz de pruebas del bloque 1

| Prueba | Capa | Requisito |
|---|---|---|
| Activa con `true`; ya activa no guarda (`guardados` no crece); sin cuenta → `AccountNotFoundException` | servicio | REQ-VC-02, 03, 04 |
| `DISABLED` → `AccountDisabledException`; `ANONYMIZED` → `AccountNotFoundException`, el mismo mensaje que sin cuenta | servicio + dominio | REQ-VC-05, 06 |
| Encabezado `false`: `EmailNotVerifiedException` y la cuenta sigue `PENDING_VERIFICATION`; el doble de Firebase no recibe ninguna llamada | servicio | REQ-VC-01, 09 |
| `X-User-Id` vacío, solo espacios, 128 (pasa) y 129 (`InvalidIdentityException`); la base no se consulta | servicio | REQ-VC-07 |
| Conflicto de versión: el repositorio simulado lanza `AccountConflictException` en el primer guardado y devuelve la cuenta `ACTIVE` en la relectura → 200; si sigue `PENDING_VERIFICATION` → `AccountConflictException` | servicio | REQ-VC-08 |
| El adaptador traduce `ObjectOptimisticLockingFailureException` a `AccountConflictException` | adaptador | REQ-VC-08 |
| Controlador: `true` → 200 y cuerpo `{id, status:"ACTIVE"}`; `false`, ausente, vacío, `abc`, `TRUE` → 403 `EMAIL_NOT_VERIFIED`; cuerpo `{"emailVerified":true}` ignorado; `X-User-Id` ausente → 400 `IDENTITY_REQUIRED`; `DISABLED` → 403 `ACCOUNT_DISABLED`; conflicto → 409 `ACCOUNT_CONFLICT`; identidad inválida → 400 `IDENTITY_INVALID_VALUE` | presentación | REQ-VC-01 a 08 |
| E2E (Docker): activar cambia `estado` y `fecha_actualizacion`; activar de nuevo no cambia `fecha_actualizacion` ni `version`; `DISABLED` y `ANONYMIZED` por SQL; dos activaciones simultáneas → 200 y 200 y `version` aumentó una sola vez; Firebase fallando no afecta la activación con `true` | integración | REQ-VC-02, 03, 05, 06, 08, 09 |
| Los registros de log de rechazo no contienen correo ni contraseña | servicio | REQ-VC-10 |
| Verificación manual de OpenAPI | manual | REQ-VC-11 |

## 7. Orden y dependencias

T-1.1 → T-1.2 → T-1.3 → T-1.4 → T-1.5 → T-1.6 → T-1.7. **BLOQUEADAS:** el texto de `EmailNotVerifiedException` por la pregunta 3; los textos y estados de `DISABLED` y `ANONYMIZED` por la 2; T-1.2 y T-1.3 (quitar `@Transactional` y traducir el conflicto) por la 5. Depende de que CM-36 (1A a 3) y CM-251 estén fusionados (pregunta 13).

## 8. Estimación

≈ 4 h, ≈ 450 líneas, un PR. Los bloques 2, 3 y 4 se estiman en la spec (sección 15).
