# CM-290 · 503 `DATABASE_UNAVAILABLE` cuando la base de datos de Cuentas no está disponible

| Dato | Valor |
|---|---|
| Tarea Jira | CM-290 (tarea, Sprint 3), responsable Paula Andrea Muñoz Delgado |
| Repositorio | `cameia-cuentas`, base `origin/develop` = `8447df4` (se vuelve a anotar el SHA al preparar la rama de código) |
| Depende de | Nada sin fusionar |
| Estado | **LISTA PARA EJECUTAR.** Aprobada por Paula Andrea Muñoz Delgado el 19-oct-2026, con las decisiones P-1 a P-5 en su opción recomendada (A; espera de 5 s confirmada tras medir 0,5 a 0,66 s en la primera conexión del entorno) |
| Estimación | 3 a 4 h de Backend (clasificador, manejador, configuración del pool, pruebas con base real, OpenAPI, Postman y documentos). Se informa a Vela por `comunicaciones/` |
| Servicio hermano | `cameia-perfil` tiene su propia spec de la misma tarea; cada una se entiende sola |

## 1. Objetivo

Cuando Cuentas no puede hablar con su base de datos PostgreSQL, hoy responde `500 INTERNAL_ERROR`, el mismo error genérico que un defecto de programación, y tarda hasta 30 segundos en hacerlo. La persona (y Frontend) no pueden distinguir «reintenta en unos segundos» de «algo se rompió».

Esta tarea hace que, en cualquier ruta de Cuentas que use la base:

1. Una base **no disponible** (apagada, conexión cortada, pool de conexiones agotado, consulta que la base corta por su tiempo máximo) responda `503` con el código estable `DATABASE_UNAVAILABLE`, la forma común de error (`code`, `requestId`, `application/problem+json;charset=UTF-8`) y el mensaje genérico que ya usa `DEPENDENCY_UNAVAILABLE`.
2. La espera máxima por una conexión sea corta (5 s), para que el 503 llegue antes de que el Gateway cierre la petición por tiempo (30 s).
3. Todo fallo de la base que **no** sea indisponibilidad (violación de restricción, SQL inválido, espera de bloqueo agotada) conserve su respuesta de hoy.

## 2. Evidencia (laboratorio con PostgreSQL 16 real, 19-oct-2026)

Laboratorio desechable (no va a ningún PR) sobre `origin/develop` `8447df4`, con `GET /api/v1/users/me` y repositorios reales (Testcontainers). Registros: `analisis/evidencia/CM-290/` de la carpeta raíz de trabajo.

| # | Situación | Resultado hoy | Excepción que llega al manejador |
|---|---|---|---|
| E-1 | Pool agotado (2 de 2 conexiones tomadas, espera 1,5 s) | 500 `INTERNAL_ERROR` en 1586 ms | `CannotCreateTransactionException` ← `JDBCConnectionException` ← `SQLTransientConnectionException` («Connection is not available, request timed out») |
| E-2 | Consulta lenta por bloqueo de tabla (`query.timeout` 500 ms) | 500 en 1071 ms | `QueryTimeoutException` ← `PSQLException` SQLState `57014` |
| E-3 | Conexiones cortadas por el servidor justo antes de la petición (`pg_terminate_backend`) | 500 en 61 ms | `JpaSystemException` ← `TransactionException` («Unable to rollback») ← `SQLException` «Connection is closed», **sin SQLState** |
| E-4 | Corte dentro de una transacción, en la segunda consulta | 500 | igual que E-3 |
| E-5 | Corte justo antes del commit | 500 | `DataAccessResourceFailureException` ← `PSQLException` `57P01`; la conexión cerrada viaja en **`getSuppressed()`**, no en `getCause()` |
| E-6 | Base detenida, configuración por defecto del pool | 500 tras **30026 ms** y **30039 ms** | `CannotCreateTransactionException` ← `JDBCConnectionException` ← `SQLTransientConnectionException` («request timed out after 30011ms») |

Hallazgos:

- **H-1** La causa verdadera puede estar en `getSuppressed()` (E-5) o no tener SQLState (E-3). Un clasificador que solo mira `getCause()` y el SQLState no reconoce dos de los cinco cortes.
- **H-2** El `connection-timeout` por defecto de Hikari (30 s) es igual al `response-timeout` del Gateway (`GATEWAY_TIMEOUT_MS`, 30 s). Con la base caída el cliente recibe el 504 del Gateway o, si llega primero la respuesta de Cuentas, un 500 después de 30 s: el 503 casi nunca se vería.
- **H-3** Cuentas no configura ningún tiempo máximo de consulta (`jakarta.persistence.query.timeout`): una consulta que no termina no produce error. Está fuera de alcance (§16, D-6); E-2 solo prueba que, si se configurara, se clasifica bien.
- **H-4** Cuentas no usa `JdbcTemplate` en código de producción (verificado con `git grep`), así que `CannotGetJdbcConnectionException` no es alcanzable.

Controles negativos medidos (siguen sin ser indisponibilidad): división por cero `22012` y violación de no nulo `23502` (`DataIntegrityViolationException`), sintaxis SQL inválida `42601` (`BadSqlGrammarException`), espera de bloqueo agotada `55P03` (`CannotAcquireLockException`) e `IllegalStateException`.

## 3. Requisitos (EARS)

- **REQ-01** Cuando una excepción que llegue al manejador general de Cuentas, o alguna de sus causas o excepciones suprimidas (`getCause()` y `getSuppressed()`, máximo 64 nodos, sin repetir nodos), sea una indisponibilidad de la base de datos según REQ-02, el sistema debe responder `503` con `code` `DATABASE_UNAVAILABLE`, `title` «Servicio no disponible», `detail` «Ocurrió un error. Inténtalo de nuevo.», `application/problem+json;charset=UTF-8`, `requestId` y el encabezado `X-Request-Id`, igual que las demás respuestas de error.
- **REQ-02** Es indisponibilidad de la base cualquiera de estas excepciones en el árbol de REQ-01: `DataAccessResourceFailureException`, `TransientDataAccessResourceException`, `QueryTimeoutException` (de Spring), `org.hibernate.exception.JDBCConnectionException`, `org.hibernate.TransactionException`, `SQLTransientConnectionException`, `SQLNonTransientConnectionException`, `SQLTimeoutException`, o una `SQLException` cuyo SQLState empiece por `08` (error de conexión) o sea `57P01`, `57P02`, `57P03` (apagado o arranque del servidor) o `53300` (demasiadas conexiones). `org.hibernate.TransactionException` cubre E-3/E-4: es el error de «no pude abrir, confirmar o deshacer la transacción». Casi siempre es la conexión, pero no se asume: no cuenta si alguna de sus causas trae un SQLState que no es de conexión (por ejemplo `23505` al confirmar), porque entonces es un fallo del dato y conserva su respuesta.
- **REQ-03** No es indisponibilidad, y por tanto conserva su respuesta actual: `DataIntegrityViolationException` (23xxx, 22xxx), `BadSqlGrammarException` (42xxx), `CannotAcquireLockException` (55P03), `IllegalStateException`, cualquier `BusinessException` y cualquier otra excepción que REQ-02 no nombre.
- **REQ-04** El sistema debe registrar la respuesta de REQ-01 en `ERROR` una sola vez por petición, con `code`, `requestId`, `sqlState` (el primero que aparezca en el árbol, o `desconocido`), la clase más específica de la cadena de causas y la traza, como ya hace con `DEPENDENCY_UNAVAILABLE`: la traza de un fallo de conexión no lleva valores de columnas y es la única forma de diagnosticarlo. La respuesta al cliente nunca contiene el mensaje de la excepción de la librería (puede traer la dirección del servidor o el usuario de la base) ni datos de la cuenta. Para que sea una sola línea `ERROR`, se apaga en `application.properties` el registro propio de Spring `TransactionInterceptor` («Application exception overridden by rollback exception», que repite el SQL y el mensaje de PostgreSQL); quedan los avisos de Hikari sobre el pool (uno por conexión cortada, sin datos).
- **REQ-05** El sistema debe fijar `spring.datasource.hikari.connection-timeout=5000` (5 s) en `application.properties`. Se puede sobrescribir en el despliegue con la variable estándar `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` (sin variable nueva en `.env.example`).
- **REQ-06** Cuando una excepción ya mapeada por otro manejador (`DependencyUnavailableException` de Firebase, `DataIntegrityViolationException`, etc.) llegue al servicio, el sistema no debe cambiar su respuesta: `DATABASE_UNAVAILABLE` solo se decide en el manejador general (`Exception.class`) y nunca reemplaza un manejador más específico.
- **REQ-07** El sistema debe documentar `DATABASE_UNAVAILABLE` en `ErrorCode`, en `docs/errores.md` (tabla de códigos, la fila «Respuestas publicadas que difieren del estándar» queda resuelta y se retira), en OpenAPI de las tres rutas que usan la base (registro, consulta de la cuenta y verificación del correo) y en un ADR.
- **REQ-08** La respuesta no debe cambiar para las rutas que no usan la base: `GET /api/v1/users/health` no consulta la base y sigue respondiendo 200 aunque la base esté caída.
- **REQ-09** El 503 de la base y el 503 de Firebase (`DEPENDENCY_UNAVAILABLE`) deben ser distinguibles por `code` y compartir título, estado y mensaje. En el registro, que usa ambos, gana el que ocurra primero (la lectura de la cuenta precede a Firebase).

## 4. Contrato

Cambio aditivo para Frontend: un código nuevo, `DATABASE_UNAVAILABLE` (503), y casos que pasan de 500 a 503.

| Petición | Antes | Después |
|---|---|---|
| `POST /api/v1/users`, `GET /api/v1/users/me`, `POST /api/v1/users/me/verification` con la base caída o el pool agotado | 500 `INTERNAL_ERROR` tras hasta 30 s | 503 `DATABASE_UNAVAILABLE` tras 5 s como máximo |
| `POST /api/v1/auth/logout-all` con la base caída | 204 (no usa la base: solo llama a Firebase) | sin cambio; con Firebase caído sigue en 503 `DEPENDENCY_UNAVAILABLE` |
| Las mismas rutas con una violación de restricción de la base | 500 `INTERNAL_ERROR` | sin cambio |
| `GET /api/v1/users/health` con la base caída | 200 | sin cambio |

Frontend consume Cuentas a través del Gateway. Aviso a Frontend al abrir el PR: código nuevo `DATABASE_UNAVAILABLE` (503, «Ocurrió un error. Inténtalo de nuevo.»); la acción esperada es la misma que con `DEPENDENCY_UNAVAILABLE`: mostrar el mensaje y permitir reintentar.

## 5. Validación de entrada y casos de prueba con datos literales

La tarea no agrega campos de entrada. Casos de prueba:

| Entrada | Caso | Valor literal | Resultado | Prueba |
|---|---|---|---|---|
| Excepción | Cadena de pool agotado | `CannotCreateTransactionException` ← `JDBCConnectionException` ← `SQLTransientConnectionException` | `matches` verdadero | `DatabaseUnavailabilityTest.matches_shouldBeTrue_whenPoolIsExhausted` |
| | Corte con la causa en `getSuppressed()` | `DataAccessResourceFailureException` ← `PSQLException("57P01")` con suprimida `SQLException("Connection is closed")` | verdadero | `…matches_shouldBeTrue_whenSqlStateIsAdminShutdown` |
| | Corte sin SQLState | `JpaSystemException` ← `org.hibernate.TransactionException` ← `SQLException("Connection is closed")` | verdadero | `…matches_shouldBeTrue_whenRollbackFailsOnClosedConnection` |
| | Causa solo en suprimidas | excepción sin causa y con una suprimida `SQLException("x","08006")` | verdadero | `…matches_shouldBeTrue_whenOnlySuppressedIsConnectionError` |
| | SQLState de conexión | `SQLException("x","08001")`, `"08006"`, `"57P01"`, `"57P02"`, `"57P03"`, `"53300"` | verdadero (6 casos) | `…matches_shouldBeTrue_whenSqlStateIsConnectionClass` |
| | Consulta lenta | `QueryTimeoutException` ← `PSQLException("57014")` | verdadero | `…matches_shouldBeTrue_whenQueryTimesOut` |
| | Negativos | `DataIntegrityViolationException`(`23502`), `BadSqlGrammarException`(`42601`), `CannotAcquireLockException`(`55P03`), `IllegalStateException`, `RuntimeException` sin causa, `null` en la cadena, `TransactionException` con causa `23505` o `42601` | falso (8 casos) | `…matches_shouldBeFalse_whenNotAvailabilityProblem` |
| | Ciclo | A.cause = B, B.cause = A, y A se suprime a sí misma | termina; falso | `…matches_shouldTerminate_whenCauseCycle` |
| | Tope | cadena de 100 `RuntimeException` con un `SQLException("x","08001")` al fondo | falso (el nodo 65 no se visita; el tope acota el trabajo) | `…matches_shouldStopAtLimit_whenChainIsTooDeep` |
| Manejador | 503 con forma común | `new DataAccessResourceFailureException("host=10.0.0.5")` | 503, `code`, `requestId`, cuerpo sin `10.0.0.5`; un `ERROR` con `code` y `requestId` | `BusinessExceptionHandlerTest.databaseUnavailable_shouldReturn503_whenAvailabilityProblem` |
| | Prioridad del manejador específico | `DependencyUnavailableException` | 503 `DEPENDENCY_UNAVAILABLE` (sin cambio) | prueba existente de `dependenciaNoDisponible` |
| | Violación de restricción | `DataIntegrityViolationException` | 500 `INTERNAL_ERROR` (sin cambio) | prueba existente de `integridadDeDatos` |
| | Imprevisto | `IllegalStateException("x")` | 500 `INTERNAL_ERROR` (sin cambio) | `BusinessExceptionHandlerTest.illegalState_shouldReturn500_whenNotInvalidParameter` existente |
| Punta a punta | Pool agotado por HTTP | `maximum-pool-size=2`, `connection-timeout=1500`, dos conexiones tomadas, `GET /api/v1/users/me` con `X-User-Id: uid-cm290-a` | 503 `DATABASE_UNAVAILABLE` en menos de 3 s | `DatabaseUnavailableEndToEndTest.currentAccount_shouldReturn503_whenPoolIsExhausted` |
| | Corte de conexiones | `pg_terminate_backend` sobre todas las conexiones del pool y luego la misma petición | 503 | `…shouldReturn503_whenServerTerminatesConnections` |
| | Base detenida | contenedor detenido, `connection-timeout=1500` | 503 en menos de 3 s; vuelve a 404 `ACCOUNT_NOT_FOUND` al reiniciar el contenedor | `…shouldRecover_whenDatabaseComesBack` |
| | Tres rutas | `POST /api/v1/users` (cuerpo válido), `GET /users/me`, `POST /users/me/verification` con la base caída | 503 `DATABASE_UNAVAILABLE` en las tres | `…allDatabaseRoutes_shouldReturn503_whenDatabaseIsDown` (3 casos) |
| | Cierre de sesión | `POST /api/v1/auth/logout-all` con la base caída | 204 (no usa la base) | `…logoutAll_shouldStayUp_whenDatabaseIsDown` |
| | Health | `GET /api/v1/users/health` con la base caída | 200 | `…health_shouldStayUp_whenDatabaseIsDown` |
| | Configuración | valor efectivo de `spring.datasource.hikari.connection-timeout` | `5000` | `DatabasePoolConfigurationTest.connectionTimeout_shouldBeFiveSeconds` |

## 6. Base de datos

Sin migración ni cambio de esquema. El cambio es de configuración del pool de conexiones (REQ-05).

## 7. Casos borde (estándar §C)

| Grupo | Caso | Tratamiento |
|---|---|---|
| Nulos y vacíos | Excepción sin causa, sin suprimidas, mensaje nulo | `matches` falso sin `NullPointerException` (prueba de negativos) |
| Límites | Árbol de 64 nodos exactos; de 65 | El nodo 64 se visita y el 65 no (`…stopAtLimit`) |
| Unicode | Mensaje de la excepción con tildes o secuencias de control | Nunca se copia a la respuesta (REQ-04) |
| Fechas y zonas | — | No aplica: no se tratan fechas |
| Duplicados e idempotencia | 20 peticiones iguales con la base caída | 20 × 503 con el mismo `code`; ninguna fila nueva al volver la base. Prueba `…shouldReturnSameAnswer_whenRepeated20Times` |
| Concurrencia y doble envío | 30 peticiones en paralelo con el pool agotado | Todas 503, ninguna 500; ninguna espera más de `connection-timeout` + 1 s |
| Falla a medias | Corte antes del commit del registro (cuenta y evento de salida en la misma transacción) | La transacción se deshace: ni cuenta ni evento. Prueba: tras el 503 y la recuperación, `SELECT count(*)` de `cuenta` y `evento_saliente` igual que antes |
| Dependencia caída o lenta | Base caída; Firebase caído | 503 con códigos distintos (REQ-09); prueba con ambos manejadores |
| Recurso ajeno / estado inválido | — | No aplica: el 503 ocurre antes de cualquier decisión de negocio |
| Carga grande | — | Fuera de alcance (§16) |
| Entradas de ataque | Mensaje de la base con SQL o dirección | No se refleja en la respuesta (REQ-04); prueba con `PSQLException("host=10.0.0.5 password=x")` |
| Recuperación | La base vuelve | La primera petición posterior responde con normalidad (Hikari repone el pool); prueba `…shouldRecover_whenDatabaseComesBack` |

## 8. Seguridad (ASVS nivel 1 y OWASP API Top 10)

| Control | Qué hace | Prueba |
|---|---|---|
| ASVS 4.1.1 | El 503 lleva `application/problem+json;charset=UTF-8` | Pruebas de punta a punta de §5 |
| ASVS 15.3.1 / API3 | La respuesta es un DTO de error con campos fijos; no revela clases, SQL, host ni usuario de la base | `…databaseUnavailable_shouldReturn503_whenAvailabilityProblem` (cuerpo sin el host) |
| ASVS 8.2.2 | La identidad sigue viniendo de `X-User-Id`; el 503 no cambia esa comprobación | Pruebas de identidad existentes |
| API4 (consumo de recursos) | La espera por una conexión pasa de 30 s a 5 s: una base caída no retiene hilos de Tomcat 30 s por petición | `DatabasePoolConfigurationTest`; prueba de concurrencia de §7 |
| API8 (configuración) | El mensaje de la excepción no llega al cliente | Prueba de entradas de ataque de §7 |

La matriz ASVS 5.0.0 nivel 1 de `cameia-infra` no trae un requisito propio de «mensaje genérico ante un error inesperado»; se cubre con API8 y 15.3.1.

## 9. Atributos de calidad de CAMEIA

La tarea no pertenece a una épica con atributos declarados; se aplican los transversales (anexo `Anexo_Restricciones_Atributos_Calidad`):

| Atributo | Métrica | Límite | Cómo | Prueba |
|---|---|---|---|---|
| AC-0002 (disponibilidad) | DES-04 | 5xx < 1 % por causas previsibles | Una base caída es un 503 conocido y no un 500 imprevisto; la espera baja de 30 s a 5 s | Pruebas de punta a punta de §5 |
| AC-0003 (seguridad) | SEG-04 | Sin datos personales en logs; sin datos internos en respuestas | REQ-04 | Prueba de entradas de ataque de §7 |
| AC-0004 (fiabilidad) | FIA-01 | Ningún fallo deja estado a medias | La transacción se deshace en el corte | Falla a medias de §7 |
| AC-0004 (fiabilidad) | FIA-02 | La misma petición mala, la misma respuesta | 20 repeticiones iguales | `…shouldReturnSameAnswer_whenRepeated20Times` |
| AC-0006 (interoperabilidad) | INT | Error con `code`, mensaje claro y distinguible | REQ-01, REQ-09 | Pruebas de §5 |
| Transversal | MAN-01 | ≥ 90 % de líneas y ramas en lo nuevo o modificado | JaCoCo | `./mvnw.cmd clean verify` |
| Transversal | IOP-01 | OpenAPI completo | 503 con ejemplo en las tres rutas que usan la base | `OpenApiDatabaseUnavailableTest` |

## 10. Errores

| Código | HTTP | Mensaje | Caso |
|---|---|---|---|
| `DATABASE_UNAVAILABLE` | 503 | «Ocurrió un error. Inténtalo de nuevo.» | **Código nuevo.** La base no responde, la conexión se cortó, el pool se agotó o una consulta pasó su tiempo máximo. Vocabulario: sujeto `DATABASE` + causa `UNAVAILABLE` (la causa ya existe en el vocabulario cerrado) |
| `DEPENDENCY_UNAVAILABLE` | 503 | igual | Firebase (sin cambio) |
| `INTERNAL_ERROR` | 500 | «Ocurrió un error. Inténtalo de nuevo.» | Solo lo imprevisto, incluida la violación de restricción |

**Excepción de negocio.** La regla «todo error previsible tiene excepción de negocio» se cumple con una salvedad documentada: la indisponibilidad de la base no nace en el dominio ni en un puerto, sino en Hibernate, Spring y el controlador JDBC, y puede aparecer en cualquier transacción (abrir, consultar, confirmar, deshacer). Se reconoce en el manejador, igual que `BadParameterEncoding` reconoce el rechazo de Tomcat, y se registra en el ADR. La alternativa (envolver cada puerto de persistencia en una excepción de dominio) no cubre el inicio y el cierre de la transacción, donde ocurren E-1, E-3 y E-5 (D-1).

## 11. Pruebas, cobertura y comandos

| Capa | Pruebas |
|---|---|
| Presentación (unitarias) | `DatabaseUnavailabilityTest` (nueva), `BusinessExceptionHandlerTest` (se amplía) |
| Configuración | `DatabasePoolConfigurationTest` (nueva) |
| Punta a punta | `DatabaseUnavailableEndToEndTest` (nueva, `RANDOM_PORT`, Testcontainers, pool de 2 conexiones y `connection-timeout=1500` por propiedades de la prueba, `RawHttpClient` existente) |
| Contrato | `OpenApiDatabaseUnavailableTest` (nueva, forma de `OpenApiProtocolErrorsTest`) y `ErrorCodeDocumentationTest` (existente) |
| Arquitectura | `LayeredArchitectureTest` y `UntypedExceptionClassificationTest` (existentes, deben seguir verdes) |

Clases a medir: `DatabaseUnavailability` y los métodos nuevos de `BusinessExceptionHandler`. Comandos: Docker encendido; `./mvnw.cmd -B -ntp clean verify`; `target/site/jacoco/jacoco.csv`; Newman (§13). En Cuentas las pruebas de punta a punta se llaman `*Test` y las corre Surefire con Testcontainers.

## 12. Lo que la aplicación no puede cambiar (se documenta)

- Si Cloud Run o Cloud SQL cortan el tráfico antes de que Cuentas responda, o si el Gateway agota su propio tiempo, la respuesta no la da Cuentas (la da el Gateway o la infraestructura). Con `connection-timeout` de 5 s la respuesta de Cuentas llega antes que el límite del Gateway (30 s).
- Una consulta que no termina no produce error mientras no haya tiempo máximo de consulta (H-3, D-6).
- `POST /api/v1/auth/logout-all` no lee ni escribe la base (solo revoca en Firebase): con la base caída responde igual que siempre y no declara `DATABASE_UNAVAILABLE`.
- Los trabajos programados (relevo de eventos, purga) no pasan por el manejador HTTP: su comportamiento ante una base caída no cambia.

## 13. Postman y Newman

Colección aparte, `postman/cameia-cuentas-base-de-datos-no-disponible.postman_collection.json`, con la carpeta «Base de datos no disponible (manual)» y el mismo script de colección (`Content-Type`, `requestId`):

| # | Petición | Espera |
|---|---|---|
| D-01 | `GET {{baseUrl}}/api/v1/users/me` con `X-User-Id: uid-cm290-newman`, **con el servicio `db` de compose detenido** (`docker compose stop db`) | 503 `DATABASE_UNAVAILABLE`, `requestId`, `charset=UTF-8`, en menos de 8 s |
| D-02 | `GET {{baseUrl}}/api/v1/users/health` en las mismas condiciones | 200 |

Va en un archivo aparte porque exige detener la base: dentro de la colección principal fallaría en cualquier corrida con la base encendida. El recorrido automático lo cubre `DatabaseUnavailableEndToEndTest`. Actualizar la lista de carpetas de `postman/README.md`. Trampa: tras `docker compose stop db`, Hikari tarda hasta 5 s en rendirse; el script de la colección espera 8 s.

## 14. Línea base

Antes de editar: `git fetch`, rama `CM-290-error-503-base-de-datos-no-disponible` desde `origin/develop` y `./mvnw.cmd -B -ntp clean verify` en verde. Si falla, detenerse y reportar. Resultado de la línea base del 19-oct-2026: ver `analisis/revisiones/revision-spec_CM-290.md`.

## 15. Decisiones

| # | Decisión | Por qué | Descartado | Quién |
|---|---|---|---|---|
| D-1 | Un clasificador `DatabaseUnavailability` en `presentation.advice` y una rama en el manejador general | Cubre abrir, consultar, confirmar y deshacer la transacción con una sola pieza; sigue el precedente de `BadParameterEncoding`; mínimo código | Envolver cada puerto de persistencia en una excepción de dominio (no cubre el inicio ni el cierre de la transacción, donde ocurre la mayoría de los cortes); un aspecto AOP (más maquinaria que una rama) | Paula, 19-oct-2026 (opción recomendada) |
| D-2 | Cuentan como indisponibilidad las excepciones de REQ-02, incluidas `QueryTimeoutException` y `org.hibernate.TransactionException`; no cuentan 23xxx, 22xxx, 42xxx ni 55P03 | Lo medido en §2: cada una es un corte de conexión o un tiempo agotado; las demás son fallos de la consulta o del dato | Incluir 55P03 (Cuentas no bloquea filas con espera; si llegara sería un defecto, no una base caída) | Paula, 19-oct-2026 (opción recomendada) |
| D-3 | `connection-timeout` de Hikari en 5 s en `application.properties` de ambos servicios | H-2: con 30 s el 503 no llega antes del límite del Gateway; 5 s absorbe un arranque en frío de la conexión y deja margen | Dejar 30 s (el 503 sería invisible); 3 s (riesgo de falsos 503 en arranque en frío de Cloud Run); una variable nueva (DevOps, `.env.example`) | Paula, 19-oct-2026 (opción recomendada) |
| D-4 | Mismo título y mensaje que `DEPENDENCY_UNAVAILABLE` | El usuario no puede actuar distinto; el mensaje específico solo ayudaría a un atacante a conocer la infraestructura | Mensaje propio («No pudimos acceder a tus datos») | Paula, 19-oct-2026 (opción recomendada) |
| D-5 | Sin encabezado `Retry-After` | No se sabe cuándo vuelve la base; Frontend ya reintenta con `DEPENDENCY_UNAVAILABLE` | `Retry-After: 5` fijo | Paula, 19-oct-2026 (opción recomendada) |
| D-6 | No se configura tiempo máximo de consulta en esta tarea | Cambia el comportamiento de consultas lentas legítimas y pide medir; sin consultas largas hoy en Cuentas. Se registra como pendiente con destino en `docs/errores.md` | Fijar `jakarta.persistence.query.timeout` ahora | Paula, 19-oct-2026 (opción recomendada) |
| D-7 | Un solo código para pool agotado, base detenida, corte y consulta lenta | Para la persona y para Frontend la acción es la misma: reintentar. El registro distingue la causa (`sqlState`, clase) | Un código por causa | Backend; Paula confirma con D-1 |

## 16. Fuera de alcance

El tiempo máximo de consulta (D-6); el comportamiento de los trabajos programados y de los consumidores de RabbitMQ ante una base caída; el servicio de Entrevista (no tiene código; su 503 queda para la tarea que lo implemente); cualquier cambio en el Gateway; un reintento automático dentro de Cuentas; `Retry-After`.

## 17. Preguntas

Todas llevan recomendación y su efecto (la respuesta es una letra):

- **P-1 (D-1) Mecanismo.** A) Clasificador en el manejador general (recomendado; ~1 clase y una rama). B) Excepción de dominio en cada puerto (más clases y no cubre el inicio de la transacción).
- **P-2 (D-2) Qué cuenta.** A) La lista de REQ-02 (recomendado). B) La misma lista sin `QueryTimeoutException` (una consulta lenta seguiría en 500).
- **P-3 (D-3) Espera por una conexión.** A) 5 s (recomendado). B) 3 s. C) Dejar 30 s: el 503 casi nunca se vería.
- **P-4 (D-4) Mensaje.** A) Igual que `DEPENDENCY_UNAVAILABLE` (recomendado). B) Propio: Frontend y Paula escriben el texto.
- **P-5 (D-5, D-6) `Retry-After` y tiempo máximo de consulta.** A) Ninguno de los dos en esta tarea (recomendado). B) Agregar `Retry-After: 5`. C) Fijar el tiempo máximo de consulta (necesita medir antes).

Sin respuesta, se avanza con la opción recomendada y se informa (acuerdo de solicitudes con recomendación).

## 18. Lista de verificación

| # | Qué | Cómo | Esperado |
|---|---|---|---|
| V-01 | REQ-01, REQ-02 | `DatabaseUnavailabilityTest` y los cinco cortes por HTTP real | 503 `DATABASE_UNAVAILABLE` |
| V-02 | REQ-03, REQ-06 | Negativos y pruebas existentes de manejadores | 500 / 503 sin cambio |
| V-03 | REQ-04 | Captura de salida con `PSQLException("host=10.0.0.5 password=x")` | Sin `10.0.0.5` ni `password` en el cuerpo; un solo `ERROR` con `code`, `requestId` y `sqlState` |
| V-04 | REQ-05 | `DatabasePoolConfigurationTest`; base detenida con la configuración real del jar | 503 en menos de 8 s (hoy 30 s) |
| V-05 | REQ-07 | `ErrorCodeDocumentationTest`, `OpenApiDatabaseUnavailableTest`, lectura de `docs/errores.md` | Verde; fila de «difieren del estándar» retirada |
| V-06 | REQ-08 | `health` con la base caída | 200 |
| V-07 | REQ-09 | Firebase caído y base caída | `DEPENDENCY_UNAVAILABLE` y `DATABASE_UNAVAILABLE` |
| V-08 | Cobertura | `jacoco.csv` | ≥ 90 % de líneas y ramas por clase tocada |
| V-09 | Newman manual | D-01 y D-02 con `db` detenida | 503 y 200 |
| V-10 | Adversarial | 30 peticiones en paralelo con el pool agotado; 20 repeticiones; mensaje con SQL | Ninguna 500; mismas respuestas; sin filtración |
| V-11 | Regresión | Suite completa, `LayeredArchitectureTest`, `UntypedExceptionClassificationTest` | Verde |
