# Tarjetas — Aceptación de la política de tratamiento de datos en Cuentas

Cada tarjeta se ejecuta sola, en el worktree `cameia-worktrees\cameia-cuentas-CM-367` y la rama `CM-367-aceptacion-politicas-cuentas`. Paquete base: `tech.cameia.cuentas` (rutas abajo relativas a `src/main/java/tech/cameia/cuentas/` o `src/test/java/tech/cameia/cuentas/`).

**Reglas que aplican a todas (de la skill `backend-estandar`):**
- Identificadores en inglés. Javadoc, comentarios, `@DisplayName`, mensajes y OpenAPI en español. Comentarios sin `CM-NNN` ni rutas a otros archivos.
- Pruebas nuevas: `method_shouldX_whenY` con `@DisplayName` en español; Arrange-Act-Assert; datos literales; sin `Thread.sleep`.
- Prohibido: dependencias nuevas, Lombok, editar `V1` a `V7`, `catch` vacío, registrar versión o fecha junto a datos personales, `getMessage()` de librería en una respuesta.
- Por tarjeta se corren solo las pruebas del módulo tocado: `./mvnw.cmd test -Dtest=<Clases>`. La suite completa y la cobertura van una vez al final (T9).
- **Detenerse y reportar si:** la tarjeta contradice el código real, una prueba existente se rompe sin causa clara o hace falta algo no listado. No improvisar el diseño.
- Terminado = pruebas nuevas en verde y `LayeredArchitectureTest` en verde, con el comando ejecutado y su salida. Sin eso, «NO VERIFICADO».

---

## T0 — Verificaciones previas (sin código)

1. `git fetch` y `git rev-parse origin/develop`. Si no es `c94f20c`, comprobar que no llegó otra `V8` (`git ls-tree origin/develop src/main/resources/db/migration`). Si llegó, renumerar a la siguiente libre en todas las tarjetas.
2. Línea base: `./mvnw.cmd clean verify` con Docker encendido. Guardar las últimas 40 líneas en `…\Desarrollo\ejecucion\evidencia\CM-367\linea-base.txt`. Si falla, detenerse.
3. Abrir `repos/cameia-infra/docs/seguridad/matriz-asvs-nivel1.md` (solo lectura) y confirmar los ID 8.2.2, 2.2.1, 4.1.1 y el de registros sin datos sensibles. Confirmar en el anexo de atributos de calidad los ID de MAN e IOP. Anotar las diferencias en `ejecucion/evidencia/CM-367/T0.md` y corregir la spec §12 y §14 antes de T1.
4. Comprobar que `CuentaConstraintsClassificationTest` recorre (o no) las restricciones de `cuenta` y anotarlo para T1.

**Resultado (12-nov-2026):** origen `c94f20c` sin otra `V8`; línea base `clean verify` en verde con 1293 pruebas y 0 fallos (`linea-base.txt`); ASVS e identificadores de calidad corregidos en la spec por el chat que la escribió (`T0.md`); `CuentaConstraintsClassificationTest` no recorre las restricciones de `cuenta`, no hubo que tocarlo.

---

## T1 — Migración `V8`

**Cubre:** REQ-POL-10, 11 y 12; criterio 4.

**Crear** `src/main/resources/db/migration/V8__aceptacion_politica_cuenta.sql`:

```sql
-- Versión de la política de tratamiento de datos que aceptó la persona y cuándo la aceptó.
-- Las cuentas creadas antes de esta migración no aceptaron ninguna: quedan con 'sin-aceptar'
-- y la fecha de su creación, y la aplicación les pide aceptar la versión vigente.
ALTER TABLE microcuentas.cuenta ADD COLUMN politica_version VARCHAR(20);
ALTER TABLE microcuentas.cuenta ADD COLUMN politica_aceptada_en TIMESTAMPTZ;

UPDATE microcuentas.cuenta
   SET politica_version = 'sin-aceptar',
       politica_aceptada_en = fecha_creacion;

ALTER TABLE microcuentas.cuenta
    ALTER COLUMN politica_version SET NOT NULL,
    ALTER COLUMN politica_aceptada_en SET NOT NULL,
    ADD CONSTRAINT ck_cuenta_politica_version_no_vacia CHECK (btrim(politica_version) <> '');

COMMENT ON COLUMN microcuentas.cuenta.politica_version IS
    'Versión de la política aceptada (AAAA-MM-vN) o sin-aceptar en las cuentas anteriores a la aceptación';
COMMENT ON COLUMN microcuentas.cuenta.politica_aceptada_en IS
    'Instante UTC de la aceptación; en las cuentas sin-aceptar es la fecha de creación';
```

**Pruebas:**
- Ampliar `infrastructure/persistence/CuentaSchemaMigrationTest`: `schema_shouldHavePolicyColumns_whenMigrated`. Consulta `information_schema.columns`: `politica_version` es `character varying`, largo 20, `is_nullable = 'NO'`; `politica_aceptada_en` es `timestamp with time zone`, `NO`. Consulta `pg_constraint`: existe `ck_cuenta_politica_version_no_vacia`.
- Ampliar la misma clase: `insert_shouldFail_whenPolicyVersionIsBlank`. Un `INSERT` con `politica_version = '   '` lanza la violación de `ck_cuenta_politica_version_no_vacia`.
- Crear `infrastructure/persistence/MigracionPoliticaConDatosTest`, copiando la estructura de `MigracionPronombresConDatosTest`: migrar hasta `V7`, insertar 3 filas (estados `PENDING_VERIFICATION`, `ACTIVE` y `ANONYMIZED`) con `fecha_creacion` `2026-10-01T10:00:00Z`, `2026-10-02T11:00:00Z` y `2026-10-03T12:00:00Z`, migrar a `V8` y comprobar:
  - `shouldSetNotAccepted_whenAccountExisted`: las 3 con `sin-aceptar`.
  - `shouldUseCreationDate_whenAccountExisted`: `politica_aceptada_en = fecha_creacion` en cada una.
  - `shouldKeepVersionAndUpdatedAt_whenMigrating`: `version` y `fecha_actualizacion` iguales a las de antes.

**Trampa:** la entidad JPA todavía no tiene las columnas. Con `ddl-auto=validate` sí arranca, porque `validate` no se queja de columnas de más, pero un `INSERT` por JPA fallaría por el `NOT NULL`. Por eso este commit solo corre las pruebas de migración, y T2 agrega la entidad. **Comando:** `./mvnw.cmd test -Dtest=CuentaSchemaMigrationTest,MigracionPoliticaConDatosTest`.

**Si `CuentaConstraintsClassificationTest` (T0.4) exige clasificar toda restricción de `cuenta`:** agregar `ck_cuenta_politica_version_no_vacia` como «imposible por la API: el dominio lo impide» y continuar con T2.

**Foco:** F-5.

**Resultado (12-nov-2026):** V8 creada (`66e0e95`, hoy `832a7e6`) y probada con filas (`MigracionPoliticaConDatosTest`). Dejó en rojo las pruebas JPA hasta T2, como preveía la tarjeta. Los `Migracion*ConDatosTest` anteriores insertan después de migrar a la última versión y se ajustaron con `conPolitica`.

---

## T2 — Dominio, códigos y persistencia

**Cubre:** REQ-POL-06, 12, 21, 23 y 26 (parte de dominio); códigos de §10.

**Crear** `domain/model/PolicyAcceptance.java`:

```java
/**
 * Versión de la política de tratamiento de datos que aceptó la persona y el instante en que la aceptó.
 *
 * <p>Las cuentas creadas antes de que existiera la aceptación guardan la versión {@link #NOT_ACCEPTED_VERSION}
 * y la fecha de su creación: no aceptaron ninguna versión y la aplicación les pide aceptar la vigente.</p>
 *
 * @param version versión aceptada, de 1 a 20 caracteres
 * @param acceptedAt instante UTC de la aceptación
 */
public record PolicyAcceptance(String version, Instant acceptedAt) {

    /** Versión que marca a las cuentas que nunca aceptaron una política. */
    public static final String NOT_ACCEPTED_VERSION = "sin-aceptar";

    /** Largo máximo de la versión: el de la columna {@code politica_version}. */
    public static final int MAX_VERSION_LENGTH = 20;

    public PolicyAcceptance {
        if (version == null || version.isBlank()
                || version.codePointCount(0, version.length()) > MAX_VERSION_LENGTH) {
            throw new IllegalArgumentException("La versión aceptada debe tener de 1 a 20 caracteres");
        }
        if (acceptedAt == null) {
            throw new IllegalArgumentException("La fecha de aceptación es obligatoria");
        }
    }

    /** @return {@code true} si la persona aceptó alguna versión */
    public boolean isAccepted() {
        return !NOT_ACCEPTED_VERSION.equals(version);
    }

    /** @return la fecha de aceptación, o vacío si la cuenta nunca aceptó una versión */
    public Optional<Instant> acceptedAtIfAccepted() {
        return isAccepted() ? Optional.of(acceptedAt) : Optional.empty();
    }
}
```

**Crear** `domain/model/CurrentPolicy.java`:

```java
/**
 * Versión vigente de la política de tratamiento de datos, la única que se puede aceptar.
 *
 * <p>El formato es {@code AAAA-MM-vN}: año, mes de 01 a 12 y número de versión de 1 a 999 sin ceros a la
 * izquierda, por ejemplo {@code 2026-11-v1}. Una versión fuera de ese formato impide arrancar el servicio.</p>
 *
 * @param version versión vigente
 */
public record CurrentPolicy(String version) {

    private static final Pattern FORMAT = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])-v[1-9]\\d{0,2}$");

    public CurrentPolicy {
        if (version == null || !FORMAT.matcher(version).matches()) {
            throw new IllegalArgumentException(
                    "La versión vigente de la política debe tener el formato AAAA-MM-vN, por ejemplo 2026-11-v1");
        }
    }

    /**
     * Exige que la versión recibida sea la vigente. Distingue mayúsculas y no normaliza.
     *
     * @param candidate versión que envió la persona, ya recortada
     * @throws PolicyVersionOutdatedException si es distinta de la vigente
     */
    public void requireCurrent(String candidate) {
        if (!version.equals(candidate)) {
            throw new PolicyVersionOutdatedException();
        }
    }
}
```

**Crear** `domain/exception/PolicyVersionOutdatedException.java`, igual que `InvalidPronounException`:
`super("policyVersion", ErrorCode.POLICY_VERSION_OUTDATED, "La política cambió. Recarga la página y vuelve a aceptarla.");`

**Modificar** `domain/exception/ErrorCode.java`: agregar después de `PHONE_NUMBER_INVALID_FORMAT`, con su Javadoc:
- `DATA_AUTHORIZATION_REQUIRED` («La persona no autorizó el tratamiento de sus datos.»)
- `POLICY_VERSION_REQUIRED` («Falta la versión de la política que se acepta.»)
- `POLICY_VERSION_OUTDATED` («La versión aceptada no es la vigente.»)

**Modificar** `domain/model/Account.java`:
- Campo `private PolicyAcceptance policyAcceptance;`, no `final`.
- `register(..., Pronoun pronoun, PolicyAcceptance policyAcceptance)`: exige que no sea nulo (`IllegalArgumentException("La aceptación de la política es obligatoria al registrarse")`).
- Los dos `rebuild`: parámetro nuevo `PolicyAcceptance policyAcceptance` justo antes de `status`. El de 8 parámetros pasa a 9 y el de 9 a 10.
- Getter `public PolicyAcceptance getPolicyAcceptance()`.
- Método nuevo:

```java
/**
 * Guarda la aceptación de una versión de la política.
 *
 * <p>Aceptar la versión que ya tiene no cambia nada: la fecha de la primera aceptación se conserva.</p>
 *
 * @param acceptance versión vigente y el instante actual
 * @return {@code true} si la aceptación cambió y hay que guardar la cuenta
 * @throws AccountDisabledException si la cuenta está deshabilitada
 * @throws AccountNotFoundException si la cuenta está anonimizada
 */
public boolean acceptPolicy(PolicyAcceptance acceptance) {
    if (status == AccountStatus.DISABLED) {
        throw new AccountDisabledException();
    }
    if (status == AccountStatus.ANONYMIZED) {
        throw new AccountNotFoundException(); // no se revela que la cuenta existió
    }
    if (policyAcceptance.version().equals(acceptance.version())) {
        return false;
    }
    policyAcceptance = acceptance;
    return true;
}
```

**Modificar** la persistencia:
- `AccountEntity`: `@Column(name = "politica_version", nullable = false, length = 20) private String politicaVersion;` y `@Column(name = "politica_aceptada_en", nullable = false) private Instant politicaAceptadaEn;`. Se suman al constructor de llenado después de `estado`, con getters y un `public void setAceptacionPolitica(String version, Instant aceptadaEn)` con Javadoc.
- `AccountMapper.toEntity`: pasa `account.getPolicyAcceptance().version()` y `.acceptedAt()`. `toDomain`: `new PolicyAcceptance(entity.getPoliticaVersion(), entity.getPoliticaAceptadaEn())` en `rebuild`.
- `AccountRepositoryAdapter.save`, dentro del `map` de la fila existente, después de `existing.setEstado(...)`: `existing.setAceptacionPolitica(account.getPolicyAcceptance().version(), account.getPolicyAcceptance().acceptedAt());`.

**Modificar (mecánico):** en cada prueba que llama a `Account.register(` o `Account.rebuild(` (`git grep -l "Account.register(\|Account.rebuild(" -- src/test`), agregar el argumento con la constante de prueba `new PolicyAcceptance("2026-11-v1", Instant.parse("2026-11-01T00:00:00Z"))`.

**Para que este commit compile** (la única llamada de `src/main` a `register` está en `RegisterUserService`, línea 213):
1. Agregar `String policyVersion` a `RegisterUserCommand`, después de `pronoun`, con su `@param`.
2. En `RegisterUserService`, pasar `new PolicyAcceptance(command.policyVersion(), clock.instant())` como último argumento de `Account.register`.
3. En `RegisterUserRequest.toCommand`, pasar por ahora `PolicyAcceptance.NOT_ACCEPTED_VERSION`. T4 lo reemplaza por el campo `policyVersion` del cuerpo. Es un estado intermedio de un solo commit y se anota en el informe de la tarjeta.
4. Las pruebas que construyen `RegisterUserCommand` a mano reciben `"2026-11-v1"`.

**Pruebas nuevas:**
- `domain/model/PolicyAcceptanceTest`: `shouldCreate_whenVersionAndDateAreValid`; `shouldReject_whenVersionIsNull`, `…IsBlank` (`"   "`), `…IsLongerThan20` (`"a".repeat(21)`); `shouldAccept_whenVersionIsExactly20` (`"a".repeat(20)`); `shouldReject_whenDateIsNull`; `isAccepted_shouldBeFalse_whenNotAcceptedVersion`; `acceptedAtIfAccepted_shouldBeEmpty_whenNotAccepted`.
- `domain/model/CurrentPolicyTest`: `@ParameterizedTest` válidas `2026-11-v1`, `2026-01-v999`; inválidas `""`, `null`, `2026-13-v1`, `2026-00-v1`, `2026-11-v0`, `2026-11-v01`, `2026-11-v1000`, `26-11-v1`, `sin-aceptar`, `2026-11-V1`; `requireCurrent_shouldPass_whenEqual`; `…shouldThrowOutdated_whenDifferent` (`2026-10-v1`), `…whenCaseDiffers` (`2026-11-V1`), `…whenNull`, `…whenFullWidth` (`2026-11-ｖ1`). La excepción tiene campo `policyVersion`, código `POLICY_VERSION_OUTDATED` y el mensaje literal.
- Ampliar `domain/model/AccountTest`: `register_shouldKeepPolicyAcceptance`; `register_shouldReject_whenPolicyAcceptanceIsNull`; `acceptPolicy_shouldReturnFalseAndKeepDate_whenSameVersion`; `acceptPolicy_shouldReplace_whenDifferentVersion` (de `sin-aceptar` a `2026-11-v1`); `acceptPolicy_shouldThrowDisabled_whenAccountIsDisabled`; `acceptPolicy_shouldThrowNotFound_whenAccountIsAnonymized`; `acceptPolicy_shouldAccept_whenAccountIsPending`.
- Ampliar `infrastructure/persistence/mapper/AccountMapperTest` (ida y vuelta de los dos campos) y `infrastructure/persistence/AccountRepositoryAdapterTest`: `save_shouldUpdatePolicyAcceptance_whenAccountExists`.
- Ampliar `ErrorCodeTest` si recorre los códigos. `ErrorCodeDocumentationTest` exige que cada código esté en `docs/errores.md`: las tres filas de la spec §10 se agregan **en este commit** (código, HTTP 422, campo, mensaje literal y cuándo), para que cada commit pase entero.

**Comando:** `./mvnw.cmd test -Dtest=PolicyAcceptanceTest,CurrentPolicyTest,AccountTest,AccountMapperTest,AccountRepositoryAdapterTest,ErrorCodeTest,LayeredArchitectureTest` y después `./mvnw.cmd test` (todo debe compilar y pasar).

**Foco:** F-4.

**Resultado (12-nov-2026):** dominio (`PolicyAcceptance`, `CurrentPolicy`, `Account.acceptPolicy`), códigos de error, entidad, mapeador y adaptador; comando de la tarjeta y la suite completa en verde.

---

## T3 — Versión vigente por configuración

**Cubre:** REQ-POL-30 y 31; D-4.

**Crear** `infrastructure/config/PolicyProperties.java`:

```java
/**
 * Configuración de la política de tratamiento de datos.
 *
 * @param currentVersion versión vigente, la única que se puede aceptar; su formato lo valida {@code CurrentPolicy}
 */
@Validated
@ConfigurationProperties("cuentas.policy")
public record PolicyProperties(@NotBlank String currentVersion) {
}
```

**Crear** `infrastructure/config/PolicyConfiguration.java`: `@Configuration`, `@EnableConfigurationProperties(PolicyProperties.class)`, `@Bean CurrentPolicy currentPolicy(PolicyProperties properties) { return new CurrentPolicy(properties.currentVersion()); }`, con Javadoc que diga que un formato inválido impide arrancar.

**Modificar** `src/main/resources/application.properties`, al final:

```properties
# Versión vigente de la política de tratamiento de datos (formato AAAA-MM-vN). Debe coincidir con la que publica la web.
cuentas.policy.current-version=${CAMEIA_POLICY_VERSION:2026-11-v1}
```

**Modificar** `.env.example`: agregar la línea comentada, nunca vacía:

```text
# Versión vigente de la política; sin definir se usa la del código. Una línea vacía impide arrancar.
# CAMEIA_POLICY_VERSION=2026-11-v1
```

**Pruebas:** crear `infrastructure/config/PolicyPropertiesTest` con `ApplicationContextRunner`, copiando la estructura de `PurgePropertiesTest`: `shouldCreateCurrentPolicy_whenVersionIsValid` (`2026-11-v1`); `shouldFailStartup_whenBlank` (`""`); `shouldFailStartup_whenMonthIsInvalid` (`2026-13-v1`); `shouldFailStartup_whenNotAcceptedSentinel` (`sin-aceptar`). El fallo de arranque se comprueba con `assertThat(context).hasFailed()`.

**Comando:** `./mvnw.cmd test -Dtest=PolicyPropertiesTest,PurgeJobProfileTest,CuentasApplicationTests`.

**Foco:** F-3 y F-7. **Al terminar T3:** pedir la revisión independiente temprana (primer tercio).

**Resultado (12-nov-2026):** `PolicyProperties` y `PolicyConfiguration` con `cuentas.policy.current-version=${CAMEIA_POLICY_VERSION:2026-11-v1}`; el arranque falla con una versión mal formada; comando de la tarjeta en verde.

---

## T4 — Registro con autorización y versión

**Cubre:** REQ-POL-01 a 08; criterio 3; D-1, D-3; I-1 e I-3.

**Crear** `presentation/dto/ExplicitConsent.java` (anotación `@Constraint(validatedBy = ExplicitConsentValidator.class)`, `message() default "Acepta la política de tratamiento de datos para continuar."`, `groups`, `payload`, Javadoc que explique que solo el JSON `true` cuenta y que `null` también se rechaza) y `ExplicitConsentValidator implements ConstraintValidator<ExplicitConsent, Object>`: `return Boolean.TRUE.equals(value);`.

**Crear** `presentation/dto/CurrentPolicyVersion.java` (anotación con `validatedBy = CurrentPolicyVersionValidator.class`) y `CurrentPolicyVersionValidator implements ConstraintValidator<CurrentPolicyVersion, String>` con constructor `CurrentPolicyVersionValidator(CurrentPolicy currentPolicy)`. `isValid`: si el valor es `null` o está en blanco, `true` (lo reporta `@NotBlank`); si no, `currentPolicy.requireCurrent(value)` y, al capturar `PolicyVersionOutdatedException`, publicar la violación con la misma secuencia que `DomainRuleValidator.isValid` (líneas 41-60: `disableDefaultConstraintViolation`, `unwrap(HibernateConstraintValidatorContext.class).withDynamicPayload(error.getErrorCode()).buildConstraintViolationWithTemplate(...)`) y devolver `false`.

**Modificar** `presentation/dto/RegisterUserRequest.java`: agregar al final del record, con `@Schema` completo:

```java
@Schema(description = "Autorización expresa del tratamiento de datos; solo el valor true la otorga",
        type = "boolean", example = "true", requiredMode = RequiredMode.REQUIRED)
@ExplicitConsent
Object dataAuthorization,

@Schema(description = "Versión de la política aceptada, con formato AAAA-MM-vN; debe ser la vigente",
        example = "2026-11-v1", minLength = 1, maxLength = 20, requiredMode = RequiredMode.REQUIRED)
@NotBlank(message = "Recarga la página y vuelve a aceptar la política.")
@CurrentPolicyVersion
String policyVersion
```

En el constructor compacto: `policyVersion = SingleLineText.normalize(policyVersion);`. En `toCommand`, pasar `policyVersion` en lugar de la constante temporal de T2. Actualizar el Javadoc de la clase y de los `@param`.

**Modificar** `presentation/advice/BusinessExceptionHandler.FIELD_ERROR_CODES`:
`Map.entry("dataAuthorization.ExplicitConsent", ErrorCode.DATA_AUTHORIZATION_REQUIRED)` y `Map.entry("policyVersion.NotBlank", ErrorCode.POLICY_VERSION_REQUIRED)`.

**Modificar** `presentation/controller/UserRegistrationController.java`: en la descripción del 422, agregar `DATA_AUTHORIZATION_REQUIRED, POLICY_VERSION_REQUIRED, POLICY_VERSION_OUTDATED`; en `EJEMPLO_422`, un elemento de `dataAuthorization`.

**Trampa (F-2):** `UserRegistrationControllerTest` usa `standaloneSetup`, que crea un validador sin contexto de Spring y no sabe construir `CurrentPolicyVersionValidator`. Agregar a `presentation/advice/ProblemDetailTestSupport` un método `static LocalValidatorFactoryBean validadorConPolitica(String version)` que devuelva un `LocalValidatorFactoryBean` con un `ConstraintValidatorFactory` que entregue `new CurrentPolicyVersionValidator(new CurrentPolicy(version))` para esa clase y delegue el resto en `ConstraintValidatorFactoryImpl`. Llamar `afterPropertiesSet()` y usar `.setValidator(...)` en el `standaloneSetup`. En la aplicación real no hace falta nada: Spring Boot inyecta el bean.

**Pruebas** (ampliar `presentation/controller/UserRegistrationControllerTest`, con versión `2026-11-v1`): todas las filas de §7 de la spec, con nombres literales. Cada una comprueba `status().isUnprocessableEntity()`, `$.code == "VALIDATION_FAILED"`, `$.errors[?(@.field=='dataAuthorization')].code` y `.message` literales, y que haya un solo elemento por campo. Agregar `register_shouldReturnAllFieldErrorsAtOnce_whenPolicyFieldsAndEmailAreInvalid`: cuerpo sin los dos campos y `email: "no-es-correo"` → 3 elementos.
- Crear `presentation/dto/ExplicitConsentValidatorTest` (`true` válido; `false`, `null`, `"true"`, `1`, `List.of()`, `Map.of()` inválidos) y `CurrentPolicyVersionValidatorTest` (nulo y blanco válidos, igual válido, distinto inválido con código `POLICY_VERSION_OUTDATED` en la carga).
- Ampliar `application/service/RegisterUserServiceTest`: `register_shouldStorePolicyAcceptance_whenAccountIsCreated` (reloj fijo `2026-11-14T15:30:00Z` → la cuenta guardada tiene la versión del comando, `2026-11-v1`, y `acceptedAt` `2026-11-14T15:30:00Z`); `register_shouldKeepStoredAcceptance_whenPendingAccountIsReturned` (cuenta pendiente con `sin-aceptar` → sigue `sin-aceptar`).
- Ampliar `AccountRegistrationEndToEndTest`: `register_shouldStorePolicyAcceptanceInSameRow` (consulta SQL a `cuenta` tras el 201: `politica_version = '2026-11-v1'` y `politica_aceptada_en` no nulo); `register_shouldNotLogPolicyVersion` (`OutputCaptureExtension`: la salida no contiene `2026-11-v1`). Actualizar los cuerpos de registro de todas las pruebas E2E y de controlador existentes con `"dataAuthorization": true, "policyVersion": "2026-11-v1"`; buscarlos con `git grep -l "\"pronoun\"" -- src/test`.

**Comando:** `./mvnw.cmd test -Dtest=UserRegistrationControllerTest,ExplicitConsentValidatorTest,CurrentPolicyVersionValidatorTest,RegisterUserServiceTest,AccountRegistrationEndToEndTest,RegistroConcurrenteEndToEndTest,LayeredArchitectureTest`.

**Foco:** F-1 y F-2.

**Resultado (12-nov-2026):** registro con `dataAuthorization` y `policyVersion` (`ExplicitConsent`, `CurrentPolicyVersion`), códigos por campo en el manejador y matriz de la spec §7 en `UserRegistrationControllerTest`; comando de la tarjeta en verde. Hallazgo: `type="boolean"` documentaba `dataAuthorization` como texto en OpenAPI; se fijó con `implementation = Boolean.class`. Hallazgo de `clean verify`: `BusinessExceptionHandlerTest.todaRestriccionDelContratoTieneCodigo` no conocía `CurrentPolicyVersion` (su código viene de la excepción del dominio); se corrigió la prueba. El ejemplo 422 del registro suma `dataAuthorization`.

---

## T5 — Caso de uso de aceptación

**Cubre:** REQ-POL-22, 23, 25, 26, 27 y 40.

**Crear** `application/command/AcceptPolicyCommand.java`: `public record AcceptPolicyCommand(String firebaseUid, String policyVersion)`, con Javadoc.

**Crear** `application/service/AcceptPolicyService.java` (`@Service`), con constructores `(AccountRepository, CurrentPolicy)` `@Autowired` y `(AccountRepository, CurrentPolicy, Clock)`, como `AccountRecordingService`:

```java
public Account accept(AcceptPolicyCommand command) {
    String firebaseUid = FirebaseUid.required(command.firebaseUid()).value();
    currentPolicy.requireCurrent(command.policyVersion()); // la misma regla del borde, por si se llama sin él
    Account cuenta = repositorio.findByFirebaseUid(firebaseUid).orElseThrow(AccountNotFoundException::new);
    if (!cuenta.acceptPolicy(new PolicyAcceptance(currentPolicy.version(), clock.instant()))) {
        return cuenta; // idempotente: ya tenía la versión vigente y se conserva su fecha
    }
    try {
        Account guardada = repositorio.save(cuenta);
        logger.info("Aceptación de la política guardada para la cuenta {}", guardada.getId());
        return guardada;
    } catch (AccountConflictException conflicto) {
        return rereadAfterConflict(firebaseUid);
    }
}

private Account rereadAfterConflict(String firebaseUid) {
    Account actual = repositorio.findByFirebaseUid(firebaseUid).orElseThrow(AccountNotFoundException::new);
    if (actual.getPolicyAcceptance().version().equals(currentPolicy.version())) {
        return actual; // otra petición igual ganó: misma respuesta
    }
    throw new AccountConflictException();
}
```

Sin `@Transactional` en el método, igual que `ActivateAccountService`: el `save` del adaptador ya es una transacción y el conflicto se resuelve fuera de ella.

**Pruebas** (crear `application/service/AcceptPolicyServiceTest`, con `InMemoryAccountRepository` y reloj fijo `2026-11-14T15:30:00Z`): `accept_shouldStoreClockInstant_whenAccountWasNotAccepted`; `accept_shouldNotWrite_whenVersionAlreadyAccepted` (la fecha sigue `2026-11-01T00:00:00Z` y el repositorio no registra un `save`); `accept_shouldThrowOutdated_whenVersionIsOld`; `accept_shouldThrowNotFound_whenNoAccount`; `…_whenAnonymized`; `accept_shouldThrowDisabled_whenDisabled`; `accept_shouldThrowIdentityRequired_whenUidIsBlank`; `accept_shouldReturnStoredAcceptance_whenConcurrentSaveWon` (repositorio que lanza `AccountConflictException` en el `save` y en la relectura devuelve la cuenta ya aceptada); `accept_shouldThrowConflict_whenRereadIsStillOld`; `accept_shouldNotLogVersionOrDate` (`OutputCaptureExtension`: la salida contiene el UUID de la cuenta y no contiene `2026-11-v1` ni `2026-11-14`).

Si `InMemoryAccountRepository` no permite contar los `save` ni forzar un conflicto, extenderlo (es un doble de prueba compartido) y decirlo en el informe.

**Comando:** `./mvnw.cmd test -Dtest=AcceptPolicyServiceTest,UntypedExceptionClassificationTest,LayeredArchitectureTest`. Si `UntypedExceptionClassificationTest` pide clasificar `accept`, se clasifica con sus excepciones de negocio.

**Foco:** F-6.

**Resultado (12-nov-2026):** `AcceptPolicyService` idempotente, con releída tras conflicto y sin `@Transactional`; 15 pruebas. Hallazgo: Java da nanosegundos y PostgreSQL guarda microsegundos, así la primera respuesta difería de la lectura siguiente; el instante se trunca a microsegundos. `UntypedExceptionClassificationTest` no pidió clasificar `accept`; comando de la tarjeta en verde.

---

## T6 — Endpoints de consulta y aceptación

**Cubre:** REQ-POL-20 a 28; criterio 5; D-2.

**Crear** `presentation/dto/PolicyAcceptanceResponse.java`:

```java
public record PolicyAcceptanceResponse(
        @Schema(description = "Versión aceptada; sin-aceptar si la persona nunca aceptó una", example = "2026-11-v1",
                maxLength = 20) String acceptedVersion,
        @Schema(description = "Instante UTC de la aceptación; nulo si nunca aceptó una versión",
                example = "2026-11-14T15:30:00Z", format = "date-time", nullable = true) Instant acceptedAt,
        @Schema(description = "Versión vigente; si es distinta de la aceptada, la web pide aceptarla",
                example = "2026-11-v1") String currentVersion) {

    public static PolicyAcceptanceResponse from(PolicyAcceptance acceptance, CurrentPolicy currentPolicy) {
        return new PolicyAcceptanceResponse(acceptance.version(), acceptance.acceptedAtIfAccepted().orElse(null),
                currentPolicy.version());
    }
}
```

**Crear** `presentation/dto/PolicyAcceptanceRequest.java`: `record PolicyAcceptanceRequest(@NotBlank(message = "Recarga la página y vuelve a aceptar la política.") @CurrentPolicyVersion String policyVersion)` con `@Schema` (igual al de §T4) y el recorte en el constructor compacto.

**Modificar** `BusinessExceptionHandler.FIELD_ERROR_CODES`: la clave `policyVersion.NotBlank` de T4 ya sirve para los dos DTO (la clave es por campo). Comprobarlo con una prueba.

**Crear** `presentation/controller/PolicyAcceptanceController.java`, con el mismo esquema y ejemplos OpenAPI que `AccountActivationController`:

```java
@PostMapping(value = "/api/v1/users/me/policy-acceptance", produces = MediaType.APPLICATION_JSON_VALUE)
ResponseEntity<PolicyAcceptanceResponse> accept(@Valid @RequestBody PolicyAcceptanceRequest request,
        @RequestHeader("X-User-Id") String firebaseUid) {
    Account cuenta = servicio.accept(new AcceptPolicyCommand(firebaseUid, request.policyVersion()));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(PolicyAcceptanceResponse.from(cuenta.getPolicyAcceptance(), currentPolicy));
}
```

Respuestas documentadas: 200, 400 `IDENTITY_REQUIRED`, 403 `ACCOUNT_DISABLED`, 404 `ACCOUNT_NOT_FOUND`, 409 `ACCOUNT_CONFLICT`, 422 (`POLICY_VERSION_REQUIRED`, `POLICY_VERSION_OUTDATED`, `REQUEST_BODY_INVALID_FORMAT`), 406, 413, 415, 500 y 503 (`DATABASE_UNAVAILABLE`).

**Modificar** `CurrentAccountResponse`: componente nuevo al final, `@Schema(description = "Aceptación de la política de tratamiento de datos") PolicyAcceptanceResponse policyAcceptance`; `from(Account account, CurrentPolicy currentPolicy)`. `CurrentAccountController` recibe `CurrentPolicy` por constructor y actualiza `EJEMPLO_200`.

**Pruebas:**
- Crear `presentation/controller/PolicyAcceptanceControllerTest` (`standaloneSetup` con `validadorConPolitica("2026-11-v1")`): `accept_shouldReturn200WithAcceptance`; `accept_shouldReturnNoStore`; `accept_shouldReturn400_whenUserIdHeaderIsMissing`; `accept_shouldReturnPolicyVersionRequired_whenBlank` (parametrizada: sin campo, `null`, `""`, `"  "`); `accept_shouldReturnPolicyVersionOutdated_whenOld`; `accept_shouldIgnoreBodyIdentity` (cuerpo con `"firebaseUid":"otro-uid"` → el servicio recibe el uid del encabezado); `accept_shouldIgnoreAcceptedAtInBody` (cuerpo con `"acceptedAt":"2000-01-01T00:00:00Z"` → no llega al comando).
- Ampliar `CurrentAccountControllerTest`: `me_shouldIncludePolicyAcceptance_whenAccepted`; `me_shouldReturnNullAcceptedAt_whenNotAccepted` (versión `sin-aceptar` → `$.policyAcceptance.acceptedAt` es `null` y `acceptedVersion` es `"sin-aceptar"`).
- Crear `AcceptPolicyEndToEndTest` (Testcontainers, puerto aleatorio, como `CurrentAccountEndToEndTest`): `acceptPolicy_shouldUpgradeNotAcceptedAccount` (fila insertada con `sin-aceptar` → 200 y la fila con `2026-11-v1`); `acceptPolicy_shouldKeepFirstDate_whenRepeated`; `acceptPolicy_shouldAnswer200Twice_whenSentConcurrently` (dos hilos con `CountDownLatch`, sin `sleep`; los dos 200 y la misma `acceptedAt`); `acceptPolicy_shouldAnswerWithin2Seconds` (20 peticiones seguidas, máximo < 2 s); `acceptPolicy_shouldReturn404_whenNoAccount`.
- Ampliar `CurrentAccountEndToEndTest`: `me_shouldReturnPolicyAcceptance_afterRegistration`.
- Crear `presentation/OpenApiPolicyContractTest` copiando `OpenApiProtocolErrorsTest`: el documento trae `dataAuthorization` y `policyVersion` en el esquema del registro, `policyAcceptance` en el de la cuenta, la ruta `/api/v1/users/me/policy-acceptance` y los tres códigos en las descripciones del 422.
- Agregar a `DatabaseUnavailableEndToEndTest` la ruta nueva → 503 `DATABASE_UNAVAILABLE`.

**Comando:** `./mvnw.cmd test -Dtest=PolicyAcceptanceControllerTest,CurrentAccountControllerTest,AcceptPolicyEndToEndTest,CurrentAccountEndToEndTest,OpenApiPolicyContractTest,DatabaseUnavailableEndToEndTest,LayeredArchitectureTest`.

**Resultado (12-nov-2026):** `POST /api/v1/users/me/policy-acceptance` y `policyAcceptance` en `GET /api/v1/users/me`, con OpenAPI y `Cache-Control: no-store`; extremo a extremo con concurrencia, 20 peticiones en menos de 2 s y fila intacta ante un 422; comando de la tarjeta en verde.

---

## T7 — Postman

**Cubre:** REQ-POL-52; criterios 3 y 5.

- Variable `policyVersion` = `2026-11-v1` en `postman/local.postman_environment.json`.
- Cuerpos de la carpeta «Registro» y de las demás carpetas que registran: sumar `"dataAuthorization": true, "policyVersion": "{{policyVersion}}"`.
- Carpeta nueva «Políticas» con las 12 peticiones de la spec §11, cada una con pruebas de estado, `Content-Type` (`application/json` o `application/problem+json;charset=UTF-8`), `code`, `errors[].field`, `errors[].code` y el mensaje literal donde la spec lo da. La 9 guarda `acceptedAt` de la 8 en una variable de colección y la compara.
- Actualizar `postman/README.md` con la carpeta nueva.

**Comando:** con la aplicación local (`docker compose up --build -d`): `npx newman run postman/cameia-cuentas.postman_collection.json -e postman/local.postman_environment.json`. Guardar el resumen en `…\Desarrollo\ejecucion\evidencia\CM-367\newman.txt`. Si `npx` no está disponible, decirlo («NO VERIFICADO») y no seguir a T9 sin resolverlo.

**Resultado (12-nov-2026):** carpeta «Políticas» (12 peticiones) y cuerpos de registro actualizados. Newman contra la aplicación empaquetada con PostgreSQL, RabbitMQ y el emulador de Firebase reales: Registro 35/0, Políticas 80/0, Mi cuenta 73/0, Activación 112/0, Cierre de sesión 53/0, Errores de protocolo 67/0 y Eventos 26/0 (aserciones ejecutadas/fallidas). La colección completa suma 2 fallos, ambos de «Firebase no disponible (manual)», que exige Firebase caído (`newman.txt`).

---

## T8 — Documentación

**Cubre:** REQ-POL-51 y 53; criterio 7.

- `docs/errores.md`, sección «Códigos que el servicio emite»: tres filas (código, HTTP 422, campo, mensaje literal, cuándo). Si se movieron a T2, aquí solo se revisan.
- `docs/adr/0006-aceptacion-de-la-politica.md`, con el formato de los ADR 0001 a 0005: contexto (Ley 1581, criterio CA-1.1.44), decisión (D-1 a D-4, I-1 a I-4), alternativas descartadas (plan §3), consecuencias (coordinación del despliegue con la web; las cuentas `sin-aceptar`; cambiar la versión exige cambiar la web y Cuentas a la vez).
- `CLAUDE.md` del repo §5: sumar `V8__aceptacion_politica_cuenta.sql` a la lista y las dos columnas a la tabla `cuenta`. §3 no cambia (la ruta nueva exige identidad).
- `docs/bitacora-ia/`: entrada con el prompt de esta spec, que enlaza a `spec.md` §3 (decisiones humanas D-1 a D-3).

**Comando:** `./mvnw.cmd test -Dtest=ErrorCodeDocumentationTest`.

**Resultado (12-nov-2026):** ADR 0006, `CLAUDE.md` §5 con V8 y las dos columnas, entrada 13 de la bitácora y revisión de las filas de `docs/errores.md`; `ErrorCodeDocumentationTest` en verde.

---

## T9 — Verificación única sobre la punta

1. `./mvnw.cmd clean verify` → 0 fallos y 0 errores. Guardar el resumen en `ejecucion/evidencia/CM-367/verify.txt`.
2. Cobertura de lo tocado, leída de `target/site/jacoco/jacoco.csv`, para cada clase de plan §2: líneas y ramas ≥ 90 %. Cada línea o rama sin cubrir, con su razón, en `ejecucion/evidencia/CM-367/cobertura.md`.
3. Newman (T7) sobre la misma punta.
4. Revisión P6: `/simplify`, `/code-review high`, `/security-review` (toca datos personales y una ruta nueva) y autochequeo (sin `getMessage()` de librería al cliente, sin datos personales en registros, sin `CM-NNN` en comentarios).
5. Cuerpo del PR con la plantilla del repo, validado con `.github/scripts/pr_policy.py`. `Impacto: contrato, datos` con sus líneas `Contrato:` y `Datos:`. En «Hallazgos que aplican a esta tarea»: se fusiona el mismo día que CM-368 (D-5).

**Resultado (12-nov-2026):** `clean verify`: BUILD SUCCESS, 1435 pruebas, 0 fallos y 0 errores (`verify.txt`; el primer intento falló por la prueba de T4 y quedó en `verify-intento1-fallo.log`). Cobertura de las 25 clases con código tocadas: líneas 100 % en todas y ramas ≥ 97,7 % (`cobertura.md`). Fixups fundidos con `--autosquash` (árbol idéntico al verificado). Sin push ni PR. Pendientes: `/simplify`, `/code-review` y `/security-review` no se ejecutaron como skills (ver `resumen.md`).
