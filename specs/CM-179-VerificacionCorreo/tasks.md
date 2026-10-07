# Tareas — CM-179-VerificacionCorreo (cameia-cuentas)

Estado: sin ejecutar; spec y plan pendientes de aprobación de Paula. Este archivo tiene las tarjetas del **bloque 1** (PR 1: activación), del **bloque 2** (PR 2: purga) y del **bloque 4** (PR 4: modo de tarea); las del bloque 3 están en `cameia-gateway/specs/CM-179-VerificacionCorreo/tasks.md`. Se marca `[x]` solo con la salida real de las pruebas.

## Reglas para todas las tarjetas (el modelo que ejecuta no lee la spec ni el plan)

- Ruta base del código: `src/main/java/tech/cameia/cuentas/`; pruebas: `src/test/java/tech/cameia/cuentas/`. Identificadores en inglés; Javadoc, comentarios, mensajes y logs en español; **sin** `CM-NNN` ni rutas a otros archivos en comentarios; sin abreviaturas; en una clase existente se mantiene su estilo (nombres de pruebas en español camelCase).
- Prohibido: agregar dependencias, tocar migraciones, registrar contraseñas, correos o tokens, cambiar un texto que la tarjeta no nombre, refactorizar fuera de la tarjeta.
- Comandos: `./mvnw.cmd -q -B -Dtest=<Clase> test`; `./mvnw.cmd -B test`; `./mvnw.cmd -B clean verify` al cerrar. Las pruebas con Testcontainers se omiten sin Docker: decirlo.
- **Detenerse y reportar** si: la tarjeta contradice el código real (CM-36 y CM-251 cambiaron el manejador y el servicio de registro), falta un dato, una prueba existente se rompe sin causa clara, o hace falta algo no listado.
- Definición de terminado: pruebas nuevas en verde, suite completa en verde, `LayeredArchitectureTest` en verde, diff dentro de lo estimado.
- Rama `CM-179-verificacion-correo-cuentas` desde `develop`. Commit: `CM-179 | <tipo>(cuentas): <resultado> [IA-ASISTIDO]`.

## [ ] T-1.1 · Excepciones y códigos — ≤ 30 min, ≈ 110 líneas — **texto de `EMAIL_NOT_VERIFIED` por la pregunta 3; textos de `DISABLED` por la pregunta 2**

- **Cubre:** REQ-VC-05, 06, 07, 08. **Crear:** `domain/exception/AccountDisabledException.java`, `AccountConflictException.java`, `InvalidIdentityException.java`. **Modificar:** `domain/exception/ErrorCode.java` (agregar `ACCOUNT_DISABLED`, `ACCOUNT_CONFLICT` e `IDENTITY_INVALID_VALUE`, cada una con su Javadoc), `domain/exception/EmailNotVerifiedException.java`, `domain/model/Account.java` (método `activate`, líneas 100-105), `AccountTest`.
- **Excepciones** (molde de `AccountNotFoundException`: constructor sin parámetros, constante `MENSAJE`, Javadoc):
  ```java
  public class AccountDisabledException extends BusinessException {
      private static final String MENSAJE = "Esta cuenta está deshabilitada.";
      public AccountDisabledException() { super(ErrorCode.ACCOUNT_DISABLED, MENSAJE); }
  }
  public class AccountConflictException extends BusinessException {
      private static final String MENSAJE = "La cuenta se está actualizando. Inténtalo de nuevo.";
      public AccountConflictException() { super(ErrorCode.ACCOUNT_CONFLICT, MENSAJE); }
  }
  public class InvalidIdentityException extends BusinessException {
      private static final String MENSAJE = "La identidad de la petición no es válida.";
      public InvalidIdentityException() { super(ErrorCode.IDENTITY_INVALID_VALUE, MENSAJE); }
  }
  ```
- **`EmailNotVerifiedException`:** `MENSAJE` pasa a `"Tu correo aún no está verificado."`.
- **`Account.activate()`** (antes → después):
  ```java
  // antes
  if (status != AccountStatus.PENDING_VERIFICATION) {
      throw new IllegalStateException("Una cuenta en estado " + status + " no se activa al verificar el correo");
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
  El Javadoc cambia: `@throws AccountDisabledException` y `@throws AccountNotFoundException`. `ACTIVE` sigue saliendo antes con `return` (idempotente).
- **Pruebas** (`AccountTest`, estilo de la clase): activar `DISABLED` lanza `AccountDisabledException` con `getErrorCode()` `ACCOUNT_DISABLED` y el estado sigue `DISABLED`; activar `ANONYMIZED` lanza `AccountNotFoundException` y el estado sigue `ANONYMIZED`; activar `ACTIVE` no cambia nada; activar `PENDING_VERIFICATION` da `ACTIVE`. Reemplazar la prueba existente que esperaba `IllegalStateException`.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=AccountTest test` en verde; `LayeredArchitectureTest` en verde.

## [ ] T-1.2 · El adaptador traduce el conflicto de versión — ≤ 20 min, ≈ 40 líneas

- **Cubre:** REQ-VC-08. **Modificar:** `infrastructure/persistence/repository/AccountRepositoryAdapter.java` (método `save`, líneas 45-55) y `AccountRepositoryAdapterTest`.
- **Cambio:** envolver el guardado:
  ```java
  try {
      return mapeador.toDomain(repositorio.save(entidad));
  } catch (ObjectOptimisticLockingFailureException conflicto) {
      // Otra escritura cambió la fila entre su lectura y este guardado: el dominio no conoce JPA
      throw new AccountConflictException();
  }
  ```
  (`org.springframework.orm.ObjectOptimisticLockingFailureException`). Actualizar el Javadoc con `@throws AccountConflictException`.
- **Prueba** (`AccountRepositoryAdapterTest`; si la clase usa base real, esta prueba puede usar un `AccountJpaRepository` simulado con Mockito en una clase nueva `AccountRepositoryAdapterConflictTest`): el repositorio JPA lanza `ObjectOptimisticLockingFailureException` en `save` → el adaptador lanza `AccountConflictException`; cualquier otra excepción no se traduce.
- **Verificación:** `./mvnw.cmd -q -B -Dtest='AccountRepositoryAdapterTest,AccountRepositoryAdapterConflictTest' test` en verde.

## [ ] T-1.3 · Servicio de activación — ≤ 30 min, ≈ 100 líneas

- **Cubre:** REQ-VC-01 a 10. **Modificar:** `application/command/ActivateAccountCommand.java`, `application/service/ActivateAccountService.java`, `ActivateAccountServiceTest`.
- **Comando:** `record ActivateAccountCommand(String firebaseUid, boolean emailVerified)`; Javadoc: «`emailVerified` es `true` solo cuando el Gateway afirmó la verificación; la ausencia o cualquier otro valor llegan como `false`».
- **Servicio** (se elimina la dependencia de `FirebaseUserDirectory`, el método `estaVerificado` y `@Transactional`; el constructor queda `ActivateAccountService(AccountRepository repositorio)`):
  ```java
  private static final int MAX_IDENTITY_LENGTH = 128;

  public Account activate(ActivateAccountCommand command) {
      String firebaseUid = exigirIdentidad(command.firebaseUid());
      Account cuenta = repositorio.findByFirebaseUid(firebaseUid).orElseThrow(AccountNotFoundException::new);

      if (!command.emailVerified()) {
          throw new EmailNotVerifiedException();   // el estado de verificación sale solo del token
      }
      if (cuenta.getStatus() == AccountStatus.ACTIVE) {
          return cuenta;                            // idempotente: no se escribe
      }
      cuenta.activate();                            // DISABLED y ANONYMIZED lo rechazan con su excepción
      try {
          Account activada = repositorio.save(cuenta);
          logger.info("Cuenta activada tras verificar el correo del usuario {}", firebaseUid);
          return activada;
      } catch (AccountConflictException conflicto) {
          return releerTrasConflicto(firebaseUid);
      }
  }

  private Account releerTrasConflicto(String firebaseUid) {
      Account actual = repositorio.findByFirebaseUid(firebaseUid).orElseThrow(AccountNotFoundException::new);
      if (actual.getStatus() == AccountStatus.ACTIVE) {
          return actual;                            // la otra petición ya la activó
      }
      throw new AccountConflictException();
  }

  private String exigirIdentidad(String firebaseUid) {
      if (firebaseUid == null || firebaseUid.isBlank()
              || firebaseUid.codePointCount(0, firebaseUid.length()) > MAX_IDENTITY_LENGTH) {
          throw new InvalidIdentityException();
      }
      return firebaseUid;
  }
  ```
  Actualizar el Javadoc de la clase (ya no consulta a Firebase; por qué no es transaccional) y de `activate` (`@throws` de cada excepción).
- **Pruebas** (`ActivateAccountServiceTest`; el repositorio en memoria de la clase gana `int lecturas` y un modo para lanzar `AccountConflictException` en el primer `save`; el doble de Firebase deja de usarse en esta clase):
  1. `activaLaCuentaCuandoElTokenDiceQueElCorreoEstaVerificado` (existente, con `new ActivateAccountCommand(UID, true)`).
  2. `sinElEncabezadoNoActivaNiConsultaAFirebase`: `new ActivateAccountCommand(UID, false)` con la cuenta pendiente → `EmailNotVerifiedException` y la cuenta sigue `PENDING_VERIFICATION` (reemplaza `consultaAFirebaseCuandoElGatewayNoPropagaElEncabezado`).
  3. `unEncabezadoEnFalsoNoSeContrastaConFirebase`: reemplaza `noSeFiaDeUnEncabezadoEnFalsoSiFirebaseDiceQueSiEstaVerificado`; `false` → `EmailNotVerifiedException` aunque Firebase diga que sí (el servicio ya no recibe el directorio: la prueba comprueba el rechazo y el constructor de una sola dependencia).
  4. `activarDosVecesNoVuelveAGuardar` (existente).
  5. `fallaCuandoElUsuarioNoTieneCuentaLocal` (existente, `AccountNotFoundException`).
  6. `unaCuentaBloqueadaDaSuExcepcion`: `DISABLED` con `true` → `AccountDisabledException` (reemplaza la expectativa `IllegalStateException`).
  7. `unaCuentaAnonimizadaSeTrataComoSinCuenta`: `ANONYMIZED` con `true` → `AccountNotFoundException` con el mismo mensaje que `fallaCuandoElUsuarioNoTieneCuentaLocal`.
  8. `laIdentidadVaciaOMuyLargaSeRechazaSinConsultarLaBase` (`@ParameterizedTest`): `""`, `"   "` y 129 caracteres → `InvalidIdentityException` y `lecturas` en 0; 128 caracteres pasa (la cuenta no existe: `AccountNotFoundException`, lo que prueba que superó la validación).
  9. `siPierdeLaCarreraYLaOtraPeticionYaActivoDevuelveLaCuenta`: primer `save` lanza `AccountConflictException`; la relectura devuelve la cuenta `ACTIVE` → resultado `ACTIVE`; variante: la relectura devuelve `PENDING_VERIFICATION` → `AccountConflictException`.
  10. `elRechazoNoRegistraElCorreoNiDatosPersonales` (con `OutputCaptureExtension`): tras un rechazo, la salida no contiene `ana@` ni el nombre `Ana`.
- **Trampa:** sin `@Transactional`, `Account` leída y guardada ya no comparten sesión: el adaptador vuelve a leer antes de guardar (código actual), no cambiar eso.
- **Verificación:** `./mvnw.cmd -B -Dtest=ActivateAccountServiceTest test` en verde.

## [ ] T-1.4 · Controlador y manejador — ≤ 30 min, ≈ 90 líneas — **textos por las preguntas 2 y 3**

- **Cubre:** REQ-VC-01, 05 a 08, 11. **Modificar:** `presentation/controller/AccountActivationController.java`, `presentation/advice/BusinessExceptionHandler.java`, `domain/exception/ErrorCode.java` si falta algo, `AccountActivationControllerTest`, `BusinessExceptionHandlerTest`.
- **Controlador** (antes → después):
  ```java
  // antes
  @RequestHeader(value = "X-User-Email-Verified", required = false) Boolean correoVerificado) {
      Account cuenta = servicio.activate(new ActivateAccountCommand(firebaseUid, correoVerificado));
  // después
  @RequestHeader(value = "X-User-Email-Verified", required = false) String correoVerificado) {
      // Solo el valor exacto «true» que firma el Gateway cuenta como verificado; cualquier otro valor es «no verificado»
      boolean verificado = "true".equals(correoVerificado);
      Account cuenta = servicio.activate(new ActivateAccountCommand(firebaseUid, verificado));
  ```
  `X-User-Id` sigue como `@RequestHeader("X-User-Id") String firebaseUid` (ausente → `ServletRequestBindingException` → 400 `IDENTITY_REQUIRED` por el manejador de CM-36 1A). **OpenAPI:** `@Operation(summary = "Activa la cuenta tras verificar el correo", description = "…")` y `@ApiResponses`: `200` (cuenta activa, incluida la repetición), `400` (`IDENTITY_REQUIRED`, `IDENTITY_INVALID_VALUE`), `403` (`EMAIL_NOT_VERIFIED`, `ACCOUNT_DISABLED`), `404` (`ACCOUNT_NOT_FOUND`), `409` (`ACCOUNT_CONFLICT`), `500` (`INTERNAL_ERROR`); `@Parameter` de cada encabezado (descripción: «lo pone el Gateway; no lo envía el navegador»; `X-User-Email-Verified`: «solo `true` activa»).
- **Manejador** (molde de `correoSinVerificar`): tres métodos, uno por excepción: `AccountDisabledException` → 403, título «Cuenta deshabilitada»; `AccountConflictException` → 409, título «Conflicto de cuenta»; `InvalidIdentityException` → 400, título «Identidad no válida». Todos con `error.getErrorCode()` y `error.getMessage()`.
- **Pruebas del controlador** (MockMvc standalone con el manejador real): `true` → 200 y `$.status` `ACTIVE`; encabezado `false`, ausente, vacío, `abc` y `TRUE` → el servicio recibe `emailVerified=false` (`ArgumentCaptor`) y, con el servicio simulado lanzando `EmailNotVerifiedException`, 403 y `$.code` `EMAIL_NOT_VERIFIED`; cuerpo `{"emailVerified":true}` con encabezado ausente → sigue sin verificar; `X-User-Id` ausente → 400 `IDENTITY_REQUIRED`; `DISABLED` → 403 `ACCOUNT_DISABLED` con el mensaje; conflicto → 409 `ACCOUNT_CONFLICT`; identidad inválida → 400 `IDENTITY_INVALID_VALUE`. **Manejador:** una prueba por excepción nueva en `BusinessExceptionHandlerTest` (estado, `code`, título y mensaje).
- **Verificación:** `./mvnw.cmd -B -Dtest='AccountActivationControllerTest,BusinessExceptionHandlerTest' test` en verde.

## [ ] T-1.5 · Punta a punta con base real — ≤ 30 min, ≈ 130 líneas

- **Cubre:** REQ-VC-02, 03, 05, 06, 08, 09. **Modificar:** `AccountRegistrationEndToEndTest` (copiar la forma de `registraLaCuentaYLuegoLaActivaAlVerificarElCorreo`, línea 62, y de `sinVerificarElCorreo…`, línea 106). Ayuda: `activar(String uid, String verificado)` que arma los encabezados `X-User-Id` y, si no es `null`, `X-User-Email-Verified`.
- **Pruebas nuevas:** (1) `activarCambiaElEstadoYLaFechaDeActualizacion`: leer `fecha_actualizacion` y `version` tras el registro; activar; `estado` = `ACTIVE`, `fecha_actualizacion` posterior y `version` +1. (2) `activarDeNuevoNoCambiaNada` (CA-1.2.4): segunda activación → 200 y `fecha_actualizacion` y `version` iguales a los de la primera. (3) `unaCuentaBloqueadaDa403`: `UPDATE ... SET estado = 'DISABLED'` → 403 y el cuerpo contiene `ACCOUNT_DISABLED`. (4) `unaCuentaAnonimizadaDa404ComoSiNoExistiera`: `UPDATE ... SET estado = 'ANONYMIZED', fecha_eliminacion = now()` (la restricción `ck_cuenta_anonimizacion` exige ambas) → 404 con el mismo cuerpo que un `X-User-Id` sin cuenta. (5) `elEncabezadoEnFalsoOInvalidoDa403` con `false`, vacío y `abc`; la cuenta sigue `PENDING_VERIFICATION`. (6) `laActivacionNoNecesitaAFirebase`: `directorio.fallarAlConsultar()` (de CM-251; si no existe, un `fallarAlBorrar()` basta: el punto es que activar con `true` no llama al directorio) → 200. (7) `dosActivacionesSimultaneasTerminanBien`: dos hilos con una barrera `CountDownLatch`, repetido 10 veces con cuentas distintas: ambos 200; `estado` `ACTIVE`; `version` aumentó una sola vez; ninguno 500.
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest=AccountRegistrationEndToEndTest test` en verde (tres veces seguidas para (7)); sin Docker, decirlo.

## [ ] T-1.6 · Cobertura y revisión — ≤ 20 min

`./mvnw.cmd -B clean verify`; cobertura de `ActivateAccountService`, `Account`, `AccountRepositoryAdapter`, `AccountActivationController`, `BusinessExceptionHandler` y las tres excepciones (≥ 90 % de líneas y ramas, cada rama sin cubrir justificada). `/simplify`, `/code-review high`, **`/security-review`** (autenticación e identidad). Autochequeo: ¿algún mensaje de librería llega al cliente? ¿algún log con correo? ¿algún comentario con `CM-NNN`?

## [ ] T-1.7 · Entrega — ≤ 30 min

Verificar OpenAPI a mano (`/v3/api-docs` con `API_DOCUMENTATION_ENABLED=true`; adjuntar el fragmento de la ruta). Aviso a Frontend: la activación exige el token verificado (un `false` o la ausencia dan 403); códigos nuevos 400, 403 y 409; cambia el texto del 403. PR: `CM-179 | fix(cuentas): activación estricta con el token, sin 500 por estado de cuenta y segura ante concurrencia [IA-ASISTIDO]`, plantilla completa, atributos de calidad (seguridad, fiabilidad, compatibilidad de contrato, observabilidad), qué es mecánico (constructores y pruebas) y qué importa revisar (`ActivateAccountService`, `Account.activate`, el adaptador). Tarjeta de Jira a «En revisión» solo al abrir el PR.

---

# Bloque 2 — purga diaria (PR 2)

Rama `CM-179-purga-cuentas-sin-verificar` desde `develop` **con el bloque 1 fusionado**. Las reglas generales del principio de este archivo aplican. Además: el servicio de purga **nunca** registra correos, nombres ni el mensaje de una excepción de Firebase (puede traer datos): solo el `firebaseUid` y el nombre de la clase de la excepción.

## [ ] T-2.1 · Tipos del dominio y puertos — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-VC-22, 23, 25, 26. **Crear:** `domain/model/UserVerificationStatus.java`, `domain/model/PurgeSummary.java`. **Modificar:** `domain/port/FirebaseUserDirectory.java`, `domain/port/AccountRepository.java`.
- **Antes de empezar:** `git grep -n "isEmailVerified" -- src/main` debe mostrar solo el puerto y su adaptador. Si hay otro llamador, detenerse y reportar.
- **Esqueletos:**
  ```java
  /**
   * Estado del correo de un usuario según el directorio de credenciales.
   *
   * <p>{@code NOT_FOUND} no es un error: la credencial pudo borrarse en una ejecución
   * anterior que no alcanzó a borrar la fila local.</p>
   */
  public enum UserVerificationStatus { VERIFIED, NOT_VERIFIED, NOT_FOUND }

  /**
   * Resultado de una ejecución de la purga de cuentas sin verificar.
   *
   * @param reviewed cuentas candidatas revisadas en esta ejecución
   * @param deleted cuentas eliminadas (credencial y fila, o solo la fila)
   * @param activated cuentas que el directorio ya tenía verificadas y se activaron
   * @param failed cuentas que no se pudieron procesar; se reintentan en la siguiente ejecución
   * @param candidatesLeft {@code true} si quedaron candidatas por encima del tope
   */
  public record PurgeSummary(int reviewed, int deleted, int activated, int failed, boolean candidatesLeft) {
      /** @return un resumen sin candidatas */
      public static PurgeSummary empty() { return new PurgeSummary(0, 0, 0, 0, false); }
  }
  ```
- **`FirebaseUserDirectory`:** quitar `boolean isEmailVerified(String)`; agregar `UserVerificationStatus verificationStatus(String firebaseUid)` con Javadoc («@throws IllegalStateException si el directorio no responde o rechaza la consulta por otra causa»). En `deleteUser`, agregar al Javadoc: «Si el usuario ya no existe, no hace nada: borrar es idempotente».
- **`AccountRepository`:**
  ```java
  /**
   * Busca las cuentas que siguen sin verificar y se crearon antes del instante dado.
   *
   * @param limit instante límite, exclusivo: una cuenta creada exactamente en él no entra
   * @param max cantidad máxima de resultados
   * @return los identificadores de Firebase, de la cuenta más antigua a la más nueva
   */
  List<String> findPendingVerificationCreatedBefore(Instant limit, int max);

  /**
   * Borra la cuenta del usuario solo si sigue sin verificar.
   *
   * @param firebaseUid identificador del usuario en Firebase Auth
   * @return {@code true} si borró una fila; {@code false} si no existía o ya no estaba pendiente
   */
  boolean deletePendingByFirebaseUid(String firebaseUid);
  ```
- **Verificación:** `./mvnw.cmd -B -q compile` falla en el adaptador de Firebase, el de la base y el doble en memoria: es lo esperado; se corrigen en T-2.2 a T-2.4. No hacer commit hasta T-2.4.

## [ ] T-2.2 · Adaptador de Firebase — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-VC-22, 23. **Modificar:** `infrastructure/client/FirebaseUserDirectoryAdapter.java` (métodos `isEmailVerified` → `verificationStatus`, y `deleteUser`), `FirebaseUserDirectoryAdapterTest`, y el doble `src/test/java/tech/cameia/cuentas/infrastructure/client/InMemoryFirebaseUserDirectory.java`.
- **Código:**
  ```java
  @Override
  public UserVerificationStatus verificationStatus(String firebaseUid) {
      try {
          return firebaseAuth.getUser(firebaseUid).isEmailVerified()
                  ? UserVerificationStatus.VERIFIED : UserVerificationStatus.NOT_VERIFIED;
      } catch (FirebaseAuthException error) {
          if (error.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
              return UserVerificationStatus.NOT_FOUND;   // la credencial ya no existe
          }
          throw new IllegalStateException("Firebase no pudo confirmar el estado del correo", error);
      }
  }
  ```
  En `deleteUser`, dentro del `catch`: si el código es `USER_NOT_FOUND`, registrar en `INFO` «La credencial del usuario {} ya no existía» y volver sin lanzar; si no, lanzar como hoy.
- **Doble en memoria:** un mapa `uid → VERIFIED|NOT_VERIFIED`, un método de prueba `markVerified(String uid)`, una bandera para fallar en un uid concreto (`failOn(String uid)`) y la lista ordenada de llamadas (`calls()`), para comprobar el orden en T-2.5. `deleteUser` de un uid inexistente no lanza.
- **Pruebas (`FirebaseUserDirectoryAdapterTest`, con `FirebaseAuth` simulado como hoy):** `usuarioVerificadoDevuelveVerified`, `usuarioSinVerificarDevuelveNotVerified`, `usuarioInexistenteDevuelveNotFound` (simular `FirebaseAuthException` con `AuthErrorCode.USER_NOT_FOUND`), `otroErrorDeFirebaseLanzaIllegalState` (`AuthErrorCode.INVALID_ID_TOKEN` u otro), `borrarUnUsuarioInexistenteNoLanza`, `borrarConOtroErrorLanzaIllegalState`.
- **Trampa:** `FirebaseAuthException` no tiene constructor público sencillo en todas las versiones: copiar cómo la construyen las pruebas existentes del adaptador; si no hay ejemplo, simularla con Mockito (`mock(FirebaseAuthException.class)` y `when(e.getAuthErrorCode())`).
- **Verificación:** `./mvnw.cmd -q -B -Dtest=FirebaseUserDirectoryAdapterTest test`.

## [ ] T-2.3 · Adaptador de la base y migración del índice — ≤ 30 min, ≈ 130 líneas

- **Cubre:** REQ-VC-21, 26, 29; T5. **Crear:** `src/main/resources/db/migration/V<n>__indice_cuenta_pendiente_fecha_creacion.sql` (`<n>` = siguiente número libre: `ls src/main/resources/db/migration`). **Modificar:** `AccountJpaRepository`, `AccountRepositoryAdapter`, `AccountRepositoryAdapterTest`, `CuentaSchemaMigrationTest`.
- **Migración (contenido exacto):**
  ```sql
  -- Índice parcial para la purga diaria: solo las cuentas sin verificar, ordenadas por antigüedad.
  CREATE INDEX ix_cuenta_pendiente_fecha_creacion
      ON microcuentas.cuenta (fecha_creacion)
      WHERE estado = 'PENDING_VERIFICATION';
  ```
- **`AccountJpaRepository`:**
  ```java
  @Query("select a.firebaseUid from AccountEntity a "
          + "where a.estado = tech.cameia.cuentas.domain.model.AccountStatus.PENDING_VERIFICATION "
          + "and a.fechaCreacion < :limite order by a.fechaCreacion asc")
  List<String> findPendingFirebaseUids(@Param("limite") Instant limite, Limit cantidad);

  @Modifying
  @Query("delete from AccountEntity a where a.firebaseUid = :firebaseUid "
          + "and a.estado = tech.cameia.cuentas.domain.model.AccountStatus.PENDING_VERIFICATION")
  int deletePendingByFirebaseUid(@Param("firebaseUid") String firebaseUid);
  ```
- **Adaptador:** `findPendingVerificationCreatedBefore` llama con `Limit.of(max)`; `deletePendingByFirebaseUid` lleva `@Transactional` y devuelve `filas == 1`.
- **Trampas:** (1) sin `@Transactional` el `@Modifying` lanza `TransactionRequiredException`; (2) `Limit` es de Spring Data 3.2 o posterior: si no compila, usar `PageRequest.of(0, max)` y devolver `List<String>`; (3) en JPQL el enumerado va con su nombre completo de clase.
- **Pruebas (Testcontainers, como las existentes):** insertar por SQL cinco filas con `fecha_creacion` fija: `PENDING` de 169 h, `PENDING` de 168 h exactas, `PENDING` de 10 h, `ACTIVE` de 300 h y `DISABLED` de 300 h, con `limite = now − 168 h` calculado en la prueba → devuelve solo la de 169 h; con tres pendientes viejas y `max = 2` → las dos más antiguas en orden; `deletePendingByFirebaseUid` de una pendiente → `true` y la fila no está; de una `ACTIVE` → `false` y la fila sigue. En `CuentaSchemaMigrationTest`: `select indexdef from pg_indexes where indexname = 'ix_cuenta_pendiente_fecha_creacion'` contiene `WHERE` y `PENDING_VERIFICATION`.
- **Trampa de las filas de prueba:** `ck_cuenta_anonimizacion` exige `fecha_eliminacion` solo con `ANONYMIZED`, y `ck_cuenta_fecha_actualizacion` exige `fecha_actualizacion >= fecha_creacion`: fijar ambas fechas iguales en el `INSERT`.
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest='AccountRepositoryAdapterTest,CuentaSchemaMigrationTest' test`; sin Docker, decirlo.

## [ ] T-2.4 · Reloj y propiedad del tope — ≤ 15 min, ≈ 20 líneas

- **Modificar:** `infrastructure/config/DomainPolicyConfiguration.java` (bean `Clock clock()` que devuelve `Clock.systemUTC()`, con Javadoc «Reloj de la aplicación, siempre en UTC; las pruebas lo reemplazan por uno fijo») y `src/main/resources/application.properties` (`cuentas.purge.max-accounts-per-run=${PURGE_MAX_ACCOUNTS_PER_RUN:1000}`).
- **No cambiar** `AgePolicy` (sigue con su constructor sin argumentos).
- **Verificación:** `./mvnw.cmd -B test` compila y la suite existente sigue en verde. Commit de T-2.1 a T-2.4: `CM-179 | feat(cuentas): consulta tipada a Firebase y lectura de cuentas sin verificar [IA-ASISTIDO]`.

## [ ] T-2.5 · Servicio de purga — ≤ 30 min, ≈ 150 líneas

- **Cubre:** REQ-VC-20 a 26, 28. **Crear:** `application/service/PurgeUnverifiedAccountsService.java`, `src/test/java/tech/cameia/cuentas/application/service/PurgeUnverifiedAccountsServiceTest.java`.
- **Esqueleto:**
  ```java
  /**
   * Elimina las cuentas que no verificaron su correo en 168 horas.
   *
   * <p>Se ejecuta una vez por invocación; la hora la decide quien la dispara. Cada cuenta se
   * procesa por separado: un fallo se cuenta, se registra y no detiene a las demás, que la
   * siguiente ejecución vuelve a encontrar. Antes de borrar se consulta el directorio: si la
   * persona ya verificó su correo, la cuenta se activa en lugar de borrarse.</p>
   */
  @Service
  public class PurgeUnverifiedAccountsService {
      static final Duration MAX_AGE = Duration.ofHours(168);
      // constructor: AccountRepository, FirebaseUserDirectory, Clock,
      //              @Value("${cuentas.purge.max-accounts-per-run}") int maxAccounts

      /** @return resumen de la ejecución */
      public PurgeSummary purge() {
          Instant limite = clock.instant().minus(MAX_AGE);
          List<String> candidatas = repositorio.findPendingVerificationCreatedBefore(limite, maxAccounts + 1);
          boolean quedan = candidatas.size() > maxAccounts;
          // procesar candidatas.subList(0, min(size, maxAccounts)), acumulando contadores
          // registrar el resumen y devolverlo
      }

      // procesar(uid): devuelve el resultado de una cuenta (DELETED, ACTIVATED o FAILED)
      //   VERIFIED     -> buscar la cuenta, activate(), save
      //   NOT_VERIFIED -> directorio.deleteUser(uid); borrarFila(uid)
      //   NOT_FOUND    -> borrarFila(uid)
      //   borrarFila devuelve FAILED si deletePendingByFirebaseUid da false
      //   cualquier RuntimeException -> WARN «No se pudo purgar la cuenta del usuario {}: {}»
      //                                 con uid y error.getClass().getSimpleName(); FAILED
  }
  ```
  Métodos de ≤ 20 líneas; el resultado por cuenta puede ser un `enum` privado. El resumen se registra en `INFO`: «Purga de cuentas sin verificar: revisadas={}, eliminadas={}, activadas={}, fallidas={}, quedan candidatas={}».
- **Pruebas (sin Spring; repositorio en memoria escrito en la prueba + `InMemoryFirebaseUserDirectory`; `Clock.fixed(Instant.parse("2026-10-10T08:00:00Z"), ZoneOffset.UTC)`):**
  1. `pideLasCandidatasConElLimiteDe168Horas`: el repositorio recibe `2026-10-03T08:00:00Z` y `max + 1`.
  2. `borraPrimeroLaCredencialYDespuesLaFila`: `NOT_VERIFIED` → orden de llamadas `deleteUser(uid)` antes del borrado de la fila; `deleted=1`.
  3. `activaEnLugarDeBorrarSiYaEstaVerificada`: `VERIFIED` → la cuenta queda `ACTIVE`, no hay borrados, `activated=1`.
  4. `siLaCredencialNoExisteBorraSoloLaFila`: `NOT_FOUND` → sin `deleteUser`, fila borrada.
  5. `unFalloNoDetieneALasDemas`: tres candidatas, Firebase falla en la segunda → `deleted=2`, `failed=1`.
  6. `siNoSeBorraLaFilaCuentaComoFallida`: el repositorio devuelve `false` → `failed=1`; segunda ejecución con el doble ya sin ese uid (`NOT_FOUND`) y la fila presente → `deleted=1`.
  7. `sinCandidatasDevuelveCeros`: resumen `PurgeSummary.empty()` y el doble de Firebase sin llamadas.
  8. `respetaElTope`: `max = 3`, cuatro candidatas → `reviewed=3`, `candidatesLeft=true`; tres candidatas → `candidatesLeft=false`.
  9. `elLogNoContieneDatosPersonales` (`@ExtendWith(OutputCaptureExtension.class)`): con una cuenta cuyo nombre es `Ana` y un fallo de Firebase con mensaje `ana@correo.co`, la salida no contiene `@` ni `Ana`.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=PurgeUnverifiedAccountsServiceTest test`; `LayeredArchitectureTest` en verde (el servicio no importa Firebase ni JPA).

## [ ] T-2.6 · Punta a punta de la purga — ≤ 30 min, ≈ 100 líneas

- **Cubre:** REQ-VC-27, idempotencia. **Crear:** `src/test/java/tech/cameia/cuentas/PurgeEndToEndTest.java`, copiando la configuración de `AccountRegistrationEndToEndTest` (Testcontainers con `@ServiceConnection`, `FirebaseTestConfiguration`, puerto aleatorio).
- **Pruebas:** (1) registrar `purga@correo.co` por HTTP (201); envejecer su fila con `JdbcTemplate`: `update microcuentas.cuenta set fecha_creacion = now() - interval '169 hours', fecha_actualizacion = now() - interval '169 hours' where firebase_uid = ?`; llamar a `purge()` del bean → `deleted=1`; registrar de nuevo el mismo correo → **201** con otro `firebaseUid`. (2) Llamar a `purge()` dos veces seguidas con una cuenta vieja → la segunda devuelve `reviewed=0`. (3) Una cuenta vieja que el doble de Firebase marca verificada → queda `ACTIVE` en la base.
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest=PurgeEndToEndTest test`; sin Docker, decirlo.

## [ ] T-2.7 · Cobertura, revisión y entrega — ≤ 30 min

`./mvnw.cmd -B clean verify`; cobertura de `PurgeUnverifiedAccountsService`, los dos adaptadores, `PurgeSummary` y `UserVerificationStatus` (≥ 90 % de líneas y ramas; cada rama sin cubrir con su razón). `/simplify`, `/code-review high`, `/security-review` (borrado de datos personales). Autochequeo: ¿algún log con correo, nombre o mensaje de Firebase? ¿algún `@Transactional` que abarque la ejecución entera? PR: `CM-179 | feat(cuentas): purga diaria de cuentas sin verificar a las 168 horas [IA-ASISTIDO]`; atributos: privacidad, fiabilidad, rendimiento (índice parcial), observabilidad (resumen). Qué importa revisar: el servicio y el borrado condicionado; qué es mecánico: los dobles y la migración. Aviso a DevOps: ninguno nuevo (el Job ya está pedido).

---

# Bloque 4 — modo de tarea para el Cloud Run Job (PR 4)

Rama `CM-179-modo-tarea-purga` desde `develop` **con el bloque 2 fusionado**.

## [ ] T-4.1 · Perfil y ejecutor — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-VC-40, 42. **Crear:** `src/main/resources/application-purge-job.properties` y `infrastructure/config/PurgeJobRunner.java`; prueba `src/test/java/tech/cameia/cuentas/infrastructure/config/PurgeJobRunnerTest.java`.
- **Propiedades (contenido exacto):**
  ```properties
  # Modo tarea: ejecuta la purga de cuentas sin verificar una vez y termina. Sin servidor web.
  spring.main.web-application-type=none
  ```
- **Ejecutor:**
  ```java
  /**
   * Ejecuta la purga de cuentas sin verificar una sola vez al arrancar en modo tarea.
   *
   * <p>Solo existe con el perfil {@code purge-job}, que usa el Cloud Run Job disparado a diario.
   * La aplicación no abre ningún puerto en ese modo y termina al acabar la purga.</p>
   */
  @Component
  @Profile("purge-job")
  class PurgeJobRunner implements ApplicationRunner {
      // constructor con PurgeUnverifiedAccountsService
      @Override
      public void run(ApplicationArguments argumentos) {
          PurgeSummary resumen = servicio.purge();
          if (resumen.failed() > 0 || resumen.candidatesLeft()) {
              logger.warn("La purga terminó con cuentas pendientes: fallidas={}, quedan candidatas={}",
                      resumen.failed(), resumen.candidatesLeft());
          }
      }
  }
  ```
  El resumen completo ya lo registra el servicio en `INFO`; aquí no se repite.
- **Pruebas (sin Spring, servicio simulado):** `ejecutaLaPurgaUnaSolaVez` (`verify(servicio, times(1)).purge()`); `avisaSiQuedanFallidas` (resumen con `failed=2`, salida capturada contiene «fallidas=2»); `noAvisaSiTodoSalioBien`.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=PurgeJobRunnerTest test`.

## [ ] T-4.2 · Salida del proceso y prueba del perfil — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-VC-41, 42. **Modificar:** `CuentasApplication.java`. **Crear:** `src/test/java/tech/cameia/cuentas/PurgeJobProfileTest.java`.
- **`main`:**
  ```java
  public static void main(String[] args) {
      ConfigurableApplicationContext contexto = SpringApplication.run(CuentasApplication.class, args);
      // En modo tarea el proceso termina al acabar la purga y devuelve el código al Job.
      if (contexto.getEnvironment().acceptsProfiles(Profiles.of("purge-job"))) {
          System.exit(SpringApplication.exit(contexto));
      }
  }
  ```
  Mantener la indentación con tabulador que tiene hoy el archivo.
- **Pruebas (`@SpringBootTest(webEnvironment = NONE)`, `@ActiveProfiles({"test", "purge-job"})` o el perfil de pruebas que usen las existentes, Testcontainers y `FirebaseTestConfiguration`; el servicio de purga como `@MockitoBean`):** (1) el contexto tiene un bean `PurgeJobRunner` y `purge()` se llamó una vez; (2) el contexto no es `WebServerApplicationContext`. Otra clase o método con `@SpringBootTest` sin `purge-job`: el contexto **no** tiene `PurgeJobRunner` (puede ir en `CuentasApplicationTests` como una afirmación más). (3) Con el servicio simulado lanzando `IllegalStateException`, `new SpringApplicationBuilder(CuentasApplication.class).profiles(...).run()` lanza (el arranque falla, que en el Job es salida distinta de 0).
- **Trampa:** no llamar a `CuentasApplication.main` en las pruebas (ejecutaría `System.exit`).
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest='PurgeJobProfileTest,CuentasApplicationTests' test`.

## [ ] T-4.3 · Guarda del emulador en el Job — ≤ 20 min, ≈ 30 líneas — P-JOB-1 respondida (Paula, 6-oct): sí

- **Modificar:** `infrastructure/config/FirebaseConfiguration.java` (constante y método que detecta el despliegue, líneas ~63 y ~155-160): considerar desplegado si existe `K_SERVICE` **o** `CLOUD_RUN_JOB`, o si está activo `prod`. Actualizar el Javadoc y el mensaje de error («… despliegue (K_SERVICE o CLOUD_RUN_JOB definida o perfil 'prod') …»).
- **Prueba (`FirebaseConfigurationTest`, copiando la de `K_SERVICE`):** con `FIREBASE_AUTH_EMULATOR_HOST=localhost:9099` y `CLOUD_RUN_JOB=cuentas-purga`, el arranque falla con ese mensaje.

## [ ] T-4.4 · Documentación, prueba manual y entrega — ≤ 30 min

- **`CLAUDE.md` del repo:** en «Verificación de cambios de código» agregar «Modo tarea (purga de cuentas sin verificar)», con el comando `java -jar target/cuentas-<versión>.jar --spring.profiles.active=local,purge-job` y la explicación: no abre puerto, ejecuta la purga una vez, sale con 0 aunque haya cuentas fallidas y con un código distinto de 0 si no pudo empezar. En producción lo arranca el Cloud Run Job con `SPRING_PROFILES_ACTIVE=prod,purge-job`. `.env.example`: línea comentada `# PURGE_MAX_ACCOUNTS_PER_RUN=1000` con su comentario.
- **Prueba manual:** `./mvnw.cmd -B -DskipTests package`, arrancar la base local con `docker compose up -d` (servicio de base de datos del repo) y ejecutar el comando de arriba; anotar en el PR la salida del resumen y el código de salida real (`echo $?`).
- `./mvnw.cmd -B clean verify`, cobertura de lo nuevo ≥ 90 %, `/code-review high`. PR: `CM-179 | feat(cuentas): modo de tarea para ejecutar la purga desde un Cloud Run Job [IA-ASISTIDO]`. Aviso a DevOps (por el documento a DevOps, punto 1): comando y variable exactos del Job.
