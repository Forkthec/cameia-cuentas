# Plan · CM-279 · Cuentas publica `cuenta.creada`

Spec: `spec.md` de esta carpeta. Base: `origin/develop` `9c4e545`. Rama: `CM-279-publicar-cuenta-creada`.
Estado: pendiente de aprobación de Paula. Las decisiones de diseño están respondidas (sección 14 de la spec); no hay tarjetas
bloqueadas.

## 1. Enfoque

Un PR de documentos y tres bloques de código apilados, cada uno desplegable solo y por debajo de ~850 líneas de diff:

| Bloque | PR (base) | Qué deja funcionando | Líneas estimadas | Horas |
|---|---|---|---|---|
| PR 0 | `develop` | Solo `specs/CM-279-PublicarCuentaCreada/` (spec, plan y tarjetas, ≈ 1450 líneas de documentos); sin él el PR de C1 pasaría de 2000 líneas | ≈ 1450 | 0,5 |
| C1 | PR 0 fusionado (o `develop`) | Tabla `evento_saliente`, puerto y adaptador; JSON Schema del evento; el registro guarda cuenta + evento en una transacción. Sin RabbitMQ: los eventos quedan pendientes | ~750 (300 código, 450 pruebas) | 3,0 |
| C2 | C1 | RabbitMQ: exchange, publicador con confirmación, relevo, intento inmediato tras el commit (espera máxima `500ms`), configuración, `docker-compose.yml`, pruebas de punta a punta con broker sano y caído | ~850 | 4,0 |
| C3 | C2 | Carga inicial y republicación, tarea `account-events-relay` (Cloud Run Job), contrato del evento, ADR, `CLAUDE.md`, Postman | ~700 | 2,5 |

Total ≈ 9,5 h en Cuentas (más ≈ 5,5 h en Perfil). El backlog estima 3 h para todo HT-04; la diferencia se informa al PO en la comunicación de la sección 4.

C1 se puede desplegar sin broker: la tabla se llena y nada se publica hasta C2. C2 publica lo pendiente desde el primer registro
siguiente solo por el intento inmediato; lo anterior a C2 sale con la tarea de C3.

## 2. Archivos

| Acción | Ruta (bajo `src/main/java/tech/cameia/cuentas/` salvo que se diga otra) | Bloque |
|---|---|---|
| Crear | `src/main/resources/db/migration/V5__evento_saliente.sql` | C1 |
| Crear | `domain/event/AccountCreated.java`, `domain/event/CorrelationId.java`, `domain/event/OutboundEvent.java` | C1 |
| Crear | `domain/port/OutboxRepository.java` | C1 |
| Crear | `application/service/AccountRecordingService.java` | C1 |
| Crear | `infrastructure/persistence/entity/OutboxEventEntity.java` | C1 |
| Crear | `infrastructure/persistence/repository/OutboxEventJpaRepository.java`, `OutboxRepositoryAdapter.java` | C1 |
| Crear | `infrastructure/messaging/payload/AccountCreatedPayloadV1.java` | C1 (el adaptador serializa la carga al guardar) |
| Crear | `docs/eventos/cuenta-creada-v1.schema.json` (JSON Schema de la v1, IOP-01) | C1 (lo usa la prueba de la carga) |
| Modificar | `application/command/RegisterUserCommand.java` (campo `requestId`) | C1 |
| Modificar | `presentation/dto/RegisterUserRequest.java` (`toCommand(String requestId)`) | C1 |
| Modificar | `presentation/controller/UserRegistrationController.java` (encabezado `X-Request-Id` opcional) | C1 |
| Modificar | `application/service/RegisterUserService.java` (`completarRegistro` usa `AccountRecordingService`) | C1 |
| Crear | `domain/port/EventPublisher.java` | C2 |
| Crear | `application/service/OutboxRelayService.java` | C2 |
| Crear | `infrastructure/messaging/publisher/RabbitEventPublisher.java` | C2 |
| Crear | `infrastructure/config/AccountEventsMessagingConfiguration.java`, `AccountEventsProperties.java` | C2 |
| Modificar | `application/service/RegisterUserService.java` (intento inmediato tras el commit) | C2 |
| Modificar | `pom.xml` (`spring-boot-starter-amqp`) | C2 |
| Modificar | `src/main/resources/application.properties`, `.env.example`, `docker-compose.yml` | C2 |
| Crear | `domain/model/UnannouncedAccount.java`; métodos nuevos en `domain/port/AccountRepository.java` y `domain/port/FirebaseUserDirectory.java` | C3 |
| Modificar | `infrastructure/persistence/repository/AccountJpaRepository.java`, `AccountRepositoryAdapter.java`; `infrastructure/client/FirebaseUserDirectoryAdapter.java` | C3 |
| Crear | `application/service/AccountCreatedBackfillService.java` | C3 |
| Crear | `infrastructure/config/AccountEventsRelayJobRunner.java`, `src/main/resources/application-account-events-relay.properties` | C3 |
| Modificar | `CuentasApplication.java` (salida con código del contexto para el perfil de la tarea) | C3 |
| Crear | `docs/eventos/cuenta-creada-v1.md`, `docs/adr/0003-eventos-con-outbox.md` | C3 |
| Modificar | `CLAUDE.md` (secciones 1, 5, 8), `README.md` si cita variables, `postman/*` | C3 |

**No se tocan:** migraciones V1 a V4, `BusinessExceptionHandler`, `docs/errores.md` (no hay códigos nuevos; solo se agrega en C3 la nota
de que la falla de base de datos al guardar el evento sigue el destino CM-290, en la sección «Pendiente con destino» ya existente),
`.github/`.

## 3. Decisiones técnicas

| # | Decisión | Por qué | Descartado |
|---|---|---|---|
| T1 | `AccountRecordingService` separado de `RegisterUserService` | `RegisterUserService` no puede ser transaccional (llama a Firebase); el método transaccional debe estar en otro bean para que el proxy de Spring aplique `@Transactional` | `@Transactional` solo en el adaptador (serían dos transacciones: cuenta y evento podrían quedar separados); `TransactionTemplate` dentro de `RegisterUserService` (mezcla orquestación y transacción) |
| T2 | La carga se serializa al guardar y se guarda en `jsonb` | El evento es inmutable: lo que se publica es lo que se guardó, aunque la cuenta cambie después | Construir la carga al publicar (leería la cuenta y Firebase en el relevo) |
| T3 | `EventPublisher.publish` devuelve `boolean` | El relevo solo decide «marcar» o «sumar intento»; una excepción obligaría a capturarla en la aplicación | Lanzar una excepción de dominio |
| T4 | Marcar con `UPDATE … WHERE id = :id AND fecha_publicacion IS NULL` y sin bloqueo | No se retiene un bloqueo de fila durante la llamada al broker; el duplicado posible lo acepta el contrato | `SELECT … FOR UPDATE SKIP LOCKED` durante la publicación (llamada externa dentro de la transacción) |
| T5 | Intento inmediato llamado por `RegisterUserService` después de `recordNewAccount`, fuera del `try` de compensación | Un fallo de publicación no debe borrar la credencial de una cuenta ya guardada | `@TransactionalEventListener` (oculta el flujo y su prueba); llamarlo dentro del `try` |
| T6 | `INSERT … ON CONFLICT (tipo, agregado_id) DO NOTHING` nativo | Idempotencia de la carga inicial sin capturar excepciones de integridad | `save` + captura de `DataIntegrityViolationException` |
| T7 | RabbitMQ en pruebas con `GenericContainer` + `@DynamicPropertySource` | Evita la dependencia `testcontainers-rabbitmq`; `@ServiceConnection` no sirve con `GenericContainer` para RabbitMQ | Módulo de Testcontainers para RabbitMQ (dependencia nueva) |
| T8 | Tarea con perfil `account-events-relay` y `ApplicationRunner` | Mismo patrón que la purga de CM-179 (Cloud Run Job, sin `@Scheduled`) | Endpoint HTTP interno; `@Scheduled` |
| T9 | `@Transactional` en los métodos de `OutboxRepositoryAdapter` (escritura) y `@Transactional(readOnly = true)` en sus lecturas (P-12) | Spring Data exige una transacción para una consulta de escritura; el relevo no puede abrirla porque entre leer y marcar está la llamada al broker. Es el mismo patrón que los adaptadores de Perfil (`ProfessionalProfileRepositoryAdapter`); dentro de `AccountRecordingService` se une a su transacción | Un servicio de aplicación sin lógica solo para abrir transacciones; la anotación en la interfaz de Spring Data |
| T10 | Plazos distintos por perfil: `500ms` de conexión y confirmación en el servicio web, `5s` en la tarea | El registro no puede sumar más de 1 s (DES-02); la tarea no atiende personas y no debe fallar por una conexión AMQPS en frío | Un solo plazo de 2 s (rompe DES-02 con el broker lento) |
| T11 | La prueba de la carga compara contra el JSON Schema (ejemplo, propiedades, `pattern` y `maxLength`) sin validador | IOP-01 sin dependencia nueva; mismo enfoque que `ProfileUpdatedEventContractTest` de CM-67 | Biblioteca de validación de JSON Schema |

## 4. Riesgos

| Riesgo | Mitigación |
|---|---|
| `connection-timeout=500ms` puede ser corto para el primer handshake AMQPS en staging | El evento queda pendiente y la tarea lo publica con `5s`; la prueba de staging lo comprueba y, si el primer registro tras cada arranque siempre queda pendiente, se informa con la medida |
| Spring Boot 4 usa Jackson 3 (`tools.jackson.*`): `JacksonException` es `RuntimeException` | Clasificarla en `UntypedExceptionClassificationTest` como invariante defensiva |
| `@JdbcTypeCode(SqlTypes.JSON)` con `ddl-auto=validate` sobre `jsonb` | Prueba de arranque (`CuentasApplicationTests`) y `OutboxRepositoryAdapterTest` lo detectan; la tarjeta trae la anotación exacta |
| Con el starter de AMQP, cada `@SpringBootTest` crea una fábrica de conexiones | Las conexiones son perezosas; las pruebas existentes usan un `EventPublisher` en memoria (`InMemoryEventPublisher`) importado por `FirebaseTestConfiguration`, así ninguna intenta conectarse |
| Choque con CM-179 en `CuentasApplication.main` y en la migración del tipo | Sección 13 de la spec: el segundo en llegar extiende la condición y agrega su propia migración |
| Número de migración: CM-179 o CM-290 podrían crear V5 antes | La tarjeta T-C1.1 comprueba `git ls-tree origin/develop src/main/resources/db/migration`; si V5 existe, se usa el siguiente libre y se corrige la spec |

## 5. Matriz de pruebas

| Camino | Prueba | Capa | Bloque |
|---|---|---|---|
| `X-Request-Id` válido, ausente, vacío, 64, 65, caracteres no permitidos | `CorrelationIdTest` | dominio | C1 |
| `AccountCreated` con nulos | `AccountCreatedTest` | dominio | C1 |
| Registro nuevo guarda cuenta + evento con valores literales | `AccountRecordingServiceTest` | aplicación | C1 |
| Falla al guardar el evento → nada guardado y credencial compensada | `RegisterUserServiceTest`, `AccountCreatedEventEndToEndTest` | aplicación, E2E | C1, C2 |
| Registro repetido (200) y conflictos sin evento | `RegisterUserServiceTest` | aplicación | C1 |
| Cada restricción de `evento_saliente` violada | `EventoSalienteSchemaMigrationTest` | persistencia | C1 |
| Largos de columna = constantes del dominio | `EventoSalienteSchemaMigrationTest.columnLengths_shouldMatchDomainLimits` | persistencia | C1 |
| Guardar, duplicado ignorado, pendientes en orden, marcar, marcar dos veces, sumar intento | `OutboxRepositoryAdapterTest` | persistencia | C1 |
| Carga JSON exacta (15/03/2008, 29/02/2008, medianoche UTC) y conforme al JSON Schema v1 | `AccountCreatedPayloadV1Test` | infraestructura | C1 |
| Publicar con confirmación; sin cola (devuelto); broker caído (< 1 200 ms) | `RabbitEventPublisherTest` | infraestructura (RabbitMQ real) | C2 |
| Registro con el broker caído: 201, evento pendiente, intento inmediato < 1 200 ms | `AccountCreatedEventBrokerDownEndToEndTest` | E2E | C2 |
| Relevo: publicado → marca; no confirmado → intento; ya publicado → nada; 10.º intento → `ERROR` una vez; lote que falla entero → se detiene | `OutboxRelayServiceTest` | aplicación | C2 |
| Fallo del relevo inmediato no cambia el 201 ni compensa | `RegisterUserServiceTest` | aplicación | C2 |
| Registro → mensaje en la cola con cuerpo y propiedades exactas; repetido → sin mensaje; broker caído → 201 y pendiente; tarea → publicado; logs sin datos personales | `AccountCreatedEventEndToEndTest` | E2E | C2 |
| Plazo de publicación fuera de rango no arranca | `AccountEventsPropertiesTest` | configuración | C2 |
| Cuentas sin evento: excluye `ANONYMIZED`, fecha nula y las que ya tienen; orden; límite | `AccountRepositoryAdapterTest` | persistencia | C3 |
| Correo por uid: encontrado, no existe, Firebase caído | `FirebaseUserDirectoryAdapterTest` | cliente | C3 |
| Carga inicial: guarda, omite, se detiene con Firebase caído, varios lotes, no duplica, republica al borrar la marca | `AccountCreatedBackfillServiceTest`, `AccountRepositoryAdapterTest` | aplicación, persistencia | C3 |
| Tarea: sin servidor web, llama carga y relevo una vez, plazos de `5s`, código de salida | `AccountEventsRelayJobRunnerTest` | integración | C3 |

## 6. Orden y paralelismo

C1 → C2 → C3 en serie (cada uno es base del siguiente). La mitad Perfil (bloques P1 y P2 de su plan) es independiente y puede ir en
paralelo. La prueba manual de punta a punta (registro en Cuentas → réplica en Perfil) se hace al final, con los dos servicios y un
solo broker local (sección 7 de `tasks.md`).

## 7. Verificación de cierre

`./mvnw.cmd clean verify` en verde con la salida real; cobertura ≥ 90 % de líneas y ramas en cada clase de la sección 2 (números de
`target/site/jacoco/jacoco.csv`); Newman de la colección completa contra la aplicación empaquetada (`java -jar target/cuentas-*.jar`)
con PostgreSQL y RabbitMQ de `docker compose`; salida pegada en el PR.
