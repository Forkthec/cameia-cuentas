# Plan — CM-291 en `cameia-cuentas`: `POST /api/v1/auth/logout-all`

Requisitos: `spec.md` de esta carpeta, REQ-291-01 a REQ-291-09. La parte del Gateway tiene su propio plan en `cameia-gateway`.

## 1. Enfoque

Un caso de uso sin base de datos que llama a una operación nueva del puerto de Firebase. Todo lo demás se reutiliza:

| Pieza | Decisión | Por qué |
|---|---|---|
| Puerto `FirebaseUserDirectory` | **Extender** con `revokeSessions(String firebaseUid)` | Es el único punto que el dominio conoce de Firebase |
| `FirebaseUserDirectoryAdapter` | **Extender**; traduce con `unavailableOrRejection` (existente) | Misma clasificación de fallos que el resto de operaciones |
| `InMemoryFirebaseUserDirectory` (pruebas) | **Extender** con registro de revocaciones y dos interruptores de fallo | Regla del repo: se amplía el doble existente |
| `RevokeAllSessionsService` | **Crear** en `application/service` | Un caso de uso por clase; ningún servicio existente revoca |
| `SessionRevocationController` | **Crear** en `presentation/controller` | La ruta `/api/v1/auth/**` no pertenece a ningún controlador existente |
| Errores | **Reutilizar** `IdentityRequiredException`, `DependencyUnavailableException`, `BusinessExceptionHandler` | Cubren 400, 503 y 500; sin código nuevo en `ErrorCode` |
| Regla del identificador | **Reutilizar** `GetCurrentAccountService.MAX_FIREBASE_UID_LENGTH`, como `ActivateAccountService` | Precedente; la extracción va a CM-296 |

Sin `@Transactional`: no hay base de datos. Sin migración. Sin dependencias nuevas.

## 2. Archivos

| Archivo | Acción |
|---|---|
| `src/main/java/tech/cameia/cuentas/domain/port/FirebaseUserDirectory.java` | Modificar: método nuevo |
| `src/main/java/tech/cameia/cuentas/infrastructure/client/FirebaseUserDirectoryAdapter.java` | Modificar: implementación |
| `src/main/java/tech/cameia/cuentas/application/service/RevokeAllSessionsService.java` | Crear |
| `src/main/java/tech/cameia/cuentas/presentation/controller/SessionRevocationController.java` | Crear |
| `src/test/java/tech/cameia/cuentas/infrastructure/client/InMemoryFirebaseUserDirectory.java` | Modificar |
| `src/test/java/tech/cameia/cuentas/infrastructure/client/FirebaseUserDirectoryAdapterTest.java` | Modificar: 6 pruebas |
| `src/test/java/tech/cameia/cuentas/application/service/RevokeAllSessionsServiceTest.java` | Crear |
| `src/test/java/tech/cameia/cuentas/presentation/controller/SessionRevocationControllerTest.java` | Crear |
| `src/test/java/tech/cameia/cuentas/SessionRevocationEndToEndTest.java` | Crear |
| `postman/cameia-cuentas.postman_collection.json`, `postman/local.postman_environment.json`, `postman/README.md` | Modificar |
| `docs/errores.md`, `CLAUDE.md` | Modificar |
| `docs/bitacora-ia/11_cierre-de-sesion-en-todos-los-dispositivos_prompt.md`, `docs/bitacora-ia/README.md` | Crear y modificar (entrada de la decisión con peso humano) |

No se tocan: `ErrorCode`, `BusinessExceptionHandler`, migraciones, `pom.xml`, `.github/`.

## 3. Orden y commits (un PR, un commit por capacidad)

| Commit | Tarjeta | Mensaje |
|---|---|---|
| 1 | C-1 | `CM-291 \| feat(cuentas): revocar las sesiones de un usuario en Firebase` |
| 2 | C-2 | `CM-291 \| feat(cuentas): caso de uso para cerrar sesión en todos los dispositivos` |
| 3 | C-3 | `CM-291 \| feat(cuentas): POST /api/v1/auth/logout-all responde 204` |
| 4 | C-4 | `CM-291 \| test(cuentas): prueba de extremo a extremo del cierre de sesión` |
| 5 | C-5 | `CM-291 \| test(cuentas): colección de Postman del cierre de sesión` |
| 6 | C-6 | `CM-291 \| docs(cuentas): catálogo de errores, CLAUDE.md y bitácora del cierre de sesión` |

C-0 (línea base) no genera commit. Cada commit compila y deja verdes las pruebas del módulo tocado. Al final, una vez sobre la punta:
`clean verify`, cobertura y Newman (C-7).

## 4. Riesgos

| Riesgo | Mitigación |
|---|---|
| El script global de la colección exige `Content-Type` con charset y el 204 no lleva `Content-Type` | C-5 exceptúa el 204 en el script global (trampa verificada en `origin/develop`) |
| `UntypedExceptionClassificationTest` marca el método nuevo | `revokeSessions` solo lanza lo que devuelve `unavailableOrRejection`, ya clasificado; si aun así falla, C-1 dice qué entrada agregar |
| El emulador de Firebase no expone `validSince` como se espera | C-5 tiene una alternativa sin esa comprobación y lo reporta |

## 5. Matriz de pruebas

Está en las tarjetas, con nombre exacto y valores literales; la correspondencia de cada criterio con su prueba y su petición está en
las secciones 8, 10 y 15 de la spec.

## 6. Integración con otras tareas del repositorio

- Sin migración: la siguiente libre sigue siendo `V8`.
- `ErrorCode` no cambia. `docs/errores.md` cambia solo en las columnas «Endpoints» de `IDENTITY_REQUIRED`, `DEPENDENCY_UNAVAILABLE`,
  `METHOD_NOT_ALLOWED` e `INTERNAL_ERROR`, y en una fila de «Respuestas publicadas que difieren del estándar». CM-290 (503 de base de
  datos) y CM-296 (alineación) tocan el mismo archivo más tarde: el conflicto previsible es de una celda y se resuelve al rebasar.
- Colección de Postman: la carpeta nueva va al final del arreglo `item`; el cambio del script global es de una línea.
- Rama `CM-291-cerrar-sesion-en-todos-los-dispositivos` desde `origin/develop`. No depende de otra tarea sin fusionar: no se apila.
- El PR del Gateway es independiente y puede fusionarse antes o después; el flujo completo necesita los dos desplegados.
