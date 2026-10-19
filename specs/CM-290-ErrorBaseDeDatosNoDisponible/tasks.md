# Tarjetas · CM-290 503 `DATABASE_UNAVAILABLE` en Cuentas

**Reglas para todas las tarjetas:** identificadores y nombres de prueba nuevos en inglés (`method_shouldX_whenY`) con `@DisplayName` en español; Javadoc en todo lo nuevo; sin `CM-290` en comentarios ni rutas a otros archivos; sin dependencias nuevas; sin tocar migraciones, `.github/`, `pom.xml`, `docker-compose.yml` ni `.env.example`; nunca devolver al cliente el mensaje de una excepción de la librería. Escapes `\uXXXX` en Java: verificar con `git diff` que no se convirtieron en el carácter real. Docker Desktop encendido. Si algo no coincide con la tarjeta, detenerse y reportar a Paula con la salida real. Estado de la spec: **LISTA PARA EJECUTAR, aprobada por Paula el 19-oct-2026** (las tarjetas usan las opciones recomendadas P-1 a P-5).

---

## T0 · Preparación

1. `git fetch`; rama `CM-290-error-503-base-de-datos-no-disponible` desde `origin/develop`; anotar el SHA (la spec se escribió sobre `8447df4`).
2. Línea base: `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS` con las pruebas de Testcontainers ejecutadas (no omitidas). Si falla, detenerse.
3. `ls docs/adr`: la spec dice 0005; si ya existe, usar el siguiente y avisar a Paula.

---

## T1 · Espera de 5 s por una conexión (REQ-05)

**Reproducción primero (falla antes del arreglo):** crear `src/test/java/tech/cameia/cuentas/infrastructure/config/DatabasePoolConfigurationTest.java`:

```java
/** Comprueba la configuración efectiva del pool de conexiones. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DatabasePoolConfigurationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("El pool espera como máximo 5 segundos por una conexión")
    void connectionTimeout_shouldBeFiveSeconds() {
        assertThat(((HikariDataSource) dataSource).getConnectionTimeout()).isEqualTo(5000L);
    }
}
```

Antes del arreglo falla con `expected: 5000L but was: 30000L`: pegar esa salida en el resultado de la tarjeta.

**Arreglo:** en `src/main/resources/application.properties`, a continuación de `spring.datasource.driver-class-name`:

```properties
# Espera máxima por una conexión del pool. Con el valor por defecto (30 s), igual al tiempo del
# Gateway, una base caída hacía esperar 30 s y el cliente nunca veía el 503.
spring.datasource.hikari.connection-timeout=5000
```

**Trampas:** si el valor sigue en 30000, comprobar que no haya un `application-*.properties` o variable `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` que lo pise; si la prueba de otro archivo fija `connection-timeout`, su propiedad de prueba gana, es correcto.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest=DatabasePoolConfigurationTest`.

**Commit:** `CM-290 | feat(cuentas): esperar como máximo 5 s por una conexión de la base de datos`.

---

## T2 · Clasificador de indisponibilidad (REQ-01, REQ-02, REQ-03)

**Código nuevo** `src/main/java/tech/cameia/cuentas/presentation/advice/DatabaseUnavailability.java` (paquete-privado):

```java
/**
 * Reconoce que un fallo se debe a que la base de datos no está disponible: no responde, la
 * conexión se cortó, el pool de conexiones se agotó o una consulta pasó su tiempo máximo.
 *
 * <p>Recorre la excepción, sus causas y sus excepciones suprimidas, porque el corte de una
 * conexión puede llegar como una excepción de transacción sin código de estado y con la causa
 * verdadera solo entre las suprimidas. No reconoce los fallos del dato o de la consulta
 * (violaciones de restricción, SQL inválido, espera de bloqueo agotada).</p>
 */
final class DatabaseUnavailability {

    /** Máximo de excepciones que se visitan: una cadena con ciclos no debe dejar el hilo en un bucle. */
    private static final int MAX_NODES = 64;

    /** Estados de apagado o arranque del servidor y de demasiadas conexiones. */
    private static final Set<String> OUTAGE_STATES = Set.of("57P01", "57P02", "57P03", "53300");

    private DatabaseUnavailability() {
    }

    /** @return {@code true} si la excepción o alguna causa o suprimida es una indisponibilidad de la base */
    static boolean matches(Throwable error) { /* recorrido en anchura, ver abajo */ }

    /** @return el primer SQLState del árbol, o {@code "desconocido"} si ninguna excepción lo trae */
    static String sqlState(Throwable error) { /* mismo recorrido */ }

    private static boolean isOutage(Throwable error) {
        if (error instanceof org.hibernate.TransactionException) {
            return !hasNonConnectionSqlState(error); // un SQLState de dato (23505, 42601...) no es una caída
        }
        if (error instanceof DataAccessResourceFailureException
                || error instanceof TransientDataAccessResourceException
                || error instanceof QueryTimeoutException
                || error instanceof JDBCConnectionException
                || error instanceof SQLTransientConnectionException
                || error instanceof SQLNonTransientConnectionException
                || error instanceof SQLTimeoutException) {
            return true;
        }
        return error instanceof SQLException sql
                && sql.getSQLState() != null
                && (sql.getSQLState().startsWith("08") || OUTAGE_STATES.contains(sql.getSQLState()));
    }
}
```

Esqueleto del recorrido (extraer a un método privado `Iterator`-free que ambos métodos usen; ≤ 20 líneas): cola `ArrayDeque<Throwable>` con la excepción de entrada; conjunto `Collections.newSetFromMap(new IdentityHashMap<>())` de visitados; mientras la cola no esté vacía y los visitados sean menos de `MAX_NODES`: sacar, ignorar si ya se visitó, aplicar el predicado, y encolar `getCause()` (si no es nulo) y todas las de `getSuppressed()`. Un `null` de entrada devuelve falso y `"desconocido"`.

**Importaciones:** `QueryTimeoutException` y las dos de acceso a recursos son de `org.springframework.dao`; `JDBCConnectionException` de `org.hibernate.exception`. `presentation` ya importa Spring; no importar clases de PostgreSQL (`org.postgresql`): el SQLState se lee de `java.sql.SQLException`.

**Pruebas** `src/test/java/tech/cameia/cuentas/presentation/advice/DatabaseUnavailabilityTest.java` (sin Spring): una por fila de la spec §5 («Excepción»), con las excepciones construidas a mano (`new PSQLException(...)` está en el classpath de pruebas; si no se quiere, `new SQLException("x", "57P01")`):

| Prueba | Entrada | Resultado |
|---|---|---|
| `matches_shouldBeTrue_whenPoolIsExhausted` | `CannotCreateTransactionException("x", new JDBCConnectionException("x", new SQLException("x")))` encadenada a `SQLTransientConnectionException("pool")` | `true` |
| `matches_shouldBeTrue_whenSqlStateIsAdminShutdown` | `DataAccessResourceFailureException("x", new SQLException("FATAL", "57P01"))` con `addSuppressed(new SQLException("Connection is closed"))` | `true` |
| `matches_shouldBeTrue_whenRollbackFailsOnClosedConnection` | `JpaSystemException(new org.hibernate.TransactionException("Unable to rollback", new SQLException("Connection is closed")))` | `true` |
| `matches_shouldBeTrue_whenOnlySuppressedIsConnectionError` | `RuntimeException("x")` con `addSuppressed(new SQLException("x", "08006"))` | `true` |
| `matches_shouldBeTrue_whenSqlStateIsConnectionClass` (`@ParameterizedTest`) | `"08001"`, `"08006"`, `"57P01"`, `"57P02"`, `"57P03"`, `"53300"` | `true` |
| `matches_shouldBeTrue_whenQueryTimesOut` | `QueryTimeoutException("x", new SQLException("canceling", "57014"))` | `true` |
| `matches_shouldBeFalse_whenNotAvailabilityProblem` (`@ParameterizedTest`) | `DataIntegrityViolationException` con `SQLException("x","23502")`; `BadSqlGrammarException` con `"42601"`; `CannotAcquireLockException` con `"55P03"`; `new IllegalStateException("x")`; `new RuntimeException()`; `new SQLException("x","22012")` | `false` |
| `matches_shouldBeFalse_whenNull` | `null` | `false` |
| `matches_shouldTerminate_whenCauseCycle` | `a` sin causa, `b` con causa `a`, y luego `a.initCause(b)` (ver trampa) | termina; `false` |
| `matches_shouldStopAtLimit_whenChainIsTooDeep` | 100 `RuntimeException` encadenadas con `new SQLException("x","08001")` en el fondo | `false` |
| `matches_shouldFind_whenOutageIsAtNode64` | 63 `RuntimeException` encadenadas y `new SQLException("x","08001")` como nodo 64 | `true` |
| `sqlState_shouldReturnFirstState_whenPresent` / `sqlState_shouldReturnUnknown_whenAbsent` | `SQLException("x","57P01")` / `RuntimeException()` | `"57P01"` / `"desconocido"` |

**Trampa:** `Throwable.initCause` lanza si la causa ya se fijó en el constructor y `addSuppressed(this)` lanza `IllegalArgumentException`; para el ciclo, `RuntimeException a = new RuntimeException("a"); RuntimeException b = new RuntimeException("b", a); a.initCause(b);` funciona porque `a` se creó sin causa.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest=DatabaseUnavailabilityTest`.

**Commit:** `CM-290 | feat(cuentas): reconocer la indisponibilidad de la base de datos en una cadena de excepciones`.

---

## T3 · Código, rama del manejador y su prueba (REQ-01, REQ-04, REQ-06, REQ-09)

1. `domain/exception/ErrorCode.java`: después de `DEPENDENCY_UNAVAILABLE` agregar

```java
    /** La base de datos no está disponible; la persona puede reintentar. */
    DATABASE_UNAVAILABLE,
```

2. `presentation/advice/BusinessExceptionHandler.java`, `falloInterno`: entre el `if` del formulario y la construcción del 500, agregar

```java
        if (DatabaseUnavailability.matches(error)) {
            return baseNoDisponible(error);
        }
```

y, junto a `dependenciaNoDisponible`, el método privado:

```java
    /**
     * La base de datos no está disponible: es un fallo del servicio y pasajero, así que responde
     * {@code 503} y se registra en {@code ERROR} con la traza, el SQLState y la clase más específica.
     * El mensaje de la excepción (puede citar el servidor o el usuario de la base) no llega al cliente.
     */
    private ProblemDetail baseNoDisponible(Exception error) {
        ProblemDetail problema = problema(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible", DETALLE_INTERNO,
                ErrorCode.DATABASE_UNAVAILABLE);
        if (primerRegistro()) {
            logger.error("Base de datos no disponible [code={}, requestId={}, sqlState={}, causa={}]",
                    ErrorCode.DATABASE_UNAVAILABLE, problema.getProperties().get("requestId"),
                    DatabaseUnavailability.sqlState(error), claseMasEspecifica(error), error);
        }
        return problema;
    }
```

Actualizar el Javadoc de `falloInterno` para decir que cubre también la indisponibilidad de la base (`503`).

3. **Pruebas** en `BusinessExceptionHandlerTest` (misma forma que `illegalState_shouldReturn500_whenNotInvalidParameter`: `manejador.falloInterno(error, request)`):

| Prueba | Entrada | Resultado |
|---|---|---|
| `databaseUnavailable_shouldReturn503_whenAvailabilityProblem` | `DataAccessResourceFailureException("host=10.0.0.5 password=x")` | estado 503; `code` `DATABASE_UNAVAILABLE`; `title` «Servicio no disponible»; `detail` «Ocurrió un error. Inténtalo de nuevo.»; `requestId` presente; `ProblemDetail` sin `10.0.0.5` ni `password` |
| `databaseUnavailable_shouldLogOnce_whenAvailabilityProblem` | la misma, con `OutputCaptureExtension` (ya activa en la clase) | una línea `ERROR` con `DATABASE_UNAVAILABLE`, `sqlState=` y el `requestId` de la respuesta |
| `databaseUnavailable_shouldKeepOtherCodes_whenNotAvailabilityProblem` | `DataIntegrityViolationException` por `integridadDeDatos` y `IllegalStateException` por `falloInterno` | 500 `INTERNAL_ERROR` en ambas (control: ya pasan) |
| `databaseUnavailable_shouldKeepDependencyCode_whenFirebaseFails` | `dependenciaNoDisponible(new DependencyUnavailableException(new RuntimeException()))` | 503 `DEPENDENCY_UNAVAILABLE` |

**Trampa:** `ErrorCodeDocumentationTest` fallará hasta que `docs/errores.md` liste el código nuevo (se hace en T6): ejecutar solo las pruebas de la tarjeta aquí, y la suite completa después de T6.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest=BusinessExceptionHandlerTest`.

**Commit:** `CM-290 | feat(cuentas): responder 503 DATABASE_UNAVAILABLE cuando la base de datos no está disponible`.

---

## T4 · Pruebas de punta a punta con PostgreSQL real (REQ-01, REQ-08, REQ-09, casos borde de §7)

Crear `src/test/java/tech/cameia/cuentas/presentation/DatabaseUnavailableEndToEndTest.java` con la forma de `ProtocolErrorsEndToEndTest` (`@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.connection-timeout=1500"})`, `@Testcontainers(disabledWithoutDocker = true)`, `@ServiceConnection`, `RawHttpClient`, `OutputCaptureExtension`). Cada caso lee `code` y `requestId` del cuerpo con `ObjectMapper`.

| Prueba | Situación | Esperado |
|---|---|---|
| `currentAccount_shouldReturn503_whenPoolIsExhausted` | Tomar las 2 conexiones del `DataSource` y pedir `GET /api/v1/users/me` con `X-User-Id: uid-cm290-a`; soltar al terminar | 503 `DATABASE_UNAVAILABLE`, `requestId`, `Content-Type` con `charset=UTF-8`, en menos de 3 s |
| `currentAccount_shouldReturn503_whenServerTerminatesConnections` | Con una conexión admin aparte: `SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid()` y luego la petición | 503 |
| `currentAccount_shouldRecover_whenDatabaseComesBack` | Pausar el contenedor (`postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec()`), pedir `GET /api/v1/users/me`, reanudarlo (`unpauseContainerCmd`) y pedir otra vez | 503 con el contenedor en pausa y 404 `ACCOUNT_NOT_FOUND` al reanudarlo (el puerto no cambia, a diferencia de `stop`/`start`) |
| `allDatabaseRoutes_shouldReturn503_whenDatabaseIsDown` (`@ParameterizedTest`) | Con el contenedor en pausa: `POST /api/v1/users` con cuerpo válido (el de las pruebas de registro), `GET /users/me`, `POST /users/me/verification`, estas dos últimas con `X-User-Id: uid-cm290-b` | 503 `DATABASE_UNAVAILABLE` en las tres |
| `health_shouldStayUp_whenDatabaseIsDown` | `GET /api/v1/users/health` con el contenedor en pausa | 200 |
| `currentAccount_shouldReturnSameAnswer_whenRepeated20Times` | 20 veces el primer caso | 20 × 503 con el mismo `code`; `SELECT count(*) FROM microcuentas.cuenta` igual antes y después |
| `currentAccount_shouldNotFail_whenManyRequestsRunInParallel` | 30 hilos con el pool agotado | 30 × 503; ninguna dura más de 3 s |
| `register_shouldRollBack_whenConnectionsAreTerminatedBeforeCommit` | **Solo si Firebase se sustituye por el doble de pruebas existente**: registro mientras se corta la base | 503 y, al volver, ni cuenta ni evento nuevos (`cuenta` y `evento_saliente` con el mismo conteo). Si el doble de Firebase de las pruebas actuales no lo permite, detenerse y reportar a Paula en lugar de inventar uno |
| `currentAccount_shouldNotEchoDatabaseMessage` | Cuerpo y registro de cualquiera de las anteriores | El cuerpo no contiene `HikariPool`, `jdbc`, `postgres` ni `org.` |

**Trampas:** Hikari no revalida las conexiones usadas en los últimos 500 ms (`aliveBypassWindowMs`): una conexión devuelta al pool justo antes de pausar la base se entrega sin comprobar y la primera consulta se cuelga en lugar de fallar rápido. Tras `pauseContainerCmd`, esperar unos 800 ms antes de la primera petición (en `currentAccount_shouldRecover_whenDatabaseComesBack` y en la prueba parametrizada de rutas). `docker pause` mantiene el puerto y deja las conexiones colgadas hasta `connection-timeout`: es la forma más estable de simular «base que no responde» sin cambiar el puerto del contenedor. Reiniciar el contenedor en la misma clase deja el pool de otras pruebas roto: no compartir contexto (`@DirtiesContext(classMode = AFTER_CLASS)` si hace falta). Cada prueba debe devolver el contenedor a su estado normal en un `@AfterEach`. Si la prueba con `pause` no es estable en esta máquina, anotar la salida real, usar `pg_terminate_backend` más pool agotado (que sí son deterministas) y reportar la limitación.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest=DatabaseUnavailableEndToEndTest`.

**Commit:** `CM-290 | test(cuentas): comprobar con PostgreSQL real que cada forma de indisponibilidad responde 503`.

---

## T5 · OpenAPI (REQ-07, IOP-01)

En los tres controladores que usan la base (`UserRegistrationController`, `CurrentAccountController`, `AccountActivationController`; `SessionRevocationController` no cambia porque esa ruta no usa la base) documentar el 503 de la base:

- La operación de registro, que ya declara `@ApiResponse(responseCode = "503")`, pasa a describir **dos** códigos y lleva **dos** ejemplos: `DEPENDENCY_UNAVAILABLE` (Firebase) y `DATABASE_UNAVAILABLE` (base de datos), con `@ExampleObject` cada uno y el mismo cuerpo de la constante `EJEMPLO_503`, cambiando solo el `code`. El cierre de sesión en todos los dispositivos conserva solo su 503 de Firebase.
- `CurrentAccountController` y `AccountActivationController` agregan `@ApiResponse(responseCode = "503", description = "DATABASE_UNAVAILABLE: la base de datos no está disponible; la persona puede reintentar", content = ... problem+json con ejemplo)`, con la misma forma que las otras respuestas de error de cada controlador (leerlas antes de escribir: usan constantes `EJEMPLO_*`).
- `HealthController` no cambia.

**Prueba** `src/test/java/tech/cameia/cuentas/presentation/OpenApiDatabaseUnavailableTest.java`, con la forma de `OpenApiProtocolErrorsTest` (`/v3/api-docs` con `springdoc.api-docs.enabled=true`):

| Prueba | Esperado |
|---|---|
| `apiDocs_shouldDocument503_whenRequested` (`@CsvSource`: `/api/v1/users,post`, `/api/v1/users/me,get`, `/api/v1/users/me/verification,post`) | `responses['503'].description` contiene `DATABASE_UNAVAILABLE`; los ejemplos de `application/problem+json` contienen `DATABASE_UNAVAILABLE` |
| `apiDocs_shouldKeepFirebase503_whenRequested` (registro) | además contienen `DEPENDENCY_UNAVAILABLE` |
| `apiDocs_shouldNotDocument503_whenHealth` | `/api/v1/users/health` no declara `503` |

**Comando:** `./mvnw.cmd -B -ntp test -Dtest=OpenApiDatabaseUnavailableTest,OpenApiProtocolErrorsTest`.

**Commit:** `CM-290 | docs(cuentas): documentar en OpenAPI el 503 de la base de datos`.

---

## T6 · Postman, ADR y documentos (REQ-07)

1. **`docs/errores.md`:** agregar la fila `DATABASE_UNAVAILABLE` | 503 | «Ocurrió un error. Inténtalo de nuevo.» | las tres rutas que usan la base | `DatabaseUnavailability` en la tabla «Códigos que el servicio emite», junto a `DEPENDENCY_UNAVAILABLE`; **retirar** de «Respuestas publicadas que difieren del estándar» la fila del 500 por base que no responde (la resuelve esta tarea) y, en su lugar, agregar a la sección de limitaciones: sin tiempo máximo de consulta (una consulta que no termina no produce error; destino: tarea de medición de consultas), y los trabajos programados no pasan por el manejador. Sección «Cómo se agrega un código»: seguirla.
2. **ADR** `docs/adr/0005-base-de-datos-no-disponible.md`, con la misma plantilla de `0004-errores-de-protocolo-previsibles.md` (leerla): Contexto (evidencia E-1 a E-6), Decisión (clasificador, lista de REQ-02, 5 s, mismo mensaje, sin `Retry-After`), Alternativas descartadas (spec §15), Consecuencias (cambio aditivo avisado a Frontend; sin tiempo de consulta) y Estado: **Aceptada por Paula, <fecha>** solo cuando ella lo diga (hasta entonces, `Propuesta`).
3. **`CLAUDE.md`, sección 4:** agregar al final de la viñeta de peticiones mal formadas: «Una base de datos no disponible (caída, conexión cortada, pool agotado o consulta que la base corta por su tiempo máximo) responde 503 `DATABASE_UNAVAILABLE` ([ADR 0005](docs/adr/0005-base-de-datos-no-disponible.md)).» Una sola frase; no reescribir más.
4. **Postman:** colección aparte `postman/cameia-cuentas-base-de-datos-no-disponible.postman_collection.json` con la carpeta «Base de datos no disponible (manual)», D-01 y D-02 (spec §13); lleva el script de colección existente (comprueba `Content-Type`, `requestId`); D-01 comprueba además `pm.response.responseTime < 8000`. Actualizar la tabla de `postman/README.md`.

**Verificación:** `./mvnw.cmd -B -ntp test -Dtest=ErrorCodeDocumentationTest` (ahora debe pasar) y un `node -e "JSON.parse(require('fs').readFileSync('postman/cameia-cuentas.postman_collection.json','utf8'))"` para validar el JSON de la colección.

**Commit:** `CM-290 | docs(cuentas): catálogo de errores, ADR y colección de Postman del 503 de la base de datos`.

---

## T7 · Verificación final (una sola vez, sobre la punta)

1. `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS`; suite completa, `LayeredArchitectureTest` y `UntypedExceptionClassificationTest` en verde.
2. Cobertura: `target/site/jacoco/jacoco.csv`; ≥ 90 % de líneas y ramas en `DatabaseUnavailability` y en `BusinessExceptionHandler`; reportar el número real y cada línea o rama sin cubrir con su razón.
3. Newman manual: levantar `docker compose up -d` (base y Cuentas), `docker compose stop db`, correr `newman run postman/cameia-cuentas-base-de-datos-no-disponible.postman_collection.json -e postman/local.postman_environment.json` → 0 fallos; `docker compose start db` y repetir una petición de `GET /users/me` → vuelve a 404 `ACCOUNT_NOT_FOUND`. Además la corrida automática de siempre (`--folder Registro`, etc.) sin cambios.
4. Tiempo real: con la base detenida y la configuración empaquetada, medir el tiempo de la respuesta (esperado ≈ 5 s, hoy 30 s) y pegarlo.
5. Revisión según `backend-estandar` §6 (`/simplify`, `/code-review high`, silent-failure-hunter) y entrega de la guía de revisión a Paula en el chat.

---

## Resultados de ejecución (19-oct-2026)

| Tarjeta | Resultado | Evidencia |
|---|---|---|
| T0 | Rama desde `8447df4` (sin cambios en `origin/develop`). `docs/adr` llega hasta `0004`, así que el ADR nuevo es `0005`. Línea base: `BUILD SUCCESS`, 1232 pruebas, 0 omitidas. Tarjeta de Jira CM-290 en «En curso» | `ejecucion/evidencia/CM-290/baseline-cuentas.log` |
| T1 | Antes del arreglo: `expected: 5000L but was: 30000L`. Después: 1 prueba en verde. Commit `56a3494` | `t1-antes.log`, `t1-despues.log` |
| T2 | `DatabaseUnavailabilityTest`: 23 pruebas en verde. Commit `91907ca` | `t2.log` |
| T3 | `BusinessExceptionHandlerTest`: 55 pruebas en verde (4 nuevas). Commit `dd5aa20` | `t3.log` |
| T4 | Primera corrida: 10 de 11 en verde; `POST /auth/logout-all` respondió 204 porque no usa la base. Decisiones de Paula (19-oct): la ruta se saca de las rutas con base (spec, plan y tarjetas corregidas) y el registro con corte se prueba con un doble de prueba propio de la clase que corta la base después de crear la credencial. Corrida final: 12 pruebas en verde. Commit `1217f04` | `t4-1.log`, `t4-2.log` |
| T5 | `OpenApiDatabaseUnavailableTest` (6) y `OpenApiProtocolErrorsTest` (16) en verde. Commit `f304f9a` | `t5.log` |
| T6 | `ErrorCodeDocumentationTest` en verde (39); JSON de la colección válido. ADR `0005` en estado `Propuesta` | `t6.log` |
