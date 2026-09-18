# Tareas — CM-14-RegistroUsuario

- **Spec:** [spec.md](spec.md) · **Plan:** [plan.md](plan.md)
- **Fecha:** 17/09/2026
- **Regla:** cada tarea se hace en 30 minutos o menos y se marca `[x]` antes de empezar la siguiente.
  Cada bloque termina con `./mvnw.cmd test` en verde.

---

## Bloque 0 — Previo: la sonda de salud alcanzable desde el Gateway

> No depende de nada de CM-14 y se hace primero. Motivo en [plan.md](plan.md) §0 y en
> [CONTRATO-GATEWAY-CM-14.md](../../CONTRATO-GATEWAY-CM-14.md) §3.

- [ ] **T-00** Mover la sonda de `GET /health` a `GET /api/v1/users/health` en `HealthController` y ajustar `HealthControllerTest` (mismo cuerpo, mismos códigos, `POST` sigue dando `405`).
- [ ] **T-01** Actualizar el `HEALTHCHECK` del `Dockerfile`, y el de `docker-compose.yml` si lo declara, a la ruta nueva. Verificar con `docker compose up --build` y `docker ps` mostrando `healthy`.
- [ ] **T-02** Actualizar [CM-103-EndpointSalud/spec.md](../CM-103-EndpointSalud/spec.md), el `README.md` y la §3 del contrato del Gateway con la ruta nueva.

## Bloque 1 — Cimientos: dependencias, esquema y configuración

- [ ] **T-03** Declarar en `pom.xml` `firebase-admin`, `flyway-core`, `flyway-database-postgresql` y `spring-boot-starter-validation`. `./mvnw.cmd clean package` en verde.
- [ ] **T-04** Declarar `testcontainers:postgresql` y `spring-boot-testcontainers` en alcance `test`.
- [ ] **T-05** Escribir `V1__esquema_inicial_cuenta.sql` con el esquema `microcuentas` y la tabla `cuenta`: columnas del DDL, sin extensiones ni triggers (`REQ-CU-15`).
- [ ] **T-06** Agregar a esa migración las restricciones de `REQ-CU-16` y `ck_cuenta_estado` con `PENDING_VERIFICATION` incluido y como `DEFAULT` (CU-1).
- [ ] **T-07** Pasar `ddl-auto` a `validate` en `application.properties`, quitar el `create` de `application-local.properties` y declarar el esquema por defecto y las propiedades de Flyway.
- [ ] **T-08** Prueba de integración mínima: Testcontainers levanta PostgreSQL 16, Flyway aplica `V1` y el contexto arranca con `validate`.

## Bloque 2 — Dominio

- [ ] **T-09** `AccountStatus` con los cuatro estados y `Pronoun` con sus tres valores.
- [ ] **T-10** Objetos de valor `EmailAddress`, `PhoneNumber` (E.164), `BirthDate` y `RawPassword`, este último sin exponerse en `toString()`.
- [ ] **T-11** `AgePolicy` con las cinco reglas de CA-1.1.3 y la edad calculada en UTC (`REQ-CU-08` a `REQ-CU-10`).
- [ ] **T-12** `AgePolicyTest`: cumple 18 hoy, los cumple mañana, fecha futura, más de 110 años y fecha inválida.
- [ ] **T-13** `PasswordPolicy` según OWASP ASVS y `PasswordPolicyTest` con los límites 11, 12, 64 y 65 caracteres (`REQ-CU-11b`).
- [ ] **T-14** Agregado `Account`: fábrica que nace `PENDING_VERIFICATION` y `activate()` con las transiciones válidas (`REQ-CU-04`, `REQ-CU-13`, `REQ-CU-14`).
- [ ] **T-15** `AccountTest`: estado inicial, activación, activación repetida y rechazo desde `DISABLED` y `ANONYMIZED`.
- [ ] **T-16** Excepciones de dominio con sus mensajes en español y `InvalidBirthDateException` con su motivo.
- [ ] **T-17** Puertos `AccountRepository` y `FirebaseUserDirectory`.

## Bloque 3 — Persistencia

- [ ] **T-18** `AccountEntity` mapeada a `microcuentas.cuenta`, con columnas en español y `@Version`.
- [ ] **T-19** `AccountMapper` entre `Account` y `AccountEntity`, con prueba de ida y vuelta.
- [ ] **T-20** `AccountJpaRepository` y `AccountRepositoryAdapter` que implementa el puerto.
- [ ] **T-21** Prueba de integración del adaptador: guardar y recuperar por `firebase_uid`, y el `firebase_uid` duplicado rechazado por la base (`REQ-CU-16`).

## Bloque 4 — Firebase

- [ ] **T-22** `FirebaseConfiguration`: inicializa el SDK en el arranque, falla rápido si las credenciales son inválidas y no crea beans con `cuentas.firebase.enabled=false`.
- [ ] **T-23** `FirebaseUserDirectoryAdapter` con `createUser`, `assignFreePlanClaim`, `deleteUser` e `isEmailVerified`, traduciendo `EMAIL_EXISTS` a `EmailAlreadyRegisteredException` (`REQ-CU-02`, `REQ-CU-03`, `REQ-CU-07`).
- [ ] **T-24** Doble en memoria del puerto para las pruebas, capaz de simular correo existente y fallo del borrado (`REQ-NF-CU-01`).

## Bloque 5 — Casos de uso

- [ ] **T-25** `RegisterUserCommand` y `RegisterUserService` con el orden de [plan.md](plan.md) §3.3, sin transacción sobre la llamada a Firebase.
- [ ] **T-26** Compensación con `deleteUser` cuando falla el claim o la escritura de la fila, y log del `firebaseUid` si el borrado también falla (`REQ-CU-06`).
- [ ] **T-27** `RegisterUserServiceTest`: camino feliz, correo existente, fallo de PostgreSQL con compensación y fallo de la compensación.
- [ ] **T-28** `ActivateAccountCommand` y `ActivateAccountService`: activa con el correo verificado, `403` si no lo está y respuesta de éxito si ya estaba activa (`REQ-CU-13`).
- [ ] **T-29** `ActivateAccountServiceTest` con esos tres escenarios y el caso de cuenta inexistente.

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
- [ ] **T-42** Actualizar `.env.example` y `docker-compose.yml` con las variables de Firebase.
- [ ] **T-43** `./mvnw.cmd test` y `./mvnw.cmd clean package` en verde, y marcar el DoD de la spec con el nombre de la prueba o el comando que lo respalda.
- [ ] **T-44** Rellenar la bitácora de IA del día y entregar `CONTRATO-GATEWAY-CM-14.md` al responsable del Gateway.

---

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
