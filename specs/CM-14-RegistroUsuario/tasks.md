# Tareas — CM-14-RegistroUsuario

- **Spec:** [spec.md](spec.md) · **Plan:** [plan.md](plan.md)
- **Fecha:** 17/09/2026
- **Regla:** cada tarea se hace en 30 minutos o menos y se marca `[x]` antes de empezar la siguiente.
  Cada bloque termina con `./mvnw.cmd test` en verde.

---

## Bloque 0 — Previo: la sonda de salud alcanzable desde el Gateway

> No depende de nada de CM-14 y se hace primero. Motivo en [plan.md](plan.md) §0 y en
> [CONTRATO-GATEWAY-CM-14.md](../../CONTRATO-GATEWAY-CM-14.md) §3.

- [x] **T-00** Mover la sonda de `GET /health` a `GET /api/v1/users/health` en `HealthController` y ajustar `HealthControllerTest` (mismo cuerpo, mismos códigos, `POST` sigue dando `405`).
- [x] **T-01** Actualizar el `HEALTHCHECK` del `Dockerfile`, y el de `docker-compose.yml` si lo declara, a la ruta nueva. Verificar con `docker compose up --build` y `docker ps` mostrando `healthy`. Verificado el 17/09/2026: `docker ps` muestra `cameia-cuentas Up (healthy)`, `GET /api/v1/users/health` responde `200` con `{"status":"UP"}` y la ruta vieja `/health` responde `404`. `docker-compose.yml` no declara `healthcheck` para la aplicación, así que solo cambió el `Dockerfile`.
- [x] **T-02** Actualizar [CM-103-EndpointSalud/spec.md](../CM-103-EndpointSalud/spec.md), el `README.md` y la §3 del contrato del Gateway con la ruta nueva.

## Bloque 1 — Cimientos: dependencias, esquema y configuración

- [x] **T-03** Declarar en `pom.xml` `firebase-admin`, `spring-boot-starter-flyway`, `flyway-database-postgresql` y `spring-boot-starter-validation`. `./mvnw.cmd clean package` en verde.
- [x] **T-04** Declarar `spring-boot-testcontainers`, `testcontainers-postgresql` y `testcontainers-junit-jupiter` en alcance `test`.
- [x] **T-05** Escribir `V1__esquema_inicial_cuenta.sql` con el esquema `microcuentas` y la tabla `cuenta`: columnas del DDL, sin extensiones ni triggers (`REQ-CU-15`).
- [x] **T-06** Agregar a esa migración las restricciones de `REQ-CU-16` y `ck_cuenta_estado` con `PENDING_VERIFICATION` incluido y como `DEFAULT` (CU-1).
- [x] **T-07** Pasar `ddl-auto` a `validate` en `application.properties`, quitar el `create` de `application-local.properties` y declarar el esquema por defecto y las propiedades de Flyway.
- [x] **T-08** Prueba de integración mínima: Testcontainers levanta PostgreSQL 16, Flyway aplica `V1` y el contexto arranca con `validate`. Escrita como `CuentaSchemaMigrationTest`, con cinco casos que también cubren `REQ-CU-16`. Verde con Docker arriba; se omite sin Docker (`disabledWithoutDocker`) para que `./mvnw.cmd test` siga siendo ejecutable sin contenedores.

## Bloque 2 — Dominio

- [x] **T-09** `AccountStatus` con los cuatro estados y `Pronoun` con sus tres valores.
- [x] **T-10** Objetos de valor `EmailAddress`, `PhoneNumber` (E.164), `BirthDate` y `RawPassword`, este último sin exponerse en `toString()`.
- [x] **T-11** `AgePolicy` con las cinco reglas de CA-1.1.3 y la edad calculada en UTC (`REQ-CU-08` a `REQ-CU-10`).
- [x] **T-12** `AgePolicyTest`: cumple 18 hoy, los cumple mañana, fecha futura, más de 110 años y fecha inválida.
- [x] **T-13** `PasswordPolicy` según OWASP ASVS y `PasswordPolicyTest` con los límites 11, 12, 64 y 65 caracteres (`REQ-CU-11b`). Incluye la lista de contraseñas conocidas que cumplen la longitud, con sus tres pruebas.
- [x] **T-14** Agregado `Account`: fábrica que nace `PENDING_VERIFICATION` y `activate()` con las transiciones válidas (`REQ-CU-04`, `REQ-CU-13`, `REQ-CU-14`).
- [x] **T-15** `AccountTest`: estado inicial, activación, activación repetida y rechazo desde `DISABLED` y `ANONYMIZED`.
- [x] **T-16** Excepciones de dominio con sus mensajes en español y `InvalidBirthDateException` con su motivo. Hechas `BusinessException`, `InvalidBirthDateException` y `WeakPasswordException`. `EmailAlreadyRegisteredException` llegó con el puerto de Firebase. `EmailNotVerifiedException` y `AccountNotFoundException` van con el bloque 5, que es donde se lanzan.
- [x] **T-17** Puertos `AccountRepository` y `FirebaseUserDirectory`.

## Bloque 3 — Persistencia

- [x] **T-18** `AccountEntity` mapeada a `microcuentas.cuenta`, con columnas en español y `@Version`.
- [x] **T-19** `AccountMapper` entre `Account` y `AccountEntity`. La ida y vuelta se prueba dentro de `AccountRepositoryAdapterTest`, contra la base real: una prueba unitaria del mapeador sola no detectaría un nombre de columna equivocado.
- [x] **T-20** `AccountJpaRepository` y `AccountRepositoryAdapter` que implementa el puerto.
- [x] **T-21** Prueba de integración del adaptador: guardar y recuperar por `firebase_uid`, y el `firebase_uid` duplicado rechazado por la base (`REQ-CU-16`).

## Bloque 4 — Firebase

- [x] **T-22** `FirebaseConfiguration`: inicializa el SDK en el arranque, falla rápido si las credenciales son inválidas y no crea beans con `cuentas.firebase.enabled=false`.
- [x] **T-23** `FirebaseUserDirectoryAdapter` con `createUser`, `assignFreePlanClaim`, `deleteUser` e `isEmailVerified`, traduciendo `EMAIL_EXISTS` a `EmailAlreadyRegisteredException` (`REQ-CU-02`, `REQ-CU-03`, `REQ-CU-07`). `FirebaseUserDirectoryAdapterTest` fija la traducción con el cliente del SDK simulado.
- [x] **T-24** Doble en memoria del puerto para las pruebas, capaz de simular correo existente y fallo del borrado (`REQ-NF-CU-01`).

## Bloque 5 — Casos de uso

- [x] **T-25** `RegisterUserCommand` y `RegisterUserService` con el orden de [plan.md](plan.md) §3.3, sin transacción sobre la llamada a Firebase.
- [x] **T-26** Compensación con `deleteUser` cuando falla el claim o la escritura de la fila, y log del `firebaseUid` si el borrado también falla (`REQ-CU-06`).
- [x] **T-27** `RegisterUserServiceTest`: camino feliz, correo existente, fallo de PostgreSQL con compensación y fallo de la compensación.
- [x] **T-28** `ActivateAccountCommand` y `ActivateAccountService`: activa con el correo verificado, `403` si no lo está y respuesta de éxito si ya estaba activa (`REQ-CU-13`).
- [x] **T-29** `ActivateAccountServiceTest` con esos tres escenarios y el caso de cuenta inexistente.

## Bloque 6 — Presentación

- [ ] **T-30** `RegisterUserRequest` con Bean Validation, `birthDate` en `dd/MM/yyyy` y Javadoc en español (`REQ-CU-01`).
- [ ] **T-31** `RegisteredUserResponse` y `ActivatedAccountResponse` como records, con el estado y el plan (`REQ-CU-05`).
- [ ] **T-32** `UserRegistrationController` para `POST /api/v1/users`, con Javadoc que alimente OpenAPI (`REQ-NF-CU-04`).
- [ ] **T-33** `AccountActivationController` para `POST /api/v1/users/me/verification`, leyendo `X-User-Id` y `X-User-Email-Verified`.
- [ ] **T-34** `BusinessExceptionHandler` con la tabla de traducción de [plan.md](plan.md) §3.4, devolviendo `ProblemDetail` (`REQ-CU-12`).
- [ ] **T-35** `UserRegistrationControllerTest`: `201` del camino feliz y `409` con el mensaje exacto de CA-1.1.2.
- [ ] **T-36** Mismo test, casos `422`: las cinco reglas de fecha, el celular fuera de E.164, el pronombre inválido y la contraseña corta, comprobando `application/problem+json`.
- [ ] **T-37** `AccountActivationControllerTest`: activación correcta, `403` sin verificar y repetición idempotente.
- [ ] **T-38** `AccountRegistrationIT`: registro de punta a punta con PostgreSQL real, Flyway y el puerto de Firebase simulado; la fila queda en `PENDING_VERIFICATION`.

## Bloque 7 — Cierre

- [ ] **T-39** `LayeredArchitectureTest` en verde y, si hace falta, una regla nueva: el dominio no importa `com.google.firebase` ni `jakarta.persistence`.
- [ ] **T-40** Revisar el diff buscando contraseñas, correos o cuerpos en logs y mensajes de error (`REQ-NF-CU-03`, CU-10).
- [ ] **T-41** Actualizar `CLAUDE.md`: contrato de error RFC 7807 y la excepción del registro al contrato de entrada del Gateway.
- [x] **T-42** Actualizar `.env.example` y `docker-compose.yml` con las variables de Firebase. `FIREBASE_PROJECT_ID`, `FIREBASE_KEY_PATH` y `FIREBASE_ENABLED`; en Compose el JSON se monta de solo lectura en `/run/secrets/` y `docker compose config` lo confirma. El `README.md` dice qué hace falta antes de levantar la aplicación.
- [ ] **T-43** `./mvnw.cmd test` y `./mvnw.cmd clean package` en verde, y marcar el DoD de la spec con el nombre de la prueba o el comando que lo respalda.
- [ ] **T-44** Rellenar la bitácora de IA del día y entregar `CONTRATO-GATEWAY-CM-14.md` al responsable del Gateway.

---

## Estado

**Bloque 2 casi completo el 18/09/2026.** Hechos T-09 a T-15: enums, objetos de valor, políticas
de edad y de contraseña, excepciones y el agregado `Account`, con sus pruebas. `./mvnw.cmd test`:
51 pruebas, 0 fallos, 0 saltadas. Quedan T-16 en parte y T-17 completo, aplazados por el límite de
tamaño de cambio; entran con el bloque que los usa.

**Bloques 0 y 1 completos el 17/09/2026.** `./mvnw.cmd test`: 17 pruebas, 0 fallos, 0 saltadas, con
Docker disponible. `docker compose up --build` deja el contenedor `healthy` contra la ruta nueva.

Un hallazgo del camino, ya corregido y anotado en [plan.md](plan.md) §1: en Spring Boot 4 la
autoconfiguración de Flyway vive en el módulo `spring-boot-flyway`. Con solo `flyway-core` en el
classpath, Flyway **no se ejecutaba y no avisaba de nada**; la primera versión de estas tareas pasó
las pruebas únicamente porque la base local ya tenía las tablas creadas por el `ddl-auto=create`
anterior. Lo delató `CuentaSchemaMigrationTest` sobre una base vacía, que es justo para lo que
está.

Queda un aviso inofensivo en cada arranque: `schema "microcuentas" already exists, skipping`,
porque `spring.flyway.create-schemas=true` crea el esquema antes de que corra el `CREATE SCHEMA IF
NOT EXISTS` de `V1`. No se toca la migración ya aplicada solo por eso: editarla cambiaría su suma
de verificación y rompería el arranque en las bases que ya la tienen.

## Estado del bloque 5

Hecho el 18/09/2026, junto con la prueba pendiente del adaptador de Firebase.
`./mvnw.cmd test`: 80 pruebas, 0 fallos, 0 saltadas.

`ActivateAccountServiceTest` encontró un fallo real antes de que llegara a ningún endpoint:
el servicio salía en silencio ante cualquier estado distinto de `PENDING_VERIFICATION`, así que
una cuenta `DISABLED` respondía como si se hubiera activado. Ahora solo corta cuando ya está
`ACTIVE` y el resto lo decide el agregado, que rechaza las bloqueadas y las anonimizadas.

Las políticas del dominio se publican como beans desde `DomainPolicyConfiguration`, en
infraestructura: `domain` no lleva anotaciones de Spring, para que las reglas se puedan instanciar
y probar con `new`.

## Estado del 18/09/2026

Bloques 2, 3 y 4 hechos. `./mvnw.cmd clean test`: 60 pruebas, 0 fallos, 0 saltadas, con Docker
arriba.

Dos cosas que aparecieron al implementar y no estaban en el plan:

- **`V2__ajustar_version_inicial_cuenta.sql`.** El DDL exigía `version >= 1`, pensado para el
  disparador que lo incrementaba; JPA siembra `@Version` en 0, así que ninguna cuenta se podía
  insertar. La migración relaja el mínimo a 0. Es el primer uso real del flujo descrito en
  [plan.md](plan.md) §2.3: no se tocó `V1`, ya aplicada.
- **Firebase se apaga en las pruebas desde Surefire**, con `FIREBASE_ENABLED=false`. El primer
  intento fue un `application.properties` en `src/test/resources`, y resultó que ese archivo
  **reemplaza** al principal en el classpath en vez de complementarlo: la suite se quedó sin base
  de datos y sin la configuración de springdoc.

## Orden y dependencias

```text
Bloque 0  (independiente, va primero)
Bloque 1 → Bloque 2 → Bloque 3 ┐
                    → Bloque 4 ┴→ Bloque 5 → Bloque 6 → Bloque 7
```

Los bloques 3 y 4 pueden hacerse en cualquier orden una vez existan los puertos del bloque 2.

## Estimación

45 tareas de 30 minutos o menos. El bloque 0 son tres tareas cortas; el grueso está en los bloques
2, 5 y 6, que concentran las reglas y sus pruebas.

## Pendientes que no son tareas

- `CU-TBD-04`, la caducidad de cuentas sin verificar, está en [bloqueo.md](bloqueo.md) y lo resuelve
  el Product Owner. Bloquea el cierre de la HU, no estas tareas.
- El enum de pronombres en inglés está por confirmar con el frontend ([plan.md](plan.md) §3.1). Si
  responden antes de T-30, se ajusta el DTO; si no, se implementa como está planeado.
