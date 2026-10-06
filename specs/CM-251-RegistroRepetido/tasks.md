# Tareas — CM-251-RegistroRepetido

Estado: sin ejecutar; spec y plan pendientes de aprobación de Paula. Un solo PR. Se marca `[x]` solo con la salida real de las pruebas pegada en el informe.

## Reglas para todas las tarjetas (el modelo que ejecuta no lee la spec ni el plan)

- Ruta base del código: `src/main/java/tech/cameia/cuentas/`; pruebas: `src/test/java/tech/cameia/cuentas/`. Identificadores en inglés; Javadoc, comentarios, mensajes y logs en español; **sin** `CM-NNN` ni rutas a otros archivos en comentarios; sin abreviaturas; en una clase existente se mantiene su estilo (el servicio actual usa nombres en español como `directorio`, `repositorio`, `compensar`).
- Prohibido: agregar dependencias, tocar migraciones, registrar contraseñas o correos, cambiar un texto de mensaje que la tarjeta no nombre, refactorizar fuera de la tarjeta.
- Comandos: `./mvnw.cmd -q -B -Dtest=<Clase> test` para una clase; `./mvnw.cmd -B test` para la suite; `./mvnw.cmd -B clean verify` al cerrar. Las pruebas con Testcontainers se omiten sin Docker: si se omiten, decirlo en el informe.
- **Detenerse y reportar** si: la tarjeta contradice el código real (el servicio cambió con CM-36), falta un dato, una prueba existente se rompe sin causa clara, una librería se comporta distinto de lo que la tarjeta afirma, o hace falta algo no listado.
- Definición de terminado: pruebas nuevas en verde, suite completa en verde, `LayeredArchitectureTest` en verde, diff dentro de lo estimado.
- Rama `CM-251-registro-repetido-pendiente` desde `develop`. Commit: `CM-251 | <tipo>(cuentas): <resultado> [IA-ASISTIDO]`.

## [ ] T-1 · Puerto, adaptador y doble en memoria — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-RR-05, 06. **Modificar:** `domain/port/FirebaseUserDirectory.java`, `infrastructure/client/FirebaseUserDirectoryAdapter.java`, `src/test/.../infrastructure/client/InMemoryFirebaseUserDirectory.java`.
- **Puerto** (junto a `createUser`):
  ```java
  /**
   * Busca la credencial de un correo.
   *
   * @param email correo normalizado
   * @return el identificador del usuario, o vacío si Firebase no tiene una credencial con ese correo
   * @throws IllegalStateException si Firebase no responde o rechaza la consulta
   */
  Optional<String> findUidByEmail(EmailAddress email);
  ```
- **Adaptador:**
  ```java
  @Override
  public Optional<String> findUidByEmail(EmailAddress email) {
      try {
          return Optional.of(firebaseAuth.getUserByEmail(email.value()).getUid());
      } catch (FirebaseAuthException error) {
          if (AuthErrorCode.USER_NOT_FOUND.equals(error.getAuthErrorCode())) {
              return Optional.empty();
          }
          // El mensaje no incluye el correo: es un dato personal
          throw new IllegalStateException("Firebase no pudo consultar la credencial del correo", error);
      }
  }
  ```
- **Doble en memoria:** cambiar `HashMap` por `ConcurrentHashMap` en los tres mapas/conjuntos (el de verificados con `ConcurrentHashMap.newKeySet()`); marcar `createUser` como `synchronized` (comprobar y registrar el correo debe ser atómico); agregar
  ```java
  private volatile boolean fallarAlConsultar;
  @Override public Optional<String> findUidByEmail(EmailAddress email) {
      if (fallarAlConsultar) { throw new IllegalStateException("Firebase no pudo consultar la credencial del correo"); }
      return correosPorUid.entrySet().stream().filter(e -> e.getValue().equals(email.value())).map(Map.Entry::getKey).findFirst();
  }
  public void fallarAlConsultar() { this.fallarAlConsultar = true; }
  ```
  y que `dejarDeFallar()` también la apague. Agregar `public int cantidadDeUsuarios()` (tamaño de `correosPorUid`) y `public void crearSinCuentaLocal(String correo)` si no existe otra forma de crear una credencial sin fila (usa `createUser` con una contraseña fija de prueba).
- **Trampa:** hay otros implementadores de `FirebaseUserDirectory` en pruebas o configuraciones: `git grep -n "implements FirebaseUserDirectory"` y completarlos.
- **Verificación:** `./mvnw.cmd -B test-compile` sin errores.

## [ ] T-2 · Resultado y servicio — ≤ 30 min, ≈ 90 líneas — **la forma del cuerpo del 200 espera la pregunta 1**

- **Cubre:** REQ-RR-01 a 10. **Crear:** `application/service/RegisterUserResult.java`. **Modificar:** `application/service/RegisterUserService.java`.
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
- **Servicio** (después de las validaciones y antes de `createUser`; la estructura final, con las validaciones existentes al inicio sin cambios):
  ```java
  public RegisterUserResult register(RegisterUserCommand command) {
      // ... validaciones existentes (correo, contraseña, fecha, celular, políticas, nombres) ...
      Optional<String> existente = directorio.findUidByEmail(email);
      if (existente.isPresent()) {
          return atenderCorreoExistente(existente.get(), false);
      }
      return registrarNueva(command, email, password, birthDate, phoneNumber);
  }

  private RegisterUserResult registrarNueva(...) {   // ≤ 3 parámetros: agrupar en un record interno si hace falta
      String firebaseUid;
      try {
          firebaseUid = directorio.createUser(email, password);
      } catch (EmailAlreadyRegisteredException carrera) {
          // Otra petición creó la credencial entre la consulta y la creación: se evalúa de nuevo, una sola vez
          String uid = directorio.findUidByEmail(email).orElseThrow(EmailAlreadyRegisteredException::new);
          return atenderCorreoExistente(uid, true);
      }
      ... (el bloque try/catch con assignFreePlanClaim, Account.register, save y compensar, sin cambios) ...
      return new RegisterUserResult(guardada, true);
  }

  private RegisterUserResult atenderCorreoExistente(String firebaseUid, boolean trasConflicto) {
      Account cuenta = repositorio.findByFirebaseUid(firebaseUid).orElseThrow(() -> {
          registrarCredencialSinCuenta(firebaseUid, trasConflicto);
          return new EmailAlreadyRegisteredException();
      });
      if (!cuenta.isPendingVerification()) {
          throw new EmailAlreadyRegisteredException();   // activa, bloqueada o anonimizada: no se distingue
      }
      logger.info("Registro repetido atendido con la cuenta pendiente del usuario {}", firebaseUid);
      return new RegisterUserResult(cuenta, false);
  }

  private void registrarCredencialSinCuenta(String firebaseUid, boolean trasConflicto) {
      if (trasConflicto) {
          logger.warn("Credencial sin cuenta local aún, uid {}; la otra petición no terminó de guardarla", firebaseUid);
      } else {
          logger.error("Credencial sin cuenta local en Firebase, uid {}; requiere conciliación manual", firebaseUid);
      }
  }
  ```
  (El parámetro booleano `trasConflicto` se acepta porque son dos métodos privados de una sola clase; si el revisor prefiere, se reemplaza por un `enum Origen`.) Actualizar el Javadoc de la clase y de `register` (devuelve el resultado; documenta `@throws EmailAlreadyRegisteredException` para S3, S4 y la carrera) y quitar «crea la credencial» como única descripción.
- **Pruebas existentes a actualizar:** `RegisterUserServiceTest` ahora usa `register(...).account()`; el caso `elCorreoRepetido…` (si existe) cambia a S2.
- **Trampa:** `atenderCorreoExistente` **no** llama a `save`, `createUser`, `assignFreePlanClaim` ni `deleteUser`; no recibe ni usa la contraseña (REQ-RR-08). `registrarNueva` conserva `compensar` exactamente como está.
- **Verificación:** `./mvnw.cmd -B -Dtest=RegisterUserServiceTest test` en verde tras ajustar las llamadas.

## [ ] T-3 · Controlador y OpenAPI — ≤ 20 min, ≈ 50 líneas

- **Cubre:** REQ-RR-01, 02, 11. **Modificar:** `presentation/controller/UserRegistrationController.java`.
- **Cambio:**
  ```java
  RegisterUserResult resultado = servicio.register(request.toCommand());   // o el comando armado a mano, según el estado tras CM-36
  HttpStatus estado = resultado.created() ? HttpStatus.CREATED : HttpStatus.OK;
  return ResponseEntity.status(estado).body(RegisteredUserResponse.de(resultado.account()));
  ```
- **OpenAPI:** `@Operation(summary = "Registra una cuenta nueva o devuelve la pendiente de verificación", description = "…")` explicando que repetir el registro con un correo cuya cuenta sigue pendiente devuelve la misma cuenta; `@ApiResponses`: `201` (cuenta creada), `200` (la cuenta ya existía pendiente de verificar; mismo cuerpo), `409` (el correo ya tiene una cuenta en otro estado o sin cuenta local, `EMAIL_ALREADY_REGISTERED`), `422` (validación, `VALIDATION_FAILED`) y `500` (`INTERNAL_ERROR`). Si T-1B.7 de CM-36 ya agregó estas anotaciones, solo se completa el `200`.
- **Verificación:** `./mvnw.cmd -B -Dtest=UserRegistrationControllerTest test` (ajustar los `when(servicio.register(...))` para devolver `new RegisterUserResult(cuenta, true)`).

## [ ] T-4 · Pruebas del servicio — ≤ 30 min, ≈ 170 líneas

- **Cubre:** REQ-RR-01 a 10, 12. **Modificar:** `RegisterUserServiceTest` (con `InMemoryFirebaseUserDirectory` y el repositorio en memoria que usa la clase). Para los registros de log: `@ExtendWith(OutputCaptureExtension.class)` y `CapturedOutput salida` (de `org.springframework.boot.test.system`).
- **Pruebas** (nombres en español camelCase; datos: correo `ana@cameia.tech`, contraseña `frase secreta larga`):
  1. `unCorreoNuevoCreaLaCuentaYDiceQueLaCreo`: `creada()` es `true`, un usuario en el directorio, una cuenta guardada.
  2. `repetirElRegistroConLaCuentaPendienteDevuelveLaMismaCuentaSinEscribirNada`: registrar dos veces; la segunda → `creada()` es `false`, `account().getId()` igual al de la primera; `directorio.cantidadDeUsuarios()` es 1; el repositorio guardó una sola vez; el directorio no recibió otro `assignFreePlanClaim` ni `deleteUser` (contadores o `fallarAl…`).
  3. `unCorreoConOtraGrafiaEsElMismoCorreo`: primero `ana@cameia.tech`, luego `  ANA@Cameia.Tech ` → segunda es S2.
  4. `unaCuentaActivaBloqueadaOAnonimizadaDaElMismoConflicto` (`@ParameterizedTest @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "DISABLED", "ANONYMIZED"})`): preparar la fila con ese estado (usar `Account.rebuild(...)` como en `ActivateAccountServiceTest`) y la credencial en el directorio; `assertThatThrownBy(...).isInstanceOf(EmailAlreadyRegisteredException.class)` con el **mismo mensaje** en los tres.
  5. `unaCredencialSinCuentaLocalDaConflictoYQuedaRegistrada`: credencial sin fila → `EmailAlreadyRegisteredException`; la salida contiene el `uid`, `conciliación manual` y **no** contiene `ana@cameia.tech`.
  6. `siFirebaseFallaAlConsultarNoSeCreaNada`: `directorio.fallarAlConsultar()` → `IllegalStateException`; cero usuarios y cero filas.
  7. `laCarreraSeResuelveConUnaSolaConsultaMas`: usar un doble de directorio que en la primera consulta devuelve vacío y falla `createUser` con `EmailAlreadyRegisteredException` mientras la segunda consulta devuelve el `uid` de una cuenta pendiente ya guardada → resultado `creada()` `false`. Variante con la fila ausente → `EmailAlreadyRegisteredException` y la salida contiene `WARN` y no `conciliación manual`. Variante con la segunda consulta vacía → `EmailAlreadyRegisteredException` sin tercer intento (contar llamadas a `createUser`: 1).
  8. `unCuerpoInvalidoNoLlegaAFirebase`: contraseña de 3 caracteres con un correo ya registrado → `WeakPasswordException` y el doble no recibió ninguna consulta (contador de `findUidByEmail` en 0; agregarlo al doble).
  9. `elRegistroRepetidoNoUsaLaContrasenia`: la salida de log completa de un registro repetido no contiene `frase secreta larga`.
- **Verificación:** `./mvnw.cmd -B -Dtest=RegisterUserServiceTest test` en verde.

## [ ] T-5 · Pruebas del adaptador y del controlador — ≤ 30 min, ≈ 110 líneas

- **Cubre:** REQ-RR-01 a 03, 05, 12. **Modificar:** `FirebaseUserDirectoryAdapterTest`, `UserRegistrationControllerTest`.
- **Adaptador** (con `errorDeFirebase(...)` como las pruebas existentes): `devuelveElUidDelCorreoExistente` (`getUserByEmail` devuelve un `UserRecord` simulado con `getUid()` = `uid-firebase`); `unCorreoSinCredencialDevuelveVacio` (`errorDeFirebase(AuthErrorCode.USER_NOT_FOUND)` → `Optional.empty()`); `cualquierOtroErrorDeFirebaseAlConsultarEsUnFalloTecnico` (`INVALID_ID_TOKEN` → `IllegalStateException`, mensaje sin `ana@cameia.tech`).
- **Controlador:** `elRegistroNuevoDevuelveCreado` (resultado `creada=true` → 201 y cuerpo con `status` `PENDING_VERIFICATION` y `plan` `FREE`); `elRegistroRepetidoDevuelve200ConElMismoCuerpo` (`creada=false` → 200 y el **mismo** cuerpo JSON que el del 201 para la misma cuenta); `elConflictoLlevaSuCodigo` (la excepción → 409, `$.code` `EMAIL_ALREADY_REGISTERED`).
- **Verificación:** `./mvnw.cmd -B -Dtest='FirebaseUserDirectoryAdapterTest,UserRegistrationControllerTest' test` en verde.

## [ ] T-6 · Punta a punta con base real — ≤ 30 min, ≈ 120 líneas

- **Cubre:** REQ-RR-02, 03, 04, 05, 10. **Modificar:** `AccountRegistrationEndToEndTest`. Copiar la forma de las pruebas existentes (`registrar`, `uidGuardado`, `estadoGuardado`, `cuentasGuardadas`).
- **Cambiar:** `elSegundoRegistroConElMismoCorreoRespondeConflicto` (línea 83) pasa a `elSegundoRegistroConElMismoCorreoDevuelveLaCuentaPendiente`: segundo registro → 200, el `id` del cuerpo es igual al del primero, `cuentasGuardadas()` es 1, `directorio.cantidadDeUsuarios()` es 1.
- **Agregar:** (1) `elReintentoNoCambiaLasFechasDeLaCuenta`: leer `fecha_creacion` y `fecha_actualizacion` con `SELECT` antes y después del segundo registro; iguales. (2) `elTerceroRepiteElRegistroYRecibeLoMismo`: tres registros → 201, 200, 200 con el mismo `id`. (3) `unCorreoConCuentaActivaDaConflictoSinRevelarElEstado`: registrar, activar con `X-User-Email-Verified: true` (como la prueba de la línea 62), registrar de nuevo → 409; y con `UPDATE microcuentas.cuenta SET estado = 'DISABLED'` → 409 con **el mismo cuerpo** (comparar los dos textos de respuesta). (4) `unaCredencialSinCuentaLocalDaConflicto`: `directorio.crearSinCuentaLocal("ana@cameia.tech")` y registrar → 409; sin filas. (5) `siFirebaseFallaAlConsultarRespondeErrorGenericoSinDatos`: `directorio.fallarAlConsultar()` → 500, el cuerpo contiene `Ocurrió un error. Inténtalo de nuevo.` y no el texto de la excepción; cero filas; `@AfterEach` o `limpiarEstado` llama `dejarDeFallar()`.
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest=AccountRegistrationEndToEndTest test` en verde; sin Docker, decirlo.

## [ ] T-7 · Concurrencia con base real — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-RR-06. **Crear:** `src/test/.../RegistroConcurrenteEndToEndTest.java` (copiar la anotación de clase, el contenedor, los campos y `limpiarEstado` de `AccountRegistrationEndToEndTest`; no heredar).
- **Prueba** `dosRegistrosSimultaneosNuncaDanError500NiDuplican`: repetir 20 veces con un correo distinto por vuelta: `CountDownLatch` de salida para lanzar dos registros idénticos en dos hilos (`ExecutorService` de 2 hilos); recoger los dos códigos de estado. Aserciones: el conjunto `{estado1, estado2}` contiene exactamente un 201; el otro es 200 o 409; ninguno es 500; al terminar la vuelta hay exactamente una fila y `directorio.cantidadDeUsuarios()` es 1. No asumir cuál hilo gana.
- **Trampas:** el doble en memoria debe ser seguro entre hilos (T-1); `TestRestTemplate` es seguro entre hilos; limpiar el estado entre vueltas.
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest=RegistroConcurrenteEndToEndTest test` en verde **tres veces seguidas**; si falla alguna, pegar la salida y reportar (no aumentar tiempos de espera).

## [ ] T-8 · Cobertura y revisión — ≤ 20 min

- `./mvnw.cmd -B clean verify`; cobertura JaCoCo de `RegisterUserService`, `RegisterUserResult`, `FirebaseUserDirectoryAdapter` y `UserRegistrationController` (≥ 90 % de líneas y ramas; cada rama sin cubrir con su razón). `/simplify`, `/code-review high`, `/security-review` (flujo de registro público y exposición de identificadores).

## [ ] T-9 · V-02: causa de CM-241 — ≤ 30 min, **sin cambios en el repositorio**

- Con el emulador de Firebase Auth (arranque con emulador: `CLAUDE.md` y `docs` del repo) y la base local, levantar Cuentas con una copia **no comprometida** de la configuración de pruebas que hace esperar 11 s a `createUser` (`Thread.sleep(11_000)` en una subclase temporal del adaptador, descartada al terminar). Ejecutar `curl --max-time 10 -X POST http://localhost:8081/api/v1/users -H "Content-Type: application/json" -d '{...cuerpo válido...}'` (datos de prueba): el cliente corta a los 10 s; verificar con `SELECT count(*) FROM microcuentas.cuenta` que la fila **sí** existe; repetir el POST: debe responder 200 con la misma cuenta.
- **Informe:** tiempos medidos (con y sin la espera), qué vio el cliente y qué quedó en la base. Si se confirma, es la causa de CM-241 y el hallazgo va a Frontend (CM-250). Si no, reportar lo observado y detenerse.

## [ ] T-10 · Entrega — ≤ 30 min

- Documentación: si existe `docs/errores.md`, el 409 ya figura; agregar la fila del 200 en el contrato del registro de la documentación del repo si la hay. Aviso a Frontend: el registro puede responder 200 o 201 con el mismo cuerpo; el reintento ya no da 409 mientras la cuenta esté pendiente.
- PR: título `CM-251 | fix(cuentas): registrar de nuevo con una cuenta pendiente devuelve la cuenta existente [IA-ASISTIDO]`, plantilla completa, atributos de calidad (fiabilidad, seguridad, compatibilidad de contrato, observabilidad), evidencia de T-7 y T-9, qué es mecánico (ajustes de `register(...)` en pruebas) y qué importa revisar (`RegisterUserService`, `FirebaseUserDirectoryAdapter`). Después del PR: tarjeta de Jira a «En revisión» y comentario con el formato del `CLAUDE.md`.
