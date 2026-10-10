# Evento `cuenta.creada` versión 1

Cuentas publica este evento cuando se crea una cuenta nueva. Lo consume Perfil para guardar la fecha de nacimiento de la persona
sin consultar a Cuentas en línea.

## Destino

| Dato | Valor |
|---|---|
| Exchange | `cuentas.events` (tipo `topic`, durable, no se borra solo) |
| Clave de enrutamiento | `cuenta.creada` |
| Quién lo declara | Cuentas al conectarse; Perfil lo declara con los mismos atributos, para que el orden de arranque no importe |

## Cuerpo

`application/json`, UTF-8, sin campos nulos ni adicionales.

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
| `usuarioId` | texto, 1 a 128 caracteres `[A-Za-z0-9]` | `cuenta.firebase_uid` | Es el `firebaseUid`, la identidad que todos los servicios reciben en `X-User-Id`. El `id` interno de la cuenta no lo conoce ningún otro servicio |
| `email` | texto `idn-email` (admite letras con tildes y eñes), hasta 254 caracteres, en minúsculas y sin espacios en los extremos | correo validado del registro | El mismo valor con el que se creó la credencial |
| `fechaNacimiento` | fecha ISO-8601 `yyyy-MM-dd` | fecha de nacimiento declarada | Nunca nula: el registro la exige |
| `creadaEn` | instante ISO-8601 en UTC con milisegundos y sufijo `Z` | reloj UTC, en el momento de guardar el evento | En la carga inicial es la fecha de creación de la cuenta |

El esquema formal está en [`cuenta-creada-v1.schema.json`](cuenta-creada-v1.schema.json) (JSON Schema 2020-12,
`additionalProperties: false`): describe exactamente lo que Cuentas emite y una prueba compara la carga real con él.

## Propiedades AMQP

Los metadatos viajan en las propiedades del mensaje; el cuerpo lleva solo los cuatro campos.

| Propiedad | Valor | Para qué |
|---|---|---|
| `message_id` | identificador del evento (UUID v4 en texto) | Identificador único; un reenvío lleva el mismo |
| `type` | `cuenta.creada` | Tipo del evento |
| `headers.x-event-version` | `1` (entero) | Versión del contrato |
| `app_id` | `cameia-cuentas` | Productor |
| `timestamp` | `creadaEn`, con precisión de segundos (límite de AMQP) | Instante |
| `correlation_id` | `X-Request-Id` del registro si cumple `^[A-Za-z0-9._-]{1,64}$`; si no, el `message_id` | Hilo para depurar entre el Gateway, Cuentas y Perfil |
| `headers.x-causation-id` | igual que `correlation_id` | La causa es la petición HTTP |
| `content_type` y `content_encoding` | `application/json` y `UTF-8` | El consumidor solo lee JSON con este tipo |
| `delivery_mode` | `2` (persistente) | Sobrevive a un reinicio del broker |
| `headers.spring_returned_message_correlation` y `headers.spring_listener_return_correlation` | identificadores internos del productor | No son parte del contrato: el consumidor los ignora |

## Garantías

- **Al menos una vez.** El mismo evento (mismo `message_id`) puede llegar dos veces; el consumidor debe ser idempotente.
- **Sin orden** garantizado entre eventos.
- **Inmutable.** El evento no cambia después de guardarse.
- **Sin pérdida por caída del broker.** El evento se guarda en la misma transacción que la cuenta y una tarea periódica lo publica
  hasta que el broker lo confirma.
- **Republicable.** Un evento ya publicado se puede volver a emitir con un `message_id` nuevo y los datos vigentes de la cuenta
  (procedimiento en el [registro de decisión 0003](../adr/0003-eventos-con-outbox.md)). Como el `message_id` es nuevo, el consumidor
  no puede deduplicarlo por mensaje: debe tratar `cuenta.creada` como idempotente por `usuarioId`.

## Privacidad

El correo y la fecha de nacimiento son datos personales. Ningún registro de Cuentas los escribe. La carga se borra de la tabla de
salida en cuanto el broker confirma la publicación; la fila queda como marca de que la cuenta ya tuvo su evento.

## Cómo se versiona

- Un campo nuevo **opcional** actualiza el esquema de la misma versión: los consumidores ignoran los campos que no usan.
- Quitar o cambiar un campo crea `cuenta.creada` **versión 2** en paralelo, con su propio esquema, y la versión 1 se mantiene hasta
  que todos los consumidores migren.
