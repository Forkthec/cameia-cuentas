# Tarjetas — CM-297: `GET /api/v1/users/me`

## Reglas para todas las tarjetas (quien ejecuta no lee la spec ni el plan)

1. **Rutas.** Código: `src/main/java/tech/cameia/cuentas/`. Pruebas: `src/test/java/tech/cameia/cuentas/`.
2. **Idioma (R1).** Inglés: identificadores, nombres de métodos de prueba y códigos. Español: Javadoc, comentarios, `@DisplayName`, OpenAPI, logs y
   mensajes. Las clases existentes con nombres en español (`servicio`, `rechazo`) conservan su estilo; lo nuevo va en inglés.
3. **Errores.** Nunca 500 por una entrada del cliente. `application/problem+json;charset=UTF-8` con `code` y `requestId`. Ningún mensaje repite el
   valor recibido. Ningún log lleva nombres, apellidos, fecha, celular ni pronombre.
4. **Código.** Métodos de ≈ 20 líneas, ≤ 3 parámetros. `domain` no importa Spring, JPA ni Google. `@Transactional` solo en `application.service`.
   Sin dependencias nuevas. Antes de crear una clase: `git grep -n <Nombre> -- src`.
5. **Documentación.** Javadoc en toda clase, `record` y método nuevo o modificado (porqué, `@param`, `@return`, `@throws`). Sin `CM-NNN`, sin rutas
   a otros archivos, sin `TODO`. Todo código nuevo o que suma un endpoint se refleja en `docs/errores.md` en la misma tarjeta.
6. **Pruebas.** Código nuevo: `method_shouldResult_whenCondition` con `@DisplayName` en español; Arrange-Act-Assert, datos literales. Primero la prueba
   que falla (pega su salida). Las de integración son `*Test` (las corre Surefire) con `@Testcontainers(disabledWithoutDocker = true)`, `postgres:16-alpine`,
   `RANDOM_PORT` y el doble `InMemoryFirebaseUserDirectory`, como `AccountRegistrationEndToEndTest`.
7. **Comandos.** `./mvnw.cmd -q -B "-Dtest=<Clase>" test`; cierre `./mvnw.cmd -B clean verify`. La salida de Surefire con `-q` no trae el resumen:
   leerlo en `target/surefire-reports/*.txt`. `mvnw.cmd` aparece modificado por saltos de línea: nunca se confirma (`git add` con rutas explícitas).
8. **Detente y reporta** con la salida real si algo no coincide con el código.
9. **Commit:** `CM-297 | <tipo>(cuentas): <resultado>`, en español, sin `[IA-ASISTIDO]`, con `Co-Authored-By`. Sin push ni PR.

---

# PR 0 (`CM-297-spec-consulta-de-mi-cuenta`, desde la punta de `CM-279-postman-eventos-cuenta`) y PR 1 (`CM-297-consulta-de-mi-cuenta`, sobre el PR 0)

T-36.0 es el PR 0 (solo documentos, ≈ 455 líneas); T-36.1 a T-36.7, el PR 1.

## [x] T-36.0 · Copiar la spec y reproducir — ≤ 15 min

- Copiar `specs/CM-297-ConsultaDeMiCuenta/` desde `C:\Users\paanm\Documents\cameia\cameia-worktrees\cuentas-specs-v6`.
- Reproducir D2 con una prueba que falla (se queda en T-36.1), en la clase nueva
  `src/test/java/tech/cameia/cuentas/infrastructure/persistence/mapper/AccountMapperTest.java` (hoy no existe; unitaria, sin Spring):
  `AccountMapperTest.toDomain_shouldAcceptUnknownBirthDate_whenColumnIsNull` (entidad
  con `fechaNacimiento` `null`) → hoy `IllegalArgumentException` «La fecha de nacimiento es obligatoria». Pega la salida.
- Commit: `CM-297 | docs(spec): spec, plan y tarjetas de la consulta de mi cuenta`.
- **Resultado (10-oct-2026):** base `origin/develop` `f671999` (CM-279 ya fusionada; el plan citaba la punta de C3c, que ahora es `develop`).
  D2 reproducido con una prueba equivalente (`toDomain` con la columna nula):
  `[ERROR] Tests run: 1, Failures: 0, Errors: 1 … java.lang.IllegalArgumentException: La fecha de nacimiento es obligatoria`.
  La prueba definitiva de `AccountMapperTest` no se confirma en el PR 0 (rompería el build); se escribe en T-36.1.

## [ ] T-36.1 · Fecha de nacimiento desconocida sin 500 — ≤ 25 min, ≈ 90 líneas

- `git grep -n "getBirthDate()" -- src` y anotar los llamadores (en la base de la cadena: `AccountMapper.toEntity`, `AccountRecordingService` y
  pruebas).
- **Modificar** `Account`:
  ```java
  /** @return fecha de nacimiento declarada; vacía en las cuentas anteriores a que el registro la exigiera */
  public Optional<BirthDate> getBirthDate() {
      return Optional.ofNullable(birthDate);
  }
  ```
  y el Javadoc de `rebuild` (`@param birthDate fecha de nacimiento, o {@code null} si la cuenta es anterior a que el registro la exigiera`).
  `register` sigue exigiéndola: si hoy no la valida contra `null`, agregar `Objects.requireNonNull(birthDate, "La fecha de nacimiento es obligatoria al registrarse")`.
- **Modificar** `AccountMapper`: `toDomain` → `entity.getFechaNacimiento() == null ? null : new BirthDate(entity.getFechaNacimiento())`;
  `toEntity` → `account.getBirthDate().map(BirthDate::value).orElse(null)`.
- **Modificar** `AccountRecordingService` (evento `cuenta.creada`): `saved.getBirthDate().orElseThrow(() -> new IllegalStateException("Una cuenta recién registrada siempre tiene fecha de nacimiento"))`
  — es un invariante, no una entrada del cliente. Si `UntypedExceptionClassificationTest` exige clasificarlo, agregarlo a su lista con esa razón.
- **Pruebas:** `AccountMapperTest.toDomain_shouldAcceptUnknownBirthDate_whenColumnIsNull` y `toEntity_shouldWriteNullBirthDate_whenUnknown`; la prueba
  de servicio de la activación con una cuenta sin fecha (`ActivateAccountServiceTest.activate_shouldActivate_whenBirthDateIsUnknown`); ajustar las que
  llaman `getBirthDate()` (`.orElseThrow()` o `.get()`).
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=AccountMapperTest,ActivateAccountServiceTest,AccountRecordingServiceTest,UntypedExceptionClassificationTest" test`.
- **Commit:** `CM-297 | fix(cuentas): una cuenta sin fecha de nacimiento se lee y se guarda sin error`.

## [ ] T-36.2 · Caso de uso — ≤ 25 min, ≈ 120 líneas

- **Crear** `domain/exception/IdentityRequiredException.java`:
  ```java
  /**
   * Señala que la petición no trae una identidad utilizable: el encabezado del Gateway llegó vacío, solo con espacios o más largo que un
   * identificador de Firebase. Responde igual que el encabezado ausente.
   */
  public class IdentityRequiredException extends BusinessException {
      private static final String MESSAGE = "La petición no incluye los datos que exige esta ruta";
      /** Crea la excepción con el mensaje del encabezado ausente. */
      public IdentityRequiredException() { super(ErrorCode.IDENTITY_REQUIRED, MESSAGE); }
  }
  ```
- **Crear** `application/service/GetCurrentAccountService.java`:
  ```java
  /**
   * Lee la cuenta de quien llama. La identidad sale solo del encabezado que pone el Gateway: nada de la ruta, la consulta ni el cuerpo decide
   * qué cuenta se lee.
   */
  @Service
  public class GetCurrentAccountService {
      /** Largo máximo de un identificador de Firebase; es el de la columna {@code firebase_uid}. */
      static final int MAX_FIREBASE_UID_LENGTH = 128;
      private final AccountRepository repository;
      public GetCurrentAccountService(AccountRepository repository) { this.repository = repository; }

      /**
       * @param firebaseUid identificador del usuario que propaga el Gateway
       * @return la cuenta de ese usuario
       * @throws IdentityRequiredException si el identificador está en blanco o es más largo que el de Firebase
       * @throws AccountNotFoundException si no hay cuenta o la cuenta fue anonimizada
       */
      @Transactional(readOnly = true)
      public Account find(String firebaseUid) {
          if (firebaseUid == null || firebaseUid.isBlank() || firebaseUid.length() > MAX_FIREBASE_UID_LENGTH) {
              throw new IdentityRequiredException();
          }
          // Una cuenta anonimizada ya no tiene datos personales que mostrar: para quien llama, no existe.
          return repository.findByFirebaseUid(firebaseUid)
                  .filter(account -> account.getStatus() != AccountStatus.ANONYMIZED)
                  .orElseThrow(AccountNotFoundException::new);
      }
  }
  ```
  (`length()` cuenta unidades UTF-16: un `uid` de Firebase es ASCII, así que coincide con los puntos de código.)
- **Pruebas** `GetCurrentAccountServiceTest` (con el doble existente `infrastructure/persistence/InMemoryAccountRepository` envuelto en
  `Mockito.spy` para los `verify`, o con Mockito si el doble no sirve):
  `find_shouldLookUpByHeaderIdentity_whenCalled` (`verify(repository).findByFirebaseUid("uid-me-1")`); `find_shouldThrowNotFound_whenNoAccount`;
  `find_shouldThrowNotFound_whenAccountIsAnonymized`; `find_shouldReturnAccount_whenStatusIsNotAnonymized` (parametrizada `PENDING_VERIFICATION`,
  `ACTIVE`, `DISABLED`); `find_shouldRejectIdentity_whenBlankOrTooLong` (parametrizada `""`, `"   "`, `"\t"`, `"u".repeat(129)`, sin llamar al
  repositorio); `find_shouldLookUp_whenIdentityHas128Characters`; `find_shouldNeverWrite_whenReading` (`verify(repository, never()).save(any())`).
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=GetCurrentAccountServiceTest,LayeredArchitectureTest" test`.
- **Commit:** `CM-297 | feat(cuentas): caso de uso que lee la cuenta de quien llama`.

## [ ] T-36.3 · Controlador, DTO, manejador y OpenAPI — ≤ 30 min, ≈ 170 líneas

- **Crear** `presentation/dto/CurrentAccountResponse.java`:
  `public record CurrentAccountResponse(UUID id, String firstName, String lastName, LocalDate birthDate, String phoneNumber, String pronoun, String status, String plan)`
  con `@Schema` en cada componente (descripción en español, ejemplo, `nullable = true` en `birthDate`, `phoneNumber` y `pronoun`;
  `allowableValues` `{"HE","SHE","THEY"}`, `{"PENDING_VERIFICATION","ACTIVE","DISABLED"}`, `{"FREE"}`; `format = "date"` en `birthDate`), constante
  `FREE_PLAN = "FREE"` y fábrica `static CurrentAccountResponse from(Account account)` que usa `getBirthDate().map(BirthDate::value).orElse(null)`,
  `getPhoneNumber().map(PhoneNumber::value).orElse(null)` y `getPronoun().map(Enum::name).orElse(null)`. Javadoc: «No incluye correo ni contraseña:
  son de Firebase y la historia los excluye. El plan es siempre FREE hasta que existan los planes de pago.».
- **Crear** `presentation/controller/CurrentAccountController.java` (package-private, como `AccountActivationController`):
  `@GetMapping(value = "/api/v1/users/me", produces = MediaType.APPLICATION_JSON_VALUE) ResponseEntity<CurrentAccountResponse> me(@RequestHeader("X-User-Id") String firebaseUid)`
  con `@Tag(name = "Mi cuenta", description = "Datos personales de la cuenta de quien llama")`, `@Operation(summary = "Consulta la cuenta de quien llama", description = "Lee la cuenta por el identificador que propaga el Gateway; cualquier parámetro de la consulta se ignora.")`,
  `@Parameter(name = "X-User-Id", in = ParameterIn.HEADER, hidden = true)` y `@ApiResponses`: 200 (ejemplo de la spec §9), 400 `IDENTITY_REQUIRED`,
  404 `ACCOUNT_NOT_FOUND`, 406 `MEDIA_TYPE_NOT_ACCEPTABLE`, 500 `INTERNAL_ERROR`, cada error con `mediaType = "application/problem+json"` y
  `schema = @Schema(implementation = ProblemDetail.class)` como `UserRegistrationController`.
- **Modificar** `BusinessExceptionHandler`: un método para `IdentityRequiredException` que responde lo mismo que `peticionIncompleta`
  (`HttpStatus.BAD_REQUEST`, título «Petición incompleta», `error.getMessage()`, `error.getErrorCode()`, contexto `"causa=identidad-invalida"`), sin
  registrar el valor del encabezado.
- **Pruebas** `CurrentAccountControllerTest` (`standaloneSetup` + `ProblemDetailTestSupport.manejadorDeErrores()`, como `AccountActivationControllerTest`;
  cuenta con `Account.rebuild(UUID.fromString("3f0c2c1e-8a47-4d5b-9a63-5b1d6e2f7a10"), "uid-me-1", "María José", "Gómez-Ruiz", new BirthDate(LocalDate.of(1995, 4, 12)), new PhoneNumber("+573001234567"), Pronoun.SHE, AccountStatus.ACTIVE)`):
  - `getMe_shouldReturnAccountFields_whenAccountExists`: 200; `Content-Type` contiene `application/json` y `charset=UTF-8`; `$.id`, `$.firstName`
    «María José», `$.lastName` «Gómez-Ruiz», `$.birthDate` «1995-04-12», `$.phoneNumber` «+573001234567», `$.pronoun` «SHE», `$.status` «ACTIVE»,
    `$.plan` «FREE».
  - `getMe_shouldNotExposeInternalFields_whenAccountExists`: las claves del JSON son exactamente las 8 (sin `email`, `password`, `firebaseUid`,
    `version`, `fechaCreacion`).
  - `getMe_shouldReturnNullPhone_whenAccountHasNoPhone`: `$.phoneNumber` existe y es `null` (`jsonPath("$.phoneNumber").value(nullValue())` con
    `jsonPath("$").value(hasKey("phoneNumber"))`).
  - `getMe_shouldReturnNullBirthDate_whenUnknown`.
  - `getMe_shouldReturn400_whenIdentityHeaderIsMissing`: 400, `$.code` `IDENTITY_REQUIRED`.
  - `getMe_shouldReturn400_whenIdentityHeaderIsBlank`: el servicio lanza `IdentityRequiredException` → 400, `$.code` `IDENTITY_REQUIRED`, `$.detail`
    «La petición no incluye los datos que exige esta ruta».
  - `getMe_shouldReturn404_whenAccountDoesNotExist`: 404, `$.code` `ACCOUNT_NOT_FOUND`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=CurrentAccountControllerTest,BusinessExceptionHandlerTest,LayeredArchitectureTest,UntypedExceptionClassificationTest" test`.
- **Commit:** `CM-297 | feat(cuentas): GET /api/v1/users/me con los datos de la cuenta y su OpenAPI`.

## [ ] T-36.4 · Prueba de punta a punta — ≤ 30 min, ≈ 180 líneas

- **Crear** `CurrentAccountEndToEndTest.java` en la raíz de pruebas con la forma de `AccountRegistrationEndToEndTest` (`RANDOM_PORT`,
  `@AutoConfigureTestRestTemplate`, `@Testcontainers(disabledWithoutDocker = true)`, `JdbcTemplate`). Datos: registrar con `POST /api/v1/users`
  (cuerpo válido como en `AccountRegistrationEndToEndTest`) o insertar filas con `JdbcTemplate` en `microcuentas.cuenta` (columnas `NOT NULL` de `V1`).
  - `getMe_shouldReturnStoredAccount_whenRegistered`: registrar «María José» «Gómez-Ruiz» `12/04/1995` `+573001234567` `SHE`; `GET /me` con el
    `firebaseUid` → 200 con esos datos (`birthDate` «1995-04-12», `status` `PENDING_VERIFICATION`, `plan` `FREE`) y sin `email`.
  - `getMe_shouldIgnoreQueryIdentity_whenAnotherUidIsSent`: cuentas A y B; `GET /api/v1/users/me?firebase_uid=<uid de B>&id=<id de B>` con
    `X-User-Id` de A → 200 con los datos de A.
  - `getMe_shouldReturnBirthDateNull_whenLegacyAccountHasNoBirthDate`: fila insertada con `fecha_nacimiento` `NULL` → 200 con `birthDate` `null`;
    después `POST /api/v1/users/me/verification` con `X-User-Email-Verified: true` → 200 (no 500).
  - `getMe_shouldReturnUnicodeNames_whenStored` (parametrizada): `"María José 😀"` / `"Núñez Ü"` y `"<script>alert(1)</script>"` / `"O'Neil"`, fecha
    `2000-02-29` → devueltos iguales, `birthDate` «2000-02-29».
  - `getMe_shouldReturn404_whenAccountIsAnonymized` (fila `ANONYMIZED` con `fecha_eliminacion` no nula, por `ck_` de `V1`).
  - `getMe_shouldReturn404_whenIdentityLooksLikeSql`: `X-User-Id: ' OR 1=1 --` → 404 `ACCOUNT_NOT_FOUND`; el cuerpo no contiene `OR 1=1`.
  - `getMe_shouldReturn400_whenIdentityIsTooLong`: 129 caracteres → 400 `IDENTITY_REQUIRED`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=CurrentAccountEndToEndTest" test` (Docker encendido).
- **Commit:** `CM-297 | test(cuentas): consulta de mi cuenta de punta a punta con PostgreSQL real`.

## [ ] T-36.5 · Catálogo de errores — ≤ 10 min

`docs/errores.md`: fila de `ACCOUNT_NOT_FOUND` → endpoints «activación, `GET /api/v1/users/me`» y origen «`AccountNotFoundException` (sin cuenta, o
cuenta anonimizada en la consulta)», pruebas `+ GetCurrentAccountServiceTest, CurrentAccountControllerTest`; fila de `IDENTITY_REQUIRED` → endpoints
«activación, `GET /api/v1/users/me`», origen «Falta `X-User-Id` (`ServletRequestBindingException`) o llega en blanco o con más de 128 caracteres
(`IdentityRequiredException`)», pruebas `+ CurrentAccountControllerTest`. `./mvnw.cmd -q -B "-Dtest=ErrorCodeDocumentationTest" test`.
Commit: `CM-297 | docs(cuentas): catálogo de errores con la consulta de mi cuenta`.

## [ ] T-36.6 · Postman — ≤ 25 min, ≈ 260 líneas

- En `postman/cameia-cuentas.postman_collection.json` (editar con `Edit`), carpeta nueva al final **«Mi cuenta»** (variables `meEmail`, `meUid`,
  `uidOtra`). El script de la colección ya comprueba `charset`, `problem+json` y `requestId`. Cada petición agrega `responseTime < 2000`.
  - `M-00 · 201 registro de la cuenta que se consultará` (como la 1 de «Registro», sin celular; guarda `meUid`) y `M-00b · 201 segunda cuenta` (guarda `uidOtra`).
  - `M-01 · 200 mi cuenta`: `X-User-Id: {{meUid}}` → 200; claves exactas (8); `plan` `FREE`; sin `email`.
  - `M-02 · 200 sin celular`: `phoneNumber` `null`.
  - `M-03 · 200 ignora firebase_uid de otra cuenta`: `?firebase_uid={{uidOtra}}` → mismo `id` que `M-01`.
  - `M-04 · 404 sin cuenta`: `X-User-Id: uid-inexistente-{{$timestamp}}` → 404 `ACCOUNT_NOT_FOUND`.
  - `M-05 · 400 sin X-User-Id` y `M-06 · 400 X-User-Id en blanco` (`"   "`) → 400 `IDENTITY_REQUIRED`.
  - `M-07 · 200 cuenta antigua sin fecha`: solo si el entorno permite preparar la fila; si no, se omite y se cita la prueba E2E (anotar en `postman/README.md`).
  - `M-08 · 406 si solo acepta XML`: `Accept: application/xml` → 406 `MEDIA_TYPE_NOT_ACCEPTABLE`.
  - `M-09 · 200 OpenAPI publica GET /api/v1/users/me` (con `API_DOCUMENTATION_ENABLED=true`): `paths['/api/v1/users/me'].get` existe.
- `postman/README.md`: la carpeta «Mi cuenta» en la tabla de archivos y su tabla de peticiones.
- **Newman:** `npx newman run postman/cameia-cuentas.postman_collection.json -e postman/local.postman_environment.json --folder "Mi cuenta" --reporters cli,junit --reporter-junit-export target/newman-mi-cuenta.xml`
  contra el jar (`docker compose up -d --build`). Si la app no arranca sin el `.env` o el emulador, anotar PENDIENTE de Paula (P36-2) con la salida y
  seguir.
- **Commit:** `CM-297 | test(cuentas): peticiones de Postman de la consulta de mi cuenta`.

## [ ] T-36.7 · Autoverificación y cierre — ≤ 30 min

- Lista V1 a V13 de la spec §15 con salida real (V4, V6 y V7 son adversariales).
- `./mvnw.cmd -B clean verify`; cobertura por clase de lo nuevo o modificado (≥ 90 % líneas y ramas; cada línea sin cubrir con su razón);
  `git diff --shortstat <punta del PR 0>...HEAD` (≤ 800; si pasa, T-36.6 a una capa propia).
- `/simplify`, `/code-review high` y `/security-review` (toca identidad y datos personales); preparar el worktree del Prompt H (`flujo/ejecucion-en-cola.md`
  §7.2) y dejar la línea en la bandeja. Revisar `docs/bitacora-ia/` del repo antes de pedir el push.
