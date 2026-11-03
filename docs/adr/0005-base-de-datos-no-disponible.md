# ADR 0005 · Base de datos no disponible

## Estado

Propuesta. Decisión de Backend, 19 de octubre de 2026; las decisiones de la spec fueron aprobadas por Paula Andrea Muñoz Delgado el 19 de
octubre de 2026. Pasa a Aceptada cuando se revise el PR.

## Contexto

Cuando Cuentas no podía hablar con su base de datos PostgreSQL respondía `500 INTERNAL_ERROR`, el mismo error genérico que un defecto de
programación, y tardaba hasta 30 segundos en hacerlo. La persona y Frontend no podían distinguir «reintenta en unos segundos» de «algo se
rompió». Se midió con PostgreSQL 16 real el 19 de octubre de 2026:

- **E-1** Pool de conexiones agotado: `CannotCreateTransactionException` ← `JDBCConnectionException` ← `SQLTransientConnectionException`.
- **E-2** Consulta lenta por un bloqueo de tabla: `QueryTimeoutException` ← `PSQLException` con SQLState `57014`.
- **E-3** Conexiones cortadas por el servidor antes de la consulta: `JpaSystemException` ← `org.hibernate.TransactionException`
  («Unable to rollback») ← `SQLException` «Connection is closed», **sin SQLState**.
- **E-4** Corte dentro de una transacción: igual que E-3.
- **E-5** Corte justo antes de confirmar: `DataAccessResourceFailureException` ← `PSQLException` `57P01`; la conexión cerrada viaja en
  `getSuppressed()`, no en `getCause()`.
- **E-6** Base detenida con la configuración por defecto del pool: 500 tras 30 s, el mismo tiempo que el Gateway espera una respuesta
  (`GATEWAY_TIMEOUT_MS`), así que el 503 casi nunca se vería.

Un fallo previsible no debe terminar en el error genérico: necesita su estado, su código estable y su mensaje.

## Decisión

- **D-1 · Un clasificador `DatabaseUnavailability`** en `presentation.advice` y una rama en el manejador general
  (`BusinessExceptionHandler.falloInterno`). Recorre la excepción, sus causas y sus suprimidas (máximo 64 nodos, sin repetir) y decide si es
  una indisponibilidad de la base.
- **D-2 · Cuentan como indisponibilidad** `DataAccessResourceFailureException`, `TransientDataAccessResourceException`,
  `QueryTimeoutException`, `JDBCConnectionException`, `org.hibernate.TransactionException` (salvo que una causa traiga un SQLState
  que no es de conexión, como `23505`), `SQLTransientConnectionException`,
  `SQLNonTransientConnectionException`, `SQLTimeoutException` y un `SQLException` con SQLState `08xxx`, `57P01`, `57P02`, `57P03` o
  `53300`. No cuentan los fallos del dato o de la consulta: `22xxx`, `23xxx`, `42xxx` ni `55P03`.
- **D-3 · `spring.datasource.hikari.connection-timeout=5000`.** Con 5 s el 503 llega antes del límite del Gateway y se absorbe el
  arranque en frío de una conexión. Se puede sobrescribir en el despliegue con `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT`.
- **D-4 · Mismo título y mensaje que `DEPENDENCY_UNAVAILABLE`:** «Servicio no disponible» y «Ocurrió un error. Inténtalo de nuevo.». El
  código nuevo es `DATABASE_UNAVAILABLE` (503). El mensaje de la excepción de la librería nunca llega al cliente.
- **D-5 · Sin `Retry-After`.** No se sabe cuándo vuelve la base.
- **D-6 · Sin tiempo máximo de consulta** en esta tarea (`jakarta.persistence.query.timeout`): cambia el comportamiento de consultas
  lentas legítimas y pide medir antes.
- **D-7 · Un solo código** para pool agotado, base detenida, corte y consulta lenta: la acción de la persona es la misma. El registro
  distingue la causa con `sqlState` y la clase más específica.
- **Rutas.** Lo emiten las tres rutas que usan la base: `POST /api/v1/users`, `GET /api/v1/users/me` y `POST /api/v1/users/me/verification`.
  `POST /api/v1/auth/logout-all` solo llama a Firebase, no usa la base y no lo emite.
- **Excepción de negocio.** La regla «todo fallo previsible tiene excepción de negocio» se cumple con una salvedad: la indisponibilidad
  no nace en el dominio ni en un puerto sino en Hibernate, Spring y el controlador JDBC, y puede aparecer al abrir, consultar, confirmar o
  deshacer cualquier transacción. Se reconoce en el manejador, igual que `BadParameterEncoding` reconoce el rechazo de Tomcat.

## Por qué

Para la persona y para Frontend una base caída es un fallo pasajero del servicio que se reintenta, no un defecto. Un solo clasificador en
el manejador general cubre abrir, consultar, confirmar y deshacer la transacción con una sola pieza y sin tocar cada puerto de persistencia.

## Alternativas descartadas

| Alternativa | Por qué se descartó |
|---|---|
| Envolver cada puerto de persistencia en una excepción de dominio | No cubre el inicio ni el cierre de la transacción, donde ocurre la mayoría de los cortes |
| Un aspecto AOP | Más maquinaria que una rama del manejador |
| Contar `55P03` (espera de bloqueo agotada) | Cuentas no bloquea filas con espera; si llegara sería un defecto, no una base caída |
| Dejar la espera por una conexión en 30 s | El 503 sería invisible: el Gateway cierra la petición a los 30 s |
| Espera de 3 s | Riesgo de falsos 503 en el arranque en frío de Cloud Run |
| Una variable de entorno nueva para la espera | Obliga a DevOps a tocar `.env.example` y el despliegue; la variable estándar de Spring Boot ya lo permite |
| Mensaje propio («No pudimos acceder a tus datos») | La persona no puede actuar distinto, y un mensaje específico ayudaría a conocer la infraestructura |
| `Retry-After: 5` fijo | Un número inventado: no se sabe cuándo vuelve la base |
| Fijar ya el tiempo máximo de consulta | Cambia el comportamiento de consultas lentas legítimas y pide medir |
| Un código por causa | Para quien llama la acción es la misma |

## Consecuencias

- Cambio aditivo para Frontend: un código nuevo, `DATABASE_UNAVAILABLE` (503), y casos que pasan de 500 a 503 tras 5 s como máximo. No se
  reemplaza ningún código existente; la acción esperada es la misma que con `DEPENDENCY_UNAVAILABLE`: mostrar el mensaje y permitir reintentar.
- Una violación de restricción, un SQL inválido y lo imprevisto siguen respondiendo 500 `INTERNAL_ERROR`.
- Una consulta que no termina no produce error mientras no haya tiempo máximo de consulta; queda en la sección de limitaciones de
  `docs/errores.md`.
- Los trabajos programados (relevo de eventos y purga) no pasan por el manejador HTTP: su comportamiento ante una base caída no cambia.
- El 503 de la base se registra en `ERROR` con la traza, `code`, `requestId`, `sqlState` y la clase más específica, una sola vez por
  petición: el registro propio de Spring `TransactionInterceptor` se apaga en `application.properties` porque repetiría el SQL y el
  mensaje de PostgreSQL. Quedan los avisos de Hikari sobre el pool, uno por conexión cortada y sin datos.
