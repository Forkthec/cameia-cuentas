# CM-279 · Cuentas publica `cuenta.creada` (HT-04, productor)

- **Jira:** CM-279 (subtarea de CM-256, «HT-04 Fecha de nacimiento disponible para Perfil»). Incluye el productor que el backlog
  llamaba CM-187.
- **Fuente de requisitos:** libro `09102026_01_Backlog_v6.xlsx`, hoja Backlog, fila HT-04 (CA-HT04.1 a CA-HT04.4 y su línea
  «Contrato»), y la nota técnica de HU-1.1 («Al crear la fila, Cuentas publica `cuenta.creada` … con outbox en la misma transacción»).
- **Repositorio:** `cameia-cuentas`, base `origin/develop` `9c4e545`. La otra mitad (consumo en Perfil) está en
  `cameia-perfil/specs/CM-279-replica-fecha-nacimiento/spec.md`.
- **Atributos de calidad:** anexo `Anexo_Restricciones_Atributos_Calidad` de la Entrega 1 (sección 15).
- **Estado:** **LISTA PARA EJECUTAR** (sección 23); pendiente de aprobación de Paula. Las decisiones de diseño están respondidas (sección 14).

## 1. Contexto

Perfil necesita la fecha de nacimiento del Usuario para validar las fechas de la experiencia laboral y de la formación (HU-2.4). El
Product Owner decidió el 8-oct-2026 (backlog v5) que Perfil no consulta a Cuentas: guarda una réplica local alimentada por el evento
`cuenta.creada`. Esta spec cubre lo que hace Cuentas: guardar el evento en una tabla de salida (outbox) en la misma transacción que la
fila de `cuenta`, publicarlo en RabbitMQ con confirmación del broker, reintentarlo hasta que el broker lo confirme y emitirlo una vez
para las cuentas que ya existen (carga inicial).

**Hoy (comprobado en `9c4e545`):** Cuentas no tiene RabbitMQ (ni dependencia, ni configuración, ni código; `infrastructure/messaging/*`
solo tiene `.gitkeep`), no tiene tabla outbox (la migración V1 la deja «para la spec que la use») y `RegisterUserService` guarda la cuenta
con `AccountRepository.save` sin transacción propia. Ninguna cuenta registrada produce un evento.

## 2. Alcance

**Dentro:**
1. Tabla `evento_saliente` (migración V5) y su puerto.
2. Registro (`POST /api/v1/users`, respuesta 201): la fila de `cuenta` y el evento `cuenta.creada` se guardan en una sola transacción.
3. Publicación del evento en el exchange `cuentas.events` con confirmación del broker; reintento de los pendientes.
4. Carga inicial y reconciliación permanente: un evento `cuenta.creada` por cada cuenta que no lo tenga, también para republicar uno
   ya publicado.
5. Configuración de RabbitMQ, `docker-compose.yml`, `.env.example`, `CLAUDE.md`, ADR y contrato del evento (documento y JSON Schema
   versionado, IOP-01).

**Fuera (con destino):**

| Qué | Destino |
|---|---|
| Consumir el evento y guardar la réplica | Mitad Perfil de CM-279 |
| Reglas de fechas de HU-2.4 que usan la réplica (CA-2.4.20 a 2.4.23, 2.4.31, 2.4.32, 2.4.36, 2.4.52, 2.4.53) | CM-274, bloque 5 |
| Publicar `cuenta.eliminada` al purgar cuentas sin verificar (CA-1.2.10) | CM-179, bloques 2 y 4: reutiliza esta tabla y el publicador (agrega el tipo por migración) |
| Publicar `cuenta.creada` en la sincronización de cuentas interrumpidas (CA-1.3.9, fuera del MVP) y al completar el registro con Google (HU-1.10, «MVP si alcanza») | La tarea de cada HU, llamando a `AccountRecordingService` (sección 8) |
| 503 cuando la base de datos no responde en el registro | CM-290 (catálogo de `docs/errores.md`, «Pendiente con destino») |
| Broker de staging, topología, Cloud Run Job y Cloud Scheduler | DevOps, por tarea que abre Vela (sección 13) |

## 3. Trazabilidad CA → requisito → prueba → Postman

| CA (backlog v6) | Qué le toca a Cuentas | Requisitos | Prueba automática | Postman (carpeta «Eventos de cuenta (local)») |
|---|---|---|---|---|
| HT-04 «Contrato»: exchange `cuentas.events`, clave `cuenta.creada`, carga `usuarioId`, `email`, `fechaNacimiento`, `creadaEn` | Publicar exactamente esa carga | REQ-EV-01 a 05, REQ-EV-10 a 14 | `AccountCreatedEventEndToEndTest.register_shouldPublishAccountCreated_whenAccountIsNew`, `OutboxRepositoryAdapterTest.appendAccountCreated_shouldStoreExactPayload_whenEventIsNew`, `AccountCreatedPayloadV1Test` | E-02 y E-03 |
| HT-04 «Contrato»: «Cuentas publica con outbox al crear la fila (CA-1.1.1)» | Evento en la misma transacción que la fila | REQ-EV-03, 04, 06 | `AccountRecordingServiceTest`, `AccountCreatedEventEndToEndTest.register_shouldLeaveNoAccountNorEvent_whenEventCannotBeStored` | E-02 |
| CA-1.1.1 / nota de HU-1.1 «el 200 de la cuenta pendiente no publica» | Registro repetido sin evento | REQ-EV-07 | `RegisterUserServiceTest.register_shouldNotRecordEvent_whenPendingAccountIsReturned`, `AccountCreatedEventEndToEndTest.register_shouldNotPublishAgain_whenRegistrationIsRepeated` | E-04 y E-05 |
| CA-HT04.1 (Perfil consume `cuenta.creada` con la fecha 15/03/2008) | La carga lleva `fechaNacimiento` `2008-03-15` | REQ-EV-05 | `AccountCreatedEventEndToEndTest.register_shouldPublishAccountCreated_whenAccountIsNew` (fecha literal 15/03/2008) | E-03 |
| CA-HT04.2 (réplica sin la fecha → 503) | El evento termina por llegar aunque el broker falle | REQ-EV-11 a 16 | `OutboxRelayServiceTest`, `AccountCreatedEventEndToEndTest.relayPending_shouldPublish_whenBrokerWasDownDuringRegistration` | — (se prueba con el broker detenido, ver sección 11) |
| CA-HT04.3 (evento duplicado → un registro en Perfil) | Entrega «al menos una vez»: puede salir dos veces | REQ-EV-15 | `OutboxRelayServiceTest.relay_shouldNotMarkPublished_whenBrokerDoesNotConfirm` | — |
| HT-04 observaciones: «carga inicial de las cuentas existentes» | Un evento por cuenta existente | REQ-EV-20 a 25 | `AccountCreatedBackfillServiceTest`, `AccountRepositoryAdapterTest.findUnannounced_*`, `AccountEventsRelayJobRunnerTest` | — (Job, sección 11) |
| CA-HT04.4 (`cuenta.eliminada`) | Nada en esta CM: lo publica CM-179 | — | — | — |

Ningún requisito queda sin CA salvo los no funcionales (sección 7), cuya razón es el estándar de Backend.

## 4. Antes y después

- **Antes (reproducible en `9c4e545`):** `POST /api/v1/users` con un correo nuevo responde 201 y ningún mensaje llega a RabbitMQ: no
  hay exchange `cuentas.events`. La primera prueba que se escribe en la tarjeta T-C2.6 (`register_shouldPublishAccountCreated_whenAccountIsNew`)
  falla con «no message received» y la petición E-03 de Postman falla con «expected 1 message, got 0». La salida real de ambas se
  pega en el PR antes de implementar.
- **Después:** la misma prueba y la misma petición encuentran exactamente un mensaje con la carga de la sección 5.

## 5. Contrato del evento `cuenta.creada` versión 1

**Destino.** Exchange `cuentas.events` (tipo `topic`, durable, no se borra solo), clave de enrutamiento `cuenta.creada`. Lo declara
Cuentas al conectarse (y Perfil también lo declara con los mismos atributos, para que el orden de arranque no importe).

**Cuerpo** (`application/json`, UTF-8, sin campos nulos ni adicionales):

```json
{
  "usuarioId": "6f1d2c3b4a5e4f60718293a4b5c6d7e8",
  "email": "ana.perez@ejemplo.test",
  "fechaNacimiento": "2008-03-15",
  "creadaEn": "2026-10-09T15:04:05.123Z"
}
```

| Campo | Tipo y formato | Origen | Regla |
|---|---|---|---|
| `usuarioId` | texto, 1 a 128 caracteres `[A-Za-z0-9]` | `cuenta.firebase_uid` | Es el `firebaseUid`, la identidad que todos los servicios reciben en `X-User-Id` (P-01). El `id` interno de `cuenta` no lo conoce ningún otro servicio |
| `email` | texto, ≤ 254 caracteres, minúsculas y sin espacios en los extremos | correo validado del registro (`EmailAddress.value()`) | El mismo valor con el que se creó la credencial |
| `fechaNacimiento` | fecha ISO-8601 `yyyy-MM-dd` | `BirthDate.value()` | Nunca nula: el registro la exige |
| `creadaEn` | instante ISO-8601 en UTC con milisegundos y sufijo `Z` (`yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`) | reloj UTC inyectado, en el momento de guardar el evento | En la carga inicial, `cuenta.fecha_creacion` |

**JSON Schema** (`docs/eventos/cuenta-creada-v1.schema.json`, IOP-01; Perfil guarda una copia literal para su prueba de contrato):

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "urn:cameia:eventos:cuenta-creada:v1",
  "title": "cuenta.creada versión 1",
  "description": "Cuerpo del evento que publica cameia-cuentas en el exchange cuentas.events con la clave cuenta.creada.",
  "type": "object",
  "additionalProperties": false,
  "required": ["usuarioId", "email", "fechaNacimiento", "creadaEn"],
  "properties": {
    "usuarioId": { "type": "string", "minLength": 1, "maxLength": 128, "pattern": "^[A-Za-z0-9]+$" },
    "email": { "type": "string", "format": "email", "maxLength": 254 },
    "fechaNacimiento": { "type": "string", "format": "date", "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}$" },
    "creadaEn": { "type": "string", "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z$" }
  },
  "examples": [
    {
      "usuarioId": "6f1d2c3b4a5e4f60718293a4b5c6d7e8",
      "email": "ana.perez@ejemplo.test",
      "fechaNacimiento": "2008-03-15",
      "creadaEn": "2026-10-09T15:04:05.123Z"
    }
  ]
}
```

`additionalProperties: false` hace que el esquema describa exactamente lo que Cuentas emite. Agregar un campo opcional actualiza el
esquema de la misma versión (los consumidores ignoran los campos que no usan; Perfil, `@JsonIgnoreProperties(ignoreUnknown = true)`);
quitar o cambiar un campo crea `cuenta.creada` versión 2 en paralelo.

**Propiedades AMQP** (P-02: los metadatos viajan aquí y el cuerpo lleva solo los cuatro campos del CA):

| Propiedad | Valor | Para qué |
|---|---|---|
| `message_id` | `evento_saliente.id` (UUID v4 en texto) | Identificador único del evento; un reenvío lleva el mismo |
| `type` | `cuenta.creada` | Tipo del evento |
| `headers.x-event-version` | `1` (entero) | Versión del contrato |
| `app_id` | `cameia-cuentas` | Productor |
| `timestamp` | `creadaEn` (precisión de segundos, límite de AMQP) | Instante |
| `correlation_id` | `X-Request-Id` del registro si cumple `^[A-Za-z0-9._-]{1,64}$`; si no, el `message_id` | Hilo para depurar entre Gateway, Cuentas y Perfil |
| `headers.x-causation-id` | igual que `correlation_id` | La causa es la petición HTTP |
| `content_type` / `content_encoding` | `application/json` / `UTF-8` | El convertidor de Perfil solo lee JSON con este tipo |
| `delivery_mode` | `2` (persistente) | Sobrevive a un reinicio del broker |

**Garantías.** Entrega al menos una vez: el mismo evento (mismo `message_id`) puede llegar dos veces. Sin orden garantizado entre
eventos. El evento no cambia nunca después de guardarse. El correo y la fecha de nacimiento son datos personales: ningún log de
Cuentas los escribe y la carga se borra de la tabla al confirmarse la publicación (P-05). Un evento ya publicado se puede republicar
(FIA-05) con el procedimiento de REQ-EV-27: sale con un `message_id` nuevo y los datos vigentes de la cuenta.

## 6. Requisitos funcionales (EARS)

### Guardar el evento con la cuenta

- **REQ-EV-01.** Cuando `POST /api/v1/users` cree una cuenta nueva (respuesta 201), el servicio debe guardar en `evento_saliente`
  una fila con `tipo` `cuenta.creada`, `version` `1`, `agregado_id` igual al `firebaseUid`, `carga` con los cuatro campos de la
  sección 5, `fecha_creacion` igual a `creadaEn`, `fecha_publicacion` nula, `intentos` `0` e `id_correlacion` según la sección 5.
- **REQ-EV-02.** El evento debe tomar el correo ya normalizado (recortado y en minúsculas) y la fecha ya validada por `AgePolicy`; nunca
  el texto crudo del cuerpo.
- **REQ-EV-03.** La fila de `cuenta` y la de `evento_saliente` deben guardarse en la misma transacción de base de datos: o quedan las
  dos o ninguna.
- **REQ-EV-04.** Si guardar el evento falla, la transacción debe deshacerse, la credencial de Firebase recién creada debe compensarse
  (borrarse) como hoy y la respuesta debe ser la misma que hoy da un fallo de base de datos en el registro (500 `INTERNAL_ERROR`; su
  cambio a 503 es de CM-290).
- **REQ-EV-05.** La fecha de nacimiento `15/03/2008` del cuerpo debe aparecer en la carga como `"2008-03-15"`; la del 29/02/2008 como
  `"2008-02-29"`.
- **REQ-EV-06.** El evento debe guardarse antes de cualquier intento de publicación: la publicación lee siempre de la tabla.
- **REQ-EV-07.** Cuando el registro devuelva una cuenta pendiente ya existente (200) o un conflicto (409, 422, 503), el servicio no debe
  guardar ni publicar ningún evento.
- **REQ-EV-08.** El servicio debe aceptar como máximo un `cuenta.creada` por cuenta (`uq_evento_saliente_tipo_agregado`); un segundo
  intento de guardarlo no debe fallar ni duplicarlo.

### Publicar

- **REQ-EV-10.** Cuando la transacción del registro termine con éxito, el servicio debe intentar publicar ese evento una vez, antes de
  responder, y esperar la confirmación del broker como máximo `cuentas.events.publish-timeout` (`500ms` en el servicio web, P-03 y
  DES-02). Si el plazo vence, el evento queda pendiente y lo publica la tarea `account-events-relay` en su siguiente ejecución (≤ 5 min).
- **REQ-EV-11.** Si el broker confirma (`ack`) y no devuelve el mensaje por no tener cola de destino, el servicio debe fijar
  `fecha_publicacion` con el reloj UTC y dejar `carga` en nulo (P-05).
- **REQ-EV-12.** Si el broker no responde dentro del plazo, rechaza (`nack`), devuelve el mensaje (sin cola enlazada) o la conexión
  falla, el servicio debe sumar 1 a `intentos`, dejar el evento pendiente y registrar un `WARN` con `eventId`, `type` y la clase simple
  del fallo, sin la carga.
- **REQ-EV-13.** Un fallo de publicación nunca debe cambiar la respuesta del registro: sigue siendo 201 con el mismo cuerpo, y la
  credencial no se compensa.
- **REQ-EV-14.** El mensaje publicado debe llevar el cuerpo y las propiedades de la sección 5.
- **REQ-EV-15.** Marcar un evento como publicado debe hacerse solo si sigue pendiente (`fecha_publicacion IS NULL`); si otra ejecución
  ya lo marcó, no es un error. La entrega duplicada que eso permite es aceptada por el contrato (al menos una vez).
- **REQ-EV-16.** Cuando se ejecute la tarea `account-events-relay`, el servicio debe publicar los eventos pendientes en orden de
  `fecha_creacion`, en lotes de 100, hasta que no quede ninguno o hasta que un lote entero falle; debe registrar en `INFO` el resumen
  (publicados, fallidos, pendientes que quedan) y en `ERROR` cada evento que llegue a 10 intentos (una sola vez, cuando `intentos` pasa
  de 9 a 10).

### Carga inicial

- **REQ-EV-20.** Cuando se ejecute la tarea `account-events-relay`, antes de publicar, el servicio debe guardar un `cuenta.creada` por
  cada cuenta sin uno, en lotes de 100 (P-04: la carga inicial es una reconciliación permanente dentro del mismo Job).
- **REQ-EV-21.** Debe tomar solo las cuentas con `estado` distinto de `ANONYMIZED` y `fecha_nacimiento` no nula; las demás no se
  emiten y no cuentan como fallo.
- **REQ-EV-22.** El correo debe leerse de Firebase por `firebaseUid`; `creadaEn` es `cuenta.fecha_creacion`; `correlation_id` es el
  `message_id`.
- **REQ-EV-23.** Si Firebase responde que el usuario no existe, o el usuario no tiene correo o su correo no cumple `EmailAddress`, la
  cuenta se omite y se cuenta en `skipped`. Al terminar la ejecución, si hubo omitidas, se registra un solo `WARN` con el total y hasta
  10 `firebaseUid` de ejemplo, sin correo (credencial perdida o dañada: se concilia a mano). Una línea por cuenta repetiría el aviso
  cada 5 minutos mientras la cuenta siga huérfana.
- **REQ-EV-24.** Si Firebase no responde (`DependencyUnavailableException`), la tarea debe detenerse sin publicar, registrar `ERROR`
  con traza y terminar con código de salida distinto de 0 para que el Job reintente; lo ya guardado queda guardado.
- **REQ-EV-25.** Repetir la tarea no debe duplicar eventos (REQ-EV-08): una cuenta con su `cuenta.creada` ya guardado no vuelve a salir.
- **REQ-EV-26.** La tarea `account-events-relay` debe arrancar sin servidor web, ejecutar REQ-EV-20 y REQ-EV-16 una vez y terminar con
  código 0 aunque queden eventos pendientes (se reintentan en la siguiente ejecución).
- **REQ-EV-27 (republicar, FIA-05).** Cuando haya que republicar el `cuenta.creada` de una cuenta ya publicada (por ejemplo, tras
  recrear la topología del broker), basta con borrar su fila de `evento_saliente`
  (`DELETE FROM microcuentas.evento_saliente WHERE tipo = 'cuenta.creada' AND agregado_id = '<firebaseUid>'`, o sin el filtro de
  `agregado_id` para todas): la siguiente ejecución de la tarea la trata como cuenta sin evento (REQ-EV-20 a 23) y la vuelve a emitir con
  un `message_id` nuevo. No hace falta código nuevo; el procedimiento queda en el ADR y lo prueba
  `AccountCreatedBackfillServiceTest.enqueueMissing_shouldRepublish_whenPublishedMarkerIsDeleted`. Perfil lo recibe como un mensaje nuevo
  y su réplica conserva la fecha ya guardada.

## 7. Requisitos no funcionales

- **REQ-NF-EV-01 (privacidad).** Ningún log de Cuentas escribe el correo, la fecha de nacimiento ni la carga; sí `eventId`, `type` y
  `firebaseUid`. Prueba: captura de salida en `AccountCreatedEventEndToEndTest.register_shouldNotLogPersonalData_whenEventIsPublished`.
- **REQ-NF-EV-02 (tiempo, DES-02).** En el servicio web, la publicación dentro del registro espera la confirmación como máximo
  `publish-timeout` (`500ms`) y la conexión al broker se corta a los `500ms` (`spring.rabbitmq.connection-timeout=500ms`); como la conexión
  se reutiliza entre peticiones, en el caso normal solo cuenta la confirmación (milisegundos). Con el broker caído o lento el registro
  suma como máximo 1 s y sigue respondiendo 201. La tarea `account-events-relay` no atiende personas y usa plazos más holgados en su
  perfil (`publish-timeout=5s`, `connection-timeout=5s`) para no fallar por un arranque en frío de la conexión AMQPS.
- **REQ-NF-EV-03 (configuración).** `cuentas.events.publish-timeout` se valida al arrancar: entre `100ms` y `10s`; fuera de rango la
  aplicación no arranca.
- **REQ-NF-EV-04 (credenciales).** Host, puerto, usuario, contraseña, vhost y TLS del broker llegan solo por variables
  `SPRING_RABBITMQ_*`; `.env.example` las declara vacías salvo los valores locales no secretos.

## 8. Diseño

```text
UserRegistrationController ──(requestId)──▶ RegisterUserService
   validar → Firebase (crear credencial, claim) ─┐
                                                  ▼
                       AccountRecordingService.recordNewAccount  (@Transactional)
                          AccountRepository.save + OutboxRepository.appendAccountCreated
                                                  │ commit
                                                  ▼
                       OutboxRelayService.relay(eventId)  ── EventPublisher (RabbitMQ, confirmación)
Job account-events-relay: AccountCreatedBackfillService.enqueueMissing → OutboxRelayService.relayPending
```

| Pieza | Capa y paquete | Por qué |
|---|---|---|
| `AccountCreated` (record) | `domain/event` | El hecho de negocio, sin JSON ni AMQP |
| `CorrelationId` (record) | `domain/event` | Regla de `X-Request-Id` (`^[A-Za-z0-9._-]{1,64}$`) con respaldo; se prueba sin Spring |
| `OutboundEvent` (record) | `domain/event` | Lo que el publicador necesita de una fila pendiente |
| `UnannouncedAccount` (record) | `domain/model` | Proyección de una cuenta sin evento (uid, fecha, `fecha_creacion`); `Account` no tiene fecha de creación y no se amplía por esto |
| `OutboxRepository` | `domain/port` | Puerto de la tabla |
| `EventPublisher` | `domain/port` | Puerto del broker; devuelve `true` solo con confirmación |
| `AccountRepository.findUnannounced(int)` | `domain/port` (método nuevo) | Cuentas sin `cuenta.creada` |
| `FirebaseUserDirectory.findEmail(String)` | `domain/port` (método nuevo) | Correo para la carga inicial |
| `AccountRecordingService` | `application/service` | Única transacción cuenta + evento; también la usan HU-1.3 y HU-1.10 |
| `OutboxRelayService` | `application/service` | Publica y marca; sin transacción larga (ninguna llamada al broker dentro de una transacción). Cada escritura de la tabla abre su propia transacción en `OutboxRepositoryAdapter` (`@Transactional` en sus métodos, como los adaptadores de Perfil; P-12) |
| `AccountCreatedBackfillService` | `application/service` | Carga inicial |
| `OutboxEventEntity`, `OutboxEventJpaRepository`, `OutboxRepositoryAdapter` | `infrastructure/persistence/*` | Patrón de tres piezas del repo |
| `AccountCreatedPayloadV1` | `infrastructure/messaging/payload` | Contrato JSON versionado; nombres en español solo en `@JsonProperty` |
| `RabbitEventPublisher` | `infrastructure/messaging/publisher` | Confirmación y devolución del broker |
| `AccountEventsMessagingConfiguration`, `AccountEventsProperties` | `infrastructure/config` | Exchange, plazo validado |
| `AccountEventsRelayJobRunner` | `infrastructure/config` | Mismo patrón que `PurgeJobRunner` de CM-179 |

**Reutilización.** `RegisterUserService` sigue siendo el orquestador y su compensación no cambia; `AccountRepository` y su adaptador
se amplían con un método; `InMemoryFirebaseUserDirectory` se amplía con `findEmail` (no se crea otro doble); `ProblemDetailTestSupport`
no se toca. No existe nada de mensajería ni de outbox que reutilizar (comprobado con `git grep -n "Rabbit\|outbox\|evento_saliente"`).

**Patrones.** Outbox transaccional (exigido por el estándar, §F «Eventos») y puerto y adaptador. Sin Strategy ni Factory: hay un solo
tipo de evento.

## 9. Base de datos (migración `V5__evento_saliente.sql`)

```sql
CREATE TABLE microcuentas.evento_saliente (
    id                UUID          NOT NULL,
    tipo              VARCHAR(64)   NOT NULL,
    version           SMALLINT      NOT NULL,
    agregado_id       VARCHAR(128)  NOT NULL,
    carga             JSONB         NULL,
    id_correlacion    VARCHAR(64)   NOT NULL,
    fecha_creacion    TIMESTAMPTZ   NOT NULL,
    fecha_publicacion TIMESTAMPTZ   NULL,
    intentos          INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_evento_saliente PRIMARY KEY (id),
    CONSTRAINT uq_evento_saliente_tipo_agregado UNIQUE (tipo, agregado_id),
    CONSTRAINT ck_evento_saliente_tipo CHECK (tipo IN ('cuenta.creada')),
    CONSTRAINT ck_evento_saliente_version CHECK (version >= 1),
    CONSTRAINT ck_evento_saliente_agregado_id CHECK (btrim(agregado_id) <> ''),
    CONSTRAINT ck_evento_saliente_id_correlacion CHECK (id_correlacion ~ '^[A-Za-z0-9._-]{1,64}$'),
    CONSTRAINT ck_evento_saliente_intentos CHECK (intentos >= 0),
    CONSTRAINT ck_evento_saliente_carga_pendiente CHECK ((fecha_publicacion IS NULL) = (carga IS NOT NULL)),
    CONSTRAINT ck_evento_saliente_fecha_publicacion CHECK (fecha_publicacion IS NULL OR fecha_publicacion >= fecha_creacion)
);
CREATE INDEX ix_evento_saliente_pendiente ON microcuentas.evento_saliente (fecha_creacion) WHERE fecha_publicacion IS NULL;
```

- `agregado_id` mide 128, igual que `cuenta.firebase_uid`; `id_correlacion` mide 64, igual que el patrón de `X-Request-Id`. Una prueba
  compara esos largos con las constantes del dominio (`CorrelationId.MAX_LENGTH`, `UnannouncedAccount.FIREBASE_UID_MAX_LENGTH`).
- `uq_evento_saliente_tipo_agregado` crea su índice y sirve al `NOT EXISTS` de la carga inicial.
- Sin clave foránea a `cuenta`: el evento es inmutable y debe sobrevivir al borrado de la cuenta (CM-179 publicará `cuenta.eliminada` con
  la fila ya borrada). `tipo` crecerá con una migración nueva en CM-179 (`cuenta.eliminada`); esta no se edita.
- `ck_evento_saliente_carga_pendiente` hace cumplir P-05 en la base: una fila publicada no puede conservar la carga. La fila publicada
  queda como marca «esta cuenta ya tiene su evento»; borrarla es el procedimiento de republicación (REQ-EV-27).
- Ninguna restricción es alcanzable desde la petición de registro (el `firebaseUid` lo genera el servicio y el correlativo se sanea
  antes): una violación es un defecto y sigue el camino de REQ-EV-04.
- Prueba de la migración sobre una base con filas de `cuenta` (`EventoSalienteSchemaMigrationTest`).

## 10. Validación y casos borde

**Entrada nueva: encabezado `X-Request-Id` del registro** (la única entrada que esta CM agrega; los campos del cuerpo no cambian y su
matriz está en la spec de CM-36):

| Caso | Valor literal | Resultado |
|---|---|---|
| Ausente | sin encabezado | `correlation_id` = `message_id` |
| Vacío / espacios | `""`, `"   "`, `"\t"` | `correlation_id` = `message_id` |
| Límite | 64 caracteres `a` | se usa tal cual |
| Uno más | 65 caracteres `a` | `correlation_id` = `message_id` |
| Caracteres no permitidos | `"abc def"`, `"<script>"`, `"ñandú"`, `"a%b"` | `correlation_id` = `message_id` |
| Válido | `3f1c9a52-7d0e-4b57-9a38-52c1e4d8a601` | se usa tal cual |

El encabezado nunca cambia la respuesta (no hay 4xx por él). Prueba: `CorrelationIdTest` (tabla parametrizada con todos los valores).

**Casos borde (estándar, §C):**

| Grupo | Caso | Cómo se cubre |
|---|---|---|
| Presencia | correo, fecha o uid nulos al construir `AccountCreated` | `AccountCreatedTest` (invariante defensiva, `NullPointerException`; clasificada en `UntypedExceptionClassificationTest`) |
| Texto | correo con tildes o mayúsculas | ya normalizado por `EmailAddress` (CM-36); la carga lleva `ana.perez@ejemplo.test` para `  Ana.Perez@Ejemplo.TEST ` |
| Fechas | 15/03/2008, 29/02/2008, `creadaEn` a medianoche UTC del 31/12/2026 (`2026-12-31T00:00:00.000Z`) | `AccountCreatedPayloadV1Test` con reloj fijo |
| Duplicados | registro repetido (200), dos ejecuciones de la tarea, dos relevos del mismo evento | REQ-EV-07, 08, 15; pruebas citadas en la sección 3 |
| Concurrencia | dos registros simultáneos del mismo correo | el perdedor no crea fila (CM-251) y por tanto no evento; `RegistroConcurrenteEndToEndTest` sigue en verde y se le agrega la aserción «un solo evento» |
| Falla parcial | falla al guardar el evento; falla de publicación tras el commit; caída del proceso entre commit y publicación | REQ-EV-04, 12, 13; el evento queda pendiente y lo publica la tarea |
| Dependencias | broker caído, lento (> 500 ms), sin cola de destino (mensaje devuelto), `nack` | `RabbitEventPublisherTest` (broker real) y `OutboxRelayServiceTest` |
| Republicación | marca de una cuenta publicada borrada a mano | REQ-EV-27; `AccountCreatedBackfillServiceTest.enqueueMissing_shouldRepublish_whenPublishedMarkerIsDeleted` |
| Carga | 250 cuentas sin evento (tres lotes) | `AccountCreatedBackfillServiceTest.enqueueMissing_shouldProcessAllBatches_whenMoreThanOneBatchExists` |
| Estado | cuenta `ANONYMIZED`, fecha nula, usuario borrado en Firebase | REQ-EV-21, 23 |
| Propiedad, colecciones de API, cuerpo grande, `Content-Type` | No aplican: no hay endpoint nuevo ni cambia el cuerpo del registro | — |

## 11. Errores

No se agrega ningún código al catálogo: el cambio no crea rutas HTTP ni respuestas nuevas. Rutas que llegan al manejador genérico:

| Ruta | Hoy | Con esta CM | Destino |
|---|---|---|---|
| Falla de base de datos al guardar cuenta + evento | 500 `INTERNAL_ERROR` | igual (REQ-EV-04) | CM-290 (503 de base de datos) |
| Falla al serializar la carga (Jackson) | — | inalcanzable: tipos propios sin ciclos; se clasifica «invariante defensiva» en `UntypedExceptionClassificationTest` | — |
| Falla del broker | — | nunca llega al cliente (REQ-EV-13) | — |

Logs: `WARN` por publicación fallida (sin traza), `ERROR` al llegar a 10 intentos y cuando la tarea no puede leer Firebase (con traza).

## 12. Seguridad (ASVS N1 y OWASP API Top 10)

- **API3 / datos mínimos:** la carga solo lleva lo que fija el CA; se borra de la tabla al publicarse (P-05).
- **API8 / configuración:** credenciales del broker por variables de entorno; AMQPS en staging con `SPRING_RABBITMQ_SSL_ENABLED=true`
  (DevOps; SEG-04 exige 100 % de comunicaciones protegidas); el exchange se declara durable sin permisos de borrado.
- **API10:** el publicador valida la confirmación y la devolución del broker antes de marcar.
- **Logs:** sin correo, fecha ni carga (REQ-NF-EV-01).
- **Inyección:** las consultas nativas usan parámetros (`:limit`, `:id`); ninguna concatena texto.
- **Identidad:** no cambia: el registro sigue sin identidad (ruta pública del Gateway).

### 12.1 ASVS 5.0.0 nivel 1 (`cameia-infra/docs/seguridad/matriz-asvs-nivel1.md`)

| ID | Aplica | Qué lo cumple | Prueba |
|---|---|---|---|
| 1.2.3 Salida JSON sin armar a mano | Sí | La carga la serializa `JsonMapper` desde `AccountCreatedPayloadV1`; nunca se concatena texto | `AccountCreatedPayloadV1Test.serialize_shouldHaveExactlyFourFields` |
| 1.2.4 Consultas parametrizadas | Sí | Las consultas nativas de `OutboxEventJpaRepository` y `AccountJpaRepository` usan `:parámetros`; ninguna concatena. La matriz dice «cero `nativeQuery`»: queda desactualizada (hallazgo para DevOps vía Vela) | `OutboxRepositoryAdapterTest`, `AccountRepositoryAdapterTest.findUnannounced_*` |
| 2.2.1 / 2.2.2 Validación de entrada en el servidor | Sí | `X-Request-Id` se valida con el patrón `^[A-Za-z0-9._-]{1,64}$` y, si falla, no se usa (nunca cambia la respuesta) | `CorrelationIdTest` (tabla de la sección 10) |
| 12.2.1 TLS en conexiones externas | Sí | AMQPS hacia el broker de staging por `SPRING_RABBITMQ_SSL_ENABLED=true` (de DevOps); en local el broker de compose es sin TLS | Pedido a DevOps (sección 13); `.env.example` |
| 14.2.1 Datos sensibles fuera de la URL | Sí | El correo y la fecha viajan solo en el cuerpo del mensaje; el encabezado `X-Request-Id` no es dato personal | `AccountCreatedEventEndToEndTest.register_shouldNotLogPersonalData_whenEventIsPublished` |
| 15.3.1 Solo los campos necesarios | Sí | La carga lleva solo los cuatro campos del contrato; `additionalProperties: false` en el esquema | `payload_shouldHaveExactlyTheSchemaProperties_whenSerialized` |
| 8.2.2 / 8.3.1 Acceso por dato | No aplica | No hay endpoint nuevo ni recurso expuesto; el evento sale hacia el broker con las credenciales del servicio | — |

### 12.2 OWASP API Security Top 10 — `POST /api/v1/users` (único endpoint tocado)

| Riesgo | Cómo | Prueba |
|---|---|---|
| API1 BOLA | No aplica: el registro no lee ni escribe recursos de otra persona | — |
| API2 Autenticación | Sin cambio: ruta pública del Gateway | Pruebas existentes del registro |
| API3 Propiedades del objeto | El cuerpo del evento lo arma el servicio con valores ya validados; ningún campo del cliente entra sin pasar por `EmailAddress`, `BirthDate` y `AgePolicy` | `AccountRecordingServiceTest` |
| API4 Consumo de recursos | Espera máxima de `500ms` por el broker; lotes de 100 y tope de 5000 cuentas por ejecución del Job | `RabbitEventPublisherTest`, `AccountCreatedBackfillServiceTest.enqueueMissing_shouldReportMoreRemain_whenLimitReached` |
| API5 Autorización por función | No aplica | — |
| API6 Flujos sensibles | El registro repetido no emite otro evento (idempotente) | `register_shouldNotPublishAgain_whenRegistrationIsRepeated` |
| API7 SSRF | No aplica: ninguna URL sale de la entrada | — |
| API8 Configuración | Credenciales del broker solo por variables; plazo validado al arrancar; el Job no abre puerto | `AccountEventsPropertiesTest`, `AccountEventsRelayJobRunnerTest.context_shouldHaveNoWebServer_whenProfileIsActive` |
| API9 Inventario | Sin ruta nueva; el único cambio de contrato HTTP es un encabezado opcional | `UserRegistrationControllerTest.register_shouldPassNull_whenHeaderMissing` |
| API10 Consumo inseguro | Se valida la confirmación y la devolución del broker antes de marcar; el correo leído de Firebase pasa por `EmailAddress` | `RabbitEventPublisherTest`, `FirebaseUserDirectoryAdapterTest.findEmail_*` |

Pruebas negativas de SEG-02 (token inválido, recurso ajeno, identidad en el cuerpo): no aplican, porque el endpoint es público y no hay
recurso por dueño.

## 13. Dependencias y acciones de otras personas

| # | Qué | Quién | Bloquea |
|---|---|---|---|
| 1 | Broker de staging con AMQPS, credenciales en Secret Manager y la opción A de topología (cada aplicación declara lo suyo) | Juan Diego Gomez, por tarea de Vela (pedido ya enviado, parte 3) | Prueba en staging |
| 2 | Cloud Run Job `account-events-relay` (misma imagen, `SPRING_PROFILES_ACTIVE=prod,account-events-relay`) y Cloud Scheduler cada 5 min | Juan Diego Gomez, por tarea de Vela (se agrega a la parte 3 del mismo pedido) | Reintento y carga inicial en staging |
| 5 | Agregar `cuenta-creada` al inventario de eventos activos del anexo de atributos de calidad (hoy lista cinco, sin este) | Actualización del anexo en la próxima entrega | — |
| 3 | La purga de CM-179 agrega `cuenta.eliminada` a `ck_evento_saliente_tipo` con una migración nueva y guarda el evento en la misma transacción que el borrado | Paula (revisión de la spec de CM-179) | CA-HT04.4 de punta a punta |
| 4 | `CuentasApplication.main` sale con el código del contexto también para el perfil `account-events-relay` (CM-179 hace lo mismo para `purge-job`): el que llegue segundo agrega su perfil a la misma condición | Paula | — |

Mientras no exista el broker de staging, todo se prueba en local con RabbitMQ real (Testcontainers y `docker compose`).

## 14. Decisiones

| # | Decisión | Por qué | Alternativas descartadas | Decisión humana |
|---|---|---|---|---|
| D1 | Consulta síncrona reemplazada por réplica con evento | Perfil no depende de Cuentas en línea | Consulta síncrona por ruta interna | Product Owner, 8-oct (backlog v5) |
| D2 | Las reglas de fechas de HU-2.4 van en CM-274; esta CM solo produce el evento | Una tarea por servicio y por HU | — | Paula (cronograma, 9-oct) |
| P-01 | `usuarioId` = `firebaseUid` | Todos los servicios lo reciben en `X-User-Id` | `cuenta.id` (UUID): Perfil no podría relacionarlo con el usuario del token | Sin alternativa razonable; Paula, 9-oct |
| P-02 | Metadatos en propiedades AMQP; correlativo desde `X-Request-Id`; formatos ISO | El cuerpo queda idéntico al CA y los metadatos van donde AMQP los define | Sobre JSON `{metadata, data}`: cambia la carga que fija el backlog | Paula, 9-oct («lo más seguro») |
| P-03 | Intento inmediato tras el commit con espera máxima de `500ms`, más Job cada 5 min | Réplica al instante en el caso normal, red de seguridad si el broker falla y el registro no suma más de 1 s (DES-02) | Espera de 2 s (rompe el p95 de DES-02 con un broker lento); solo Job (réplica hasta 5 min tarde); solo inmediato (un evento fallido queda pendiente para siempre); `@Scheduled` (D4 de CM-179: Cloud Run no garantiza instancia viva) | Paula, 9-oct |
| P-04 | Carga inicial dentro del mismo Job, como reconciliación permanente | Un solo Job para DevOps; repara cualquier cuenta sin evento y permite republicar (REQ-EV-27) | Job aparte de una sola vez; sin carga inicial | Paula, 9-oct |
| P-05 | Borrar la carga al confirmar la publicación y conservar la fila como marca | Mínimo de datos personales (Ley 1581); FIA-05 se cumple republicando desde la fuente (REQ-EV-27) | Conservar la carga sin plazo | Paula, 9-oct |
| P-06 | Dependencia `spring-boot-starter-amqp`; RabbitMQ en pruebas con `GenericContainer` (sin dependencia nueva de pruebas); imagen `rabbitmq:3.13-management-alpine` | Es la integración estándar de Spring Boot; la imagen es la que ya usa Perfil | Cliente AMQP a mano | Sin alternativa razonable; Paula, 9-oct |
| P-12 | `@Transactional` en los métodos de `OutboxRepositoryAdapter` | Mismo patrón que los adaptadores de Perfil (`ProfessionalProfileRepositoryAdapter`); el relevo no puede abrir una transacción porque entre leer y marcar está la llamada al broker | Servicio de aplicación sin lógica solo para abrir transacciones; anotación en la interfaz de Spring Data | Precedente de CM-271, aceptado por Paula, 9-oct |
| P-13 | Interruptor `cuentas.events.enabled` (`EVENTS_ENABLED`, por defecto `true`) | Igual que `FIREBASE_ENABLED`: la suite corre sin broker y solo las pruebas de mensajería lo encienden; en despliegue no se define | Sin interruptor: toda prueba con contexto de Spring necesitaría RabbitMQ | Precedente del repo, aceptado por Paula, 9-oct |
| Idioma | Identificadores, nombres de método de prueba y códigos en inglés; Javadoc, comentarios, `@DisplayName`, logs, mensajes, OpenAPI y documentos en español (R1) | Es la regla vigente y la que ya sigue todo el código de Cuentas | Comentarios y logs en inglés | Paula, 9-oct |

## 15. Atributos de calidad de CAMEIA (anexo de restricciones y atributos de calidad)

AC-0001 (adecuación funcional de las funciones con IA) no aplica: esta CM no usa IA.

| Atributo | Métrica del anexo | Cómo lo cumple | Prueba o evidencia |
|---|---|---|---|
| AC-0002 Desempeño (DES-02: p95 de operaciones JSON propias ≤ 2 s) | El registro suma como máximo 1 s por la publicación, solo con el broker caído o lento | `publish-timeout` y `connection-timeout` de `500ms` en el servicio web; conexión reutilizada | `RabbitEventPublisherTest.publish_shouldReturnFalseWithinTimeout_whenBrokerIsDown` (mide < 1,2 s); `AccountCreatedEventBrokerDownEndToEndTest.register_shouldRespond201WithinOneSecondExtra_whenBrokerIsDown` |
| AC-0003 Seguridad (SEG-04: comunicaciones protegidas y cero datos sensibles en telemetría) | AMQPS en staging; 0 correos, fechas o cargas en logs | REQ-NF-EV-01, 04; sección 12 | `AccountCreatedEventEndToEndTest.register_shouldNotLogPersonalData_whenEventIsPublished`; `SPRING_RABBITMQ_SSL_ENABLED` en el ADR y en `.env.example` |
| AC-0004 Fiabilidad (FIA-01: el fallo conserva el último estado confirmado) | Cuenta y evento en la misma transacción; un fallo del broker no cambia el registro | REQ-EV-03, 04, 12, 13 | `AccountRecordingServiceTest`, `register_shouldLeaveNoAccountNorEvent_whenEventCannotBeStored`, `relayPending_shouldPublish_whenBrokerWasDownDuringRegistration` |
| AC-0004 Fiabilidad (FIA-02: cero efectos adicionales por repetir la operación) | Un registro repetido no emite otro evento; la tarea repetida no duplica | REQ-EV-07, 08, 25 | `register_shouldNotPublishAgain_whenRegistrationIsRepeated`, `AccountCreatedBackfillServiceTest.enqueueMissing_shouldNotDuplicate_whenRunTwice` |
| AC-0004 Fiabilidad (FIA-05: cero eventos confirmados en Outbox sin posibilidad de republicación) | Todo evento publicado se puede volver a emitir | REQ-EV-27 | `AccountCreatedBackfillServiceTest.enqueueMissing_shouldRepublish_whenPublishedMarkerIsDeleted` |
| AC-0006 Capacidad de interacción | No cambia: el registro responde lo mismo que hoy | REQ-EV-13 | Pruebas existentes del registro en verde |
| MAN-01 (cobertura > 70 %; meta de Backend ≥ 90 % de líneas y ramas en lo nuevo) | ≥ 90 % por clase de la sección 8 | Puertos y adaptadores; métodos cortos | JaCoCo (`target/site/jacoco/jacoco.csv`) |
| MAN-02 (adaptadores con pruebas de éxito, formato inválido y timeout) | `RabbitEventPublisher` y `FirebaseUserDirectory.findEmail` con esos casos | Sección 10 | `RabbitEventPublisherTest` (confirmado, devuelto, `nack`, plazo vencido), `FirebaseUserDirectoryAdapterTest` (`findEmail`) |
| IOP-01 (APIs y eventos con OpenAPI, AsyncAPI o JSON Schema versionado) | Esquema v1 versionado y la carga real comparada con él | `docs/eventos/cuenta-creada-v1.schema.json` y `docs/eventos/cuenta-creada-v1.md` | `AccountCreatedPayloadV1Test.payload_shouldMatchSchemaExample_whenSerialized` y `payload_shouldHaveExactlyTheSchemaProperties_whenSerialized` |
| MAN-03 (cero accesos directos o FK entre bases de contextos) | `evento_saliente` sin clave foránea a otro servicio; Perfil recibe el dato por evento | Sección 9 | `EventoSalienteSchemaMigrationTest` |
| IOP-03 (topología asíncrona del MVP) | Exchange `cuentas.events` y clave `cuenta.creada` del contrato de HT-04 | Sección 5 | `RabbitEventPublisherTest.publish_shouldReturnTrueAndDeliverExactMessage_whenBrokerConfirms` |
| IOP-04 (productores con Outbox) | 100 % de los eventos de Cuentas salen de `evento_saliente` | REQ-EV-06 | `AccountRecordingServiceTest`, `OutboxRelayServiceTest` |

## 16. Pruebas (resumen; el detalle está en `tasks.md`)

Dominio: `CorrelationIdTest`, `AccountCreatedTest`. Aplicación: `AccountRecordingServiceTest`, `OutboxRelayServiceTest`,
`AccountCreatedBackfillServiceTest`, ampliación de `RegisterUserServiceTest`. Persistencia: `EventoSalienteSchemaMigrationTest`,
`OutboxRepositoryAdapterTest`, ampliación de `AccountRepositoryAdapterTest`. Cliente: ampliación de `FirebaseUserDirectoryAdapterTest`.
Mensajería: `AccountCreatedPayloadV1Test` (contra el JSON Schema), `RabbitEventPublisherTest`. Punta a punta:
`AccountCreatedEventEndToEndTest`. Tarea: `AccountEventsRelayJobRunnerTest`. Arquitectura: `LayeredArchitectureTest` y
`UntypedExceptionClassificationTest` en verde. Cobertura medida con `./mvnw.cmd clean verify` sobre las clases de la sección 8.

## 17. Preguntas respondidas

| # | Respuesta | Quién y cuándo |
|---|---|---|
| P-01 a P-06 e idioma | Sección 14 | Paula, 9-oct-2026 |
| P-12, P-13 | Resueltas por precedentes ya aprobados (adaptadores de Perfil en CM-271; `FIREBASE_ENABLED` en Cuentas) | Aceptado por Paula, 9-oct-2026 |
| Espera de confirmación en el registro | `500ms`, por DES-02 | Paula, 9-oct-2026 |

Preguntas abiertas: ninguna.

## 18. Riesgos aceptados y límites conocidos

| Riesgo | Decisión |
|---|---|
| Cuentas con `fecha_nacimiento` nula (la columna admite nulo; el registro la exige, pero hay filas anteriores) no reciben evento y su Perfil responde 503 `BIRTH_DATE_UNAVAILABLE` al agregar fechas | Aceptado: sin fecha no hay qué replicar. Antes de activar el Job en staging se cuenta con `SELECT count(*) FROM microcuentas.cuenta WHERE fecha_nacimiento IS NULL AND estado <> 'ANONYMIZED'` (consulta de solo lectura que ejecuta Paula en la base de Cuentas de staging, con la conexión de solo lectura que ya usa, en `BEGIN READ ONLY;` … `ROLLBACK;`, sin imprimir credenciales; resultado esperado: 0, y si no, el número se anota en `ESTADO.md` y se informa a Vela porque esas cuentas no tendrán fecha en Perfil) |
| Cuentas omitidas de forma permanente (usuario ausente en Firebase) se vuelven a consultar en cada ejecución; si pasaran de 5000 ocuparían todo el lote y las más nuevas no se procesarían | Aceptado para el MVP (unos 60 usuarios). `omitidos` y `faltanPorRegistrar` salen en el resumen `INFO`; si crecen, se agrega un cursor por `fecha_creacion` |
| Evento publicado dos veces (relevos simultáneos) | Contrato de al menos una vez; Perfil lo descarta por el Inbox |
| Registro que espera hasta 1 s si el broker está caído | Medido y acotado (DES-02); el 201 no cambia |

## 19. Integración con las otras CM de Cuentas

| Pieza compartida | Qué hace CM-279 | Con quién se coordina |
|---|---|---|
| Migraciones | `V5__evento_saliente.sql` (C1). Las tarjetas comprueban `origin/develop`; si ya existe un `V5`, se toma el siguiente y se corrige la spec | CM-179 (índice parcial de la purga y tipo `cuenta.eliminada`: migración posterior) y CM-290 |
| `ErrorCode`, `docs/errores.md` | Ningún código nuevo; solo se amplía la fila del 500 por base de datos (C3) | CM-290 |
| ADR | `0003-eventos-con-outbox.md`; si CM-179 ya tomó el `0003`, se usa el siguiente libre | CM-179 |
| `CuentasApplication.main` | Salida con código para el perfil `account-events-relay`; quien llegue segundo agrega su perfil a la misma condición | CM-179 (`purge-job`) |
| `RegisterUserService` | Constructor con `AccountRecordingService` y `OutboxRelayService`; `RegisterUserResult` con `eventId` | CM-179 y HU-1.3/HU-1.10 reutilizan `AccountRecordingService` |
| Colección de Postman | Carpeta «Eventos de cuenta (local)» y variables de RabbitMQ en `local.postman_environment.json` | CM-179 agrega la suya |
| `CLAUDE.md` | Secciones 1, 2, 5, 8 y 10 | CM-179 |

Orden en Cuentas: C1 → C2 → C3 en serie. CM-179 puede ir antes o después, pero **antes de ejecutar CM-179 hay que corregir su spec**
(hoy dice que la purga no publica eventos; CA-1.2.10 pide `cuenta.eliminada`) y alinear migración, ADR y `main`.

**Tamaño.** El código de cada bloque (C1 ≈ 750, C2 ≈ 850, C3 ≈ 700 líneas) cabe en los 1000 del límite, pero la spec, el plan y las
tarjetas suman ≈ 1450 líneas: van en un **PR 0 de solo documentos** (como la spec de CM-36), de modo que el stack queda en 4 PR.
Decidido por Paula el 9-oct-2026: sí, PR 0 de documentos.

## 20. Línea base verde

En `origin/develop` (con el PR 0 si ya está fusionado), antes de editar; salida pegada en el reporte de la primera tarjeta.

| Comprobación | Comando | Esperado |
|---|---|---|
| Herramientas | `java -version`; `docker version`; `npx newman --version` | JDK 21, Docker encendido, Newman 6 |
| Build y pruebas | `.\mvnw.cmd clean verify` | `BUILD SUCCESS`, 0 fallos y 0 omitidas (713 pruebas en `9c4e545`) |
| Entorno | Si `CuentasApplicationTests` falla por el puerto 5432 ocupado, es del entorno de la máquina de Paula (otro PostgreSQL local): se usa un PostgreSQL desechable en otro puerto y se informa | — |
| Cobertura de partida | `target/site/jacoco/jacoco.csv` | Global anotado (no debe bajar) |
| Migraciones | `git ls-tree --name-only origin/develop src/main/resources/db/migration/` | V1 a V4 |
| Postman | `docker compose up -d db`, jar empaquetado, Newman de la colección completa | 0 fallos |

## 21. Regla → evidencia (R1 a R14)

| Regla | Evidencia prevista | Estado en la spec |
|---|---|---|
| R1 | Identificadores en inglés; Javadoc, comentarios, logs y mensajes en español (regla 1 de las tarjetas) | Cubierta |
| R2 | Sección 10: la única entrada nueva es `X-Request-Id`; el cuerpo no cambia | Cubierta |
| R3 | Sección 11: ninguna ruta nueva al 500 genérico; el fallo del broker nunca llega al cliente | Cubierta |
| R4 | Sección 9 y `EventoSalienteSchemaMigrationTest` (cada `ck_` violado, largos, migración con cuentas) | Cubierta |
| R5 | Sección 10 | Cubierta |
| R6 | Sección 12 (ASVS y OWASP) | Cubierta |
| R7 | Sección 8 y decisiones T1 a T11 del plan; `LayeredArchitectureTest` | Cubierta |
| R8 | Javadoc en español, `@Parameter` del encabezado en OpenAPI, `docs/eventos/`, ADR 0003 | Cubierta |
| R9 | Sección 3 y matriz del plan | Cubierta |
| R10 | `jacoco.csv` de las clases de la sección 8 | Cubierta |
| R11 | T-C3.6 (carpeta «Eventos de cuenta (local)») y Newman completo | Cubierta |
| R12 | Sección 3 (HT-04 leída con `extraer_hu.py --id HT-04`) | Cubierta |
| R13 | Sección 15 | Cubierta |
| R14 | Sin push, PR ni Jira sin el sí de Paula | Cubierta |

## 22. Lista de verificación de la CM

| # | Comprobación | Cómo | Esperado |
|---|---|---|---|
| V-01 | Cada prueba de la sección 3 existe | `git grep -n "<método>" -- src/test` | Todas existen |
| V-02 | Antes y después | Primera prueba de T-C2.5 sobre el commit de C1 y luego sobre C2 | Falla y luego pasa, con la salida pegada |
| V-03 | Suite completa | `.\mvnw.cmd clean verify` | Verde, 0 omitidas |
| V-04 | Carga exacta | `AccountCreatedPayloadV1Test` y petición E-03 | Cuatro campos, fechas ISO, milisegundos con `Z` |
| V-05 | Transacción | `register_shouldLeaveNoAccountNorEvent_whenEventCannotBeStored`, `AccountRecordingTransactionTest` | Ni cuenta ni evento; credencial compensada |
| V-06 | Idempotencia | `register_shouldNotPublishAgain_whenRegistrationIsRepeated`, `enqueueMissing_shouldNotDuplicate_whenRunTwice` | Un solo evento |
| V-07 | Broker caído | `AccountCreatedEventBrokerDownEndToEndTest` | 201, evento pendiente, relevo < 1200 ms; se informa la medida |
| V-08 | Republicación | `enqueueMissing_shouldRepublish_whenPublishedMarkerIsDeleted` | Evento nuevo con otro `message_id` |
| V-09 | Tarea | Corrida manual con el perfil `account-events-relay` | Sin servidor web, código 0, línea de resumen |
| V-10 | Logs | `register_shouldNotLogPersonalData_whenEventIsPublished` y revisión del log de Newman | Sin correo, fecha ni carga |
| V-11 | Migración | `EventoSalienteSchemaMigrationTest` | Cada restricción rechaza su fila; no edita V1 a V4 |
| V-12 | Cobertura | `jacoco.csv` | ≥ 90 % de líneas y ramas por clase tocada; global no baja; `System.exit` de `main` justificado |
| V-13 | Postman | Newman completo | 0 fallos |
| V-14 | Comentarios y dependencias | `git grep -n "CM-[0-9]" -- src`; `git diff origin/develop -- pom.xml` | Ninguno; solo `spring-boot-starter-amqp` |
| V-15 | Tamaño | `git diff --shortstat <base>...<rama>` | ≤ 1000 por PR |
| V-16 | Revisiones | `/simplify`, `/code-review high`, `/security-review` | Hallazgos corregidos o descartados con razón |
| V-17 | Manual de punta a punta | Sección 7 de las tarjetas con Perfil P2 | Fila con `2008-03-15` en la base de Perfil |

## 23. Puerta de listo

| Condición | Estado |
|---|---|
| 1. Trazabilidad y atributos | Secciones 3 y 15 |
| 2. Matriz, casos borde, ruta al 500 y plan de seguridad | Secciones 10, 11 y 12 |
| 3. Contraste con `9c4e545` y ensayo en seco | Hecho el 9-oct-2026: se verificaron en el código real los archivos, firmas y pruebas citados, la sección de `docs/errores.md`, la API de `spring-rabbit-4.1.1.jar` y la imagen de RabbitMQ; correcciones en `analisis/revision-spec_CM-279.md` |
| 4. Integración | Sección 19 |
| 5. Línea base | Sección 20 |
| 6. Lista de verificación | Sección 22 |
| 7. Dudas | Ninguna. El PR 0 de documentos quedó decidido (Paula, 9-oct) |

**Dictamen: LISTA PARA EJECUTAR.** La aprueba solo Paula. Para el trabajo de DevOps de la sección 13 no hay que esperar: solo
condiciona la prueba en staging.
