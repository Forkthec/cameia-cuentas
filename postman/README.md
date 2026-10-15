# Pruebas de API de Cuentas (Postman y Newman)

Esta carpeta trae la colección de Postman del microservicio de Cuentas y el entorno local con el que se corre.

| Archivo | Contenido |
|---|---|
| `cameia-cuentas.postman_collection.json` | Colección v2.1: carpeta `Registro` (8 peticiones), carpeta `Firebase no disponible (manual)` (1 petición), carpeta `Eventos de cuenta (local)` (10 peticiones), carpeta `Mi cuenta` (12 peticiones), carpeta `Activación` (18 peticiones) y carpeta `Cerrar sesión en todos los dispositivos` (12 peticiones) |
| `local.postman_environment.json` | Entorno local: `baseUrl` (`http://localhost:8081`, el puerto de `SERVER_PORT`) y los datos del broker de compose (`rabbitManagementUrl`, `rabbitUser` y `rabbitPassword`, esta última vacía) y el emulador de Firebase Auth (`firebaseEmulatorUrl`, `http://localhost:9099`, y `firebaseProjectId`, `demo-cameia`). No lleva secretos |

## Qué verifica

Cada petición comprueba su estado y su cuerpo. Además, el script de la colección comprueba en **todas** las respuestas:

- `Content-Type` con `charset=utf-8` (salvo en un 204, que no lleva cuerpo ni tipo);
- en los éxitos con cuerpo, tipo JSON;
- en los errores (estado 400 o mayor), `application/problem+json`, `requestId` igual al encabezado `X-Request-Id` y un cuerpo sin las marcas `Exception`, `SQL`, `org.` ni `tech.cameia`.

Carpeta `Registro` (`POST /api/v1/users`), en este orden y con un correo sintético nuevo en cada vuelta (`prueba+<marca de tiempo>-<iteración>@ejemplo.test`):

| # | Petición | Resultado esperado |
|---|---|---|
| 1 | Registro nuevo | 201, `id`, `firebaseUid` de 32 caracteres hexadecimales, `PENDING_VERIFICATION`, `FREE` |
| 2 | El mismo registro otra vez | 200 con el mismo `id` y el mismo `firebaseUid` |
| 3 | El mismo correo con mayúsculas y espacios | 200 con el mismo `id` |
| 4 | Otros datos y otra contraseña | 200 con el mismo `id`; el reintento ignora los datos nuevos |
| 5 | Cuerpo inválido con el correo existente | 422 `VALIDATION_FAILED` con el campo `password` |
| 6a | Registro de una segunda cuenta | 201 |
| 6b | Activación de esa cuenta (`POST /api/v1/users/me/verification`, con los encabezados `X-User-Id` y `X-User-Email-Verified` que pone el Gateway) | 200 |
| 6c | Registro con el correo de la cuenta ya activa | 409 `EMAIL_ALREADY_REGISTERED`, con «Ese correo ya tiene una cuenta.» en `errors[email]` |

Carpeta `Eventos de cuenta (local)`: comprueba que un registro nuevo publica exactamente un evento `cuenta.creada` con la carga del contrato. Habla con el API de administración del broker de compose, así que **exige el broker levantado** (`docker compose up -d db rabbitmq`) y la contraseña del `.env` en `rabbitPassword`. El script de la colección no aplica sus comprobaciones de `charset` y `problem+json` a las peticiones al broker, que no las cumplen por ser de otro sistema.

| # | Petición | Resultado esperado |
|---|---|---|
| E-00 | `PUT .../api/exchanges/%2F/cuentas.events` | 201 o 204: declara el exchange, que en un broker recién creado solo existe tras la primera publicación |
| E-01a | `PUT .../api/queues/%2F/qa.cuenta-creada` | 201 o 204: cola de prueba no durable |
| E-01b | `POST .../api/bindings/%2F/e/cuentas.events/q/qa.cuenta-creada` | 201 o 204: enlazada con la clave `cuenta.creada` |
| E-02 | Registro nuevo con `X-Request-Id: pm-<marca>` y fecha `15/03/2008` | 201; guarda el `firebaseUid` |
| E-03 | `POST .../qa.cuenta-creada/get` (hasta 10 mensajes, para que un duplicado se vea) | Exactamente un mensaje con exactamente `usuarioId`, `email`, `fechaNacimiento` y `creadaEn`; `type`, `app_id`, `message_id` (UUID), `correlation_id` (el `X-Request-Id` enviado), `x-causation-id`, `x-event-version`, `delivery_mode` 2, `content_encoding` y `content_type` del contrato |
| E-04 | El mismo registro | 200 |
| E-05 | El `get` de E-03 otra vez | Arreglo vacío: no hubo un segundo evento |
| E-07 | Registro nuevo con un `X-Request-Id` de 65 caracteres (no válido) | 201 |
| E-08 | El `get` de E-03 | Un mensaje cuyo `correlation_id` y `x-causation-id` son su `message_id`: el identificador inválido se descarta |
| E-06 | `DELETE .../qa.cuenta-creada` | 204 |

Carpeta `Mi cuenta` (`GET /api/v1/users/me`): registra dos cuentas sintéticas y consulta la primera con la identidad que pondría el Gateway. Todas las peticiones comprueban además que respondan en menos de 2 s.

| # | Petición | Resultado esperado |
|---|---|---|
| M-00 | Registro de la cuenta que se consultará | 201; guarda `meUid` |
| M-00b | Registro de una segunda cuenta | 201; guarda `uidOtra` e `idOtra` |
| M-01 | `GET /api/v1/users/me` con `X-User-Id: {{meUid}}` | 200 con exactamente `id`, `firstName`, `lastName`, `birthDate` (`1995-04-12`), `phoneNumber`, `pronoun`, `status` (`PENDING_VERIFICATION`) y `plan` (`FREE`); sin correo ni identificadores internos; encabezado `Cache-Control: no-store` |
| M-02 | La misma consulta | 200 con `phoneNumber` presente y `null` (la cuenta se registró sin celular) |
| M-03 | Consulta con `?firebase_uid={{uidOtra}}&id={{idOtra}}&uid={{uidOtra}}` | 200 con el `id` propio y nunca el de la otra cuenta |
| M-04 | `X-User-Id` de una cuenta que no existe | 404 `ACCOUNT_NOT_FOUND` con «No encontramos una cuenta para este usuario.» |
| M-05 | Sin `X-User-Id` | 400 `IDENTITY_REQUIRED` con «La petición no incluye los datos que exige esta ruta.» |
| M-06 | `X-User-Id` en blanco | 400 `IDENTITY_REQUIRED` |
| M-06b | `X-User-Id` de 129 caracteres | 400 `IDENTITY_REQUIRED` |
| M-06c | `X-User-Id: ' OR 1=1 --` | 404 `ACCOUNT_NOT_FOUND`, sin repetir el valor |
| M-08 | `Accept: application/xml` | 406 `MEDIA_TYPE_NOT_ACCEPTABLE` |
| M-09 | `GET /v3/api-docs` | 200 con `GET /api/v1/users/me` y sus respuestas 200, 400, 404, 406 y 500; exige `API_DOCUMENTATION_ENABLED=true` |

La cuenta anterior al registro con fecha (sin fecha de nacimiento, que se devuelve con `birthDate: null`) no se puede crear por la API, así que no tiene petición aquí: la cubre `CurrentAccountEndToEndTest.getMe_shouldReturnBirthDateNull_whenLegacyAccountHasNoBirthDate`.

Carpeta `Activación` (`POST /api/v1/users/me/verification`): registra una cuenta sintética y la activa con la identidad y la verificación que pondría el Gateway. Las peticiones corren en el orden de la tabla: primero los rechazos con la cuenta aún pendiente y después la activación. Todas comprueban `code` y `detail` literales y que respondan en menos de 2 s.

| # | Petición | Resultado esperado |
|---|---|---|
| A-00 | Registro de la cuenta que se activará | 201; guarda `actUid` y `actId` |
| A-03 | `X-User-Email-Verified: false` | 403 `EMAIL_NOT_VERIFIED` con «Tu correo aún no está verificado.» |
| A-04 | Sin `X-User-Email-Verified` | 403 `EMAIL_NOT_VERIFIED` |
| A-05 | `X-User-Email-Verified: abc` | 403 `EMAIL_NOT_VERIFIED` (nunca 500) |
| A-06 | Cuerpo `{"emailVerified": true, "status": "ACTIVE"}` sin encabezado | 403 `EMAIL_NOT_VERIFIED`: el cuerpo se ignora |
| A-06b | Cuerpo que no es JSON, sin encabezado | 403 `EMAIL_NOT_VERIFIED` |
| A-12 | `X-User-Id` de la cuenta de A-00 sin el encabezado de verificación | 403 `EMAIL_NOT_VERIFIED` |
| A-12b | `GET /api/v1/users/me` de esa cuenta | 200 con `status` `PENDING_VERIFICATION`: los rechazos no la cambiaron |
| A-07 | Sin `X-User-Id` | 400 `IDENTITY_REQUIRED` con «La petición no incluye los datos que exige esta ruta.» |
| A-08 | `X-User-Id` en blanco | 400 `IDENTITY_REQUIRED` |
| A-09 | `X-User-Id` de 129 caracteres | 400 `IDENTITY_REQUIRED` |
| A-10 | `X-User-Id: ' OR '1'='1` con el encabezado de verificación | 404 `ACCOUNT_NOT_FOUND`, sin repetir el valor |
| A-10b | `X-User-Id` de 128 «ñ» | 404 `ACCOUNT_NOT_FOUND` (el largo exacto pasa la validación) |
| A-01 | `X-User-Email-Verified: true` | 200 con exactamente `id` y `status` `ACTIVE` |
| A-02 | La misma petición otra vez | 200, el mismo resultado |
| A-02b | `GET /api/v1/users/me` | 200 con `status` `ACTIVE` |
| A-02c | Activación con `X-User-Email-Verified: false` sobre la cuenta ya activa | 200 con `ACTIVE`: el encabezado solo cuenta para la cuenta pendiente |
| A-11 | `GET /v3/api-docs` | 200 con `POST /api/v1/users/me/verification` y sus respuestas 200, 400, 403, 404, 409 y 500; exige `API_DOCUMENTATION_ENABLED=true` |

Las respuestas 403 `ACCOUNT_DISABLED` y 409 `ACCOUNT_CONFLICT` no se pueden provocar por HTTP sin tocar la base: las cubren `AccountActivationControllerTest`, `ActivateAccountServiceTest` y `AccountRegistrationEndToEndTest`.

Carpeta `Cerrar sesión en todos los dispositivos` (`POST /api/v1/auth/logout-all`): registra una cuenta sintética, cierra su sesión con la identidad que pondría el Gateway y comprueba en el emulador de Firebase que la revocación quedó guardada. Como las otras carpetas, comprueba `code` y `detail` literales y que cada petición a Cuentas responda en menos de 2 s; las peticiones al emulador y el script de la colección no aplican a una respuesta 204 la comprobación de `charset`.

| # | Petición | Resultado esperado |
|---|---|---|
| L-00 | Registro de la cuenta que cerrará sesión | 201; guarda `logoutUid` |
| L-01 | `POST /api/v1/auth/logout-all` con `X-User-Id: {{logoutUid}}`; antes guarda `revocadoDesde` (segundos) y espera 1,1 s | 204 con cuerpo vacío |
| L-01b | `POST {{firebaseEmulatorUrl}}/identitytoolkit.googleapis.com/v1/projects/{{firebaseProjectId}}/accounts:lookup` con `Authorization: Bearer owner` | 200; `validSince` del usuario es mayor o igual que `revocadoDesde` |
| L-02 | Repetir L-01 | 204: es idempotente |
| L-03 | `X-User-Id` de un usuario que no existe en Firebase | 204 |
| L-04 | Sin `X-User-Id` | 400 `IDENTITY_REQUIRED` con «La petición no incluye los datos que exige esta ruta.» |
| L-05 | `X-User-Id` de 129 caracteres | 400 `IDENTITY_REQUIRED` |
| L-06 | Cuerpo `{"firebaseUid":"uid-ajeno"}` | 204: el cuerpo se ignora |
| L-07 | `?uid=uid-ajeno&firebaseUid=uid-ajeno` | 204: la consulta se ignora |
| L-08 | `X-User-Id: ' OR 1=1 --` | 204 con cuerpo vacío, sin repetir el valor |
| L-09 | `GET` sobre la ruta | 405 `METHOD_NOT_ALLOWED`; `Allow` contiene `POST` |
| L-10 | `GET /v3/api-docs` | 200 con `POST /api/v1/auth/logout-all` y sus respuestas 204, 400, 405, 500 y 503; exige `API_DOCUMENTATION_ENABLED=true` |

La respuesta 503 `DEPENDENCY_UNAVAILABLE` (Firebase caído al revocar) y la 500 `INTERNAL_ERROR` (Firebase rechaza la revocación por permisos) no se pueden provocar contra el emulador: las cubren `SessionRevocationControllerTest` y `SessionRevocationEndToEndTest`. La primera petición tras arrancar la aplicación puede superar los 2 s por el arranque en frío; volver a correr la carpeta.

La carpeta `Firebase no disponible (manual)` tiene una sola petición: 503 `DEPENDENCY_UNAVAILABLE` en menos de 9 s.

## Cómo correrla

Requisitos: la aplicación en marcha (`README.md` de la raíz, sección «Ejecución local»), con el emulador de Firebase Auth y su base de datos, y Node.js para Newman.

```powershell
npx newman run postman/cameia-cuentas.postman_collection.json -e postman/local.postman_environment.json --folder Registro --reporters cli,junit --reporter-junit-export target/newman.xml
```

- La carpeta de activación se corre con `--folder "Activación"` y `--reporter-junit-export target/newman-activacion.xml`; no necesita el broker.
- La carpeta de eventos se corre aparte, con la contraseña del broker por variable (no se versiona):

  ```powershell
  npx newman run postman/cameia-cuentas.postman_collection.json -e postman/local.postman_environment.json --folder "Eventos de cuenta (local)" --env-var rabbitPassword=<la del .env> --reporters cli,junit --reporter-junit-export target/newman-eventos.xml
  ```

- La carpeta de la consulta se corre con `--folder "Mi cuenta"` y `--reporter-junit-export target/newman-mi-cuenta.xml`; no necesita el broker.
- La carpeta de cierre de sesión se corre con `--folder "Cerrar sesión en todos los dispositivos"` y `--reporter-junit-export target/newman-logout.xml`; necesita el emulador de Firebase en `firebaseEmulatorUrl` y no necesita el broker.
- Para medir tiempos, `-n 100` repite la carpeta 100 veces; cada vuelta usa correos nuevos.
- Para importarla en Postman: **Import** con los dos archivos `.json`, y elegir el entorno `cameia-cuentas local`.

### La petición 503 (manual)

Debe correrse aparte, con Firebase inaccesible, para no afectar a las demás. Dos formas:

1. Detener el emulador de Firebase Auth y correr `--folder "Firebase no disponible (manual)"`. El emulador es compartido con los demás servicios: se vuelve a levantar al terminar.
2. Sin detener el emulador: arrancar otra instancia de la aplicación en otro puerto con `FIREBASE_AUTH_EMULATOR_HOST` apuntando a un puerto cerrado (por ejemplo `localhost:9`) y correr la petición con `--env-var baseUrl=http://localhost:<puerto>`.

## Datos de prueba

Los correos usan el dominio reservado `ejemplo.test` y la contraseña es una frase de prueba, sin valor real. Cada corrida deja cuentas pendientes en la base local y usuarios en el emulador; para empezar de cero se borran los dos como indica el `README.md` de la raíz.
