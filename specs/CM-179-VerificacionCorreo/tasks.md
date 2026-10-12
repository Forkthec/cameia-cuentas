# Tareas — CM-179-VerificacionCorreo (cameia-cuentas)

Estado: sin ejecutar; spec aprobada por Paula el 11-oct-2026 y `LISTA PARA EJECUTAR` (sección 21 de la spec). Tarjetas de las piezas 1 (activación), 2 (purga) y 4 (modo tarea), más Postman y cierre. Las de la pieza 3 están en `cameia-gateway/specs/CM-179-VerificacionCorreo/tasks.md`. Se marca `[x]` solo con la salida real de las pruebas.

## Reglas para todas las tarjetas (quien ejecuta no lee la spec ni el plan)

- Base: `origin/develop` `621a846`. Código en `src/main/java/tech/cameia/cuentas/`; pruebas en `src/test/java/tech/cameia/cuentas/`. Si una ruta, línea o nombre de esta tarjeta no coincide con el código, **detenerse y reportar**.
- Identificadores en inglés. Javadoc, comentarios, mensajes, `@DisplayName` y logs en español. **Pruebas nuevas:** `metodo_shouldX_whenY` en inglés más `@DisplayName` en español; las pruebas existentes en español no se renombran salvo que la tarjeta lo diga. Sin `CM-NNN` ni rutas a otros archivos en comentarios; sin abreviaturas.
- Prohibido: agregar dependencias; editar `V1` a `V5`; registrar correos, nombres, contraseñas, tokens o el mensaje de una excepción de Firebase; cambiar un texto que la tarjeta no nombre; refactorizar fuera de la tarjeta; tocar `.github/**`.
- Todo código de error nuevo o con texto cambiado se refleja en `docs/errores.md` (columnas `Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba`) en el mismo commit; `ErrorCodeDocumentationTest` lo exige.
- Comandos: por tarjeta, `./mvnw.cmd -q -B "-Dtest=<Clase>" test`. Al final (T-5.2), una sola vez, `./mvnw.cmd -B clean verify`. Las pruebas con Testcontainers necesitan Docker: si no hay, decirlo; nunca darlas por pasadas.
- Commits: `CM-179 | <tipo>(cuentas): <resultado>`, **sin** `[IA-ASISTIDO]`, con el trailer `Co-Authored-By` del modelo. El orden y el texto de cada commit están en `plan.md` §1.
- Definición de terminado de cada tarjeta: sus pruebas en verde, `LayeredArchitectureTest` y `UntypedExceptionClassificationTest` en verde.

## [x] T-0 · Preparar la rama y sembrar archivos compartidos — ≤ 30 min

Resultado: rebase sobre `621a846` sin conflictos; SHA previo en `ejecucion/evidencia/CM-179/rama-antes-del-rebase.txt`. Línea base `clean verify`: 933 pruebas, 1 error de entorno (`AccountCreatedEventEndToEndTest.relayPending_shouldPublish_whenBrokerWasDownDuringRegistration`, `AmqpTimeoutException` en `cleanState` al conectar con el RabbitMQ de Testcontainers); la clase sola, repetida, pasa 15/15 (`linea-base-reintento-evento.log`). Paula autorizó seguir. `ErrorCodeDocumentationTest` pasó con las filas sembradas.

1. `git fetch`; anotar en `ejecucion/evidencia/CM-179/rama-antes-del-rebase.txt` (carpeta de la raíz `cameia`) el SHA y el árbol de la punta (`git rev-parse HEAD HEAD^{tree}`). Confirmar que la rama no tiene remoto (`git ls-remote --heads origin CM-179-verificacion-correo-cuentas` vacío). `git rebase origin/develop`. Si hay conflictos en archivos que no sean de `specs/CM-179-*`, **detenerse**.
2. `docs/errores.md`: en la tabla de códigos, agregar las filas `ACCOUNT_DISABLED` (403, activación, —, «Esta cuenta está deshabilitada.», `AccountDisabledException`, `AccountActivationControllerTest`) y `ACCOUNT_CONFLICT` (409, activación, —, «La cuenta se está actualizando. Inténtalo de nuevo.», `AccountConflictException`, `ActivateAccountServiceTest`). Cambiar el mensaje de `EMAIL_NOT_VERIFIED` a «Tu correo aún no está verificado.», el de `ACCOUNT_NOT_FOUND` a «No encontramos una cuenta para este usuario.» y el de `IDENTITY_REQUIRED` a «La petición no incluye los datos que exige esta ruta.». En la fila de `ACCOUNT_NOT_FOUND`, el origen pasa a «sin cuenta, o cuenta anonimizada (en la activación y en la consulta)». Borrar de la sección de deuda las cuatro filas que dicen «La tarea de verificación de correo» (líneas 86 a 89 de `develop`).
3. `postman/cameia-cuentas.postman_collection.json`: agregar la carpeta vacía `Activación` después de `Mi cuenta` (la llenará T-5.1), y en `postman/README.md` su fila de la tabla con «(se completa en esta rama)».
4. **Línea base** (spec, sección 19), antes de cualquier cambio de código: `./mvnw.cmd -B -ntp clean verify` con Docker encendido → BUILD SUCCESS y 0 fallos (933 pruebas sobre `621a846`). Guardar la salida en `ejecucion/evidencia/CM-179/linea-base-cuentas.log`. Si no está en verde, detenerse y reportar.
5. **Verificación:** `./mvnw.cmd -q -B "-Dtest=ErrorCodeDocumentationTest" test`. **Falla** porque los dos códigos no existen aún en `ErrorCode`: anotarlo y seguir; T-1.1 lo pone en verde. Commit 2 de `plan.md`.

# Pieza 1 — activación

## [x] T-1.1 · Excepciones, códigos, textos y `Account.activate()` — ≤ 30 min

Resultado: `AccountTest`, `ErrorCodeTest`, `ErrorCodeDocumentationTest`, `UntypedExceptionClassificationTest` y `LayeredArchitectureTest` en verde. Trampa: `ErrorCodeTest` fijaba el vocabulario de causas; se agregaron `DISABLED` y `CONFLICT` (la spec ya los declara).

- **Cubre:** REQ-VC-01, 04, 05, 06, 07, 08 (textos). **Crear:** `domain/exception/AccountDisabledException.java`, `domain/exception/AccountConflictException.java`. **Modificar:** `domain/exception/ErrorCode.java`, `EmailNotVerifiedException.java`, `AccountNotFoundException.java`, `IdentityRequiredException.java`, `domain/model/Account.java` (método `activate`, líneas 92-110), `presentation/advice/BusinessExceptionHandler.java` (solo el texto de la línea 264), `domain/model/AccountTest.java`, `architecture/UntypedExceptionClassificationTest.java`.
- **`ErrorCode`**, después de `EMAIL_NOT_VERIFIED`:
  ```java
  /** La cuenta está deshabilitada y no se puede activar. */
  ACCOUNT_DISABLED,

  /** Otra petición cambió la cuenta al mismo tiempo; la persona puede reintentar. */
  ACCOUNT_CONFLICT,
  ```
- **Excepciones** (molde de `AccountNotFoundException`: constante `MENSAJE`, constructor sin parámetros, Javadoc de clase y de constructor):
  ```java
  /** Señala que se intentó activar una cuenta deshabilitada: verificar un correo no revierte esa decisión. */
  public class AccountDisabledException extends BusinessException {
      private static final String MENSAJE = "Esta cuenta está deshabilitada.";
      /** Crea la excepción con el mensaje que ve la persona. */
      public AccountDisabledException() { super(ErrorCode.ACCOUNT_DISABLED, MENSAJE); }
  }

  /** Señala que otra petición cambió la cuenta mientras esta intentaba guardarla. */
  public class AccountConflictException extends BusinessException {
      private static final String MENSAJE = "La cuenta se está actualizando. Inténtalo de nuevo.";
      /** Crea la excepción con el mensaje que ve la persona. */
      public AccountConflictException() { super(ErrorCode.ACCOUNT_CONFLICT, MENSAJE); }
  }
  ```
- **Textos** (antes → después): `EmailNotVerifiedException.MENSAJE` «Primero debes verificar tu correo con el enlace que te enviamos» → «Tu correo aún no está verificado.»; `AccountNotFoundException.MENSAJE` «No encontramos una cuenta para este usuario» → «No encontramos una cuenta para este usuario.»; `IdentityRequiredException.MESSAGE` y el literal de `BusinessExceptionHandler.peticionIncompleta` (línea 264) «La petición no incluye los datos que exige esta ruta» → «La petición no incluye los datos que exige esta ruta.».
- **`Account.activate()`** (antes → después):
  ```java
  // antes
  if (status != AccountStatus.PENDING_VERIFICATION) {
      throw new IllegalStateException(
              "Una cuenta en estado " + status + " no se activa al verificar el correo");
  }
  // después
  if (status == AccountStatus.DISABLED) {
      throw new AccountDisabledException();
  }
  if (status == AccountStatus.ANONYMIZED) {
      // No se revela que la cuenta existió: se responde igual que si no hubiera cuenta
      throw new AccountNotFoundException();
  }
  ```
  El Javadoc cambia `@throws IllegalStateException …` por `@throws AccountDisabledException si la cuenta está deshabilitada` y `@throws AccountNotFoundException si la cuenta fue anonimizada`. `ACTIVE` sigue saliendo antes con `return`.
- **`UntypedExceptionClassificationTest`:** quitar la entrada `MODEL_PACKAGE + "Account#activate"` (línea 61). La de `isEmailVerified` (línea 79) se queda hasta T-2.5.
- **Pruebas** (`AccountTest`): reemplazar `unaCuentaBloqueadaNoSeActivaAlVerificarElCorreo` (línea 86) por `activate_shouldThrowAccountDisabled_whenDisabled` (`@DisplayName("Una cuenta deshabilitada no se activa al verificar el correo")`; `getErrorCode()` es `ACCOUNT_DISABLED` y el estado sigue `DISABLED`) y `unaCuentaAnonimizadaNoSeActivaAlVerificarElCorreo` (línea 94) por `activate_shouldThrowAccountNotFound_whenAnonymized` (el estado sigue `ANONYMIZED`). Las dos pruebas de activación existentes siguen igual.
- **Trampa:** `ErrorCodeTest` puede fijar el orden o la cantidad de códigos; si falla, leer su aserción y ajustar solo lo que pide el código nuevo.
- **Verificación:** `./mvnw.cmd -q -B "-Dtest=AccountTest,ErrorCodeTest,ErrorCodeDocumentationTest,UntypedExceptionClassificationTest" test` en verde.

## [x] T-1.2 · El adaptador traduce el conflicto de versión — ≤ 20 min

Resultado: `AccountRepositoryAdapterConflictTest` 2/2 en verde.

- **Cubre:** REQ-VC-08. **Modificar:** `infrastructure/persistence/repository/AccountRepositoryAdapter.java` (método `save`, líneas 39-49). **Crear:** `src/test/java/tech/cameia/cuentas/infrastructure/persistence/AccountRepositoryAdapterConflictTest.java` (Mockito, sin Spring ni base).
- **Cambio** (la última línea de `save`):
  ```java
  try {
      return mapeador.toDomain(repositorio.save(entidad));
  } catch (ObjectOptimisticLockingFailureException conflicto) {
      // Otra escritura cambió la fila entre su lectura y este guardado; el dominio no conoce JPA
      throw new AccountConflictException();
  }
  ```
  (`org.springframework.orm.ObjectOptimisticLockingFailureException`). Javadoc: `@throws AccountConflictException si otra escritura cambió la fila al mismo tiempo`.
- **Pruebas:** `save_shouldThrowAccountConflict_whenVersionChanged` (el `AccountJpaRepository` simulado lanza `new ObjectOptimisticLockingFailureException(AccountEntity.class, "id")` en `save`) y `save_shouldPropagateOtherFailures_whenNotAVersionConflict` (lanza `DataIntegrityViolationException` → sale la misma).
- **Verificación:** `./mvnw.cmd -q -B "-Dtest=AccountRepositoryAdapterConflictTest" test`.

## [x] T-1.3 · Servicio de activación — ≤ 30 min

Resultado: `ActivateAccountServiceTest` 18/18 en verde.

- **Cubre:** REQ-VC-01 a 10. **Modificar:** `application/command/ActivateAccountCommand.java`, `application/service/ActivateAccountService.java`, `application/service/ActivateAccountServiceTest.java`.
- **Comando:** `public record ActivateAccountCommand(String firebaseUid, boolean emailVerified)`. Javadoc de `emailVerified`: «`true` solo cuando el Gateway afirmó la verificación del correo; la ausencia del encabezado o cualquier otro valor llegan como `false`».
- **Servicio:** se eliminan el campo `directorio`, el método `estaVerificado`, `@Transactional` y su import. Constructor `ActivateAccountService(AccountRepository repositorio)`. Javadoc de la clase: la verificación sale solo del encabezado del Gateway; no es transaccional porque el conflicto de versión ocurre al confirmar y así el servicio puede releer.
  ```java
  public Account activate(ActivateAccountCommand command) {
      String firebaseUid = command.firebaseUid();
      if (firebaseUid == null || firebaseUid.isBlank()
              || firebaseUid.length() > GetCurrentAccountService.MAX_FIREBASE_UID_LENGTH) {
          throw new IdentityRequiredException();   // misma regla que la consulta de la cuenta
      }
      Account cuenta = repositorio.findByFirebaseUid(firebaseUid).orElseThrow(AccountNotFoundException::new);
      if (!command.emailVerified()) {
          throw new EmailNotVerifiedException();   // la verificación sale solo del token
      }
      if (cuenta.getStatus() == AccountStatus.ACTIVE) {
          return cuenta;                            // idempotente: no se escribe
      }
      cuenta.activate();                            // deshabilitada y anonimizada lanzan su excepción
      try {
          Account activada = repositorio.save(cuenta);
          logger.info("Cuenta activada tras verificar el correo del usuario {}", firebaseUid);
          return activada;
      } catch (AccountConflictException conflicto) {
          return rereadAfterConflict(firebaseUid);
      }
  }

  /** La otra petición casi siempre ya la activó; si no, se pide reintentar. */
  private Account rereadAfterConflict(String firebaseUid) {
      Account actual = repositorio.findByFirebaseUid(firebaseUid).orElseThrow(AccountNotFoundException::new);
      if (actual.getStatus() == AccountStatus.ACTIVE) {
          return actual;
      }
      throw new AccountConflictException();
  }
  ```
  `GetCurrentAccountService.MAX_FIREBASE_UID_LENGTH` es package-private y está en el mismo paquete. `@throws` de cada excepción en el Javadoc de `activate`. Una cuenta `ANONYMIZED` ya lanza `AccountNotFoundException` dentro de `activate()`; con `true` y estado `ANONYMIZED`, el orden anterior da 404, que es lo pedido.
- **Pruebas** (la clase conserva su `RepositorioEnMemoria`, que gana `int lecturas` y `void failFirstSaveWithConflict(Account relectura)`; el doble de Firebase deja de usarse y se quita su import):
  1. `activaLaCuentaCuandoElGatewayConfirmaElCorreoVerificado` (existente) con `new ActivateAccountCommand(UID, true)`.
  2. Reemplaza `consultaAFirebaseCuandoElGatewayNoPropagaElEncabezado` y `noSeFiaDeUnEncabezadoEnFalsoSiFirebaseDiceQueSiEstaVerificado`: `activate_shouldRejectWithoutCallingFirebase_whenHeaderIsNotTrue` (`false` → `EmailNotVerifiedException`, la cuenta sigue `PENDING_VERIFICATION`; el constructor ya no recibe el directorio, lo que prueba que no lo consulta).
  3. `rechazaCuandoElCorreoSigueSinVerificarse` (existente) con `false`.
  4. `activarDosVecesNoFallaNiVuelveAGuardar` y `activate_shouldActivate_whenBirthDateIsUnknown` (existentes) con `true`.
  5. `fallaCuandoElUsuarioNoTieneCuentaLocal` (existente).
  6. `unaCuentaBloqueadaNoSeActivaAlVerificarElCorreo` (existente, línea 109): ahora espera `AccountDisabledException`.
  7. `activate_shouldAnswerLikeMissingAccount_whenAnonymized`: `ANONYMIZED` con `true` → `AccountNotFoundException` con el mismo `getMessage()` que la prueba 5.
  8. `activate_shouldRejectIdentityWithoutReadingDatabase_whenBlankOrTooLong` (`@ParameterizedTest @ValueSource`): `""`, `"   "`, `"\t"` y `"a".repeat(129)` → `IdentityRequiredException` y `lecturas == 0`. Aparte, `@ParameterizedTest` con `"a".repeat(127)`, `"a".repeat(128)` y `"ñ".repeat(128)` → `AccountNotFoundException` (pasó la validación).
  9. `activate_shouldReturnActiveAccount_whenLosingTheRaceToAnotherActivation`: el primer `save` lanza `AccountConflictException` y la relectura devuelve la cuenta `ACTIVE` → resultado `ACTIVE`. `activate_shouldThrowConflict_whenRereadIsStillPending`: la relectura devuelve `PENDING_VERIFICATION` → `AccountConflictException`.
  10. `activate_shouldNotLogPersonalData_whenRejected` (`@ExtendWith(OutputCaptureExtension.class)`): cuenta con nombre `Ana`, rechazo por `false` → la salida no contiene `Ana` ni `@`.
- **Trampa:** sin `@Transactional`, la lectura y el guardado no comparten sesión: el adaptador vuelve a leer antes de guardar (código actual); no cambiar eso.
- **Verificación:** `./mvnw.cmd -q -B "-Dtest=ActivateAccountServiceTest" test`.

## [x] T-1.4 · Controlador, manejador y OpenAPI — ≤ 30 min

Resultado: `AccountActivationControllerTest` 16, `BusinessExceptionHandlerTest` 37, `CurrentAccountControllerTest` 8 y `GetCurrentAccountServiceTest` en verde. Se actualizaron además los ejemplos OpenAPI de `CurrentAccountController` y las aserciones con texto sin punto.

- **Cubre:** REQ-VC-01, 05, 07, 08, 11. **Modificar:** `presentation/controller/AccountActivationController.java`, `presentation/advice/BusinessExceptionHandler.java`, `AccountActivationControllerTest.java`, `BusinessExceptionHandlerTest.java`.
- **Controlador** (antes → después):
  ```java
  // antes
  @RequestHeader(value = "X-User-Email-Verified", required = false) Boolean emailVerified) {
      Account cuenta = servicio.activate(new ActivateAccountCommand(firebaseUid, emailVerified));
  // después
  @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified) {
      // Solo el texto exacto «true» que escribe el Gateway cuenta; cualquier otro valor es «no verificado»
      Account cuenta = servicio.activate(new ActivateAccountCommand(firebaseUid, "true".equals(emailVerified)));
  ```
  Javadoc de la clase: quitar «el Gateway todavía no lo emite» y «se consulta a Firebase»; decir que la verificación sale solo de ese encabezado y que su ausencia es «no verificado». **OpenAPI** con el estilo de `CurrentAccountController` (mismo repo): `@Operation(summary = "Activa la cuenta tras verificar el correo")`, `@ApiResponses` con 200 (incluida la repetición), 400 `IDENTITY_REQUIRED`, 403 `EMAIL_NOT_VERIFIED` y `ACCOUNT_DISABLED`, 404 `ACCOUNT_NOT_FOUND`, 409 `ACCOUNT_CONFLICT`, 500 `INTERNAL_ERROR`, cada una con su ejemplo `application/problem+json`; `@Parameter` de cada encabezado («Lo pone el Gateway a partir del token; el navegador no lo envía»; para la verificación, «solo `true` activa la cuenta»).
- **Manejador** (molde de `correoSinVerificar`, línea 185):
  ```java
  /** Cuenta deshabilitada. @return {@code 403 Forbidden} */
  @ExceptionHandler(AccountDisabledException.class)
  ProblemDetail cuentaDeshabilitada(AccountDisabledException error) {
      return rechazo(HttpStatus.FORBIDDEN, "Cuenta deshabilitada", error.getMessage(), error.getErrorCode());
  }

  /** Otra petición cambió la cuenta al mismo tiempo. @return {@code 409 Conflict} */
  @ExceptionHandler(AccountConflictException.class)
  ProblemDetail conflictoDeCuenta(AccountConflictException error) {
      return rechazo(HttpStatus.CONFLICT, "Conflicto de cuenta", error.getMessage(), error.getErrorCode());
  }
  ```
- **Pruebas del controlador** (el estilo de la clase existente; métodos nuevos en inglés):
  - `laActivacionDevuelveLaCuentaActiva` (existente): sigue en verde.
  - `funcionaSinElEncabezadoDeVerificacionDelGateway` (existente, línea 54) cambia de comportamiento: se reemplaza por `activate_shouldPassNotVerified_whenHeaderIsNotTrue` y `activate_shouldPassNotVerified_whenHeaderIsMissing` (`@ParameterizedTest` con encabezado ausente, `""`, `false`, `TRUE`, `" true"` y `abc`): el servicio recibe `emailVerified=false` (`ArgumentCaptor`); con el servicio simulado lanzando `EmailNotVerifiedException` → 403, `$.code` `EMAIL_NOT_VERIFIED`, `$.detail` «Tu correo aún no está verificado.» y `Content-Type` con `charset=UTF-8`. Con `abc`, nunca 500.
  - `activate_shouldIgnoreBody_whenItClaimsVerification` (`@ParameterizedTest`): cuerpos `{"emailVerified":true}`, `{"status":"ACTIVE"}` y el texto `no es json`, sin encabezado → el servicio recibe `false`; nunca 400 ni 500.
  - `activate_shouldReturn403AccountDisabled_whenAccountIsDisabled`, `activate_shouldReturn409AccountConflict_whenConflictPersists` y `activate_shouldReturn400IdentityRequired_whenIdentityIsBlank` (servicio simulado lanzando cada excepción), con su `code`, título y mensaje literal.
  - `sinIdentidadDelGatewayLaPeticionNoLlegaAlCasoDeUso` (existente): ahora su `detail` lleva punto final; actualizar la aserción del texto si la tiene.
- **Manejador:** en `BusinessExceptionHandlerTest`, una prueba por excepción nueva (estado, `code`, `title`, `detail`) y actualizar las aserciones de texto de `EMAIL_NOT_VERIFIED`, `ACCOUNT_NOT_FOUND` e `IDENTITY_REQUIRED` si las tiene. Buscar también con `git grep -n "No encontramos una cuenta para este usuario\"\|exige esta ruta\"\|Primero debes verificar" -- src/test` y actualizar cada aserción que compare el texto sin punto (por ejemplo, en `CurrentAccountControllerTest` y `CurrentAccountEndToEndTest`).
- **Verificación:** `./mvnw.cmd -q -B "-Dtest=AccountActivationControllerTest,BusinessExceptionHandlerTest,CurrentAccountControllerTest" test`. Commit 3 de `plan.md` (T-1.1 a T-1.4).

## [x] T-1.5 · Activación de punta a punta con base real — ≤ 30 min

Resultado: `AccountRegistrationEndToEndTest` 50/50 en verde, tres corridas seguidas con Docker (incluye las 7 pruebas nuevas de activación y la de concurrencia de 10 rondas; log de la tercera en `ejecucion/evidencia/CM-179/t15-e2e-activacion-3a-corrida.log`).

- **Cubre:** REQ-VC-02, 03, 05, 06, 08, 09; FIA-02. **Modificar:** `AccountRegistrationEndToEndTest.java` (copiar la forma de `registraLaCuentaYLuegoLaActivaAlVerificarElCorreo`, línea 69). Ayudante privado `ResponseEntity<String> activar(String uid, String verificado)` que pone `X-User-Id` y, si `verificado` no es `null`, `X-User-Email-Verified`.
- **Pruebas nuevas:**
  1. `activate_shouldChangeStatusAndUpdateDate_whenPending`: leer `fecha_actualizacion` y `version` tras registrar; activar con `"true"` → 200; `estado` `ACTIVE`, `fecha_actualizacion` posterior, `version` + 1.
  2. `activate_shouldNotWrite_whenAlreadyActive` (CA-1.2.4): segunda activación → 200 con el mismo cuerpo; `fecha_actualizacion` y `version` iguales a los de la primera. Repetir 20 veces: ninguna cambia nada (FIA-02).
  3. `activate_shouldReturn403_whenAccountIsDisabled`: `UPDATE microcuentas.cuenta SET estado = 'DISABLED' WHERE firebase_uid = ?` → 403 y el cuerpo contiene `ACCOUNT_DISABLED`; `estado` sigue `DISABLED`.
  4. `activate_shouldAnswerLikeMissingAccount_whenAnonymized`: `UPDATE … SET estado = 'ANONYMIZED', fecha_eliminacion = now()` (`ck_cuenta_anonimizacion` exige ambas) → 404 con el mismo `code` y `detail` que un `X-User-Id` sin cuenta.
  5. `activate_shouldReturn403AndKeepPending_whenHeaderIsNotTrue` con `"false"`, `""` y `"abc"`: 403 `EMAIL_NOT_VERIFIED`; la cuenta sigue `PENDING_VERIFICATION`.
  6. `activate_shouldNotNeedFirebase_whenHeaderIsTrue`: `directorio.fallarAlConsultar()` → 200.
  7. `activate_shouldSucceedOnce_whenTwoRequestsRace`: dos hilos con `CountDownLatch`, 10 rondas con cuentas distintas: ambas 200; `estado` `ACTIVE`; `version` aumentó exactamente 1; ninguna 500.
- **Verificación:** con Docker, `./mvnw.cmd -q -B "-Dtest=AccountRegistrationEndToEndTest" test` tres veces seguidas (por la 7). Sin Docker, decirlo. Commit 4 de `plan.md`. **Pedir aquí la revisión independiente temprana** (plan §1).

# Pieza 2 — purga y evento

## [x] T-2.1 · Migraciones — ≤ 30 min

Resultado: `V6` y `V7` (no había migraciones posteriores a `V5` en `develop`). `CuentaSchemaMigrationTest` 9/9, `EventoSalienteSchemaMigrationTest` 15/15 y `MigracionEventoSalienteConDatosTest` 1/1 en verde con Docker.

- **Cubre:** REQ-VC-27, 32. **Crear:** `src/main/resources/db/migration/V6__indice_cuenta_pendiente_fecha_creacion.sql` y `V7__evento_cuenta_eliminada.sql` (si `develop` ya tiene `V6`, usar los dos siguientes libres y decirlo). **Modificar:** `infrastructure/persistence/CuentaSchemaMigrationTest.java`, `infrastructure/persistence/EventoSalienteSchemaMigrationTest.java`.
- **Contenido exacto:**
  ```sql
  -- V6: índice parcial para la purga diaria: solo las cuentas sin verificar, por antigüedad.
  CREATE INDEX ix_cuenta_pendiente_fecha_creacion
      ON microcuentas.cuenta (fecha_creacion)
      WHERE estado = 'PENDING_VERIFICATION';
  ```
  ```sql
  -- V7: la tabla de salida admite el evento de cuenta eliminada que publica la purga.
  ALTER TABLE microcuentas.evento_saliente
      DROP CONSTRAINT ck_evento_saliente_tipo,
      ADD CONSTRAINT ck_evento_saliente_tipo CHECK (tipo IN ('cuenta.creada', 'cuenta.eliminada'));
  ```
- **Pruebas:** en `CuentaSchemaMigrationTest`, `select indexdef from pg_indexes where indexname = 'ix_cuenta_pendiente_fecha_creacion'` contiene `WHERE` y `PENDING_VERIFICATION`. En `EventoSalienteSchemaMigrationTest`, `insert_shouldAccept_whenTypeIsAccountDeleted` (insertar `'cuenta.eliminada'` pasa); `insert_shouldFail_whenTypeIsUnknown` (existente, con `cuenta.borrada`) sigue en verde.
- **Verificación:** con Docker, `./mvnw.cmd -q -B "-Dtest=CuentaSchemaMigrationTest,EventoSalienteSchemaMigrationTest,MigracionEventoSalienteConDatosTest" test`. Commit 5.

## [x] T-2.2 · Evento `cuenta.eliminada` y su contrato — ≤ 30 min

Resultado: `AccountDeletedTest` 3, `AccountDeletedPayloadV1Test` 6 y `OutboxRepositoryAdapterTest` 14 en verde (los dos nuevos de `appendAccountDeleted` y uno de convivencia con `cuenta.creada`). Trampa: `UntypedExceptionClassificationTest` exigió clasificar `AccountDeleted#<init>` como invariante defensivo (mismo precedente que `AccountCreated`).

- **Cubre:** REQ-VC-27; IOP-01. **Crear:** `domain/event/AccountDeleted.java`, `infrastructure/messaging/payload/AccountDeletedPayloadV1.java`, `docs/eventos/cuenta-eliminada-v1.md`, `docs/eventos/cuenta-eliminada-v1.schema.json`, `src/test/java/tech/cameia/cuentas/domain/event/AccountDeletedTest.java`, `src/test/java/tech/cameia/cuentas/infrastructure/messaging/payload/AccountDeletedPayloadV1Test.java`. **Modificar:** `domain/port/OutboxRepository.java`, `infrastructure/persistence/repository/OutboxRepositoryAdapter.java`, el doble `src/test/java/tech/cameia/cuentas/infrastructure/persistence/InMemoryOutboxRepository.java`.
- **Molde:** `AccountCreated` y `AccountCreatedPayloadV1` (mismo paquete, mismo estilo):
  ```java
  public record AccountDeleted(UUID eventId, String firebaseUid, Instant deletedAt, CorrelationId correlationId) {
      public static final String TYPE = "cuenta.eliminada";
      public static final int VERSION = 1;
      public AccountDeleted { /* requireNonNull de cada componente; deletedAt truncado a milisegundos */ }
  }

  public record AccountDeletedPayloadV1(@JsonProperty("usuarioId") String userId,
          @JsonProperty("eliminadaEn") String deletedAt) {
      public static AccountDeletedPayloadV1 from(AccountDeleted event) { /* mismo INSTANT_FORMAT que AccountCreatedPayloadV1 */ }
  }
  ```
  `OutboxRepository`: `boolean appendAccountDeleted(AccountDeleted event)`, con Javadoc gemelo de `appendAccountCreated`. El adaptador la implementa con `insertIfAbsent(event.eventId(), AccountDeleted.TYPE, (short) AccountDeleted.VERSION, event.firebaseUid(), payload, event.correlationId().value(), event.deletedAt())`; revisar el orden real de los parámetros en `OutboxEventJpaRepository.insertIfAbsent` (línea 43) y el acceso real al valor de `CorrelationId`. El doble gana una lista `appendedDeletions()`.
- **Contrato** `docs/eventos/cuenta-eliminada-v1.md`, con el mismo índice que `cuenta-creada-v1.md`: exchange `cuentas.events`, clave `cuenta.eliminada`, `message_id` = `eventId`, campos `usuarioId` (texto, el `firebaseUid`, 1 a 128) y `eliminadaEn` (instante UTC `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`); productor: la purga de cuentas sin verificar; consumidor conocido: Perfil (cola `perfil.cuenta-eliminada`, borra la fecha de nacimiento de su réplica). El esquema JSON exige los dos campos con `additionalProperties: false`, como el de `cuenta.creada`.
- **Pruebas:** `AccountDeletedTest` (nulos rechazados; instante truncado a milisegundos). `AccountDeletedPayloadV1Test`, copiando `AccountCreatedPayloadV1Test`: la carga serializada valida contra `docs/eventos/cuenta-eliminada-v1.schema.json` y es exactamente `{"usuarioId":"uid-1","eliminadaEn":"2026-10-10T08:00:00.000Z"}` para `Instant.parse("2026-10-10T08:00:00.123456Z")` truncado. En `OutboxRepositoryAdapterTest` (Testcontainers): `appendAccountDeleted_shouldStorePendingEvent_whenNew` y `appendAccountDeleted_shouldReturnFalse_whenAlreadyStored`.
- **Verificación:** `./mvnw.cmd -q -B "-Dtest=AccountDeletedTest,AccountDeletedPayloadV1Test,OutboxRepositoryAdapterTest" test` (la última con Docker).

## [x] T-2.3 · Transacción de borrado de la fila y su evento — ≤ 30 min

Resultado: `PendingAccountDeletionServiceTest` 3 y `PendingAccountDeletionTransactionTest` 3 en verde con Docker (fila conservada si el INSERT del evento falla; cuenta activa no se borra). Los dobles `RepositorioEnMemoria` de `ActivateAccountServiceTest` y `RegisterUserServiceTest` ganaron el método nuevo del puerto.

- **Cubre:** REQ-VC-22, 25, 27; FIA-01; IOP-04. **Modificar:** `domain/port/AccountRepository.java`, `infrastructure/persistence/repository/AccountJpaRepository.java`, `AccountRepositoryAdapter.java`, el doble `InMemoryAccountRepository`. **Crear:** `application/service/PendingAccountDeletionService.java`, `PendingAccountDeletionServiceTest.java` (sin Spring) y `PendingAccountDeletionTransactionTest.java` (Testcontainers, copiando `AccountRecordingTransactionTest`).
- **Puerto:** `boolean deletePendingByFirebaseUid(String firebaseUid)`: «Borra la cuenta del usuario solo si sigue sin verificar. @return `true` si borró una fila; `false` si no existía o ya no estaba pendiente».
- **JPA:**
  ```java
  @Modifying
  @Query("delete from AccountEntity a where a.firebaseUid = :firebaseUid "
          + "and a.estado = tech.cameia.cuentas.domain.model.AccountStatus.PENDING_VERIFICATION")
  int deletePendingByFirebaseUid(@Param("firebaseUid") String firebaseUid);
  ```
  El adaptador devuelve `filas == 1` y **no** lleva `@Transactional`: la transacción la abre el servicio.
- **Servicio** (molde de `AccountRecordingService`: reloj por constructor, `@Autowired` en el de producción):
  ```java
  /**
   * Borra la cuenta pendiente y guarda su evento cuenta.eliminada en una sola transacción.
   *
   * <p>O desaparecen la fila y aparece el evento, o no cambia nada: el evento nunca anuncia un borrado que no ocurrió.</p>
   */
  @Service
  public class PendingAccountDeletionService {
      @Transactional
      public boolean deletePending(String firebaseUid) {
          if (!accounts.deletePendingByFirebaseUid(firebaseUid)) {
              return false;
          }
          UUID eventId = UUID.randomUUID();
          AccountDeleted event = new AccountDeleted(eventId, firebaseUid, clock.instant(),
                  CorrelationId.fromRequestIdOrElse(null, eventId));
          if (!outbox.appendAccountDeleted(event)) {
              throw new IllegalStateException("La cuenta ya tiene su evento de cuenta eliminada");
          }
          return true;
      }
  }
  ```
  Agregar `PendingAccountDeletionService#deletePending` a `UntypedExceptionClassificationTest` con el motivo `DEFENSIVE_INVARIANT` (imposible con una fila recién borrada; la transacción se deshace).
- **Pruebas:** sin Spring: `deletePending_shouldRecordEvent_whenRowDeleted` (un evento con `TYPE` y el uid) y `deletePending_shouldNotRecordEvent_whenNothingDeleted`. Con base real: `deletePending_shouldKeepRow_whenEventInsertFails` (copiar cómo `AccountRecordingTransactionTest` provoca el fallo del `INSERT`, línea 46: un evento previo con el mismo `tipo` y `agregado_id`) y `deletePending_shouldNotDelete_whenAccountIsActive`.
- **Verificación:** `./mvnw.cmd -q -B "-Dtest=PendingAccountDeletionServiceTest,PendingAccountDeletionTransactionTest,UntypedExceptionClassificationTest" test`. Commit 6 (T-2.2 y T-2.3).

## [ ] T-2.4 · Lectura de candidatas — ≤ 20 min

- **Cubre:** REQ-VC-21, 29, 32. **Modificar:** `AccountRepository`, `AccountJpaRepository`, `AccountRepositoryAdapter`, `InMemoryAccountRepository`, `src/test/java/tech/cameia/cuentas/infrastructure/persistence/AccountRepositoryAdapterTest.java`.
- **Puerto:** `List<String> findPendingVerificationCreatedBefore(Instant limit, int max)`: «@param limit instante, exclusivo: una cuenta creada exactamente en él no entra; @return los `firebaseUid`, de la más antigua a la más nueva».
- **JPA** (JPQL, con el nombre completo del enumerado):
  ```java
  @Query("select a.firebaseUid from AccountEntity a "
          + "where a.estado = tech.cameia.cuentas.domain.model.AccountStatus.PENDING_VERIFICATION "
          + "and a.fechaCreacion < :limite order by a.fechaCreacion asc, a.id asc")
  List<String> findPendingFirebaseUids(@Param("limite") Instant limite, Limit cantidad);
  ```
  Si `Limit` no compila con la versión de Spring Data del repo, usar `Pageable` con `PageRequest.of(0, max)`.
- **Pruebas** (Testcontainers): insertar por SQL cinco filas con `fecha_creacion = fecha_actualizacion` fijas (`ck_cuenta_fecha_actualizacion` exige `fecha_actualizacion >= fecha_creacion`): pendiente de 169 h, pendiente de 168 h exactas, pendiente de 10 h, `ACTIVE` de 300 h, `DISABLED` de 300 h; con `limite = now − 168 h` calculado en la prueba → solo la de 169 h. Con tres pendientes viejas y `max = 2` → las dos más antiguas, en orden.
- **Verificación:** con Docker, `./mvnw.cmd -q -B "-Dtest=AccountRepositoryAdapterTest" test`.

## [ ] T-2.5 · Consulta tipada a Firebase — ≤ 20 min

- **Cubre:** REQ-VC-22, 26. **Antes:** `git grep -n "isEmailVerified" -- src/main` debe mostrar solo el puerto y su adaptador (T-1.3 quitó el otro uso). Si hay otro, detenerse. **Crear:** `domain/model/UserVerificationStatus.java`. **Modificar:** `FirebaseUserDirectory`, `FirebaseUserDirectoryAdapter` (líneas 266-280), `InMemoryFirebaseUserDirectory` (línea 131), `FirebaseUserDirectoryAdapterTest`, `UntypedExceptionClassificationTest` (quitar la entrada de `isEmailVerified`, línea 79).
- **Código:**
  ```java
  /** Estado del correo de un usuario según el directorio. {@code NOT_FOUND} no es un error: la credencial pudo borrarse antes. */
  public enum UserVerificationStatus { VERIFIED, NOT_VERIFIED, NOT_FOUND }
  ```
  ```java
  @Override
  public UserVerificationStatus verificationStatus(String firebaseUid) {
      try {
          return firebaseAuth.getUser(firebaseUid).isEmailVerified()
                  ? UserVerificationStatus.VERIFIED : UserVerificationStatus.NOT_VERIFIED;
      } catch (FirebaseAuthException error) {
          if (AuthErrorCode.USER_NOT_FOUND.equals(error.getAuthErrorCode())) {
              return UserVerificationStatus.NOT_FOUND;
          }
          throw unavailableOrRejection(error, "Firebase rechazó la consulta del estado del correo");
      }
  }
  ```
  Javadoc del puerto: `@throws DependencyUnavailableException si el directorio no respondió o falló de su lado`. El doble devuelve `VERIFIED` si `marcarCorreoVerificado` se llamó para ese uid, `NOT_FOUND` si no existe, `NOT_VERIFIED` en otro caso, y respeta `fallarAlConsultar()` lanzando `DependencyUnavailableException`. Agregar al doble `void rejectLookupOf(String uid)` (lanza `IllegalStateException` solo para ese uid).
- **Pruebas** (`FirebaseUserDirectoryAdapterTest`, con la forma en que la clase ya simula `FirebaseAuthException`): `verificationStatus_shouldReturnVerified_whenEmailVerified`, `…NotVerified…`, `…NotFound_whenUserDoesNotExist`, `verificationStatus_shouldThrowDependencyUnavailable_whenFirebaseIsDown` (código `UNAVAILABLE`), `verificationStatus_shouldThrowIllegalState_whenRejectedForOtherReason`. Quitar las de `isEmailVerified`.
- **Verificación:** `./mvnw.cmd -q -B "-Dtest=FirebaseUserDirectoryAdapterTest,UntypedExceptionClassificationTest" test`. Commit 7 (T-2.4 y T-2.5).

## [ ] T-2.6 · Propiedades del tope y resumen — ≤ 15 min

- **Cubre:** REQ-VC-28, 29. **Crear:** `infrastructure/config/PurgeProperties.java`, `domain/model/PurgeSummary.java`, prueba `infrastructure/config/PurgePropertiesTest.java` (copiar `AccountEventsPropertiesTest`). **Modificar:** `infrastructure/config/DomainPolicyConfiguration.java` (`@EnableConfigurationProperties(PurgeProperties.class)`), `src/main/resources/application.properties` (`cuentas.purge.max-accounts-per-run=${PURGE_MAX_ACCOUNTS_PER_RUN:1000}`).
  ```java
  /** Configuración de la purga de cuentas sin verificar. @param maxAccountsPerRun cuentas como máximo por ejecución */
  @Validated
  @ConfigurationProperties("cuentas.purge")
  public record PurgeProperties(@Min(1) @Max(1000) int maxAccountsPerRun) { }

  /** Resultado de una ejecución. */
  public record PurgeSummary(int reviewed, int deleted, int activated, int failed, int orphanCredentialsDeleted,
          boolean candidatesLeft) { }
  ```
- **Pruebas:** 0 y 1001 hacen fallar el arranque; 1 y 1000 arrancan.
- **Verificación:** `./mvnw.cmd -q -B "-Dtest=PurgePropertiesTest" test`.

## [ ] T-2.7 · Servicio de purga — ≤ 30 min

- **Cubre:** REQ-VC-20 a 26, 28, 29, 31. **Crear:** `application/service/PurgeUnverifiedAccountsService.java`, `PurgeUnverifiedAccountsServiceTest.java` (sin Spring: `InMemoryAccountRepository`, `InMemoryOutboxRepository`, `InMemoryFirebaseUserDirectory`, `PendingAccountDeletionService` real sobre esos dobles, `Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC)`).
- **Esqueleto** (constructores como `AccountRecordingService`; métodos de ≤ 20 líneas; el resultado de cada cuenta es un `enum` privado `Outcome { DELETED, ACTIVATED, FAILED }`):
  ```java
  /**
   * Elimina las cuentas que no verificaron su correo en 168 horas.
   *
   * <p>Se ejecuta una vez por invocación; la hora la decide quien la dispara. Antes de borrar consulta el directorio: si la
   * persona ya verificó, la cuenta se activa. Si no, se borra la credencial y después la fila, que lleva su evento. Cada cuenta
   * es independiente: un rechazo se cuenta y no detiene a las demás. Si el directorio no está disponible, se detiene y
   * lanza, para que la tarea termine con error y se reintente.</p>
   */
  @Service
  public class PurgeUnverifiedAccountsService {
      static final Duration MAX_AGE = Duration.ofHours(168);

      public PurgeSummary purge() {
          Instant limite = clock.instant().minus(MAX_AGE);
          List<String> candidatas = accounts.findPendingVerificationCreatedBefore(limite, max + 1);
          // procesar las primeras min(size, max); acumular contadores;
          // ante DependencyUnavailableException: registrar el resumen parcial y relanzar
          // al final: registrar el resumen en INFO (texto exacto de REQ-VC-28) y devolverlo
      }
      // procesar(uid):
      //   VERIFIED     -> buscar la cuenta, activate(), save -> ACTIVATED
      //   NOT_VERIFIED -> directory.deleteUser(uid); deletion.deletePending(uid)
      //                   false -> logger.error("Cuenta activa sin credencial tras la purga, uid {}", uid) -> FAILED
      //   NOT_FOUND    -> deletion.deletePending(uid) ? DELETED : FAILED (WARN)
      //   DependencyUnavailableException -> se propaga (no se captura aquí)
      //   otra RuntimeException -> WARN «No se pudo purgar la cuenta del usuario {}: {}» con uid y
      //                            error.getClass().getSimpleName(); FAILED
  }
  ```
  La conciliación de huérfanas (T-2.8) se llama al final de `purge()` y por ahora devuelve 0.
- **Pruebas:**
  1. `purge_shouldAskForCandidatesOlderThan168Hours_whenRun`: el doble recibe `2026-10-03T08:00:00Z` y `max + 1`.
  2. `purge_shouldDeleteCredentialBeforeRowAndRecordEvent_whenNotVerified`: orden `deleteUser` → borrado de fila; un `AccountDeleted` con el uid; `deleted=1`.
  3. `purge_shouldActivateInsteadOfDeleting_whenAlreadyVerified`: `ACTIVE`, sin borrados ni evento, `activated=1`.
  4. `purge_shouldDeleteOnlyRow_whenCredentialIsGone`: sin `deleteUser`; fila borrada y evento.
  5. `purge_shouldContinue_whenOneAccountIsRejected`: tres candidatas, `rejectLookupOf` la segunda → `deleted=2`, `failed=1`.
  6. `purge_shouldLogErrorAndSkipEvent_whenRowWasActivatedMeanwhile`: el doble de la base devuelve `false` al borrar tras `deleteUser` → `failed=1`, ningún evento, la salida contiene «Cuenta activa sin credencial tras la purga».
  7. `purge_shouldReturnZeros_whenNoCandidates`: todo en 0 y el directorio sin consultas.
  8. `purge_shouldRespectLimit_whenMoreCandidatesThanMax`: `max = 3`, cuatro candidatas → `reviewed=3`, `candidatesLeft=true`; con tres → `false`.
  9. `purge_shouldNotLogPersonalData_whenFailing` (`OutputCaptureExtension`): cuenta de `Ana`, fallo con mensaje `ana@correo.co` → la salida no contiene `@` ni `Ana`.
  10. `purge_shouldStopAndRethrow_whenDirectoryIsUnavailable`: `fallarAlConsultar()` en la segunda de tres → lanza `DependencyUnavailableException`; la primera quedó procesada; la tercera intacta.
- **Verificación:** `./mvnw.cmd -q -B "-Dtest=PurgeUnverifiedAccountsServiceTest" test`; `LayeredArchitectureTest` en verde (el servicio no importa Firebase ni JPA).
- **Punta a punta** (misma tarjeta, otra clase): crear `src/test/java/tech/cameia/cuentas/PurgeEndToEndTest.java` copiando la configuración de `AccountRegistrationEndToEndTest`. (1) Registrar `purga@correo.co` (201); envejecer la fila: `update microcuentas.cuenta set fecha_creacion = now() - interval '169 hours', fecha_actualizacion = now() - interval '169 hours' where firebase_uid = ?`; llamar a `purge()` del bean → `deleted=1`; la fila no está; `evento_saliente` tiene una fila `cuenta.eliminada` pendiente con ese `agregado_id`; registrar de nuevo el mismo correo → **201**. (2) Dos `purge()` seguidas → la segunda `reviewed=0`. (3) Una cuenta vieja que el doble marca verificada → `ACTIVE` en la base, sin evento. Con Docker: `./mvnw.cmd -q -B "-Dtest=PurgeEndToEndTest" test`. Commit 8.

## [ ] T-2.8 · Conciliación de credenciales huérfanas — ≤ 30 min

Opción C de la pregunta 18, elegida por Paula el 11-oct-2026: solo credenciales sin fila, con el correo sin verificar, de más de 168 h y con único proveedor `password`.

- **Cubre:** REQ-VC-33. **Modificar:** `FirebaseUserDirectory` + adaptador + doble; `AccountRepository` + JPA + adaptador + doble; `PurgeUnverifiedAccountsService` y su prueba; `PurgeEndToEndTest`.
- **Puertos:** `List<String> findUnverifiedPasswordUsersCreatedBefore(Instant limit, int max)`: recorre `firebaseAuth.listUsers(pageToken, 1000)` hasta agotar o reunir `max`; incluye un usuario si `getProviderData()` tiene un único proveedor con `getProviderId()` igual a `"password"`, `!isEmailVerified()` y `getUserMetadata().getCreationTimestamp() < limit.toEpochMilli()`. `Set<String> findExistingFirebaseUids(Collection<String> firebaseUids)`: JPQL `select a.firebaseUid from AccountEntity a where a.firebaseUid in :uids`.
- **Servicio:** al final de `purge()`, si no se detuvo antes: candidatas huérfanas = las de la consulta a Firebase que no están en `findExistingFirebaseUids`; por cada una, `deleteUser` → `orphanCredentialsDeleted++`; un rechazo es `failed` y `WARN`; la indisponibilidad se propaga como en T-2.7. **No hay evento** (nunca hubo `cuenta.creada`).
- **Pruebas:** huérfana sin verificar de 169 h → borrada; huérfana verificada → se conserva; huérfana de 10 h → se conserva; credencial con fila → se conserva; credencial de Google (proveedor `google.com`) → se conserva. En E2E, `directorio.crearSinCuentaLocal("huerfana@correo.co", <instante de hace 169 h>)` → borrada tras `purge()`.
- **Verificación:** las de T-2.7. Commit 9.

# Pieza 4 — modo tarea

## [ ] T-4.1 · Perfil y ejecutor — ≤ 20 min

- **Cubre:** REQ-VC-50, 52. **Crear:** `src/main/resources/application-purge-job.properties`, `infrastructure/config/PurgeJobRunner.java`, `src/test/java/tech/cameia/cuentas/infrastructure/config/PurgeJobRunnerTest.java` (sin Spring; molde `AccountEventsRelayJobRunnerTest`).
- **Propiedades (exacto):**
  ```properties
  # Tarea de una sola ejecucion (Cloud Run Job): purga de cuentas sin verificar. No abre puerto HTTP.
  spring.main.web-application-type=none
  ```
- **Ejecutor** (molde de `AccountEventsRelayJobRunner`):
  ```java
  /**
   * Ejecuta una vez la purga de cuentas sin verificar.
   *
   * <p>Solo existe con el perfil {@code purge-job}, que usa el Cloud Run Job que dispara Cloud Scheduler a diario. Si la
   * purga lanza (por ejemplo, el directorio de usuarios no responde), la excepción sale y el proceso termina con un código
   * distinto de 0 para que el Job se reintente.</p>
   */
  @Component
  @Profile(PurgeJobRunner.PROFILE)
  public class PurgeJobRunner implements ApplicationRunner {
      public static final String PROFILE = "purge-job";
      @Override
      public void run(ApplicationArguments arguments) {
          PurgeSummary resumen = service.purge();
          if (resumen.failed() > 0 || resumen.candidatesLeft()) {
              logger.warn("La purga terminó con cuentas pendientes [fallidas={}, quedanCandidatas={}]",
                      resumen.failed(), resumen.candidatesLeft());
          }
      }
  }
  ```
- **Pruebas:** `run_shouldPurgeOnce_whenStarted`; `run_shouldWarn_whenSomeAccountsFailed` (salida con `fallidas=2`); `run_shouldNotWarn_whenAllSucceeded`; `run_shouldPropagate_whenPurgeThrows`.
- **Verificación:** `./mvnw.cmd -q -B "-Dtest=PurgeJobRunnerTest" test`.

## [ ] T-4.2 · Salida del proceso — ≤ 15 min

- **Cubre:** REQ-VC-51. **Modificar:** `CuentasApplication.exitWhenOneShotJob` (la condición) y su Javadoc; `CuentasApplicationTest`.
  ```java
  if (context.getEnvironment().acceptsProfiles(
          Profiles.of(AccountEventsRelayJobRunner.PROFILE + " | " + PurgeJobRunner.PROFILE))) {
  ```
  Mantener la indentación con tabuladores del archivo.
- **Pruebas** (copiar `exitWhenOneShotJob_shouldExitWithZeroAndCloseContext_whenRelayProfileIsActive`): `…_whenPurgeProfileIsActive`. La prueba con solo `local` sigue igual.
- **Verificación:** `./mvnw.cmd -q -B "-Dtest=CuentasApplicationTest" test`.

## [ ] T-4.3 · Guarda del emulador en un Job — ≤ 20 min

- **Cubre:** REQ-VC-53. **Modificar:** `infrastructure/config/FirebaseConfiguration.java` (constante de la línea 63 y método de la línea ~172) y `FirebaseConfigurationTest`.
- **Cambio:** agregar `private static final String CLOUD_RUN_JOB_VARIABLE = "CLOUD_RUN_JOB";` y considerar desplegado si existe `K_SERVICE` **o** `CLOUD_RUN_JOB`, o si está activo `prod`. Actualizar el Javadoc y el mensaje de error («… despliegue (K_SERVICE o CLOUD_RUN_JOB definida, o perfil 'prod') …»).
- **Prueba** (copiar la de `K_SERVICE`): con `FIREBASE_AUTH_EMULATOR_HOST=localhost:9099` y `CLOUD_RUN_JOB=cuentas-purga`, el arranque falla con ese mensaje.
- **Verificación:** `./mvnw.cmd -q -B "-Dtest=FirebaseConfigurationTest" test`.

## [ ] T-4.4 · Documentación y prueba manual — ≤ 30 min

- **`CLAUDE.md` del repo:** junto al bloque del relevo (línea ~165), «Purga de cuentas sin verificar (modo tarea)»: `java -jar target\cuentas-0.0.1-SNAPSHOT.jar --spring.profiles.active=local,purge-job`; no abre puerto; ejecuta la purga una vez; sale con 0 aunque haya cuentas fallidas y con un código distinto de 0 si no pudo empezar o si Firebase no responde. En Cloud Run: Job con `SPRING_PROFILES_ACTIVE=prod,purge-job`, Cloud Scheduler a las 03:00 `America/Bogota`, las mismas variables que el servicio (incluidas `SPRING_RABBITMQ_*`, que exige el perfil `prod`). En la regla del emulador (línea 139), agregar `CLOUD_RUN_JOB`. En la tabla de pendientes de DevOps (línea ~190), la fila del Job de la purga.
- **`.env.example`:** `# PURGE_MAX_ACCOUNTS_PER_RUN=1000` con su comentario (de 1 a 1000; por defecto 1000).
- **Prueba manual:** `./mvnw.cmd -B -DskipTests package`; base y emulador locales (`docker compose up -d` del repo); registrar una cuenta por HTTP, envejecerla por SQL y ejecutar el comando; anotar en `ejecucion/evidencia/CM-179/` la línea del resumen, el código de salida real (`echo $LASTEXITCODE`) y la fila `cuenta.eliminada` de `evento_saliente`. Commit 10.

# Postman, verificación final y entrega

## [ ] T-5.1 · Carpeta «Activación» de Postman — ≤ 30 min

- **Cubre:** P7 del estándar; DES-02. **Modificar:** `postman/cameia-cuentas.postman_collection.json`, `postman/README.md`. Molde: la carpeta `Mi cuenta` (peticiones con los encabezados del Gateway y comprobación de `charset`, `problem+json` y `< 2000 ms` del script de la colección).
- **Peticiones:** `A-00 · 201 registro de la cuenta que se activará` (guarda el uid como lo hace `M-00`); `A-01 · 200 activación` (`X-User-Email-Verified: true`; `$.status` `ACTIVE`); `A-02 · 200 activación repetida`; `A-03 · 403 encabezado en false`; `A-04 · 403 sin encabezado de verificación`; `A-05 · 403 encabezado abc` (nunca 500); `A-06 · 403 cuerpo que afirma verificación sin encabezado` (y una variante con un cuerpo que no es JSON); `A-07 · 400 sin X-User-Id`; `A-08 · 400 X-User-Id en blanco`; `A-09 · 400 X-User-Id de 129 caracteres`; `A-10 · 404 X-User-Id sin cuenta` (con el valor `' OR '1'='1`); `A-10b · 404 X-User-Id con 128 «ñ»`; `A-11 · 200 OpenAPI publica POST /api/v1/users/me/verification`; `A-12 · 403 identidad de otra cuenta sin la verificación del Gateway` (`X-User-Id` de la cuenta de `A-00` y sin `X-User-Email-Verified`: la cuenta no cambia). Cada una comprueba `code` y `detail` literales.
- Las respuestas 403 `ACCOUNT_DISABLED` y 409 `ACCOUNT_CONFLICT` no se pueden provocar por HTTP sin tocar la base: quedan en las pruebas automáticas, y el README lo dice.
- Commit 11 (con T-4.4 si no se hizo antes).

## [ ] T-5.2 · Verificación única sobre la punta — ≤ 30 min

- `./mvnw.cmd -B clean verify` con salida real guardada en `ejecucion/evidencia/CM-179/verify-cuentas.log`.
- Cobertura (JaCoCo, `target/site/jacoco/jacoco.csv`) de cada clase nueva o modificada, ≥ 90 % de líneas y de ramas; cada línea o rama sin cubrir con su razón.
- Newman sobre la aplicación local con el emulador: carpetas `Registro`, `Mi cuenta` y `Activación`, salida guardada.
- **Lista de verificación** de la spec (sección 20, L-01 a L-28): correrla fila por fila, anotar la salida real de cada una en `ejecucion/evidencia/CM-179/resumen.md` y corregir lo que falle. Agregar la exploración adversarial propia (cuerpo ilegible, campos extra, recurso ajeno, n−1, n y n+1, Unicode, doble envío).
- Tabla «regla R1–R14 → evidencia» en el mismo `resumen.md`, siguiendo el plan de la sección 18 de la spec, con archivo, prueba, comando y salida real. No se marca `cumple` lo que no se ejecutó.
- `/simplify`, `/code-review high`, `/security-review` (identidad, autorización y borrado de datos personales). Autochequeo: ¿algún mensaje de librería llega al cliente? ¿algún log con correo, nombre o mensaje de Firebase? ¿algún `@Transactional` que abarque toda la purga? ¿algún comentario con `CM-NNN`?

## [ ] T-5.3 · Entrega — ≤ 30 min

- Revisar `docs/bitacora-ia/` del repo: una entrada (solo el prompt) por cada decisión con peso humano de la spec (D3, D5, D6, D10, D12 y la de la pregunta 18), enlazando la spec.
- Cuerpo del PR en `ejecucion/cuerpos/` de la raíz con la plantilla del repo, validado con `.github/scripts/pr_policy.py` (`Impacto: contrato, datos, despliegue` con sus líneas `Contrato:`, `Datos:` y `Despliegue:`; `Control humano: pendiente` y PR en **Draft**). Título: `CM-179 | feat(cuentas): verificación de correo, purga diaria y modo tarea [IA-ASISTIDO]`. Guía de revisión por commit (plan §1), lo mecánico y la tabla «requisito o caso borde → prueba».
- **Push y PR solo con el sí de Paula**, como dice el `CLAUDE.md` de la raíz. Jira a «En revisión» solo con el PR abierto y el del Gateway también listo. Aviso a Frontend (spec, A3) y a Testing (las cuentas de prueba sin verificar de más de 7 días desaparecerán).
