# Plan — CM-251-RegistroRepetido

Base: `develop` después de fusionar los PR 1A a 3 de CM-36 (se revalidan rutas y líneas al empezar; las que se citan son de `908112c`). Estado: pendiente de aprobación de Paula. Un solo PR (≈ 600 líneas con pruebas, ≈ 5 h).

## 1. Cómo se aborda

1. **Puerto:** `FirebaseUserDirectory.findUidByEmail(EmailAddress)` devuelve `Optional<String>` (vacío si Firebase no tiene al usuario). El adaptador usa `FirebaseAuth.getUserByEmail`; `USER_NOT_FOUND` es vacío y cualquier otro error es `IllegalStateException` (500 genérico).
2. **Servicio:** `RegisterUserService.register` conserva las validaciones y pasa a devolver un `RegisterUserResult(cuenta, creada)`. Después de validar consulta el correo: si existe resuelve S2/S3/S4; si no, crea (camino actual, extraído a un método privado para no pasar de ≈ 20 líneas). Si `createUser` falla por «correo ya existe» (carrera), vuelve a consultar una vez y resuelve igual (REQ-RR-06).
3. **Controlador:** responde 201 o 200 según `creada` con el mismo cuerpo; documenta ambas respuestas.
4. **Doble de pruebas:** `InMemoryFirebaseUserDirectory` gana `findUidByEmail`, una bandera para fallar al consultar y seguridad entre hilos (mapas concurrentes y `createUser` sincronizado) para la prueba de concurrencia.
5. **V-02** se reproduce en local sin cambiar código del repositorio.

## 2. Archivos (paquete base `src/main/java/tech/cameia/cuentas`)

| Acción | Archivo | Qué |
|---|---|---|
| Modificar | `domain/port/FirebaseUserDirectory.java` | Método `findUidByEmail` |
| Modificar | `infrastructure/client/FirebaseUserDirectoryAdapter.java` | Implementación con `getUserByEmail` |
| Crear | `application/service/RegisterUserResult.java` | `record RegisterUserResult(Account account, boolean created)` |
| Modificar | `application/service/RegisterUserService.java` | Consulta previa, S2 a S4, carrera, resultado |
| Modificar | `presentation/controller/UserRegistrationController.java` | 201 o 200; OpenAPI |
| Modificar (pruebas) | `src/test/.../infrastructure/client/InMemoryFirebaseUserDirectory.java` | `findUidByEmail`, bandera de fallo, hilos |
| Modificar (pruebas) | `RegisterUserServiceTest`, `UserRegistrationControllerTest`, `FirebaseUserDirectoryAdapterTest`, `AccountRegistrationEndToEndTest` | Ver `tasks.md` |
| Crear (pruebas) | `src/test/.../RegistroConcurrenteEndToEndTest.java` | Dos hilos, 20 repeticiones, base real |
| No se tocan | `domain/model/**`, `infrastructure/persistence/**`, migraciones, `pom.xml`, `.github/**` | |

## 3. Reutiliza / por qué se crea algo

Se reutilizan: `AccountRepository.findByFirebaseUid` (ya existe), `Account.isPendingVerification()`, `EmailAlreadyRegisteredException` (409 y su código), `compensar`, el manejador de errores y `ProblemDetailTestSupport`, `FirebaseTestConfiguration`. Se crea solo `RegisterUserResult`: el servicio no puede decir «200 o 201» sin un resultado propio, y devolver `Account` mezclaría un dato de presentación en el dominio.

## 4. Decisiones técnicas y descartes

Spec, sección 11 (D1 a D5). El plan agrega: el resultado es un `record` de `application` (no de `domain`), `RegisterUserService` no recibe nuevas dependencias (ya tiene el puerto de Firebase y el repositorio), y no se agrega `@Transactional` al método (la creación en Firebase no es transaccional, igual que hoy).

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| El emulador devuelve un código distinto de `USER_NOT_FOUND` para `getUserByEmail` inexistente | T-5 lo prueba con el adaptador simulado y T-9 (V-02) con el emulador real; si difiere, se detiene y se reporta |
| La prueba de concurrencia es inestable | 20 repeticiones con barrera (`CountDownLatch`) y aserciones sobre el **conjunto** de resultados admitidos, nunca sobre cuál hilo gana |
| El doble en memoria no es seguro entre hilos | T-1 lo corrige con `ConcurrentHashMap` y `synchronized` en `createUser`; la clase solo se usa en pruebas |
| Conflicto de fusión con CM-36 (mismo servicio y manejador) | Este PR sale **después** de los de CM-36; si alguno sigue abierto, se rebasa y se revalidan las líneas de las tarjetas |
| Cambio de la prueba existente `elSegundoRegistroConElMismoCorreoRespondeConflicto` (ahora 200) | Se reescribe en T-6 y se anuncia a Frontend: el reintento ya no da 409 |

## 6. Matriz de pruebas

| Prueba | Capa | Requisito |
|---|---|---|
| S1: correo nuevo → resultado `creada=true`, un usuario, una fila | servicio | REQ-RR-01 |
| S2: credencial y fila pendiente → `creada=false`, misma cuenta, sin `createUser`/`assignFreePlanClaim`/`deleteUser`, sin guardar | servicio | REQ-RR-02, 08 |
| S3: `ACTIVE`, `DISABLED`, `ANONYMIZED` → `EmailAlreadyRegisteredException`; mismo mensaje y código en los tres | servicio | REQ-RR-03 |
| S4: credencial sin fila → `EmailAlreadyRegisteredException` y línea `ERROR` con el `firebase_uid` y sin correo | servicio | REQ-RR-04, 12 |
| S5: la consulta falla → `IllegalStateException`, nada creado | servicio | REQ-RR-05 |
| Carrera: `createUser` falla por correo existente y la segunda consulta devuelve la cuenta pendiente → 200; sin fila → 409 con `WARN`; sin credencial → 409 | servicio | REQ-RR-06 |
| S7: cuerpo inválido con correo existente → 422 y ninguna llamada a Firebase | servicio | REQ-RR-07 |
| Dos repeticiones seguidas → misma cuenta | servicio | REQ-RR-10 |
| `findUidByEmail`: encontrado, `USER_NOT_FOUND` → vacío, otro error → `IllegalStateException` sin el correo en el mensaje | adaptador | REQ-RR-05, 12 |
| 201 y 200 con el mismo cuerpo; 409 con `code` | controlador | REQ-RR-01, 02, 03 |
| Repetido → 200 con el mismo `id`; una fila; `fecha_creacion` y `fecha_actualizacion` iguales; un usuario en el directorio | punta a punta (Docker) | REQ-RR-02, 10 |
| Repetido con cuenta `ACTIVE`, `DISABLED` (por SQL) → 409 idéntico; credencial sin fila → 409 | punta a punta | REQ-RR-03, 04 |
| 20 pares simultáneos → un 201 por par y el otro 200 o 409; nunca 500; una credencial y una fila | punta a punta | REQ-RR-06 |
| Firebase falla al consultar → 500 `INTERNAL_ERROR`, sin fila | punta a punta | REQ-RR-05 |
| Verificación manual de OpenAPI y de V-02 | manual | REQ-RR-11; sección 10 de la spec |

## 7. Orden y dependencias

T-1 → T-2 → T-3 → T-4 y T-5 (independientes) → T-6 → T-7 → T-8 → T-9 (V-02 puede ir en paralelo con T-4 a T-7) → T-10. **BLOQUEADA por la pregunta 1** solo la forma del cuerpo del 200 (T-2/T-3: si Paula decide otra cosa, cambia `RegisteredUserResponse`). El nivel del registro de S4 (pregunta 2) cambia una línea. Depende de que los PR 1A a 3 de CM-36 estén fusionados (códigos de error y servicio actual).

## 8. Estimación

≈ 5 h (spec, sección 14), ≈ 600 líneas, un PR. Sin bloqueos de otras personas.
