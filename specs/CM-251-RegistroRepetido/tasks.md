# Tareas — CM-251-RegistroRepetido

Estado: sin ejecutar; spec y plan pendientes de aprobación de Paula. Dos PR: **A** (T-1 a T-9) y **B** (T-10 a T-12). Se marca `[x]` solo con la salida real de las pruebas pegada en el informe.

## Reglas para todas las tarjetas (el modelo que ejecuta no lee la spec ni el plan)

- Ruta base del código: `src/main/java/tech/cameia/cuentas/`; pruebas: `src/test/java/tech/cameia/cuentas/`. Identificadores en inglés; Javadoc, comentarios, mensajes y logs en español; **sin** `CM-NNN` ni rutas a otros archivos en comentarios; sin abreviaturas; en una clase existente se mantiene su estilo (el servicio actual usa nombres en español como `directorio`, `repositorio`, `compensar`).
- Prohibido: agregar dependencias, tocar migraciones, registrar contraseñas o correos, cambiar un texto de mensaje que la tarjeta no nombre, refactorizar fuera de la tarjeta. Los editores convierten los escapes `\uXXXX` de Java en el carácter real: no escribir escapes Unicode en el código.
- Todo código de error cuyo estado, texto u origen cambia se actualiza en `docs/errores.md` en el mismo PR (`Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba`). `ErrorCodeDocumentationTest` lo vigila.
- Comandos: `./mvnw.cmd -q -B -Dtest=<Clase> test` para una clase; `./mvnw.cmd -B test` para la suite; `./mvnw.cmd -B clean verify` al cerrar. Las pruebas con Testcontainers se omiten sin Docker: si se omiten, decirlo en el informe.
- **Detenerse y reportar** si: la tarjeta contradice el código real, falta un dato, una prueba existente se rompe sin causa clara, una librería se comporta distinto de lo que la tarjeta afirma, o hace falta algo no listado. Reportar todo error con su salida real.
- Definición de terminado: pruebas nuevas en verde, suite completa en verde, `LayeredArchitectureTest` y `UntypedExceptionClassificationTest` en verde, diff dentro de lo estimado.
- Rama `CM-251-registro-repetido-pendiente` desde `develop`. Commit: `CM-251 | <tipo>(cuentas): <resultado>` más el trailer `Co-Authored-By`; el commit no lleva `[IA-ASISTIDO]`.

## [ ] T-1 · Puerto, adaptador y doble en memoria — ≤ 40 min, ≈ 120 líneas

- **Cubre:** REQ-RR-04, 05, 06. **Crear:** `domain/model/DirectoryUser.java`. **Modificar:** `domain/port/FirebaseUserDirectory.java`, `infrastructure/client/FirebaseUserDirectoryAdapter.java`, `domain/exception/EmailAlreadyRegisteredException.java`, `src/test/.../infrastructure/client/InMemoryFirebaseUserDirectory.java`.
- **Registro:**
  ```java
  /**
   * Credencial de un usuario en el directorio.
   *
   * @param uid identificador del usuario
   * @param createdAt instante en que se creó la credencial
   * @param disabled {@code true} si la credencial está deshabilitada
   */
  public record DirectoryUser(String uid, Instant createdAt, boolean disabled) { }
  ```
- **Puerto** (junto a `createUser`, con Javadoc que documente `@throws DependencyUnavailableException` y `@throws InvalidEmailException`):
  ```java
  Optional<DirectoryUser> findByEmail(EmailAddress email);
  ```
- **Adaptador:** extraer del `catch` de `createUser` el bloque que traduce `INVALID_EMAIL` a un método privado `translateRejection(FirebaseAuthException error, String rejectionMessage)` que devuelve la `RuntimeException` (igual que hoy: `InvalidEmailException.createInvalidFormat()` con el `WARN`, o `unavailableOrRejection(...)`), y usarlo en `createUser` y en el método nuevo, sin cambiar el comportamiento de `createUser`:
  ```java
  @Override
  public Optional<DirectoryUser> findByEmail(EmailAddress email) {
      try {
          UserRecord usuario = firebaseAuth.getUserByEmail(email.value());
          Instant creada = Instant.ofEpochMilli(usuario.getUserMetadata().getCreationTimestamp());
          return Optional.of(new DirectoryUser(usuario.getUid(), creada, usuario.isDisabled()));
      } catch (FirebaseAuthException error) {
          if (AuthErrorCode.USER_NOT_FOUND.equals(error.getAuthErrorCode())) {
              return Optional.empty();
          }
          // El mensaje no incluye el correo: es un dato personal
          throw translateRejection(error, "Firebase rechazó la consulta de la credencial");
      }
  }
  ```
  Si `translateRejection` lanza (`throw`) en lugar de devolver, ajustar los dos llamadores; lo importante es el comportamiento.
- **Cuota agotada:** en `unavailableOrRejection` agregar `code == ErrorCode.RESOURCE_EXHAUSTED` a las causas de indisponibilidad (límite de uso o cuota de Firebase: se resuelve esperando, no es un defecto). Actualizar su Javadoc. Aplica a todas las llamadas del adaptador.
- **Excepción:** `EmailAlreadyRegisteredException` conserva su constructor sin argumentos y agrega un segundo, `EmailAlreadyRegisteredException(String firebaseUid, boolean requiresReconciliation)`, con `getFirebaseUid()` y `requiresReconciliation()` (Javadoc: el identificador es interno, no sale en la respuesta y solo sirve para el registro del error). Sin cambio de código, estado ni mensaje.
- **Doble en memoria:** mapas con `ConcurrentHashMap` y el conjunto de verificados con `ConcurrentHashMap.newKeySet()`; `createUser` `synchronized`. Guardar por `uid` un `Instant` de creación (`Clock` fijo opcional con `reloj(Clock)`; por omisión `Clock.systemUTC()`). Agregar: `findByEmail` (cuenta cada llamada en `consultas()`), `fallarAlConsultar()` (lanza `DependencyUnavailableException` con una causa), `deshabilitar(String uid)`, `crearSinCuentaLocal(String correo, Instant creada)` (crea la credencial sin fila con esa fecha), `creaciones()` y `borrados()` (contadores de `createUser` y `deleteUser`), `cantidadDeUsuarios()`. `dejarDeFallar()` apaga también `fallarAlConsultar`.
- **Trampa:** hay otros implementadores de `FirebaseUserDirectory` en pruebas: `git grep -n "implements FirebaseUserDirectory"` y completarlos. `EmailAddress` ya llega en minúsculas y NFC: el doble compara con `equals` sobre `value()`.
- **Verificación:** `./mvnw.cmd -B test-compile` sin errores.

## [ ] T-2 · Tiempos de espera — ≤ 15 min, ≈ 25 líneas

- **Cubre:** REQ-RR-15. **Modificar:** `infrastructure/config/FirebaseConfiguration.java` (líneas 71 y 77): `CONNECT_TIMEOUT_MS = 3_000` y `READ_TIMEOUT_MS = 5_000`. Actualizar el Javadoc de ambas constantes (qué miden y por qué caben en los 30 s del Gateway con cuatro llamadas) y cualquier comentario que cite 5 s o 10 s (`git grep -n "10 s\|10_000\|5 s"` en `src` y `docs`, incluidos `README.md` y `docs/`).
- **Pruebas:** en `FirebaseConfigurationTest` (línea 169) agregar `assertThat(FirebaseConfiguration.CONNECT_TIMEOUT_MS).isEqualTo(3_000)` y `assertThat(FirebaseConfiguration.READ_TIMEOUT_MS).isEqualTo(5_000)` además de las comparaciones con las opciones del SDK.
- **Trampa:** hay una prueba que usa un servidor mudo y espera el agotamiento del tiempo (`FirebaseRejectionsWithSdkTest`); su duración baja de ≈ 10 s a ≈ 5 s: ajustar la espera máxima de la prueba, nunca subirla.
- **Verificación:** `./mvnw.cmd -B -Dtest='FirebaseConfigurationTest,FirebaseRejectionsWithSdkTest' test` en verde.

## [ ] T-3 · Resultado y servicio — ≤ 45 min, ≈ 130 líneas

- **Cubre:** REQ-RR-01 a 10, 12, 13. Ningún método con más de 3 parámetros ni de ≈ 20 líneas (se cumple con `ValidRegistration`). **Crear:** `application/service/RegisterUserResult.java`. **Modificar:** `application/service/RegisterUserService.java`.
- **Resultado:**
  ```java
  /**
   * Resultado de un registro: la cuenta y si se creó en esta llamada.
   *
   * @param account cuenta devuelta
   * @param created {@code true} si esta llamada la creó; {@code false} si ya existía pendiente de verificar
   */
  public record RegisterUserResult(Account account, boolean created) { }
  ```
- **Servicio.** Agregar `Clock clock` por un segundo constructor (el primero, el que usa Spring, pasa `Clock.systemUTC()`; mismo patrón que `AgePolicy`), y la constante `private static final Duration REGISTRO_EN_CURSO = Duration.ofSeconds(60);` con su Javadoc (cubre el peor caso del registro: cuatro llamadas de hasta 8 s). Estructura final, con las validaciones existentes sin cambios al inicio del método:
  ```java
  public RegisterUserResult register(RegisterUserCommand command) {
      // ... validaciones existentes (correo, contraseña, fecha, celular, políticas, nombres), que arman el record privado ValidRegistration ...
      Optional<DirectoryUser> existente = directorio.findByEmail(email);
      if (existente.isPresent()) {
          return atenderCorreoExistente(existente.get(), false);
      }
      return registrarNueva(registro);
  }

  /** Datos del formulario ya validados; agrupa los valores para no pasar más de tres parámetros. */
  private record ValidRegistration(EmailAddress email, RawPassword password, BirthDate birthDate,
          PhoneNumber phoneNumber, PersonName firstName, PersonName lastName, Pronoun pronoun) { }

  private RegisterUserResult registrarNueva(ValidRegistration registro) {
      String firebaseUid;
      try {
          firebaseUid = directorio.createUser(email, password);
      } catch (EmailAlreadyRegisteredException carrera) {
          // Otra petición creó la credencial entre la consulta y la creación: se evalúa de nuevo, una sola vez
          DirectoryUser creada = directorio.findByEmail(email).orElseThrow(EmailAlreadyRegisteredException::new);
          return atenderCorreoExistente(creada, true);
      }
      ... (el bloque try/catch con assignFreePlanClaim, Account.register, save y compensar, sin cambios) ...
      return new RegisterUserResult(guardada, true);
  }

  private RegisterUserResult atenderCorreoExistente(DirectoryUser credencial, boolean trasConflicto) {
      Optional<Account> fila = repositorio.findByFirebaseUid(credencial.uid());
      if (fila.isEmpty()) {
          // El nivel del registro (WARN o ERROR) lo decide el manejador con esta bandera
          boolean huerfana = !trasConflicto && antiguedad(credencial).compareTo(REGISTRO_EN_CURSO) >= 0;
          throw new EmailAlreadyRegisteredException(credencial.uid(), huerfana);
      }
      Account cuenta = fila.get();
      if (credencial.disabled() || !cuenta.isPendingVerification()) {
          throw new EmailAlreadyRegisteredException();   // bloqueada, activa o anonimizada: no se distingue
      }
      logger.info("Registro repetido atendido con la cuenta pendiente del usuario {}", credencial.uid());
      return new RegisterUserResult(cuenta, false);
  }

  private Duration antiguedad(DirectoryUser credencial) {
      return Duration.between(credencial.createdAt(), clock.instant());
  }
  ```
  `trasConflicto` es un `enum Origen { CONSULTA_PREVIA, TRAS_CONFLICTO }` privado si el revisor prefiere evitar el booleano. Un límite de exactamente 60 s cuenta como huérfana. El servicio **no** registra nada de S4: lo hace el manejador (T-4), una sola línea con `code`, `requestId` y `uid`. Actualizar el Javadoc de la clase y de `register` (devuelve el resultado; `@throws` para S3, S4, S8 y la carrera) y no describir ya el método como «crea la cuenta» a secas. Comentario de bloque sobre `atenderCorreoExistente`: explica que la contraseña no se verifica y los datos del cuerpo se ignoran.
- **Pruebas existentes a actualizar:** `RegisterUserServiceTest` usa `register(...).account()`; el caso de correo repetido cambia a S2.
- **Trampa:** `atenderCorreoExistente` **no** llama a `save`, `createUser`, `assignFreePlanClaim` ni `deleteUser`, y no recibe la contraseña (REQ-RR-08). `registrarNueva` conserva `compensar` exactamente como está.
- **Verificación:** `./mvnw.cmd -B -Dtest=RegisterUserServiceTest test` en verde tras ajustar las llamadas.

## [ ] T-4 · Controlador, manejador y OpenAPI — ≤ 40 min, ≈ 90 líneas

- **Cubre:** REQ-RR-01, 02, 11, 14. **Modificar:** `presentation/controller/UserRegistrationController.java` y `presentation/advice/BusinessExceptionHandler.java`.
- **Controlador:**
  ```java
  RegisterUserResult resultado = servicio.register(request.toCommand());
  HttpStatus estado = resultado.created() ? HttpStatus.CREATED : HttpStatus.OK;
  return ResponseEntity.status(estado).body(RegisteredUserResponse.de(resultado.account()));
  ```
  `@Operation` actualizado (summary «Registra una cuenta nueva o devuelve la pendiente de verificación», con la descripción de S2). `@ApiResponses`: `201`, `200` (la cuenta ya existía pendiente; mismo cuerpo), `409` (`EMAIL_ALREADY_REGISTERED`, con `errors`), `422`, `500` y `503`, cada error con `@ExampleObject` que incluya `code`, `requestId` y, donde aplique, `errors`. `@Schema` de `RegisteredUserResponse` sin cambios salvo que falte algún límite.
- **Manejador:** `correoRepetido` conserva estado, `title`, `detail` y `code`, y agrega la propiedad `errors` con un solo `CampoRechazado("email", "EMAIL_ALREADY_REGISTERED", error.getMessage())`, con el helper que ya existe. El `detail` sigue siendo el mensaje del correo (no pasa a «Revisa los campos marcados.»: ese texto es solo del 422). **Registro:** una sola línea por error. Sin `firebaseUid` en la excepción: el `WARN` de siempre (`rechazo`). Con `firebaseUid` y sin conciliación: `WARN` con `uid={}` y la frase «registro en curso». Con conciliación: `logger.error("Credencial sin cuenta local; requiere conciliación manual [code={}, requestId={}, uid={}]", ...)`, construyendo la respuesta con `problema(...)` y no con `rechazo`, para no registrar dos veces. El correo nunca aparece.
- **Verificación:** `./mvnw.cmd -B -Dtest='UserRegistrationControllerTest,BusinessExceptionHandlerTest' test` (ajustar los `when(servicio.register(...))` para devolver `new RegisterUserResult(cuenta, true)`).

## [ ] T-5 · Pruebas del servicio — ≤ 45 min, ≈ 220 líneas

- **Cubre:** REQ-RR-01 a 10, 12, 13. **Modificar:** `RegisterUserServiceTest` (con `InMemoryFirebaseUserDirectory`, el repositorio en memoria de la clase y un `Clock` fijo). Registros de log: `@ExtendWith(OutputCaptureExtension.class)` y `CapturedOutput salida`.
- **Pruebas** (nombres en español camelCase; datos: correo `ana@cameia.tech`, contraseña `frase secreta larga`):
  1. `unCorreoNuevoCreaLaCuentaYDiceQueLaCreo`: `created()` es `true`, un usuario, una fila.
  2. `repetirElRegistroConLaCuentaPendienteDevuelveLaMismaCuentaSinEscribirNada`: la segunda → `created()` `false`, mismo `id`; `directorio.creaciones()` es 1 y `borrados()` 0; el repositorio guardó una vez.
  3. `unCorreoConOtraGrafiaEsElMismoCorreo`: `ana@cameia.tech` y luego `  ANA@Cameia.Tech ` → S2.
  4. `unaCuentaActivaBloqueadaOAnonimizadaDaElMismoConflicto` (`@EnumSource(names = {"ACTIVE", "DISABLED", "ANONYMIZED"})`, fila con `Account.rebuild(...)`): `EmailAlreadyRegisteredException` con el **mismo mensaje** en los tres.
  5. `unUsuarioDeshabilitadoEnFirebaseConFilaPendienteDaConflicto`: `directorio.deshabilitar(uid)` → `EmailAlreadyRegisteredException`; la fila no cambia.
  6. `unaCredencialSinCuentaLocalAntigua` (`@ParameterizedTest` con 59 s, 60 s y 61 s de antigüedad): `EmailAlreadyRegisteredException` con `getFirebaseUid()` igual al `uid`; `requiresReconciliation()` es `false` con 59 s y `true` con 60 s y 61 s; el servicio no escribe ninguna línea de log sobre el caso.
  7. `siFirebaseNoRespondeAlConsultarNoSeCreaNada`: `fallarAlConsultar()` → `DependencyUnavailableException`; cero usuarios y cero filas.
  8. `laCarreraSeResuelveConUnaSolaConsultaMas`: doble que en la primera consulta devuelve vacío y falla `createUser` con `EmailAlreadyRegisteredException`, y en la segunda devuelve una credencial con fila pendiente → `created()` `false`. Variante sin fila → `EmailAlreadyRegisteredException` con `requiresReconciliation()` `false`, aunque la credencial tenga 10 min. Variante con segunda consulta vacía → `EmailAlreadyRegisteredException` y `creaciones()` igual a 1.
  9. `unCuerpoInvalidoNoLlegaAFirebase`: contraseña `123` con correo ya registrado → `WeakPasswordException`; `consultas()` es 0.
  10. `elRegistroRepetidoIgnoraLosOtrosDatosYNoUsaLaContrasenia`: segundo registro con nombre `Luz`, fecha `01/01/1990`, pronombre y celular distintos y otra contraseña → mismo `id`; el repositorio guardó una vez; `directorio.contrasenaDe(uid)` es la original; la salida de log completa no contiene ninguna de las dos contraseñas.
- **Verificación:** `./mvnw.cmd -B -Dtest=RegisterUserServiceTest test` en verde.

## [ ] T-6 · Pruebas del adaptador, del controlador y del manejador — ≤ 40 min, ≈ 150 líneas

- **Cubre:** REQ-RR-01 a 03, 05, 12, 14. **Modificar:** `FirebaseUserDirectoryAdapterTest`, `UserRegistrationControllerTest`, `BusinessExceptionHandlerTest`.
- **Adaptador** (con `errorDeFirebase(...)` como las pruebas existentes): `devuelveLaCredencialDelCorreoExistente` (`UserRecord` simulado con `getUid()`, `getUserMetadata().getCreationTimestamp()` = 1 000 y `isDisabled()` `true`; verifica los tres campos); `unCorreoSinCredencialDevuelveVacio` (`USER_NOT_FOUND`); `laIndisponibilidadAlConsultarEsUnFalloDeDependencia` (`UNAVAILABLE`, `DEADLINE_EXCEEDED` y un error de E/S sin respuesta HTTP → `DependencyUnavailableException`); `unCorreoQueFirebaseRechazaComoInvalidoEs422` (cuerpo con `INVALID_EMAIL` → `InvalidEmailException`); `laCuotaAgotadaEsUnFalloDeDependencia` (`RESOURCE_EXHAUSTED` en la consulta, en `createUser` y en `assignFreePlanClaim` → `DependencyUnavailableException`); `cualquierOtroRechazoAlConsultarEsUnFalloTecnico` (`PERMISSION_DENIED` → `IllegalStateException` cuyo mensaje no contiene `ana@cameia.tech`).
- **Controlador:** `elRegistroNuevoDevuelveCreado` (201); `elRegistroRepetidoDevuelve200ConElMismoCuerpo` (200 y cuerpo JSON idéntico al del 201 para la misma cuenta); `elConflictoLlevaSuCodigoYSuCampo` (409, `$.code` y `$.errors[0].code` `EMAIL_ALREADY_REGISTERED`, `$.errors[0].field` `email`, `$.errors[0].message` «Ese correo ya tiene una cuenta.», `$.detail` igual al mensaje); `firebaseNoDisponibleDevuelve503ConSuCodigo`.
- **Manejador:** el 409 conserva `status`, `title`, `code` y `requestId`, y agrega `errors` con un elemento. Con `CapturedOutput`: sin `firebaseUid`, una línea `WARN`; con `firebaseUid` y sin conciliación, una línea `WARN` con el `uid` y el `requestId`; con conciliación, **una** línea `ERROR` con «conciliación manual», el `code`, el `requestId` y el `uid` (y ninguna otra línea del mismo error); en ninguna aparece el correo.
- **Verificación:** `./mvnw.cmd -B -Dtest='FirebaseUserDirectoryAdapterTest,UserRegistrationControllerTest,BusinessExceptionHandlerTest' test` en verde.

## [ ] T-7 · Punta a punta con base real — ≤ 45 min, ≈ 170 líneas

- **Cubre:** REQ-RR-02, 03, 04, 05, 10, 13. **Modificar:** `AccountRegistrationEndToEndTest` (copiar la forma de las pruebas existentes: `registrar`, `uidGuardado`, `estadoGuardado`, `cuentasGuardadas`).
- **Cambiar:** `elSegundoRegistroConElMismoCorreoRespondeConflicto` pasa a `elSegundoRegistroConElMismoCorreoDevuelveLaCuentaPendiente`: segundo registro → 200, mismo `id`, `cuentasGuardadas()` 1, `directorio.cantidadDeUsuarios()` 1.
- **Agregar:** (1) `elReintentoNoCambiaLasFechasNiLaVersion`: `SELECT fecha_creacion, fecha_actualizacion, version` antes y después; iguales. (2) `veinteRepeticionesDelMismoRegistroNoProducenEfectosAdicionales`: un registro (201) y 20 repeticiones (20 veces 200), todas con el mismo `id`; al final una fila, un usuario en el directorio, fechas y `version` iguales a las del 201. (3) `unCorreoConCuentaActivaDaConflictoSinRevelarElEstado`: registrar, activar con `X-User-Email-Verified: true`, registrar de nuevo → 409; con `UPDATE microcuentas.cuenta SET estado = 'DISABLED'` → 409 con **el mismo cuerpo** salvo `requestId`; el cuerpo trae `errors[0].field` `email`. (4) `unaCredencialSinCuentaLocalDaConflicto`: `crearSinCuentaLocal(...)` y registrar → 409; cero filas. (5) `unUsuarioDeshabilitadoDaConflicto`: registrar, `directorio.deshabilitar(uid)`, registrar → 409. (6) `siFirebaseNoRespondeAlConsultarRespondeServicioNoDisponible`: `fallarAlConsultar()` → 503, `code` `DEPENDENCY_UNAVAILABLE`, cuerpo con «Ocurrió un error. Inténtalo de nuevo.» y sin el texto de la excepción; cero filas; `limpiarEstado` llama `dejarDeFallar()`. (7) `elSegundoRegistroConOtrosDatosNoCambiaLaFila`: segundo registro con otro nombre, fecha y contraseña → 200; el `SELECT` de la fila es idéntico y `contrasenaDe(uid)` es la original.
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest=AccountRegistrationEndToEndTest test` en verde; sin Docker, decirlo.

## [ ] T-8 · Concurrencia con base real — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-RR-06. **Crear:** `src/test/.../RegistroConcurrenteEndToEndTest.java` (copiar la anotación de clase, el contenedor, los campos y `limpiarEstado` de `AccountRegistrationEndToEndTest`; no heredar).
- **Prueba** `dosRegistrosSimultaneosNuncaDanError500NiDuplican`: 20 vueltas con un correo distinto por vuelta; `CountDownLatch` de salida para lanzar dos registros idénticos en dos hilos (`ExecutorService` de 2 hilos); recoger los dos estados. Aserciones: contienen exactamente un 201; el otro es 200 o 409; ninguno es 500 ni 503; al terminar la vuelta hay una fila y `cantidadDeUsuarios()` es 1. No asumir cuál hilo gana.
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest=RegistroConcurrenteEndToEndTest test` en verde **tres veces seguidas**; si falla alguna, pegar la salida y reportar (no subir tiempos de espera).

## [ ] T-9 · Documentación, cobertura y revisión del PR A — ≤ 45 min

- **Documentación:** en `docs/errores.md`: fila de `EMAIL_ALREADY_REGISTERED` (campo `email`, origen con S3/S4/S8/carrera, pruebas), fila de `DEPENDENCY_UNAVAILABLE` (origen: también la consulta por correo; mensaje de RT-05-CA01), la fila de «409 al reintentar un registro cuya credencial quedó sin cuenta local» pasa a describir el registro `WARN` o `ERROR` por antigüedad en la línea del manejador, la causa `RESOURCE_EXHAUSTED` entre las de `DEPENDENCY_UNAVAILABLE`, el texto de la restricción `uq_cuenta_firebase_uid` pasa a «inalcanzable por el registro; responde `INTERNAL_ERROR`», y el párrafo de tiempos de espera con 3 s y 5 s. ADR nuevo en `docs/adr/` (siguiente número libre) con: el 200 además del 201, el `errors` del 409, el clasificador de antigüedad y su porqué.
- **Revisión:** `./mvnw.cmd -B clean verify`; cobertura JaCoCo de `RegisterUserService`, `RegisterUserResult`, `DirectoryUser`, `FirebaseUserDirectoryAdapter`, `FirebaseConfiguration`, `UserRegistrationController` y `BusinessExceptionHandler` (≥ 90 % de líneas y ramas en lo nuevo o modificado; cada rama sin cubrir con su razón; la cobertura del repo no baja). `/simplify`, `/code-review high` y `/security-review` (flujo público y exposición de identificadores); `silent-failure-hunter` y `pr-test-analyzer` si están instalados. Revisar el OpenAPI generado contra la spec.
- **Entrega (previa pregunta a Paula):** título `CM-251 | fix(cuentas): registrar de nuevo con una cuenta pendiente devuelve la cuenta existente [IA-ASISTIDO]`, plantilla completa, atributos de calidad, matriz de seguridad con el riesgo de la exposición del `id` y el `firebaseUid` en el 200, qué es mecánico (ajustes de `register(...)` en pruebas) y qué importa revisar (`RegisterUserService`, `FirebaseUserDirectoryAdapter`, `BusinessExceptionHandler`). Después: tarjeta de Jira a «En revisión» y comentario con el formato del `CLAUDE.md` raíz.

## [ ] T-10 · V-02: causa de CM-241 — ≤ 45 min (PR B)

- Con el emulador de Firebase Auth (arranque: `README.md` del repo y `docs/`) y la base local, con la aplicación del PR A: (1) **Medir:** tras calentar, 100 registros nuevos y 100 registros repetidos con Newman (`-n 100`); reportar mínimo, mediana y p95 de cada uno, separando el tiempo en Firebase del tiempo propio, y comparar el p95 con el límite de 2 s; medir aparte el primer registro tras arrancar la aplicación (arranque en frío). (2) **Mayúsculas y Unicode:** crear `ana@correo.co` y consultar `ANA@Correo.CO` y `ÁNA@correo.co` / `ána@correo.co`; el resultado esperado es 200 en las variantes del mismo correo normalizado; si el emulador distingue, detenerse y reportar. (3) **Reproducir:** con una subclase temporal del adaptador que espera 11 s al crear el usuario (descartada al terminar, no se compromete), `curl --max-time 10 -X POST http://localhost:8081/api/v1/users -H "Content-Type: application/json" -d '{...cuerpo válido de prueba...}'`; el cliente corta a los 10 s; `SELECT count(*) FROM microcuentas.cuenta` muestra la fila; repetir el POST: 200 con la misma cuenta.
- **Informe** `docs/verificaciones/V-02-registro-interrumpido.md`: independiente (no depende de leer otro documento), con la versión probada (rama y SHA), el entorno, los tiempos medidos, qué vio el cliente y qué quedó en la base, y el hallazgo para Frontend (CM-250). Si no se confirma, reportar lo observado y detenerse.

## [ ] T-11 · Colección de Postman — ≤ 60 min, ≈ 400 líneas (PR B)

- **Crear:** `postman/cameia-cuentas.postman_collection.json` (v2.1, UTF-8), `postman/local.postman_environment.json` (solo `baseUrl`, sin secretos; puertos y rutas tomados del `README.md` y de `docker-compose.yml`, no inventados) y `postman/README.md` (cómo importar, cómo correr con Newman: `npx newman run postman/cameia-cuentas.postman_collection.json -e postman/local.postman_environment.json --reporters cli,junit --reporter-junit-export target/newman.xml`).
- **Carpeta «Registro» (`POST /api/v1/users`).** Correo sintético `prueba+{{$timestamp}}@ejemplo.test` guardado en una variable de colección en la primera petición; contraseña de prueba no real. Peticiones en orden:
  1. `201 registro nuevo`: guarda `accountId`; prueba `id` y `firebaseUid` presentes, `status` `PENDING_VERIFICATION`, `plan` `FREE`.
  2. `200 el mismo registro otra vez`: `id` igual a `accountId`.
  3. `200 el mismo correo con mayúsculas y espacios` (`"  PRUEBA+...@EJEMPLO.TEST "`): `id` igual.
  4. `200 con otros datos y otra contraseña`: `id` igual (el reintento ignora los datos).
  5. `422 cuerpo inválido con el correo existente` (`"password": "123"`): `code` `VALIDATION_FAILED`, `errors[].field` incluye `password`.
  6. `409 correo de una cuenta ya activa`: usa un correo de una cuenta activada por el procedimiento del README (activación con el emulador); `code` y `errors[0].field` `email`, mensaje «Ese correo ya tiene una cuenta.».
  7. `503 Firebase no disponible` (manual: detener el emulador antes; el README lo indica): `code` `DEPENDENCY_UNAVAILABLE`, tiempo de respuesta menor a 9 s.
- **Pruebas en cada petición de error:** estado, `Content-Type` `application/problem+json` con `charset=utf-8`, `code` esperado, `requestId` igual al encabezado `X-Request-Id`, cuerpo sin `Exception`, `SQL`, `org.` ni `tech.cameia`. En los éxitos, `Content-Type` JSON con charset.
- **Verificación:** `npx newman run ...` con la aplicación y el emulador en local: pegar el resumen (peticiones y pruebas, N pasan). La petición 7 se corre aparte.

## [ ] T-12 · Entrega del PR B — ≤ 20 min

- Cobertura: no aplica (sin código de producción); decirlo en el PR. PR sobre el PR A (o sobre `develop` si A ya se fusionó), con título `CM-251 | test(cuentas): colección de Postman y verificación del registro interrumpido [IA-ASISTIDO]`, plantilla completa y la salida de Newman y del informe de V-02 como evidencia. Se pregunta a Paula antes de subir o abrir. Aviso a Frontend (200 también es éxito, `errors` del 409, tiempo de 30 s para el registro y hallazgo de V-02). Al final, se vuelve a estimar con las horas reales y se informa a Vela.
