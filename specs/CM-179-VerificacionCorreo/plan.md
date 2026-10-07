# Plan — CM-179-VerificacionCorreo (cameia-cuentas)

Base: `develop` después de fusionar el PR 1A de CM-36 (pregunta 13 de la spec, respondida por Paula el 6-oct-2026: no espera a 1B, 2, 3 ni a CM-251; lo que se fusione antes obliga a un rebase; se revalidan rutas y líneas al empezar: las que se citan son de `908112c`). Estado: pendiente de aprobación de Paula. Este plan contiene el **bloque 1** (activación robusta, secciones 1 a 8), el **bloque 2** (purga, sección 9) y el **bloque 4** (modo de tarea, sección 10), un PR cada uno; el bloque 3 (Gateway) tiene su plan en `cameia-gateway/specs/CM-179-VerificacionCorreo/`.

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

T-1.1 → T-1.2 → T-1.3 → T-1.4 → T-1.5 → T-1.6 → T-1.7. Sin bloqueos: los textos y estados (preguntas 1 a 3) se cerraron sin consulta y la pregunta 5 está respondida. Depende de que el PR 1A de CM-36 esté fusionado (pregunta 13 respondida).

## 8. Estimación

≈ 4 h, ≈ 450 líneas, un PR. Los bloques 2, 3 y 4 se estiman en la spec (sección 15).

## 9. Bloque 2 — purga diaria de cuentas sin verificar (PR 2)

Base: `develop` con el bloque 1 fusionado (usa `Account.activate()` con excepciones tipadas y la traducción del conflicto de versión). Rama `CM-179-purga-cuentas-sin-verificar`. ≈ 6 h, ≈ 700 líneas con pruebas.

### 9.1 Cómo se aborda

1. **Dominio.** Un enumerado nuevo `UserVerificationStatus` (`VERIFIED`, `NOT_VERIFIED`, `NOT_FOUND`) y un `record PurgeSummary(int reviewed, int deleted, int activated, int failed, boolean candidatesLeft)`, ambos en `domain/model`. El puerto `FirebaseUserDirectory` cambia `isEmailVerified` por `UserVerificationStatus verificationStatus(String firebaseUid)` (tras el bloque 1 nadie más lo usa: se comprueba con `git grep -n isEmailVerified`) y `deleteUser` pasa a ser idempotente: un usuario inexistente es éxito (REQ-VC-23). El puerto `AccountRepository` gana `List<String> findPendingVerificationCreatedBefore(Instant limit, int max)` (solo los `firebaseUid`, de la más antigua a la más nueva) y `boolean deletePendingByFirebaseUid(String firebaseUid)` (devuelve si borró una fila).
2. **Infraestructura.** El adaptador de Firebase traduce `AuthErrorCode.USER_NOT_FOUND` a `NOT_FOUND` en la consulta y a éxito en el borrado; cualquier otro `FirebaseAuthException` sigue saliendo como `IllegalStateException`, y la purga cuenta esa cuenta como fallida. El adaptador de la base agrega una consulta JPQL con orden y tope, y un borrado por `firebase_uid` **condicionado a `estado = 'PENDING_VERIFICATION'`**, con `@Modifying` dentro de una transacción propia.
3. **Aplicación.** `PurgeUnverifiedAccountsService.purge()`, sin `@Transactional` en el método: cada cuenta es independiente y un fallo no deshace las demás. Calcula `limit = clock.instant().minus(168, ChronoUnit.HOURS)`, pide `max + 1` candidatas para saber si quedan más, procesa como máximo `max` (1000) y devuelve el resumen. Por cada `firebaseUid`: consulta el estado en Firebase → `VERIFIED`: busca la cuenta, la activa (`activate()` + `save`); `NOT_VERIFIED`: borra la credencial y después la fila; `NOT_FOUND`: borra solo la fila. Una excepción en una cuenta (o un borrado de fila que afecta 0 filas) la cuenta como fallida, se registra y se sigue con la siguiente.
4. **Configuración.** Un bean `Clock` (`Clock.systemUTC()`) en `DomainPolicyConfiguration` y la propiedad `cuentas.purge.max-accounts-per-run=${PURGE_MAX_ACCOUNTS_PER_RUN:1000}`.
5. **Migración** `V<n>__indice_cuenta_pendiente_fecha_creacion.sql` con `CREATE INDEX ix_cuenta_pendiente_fecha_creacion ON microcuentas.cuenta (fecha_creacion) WHERE estado = 'PENDING_VERIFICATION';`. `<n>` es el siguiente número libre en `develop` el día del PR (CM-36 usa V3).

### 9.2 Archivos (paquete base `src/main/java/tech/cameia/cuentas`)

| Acción | Archivo | Qué |
|---|---|---|
| Crear | `domain/model/UserVerificationStatus.java` | Enumerado de tres valores |
| Crear | `domain/model/PurgeSummary.java` | Resumen inmutable de una ejecución |
| Crear | `application/service/PurgeUnverifiedAccountsService.java` | El caso de uso |
| Crear | `src/main/resources/db/migration/V<n>__indice_cuenta_pendiente_fecha_creacion.sql` | Índice parcial |
| Modificar | `domain/port/FirebaseUserDirectory.java` | `verificationStatus` en lugar de `isEmailVerified`; Javadoc de `deleteUser` (idempotente) |
| Modificar | `domain/port/AccountRepository.java` | Dos métodos nuevos |
| Modificar | `infrastructure/client/FirebaseUserDirectoryAdapter.java` | Traducción de `USER_NOT_FOUND` |
| Modificar | `infrastructure/persistence/repository/AccountJpaRepository.java` y `AccountRepositoryAdapter.java` | Consulta con tope y borrado condicionado |
| Modificar | `infrastructure/config/DomainPolicyConfiguration.java` | Bean `Clock` |
| Modificar | `src/main/resources/application.properties` | Propiedad del tope |
| Pruebas | `PurgeUnverifiedAccountsServiceTest` (nueva, sin Spring), `FirebaseUserDirectoryAdapterTest`, `AccountRepositoryAdapterTest`, `CuentaSchemaMigrationTest`, `InMemoryFirebaseUserDirectory` (doble), `PurgeEndToEndTest` (nueva, Testcontainers) | Ver tarjetas |
| No se tocan | Controladores, manejador de errores, `pom.xml`, migraciones existentes, `.github/**` | |

### 9.3 Decisiones técnicas del bloque

| # | Decisión | Porqué | Alternativa descartada |
|---|---|---|---|
| T1 | Pedir `max + 1` candidatas | Una sola consulta dice si quedaron candidatas sin procesar (REQ-VC-26) | Un `COUNT` aparte |
| T2 | Borrar la credencial **antes** que la fila | Si falla entre ambos, la siguiente ejecución encuentra `NOT_FOUND` y borra la fila; al revés quedaría una credencial sin fila, que CM-251 responde con 409 y el correo quedaría bloqueado | Fila primero |
| T3 | Sin transacción que abarque la ejecución | Un fallo en la cuenta 500 no debe deshacer las 499 anteriores; las llamadas a Firebase no son transaccionales | `@Transactional` en `purge()` |
| T4 | El servicio recibe `Clock` y `max` por constructor | Pruebas de 167 h 59 min, 168 h y 168 h 1 min con reloj fijo | `Instant.now()` dentro del método |
| T5 | El borrado de la fila exige `estado = 'PENDING_VERIFICATION'` en la misma sentencia | Si la persona activó la cuenta entre la consulta y el borrado, la fila no se borra: 0 filas, se cuenta como fallida y queda en el log | Borrar sin condición |

T5 reduce la ventana de D6 en la base, no en Firebase (la credencial ya se borró). Se acepta (pregunta 8 respondida).

### 9.4 Matriz de pruebas

| Prueba | Capa | Requisito |
|---|---|---|
| Reloj fijo `2026-10-10T08:00:00Z`: la consulta recibe `limit = 2026-10-03T08:00:00Z`; con base real, filas de 168 h 1 min (sí), 168 h exactas (no) y 167 h 59 min (no) | servicio + adaptador | REQ-VC-21 |
| `NOT_VERIFIED` → `deleteUser` y luego `deletePendingByFirebaseUid`, en ese orden | servicio | REQ-VC-22 |
| `VERIFIED` → la cuenta queda `ACTIVE`, sin borrar nada; `activated=1` | servicio | REQ-VC-22 |
| `NOT_FOUND` → solo se borra la fila | servicio | REQ-VC-22, 23 |
| Firebase falla en la segunda de tres → primera y tercera procesadas, `failed=1` | servicio | REQ-VC-24 |
| Falla el borrado de la fila tras borrar la credencial → `failed=1`; la segunda ejecución recibe `NOT_FOUND` y borra la fila | servicio | REQ-VC-24 |
| Lote vacío → resumen en ceros, sin llamadas a Firebase | servicio | REQ-VC-28 |
| 1000 candidatas → `reviewed=1000`, `candidatesLeft=false`; 1001 → `reviewed=1000`, `candidatesLeft=true` | servicio | REQ-VC-26 |
| Orden de la más antigua a la más nueva; `ACTIVE`, `DISABLED` y `ANONYMIZED` viejas no se devuelven | adaptador (Testcontainers) | REQ-VC-21, 26 |
| `deletePendingByFirebaseUid` de una fila `ACTIVE` devuelve `false` y no la borra | adaptador (Testcontainers) | T5 |
| Firebase: `USER_NOT_FOUND` → `NOT_FOUND`; verificado → `VERIFIED`; no verificado → `NOT_VERIFIED`; otro código → `IllegalStateException`; `deleteUser` de inexistente no lanza | adaptador | REQ-VC-23 |
| El índice existe y es parcial (`pg_indexes.indexdef` contiene `WHERE`) | esquema (Testcontainers) | REQ-VC-29 |
| Punta a punta: registrar, envejecer la fila por SQL (`now() - interval '169 hours'`), purgar y registrar de nuevo el mismo correo → 201 | integración | REQ-VC-27 |
| Dos ejecuciones seguidas → la segunda no encuentra candidatas | integración | idempotencia |
| Ningún log contiene el correo (`OutputCaptureExtension`) | servicio | REQ-VC-25 |

### 9.5 Riesgos

| Riesgo | Mitigación |
|---|---|
| `fecha_creacion` la pone la base (`DEFAULT now()`) y el reloj de la aplicación puede diferir unos segundos | Irrelevante frente a 168 h; se documenta en el Javadoc |
| Borrado de Spring Data sin transacción (`TransactionRequiredException`) | `@Modifying @Query` con `@Transactional` en el método del adaptador (trampa en la tarjeta) |
| Cambiar `isEmailVerified` rompe a un llamador | La tarjeta exige `git grep` antes de quitarlo |
| CM-251 también cambia `FirebaseUserDirectory` (agrega `getUserByEmail`) | Si se fusionó antes, rebase y conservar ambos cambios |

## 10. Bloque 4 — modo de arranque de tarea para el Cloud Run Job (PR 4)

Base: `develop` con el bloque 2 fusionado. Rama `CM-179-modo-tarea-purga`. ≈ 3 h, ≈ 300 líneas con pruebas y documentación.

### 10.1 Cómo se aborda

1. **Perfil `purge-job`** en `application-purge-job.properties`: `spring.main.web-application-type=none` (no abre puerto). El resto de la configuración llega de los demás perfiles: el Job se arranca con `SPRING_PROFILES_ACTIVE=prod,purge-job`.
2. **Ejecutor** `infrastructure/config/PurgeJobRunner` (`ApplicationRunner` con `@Profile("purge-job")`): llama a `purge()` una vez y registra el resumen en `INFO`, o en `WARN` si `failed > 0` o `candidatesLeft`.
3. **Salida del proceso.** `CuentasApplication.main` cierra el contexto y sale con `System.exit(SpringApplication.exit(contexto))` cuando el perfil `purge-job` está activo. Código 0 si `purge()` terminó, aunque haya cuentas fallidas (se reintentan mañana: REQ-VC-41). Distinto de 0 si el contexto no arrancó (base inaccesible, configuración inválida) o si `purge()` lanzó (por ejemplo, falló la consulta de candidatas): Spring Boot sale con 1 cuando falla un `ApplicationRunner`.
4. **Documentación:** `CLAUDE.md` del repo con el comando exacto, la variable que lo activa y que no expone ruta HTTP; `.env.example` con la línea comentada `# PURGE_MAX_ACCOUNTS_PER_RUN=1000`.

### 10.2 Archivos

| Acción | Archivo | Qué |
|---|---|---|
| Crear | `src/main/resources/application-purge-job.properties` | Sin servidor web |
| Crear | `infrastructure/config/PurgeJobRunner.java` | Ejecuta la purga una vez |
| Modificar | `CuentasApplication.java` | Sale con el código del contexto en modo tarea |
| Modificar | `CLAUDE.md` del repo, `.env.example` | Documentación |
| Pruebas | `PurgeJobRunnerTest` (sin Spring), `PurgeJobProfileTest` (`@SpringBootTest` con el perfil, Testcontainers) | Ver tarjetas |
| No se tocan | Controladores, `Dockerfile` (la misma imagen sirve para el Job), `.github/**` | |

### 10.3 Decisiones técnicas del bloque

| # | Decisión | Porqué | Alternativa descartada |
|---|---|---|---|
| T6 | Misma imagen; el perfil decide el modo | Una sola imagen que se promueve por digest | Una imagen o un módulo aparte |
| T7 | `ApplicationRunner` con `@Profile` en `infrastructure/config` | Es arranque de infraestructura; el caso de uso sigue en `application` y se prueba sin Spring | Ruta HTTP interna o `@Scheduled` (descartados en D4) |
| T8 | `System.exit(SpringApplication.exit(contexto))` solo en modo tarea | El proceso termina y devuelve el código al Job aunque quede algún hilo vivo | Confiar en que el JVM termine solo |

### 10.4 Hallazgo de seguridad

`FirebaseConfiguration` detecta un despliegue con `K_SERVICE`, que Cloud Run define en los **servicios**; en un **Job** la variable es `CLOUD_RUN_JOB`. En el Job, la protección contra el emulador de Firebase depende solo del perfil `prod`. **Pregunta P-JOB-1 (Paula):** ¿se agrega `CLOUD_RUN_JOB` a la detección en este bloque (≈ 10 líneas y una prueba)? Recomendación: sí. **Respondida (Paula, 6-oct-2026): sí**, en la tarjeta T-4.3.

### 10.5 Matriz de pruebas

| Prueba | Capa | Requisito |
|---|---|---|
| El ejecutor llama a `purge()` una sola vez; `INFO` con el resumen, `WARN` con fallidas o candidatas pendientes | unidad | REQ-VC-40, 25 |
| Con el perfil `purge-job` y base real: no hay servidor web y la purga corre una vez | integración | REQ-VC-40, 42 |
| Si `purge()` lanza, el arranque falla (el contexto propaga la excepción) | integración | REQ-VC-41 |
| Sin el perfil, el ejecutor no existe | integración | REQ-VC-42 |
| Si se aprueba P-JOB-1: emulador con `CLOUD_RUN_JOB` definida → no arranca | integración (`FirebaseConfigurationTest`) | seguridad |
| Manual, documentada en el PR: `java -jar target/cuentas-*.jar --spring.profiles.active=local,purge-job` termina solo y el código de salida es 0 | manual | REQ-VC-41, 43 |
