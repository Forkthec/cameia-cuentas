# Plan — CM-251-RegistroRepetido

Base: `develop` (`e5e17e4`, CM-36 completo fusionado). Rutas y líneas se revalidan al empezar. Estado: pendiente de aprobación de Paula. Dos PR: **A** (código, ≈ 750 líneas, ≈ 6 h) y **B** (Postman y V-02, ≈ 450 líneas, ≈ 2 h), B sobre A. Subir ramas o abrir PR se pregunta a Paula cada vez.

## 1. Cómo se aborda

1. **Puerto:** `FirebaseUserDirectory.findByEmail(EmailAddress)` devuelve `Optional<DirectoryUser>`; `DirectoryUser` es un `record` de `domain/model` con `uid`, `createdAt` (`Instant`) y `disabled`. El adaptador usa `FirebaseAuth.getUserByEmail`: `USER_NOT_FOUND` es vacío; los demás errores pasan por la misma clasificación que `createUser` (indisponibilidad → `DependencyUnavailableException`; `INVALID_EMAIL` → `InvalidEmailException`; el resto → `IllegalStateException`, 500 de respaldo). La clasificación de `INVALID_EMAIL` se extrae a un método privado compartido con `createUser`.
2. **Servicio:** `RegisterUserService.register` conserva las validaciones y devuelve `RegisterUserResult(account, created)`. Después de validar consulta el correo: si existe resuelve S2/S3/S4/S8 con un método privado; si no, crea (camino actual, extraído a otro método privado). Si `createUser` falla por «correo ya existe», vuelve a consultar una vez y resuelve igual. Recibe un `Clock` (segundo constructor, como `AgePolicy`) para clasificar la antigüedad de la credencial sin fila.
3. **Manejador y excepción:** el 409 agrega `errors[{field:"email", code, message}]`, con el helper que ya arma la lista de la validación. `EmailAlreadyRegisteredException` lleva el `firebaseUid` interno y si requiere conciliación; el manejador registra **una** línea con `code`, `requestId` y `uid`, en `WARN` o `ERROR`. El servicio no registra el caso.
4. **Controlador:** 201 o 200 según `created`, mismo cuerpo; OpenAPI completo (200, 201, 409, 422, 500, 503, con ejemplos).
5. **Tiempos de espera:** `FirebaseConfiguration` pasa a 3 s y 5 s; la prueba compara los valores literales.
6. **Doble de pruebas:** `InMemoryFirebaseUserDirectory` gana `findByEmail`, creación con fecha controlable, usuario deshabilitado, contadores de llamadas, banderas de fallo y seguridad entre hilos.
7. **Documentación:** `docs/errores.md` y un ADR (siguiente número libre en `docs/adr/`) para el cambio aditivo.
8. **PR B:** `postman/` y el informe de V-02, sin código de producción.

## 2. Archivos (paquete base `src/main/java/tech/cameia/cuentas`)

| PR | Acción | Archivo | Qué |
|---|---|---|---|
| A | Crear | `domain/model/DirectoryUser.java` | Record con `uid`, `createdAt`, `disabled` |
| A | Modificar | `domain/port/FirebaseUserDirectory.java` | Método `findByEmail` |
| A | Modificar | `infrastructure/client/FirebaseUserDirectoryAdapter.java` | `findByEmail`, clasificación compartida y `RESOURCE_EXHAUSTED` como indisponibilidad |
| A | Modificar | `domain/exception/EmailAlreadyRegisteredException.java` | Segundo constructor con `firebaseUid` y bandera de conciliación |
| A | Modificar | `infrastructure/config/FirebaseConfiguration.java` | 3 s y 5 s; Javadoc |
| A | Crear | `application/service/RegisterUserResult.java` | `record RegisterUserResult(Account account, boolean created)` |
| A | Modificar | `application/service/RegisterUserService.java` | Consulta previa, S2/S3/S4/S8, carrera, reloj |
| A | Modificar | `presentation/controller/UserRegistrationController.java` | 201 o 200; OpenAPI |
| A | Modificar | `presentation/advice/BusinessExceptionHandler.java` | `errors[]` en el 409 |
| A | Modificar | `docs/errores.md`; crear `docs/adr/NNNN-registro-repetido-y-errors-del-409.md` | Catálogo y decisión |
| A | Modificar (pruebas) | `InMemoryFirebaseUserDirectory`, `RegisterUserServiceTest`, `UserRegistrationControllerTest`, `BusinessExceptionHandlerTest`, `FirebaseUserDirectoryAdapterTest`, `FirebaseConfigurationTest`, `AccountRegistrationEndToEndTest`, `UntypedExceptionClassificationTest` (si hace falta) | Ver `tasks.md` |
| A | Crear (pruebas) | `src/test/.../RegistroConcurrenteEndToEndTest.java` | Dos hilos, 20 repeticiones, base real |
| B | Crear | `postman/cameia-cuentas.postman_collection.json`, `postman/local.postman_environment.json`, `postman/README.md` | Colección v2.1 en UTF-8 y entorno sin secretos |
| B | Crear | `docs/verificaciones/V-02-registro-interrumpido.md` | Informe de V-02 con tiempos medidos |
| — | No se tocan | `domain/model/Account.java`, `infrastructure/persistence/**`, migraciones, `pom.xml`, `.github/**` | |

## 3. Reutiliza / por qué se crea algo

Se reutilizan: `AccountRepository.findByFirebaseUid`, `Account.isPendingVerification()` y `getStatus()`, `EmailAlreadyRegisteredException`, `DependencyUnavailableException`, `InvalidEmailException`, `compensar`, el helper `validacion`/`campo` del manejador, `ProblemDetailTestSupport`, `FirebaseTestConfiguration`. Se crean: `RegisterUserResult` (el servicio no puede decir «200 o 201» sin un resultado propio, y devolver `Account` mezclaría un dato de presentación en el dominio) y `DirectoryUser` (el servicio necesita tres datos de la credencial, no solo el `uid`: devolver tres consultas separadas sumaría llamadas externas).

## 4. Decisiones técnicas y descartes

Spec, sección 13 (D1 a D13). El plan agrega: el resultado es un `record` de `application`; el reloj se inyecta por constructor y no hay `@Transactional` en el método (la creación en Firebase no es transaccional); el parámetro booleano de la carrera se reemplaza por un `enum Origen` privado si el método supera tres parámetros.

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| El emulador devuelve un código distinto de `USER_NOT_FOUND` para un correo inexistente, o distingue mayúsculas | La tarjeta T-1 lo prueba con el emulador real (`FirebaseRejectionsWithSdkTest` ya usa el SDK con servidor simulado); si difiere, se detiene y se reporta |
| `getUserByEmail` de Firebase real responde distinto al emulador con correos como `ana..perez@correo.co` | Lo cubre la clasificación de `INVALID_EMAIL`; se pide confirmar con Firebase real en staging (mismo procedimiento que CM-36) |
| La prueba de concurrencia es inestable | 20 repeticiones con barrera (`CountDownLatch`) y aserciones sobre el **conjunto** de resultados admitidos, nunca sobre cuál hilo gana |
| El doble en memoria no es seguro entre hilos | T-1 usa mapas concurrentes y `createUser` sincronizado; la clase solo se usa en pruebas |
| `UntypedExceptionClassificationTest` falla por el método nuevo | Se reutiliza `unavailableOrRejection` (ya clasificado); si la prueba pide clasificar `findByEmail`, se agrega con su motivo |
| Conflicto de fusión con CM-179 bloque 1 (retira `isEmailVerified` del puerto y toca el manejador) | El segundo en fusionarse se rebasa; se revalidan las líneas de las tarjetas |
| Cambio de la prueba existente `elSegundoRegistroConElMismoCorreoRespondeConflicto` (ahora 200) | Se reescribe en T-6 y se anuncia a Frontend |
| Tiempos de 3 s y 5 s afectan a todas las llamadas a Firebase (activación incluida) | Es lo buscado (spec, sección 9); `FirebaseConfigurationTest` fija los valores y la suite completa los ejerce |

## 6. Matriz de pruebas

| Prueba | Capa | Requisito |
|---|---|---|
| S1: correo nuevo → `created=true`, un usuario, una fila | servicio | REQ-RR-01 |
| S2: credencial habilitada y fila pendiente → `created=false`, misma cuenta, sin `createUser`/`assignFreePlanClaim`/`deleteUser`, sin guardar | servicio | REQ-RR-02, 08 |
| S2 con datos distintos en el cuerpo → misma cuenta, fila intacta | servicio, punta a punta | REQ-RR-13 |
| S3: `ACTIVE`, `DISABLED`, `ANONYMIZED` → `EmailAlreadyRegisteredException`, mismo mensaje en los tres | servicio | REQ-RR-03 |
| S8: usuario deshabilitado con fila pendiente → `EmailAlreadyRegisteredException` | servicio | REQ-RR-03 |
| S4: credencial sin fila de 299 s → excepción sin conciliación; de 300 s y 301 s → con conciliación | servicio | REQ-RR-04 |
| S4: una sola línea de log del manejador con `code`, `requestId` y `uid` (`WARN` o `ERROR`), sin correo | manejador | REQ-RR-04, 12 |
| S5: la consulta falla por indisponibilidad → `DependencyUnavailableException`, nada creado | servicio, adaptador | REQ-RR-05 |
| `INVALID_EMAIL` al consultar → `InvalidEmailException`; cuota agotada → `DependencyUnavailableException`; otro rechazo → `IllegalStateException` sin el correo | adaptador | REQ-RR-05 |
| Carrera: `createUser` falla por correo existente y la segunda consulta devuelve la cuenta pendiente → 200; sin fila → 409 con `WARN`; sin credencial → 409; un solo `createUser` | servicio | REQ-RR-06 |
| S7: cuerpo inválido con correo existente → 422 y 0 consultas | servicio | REQ-RR-07 |
| La contraseña no aparece en el log del registro repetido | servicio | REQ-RR-08, 12 |
| Dos repeticiones seguidas → misma cuenta y mismo cuerpo | servicio, punta a punta | REQ-RR-10 |
| 201 y 200 con el mismo cuerpo; 409 con `code` y `errors[0].field = email` | controlador, manejador | REQ-RR-01, 02, 03, 14 |
| Tiempos de espera de 3 000 y 5 000 ms | configuración | REQ-RR-15 |
| Un servidor mudo hace que la consulta falle por tiempo agotado → 503 | adaptador con el SDK | REQ-RR-05, 15 |
| Un registro y 20 repeticiones → 201 y 20 veces 200 con el mismo `id`; una fila; fechas y `version` iguales; un usuario (FIA-02) | punta a punta (Docker) | REQ-RR-02, 10 |
| Repetido con cuenta `ACTIVE` y `DISABLED` (por SQL) → 409 idéntico; credencial sin fila → 409; usuario deshabilitado → 409 | punta a punta | REQ-RR-03, 04 |
| 20 pares simultáneos → un 201 por par y el otro 200 o 409; nunca 500; una credencial y una fila | punta a punta | REQ-RR-06 |
| Firebase falla al consultar → 503 `DEPENDENCY_UNAVAILABLE`, sin fila | punta a punta | REQ-RR-05 |
| Mayúscula no ASCII, mismo correo → 200 | emulador | REQ-RR-02 |
| Verificación de OpenAPI y del catálogo | manual y `ErrorCodeDocumentationTest` | REQ-RR-11, 17 |
| Colección de Postman con Newman | PR B | REQ-RR-16 |

## 7. Orden y dependencias

**PR A:** T-1 → T-2 → T-3 → T-4 y T-5 (independientes) → T-6 → T-7 → T-8 → T-9. **PR B:** T-10 → T-11 → T-12. T-10 (V-02) puede hacerse en paralelo con T-4 a T-7. Depende de CM-36 fusionado (ya lo está). Preguntas abiertas: spec, sección 14 (no bloquean el código).

## 8. Estimación

≈ 8 h en total (PR A ≈ 6 h, PR B ≈ 2 h). Al terminar se vuelve a estimar con las horas reales y se informa a Vela.
