# Plan técnico — CM-14-RegistroUsuario

- **Spec:** [spec.md](spec.md) · **Bloqueo abierto:** [bloqueo.md](bloqueo.md) (`CU-TBD-04`, no frena la implementación)
- **Fecha:** 17/09/2026
- **Rama:** `CM-14-RegistroUsuario`
- **Estado:** `Borrador`

Este documento dice **cómo** se implementa la spec. Las decisiones de negocio ya están cerradas en
`spec.md` §2 y aquí no se reabren.

---

## 0. Trabajo previo: la ruta de la sonda de salud

Antes de tocar el registro hay que resolver el desajuste que documenta
[CONTRATO-GATEWAY-CM-14.md](../../CONTRATO-GATEWAY-CM-14.md) §3: el Gateway enruta
`Path=/api/v1/users/**` sin `StripPrefix` ni `RewritePath`, así que la petición llega a Cuentas con
la ruta completa, y hoy la sonda vive en `GET /health`.

**Decisión de implementación:** la sonda se **mueve** a `GET /api/v1/users/health`. No se deja un
alias en `/health`: dos rutas para lo mismo obligan a mantener dos pruebas y a decidir cuál es la
buena cada vez que algo cambie.

Arrastra tres archivos además del controlador: el `HEALTHCHECK` del `Dockerfile`, el de
`docker-compose.yml` si lo declara, y la spec [CM-103-EndpointSalud](../CM-103-EndpointSalud/spec.md),
que queda actualizada en el mismo PR porque cambia un contrato ya documentado.

Va primero y aparte: es pequeño, no depende de nada de CM-14 y deja el microservicio alcanzable
desde el Gateway antes de que exista el primer endpoint de negocio.

---

## 1. Dependencias nuevas en `pom.xml`

| Dependencia | Para qué | Nota |
|---|---|---|
| `com.google.firebase:firebase-admin` | Crear la credencial, escribir el claim y borrar en la compensación | Ya generada por Juan Vela; solo falta declararla |
| `spring-boot-starter-flyway` y `org.flywaydb:flyway-database-postgresql` | Migraciones versionadas (CU-7) | **Ojo:** en Spring Boot 4 la autoconfiguración de Flyway vive en el módulo `spring-boot-flyway`, que trae ese starter. Con solo `flyway-core` en el classpath, Flyway **no se ejecuta** y no avisa de nada |
| `spring-boot-starter-validation` | Bean Validation para la sintaxis del cuerpo | |
| `spring-boot-testcontainers`, `testcontainers-postgresql` y `testcontainers-junit-jupiter` (test) | Pruebas de integración con PostgreSQL real y puerto aleatorio | Lo exige el principio 9 de la constitución. Spring Boot 4 trae Testcontainers 2.0, donde los artefactos se llaman `testcontainers-*` y `PostgreSQLContainer` cambió al paquete `org.testcontainers.postgresql` y dejó de ser genérico |

Firebase Admin **no** entra en el dominio: solo lo importa su adaptador de infraestructura (CU-9).

---

## 2. Esquema y configuración

### 2.1 Migración `V1__esquema_inicial_cuenta.sql`

Contiene solo lo que el registro necesita (CU-7):

- `CREATE SCHEMA IF NOT EXISTS microcuentas`.
- Tabla `cuenta` con las columnas del DDL: `id`, `firebase_uid`, `nombre`, `apellido`,
  `fecha_nacimiento`, `telefono`, `pronombres`, `estado`, `version`, `fecha_creacion`,
  `fecha_actualizacion`, `fecha_eliminacion`.
- Restricciones que se quedan en la base (CU-8, `REQ-CU-16`): `uq_cuenta_firebase_uid`,
  `ck_cuenta_nombre`, `ck_cuenta_apellido`, `ck_cuenta_telefono_e164`,
  `ck_cuenta_pronombres_no_vacio`, `ck_cuenta_version`, las dos de coherencia de fechas y
  `ck_cuenta_anonimizacion`.
- `ck_cuenta_estado` **ampliado** con `PENDING_VERIFICATION`, y el `DEFAULT` del estado pasa a ese
  valor (CU-1).

Sin extensiones, sin triggers, sin funciones plpgsql y sin las tablas de planes, suscripciones,
pagos ni Outbox: cada una llega con la spec que la use.

`id` lo genera la aplicación, no un `DEFAULT` de la base, y `version` la gobierna `@Version` de JPA
en lugar del trigger `trg_cuenta_incrementar_version`.

### 2.2 Propiedades

| Propiedad | Valor | Dónde |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `validate` | `application.properties`, y se quita el `create` de `application-local.properties` |
| `spring.jpa.properties.hibernate.default_schema` | `microcuentas` | `application.properties` |
| `spring.flyway.schemas` / `create-schemas` | `microcuentas` / `true` | `application.properties` |
| `FIREBASE_PROJECT_ID`, `FIREBASE_KEY_PATH` | sin default | `.env.example`, `docker-compose.yml` |
| `cuentas.firebase.enabled` | `true`, y `false` en el perfil `test` | Permite que la suite corra sin credenciales (`REQ-NF-CU-01`) |

Flyway corre antes de que Hibernate valide, así que un desajuste entre entidad y tabla rompe el
arranque y no una petición en producción (`REQ-CU-15`).

### 2.3 Cómo se cambia el esquema a partir de ahora

La entidad JPA no genera nada: con `validate`, Hibernate solo compara. Todo cambio de esquema sigue
estos pasos, dentro de un mismo commit:

1. Escribir una migración nueva: `V2__<que_hace>.sql`, `V3__...` y así. **Nunca se edita una ya
   aplicada:** Flyway guarda su suma de verificación en `microcuentas.flyway_schema_history` y un
   archivo modificado rompe el arranque en toda base que ya lo tenga. Un error en una migración
   aplicada se corrige con la siguiente.
2. Ajustar la entidad.
3. Arrancar o correr las pruebas. Flyway aplica lo que falte y Hibernate valida: si falta la
   columna, la aplicación no arranca y el mensaje dice cuál.

En la base local, mientras nadie más dependa de ella, `docker compose down -v` borra el volumen y
todo se reconstruye desde `V1`.

El script DDL del Drive queda como una foto del modelo, no como la fuente de verdad. Esa es la
carpeta `src/main/resources/db/migration`, que se revisa por PR y es la que se ejecuta. Cuando el
equipo necesite el DDL actualizado como entregable, se genera con
`pg_dump --schema-only -n microcuentas` sobre una base con las migraciones aplicadas, en vez de
editarlo a mano.

---

## 3. Diseño por capas

```text
presentation
├── controller/UserRegistrationController        POST /api/v1/users
├── controller/AccountActivationController       POST /api/v1/users/me/verification
├── dto/RegisterUserRequest, RegisteredUserResponse, ActivatedAccountResponse
└── advice/BusinessExceptionHandler              excepciones de dominio -> ProblemDetail
application
├── command/RegisterUserCommand, ActivateAccountCommand
└── service/RegisterUserService, ActivateAccountService
domain
├── model/Account, AccountStatus, BirthDate, PhoneNumber, Pronoun, EmailAddress, RawPassword
├── policy/AgePolicy, PasswordPolicy
├── port/AccountRepository, FirebaseUserDirectory
└── exception/EmailAlreadyRegisteredException, InvalidBirthDateException,
    WeakPasswordException, EmailNotVerifiedException, AccountNotFoundException
infrastructure
├── persistence/entity/AccountEntity · repository/AccountJpaRepository + AccountRepositoryAdapter
│   · mapper/AccountMapper
├── client/FirebaseUserDirectoryAdapter
└── config/FirebaseConfiguration
```

No se crean subcarpetas nuevas: cada clase cae en una carpeta que `CLAUDE.md` ya define.

### 3.1 Dominio

`Account` es el agregado. Nace por un método de fábrica que lo deja en `PENDING_VERIFICATION`
(`REQ-CU-04`) y expone `activate()`, que solo admite la transición desde ese estado y es idempotente
si ya está `ACTIVE` (`REQ-CU-13`, `REQ-CU-14`). `DISABLED` y `ANONYMIZED` no pueden activarse.

Los objetos de valor validan en su construcción: `PhoneNumber` exige E.164, `EmailAddress` formato,
`BirthDate` que sea una fecha real. `Pronoun` es un enum de tres valores.

> **A confirmar con el frontend antes de codificar el DTO:** los identificadores del enum van en
> inglés (`HE`, `SHE`, `THEY`) por la convención de idioma de `CLAUDE.md`, y la columna
> `pronombres` guarda ese nombre. Las etiquetas Él, Ella y Elle son del prototipo. Si el frontend
> ya envía las etiquetas en español, se resuelve con un mapeo en el DTO, no cambiando el dominio.

`AgePolicy` concentra las cinco reglas de CA-1.1.3 y calcula la edad con `LocalDate.now(ZoneOffset.UTC)`.
Devuelve un motivo distinto por caso, porque cada uno tiene su mensaje (`REQ-CU-08` a `REQ-CU-11`).

`PasswordPolicy` aplica OWASP ASVS: 12 caracteres mínimo, 64 admitidos, sin reglas de composición y
sin recortar la entrada. Además rechaza las contraseñas de una lista de valores conocidos que sí
cumplen la longitud, comparando sin distinguir mayúsculas ni espacios alrededor (`REQ-CU-11b`).

La lista vive como constante en la política, con unas decenas de entradas. Es un piso, no la
comprobación completa que describe ASVS: contrastar contra el corpus de contraseñas filtradas exige
consultar un servicio externo, y eso es una integración con su propia spec, sus reintentos y su
decisión sobre qué hacer cuando el servicio no responde.

### 3.2 Puertos

```java
public interface FirebaseUserDirectory {
    String createUser(EmailAddress email, RawPassword password);
    void assignFreePlanClaim(String firebaseUid);
    void deleteUser(String firebaseUid);
    boolean isEmailVerified(String firebaseUid);
}
```

`createUser` traduce el `EMAIL_EXISTS` del SDK a `EmailAlreadyRegisteredException`: el dominio no
conoce códigos de Firebase. `AccountRepository` expone `save` y `findByFirebaseUid`.

### 3.3 Orquestación del registro

`RegisterUserService` ejecuta, en este orden:

1. Construye los objetos de valor y aplica `AgePolicy` y `PasswordPolicy`. Si algo falla, no se
   toca Firebase.
2. `createUser` y luego `assignFreePlanClaim`.
3. Guarda la cuenta.
4. Si 2 o 3 fallan después de que la credencial exista, `deleteUser` y `500`; si la compensación
   falla, un log con el `firebaseUid` (`REQ-CU-06`).

**El método no es `@Transactional` de punta a punta.** Firebase no participa en una transacción de
base de datos, y envolverlo daría una falsa sensación de atomicidad; la transacción cubre solo la
escritura de la fila. La compensación es explícita.

### 3.4 Errores

`BusinessExceptionHandler`, un `@RestControllerAdvice`, traduce cada excepción a `ProblemDetail`, que
Spring ya serializa como `application/problem+json` (`REQ-CU-12`):

| Excepción o fallo | Estado | `detail` |
|---|---|---|
| `EmailAlreadyRegisteredException` | `409` | "Este correo ya se encuentra registrado" |
| `InvalidBirthDateException` con motivo `MENOR_DE_EDAD` | `422` | "Debes ser mayor de edad" |
| `InvalidBirthDateException` con motivo `FUTURA` | `422` | "Fecha de nacimiento inválida" |
| `InvalidBirthDateException` con motivo `IMPLAUSIBLE` | `422` | Mensaje de fecha implausible a verificar |
| `MethodArgumentNotValidException` y `HttpMessageNotReadableException` | `422` | Un elemento por campo en `errors` |
| `WeakPasswordException` | `422` | Longitud mínima exigida, sin repetir la contraseña |
| `EmailNotVerifiedException` | `403` | Falta verificar el correo |
| Cualquier otro fallo | `500` | Mensaje genérico; el detalle solo al log (OWASP, CU-10) |

`birthDate` se enlaza con `@JsonFormat(pattern = "dd/MM/yyyy")`; un valor que Jackson no pueda leer
cae en `HttpMessageNotReadableException` y sale como error de formato, distinto del de mayoría de
edad (`REQ-CU-11`).

### 3.5 Activación

`AccountActivationController` recibe `POST /api/v1/users/me/verification` sin cuerpo. La identidad
sale de `X-User-Id` y la verificación de `X-User-Email-Verified`, según
[CONTRATO-GATEWAY-CM-14.md](../../CONTRATO-GATEWAY-CM-14.md) §1. Si el encabezado no llega o dice
`false`, Cuentas consulta `isEmailVerified` en Firebase antes de responder `403`: así la ruta
funciona aunque el Gateway todavía no propague el encabezado.

---

## 4. Seguridad

- La contraseña viaja en el cuerpo, se convierte en `RawPassword` y muere ahí. No se persiste, no
  entra en ningún log y no vuelve en ninguna respuesta de error.
- `RawPassword` sobreescribe `toString()` para no filtrarse por un log descuidado.
- Los logs registran, como máximo, el `firebaseUid`. Nunca el correo, el cuerpo ni el token.
- El `409` de correo repetido permite enumerar cuentas. Es la excepción consciente que la spec
  documenta; el control compensatorio es el rate limit de GCP.

---

## 5. Pruebas

| Nivel | Clases | Qué cubre |
|---|---|---|
| Dominio | `AgePolicyTest`, `PasswordPolicyTest`, `AccountTest` | Las cinco reglas de fecha, los límites 12 y 64 de la contraseña, el estado inicial y las transiciones |
| Aplicación | `RegisterUserServiceTest`, `ActivateAccountServiceTest` | Orden de llamadas, compensación con `deleteUser`, fallo de la compensación, idempotencia de la activación |
| Web | `UserRegistrationControllerTest`, `AccountActivationControllerTest` | Códigos, `application/problem+json` y los mensajes exactos de los criterios |
| Integración | `AccountRegistrationIT` | Testcontainers con PostgreSQL 16, Flyway aplicado, puerto aleatorio y el puerto de Firebase simulado |
| Arquitectura | `LayeredArchitectureTest` (ya existe) | Que el dominio no importe Firebase ni JPA |

El doble de `FirebaseUserDirectory` es una implementación en memoria, no un mock del SDK: mantiene el
dominio libre de Firebase y permite simular `EMAIL_EXISTS` y el fallo del borrado.

---

## 6. Fuera de este plan

- Trazabilidad de `X-Request-Id` en los logs: útil, pero es una spec de observabilidad propia.
- Caducidad de cuentas sin verificar: [bloqueo.md](bloqueo.md).
- Suscripción FREE y sus semillas: spec de planes.
- Reintentos del claim con `sincronizacion_claim_firebase`: si `assignFreePlanClaim` falla, aquí se
  compensa y se responde `500`; la cola de reintentos llega con la spec de planes.
