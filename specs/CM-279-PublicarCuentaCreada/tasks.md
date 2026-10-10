# Tarjetas · CM-279 · Cuentas publica `cuenta.creada`

Repositorio `cameia-cuentas`, worktree `C:\Users\paanm\Documents\cameia-worktrees\cuentas-CM-279-replica`, rama
`CM-279-publicar-cuenta-creada`, base `origin/develop` `9c4e545`. Bloques C1 → C2 → C3 (uno por PR, apilados).
Las decisiones de diseño están respondidas (sección 14 de `spec.md`): `usuarioId` es el `firebaseUid`, los metadatos van en las
propiedades AMQP, la carga se borra al publicar, el registro espera la confirmación como máximo `500ms` y un Job cada 5 min reintenta,
emite la carga inicial y permite republicar. Ninguna tarjeta está bloqueada.

## 0. Reglas para todas las tarjetas (léelas antes de cualquiera)

1. **Idioma (R1).** En inglés solo los identificadores: clases, métodos, variables, constantes, paquetes y nombres de método de
   prueba (`method_shouldX_whenY`). En español todo lo demás: Javadoc, comentarios de línea y de bloque, `@DisplayName`, mensajes de
   log, mensajes de excepción, descripciones de OpenAPI, comentarios de SQL y de `.properties`, y los documentos. Los nombres de tablas
   y columnas (`evento_saliente`, `fecha_creacion`) y los de campo del JSON del evento (`usuarioId`, `email`, `fechaNacimiento`,
   `creadaEn`) son los que fija el contrato. En una clase existente con variables en español (`RegisterUserService`: `directorio`,
   `repositorio`) no se renombra lo existente; lo nuevo lleva identificadores en inglés. Los esqueletos de abajo ya traen el Javadoc y
   los mensajes en español: se copian tal cual.
2. **Pruebas.** Clase `XTest` (Surefire solo corre `*Test`; no crear `*IT`). Método `method_shouldX_whenY`. Arrange-Act-Assert, una
   conducta por prueba, valores literales, reloj fijo `Clock.fixed(Instant.parse("2026-10-09T15:04:05.123Z"), ZoneOffset.UTC)`, sin
   `Thread.sleep` (para esperar un mensaje se usa `RabbitTemplate.receive(queue, 5000)`). Toda prueba debe poder fallar: aserciones
   concretas, nunca solo «no lanza».
3. **Base de datos en pruebas:** PostgreSQL real con `@Testcontainers(disabledWithoutDocker = true)`, `@Container @ServiceConnection
   static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");` (igual que `AccountRepositoryAdapterTest`).
   Nunca H2.
4. **RabbitMQ en pruebas** (solo donde la tarjeta lo diga): sin dependencia nueva.
   ```java
   @Container
   static GenericContainer<?> rabbit = new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
           .withExposedPorts(5672)
           .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

   @DynamicPropertySource
   static void rabbitProperties(DynamicPropertyRegistry registry) {
       registry.add("spring.rabbitmq.host", rabbit::getHost);
       registry.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
       registry.add("spring.rabbitmq.username", () -> "guest");
       registry.add("spring.rabbitmq.password", () -> "guest");
       registry.add("cuentas.events.enabled", () -> "true");
   }
   ```
   (`guest` sirve porque la prueba se conecta por el puerto mapeado del anfitrión; `@ServiceConnection` no funciona con `GenericContainer`
   para RabbitMQ.)
5. **Prohibido:** agregar dependencias que la tarjeta no nombre; tocar `V1` a `V4`; tocar `.github/`; comentarios con `CM-279` u otra
   clave, o con rutas a otros archivos; registrar correo, fecha de nacimiento, carga del evento, contraseñas o tokens; `System.out`;
   `catch` vacío; cambiar el cuerpo o los estados de `POST /api/v1/users`.
6. **Arquitectura.** `domain` no importa Spring, JPA, Jackson ni AMQP. `@Transactional` en `application.service` y en los métodos de
   escritura de `OutboxRepositoryAdapter` (decisión T9 del plan, P-12: mismo patrón que los adaptadores de Perfil); nunca en las
   interfaces de Spring Data. `LayeredArchitectureTest` y
   `UntypedExceptionClassificationTest` en verde: todo método nuevo que lance una excepción sin código (`IllegalStateException`,
   `NullPointerException` de `Objects.requireNonNull`, `JacksonException`) se agrega a `CLASSIFICATION` de esa prueba con su motivo.
7. **Javadoc en español** en toda clase, record, interfaz y método (también de paquete), con el porqué, `@param`, `@return`, `@throws`.
   Comentario de bloque sobre la transacción, la compensación y la confirmación del broker.
8. **Comandos** (PowerShell, en la raíz del worktree):
   - Una clase: `.\mvnw.cmd -q test -Dtest=NombreTest`
   - Suite: `.\mvnw.cmd test`
   - Cierre de bloque: `.\mvnw.cmd clean verify` (genera `target\site\jacoco\jacoco.csv`).
   - Docker debe estar encendido. Si `CuentasApplicationTests` falla por el puerto 5432 ocupado, es del entorno (ver `ESTADO.md` de la
     raíz): se informa, no se «arregla» la prueba.
9. **Detente y reporta** (con la salida real) si: la tarjeta contradice el código real; un nombre de clase o método de Spring no existe
   en la versión del repo; una prueba existente se rompe sin causa clara; hace falta algo no listado. No improvises el diseño.
10. **Terminado** = pruebas nuevas en verde, suite completa en verde, arquitectura en verde, salida pegada en el reporte de la tarjeta.

---

## Bloque C1 — tabla de salida y registro transaccional (sin RabbitMQ)

### T-C1.1 · Migración `V5__evento_saliente.sql` y su prueba

- **Cubre:** REQ-EV-01, 03, 08 (estructura), sección 9 de la spec.
- **Antes:** `git ls-tree --name-only origin/develop src/main/resources/db/migration/`. Si ya existe un `V5__*`, usa el siguiente
  número libre y repórtalo (no renombres migraciones ajenas).
- **Crear** `src/main/resources/db/migration/V5__evento_saliente.sql` con exactamente el SQL de la sección 9 de la spec, precedido de
  este encabezado (en español porque es SQL de esquema, igual que V1):
  ```sql
  -- Tabla de salida (outbox) de los eventos que publica Cuentas.
  -- El evento se guarda en la misma transaccion que el cambio que lo origina y se publica despues;
  -- asi un fallo del broker no pierde eventos. Al confirmarse la publicacion la carga se borra
  -- (datos personales minimos) y la fila queda como marca de que el evento ya existio.
  ```
  y al final `COMMENT ON TABLE` y `COMMENT ON COLUMN` para `carga` («JSON del evento; nulo una vez publicado») y `agregado_id`
  («firebase_uid de la cuenta del evento»).
- **Crear** `src/test/java/tech/cameia/cuentas/infrastructure/persistence/EventoSalienteSchemaMigrationTest.java` (`@DataJpaTest` +
  `@AutoConfigureTestDatabase(replace = NONE)` + Testcontainers, como `CuentaSchemaMigrationTest`; usa `JdbcTemplate`). Inserta con
  SQL literal. Pruebas (cada una comprueba el SQLState y el nombre de la restricción en el mensaje de `DataIntegrityViolationException`
  con `hasMessageContaining`):

  | Método | Inserta | Espera |
  |---|---|---|
  | `insert_shouldSucceed_whenRowIsPending` | `id` `11111111-1111-4111-8111-111111111111`, `tipo` `cuenta.creada`, `version` 1, `agregado_id` `uid-1`, `carga` `'{"usuarioId":"uid-1"}'`, `id_correlacion` `req-1`, `fecha_creacion` `2026-10-09T15:04:05.123Z`, `fecha_publicacion` NULL | 1 fila; `intentos` = 0 |
  | `insert_shouldFail_whenTypeIsUnknown` | igual con `tipo` `cuenta.borrada` | `ck_evento_saliente_tipo` |
  | `insert_shouldFail_whenVersionIsZero` | `version` 0 | `ck_evento_saliente_version` |
  | `insert_shouldFail_whenAggregateIsBlank` | `agregado_id` `'   '` | `ck_evento_saliente_agregado_id` |
  | `insert_shouldFail_whenCorrelationHasSpaces` | `id_correlacion` `'req 1'` | `ck_evento_saliente_id_correlacion` |
  | `insert_shouldFail_whenCorrelationHas65Characters` | `id_correlacion` = 65 `a` | `value too long` (SQLState 22001) |
  | `insert_shouldFail_whenAttemptsAreNegative` | `intentos` -1 | `ck_evento_saliente_intentos` |
  | `insert_shouldFail_whenPendingRowHasNoPayload` | `carga` NULL, `fecha_publicacion` NULL | `ck_evento_saliente_carga_pendiente` |
  | `insert_shouldFail_whenPublishedRowKeepsPayload` | `carga` con JSON, `fecha_publicacion` = `fecha_creacion` | `ck_evento_saliente_carga_pendiente` |
  | `insert_shouldFail_whenPublishedBeforeCreated` | `carga` NULL, `fecha_publicacion` 1 s antes de `fecha_creacion` | `ck_evento_saliente_fecha_publicacion` |
  | `insert_shouldFail_whenSameTypeAndAggregateRepeat` | dos filas `cuenta.creada`/`uid-1` con `id` distinto | `uq_evento_saliente_tipo_agregado` (SQLState 23505) |
  | `insert_shouldFail_whenPayloadIsNotJson` | `carga` `'no es json'` | SQLState 22P02 |
  | `columnLengths_shouldMatchDomainLimits` | consulta `information_schema.columns` (`table_schema = 'microcuentas'`) | `agregado_id` = 128 = largo de `cuenta.firebase_uid`; `id_correlacion` = 64 = `CorrelationId.MAX_LENGTH` (esta aserción se agrega en T-C1.2, cuando la constante exista) |
  | `pendingIndex_shouldExist_whenMigrationApplied` | `pg_indexes` | existe `ix_evento_saliente_pendiente` con `WHERE (fecha_publicacion IS NULL)` |
  | `migration_shouldApply_whenAccountsAlreadyExist` | antes de nada, `SELECT count(*) FROM microcuentas.cuenta` tras insertar 2 cuentas con SQL | las cuentas siguen; `evento_saliente` vacía (la migración no crea eventos) |

- **Trampas:** el `CHECK` de `id_correlacion` con `~` necesita la barra del guion al final de la clase (`[A-Za-z0-9._-]`), como en la
  spec. `jsonb` rechaza texto que no es JSON con SQLState `22P02`. Con PowerShell, no edites el `.sql` con heredoc (pierde barras): usa
  la herramienta de escritura de archivos.
- **Verificar:** `.\mvnw.cmd -q test -Dtest=EventoSalienteSchemaMigrationTest` y `.\mvnw.cmd -q test -Dtest=CuentasApplicationTests`
  (el arranque valida las entidades; todavía no hay entidad nueva).
- **Resultado (9-oct-2026):** hecha. `EventoSalienteSchemaMigrationTest` 15 pruebas, 0 fallos (informe de Surefire); V5 libre en `origin/develop` (solo V1 a V4). Hallazgo: `-q` no imprime el resumen; se lee de `target/surefire-reports`.

### T-C1.2 · Dominio: `CorrelationId`, `AccountCreated`, `OutboundEvent`

- **Cubre:** REQ-EV-01, 02, 05; sección 10 (encabezado `X-Request-Id`).
- **Crear** `src/main/java/tech/cameia/cuentas/domain/event/CorrelationId.java`:
  ```java
  /**
   * Identificador que une un evento con la petición HTTP que lo originó.
   *
   * <p>Reutiliza el {@code X-Request-Id} que pone el Gateway cuando es seguro propagarlo (el mismo patrón que acepta el
   * manejador de errores); si no, usa el identificador del evento, así que todo evento tiene siempre un valor de correlación.</p>
   *
   * @param value valor de correlación, de 1 a 64 caracteres de {@code [A-Za-z0-9._-]}
   */
  public record CorrelationId(String value) {

      /** Largo máximo, igual al de la columna {@code id_correlacion}. */
      public static final int MAX_LENGTH = 64;

      private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9._-]{1," + MAX_LENGTH + "}$");

      /** @throws IllegalArgumentException si el valor no cumple el patrón permitido */
      public CorrelationId { ... }

      /**
       * Elige el identificador de la petición si es válido y, si no, el de respaldo.
       *
       * @param requestId {@code X-Request-Id} tal como llegó, puede ser {@code null}
       * @param fallback identificador del evento que se usa cuando el encabezado falta o no es seguro
       * @return el identificador de correlación
       */
      public static CorrelationId fromRequestIdOrElse(String requestId, UUID fallback) { ... }
  }
  ```
- **Crear** `domain/event/AccountCreated.java`:
  ```java
  /**
   * Hecho que se publica al crearse la fila de una cuenta: los demás servicios guardan solo lo que necesitan de él.
   *
   * @param eventId identificador único del evento, también el {@code message_id} de AMQP
   * @param firebaseUid identidad de la cuenta que comparten todos los servicios ({@code usuarioId} en el contrato)
   * @param email correo normalizado con el que se creó la credencial
   * @param birthDate fecha de nacimiento ya aceptada por la política de edad
   * @param createdAt instante UTC en que se registró el evento, truncado a milisegundos
   * @param correlationId correlación con la petición
   */
  public record AccountCreated(UUID eventId, String firebaseUid, EmailAddress email, BirthDate birthDate,
          Instant createdAt, CorrelationId correlationId) {
      public static final String TYPE = "cuenta.creada";
      public static final int VERSION = 1;
      /** @throws NullPointerException si algún componente es nulo (defensivo: quien llama pasa valores ya validados) */
      public AccountCreated { Objects.requireNonNull(...) for each; createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS); }
  }
  ```
- **Crear** `domain/event/OutboundEvent.java`: `record OutboundEvent(UUID id, String type, int version, String aggregateId,
  String payloadJson, Instant createdAt, String correlationId, int attempts)` con Javadoc («fila pendiente tal como la necesita el publicador») y
  `Objects.requireNonNull` en todo salvo `attempts`.
- **Pruebas** `src/test/java/tech/cameia/cuentas/domain/event/CorrelationIdTest.java`:
  - `fromRequestIdOrElse_shouldKeepRequestId_whenValid` — `@ParameterizedTest @ValueSource(strings = {"3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601", "req.1_A-b", "a"})` y 64 `a` (prueba aparte `..._whenExactly64Characters`) → valor igual a la entrada.
  - `fromRequestIdOrElse_shouldUseFallback_whenUnsafe` — `@NullSource` + `@ValueSource(strings = {"", "   ", "\t", "\n", "abc def", "<script>", "ñandú", "a%b", "a/b"})` y 65 `a` → valor `"11111111-1111-4111-8111-111111111111"` (el `fallback`).
  - `constructor_shouldReject_whenValueIsInvalid` — `new CorrelationId("a b")` → `IllegalArgumentException`.
- **Pruebas** `domain/event/AccountCreatedTest.java`: `constructor_shouldTruncateToMillis_whenInstantHasNanos`
  (`2026-10-09T15:04:05.123456789Z` → `...05.123Z`); `constructor_shouldRejectNull_whenAnyComponentIsMissing` (parametrizada, 6 casos).
- **Después:** agrega en `EventoSalienteSchemaMigrationTest.columnLengths_shouldMatchDomainLimits` la comparación con
  `CorrelationId.MAX_LENGTH`. Clasifica en `UntypedExceptionClassificationTest`: `domain.event.CorrelationId#<init>`,
  `domain.event.AccountCreated#<init>`, `domain.event.OutboundEvent#<init>` → `DEFENSIVE_INVARIANT`.
- **Verificar:** `.\mvnw.cmd -q test -Dtest="CorrelationIdTest,AccountCreatedTest,LayeredArchitectureTest,UntypedExceptionClassificationTest"`.
- **Resultado (9-oct-2026):** hecha. Falló primero: `testCompile` con `cannot find symbol: class CorrelationId` (y `AccountCreated`). Después: `CorrelationIdTest` 19, `AccountCreatedTest` 8, `OutboundEventTest` 7 (agregada para cubrir el constructor), `LayeredArchitectureTest` 3, `UntypedExceptionClassificationTest` 1 y `EventoSalienteSchemaMigrationTest` 15 (ahora compara `id_correlacion` con `CorrelationId.MAX_LENGTH`): 0 fallos, 0 errores. Se agregó `package-info.java` de `domain.event` (R8).

### T-C1.3 · Puerto `OutboxRepository`, entidad, repositorio y adaptador; carga JSON y su esquema

- **Cubre:** REQ-EV-01, 05, 08, 11, 12, 15; IOP-01; decisiones T2, T4, T6, T9.
- **Crear** `domain/port/OutboxRepository.java`:
  ```java
  /** Tabla de salida (outbox): eventos que se guardan junto con el cambio que los origina y se publican después. */
  public interface OutboxRepository {
      /** @return true si se guardó; false si la cuenta ya tiene un evento de ese tipo */
      boolean appendAccountCreated(AccountCreated event);
      /** @return los pendientes más antiguos primero (por instante de creación y luego por id), como máximo {@code limit} */
      List<OutboundEvent> findPending(int limit);
      /** @return el evento si existe y sigue pendiente */
      Optional<OutboundEvent> findPendingById(UUID id);
      /** Lo marca como publicado y borra la carga; @return true si seguía pendiente */
      boolean markPublished(UUID id, Instant publishedAt);
      /** Suma un intento fallido a un evento pendiente. */
      void recordFailedAttempt(UUID id);
      /** @return cantidad de eventos pendientes */
      long countPending();
  }
  ```
- **Crear** `infrastructure/messaging/payload/AccountCreatedPayloadV1.java`:
  ```java
  /**
   * Cuerpo JSON de {@code cuenta.creada} versión 1. Los nombres de campo los fija el contrato publicado.
   * Todo valor es texto para que el formato en el cable no dependa de los valores por defecto del serializador.
   */
  public record AccountCreatedPayloadV1(
          @JsonProperty("usuarioId") String userId,
          @JsonProperty("email") String email,
          @JsonProperty("fechaNacimiento") String birthDate,
          @JsonProperty("creadaEn") String createdAt) {

      private static final DateTimeFormatter INSTANT_FORMAT =
              DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

      /** @return la carga del evento, con fecha ISO e instante UTC con milisegundos */
      public static AccountCreatedPayloadV1 from(AccountCreated event) {
          return new AccountCreatedPayloadV1(event.firebaseUid(), event.email().value(),
                  event.birthDate().value().toString(), INSTANT_FORMAT.format(event.createdAt()));
      }
  }
  ```
  `@JsonProperty` es `com.fasterxml.jackson.annotation.JsonProperty` (las anotaciones siguen en ese paquete en Jackson 3); compruébalo con
  `git grep -n "JsonProperty" src/main` o en el jar de `jackson-annotations` del repositorio local antes de escribir.
- **Crear** `infrastructure/persistence/entity/OutboxEventEntity.java` (`@Entity @Table(name = "evento_saliente")`), campos `id`
  (`UUID`), `tipo`, `version` (`short`), `agregadoId`, `carga` (`String`, `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "carga",
  columnDefinition = "jsonb")`), `idCorrelacion`, `fechaCreacion`, `fechaPublicacion`, `intentos` (`int`), constructor protegido para
  JPA y getters. Sin setters: la escritura va por consultas.
- **Crear** `infrastructure/persistence/repository/OutboxEventJpaRepository.java` (`JpaRepository<OutboxEventEntity, UUID>`):
  ```java
  @Modifying
  @Query(value = """
          INSERT INTO microcuentas.evento_saliente
              (id, tipo, version, agregado_id, carga, id_correlacion, fecha_creacion, intentos)
          VALUES (:id, :tipo, :version, :agregadoId, CAST(:carga AS jsonb), :idCorrelacion, :fechaCreacion, 0)
          ON CONFLICT ON CONSTRAINT uq_evento_saliente_tipo_agregado DO NOTHING
          """, nativeQuery = true)
  int insertIfAbsent(@Param("id") UUID id, ...);   // 7 parámetros: se permite aquí porque refleja las columnas de la tabla

  @Query("select e from OutboxEventEntity e where e.fechaPublicacion is null order by e.fechaCreacion, e.id")
  List<OutboxEventEntity> findPending(Limit limit);

  Optional<OutboxEventEntity> findByIdAndFechaPublicacionIsNull(UUID id);

  @Modifying
  @Query("update OutboxEventEntity e set e.fechaPublicacion = :at, e.carga = null where e.id = :id and e.fechaPublicacion is null")
  int markPublished(@Param("id") UUID id, @Param("at") Instant at);

  @Modifying
  @Query("update OutboxEventEntity e set e.intentos = e.intentos + 1 where e.id = :id and e.fechaPublicacion is null")
  int incrementAttempts(@Param("id") UUID id);

  long countByFechaPublicacionIsNull();
  ```
- **Crear** `OutboxRepositoryAdapter.java` (`@Repository`, implementa el puerto): serializa con
  `private static final JsonMapper JSON = JsonMapper.builder().build();` (`tools.jackson.databind.json.JsonMapper`, como
  `FirebaseUserDirectoryAdapter`) y convierte la entidad a `OutboundEvent`. `appendAccountCreated` devuelve `insertIfAbsent(...) == 1`.
  Transacciones (P-12, regla 6): `appendAccountCreated`, `markPublished` y `recordFailedAttempt` llevan `@Transactional` (se unen a la
  del servicio cuando la hay, como en `AccountRecordingService`, y abren una propia cuando las llama el relevo); `findPending`,
  `findPendingById` y `countPending` llevan `@Transactional(readOnly = true)`. Comentario de bloque en la clase: «El relevo no puede mantener una
  transacción abierta mientras espera al broker, así que cada escritura abre aquí su propia transacción corta.»
- **Crear** `docs/eventos/cuenta-creada-v1.schema.json` con el JSON Schema literal de la sección 5 de `spec.md` (mismo texto, sin
  comentarios: JSON no los admite). Perfil copia este archivo para su prueba de contrato.
- **Pruebas** `infrastructure/persistence/OutboxRepositoryAdapterTest.java` (Testcontainers; `@DataJpaTest` +
  `@Import(OutboxRepositoryAdapter.class)`):
  - `appendAccountCreated_shouldStoreExactPayload_whenEventIsNew`: evento `eventId` `11111111-1111-4111-8111-111111111111`, uid
    `6f1d2c3b4a5e4f60718293a4b5c6d7e8`, correo `ana.perez@ejemplo.test`, fecha `2008-03-15`, instante `2026-10-09T15:04:05.123Z`,
    correlativo `req-1` → `true`; leer `carga::text` con `JdbcTemplate` y compararla **como árbol JSON** (jsonb reordena las claves) con
    `{"usuarioId":"6f1d2c3b4a5e4f60718293a4b5c6d7e8","email":"ana.perez@ejemplo.test","fechaNacimiento":"2008-03-15","creadaEn":"2026-10-09T15:04:05.123Z"}`;
    columnas `tipo`=`cuenta.creada`, `version`=1, `agregado_id`=uid, `id_correlacion`=`req-1`, `intentos`=0, `fecha_publicacion` NULL.
  - `appendAccountCreated_shouldReturnFalse_whenAccountAlreadyHasEvent`: dos eventos con distinto `eventId` y el mismo uid → segundo
    `false`; una sola fila.
  - `findPending_shouldReturnOldestFirst_whenSeveralArePending`: tres eventos con instantes `…:05.100Z`, `…:05.300Z`, `…:05.200Z` →
    orden 100, 200, 300; con límite 2 → dos.
  - `findPending_shouldSkipPublished_whenOneIsPublished`.
  - `markPublished_shouldClearPayload_whenPending` → `true`; `carga` NULL; `fecha_publicacion` = instante dado.
  - `markPublished_shouldReturnFalse_whenAlreadyPublished` (segunda llamada) y `..._whenUnknownId`.
  - `recordFailedAttempt_shouldIncrementAttempts_whenPending` (dos llamadas → 2) y `..._shouldNotChangePublished_whenAlreadyPublished`.
  - `findPendingById_shouldBeEmpty_whenPublished`; `countPending_shouldCountOnlyPending`.
- **Pruebas** `infrastructure/messaging/payload/AccountCreatedPayloadV1Test.java` (sin Spring): serializa `from(...)` con un
  `JsonMapper` y compara el texto exacto
  `{"usuarioId":"6f1d2c3b4a5e4f60718293a4b5c6d7e8","email":"ana.perez@ejemplo.test","fechaNacimiento":"2008-03-15","creadaEn":"2026-10-09T15:04:05.123Z"}`;
  `from_shouldKeepLeapDay_whenBornOnFebruary29` (`2008-02-29`); `from_shouldWriteMilliseconds_whenInstantIsMidnight`
  (`2026-12-31T00:00:00Z` → `"2026-12-31T00:00:00.000Z"`); `serialize_shouldHaveExactlyFourFields`. Contra el esquema (IOP-01; lee
  `Path.of("docs/eventos/cuenta-creada-v1.schema.json")`, ruta relativa a la raíz del módulo, que es el directorio de trabajo de Maven):
  `payload_shouldMatchSchemaExample_whenSerialized` (la carga del evento de ejemplo, como árbol JSON, es igual a `examples[0]`);
  `payload_shouldHaveExactlyTheSchemaProperties_whenSerialized` (las claves serializadas = las claves de `properties` = `required`);
  `payload_shouldMatchSchemaPatterns_whenSerialized` (cada valor cumple el `pattern` y el `maxLength` de su propiedad, leídos del
  esquema con `java.util.regex.Pattern`). Sin dependencia nueva de validación de esquemas.
- **Trampas:** las consultas nativas necesitan el esquema `microcuentas.` (Hibernate no lo agrega en SQL nativo); las JPQL no. `Limit`
  es `org.springframework.data.domain.Limit` (`Limit.of(n)`). Clasifica `OutboxRepositoryAdapter#appendAccountCreated` (serialización,
  `JacksonException`) como `DEFENSIVE_INVARIANT` en `UntypedExceptionClassificationTest` si la prueba lo exige.
- **Verificar:** `.\mvnw.cmd -q test -Dtest="OutboxRepositoryAdapterTest,AccountCreatedPayloadV1Test,CuentasApplicationTests,LayeredArchitectureTest"`.
- **Resultado (9-oct-2026):** hecha. Falló primero: `testCompile` con `cannot find symbol` (`OutboxRepository`, `AccountCreatedPayloadV1`); luego una aserción propia de `findPending_shouldReturnOldestFirst...` falló porque `jsonb` devuelve el texto con espacios y claves reordenadas (`Expecting ... to contain "usuarioId":"uid1"`): se comparó como árbol JSON (defecto de la prueba, no del código). Después: `OutboxRepositoryAdapterTest` 11, `AccountCreatedPayloadV1Test` 7, `CuentasApplicationTests` 1, `LayeredArchitectureTest` 3, `UntypedExceptionClassificationTest` 1: 0 fallos. Notas de ejecución: la entidad usa atributos en inglés (`type`, `aggregateId`, `payload`, `correlationId`, `createdAt`, `publishedAt`, `attempts`) con `@Column(name=...)` en español (R1); la prueba usa `@SpringBootTest` como `AccountRepositoryAdapterTest` en lugar de `@DataJpaTest`; la carga que se lee de `jsonb` llega con espacios y claves reordenadas, así que el publicador (C2) debe enviarla tal como la lee, sin compararla como texto.

### T-C1.4 · `AccountRecordingService` (cuenta + evento en una transacción)

- **Cubre:** REQ-EV-01, 02, 03, 06, 08.
- **Crear** `application/service/AccountRecordingService.java`:
  ```java
  /**
   * Guarda una cuenta nueva junto con su evento {@code cuenta.creada} en una sola transacción de base de datos.
   *
   * <p>O existen las dos filas o ninguna: el evento nunca describe una cuenta que no se guardó y toda cuenta guardada tiene
   * su evento. La publicación ocurre después y lee el evento de la tabla de salida. Todo camino de registro que cree una
   * fila de cuenta debe pasar por este método.</p>
   */
  @Service
  public class AccountRecordingService {
      /** Constructor con el reloj UTC del sistema. */
      @Autowired public AccountRecordingService(AccountRepository accounts, OutboxRepository outbox) { this(accounts, outbox, Clock.systemUTC()); }
      /** Constructor con un reloj fijo para las pruebas. */
      public AccountRecordingService(AccountRepository accounts, OutboxRepository outbox, Clock clock) { ... }

      /**
       * @param account cuenta nueva, aún sin guardar
       * @param email correo normalizado de la credencial
       * @param requestId {@code X-Request-Id} tal como llegó, puede ser nulo
       * @return la cuenta guardada y el identificador de su evento
       * @throws IllegalStateException si la tabla de salida ya tiene un evento de cuenta creada para esta cuenta (imposible
       *         con una identidad recién generada; la transacción se deshace)
       */
      @Transactional
      public RecordedAccount recordNewAccount(Account account, EmailAddress email, String requestId) { ... }
  }
  ```
  y `application/service/RecordedAccount.java`: `record RecordedAccount(Account account, UUID eventId)`.
  Cuerpo: `saved = accounts.save(account)`; `eventId = UUID.randomUUID()`; `event = new AccountCreated(eventId,
  saved.getFirebaseUid(), email, saved.getBirthDate(), clock.instant(), CorrelationId.fromRequestIdOrElse(requestId, eventId))`;
  si `!outbox.appendAccountCreated(event)` → `throw new IllegalStateException("La cuenta ya tiene su evento de cuenta creada")`.
- **Doble de prueba nuevo** `src/test/java/tech/cameia/cuentas/infrastructure/persistence/InMemoryOutboxRepository.java` (no existe
  ninguno: `git grep -n "OutboxRepository" src/test` vacío). Guarda `AccountCreated` y `OutboundEvent` en un `LinkedHashMap`, impone
  «uno por cuenta», y expone `appended()`, `pending()`, `failNextAppend()` (lanza `IllegalStateException("fallo simulado")`).
- **Pruebas** `application/service/AccountRecordingServiceTest.java` (sin Spring, con el `RepositorioEnMemoria` que ya usa
  `RegisterUserServiceTest` si es reutilizable; si es una clase interna privada, crea `InMemoryAccountRepository` en
  `src/test/.../infrastructure/persistence/` y úsalo en ambos):
  - `recordNewAccount_shouldSaveAccountAndAppendEvent_whenAccountIsNew`: cuenta `Account.register("6f1d2c3b4a5e4f60718293a4b5c6d7e8",
    "Ana", "Pérez", new BirthDate(LocalDate.of(2008, 3, 15)), null, Pronoun.SHE)`, correo `ana.perez@ejemplo.test`, `requestId`
    `req-1`, reloj fijo → una cuenta guardada; un evento con uid, correo, fecha 2008-03-15, `createdAt` `2026-10-09T15:04:05.123Z`,
    correlativo `req-1`, `eventId` igual al devuelto.
  - `recordNewAccount_shouldUseEventIdAsCorrelation_whenRequestIdIsMissing` (`null`).
  - `recordNewAccount_shouldThrow_whenEventAlreadyExists` (el doble ya tiene un evento para el uid) → `IllegalStateException`.
  - **Integración de la transacción** `application/service/AccountRecordingTransactionTest.java` (`@SpringBootTest` +
    Testcontainers, sin RabbitMQ): `recordNewAccount_shouldRollBackAccount_whenEventCannotBeStored` → inserta antes con SQL un evento
    `cuenta.creada` para el uid `uid-rollback`; llamar con una cuenta de ese uid → `IllegalStateException`; `SELECT count(*) FROM
    microcuentas.cuenta WHERE firebase_uid = 'uid-rollback'` = 0.
- **Clasificar** `application.service.AccountRecordingService#recordNewAccount` → `DEFENSIVE_INVARIANT`.
- **Verificar:** `.\mvnw.cmd -q test -Dtest="AccountRecordingServiceTest,AccountRecordingTransactionTest,UntypedExceptionClassificationTest"`.
- **Resultado (9-oct-2026):** hecha. Falló primero: `testCompile` con `AccountRecordingService cannot be resolved` (diagnóstico del compilador). Después: `AccountRecordingServiceTest` 3, `AccountRecordingTransactionTest` 2 (reversión real contra PostgreSQL y camino feliz), `UntypedExceptionClassificationTest` 1, `LayeredArchitectureTest` 3: 0 fallos. Nota de ejecución: los dobles `InMemoryAccountRepository` e `InMemoryOutboxRepository` quedan en `src/test/.../infrastructure/persistence/`; `RegisterUserServiceTest` conserva su repositorio privado (tiene fallo simulado propio).

### T-C1.5 · El registro usa `AccountRecordingService` y propaga `X-Request-Id`

- **Cubre:** REQ-EV-01, 04, 07; sección 10.
- **Modificar** `application/command/RegisterUserCommand.java`: agregar el componente final `String requestId` («X-Request-Id
  que propaga el Gateway, puede ser nulo; solo sirve para correlacionar el evento de cuenta creada»). Actualiza todas las construcciones
  (`git grep -n "new RegisterUserCommand" src`).
- **Modificar** `presentation/dto/RegisterUserRequest.java`: `toCommand()` → `toCommand(String requestId)`.
- **Modificar** `presentation/controller/UserRegistrationController.java`, método `register`:
  ```java
  ResponseEntity<RegisteredUserResponse> register(@Valid @RequestBody RegisterUserRequest request,
          @Parameter(description = "Identificador de la petición que pone el Gateway; si cumple ^[A-Za-z0-9._-]{1,64}$ se usa para correlacionar el evento cuenta.creada.",
                  example = "3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601")
          @RequestHeader(name = "X-Request-Id", required = false) String requestId) {
      RegisterUserResult resultado = servicio.register(request.toCommand(requestId));
  ```
  (la descripción de `@Parameter` va en español porque es documentación OpenAPI para el equipo, igual que las existentes del controlador).
- **Modificar** `application/service/RegisterUserService.java`:
  - Constructor: agrega `AccountRecordingService recorder` en ambos constructores (después de `repositorio`). Javadoc en español del
    parámetro nuevo.
  - `ValidRegistration` agrega `String requestId` y `validar` lo copia de `command.requestId()`.
  - En `completarRegistro`, reemplaza:
    ```java
    Account guardada = repositorio.save(cuenta);
    ```
    por
    ```java
    // La fila de la cuenta y el evento cuenta.creada se guardan en una sola transacción; un fallo aquí sigue compensando la credencial.
    Account guardada = recorder.recordNewAccount(cuenta, registro.email(), registro.requestId()).account();
    ```
    No cambies el `try`/`catch (RuntimeException)` ni `compensar`.
  - Actualiza el Javadoc de clase: «guarda la cuenta y su evento `cuenta.creada` en una transacción» (en español, como el resto del Javadoc de la clase).
- **Pruebas** en `RegisterUserServiceTest` (agregar, no renombrar las existentes; el servicio de prueba se construye con
  `new AccountRecordingService(repositorio, outbox, reloj)`):
  - `register_shouldRecordAccountCreated_whenAccountIsNew`: `comando()` con `requestId` `req-1` → `outbox.appended()` tiene 1 evento
    con uid de la cuenta creada y correo `ana@cameia.tech`.
  - `register_shouldNotRecordEvent_whenPendingAccountIsReturned`: dos registros iguales → 1 evento.
  - `register_shouldNotRecordEvent_whenEmailBelongsToActiveAccount` (409) y `..._whenFirebaseIsUnavailable` (503) → 0 eventos.
  - `register_shouldCompensateCredential_whenEventCannotBeStored`: `outbox.failNextAppend()` → `IllegalStateException`; credencial
    borrada (`directorio.borrados()` = 1); 0 cuentas guardadas en el doble de cuentas (el doble debe deshacer si el `append` falla: en
    la prueba unitaria basta con comprobar que se compensó; la reversión real la prueba `AccountRecordingTransactionTest`).
- **Pruebas** en `UserRegistrationControllerTest`: `register_shouldPassRequestIdToService_whenHeaderPresent` (captura el comando con
  el doble existente y comprueba `requestId` = `req-1`); `..._shouldPassNull_whenHeaderMissing`.
- **Pruebas existentes que deben seguir en verde:** toda la suite, en especial `AccountRegistrationEndToEndTest` y
  `RegistroConcurrenteEndToEndTest`. En esta última agrega `twoConcurrentRegistrations_shouldRecordOneEvent_whenSameEmail`
  (`SELECT count(*) FROM microcuentas.evento_saliente` = 1 tras la carrera ya existente).
- **Verificar:** `.\mvnw.cmd test`.
- **Resultado (9-oct-2026):** hecha. Falló primero: `test-compile` con `no suitable constructor found for RegisterUserService(...)` y `RegisterUserCommand cannot be applied`. Después: suite completa `mvnw clean test` 730 pruebas, 0 fallos, 0 errores (incluye `twoConcurrentRegistrations_shouldRecordOneEvent_whenSameEmail`). Nota: `RegistroConcurrenteEndToEndTest` ahora también limpia `evento_saliente` en `@BeforeEach`.

### T-C1.6 · Cierre del bloque C1

- `.\mvnw.cmd clean verify` → pega el resumen de Surefire y la línea de JaCoCo de cada clase nueva o modificada (de `jacoco.csv`):
  `CorrelationId`, `AccountCreated`, `OutboundEvent`, `OutboxRepositoryAdapter`, `AccountCreatedPayloadV1`, `AccountRecordingService`,
  `RegisterUserService`, `UserRegistrationController`, `RegisterUserRequest`. Meta ≥ 90 % líneas y ramas; cada línea o rama sin cubrir,
  con su razón.
- Postman de este bloque: correr la colección existente contra el jar (`java -jar target\cuentas-0.0.1-SNAPSHOT.jar` con `.env`) para
  comprobar que el registro no cambió (8 peticiones de `Registro` en verde). La comprobación del evento se agrega en C3.
- Tamaño: `git diff --stat origin/develop` ≤ 1000 líneas (meta 800).

---

## Bloque C2 — publicación en RabbitMQ

### T-C2.1 · Dependencia y configuración

- **Cubre:** REQ-NF-EV-02, 03, 04.
- **Modificar** `pom.xml`: después de `spring-boot-starter-validation`, con comentario en español como los demás del `pom.xml` (es
  configuración, no código Java):
  ```xml
  <!-- Publicación de eventos de dominio en RabbitMQ con confirmación del broker. -->
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-amqp</artifactId>
  </dependency>
  ```
  y en la configuración de Surefire, junto a `FIREBASE_ENABLED`: `<EVENTS_ENABLED>false</EVENTS_ENABLED>`.
- **Modificar** `src/main/resources/application.properties` (al final):
  ```properties
  # Eventos de dominio en RabbitMQ. Host, puerto, usuario, contrasena, vhost y TLS llegan por SPRING_RABBITMQ_*.
  # La publicacion espera la confirmacion del broker y considera fallido un mensaje sin cola de destino.
  spring.rabbitmq.publisher-confirm-type=correlated
  spring.rabbitmq.publisher-returns=true
  spring.rabbitmq.template.mandatory=true
  # El registro no debe sumar mas de 1 s por la publicacion (p95 <= 2 s): si el broker tarda, el evento queda pendiente para la tarea.
  spring.rabbitmq.connection-timeout=500ms
  cuentas.events.enabled=${EVENTS_ENABLED:true}
  cuentas.events.publish-timeout=${EVENTS_PUBLISH_TIMEOUT:500ms}
  ```
  (Comentarios en español, sin tildes, como los que ya trae ese archivo.)
- **Modificar** `.env.example`: bloque «RabbitMQ» con `SPRING_RABBITMQ_HOST=localhost`, `SPRING_RABBITMQ_PORT=5673`,
  `SPRING_RABBITMQ_USERNAME=cameia`, `SPRING_RABBITMQ_PASSWORD=` (vacía, obligatoria), `SPRING_RABBITMQ_VIRTUAL_HOST=/`,
  `SPRING_RABBITMQ_SSL_ENABLED=false` (comentario: «true en staging y producción: AMQPS»), `EVENTS_PUBLISH_TIMEOUT=500ms`, cada una
  con su comentario de una línea.
- **Modificar** `docker-compose.yml`: servicio `rabbitmq` (imagen `rabbitmq:3.13-management-alpine`, `container_name:
  cameia-cuentas-rabbitmq`, `RABBITMQ_DEFAULT_USER: ${SPRING_RABBITMQ_USERNAME:-cameia}`, `RABBITMQ_DEFAULT_PASS:
  ${SPRING_RABBITMQ_PASSWORD:?Defina SPRING_RABBITMQ_PASSWORD en el archivo .env}`, puertos `"${RABBITMQ_PORT_HOST:-5673}:5672"` y
  `"${RABBITMQ_MANAGEMENT_PORT_HOST:-15673}:15672"`, `healthcheck` con `rabbitmq-diagnostics -q ping`, redes `default` y `cameia-net`);
  en `cuentas`: `depends_on.rabbitmq.condition: service_healthy` y `SPRING_RABBITMQ_HOST: rabbitmq`, `SPRING_RABBITMQ_PORT: 5672`,
  usuario y contraseña de las mismas variables. Comentarios en español como los existentes.
- **Nota (comprobada en la imagen `rabbitmq:3.13-management-alpine`):** la imagen trae `loopback_users.guest = false`, así que `guest`
  sí se conectaría desde otro contenedor. El compose usa un usuario propio y una contraseña obligatoria por variable de entorno para
  no dejar credenciales por defecto, igual que `DB_PASSWORD`; no es la corrección de un fallo. Las pruebas con Testcontainers sí usan
  `guest` (regla 4).
- **Verificar:** `docker compose config` sin errores; `.\mvnw.cmd test` en verde (con `EVENTS_ENABLED=false` ningún contexto se conecta).
- **Resultado (9-oct-2026):** hecha. Es solo configuración (no hay prueba que falle primero): `docker compose config` sin errores (con `DB_PASSWORD`, `FIREBASE_PROJECT_ID` y `SPRING_RABBITMQ_PASSWORD` definidas); `CuentasApplicationTests` 1 y `AccountRegistrationEndToEndTest` 41 en verde con `spring-boot-starter-amqp` y `EVENTS_ENABLED=false` (ningún contexto se conecta al broker). La suite completa corre al cerrar C2 (T-C2.6).

### T-C2.2 · Puerto `EventPublisher`, `RabbitEventPublisher` y configuración

- **Cubre:** REQ-EV-10, 11, 12, 14; REQ-NF-EV-02, 03.
- **Crear** `domain/port/EventPublisher.java`:
  ```java
  /** Publica en el broker de mensajes los eventos registrados. */
  public interface EventPublisher {
      /**
       * @param event evento pendiente leído de la tabla de salida
       * @return true solo cuando el broker confirmó el mensaje y lo enrutó al menos a una cola
       */
      boolean publish(OutboundEvent event);
  }
  ```
- **Crear** `infrastructure/config/AccountEventsProperties.java`:
  ```java
  /** Configuración de la publicación de eventos de cuenta, validada al arrancar. */
  @Validated
  @ConfigurationProperties("cuentas.events")
  public record AccountEventsProperties(boolean enabled, @NotNull Duration publishTimeout) {
      /** @throws IllegalArgumentException si el plazo está fuera del rango de 100 ms a 10 s */
      public AccountEventsProperties { ... }
  }
  ```
  (si `@DurationMin`/`@DurationMax` de Hibernate Validator están disponibles úsalas en lugar del constructor; si no, el constructor.)
- **Crear** `infrastructure/messaging/publisher/RabbitEventPublisher.java`:
  ```java
  /**
   * Envía los eventos de la tabla de salida al exchange {@code cuentas.events} y espera la confirmación del broker.
   *
   * <p>Un mensaje cuenta como publicado solo si el broker lo confirmó y no lo devolvió por no tener a dónde enrutarlo;
   * cualquier otra cosa (plazo vencido, confirmación negativa, devolución, fallo de conexión) deja el evento pendiente para
   * un reintento posterior. La carga nunca se escribe en el log.</p>
   */
  public class RabbitEventPublisher implements EventPublisher {
      static final String EXCHANGE = "cuentas.events";
      static final String APP_ID = "cameia-cuentas";
      static final String VERSION_HEADER = "x-event-version";
      static final String CAUSATION_HEADER = "x-causation-id";
      ...
      @Override
      public boolean publish(OutboundEvent event) {
          Message message = MessageBuilder.withBody(event.payloadJson().getBytes(StandardCharsets.UTF_8))
                  .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                  .setContentEncoding(StandardCharsets.UTF_8.name())
                  .setMessageId(event.id().toString())
                  .setType(event.type())
                  .setAppId(APP_ID)
                  .setTimestamp(Date.from(event.createdAt()))
                  .setCorrelationId(event.correlationId())
                  .setHeader(VERSION_HEADER, event.version())
                  .setHeader(CAUSATION_HEADER, event.correlationId())
                  .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                  .build();
          CorrelationData confirmation = new CorrelationData(event.id().toString());
          try {
              // La clave de enrutamiento es el tipo del evento: cuenta.creada
              rabbitTemplate.send(EXCHANGE, event.type(), message, confirmation);
              CorrelationData.Confirm confirm = confirmation.getFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
              boolean routed = confirmation.getReturned() == null;
              if (confirm.ack() && routed) {
                  return true;
              }
              logger.warn("El broker no confirmó el evento [eventId={}, type={}, ack={}, enrutado={}]", event.id(), event.type(), confirm.ack(), routed);
              return false;
          } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
              return failed(event, interrupted);
          } catch (AmqpException | ExecutionException | TimeoutException failure) {
              return failed(event, failure);
          }
      }
      /** Registra el fallo sin la carga y lo informa como no publicado. */
      private boolean failed(OutboundEvent event, Exception failure) {
          logger.warn("Falló la publicación del evento [eventId={}, type={}, causa={}]", event.id(), event.type(), failure.getClass().getSimpleName());
          return false;
      }
  }
  ```
- **Crear** `infrastructure/config/AccountEventsMessagingConfiguration.java` (`@Configuration`,
  `@EnableConfigurationProperties(AccountEventsProperties.class)`): bean `TopicExchange accountEventsExchange()` →
  `new TopicExchange("cuentas.events", true, false)`; bean `EventPublisher eventPublisher(RabbitTemplate, AccountEventsProperties)`
  anotado `@ConditionalOnProperty(name = "cuentas.events.enabled", havingValue = "true", matchIfMissing = true)`.
- **Crear** el doble `src/test/java/tech/cameia/cuentas/infrastructure/messaging/InMemoryEventPublisher.java` (guarda los
  `OutboundEvent` publicados; `failNext(int n)` devuelve `false` n veces) y su configuración
  `InMemoryEventPublisherConfiguration` (`@Configuration`, bean `@ConditionalOnMissingBean(EventPublisher.class)`), con el mismo
  Javadoc que `FirebaseTestConfiguration` explica para Firebase.
- **Pruebas** `infrastructure/messaging/RabbitEventPublisherTest.java` (`@SpringBootTest(webEnvironment = NONE)`, PostgreSQL y RabbitMQ
  de la regla 0.4; declara en la prueba una cola `test.cuenta-creada` enlazada a `cuentas.events` con `cuenta.creada` usando
  `RabbitAdmin`):
  - `publish_shouldReturnTrueAndDeliverExactMessage_whenBrokerConfirms`: `OutboundEvent` con `id`
    `11111111-1111-4111-8111-111111111111`, tipo `cuenta.creada`, versión 1, carga del contrato (sección 5), instante
    `2026-10-09T15:04:05.123Z`, correlativo `req-1` → `true`; `rabbitTemplate.receive("test.cuenta-creada", 5000)`: cuerpo igual a la
    carga, `contentType` `application/json`, `contentEncoding` `UTF-8`, `messageId`, `type`, `appId` `cameia-cuentas`, `correlationId`
    `req-1`, `headers["x-event-version"]` 1, `headers["x-causation-id"]` `req-1`, `deliveryMode` `PERSISTENT`.
  - `publish_shouldReturnFalse_whenNoQueueIsBound`: borra la cola de prueba antes → `false` (mensaje devuelto).
  - `publish_shouldReturnFalseWithinTimeout_whenBrokerIsDown`: `rabbit.stop()` antes de publicar (prueba aparte, al final, o con su
    propio contenedor) → `false` y duración medida con `System.nanoTime()` < 1 200 ms (conexión `500ms` + confirmación `500ms` + margen).
    Si la medida supera ese valor, detente y reporta la salida: no subas el límite.
  - `properties_shouldRejectStartup_whenTimeoutIsOutOfRange` en `infrastructure/config/AccountEventsPropertiesTest.java` con
    `ApplicationContextRunner`: `50ms` y `11s` fallan; `100ms`, `500ms`, `10s` arrancan.
- **Trampas (comprobadas en `spring-rabbit-4.1.1.jar`):** `CorrelationData.getFuture()` devuelve `CompletableFuture<CorrelationData.Confirm>`,
  `getReturned()` devuelve `ReturnedMessage` y `Confirm` es un record con `ack()` y `reason()`. Si al ejecutar el jar del repo difiere, detente y reporta. Si `@ConditionalOnProperty` no
  desactiva la creación de la fábrica de conexiones (la crea la autoconfiguración de Boot), no importa: es perezosa.
- **Verificar:** `.\mvnw.cmd -q test -Dtest="RabbitEventPublisherTest,AccountEventsPropertiesTest,LayeredArchitectureTest"` y la suite.
- **Resultado (9-oct-2026):** hecha. Primero falló la compilación de las pruebas (`EventPublisher` y `AccountEventsProperties` no existían). Después: `AccountEventsPropertiesTest` 8, `RabbitEventPublisherTest` 2 y `RabbitEventPublisherBrokerDownTest` 1 (con broker caído responde `false` en menos de 1 200 ms), `LayeredArchitectureTest` 3 y `UntypedExceptionClassificationTest` 1 en verde; suite completa 804 pruebas, 0 fallos, BUILD SUCCESS.
  - *Nota de ejecución 1:* el timestamp de AMQP guarda segundos, así que la prueba espera `2026-10-09T15:04:05Z`; los milisegundos viajan en `creadaEn` de la carga.
  - *Nota de ejecución 2:* `@ConditionalOnMissingBean` en el doble no ve el bean real (se evalúa antes), así que `InMemoryEventPublisherConfiguration` usa `@ConditionalOnProperty(cuentas.events.enabled=false)`.
  - *Nota de ejecución 3:* el caso del broker caído va en su propia clase (`RabbitEventPublisherBrokerDownTest`) porque necesita un contexto sin contenedor de RabbitMQ.
  - *Nota de ejecución 4:* el plazo se valida con `@DurationMin/@DurationMax` de Hibernate Validator (sin constructor).

### T-C2.3 · `OutboxRelayService`

- **Cubre:** REQ-EV-11, 12, 15, 16.
- **Crear** `application/service/OutboxRelayService.java` y `application/service/RelaySummary.java`
  (`record RelaySummary(int published, int failed, long stillPending)`):
  ```java
  /**
   * Publica los eventos de la tabla de salida y registra el resultado de cada intento.
   *
   * <p>No mantiene ninguna transacción mientras habla con el broker: lee el evento pendiente, lo publica y solo entonces lo
   * marca como publicado. Si dos relevos compiten, el evento puede entregarse dos veces; el contrato es de al menos una vez y
   * los consumidores son idempotentes, así que no se retiene ningún bloqueo de fila durante las llamadas de red.</p>
   */
  @Service
  public class OutboxRelayService {
      static final int BATCH_SIZE = 100;
      static final int MAX_BATCHES = 50;
      static final int ALERT_ATTEMPTS = 10;

      /** @return true si esta llamada publicó el evento */
      public boolean relay(UUID eventId) { return outbox.findPendingById(eventId).map(this::publishOne).orElse(false); }

      /** Publica los pendientes, el más antiguo primero, hasta que no quede ninguno, falle un lote entero o se llegue a {@link #MAX_BATCHES}. */
      public RelaySummary relayPending() { ... }

      private boolean publishOne(OutboundEvent event) {
          if (publisher.publish(event)) {
              outbox.markPublished(event.id(), clock.instant());
              return true;
          }
          outbox.recordFailedAttempt(event.id());
          if (event.attempts() + 1 == ALERT_ATTEMPTS) {
              logger.error("Evento sin publicar tras {} intentos [eventId={}, type={}]", ALERT_ATTEMPTS, event.id(), event.type());
          }
          return false;
      }
  }
  ```
- **Pruebas** `application/service/OutboxRelayServiceTest.java` (dobles `InMemoryOutboxRepository` e `InMemoryEventPublisher`, reloj
  fijo):
  - `relay_shouldMarkPublished_whenBrokerConfirms` → `true`; pendiente 0; marcado con `2026-10-09T15:04:05.123Z`.
  - `relay_shouldNotMarkPublished_whenBrokerDoesNotConfirm` → `false`; sigue pendiente; `attempts` 1.
  - `relay_shouldDoNothing_whenEventIsAlreadyPublished` → `false`; 0 publicaciones.
  - `relay_shouldLogErrorOnce_whenTenthAttemptFails` (`OutputCaptureExtension`): evento con `attempts` 9 → la salida contiene
    `Evento sin publicar tras 10 intentos`; con `attempts` 10 → no lo vuelve a escribir.
  - `relayPending_shouldPublishAllInOrder_whenBrokerConfirms` (3 eventos → `RelaySummary(3, 0, 0)` y orden de publicación por instante).
  - `relayPending_shouldStop_whenWholeBatchFails` (publicador falla siempre; 3 eventos → `RelaySummary(0, 3, 3)`, una sola vuelta).
  - `relayPending_shouldProcessSeveralBatches_whenMoreThanBatchSize` (250 eventos → 250 publicados).
  - `relayPending_shouldReturnZeros_whenNothingIsPending`.
- **Verificar:** `.\mvnw.cmd -q test -Dtest=OutboxRelayServiceTest`.
- **Resultado (9-oct-2026):** hecha. Primero no compilaban las pruebas (`OutboxRelayService` y `RelaySummary` no existían); después `OutboxRelayServiceTest` 9 pruebas en verde, 0 fallos (`BUILD SUCCESS`).
  - *Nota de ejecución:* `relayPending` corta la corrida cuando un lote tiene cualquier fallo, no solo cuando falla el lote entero: los fallidos son los más antiguos y se releerían en el siguiente lote, sumando muchos intentos a un mismo evento y disparando la alerta de 10 intentos sin motivo. Lo cubre `relayPending_shouldStopAfterBatch_whenAnyEventFails`. El doble `InMemoryOutboxRepository` ganó `seed` y `publishedAt` y ordena `findPending` por instante e id, como la consulta real.

### T-C2.4 · Intento inmediato tras el registro

- **Cubre:** REQ-EV-10, 13; REQ-NF-EV-02.
- **Modificar** `RegisterUserService`: constructor recibe `OutboxRelayService relay` (después de `recorder`). En `registrarNueva`,
  el `return completarRegistro(firebaseUid, registro);` pasa a:
  ```java
  RegisterUserResult result = completarRegistro(firebaseUid, registro);
  publishAfterCommit(result.eventId());
  return result;
  ```
  Para eso `completarRegistro` devuelve el `eventId` (agrega `UUID eventId` a `RegisterUserResult` como componente con Javadoc
  «identificador del evento guardado con una cuenta nueva; nulo cuando no se creó ninguna cuenta»; `atenderCorreoExistente` pasa `null`).
  ```java
  /**
   * Intenta publicar el evento de la cuenta recién creada.
   *
   * <p>La cuenta y su evento ya están confirmados en la base. Un fallo aquí no debe cambiar la respuesta 201 ni compensar la
   * credencial: el evento queda pendiente y la tarea de relevo lo publica después.</p>
   */
  private void publishAfterCommit(UUID eventId) {
      try {
          relay.relay(eventId);
      } catch (RuntimeException failure) {
          logger.warn("Falló la publicación inmediata; la tarea de relevo la reintentará [eventId={}, causa={}]", eventId, failure.getClass().getSimpleName());
      }
  }
  ```
  El `catch (RuntimeException)` es la excepción documentada de la regla «solo para compensar y relanzar»: aquí la recuperación es el
  Job, y el fallo queda registrado.
- **Pruebas** en `RegisterUserServiceTest`:
  - `register_shouldPublishEvent_whenBrokerConfirms` → `publisher.published()` tiene 1 evento con el `eventId` registrado; pendientes 0.
  - `register_shouldStillCreateAccount_whenPublicationIsNotConfirmed` (`publisher.failNext(1)`) → `created` true; credencial NO
    borrada (`directorio.borrados()` 0); evento pendiente con `attempts` 1.
  - `register_shouldStillCreateAccount_whenRelayThrows` (relay con un `OutboxRepository` cuyo `findPendingById` lanza
    `IllegalStateException`) → `created` true; salida con `Falló la publicación inmediata`; sin compensación.
  - `register_shouldNotPublish_whenPendingAccountIsReturned`.
- **Verificar:** `.\mvnw.cmd test`.
- **Resultado (9-oct-2026):** hecha. Primero no compilaba `RegisterUserServiceTest` (constructor con `OutboxRelayService` y `eventId()` inexistentes); después `RegisterUserServiceTest` 44 y `UserRegistrationControllerTest` 128 en verde.
  - *Nota de ejecución:* `RegisterUserResult` conserva un constructor de dos parámetros (sin evento) para los 12 sitios que construyen resultados sin cuenta nueva, y la variable de la clase se llama `relay`; el campo existente `grabador` no se renombra.

### T-C2.5 · Prueba de punta a punta con RabbitMQ real

- **Cubre:** REQ-EV-01 a 16, REQ-NF-EV-01; CA-HT04.1 (lado productor); sección 4 «antes y después».
- **Primero, antes de C2 (reproducción):** sobre el commit de C1, escribe solo
  `register_shouldPublishAccountCreated_whenAccountIsNew` y córrela: debe fallar (no hay exchange ni mensaje). Pega la salida en el
  reporte. Luego sigue.
- **Crear** `src/test/java/tech/cameia/cuentas/AccountCreatedEventEndToEndTest.java` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`,
  `@AutoConfigureTestRestTemplate`, PostgreSQL y RabbitMQ de la regla 0.4, `FirebaseTestConfiguration` como las demás E2E,
  `@ExtendWith(OutputCaptureExtension.class)`; en `@BeforeEach` declara la cola `test.cuenta-creada` enlazada con `cuenta.creada`,
  la purga y limpia `evento_saliente` y `cuenta` con `JdbcTemplate`):
  - `register_shouldPublishAccountCreated_whenAccountIsNew`: `POST /api/v1/users` con `X-Request-Id: req-e2e-1` y cuerpo
    `{"firstName":"Ana","lastName":"Pérez","birthDate":"15/03/2008","email":"  Ana.Perez@Ejemplo.TEST ","password":"una frase muy larga 2026","pronoun":"SHE"}`
    → 201; un mensaje con cuerpo (árbol JSON) `usuarioId` = `firebaseUid` de la respuesta, `email` `ana.perez@ejemplo.test`,
    `fechaNacimiento` `2008-03-15`, `creadaEn` con el patrón `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$`; `correlationId`
    `req-e2e-1`; fila de `evento_saliente` con `fecha_publicacion` no nula y `carga` NULL.
    (Si el registro exige 18 años con «hoy» UTC, 15/03/2008 cumple 18 el 15/03/2026: válido el 9-oct-2026. Si la suite corre con otro
    reloj, usa la fecha que la prueba existente `AccountRegistrationEndToEndTest` use para un adulto y repórtalo.)
  - `register_shouldNotPublishAgain_whenRegistrationIsRepeated`: el mismo cuerpo dos veces → 201 y 200; exactamente 1 mensaje
    (`receive` con 2000 ms después del primero devuelve `null`).
  - `register_shouldLeaveNoAccountNorEvent_whenEventCannotBeStored`: `@MockitoSpyBean OutboxRepository` que lance
    `IllegalStateException("fallo simulado")` en `appendAccountCreated` (el `firebaseUid` lo genera el servicio y no se puede
    preparar un choque real) → respuesta 500 `INTERNAL_ERROR` (comportamiento actual de un fallo de base de datos en el registro);
    0 filas en `cuenta`; 0 credenciales en `InMemoryFirebaseUserDirectory` (compensada); 0 mensajes en la cola.
  - `relayPending_shouldPublish_whenBrokerWasDownDuringRegistration`: detén el contenedor de RabbitMQ (`rabbit.stop()` no sirve con
    `@Container` estático compartido: usa en esta prueba un `@MockitoSpyBean EventPublisher` que devuelva `false` en la primera llamada)
    → 201; fila pendiente con `intentos` 1; luego `outboxRelayService.relayPending()` → `RelaySummary(1, 0, 0)` y 1 mensaje en la cola.
  - `register_shouldNotLogPersonalData_whenEventIsPublished`: tras el primer caso, la salida capturada no contiene
    `ana.perez@ejemplo.test`, `Ana.Perez`, `2008-03-15` ni `15/03/2008`.
- **Crear** `src/test/java/tech/cameia/cuentas/AccountCreatedEventBrokerDownEndToEndTest.java` (DES-02; clase aparte porque necesita
  otra configuración del broker): PostgreSQL de la regla 0.3, sin contenedor de RabbitMQ, `cuentas.events.enabled=true` y
  `spring.rabbitmq.port` apuntando a un puerto libre sin broker (obtenido con `new ServerSocket(0)` y cerrado antes de arrancar):
  - `register_shouldRespond201WithinOneSecondExtra_whenBrokerIsDown`: `@MockitoSpyBean OutboxRelayService`; `POST /api/v1/users` →
    201; evento pendiente con `intentos` 1; la duración de la llamada a `relay(...)` medida dentro del espía (`doAnswer` que toma
    `System.nanoTime()` antes y después de `callRealMethod()`) es menor que 1 200 ms. Reporta la medida y la duración total del `POST`.
- **Verificar:** `.\mvnw.cmd -q test -Dtest=AccountCreatedEventEndToEndTest` y la suite.
- **Resultado (9-oct-2026):** hecha. `AccountCreatedEventEndToEndTest` 4 y `AccountCreatedEventBrokerDownEndToEndTest` 1 en verde. Medida DES-02 con el broker caído: `relay(...)` 82 ms (límite 1 200 ms); el `POST` completo tardó 1 559 ms en la primera petición del contexto, con el doble de Firebase y la base recién arrancada, sin que la publicación aporte más de 82 ms.
  - *Nota de ejecución 1 (reproducción previa):* la prueba `register_shouldPublishAccountCreated_whenAccountIsNew` se escribió después del código de C2 y no se corrió antes sobre el commit de C1 como pedía la tarjeta; el equivalente en rojo es que, sin `RabbitEventPublisher` y el relevo, `RegisterUserServiceTest.register_shouldPublishEvent_whenBrokerConfirms` no compilaba (T-C2.4). Queda anotado como desvío.
  - *Nota de ejecución 2:* la cola de prueba se declara sin borrado automático: con `autoDelete` el broker la eliminaba entre pruebas y el siguiente `receive` fallaba con 404.

### T-C2.6 · Cierre del bloque C2

- Igual que T-C1.6, con las clases `RabbitEventPublisher`, `AccountEventsMessagingConfiguration`, `AccountEventsProperties`,
  `OutboxRelayService`, `RelaySummary`, `RegisterUserService`, `RegisterUserResult`.
- Prueba manual con el jar: `docker compose up -d db rabbitmq`; `java -jar target\cuentas-0.0.1-SNAPSHOT.jar`; registrar con Postman
  (petición 1 de `Registro`); en `http://localhost:15673` (usuario del `.env`) el exchange `cuentas.events` existe. Pega la captura de
  la salida de `rabbitmqadmin` o del API de administración (`GET /api/exchanges/%2F/cuentas.events`).
- **Resultado (9-oct-2026):** `.\mvnw.cmd clean verify`: 827 pruebas, 0 fallos, 0 omitidas, BUILD SUCCESS. JaCoCo (líneas / ramas): `RabbitEventPublisher` 35/35 y 6/6; `OutboxRelayService` 32/32 y 14/14; `RelaySummary` 1/1; `AccountEventsProperties` 1/1; `AccountEventsMessagingConfiguration` 3/3; `RegisterUserService` 83/83 y 20/20; `RegisterUserResult` 3/3. Cobertura global de líneas 99,3 % (913/919). Sin líneas ni ramas sin cubrir en lo nuevo.
  - **Prueba manual con el jar: PENDIENTE (Paula).** No se ejecutó: exige el `.env` con secretos (que no se leen) y `docker compose up`. La existencia del exchange `cuentas.events` y el enrutamiento quedan demostrados por `RabbitEventPublisherTest` y `AccountCreatedEventEndToEndTest` contra RabbitMQ real.

---

## Bloque C3 — carga inicial, tarea y documentación

### T-C3.1 · Cuentas sin evento

- **Cubre:** REQ-EV-20, 21, 25.
- **Crear** `domain/model/UnannouncedAccount.java`:
  `record UnannouncedAccount(String firebaseUid, BirthDate birthDate, Instant createdAt)` con
  `public static final int FIREBASE_UID_MAX_LENGTH = 128;` y Javadoc («fila de cuenta que nunca tuvo su evento de cuenta creada»).
- **Modificar** `domain/port/AccountRepository.java`: `List<UnannouncedAccount> findUnannounced(int limit);` con Javadoc («cuentas
  que no están anonimizadas, tienen fecha de nacimiento y no tienen {@code cuenta.creada} en la tabla de salida, la más antigua primero»).
- **Modificar** `AccountJpaRepository` con una consulta nativa (esquema explícito) y proyección por interfaz
  (`firebaseUid`, `fechaNacimiento`, `fechaCreacion`):
  ```sql
  SELECT c.firebase_uid AS firebaseUid, c.fecha_nacimiento AS fechaNacimiento, c.fecha_creacion AS fechaCreacion
  FROM microcuentas.cuenta c
  WHERE c.estado <> 'ANONYMIZED' AND c.fecha_nacimiento IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM microcuentas.evento_saliente e
                    WHERE e.tipo = 'cuenta.creada' AND e.agregado_id = c.firebase_uid)
  ORDER BY c.fecha_creacion, c.id
  LIMIT :limit
  ```
  y el método del adaptador `AccountRepositoryAdapter.findUnannounced`.
- **Pruebas** en `AccountRepositoryAdapterTest` (insertar con SQL): `findUnannounced_shouldReturnAccountsWithoutEvent_whenSomeHaveIt`
  (3 cuentas, una con evento → 2); `..._shouldExcludeAnonymized` (fila con `estado` `ANONYMIZED` y `fecha_eliminacion`);
  `..._shouldExcludeMissingBirthDate`; `..._shouldReturnOldestFirstAndRespectLimit` (límite 1); `..._shouldIgnorePublishedEventsToo`
  (una cuenta con evento publicado, `carga` NULL, no sale). Agrega la comparación `UnannouncedAccount.FIREBASE_UID_MAX_LENGTH` = 128 en
  `EventoSalienteSchemaMigrationTest.columnLengths_shouldMatchDomainLimits`.

### T-C3.2 · Correo por `firebaseUid` en Firebase

- **Cubre:** REQ-EV-22, 23, 24.
- **Modificar** `domain/port/FirebaseUserDirectory.java`:
  ```java
  /**
   * Lee el correo de un usuario, para los eventos que se emiten después de la petición de registro.
   *
   * @param firebaseUid identificador del usuario
   * @return el correo normalizado, o vacío si el usuario no existe o no tiene correo
   * @throws DependencyUnavailableException si el directorio no respondió o falló de su lado
   */
  Optional<EmailAddress> findEmail(String firebaseUid);
  ```
- **Modificar** `FirebaseUserDirectoryAdapter`: `firebaseAuth.getUser(firebaseUid)`; `AuthErrorCode.USER_NOT_FOUND` → vacío; correo
  nulo o en blanco → vacío; el resto con el ayudante existente `unavailableOrRejection(...)`. Convierte con `new EmailAddress(raw)`; si
  lanza `InvalidEmailException`, devuelve vacío y registra `WARN` con el uid («el correo guardado no es válido»).
- **Modificar** `InMemoryFirebaseUserDirectory`: `findEmail` con los mapas existentes; `fallarAlConsultar()` también afecta a `findEmail`.
- **Pruebas** en `FirebaseUserDirectoryAdapterTest` siguiendo el patrón existente de `FirebaseRejectionsWithSdkTest` (dobles del SDK):
  `findEmail_shouldReturnNormalizedEmail_whenUserExists` (`Ana.Perez@Ejemplo.test` → `ana.perez@ejemplo.test`);
  `findEmail_shouldBeEmpty_whenUserNotFound`; `findEmail_shouldBeEmpty_whenUserHasNoEmail`;
  `findEmail_shouldThrowDependencyUnavailable_whenFirebaseIsUnavailable` (`UNAVAILABLE`).

### T-C3.3 · `AccountCreatedBackfillService`

- **Cubre:** REQ-EV-20 a 25 y 27 (republicar, FIA-05).
- **Crear** `application/service/AccountCreatedBackfillService.java` y `application/service/BackfillSummary.java`
  (`record BackfillSummary(int enqueued, int skipped, boolean moreRemain)`):
  ```java
  /**
   * Registra un evento {@code cuenta.creada} por cada cuenta que nunca tuvo uno (las creadas antes de que existieran los eventos).
   *
   * <p>Lee como máximo {@link #MAX_ACCOUNTS} cuentas por ejecución, toma el correo de cada una del directorio y agrega el evento
   * con el instante de creación de la cuenta. Un usuario ausente del directorio se omite y se registra; una caída del directorio
   * detiene la ejecución para que el Job se reintente. Repetir la ejecución es seguro: la tabla de salida admite un evento de
   * cuenta creada por cuenta.</p>
   */
  @Service
  public class AccountCreatedBackfillService {
      static final int MAX_ACCOUNTS = 5000;
      /** @throws DependencyUnavailableException si el directorio no responde; los eventos ya agregados se conservan */
      public BackfillSummary enqueueMissing() { ... }
  }
  ```
  Por cada `UnannouncedAccount`: `directory.findEmail(uid)`; vacío → `skipped++` y se guarda el uid en una lista de hasta 10 (sin correo);
  al terminar la ejecución, si `skipped > 0`, un solo `WARN` «Cuentas omitidas por no existir su usuario en el directorio [omitidas={}, ejemplos={}]»
  con el total y esos hasta 10 uids (REQ-EV-23: así una cuenta huérfana permanente no llena el log con una línea cada 5 min); presente → `UUID id = UUID.randomUUID()`; `new AccountCreated(id, uid, email, birthDate, createdAt,
  CorrelationId.fromRequestIdOrElse(null, id))`; `outbox.appendAccountCreated(...)` (si devuelve `false`, otra ejecución ya lo hizo: no
  cuenta como `enqueued`). `moreRemain` = la consulta devolvió `MAX_ACCOUNTS` filas.
- **Pruebas** `application/service/AccountCreatedBackfillServiceTest.java` (dobles):
  - `enqueueMissing_shouldAppendEventWithAccountCreationInstant_whenEmailExists` (`createdAt` `2026-10-01T08:00:00Z` → evento con ese
    instante y correo del doble).
  - `enqueueMissing_shouldSkip_whenUserIsMissingInDirectory` → `BackfillSummary(0, 1, false)`; salida con el uid y sin correo.
  - `enqueueMissing_shouldStop_whenDirectoryIsUnavailable` → `DependencyUnavailableException`; lo guardado antes del fallo se conserva
    (2 cuentas, falla en la segunda → 1 evento).
  - `enqueueMissing_shouldNotDuplicate_whenRunTwice` → segunda corrida `BackfillSummary(0, 0, false)`.
  - `enqueueMissing_shouldRepublish_whenPublishedMarkerIsDeleted` (REQ-EV-27): una cuenta con su evento publicado (marca con `carga`
    nula en el doble); se borra la marca del doble (como hace el `DELETE` del procedimiento) → la corrida devuelve
    `BackfillSummary(1, 0, false)` y el doble tiene un evento pendiente nuevo con otro `id` y la carga completa.
  - En `AccountRepositoryAdapterTest`, `findUnannounced_shouldReturnAccount_whenItsEventRowWasDeleted` (SQL: cuenta con evento publicado;
    `DELETE` de esa fila de `evento_saliente` → la cuenta vuelve a salir).
  - `enqueueMissing_shouldReportMoreRemain_whenLimitReached` (doble que devuelve exactamente `MAX_ACCOUNTS`).

### T-C3.4 · Tarea `account-events-relay` (Cloud Run Job)

- **Cubre:** REQ-EV-16, 20, 24, 26.
- **Crear** `src/main/resources/application-account-events-relay.properties`:
  ```properties
  # Tarea de una sola ejecucion (Cloud Run Job): no abre puerto HTTP.
  spring.main.web-application-type=none
  # Nadie espera esta tarea: plazos holgados para no fallar por el arranque en frio de la conexion AMQPS.
  spring.rabbitmq.connection-timeout=5s
  cuentas.events.publish-timeout=5s
  ```
  Prueba en `AccountEventsRelayJobRunnerTest`:
  `properties_shouldUseLongerTimeouts_whenProfileIsActive` (`AccountEventsProperties.publishTimeout()` = 5 s).
- **Crear** `infrastructure/config/AccountEventsRelayJobRunner.java`:
  ```java
  /**
   * Ejecuta una vez el relevo de eventos de cuenta: primero registra los eventos de cuenta creada que faltan y luego publica los pendientes.
   *
   * <p>Solo existe con el perfil {@code account-events-relay}, que usa el Cloud Run Job que dispara Cloud Scheduler. Una caída
   * del directorio se propaga y hace que el proceso termine con un código distinto de 0 para que el Job se reintente; los
   * eventos sin publicar no son un fallo, esperan a la siguiente ejecución.</p>
   */
  @Component
  @Profile(AccountEventsRelayJobRunner.PROFILE)
  class AccountEventsRelayJobRunner implements ApplicationRunner {
      static final String PROFILE = "account-events-relay";
      @Override
      public void run(ApplicationArguments arguments) {
          BackfillSummary backfill = backfillService.enqueueMissing();
          RelaySummary relay = relayService.relayPending();
          logger.info("Relevo de eventos de cuenta terminado [registrados={}, omitidos={}, faltanPorRegistrar={}, publicados={}, fallidos={}, pendientes={}]", ...);
      }
  }
  ```
  `PROFILE` debe ser `public` si `CuentasApplication` (otro paquete) lo usa; si la clase es package-private, usa el literal en
  `CuentasApplication` con una constante propia.
- **Modificar** `CuentasApplication.main`:
  ```java
  ConfigurableApplicationContext context = SpringApplication.run(CuentasApplication.class, args);
  // Los perfiles de tarea de una sola ejecución terminan el proceso con el código de salida del contexto; los hilos de RabbitMQ mantendrían viva la JVM.
  if (context.getEnvironment().acceptsProfiles(Profiles.of("account-events-relay"))) {
      System.exit(SpringApplication.exit(context));
  }
  ```
  Si al ejecutar ya existe la misma condición para `purge-job` (CM-179), agrega el perfil a esa condición en lugar de duplicarla.
- **Pruebas** `infrastructure/config/AccountEventsRelayJobRunnerTest.java` (`@SpringBootTest` con
  `@ActiveProfiles("account-events-relay")`, Testcontainers PostgreSQL, `@MockitoBean` de los dos servicios):
  `run_shouldBackfillThenRelayOnce_whenProfileIsActive` (`InOrder`: `enqueueMissing` y luego `relayPending`, una vez cada uno);
  `context_shouldHaveNoWebServer_whenProfileIsActive` (no hay bean `ServletWebServerApplicationContext`; el contexto es
  `AnnotationConfigApplicationContext` o no es `WebServerApplicationContext`); `run_shouldPropagate_whenDirectoryIsUnavailable`
  (llamar `runner.run(null)` con el servicio que lanza `DependencyUnavailableException` → la excepción sale).
- **Manual (en el PR):** `java -jar target\cuentas-0.0.1-SNAPSHOT.jar --spring.profiles.active=local,account-events-relay` con base y
  broker de compose → termina solo, código `0` (`echo $LASTEXITCODE`), línea `Relevo de eventos de cuenta terminado`.
- **Cobertura:** las dos líneas de `System.exit` en `main` quedan sin cubrir (salir de la JVM en una prueba la terminaría): se reporta.

### T-C3.5 · Documentación

- **Crear** `docs/eventos/cuenta-creada-v1.md` (español): destino, cuerpo con ejemplo, enlace al esquema
  `cuenta-creada-v1.schema.json`, tabla de campos, propiedades AMQP, garantías y privacidad (sección 5 de la spec, completa y
  autosuficiente) y «Cómo se versiona»: un campo nuevo opcional actualiza el esquema de la misma versión; quitar o cambiar un campo crea
  `cuenta.creada` versión 2 en paralelo.
- **Crear** `docs/adr/0003-eventos-con-outbox.md`: contexto, decisión (outbox en la misma transacción + intento inmediato con espera
  máxima de `500ms` + Job cada 5 min que reintenta y emite la carga inicial; carga borrada al publicar), por qué (IOP-04, FIA-01, FIA-02,
  FIA-05 y DES-02 del anexo de atributos de calidad; Ley 1581), alternativas descartadas (espera de 2 s, solo Job, solo inmediato,
  `@Scheduled`, Job aparte para la carga inicial, conservar la carga, sobre JSON con metadatos), consecuencias, **procedimiento de
  republicación** (REQ-EV-27, con la sentencia `DELETE` exacta y qué recibe Perfil), conexión AMQPS en staging y la decisión humana
  (Paula, 9-oct-2026).
- **Modificar** `CLAUDE.md`: §1 (publica `cuenta.creada`), §2 (`domain/event`, `messaging/publisher`, `messaging/payload` ya usados),
  §5 (tabla `evento_saliente` con sus columnas y reglas, y `V5` en la lista de migraciones), §8 («Tarea de eventos de cuenta» con el
  comando exacto y el broker de compose) y §10 (pendientes: broker de staging y Job, de DevOps).
- **Modificar** `docs/errores.md`, sección «Respuestas publicadas que difieren del estándar» (no existe una sección «Pendiente con
  destino»): en la fila «500 `INTERNAL_ERROR` en el registro cuando la base de datos no responde…» agrega «o al guardar la cuenta y su
  evento» (mismo destino, CM-290). No hay código nuevo, así que `ErrorCodeDocumentationTest` no cambia. El archivo no cita ningún libro del backlog.
- **Verificar:** `python C:\Users\paanm\Documents\cameia\.claude\skills\backend-estandar\verificar-documentos.py` si aplica al repo
  (léelo antes: si no aplica, no lo corras y dilo).

### T-C3.6 · Postman y Newman

- **Modificar** `postman/local.postman_environment.json`: `rabbitManagementUrl` = `http://localhost:15673`, `rabbitUser` = `cameia`,
  `rabbitPassword` = vacío con descripción «la del .env local; no se versiona» (Newman la recibe con `--env-var`).
- **Modificar** `postman/cameia-cuentas.postman_collection.json`: carpeta nueva «Eventos de cuenta (local)» con autenticación básica
  `{{rabbitUser}}`/`{{rabbitPassword}}` en las peticiones al broker:

  | # | Petición | Pruebas (`pm.test`) |
  |---|---|---|
  | E-01 | `PUT {{rabbitManagementUrl}}/api/queues/%2F/qa.cuenta-creada` `{"durable":false,"auto_delete":false}` y `POST .../api/bindings/%2F/e/cuentas.events/q/qa.cuenta-creada` `{"routing_key":"cuenta.creada"}` (dos peticiones, E-01a y E-01b) | 201 o 204 |
  | E-02 | `POST {{baseUrl}}/api/v1/users` con `X-Request-Id: pm-{{$timestamp}}` y correo `evento+{{$timestamp}}@ejemplo.test`, fecha `15/03/2008` | 201; guarda `firebaseUid` |
  | E-03 | `POST {{rabbitManagementUrl}}/api/queues/%2F/qa.cuenta-creada/get` `{"count":1,"ackmode":"ack_requeue_false","encoding":"auto"}` | un mensaje; `JSON.parse(payload)` tiene exactamente las claves `usuarioId`, `email`, `fechaNacimiento`, `creadaEn`; `usuarioId` = `firebaseUid`; `fechaNacimiento` = `2008-03-15`; `creadaEn` con el patrón de milisegundos; `properties.type` = `cuenta.creada`; `properties.app_id` = `cameia-cuentas`; `properties.correlation_id` = el `X-Request-Id` enviado; `properties.headers["x-event-version"]` = 1; `properties.content_type` = `application/json` |
  | E-04 | El mismo registro de E-02 | 200 |
  | E-05 | El `get` de E-03 otra vez | arreglo vacío (no hubo segundo evento) |
  | E-06 | `DELETE {{rabbitManagementUrl}}/api/queues/%2F/qa.cuenta-creada` | 204 |

- **Ejecutar** contra el jar con base y broker de compose:
  `npx newman run postman\cameia-cuentas.postman_collection.json -e postman\local.postman_environment.json --env-var rabbitPassword=<la del .env> --reporters cli,junit`
  y pega la salida (todas las peticiones de la colección, no solo la carpeta nueva).
- **Modificar** `postman/README.md`: tabla de archivos y la carpeta nueva, con el requisito «broker de compose levantado».

### T-C3.7 · Cierre del bloque C3 y de la mitad Cuentas

- `.\mvnw.cmd clean verify`, cobertura por clase (C1 a C3), Newman completo, `git diff --stat` por bloque. Reporte con: pruebas,
  cobertura con cada línea sin cubrir y su razón, salida de Newman, prueba manual de la tarea con su código de salida.

## 7. Prueba manual de punta a punta (después de Cuentas C2 y Perfil P2)

1. Levantar el broker de Cuentas (`docker compose up -d rabbitmq` en `cameia-cuentas`).
2. Arrancar Perfil apuntando a ese broker: `SPRING_RABBITMQ_HOST=localhost`, `SPRING_RABBITMQ_PORT=5673`, usuario y contraseña del
   `.env` de Cuentas.
3. Registrar en Cuentas con fecha `15/03/2008`.
4. En la base de Perfil: `SELECT firebase_uid, fecha_nacimiento FROM fecha_nacimiento_usuario WHERE firebase_uid = '<uid>'` → una fila
   con `2008-03-15`. Pegar la salida en los dos PR.
