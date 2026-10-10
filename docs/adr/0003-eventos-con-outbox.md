# ADR 0003 · Eventos de cuenta con tabla de salida (Outbox)

## Estado

Aceptada. Decisión de Backend con aprobación de la dueña del Backend, 9 de octubre de 2026.

## Contexto

Perfil necesita la fecha de nacimiento de cada persona para sus reglas de fechas y no debe consultar a Cuentas en línea. Cuentas
publica un evento `cuenta.creada` en RabbitMQ cuando crea una cuenta. Publicar directamente desde el registro tiene dos fallos
posibles: que el broker esté caído y el evento se pierda, o que la cuenta no se guarde y el evento describa algo que no existe. El
registro no puede tardar más de lo permitido por la métrica de desempeño de las operaciones JSON propias (p95 de 2 s).

## Decisión

- **Tabla de salida en la misma transacción.** La cuenta y su evento se guardan juntos en `microcuentas.evento_saliente`: o existen
  las dos filas o ninguna. Hay un evento `cuenta.creada` por cuenta (`uq_evento_saliente_tipo_agregado`).
- **Intento inmediato.** Cuando la transacción termina, el registro intenta publicar el evento y espera la confirmación del broker
  como máximo `500ms` (`cuentas.events.publish-timeout`; la conexión se corta a los `500ms`). Un fallo no cambia la respuesta 201 ni
  compensa la credencial: el evento queda pendiente.
- **Tarea de relevo cada 5 minutos.** Un Cloud Run Job con el perfil `account-events-relay` (misma imagen, sin puerto HTTP) corre
  cada 5 minutos: primero registra el evento de las cuentas que no tienen uno, después publica los pendientes. Usa plazos de 5 s
  porque nadie espera su respuesta. Termina con código 0 aunque queden pendientes; si el directorio de usuarios no responde, termina
  con un código distinto de 0 para que el Job se reintente.
- **Carga borrada al publicar.** Al confirmarse la publicación se borra la carga y se conserva la fila como marca. Así la base no
  guarda el correo ni la fecha de nacimiento por duplicado (Ley 1581).
- **Entrega al menos una vez.** Un reenvío lleva el mismo `message_id`; los consumidores son idempotentes (Inbox en Perfil).
- **Interruptor** `cuentas.events.enabled` (`EVENTS_ENABLED`, por defecto `true`), solo para las pruebas: la suite corre sin
  broker sustituyendo el publicador por un doble en memoria. No apaga los eventos en un despliegue: con `false` no existe el
  publicador real y la aplicación no arranca (a diferencia de `FIREBASE_ENABLED`). En un despliegue no se define.

## Por qué

Atributos de calidad del anexo: IOP-04 (productores con Outbox), FIA-01 (un fallo conserva el último estado confirmado), FIA-02
(repetir la operación no produce efectos adicionales), FIA-05 (todo evento publicado se puede republicar) y DES-02 (el registro no
suma más de 1 s por la publicación). El estándar del Backend exige Outbox para todo evento.

## Alternativas descartadas

- **Esperar la confirmación hasta 2 s:** con un broker lento rompe el p95 de 2 s del registro.
- **Solo el Job:** la réplica de Perfil llegaría hasta 5 minutos tarde en el caso normal.
- **Solo el intento inmediato:** un evento que falla queda pendiente para siempre.
- **`@Scheduled` dentro del servicio:** Cloud Run no garantiza que haya una instancia viva.
- **Un Job aparte para la carga inicial:** el mismo Job ya repara cualquier cuenta sin evento y permite republicar.
- **Conservar la carga sin plazo:** guarda datos personales de más.
- **Sobre JSON con metadatos (`{metadata, data}`):** cambia la carga que fija el criterio de aceptación; los metadatos van en las
  propiedades AMQP.

## Consecuencias

- **Conexión segura.** En staging y producción la conexión al broker usa AMQPS: `SPRING_RABBITMQ_SSL_ENABLED=true`, con host, puerto,
  usuario, contraseña y vhost por `SPRING_RABBITMQ_*`. Los secretos viven en el gestor de secretos, nunca en Git.
- **Réplica con retraso posible.** Si el broker falla, Perfil recibe el evento en la siguiente ejecución del Job (hasta 5 minutos).
- **Dos entregas posibles.** Si dos relevos coinciden, el mismo evento puede publicarse dos veces; no se retiene un bloqueo de fila
  durante las llamadas de red.
- **Anonimización pendiente.** Un evento pendiente guarda el correo y la fecha de nacimiento de su cuenta. Anonimizar una cuenta debe,
  en la misma transacción, poner en nulo `evento_saliente.carga` de sus eventos pendientes y marcarlos como publicados sin
  enviarlos; es trabajo de la tarea de anonimización de cuentas, porque el relevo actual no consulta el estado de la cuenta.
- **Tiempo máximo del relevo.** Una corrida se corta a los 4 minutos para terminar y registrar su resumen antes del plazo de 10
  minutos del Cloud Run Job; lo que quede se reintenta en la ejecución siguiente.
- **Eventos nuevos.** Un tipo de evento nuevo agrega su valor a `ck_evento_saliente_tipo` con una migración nueva.
- **Un Job con perfil propio.** El proceso sale con el código del contexto de Spring cuando el perfil de tarea está activo; los
  hilos de RabbitMQ mantendrían viva la JVM.
- **Configuración obligatoria en producción.** Con el perfil `prod`, la aplicación no arranca si falta `SPRING_RABBITMQ_ADDRESSES` (la URL
  `amqps://` completa, que es la forma de staging) o, sin ella, alguna de `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_USERNAME` y
  `SPRING_RABBITMQ_PASSWORD`: el mensaje de error nombra la variable y nunca su valor. Sin esa
  verificación Spring dejaría el texto `${VARIABLE}` como valor y los eventos quedarían pendientes sin ningún aviso. Fuera de
  producción no hay usuario ni contraseña por defecto.

## Transacciones

El estándar del servicio permite `@Transactional` solo en los servicios de aplicación. `OutboxRepositoryAdapter` es la excepción
y la razón es la siguiente: `OutboxRelayService` llama al broker y no puede ser transaccional, porque una transacción abierta
mientras se espera la red retendría una conexión de la base. El adaptador abre una transacción corta por cada escritura del relevo
(marcar publicado, registrar un intento fallido, agregar el evento) y, en el registro de la cuenta, se une a la transacción que ya
abrió el servicio de aplicación, de modo que la cuenta y su evento se guardan o se descartan juntos. Cualquier otro adaptador
sigue sin poder abrir transacciones.

## Procedimiento de republicación

Cuando haya que volver a emitir el `cuenta.creada` de una cuenta ya publicada (por ejemplo, tras recrear la topología del broker), se
borra su fila de la tabla de salida:

```sql
DELETE FROM microcuentas.evento_saliente WHERE tipo = 'cuenta.creada' AND agregado_id = '<firebaseUid>';
```

Sin el filtro de `agregado_id` se republican todas. La siguiente ejecución del Job trata la cuenta como una cuenta sin evento y la
emite con un `message_id` nuevo y los datos vigentes (correo leído de Firebase y fecha de nacimiento actual). Perfil lo recibe como
un mensaje nuevo y su réplica conserva la fecha ya guardada. No hace falta código nuevo.

## Decisión humana

La dueña del Backend, 9 de octubre de 2026: intento inmediato con espera de `500ms` más Job cada 5 minutos, carga inicial
dentro del mismo Job, carga borrada al publicar, metadatos en las propiedades AMQP, `usuarioId` igual a `firebaseUid`, e
interruptor `EVENTS_ENABLED`.
