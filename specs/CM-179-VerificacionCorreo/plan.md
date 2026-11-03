# Plan — CM-179-VerificacionCorreo (cameia-cuentas)

Base: `origin/develop` `621a846` (11-oct-2026). Estado: aprobado por Paula con la spec el 11-oct-2026. Un PR en Cuentas con las piezas 1 (activación), 2 (purga) y 4 (modo tarea), un commit por capacidad. La pieza 3 (Gateway) tiene su plan en `cameia-gateway/specs/CM-179-VerificacionCorreo/` y va en su propio PR, en paralelo.

## 1. Rama y orden de commits

La rama `CM-179-verificacion-correo-cuentas` existe en local con 12 commits de spec y 38 por detrás de `develop`; nunca se subió. Antes de la tarjeta T-0: guardar su SHA en `ejecucion/evidencia/CM-179/` y rebasarla sobre `origin/develop`, sin force-push porque no tiene remoto. Los commits de spec quedan primero.

| # | Commit (sin `[IA-ASISTIDO]`; trailer `Co-Authored-By`) | Tarjetas | Qué pide al revisor |
|---|---|---|---|
| 1 | `CM-179 \| docs(cuentas): spec, plan y tarjetas de la verificación de correo` | ya hecho | Criterio |
| 2 | `CM-179 \| docs(cuentas): catálogo de errores y colección de Postman de la verificación` | T-0 | Mecánico: siembra de archivos compartidos |
| 3 | `CM-179 \| fix(cuentas): activación con la verificación del token y sin 500 por estado de cuenta` | T-1.1 a T-1.4 | Criterio: `ActivateAccountService`, `Account.activate`, el adaptador |
| 4 | `CM-179 \| test(cuentas): activación de punta a punta con base real` | T-1.5 | Criterio: la prueba de concurrencia |
| 5 | `CM-179 \| feat(cuentas): migraciones del índice de la purga y del evento de cuenta eliminada` | T-2.1 | Mecánico, pero leer el `ALTER` |
| 6 | `CM-179 \| feat(cuentas): evento cuenta.eliminada con su contrato y la transacción de borrado` | T-2.2, T-2.3 | Criterio: la transacción |
| 7 | `CM-179 \| feat(cuentas): consulta tipada a Firebase y lectura de cuentas sin verificar` | T-2.4, T-2.5 | Mecánico salvo la traducción de errores |
| 8 | `CM-179 \| feat(cuentas): purga diaria de cuentas sin verificar` | T-2.6, T-2.7 | Criterio: el servicio |
| 9 | `CM-179 \| feat(cuentas): conciliación de credenciales huérfanas en la purga` | T-2.8 | Criterio |
| 10 | `CM-179 \| feat(cuentas): modo tarea de la purga para el Cloud Run Job` | T-4.1 a T-4.3 | Mecánico salvo la guarda del emulador |
| 11 | `CM-179 \| docs(cuentas): modo tarea, contrato del evento y Newman de la activación` | T-4.4, T-5.1 | Mecánico |

**Verificación (regla del 10-oct-2026):** por tarjeta, solo las pruebas de la clase tocada. Al terminar, una sola vez sobre la punta: `./mvnw.cmd -B clean verify`, cobertura de lo tocado y Newman (T-5.2). **Revisión independiente temprana** (`/code-review high` en un worktree aparte) al cerrar el commit 4, que es el primer tercio.

## 2. Pieza 1 — activación

1. **Dominio:** `AccountDisabledException` y `AccountConflictException` nuevas; `ErrorCode` gana `ACCOUNT_DISABLED` y `ACCOUNT_CONFLICT`. `Account.activate()` lanza `AccountDisabledException` con `DISABLED` y `AccountNotFoundException` con `ANONYMIZED`. Los textos de `EmailNotVerifiedException`, `AccountNotFoundException` e `IdentityRequiredException` pasan a la sección 5 de la spec.
2. **Persistencia:** `AccountRepositoryAdapter.save` traduce `ObjectOptimisticLockingFailureException` a `AccountConflictException`.
3. **Aplicación:** `ActivateAccountService` pierde su dependencia de Firebase y su `@Transactional` (D3). Orden: identidad (`IdentityRequiredException` con la regla y la constante de `GetCurrentAccountService`) → buscar (404) → exigir el token verificado (403) → si ya es `ACTIVE`, devolverla sin escribir → activar y guardar → ante conflicto, releer una vez.
4. **Presentación:** el encabezado `X-User-Email-Verified` llega como `String`; solo `"true"` cuenta. El manejador traduce las dos excepciones nuevas con `rechazo(...)`. El texto fijo del 400 en `peticionIncompleta` (línea 264) gana el punto. OpenAPI completo en el controlador.
5. **Clasificación:** se quita `Account#activate` de `UntypedExceptionClassificationTest`; se borran de `docs/errores.md` las cuatro filas de deuda que esta tarea cierra.

## 3. Pieza 2 — purga y evento

1. **Migraciones:** índice parcial y `CHECK` del tipo de evento (spec §7).
2. **Evento:** `domain/event/AccountDeleted` (`eventId`, `firebaseUid`, `deletedAt`, `correlationId`; `TYPE = "cuenta.eliminada"`, `VERSION = 1`), `AccountDeletedPayloadV1` (`usuarioId`, `eliminadaEn`), `OutboxRepository.appendAccountDeleted` (mismo `insertIfAbsent`), `docs/eventos/cuenta-eliminada-v1.md` y su esquema JSON.
3. **Transacción de borrado:** `application/service/PendingAccountDeletionService.deletePending(String firebaseUid)`, `@Transactional`: `AccountRepository.deletePendingByFirebaseUid` y, si borró, `outbox.appendAccountDeleted`. Devuelve si borró. Es el gemelo de `AccountRecordingService`.
4. **Puertos:** `FirebaseUserDirectory.verificationStatus(uid)` reemplaza a `isEmailVerified` (traduce `USER_NOT_FOUND` a `NOT_FOUND`, y lo demás con `unavailableOrRejection`). `AccountRepository` gana `findPendingVerificationCreatedBefore(Instant, int)` y `deletePendingByFirebaseUid(String)`.
5. **Servicio:** `PurgeUnverifiedAccountsService.purge()`, sin `@Transactional`. Reloj por constructor, como `AccountRecordingService`. Tope por `PurgeProperties` (`@ConfigurationProperties("cuentas.purge")` + `@Validated`, `@Min(1) @Max(1000)`), registrada con `@EnableConfigurationProperties(PurgeProperties.class)` en `DomainPolicyConfiguration` (no en la configuración de mensajería, que es de otro motivo de cambio).
6. **Conciliación (opción C de la pregunta 18, elegida por Paula el 11-oct-2026):** `FirebaseUserDirectory.findUnverifiedPasswordUsersCreatedBefore(Instant, int)` recorre `listUsers` por páginas de 1000 y `AccountRepository.findExistingFirebaseUids(Collection<String>)` filtra las que tienen fila.

| # | Decisión técnica | Porqué | Descartada |
|---|---|---|---|
| T1 | Pedir `max + 1` candidatas | Una consulta dice si quedan | `COUNT` aparte |
| T2 | Credencial antes que fila | Lo fija la nota técnica de HU-1.2; el fallo intermedio deja `NOT_FOUND`, que la siguiente ejecución termina | Fila primero (contradice la nota técnica) |
| T3 | Sin transacción que abarque la ejecución; una transacción por fila y evento | Un fallo en la cuenta 500 no deshace las anteriores | `@Transactional` en `purge()` |
| T4 | Borrado condicionado a `PENDING_VERIFICATION` | Si alguien activó entre medias, no se borra (REQ-VC-25) | Borrar sin condición |
| T5 | `DependencyUnavailableException` detiene y se relanza | REQ-VC-26, D10 | Seguir |

## 4. Pieza 4 — modo tarea

Mismo patrón que `account-events-relay`: `application-purge-job.properties` (`spring.main.web-application-type=none`), `infrastructure/config/PurgeJobRunner` (`@Profile(PurgeJobRunner.PROFILE)`, `PROFILE = "purge-job"`), y `CuentasApplication.exitWhenOneShotJob` amplía su condición a `Profiles.of(AccountEventsRelayJobRunner.PROFILE + " | " + PurgeJobRunner.PROFILE)`. `FirebaseConfiguration` reconoce `CLOUD_RUN_JOB`. No se crea `main` nuevo ni otra imagen.

## 5. Archivos (paquete base `src/main/java/tech/cameia/cuentas`)

| Acción | Archivo |
|---|---|
| Crear | `domain/exception/AccountDisabledException.java`, `AccountConflictException.java`; `domain/event/AccountDeleted.java`; `domain/model/UserVerificationStatus.java`, `PurgeSummary.java`; `application/service/PendingAccountDeletionService.java`, `PurgeUnverifiedAccountsService.java`; `infrastructure/messaging/payload/AccountDeletedPayloadV1.java`; `infrastructure/config/PurgeProperties.java`, `PurgeJobRunner.java` (y `@EnableConfigurationProperties` en `DomainPolicyConfiguration`); `src/main/resources/application-purge-job.properties`; dos migraciones; `docs/eventos/cuenta-eliminada-v1.md` y `.schema.json` |
| Modificar | `ErrorCode`, `EmailNotVerifiedException`, `AccountNotFoundException`, `IdentityRequiredException`, `Account`, `ActivateAccountCommand`, `ActivateAccountService`, `AccountActivationController`, `BusinessExceptionHandler`, `AccountRepository`, `AccountRepositoryAdapter`, `AccountJpaRepository`, `FirebaseUserDirectory`, `FirebaseUserDirectoryAdapter`, `OutboxRepository`, `OutboxRepositoryAdapter`, `FirebaseConfiguration`, `CuentasApplication`, `application.properties`, `CLAUDE.md`, `.env.example`, `docs/errores.md`, `postman/cameia-cuentas.postman_collection.json`, `postman/README.md` |
| Pruebas | ver `tasks.md`; dobles `InMemoryAccountRepository`, `InMemoryOutboxRepository`, `InMemoryFirebaseUserDirectory` |
| No se tocan | `V1` a `V5`, `pom.xml`, `Dockerfile`, `.github/**`, `RegisterUserService`, el publicador y el relevo |

## 6. Riesgos

| Riesgo | Mitigación |
|---|---|
| Quitar `@Transactional` de la activación | Una sola escritura; `open-in-view=false`; la E2E lo cubre |
| Un cliente que hoy envía `false` y confiaba en Firebase deja de activar | Es el defecto que corrige CA-1.2.6; el Gateway envía el valor del token; aviso a Frontend |
| Cambio de textos en `GET /users/me` | Frontend resuelve por `code`; aviso A3 |
| `ErrorCodeDocumentationTest` exige cada código en `docs/errores.md` | T-0 siembra las filas antes del código |
| El `ALTER` del `CHECK` sobre una tabla con filas | La migración no cambia filas; prueba con datos como `MigracionEventoSalienteConDatosTest` |
| `@Modifying` sin transacción | El borrado vive dentro de `PendingAccountDeletionService` (`@Transactional`) |
| El Job en `prod` sin variables del broker no arranca | Acción A1 de la spec |
| Primera ejecución en staging borra cuentas reales viejas sin verificar | Es lo pedido; se comprueba antes en local y Newman. Las cuentas de prueba de Testing con más de 7 días sin verificar desaparecerán: aviso a Testing con el despliegue |

## 7. Estimación

≈ 17 h en Cuentas (spec §15 sin el Gateway).
