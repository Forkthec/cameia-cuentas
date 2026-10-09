# Pruebas de API de Cuentas (Postman y Newman)

Esta carpeta trae la colección de Postman del microservicio de Cuentas y el entorno local con el que se corre.

| Archivo | Contenido |
|---|---|
| `cameia-cuentas.postman_collection.json` | Colección v2.1: carpeta `Registro` (8 peticiones) y carpeta `Firebase no disponible (manual)` (1 petición) |
| `local.postman_environment.json` | Entorno local: solo `baseUrl` (`http://localhost:8081`, el puerto de `SERVER_PORT`). No lleva secretos |

## Qué verifica

Cada petición comprueba su estado y su cuerpo. Además, el script de la colección comprueba en **todas** las respuestas:

- `Content-Type` con `charset=utf-8`;
- en los éxitos, tipo JSON;
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

La carpeta `Firebase no disponible (manual)` tiene una sola petición: 503 `DEPENDENCY_UNAVAILABLE` en menos de 9 s.

## Cómo correrla

Requisitos: la aplicación en marcha (`README.md` de la raíz, sección «Ejecución local»), con el emulador de Firebase Auth y su base de datos, y Node.js para Newman.

```powershell
npx newman run postman/cameia-cuentas.postman_collection.json -e postman/local.postman_environment.json --folder Registro --reporters cli,junit --reporter-junit-export target/newman.xml
```

- Para medir tiempos, `-n 100` repite la carpeta 100 veces; cada vuelta usa correos nuevos.
- Para importarla en Postman: **Import** con los dos archivos `.json`, y elegir el entorno `cameia-cuentas local`.

### La petición 503 (manual)

Debe correrse aparte, con Firebase inaccesible, para no afectar a las demás. Dos formas:

1. Detener el emulador de Firebase Auth y correr `--folder "Firebase no disponible (manual)"`. El emulador es compartido con los demás servicios: se vuelve a levantar al terminar.
2. Sin detener el emulador: arrancar otra instancia de la aplicación en otro puerto con `FIREBASE_AUTH_EMULATOR_HOST` apuntando a un puerto cerrado (por ejemplo `localhost:9`) y correr la petición con `--env-var baseUrl=http://localhost:<puerto>`.

## Datos de prueba

Los correos usan el dominio reservado `ejemplo.test` y la contraseña es una frase de prueba, sin valor real. Cada corrida deja cuentas pendientes en la base local y usuarios en el emulador; para empezar de cero se borran los dos como indica el `README.md` de la raíz.
