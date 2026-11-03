# Evento `cuenta.eliminada` versión 1

Cuentas publica este evento cuando la purga diaria borra una cuenta que nunca verificó su correo. Lo consume Perfil para borrar
la fecha de nacimiento que guardó de esa persona cuando se creó la cuenta.

## Destino

| Dato | Valor |
|---|---|
| Exchange | `cuentas.events` (tipo `topic`, durable, no se borra solo) |
| Clave de enrutamiento | `cuenta.eliminada` |
| Productor | La purga de cuentas sin verificar |
| Consumidor conocido | Perfil, cola `perfil.cuenta-eliminada`: borra la fecha de nacimiento de su réplica |
| Quién lo declara | Cuentas al conectarse; Perfil declara el exchange con los mismos atributos, para que el orden de arranque no importe |

## Cuerpo

`application/json`, UTF-8, sin campos nulos ni adicionales.

```json
{
  "usuarioId": "6f1d2c3b4a5e4f60718293a4b5c6d7e8",
  "eliminadaEn": "2026-10-10T08:00:00.000Z"
}
```

| Campo | Tipo y formato | Origen | Regla |
|---|---|---|---|
| `usuarioId` | texto, 1 a 128 caracteres | `cuenta.firebase_uid` | Es el `firebaseUid`, la identidad que todos los servicios reciben en `X-User-Id`. El `id` interno de la cuenta no lo conoce ningún otro servicio |
| `eliminadaEn` | instante ISO-8601 en UTC con milisegundos y sufijo `Z` | reloj UTC, en el momento de borrar la cuenta | Es el instante del borrado, no el de la publicación |

El cuerpo no lleva correo ni fecha de nacimiento: el consumidor ya conoce a la persona por `usuarioId`.

El esquema formal está en [`cuenta-eliminada-v1.schema.json`](cuenta-eliminada-v1.schema.json) (JSON Schema 2020-12,
`additionalProperties: false`): describe exactamente lo que Cuentas emite y una prueba compara la carga real con él.

## Propiedades AMQP

Los metadatos viajan en las propiedades del mensaje; el cuerpo lleva solo los dos campos.

| Propiedad | Valor | Para qué |
|---|---|---|
| `message_id` | identificador del evento (UUID v4 en texto) | Identificador único; un reenvío lleva el mismo |
| `type` | `cuenta.eliminada` | Tipo del evento |
| `headers.x-event-version` | `1` (entero) | Versión del contrato |
| `app_id` | `cameia-cuentas` | Productor |
| `timestamp` | `eliminadaEn`, con precisión de segundos (límite de AMQP) | Instante |
| `correlation_id` | igual que `message_id`: la purga no nace de una petición HTTP | Hilo para depurar |
| `headers.x-causation-id` | igual que `correlation_id` | La causa es la purga programada |
| `content_type` y `content_encoding` | `application/json` y `UTF-8` | El consumidor solo lee JSON con este tipo |
| `delivery_mode` | `2` (persistente) | Sobrevive a un reinicio del broker |
| `headers.spring_returned_message_correlation` y `headers.spring_listener_return_correlation` | identificadores internos del productor | No son parte del contrato: el consumidor los ignora |

## Garantías

- **Al menos una vez.** El mismo evento (mismo `message_id`) puede llegar dos veces; el consumidor debe ser idempotente: borrar
  lo que ya no existe no es un error.
- **Sin orden** garantizado entre eventos.
- **Inmutable.** El evento no cambia después de guardarse.
- **Sin pérdida por caída del broker.** El evento se guarda en la misma transacción que el borrado de la cuenta y una tarea
  periódica lo publica hasta que el broker lo confirma. El evento nunca anuncia un borrado que no ocurrió.
- **Uno por cuenta.** La tabla de salida admite un solo `cuenta.eliminada` por `usuarioId`.

## Privacidad

El cuerpo solo lleva el identificador de la cuenta y un instante. La carga se borra de la tabla de salida en cuanto el broker
confirma la publicación.

## Cómo se versiona

- Un campo nuevo **opcional** actualiza el esquema de la misma versión: los consumidores ignoran los campos que no usan.
- Quitar o cambiar un campo crea `cuenta.eliminada` **versión 2** en paralelo, con su propio esquema, y la versión 1 se mantiene
  hasta que todos los consumidores migren.
