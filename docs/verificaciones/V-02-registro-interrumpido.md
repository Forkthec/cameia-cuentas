# V-02 · Registro interrumpido por el cliente

## Qué se quería saber

Si un registro puede quedar a medias, sin que la persona lo sepa, cuando el navegador se rinde a los 10 s mientras el servidor sigue trabajando y termina creando la cuenta; y cuánto tarda hoy un registro nuevo y uno repetido.

## Conclusión

1. **Se confirma el mecanismo.** Si el servidor tarda más de 10 s en responder, el cliente corta, pero el servidor termina y la cuenta queda creada. Repetir el mismo envío responde 200 con esa cuenta, sin crear nada nuevo. Con el registro repetido (200 con la cuenta pendiente), la persona puede reintentar sin error.
2. **Con los tiempos de espera de Firebase (3 s de conexión y 5 s de lectura), Firebase no puede retener un registro más de ~5 s.** Cuando la creación en Firebase no responde, el servicio devuelve 503 `DEPENDENCY_UNAVAILABLE` a los 5,1 s, tras borrar su propia credencial.
3. **El tiempo que puede pasar de 10 s es el arranque en frío**, no Firebase: en staging, el primer registro tras un periodo sin tráfico tardó **16,9 s**; los siguientes, entre 0,3 y 0,8 s. Es un solo dato de arranque en frío en staging, no una distribución.
4. **Hallazgo para Frontend.** El tiempo de espera del navegador para el registro no debe ser de 10 s. Con una observación de 16,9 s en frío, un valor de 30 s deja unos 13 s de margen. El valor lo decide Paula Andrea Muñoz Delgado.

## Versión y entorno

| Dato | Valor |
|---|---|
| Código probado | rama `develop`, commit `05bb23e` (incluye la credencial con `uid` propio, los tiempos de espera de Firebase y la respuesta 200 del registro repetido) |
| Aplicación | JAR empaquetado con `mvnw package`, perfil `local`, Java 21, puerto `8081` |
| Firebase | emulador de Firebase Auth (proyecto `demo-cameia`), no un proyecto real, salvo la medición de staging del punto 3 |
| Base de datos | PostgreSQL 16 desechable en contenedor |
| Pruebas | colección de Postman de esta carpeta `postman/`, con Newman 6.2.2 y Node.js 24, en Windows 11 |

## 1. Tiempos medidos (emulador y base local)

Tras 30 vueltas de calentamiento, 100 vueltas de la carpeta `Registro` (`-n 100`), 800 peticiones y 3.500 aserciones, sin fallos. Tiempo de respuesta total medido por Newman, en milisegundos:

| Operación | Observaciones | Mínimo | Mediana | p95 | Máximo |
|---|---|---|---|---|---|
| Registro nuevo (201) | 100 | 31 | 55 | 110 | 226 |
| Registro repetido (200: el mismo, con mayúsculas y espacios, y con otros datos) | 300 | 11 | 19 | 48 | 128 |
| Cuerpo inválido (422) | 100 | 4 | 8 | 22 | 42 |
| Correo de una cuenta activa (409) | 100 | 12 | 19 | 42 | 62 |

**Límite de comparación:** el p95 de las operaciones JSON propias debe ser de 2 s o menos. El p95 del registro nuevo, 110 ms, y el del repetido, 48 ms, lo cumplen con un margen de más de 18 veces.

**Tiempo en Firebase y tiempo propio.** Contra el emulador, con 100 observaciones de cada llamada que hace el servicio (cliente Python con conexión persistente, tras 30 de calentamiento):

| Llamada | Mediana | p95 | Máximo |
|---|---|---|---|
| Consulta de usuario por correo | 3,7 ms | 6,3 ms | 22,1 ms |
| Creación de usuario | 3,6 ms | 6,9 ms | 19,5 ms |

Con el emulador, Firebase pesa unos 4 ms de los 19 ms (repetido) o de los 55 ms (nuevo) medianos: casi todo el tiempo es propio (validación, consulta e inserción en la base de datos y serialización). **Un proyecto real de Firebase añade latencia de red que el emulador no tiene**; en staging, un registro repetido tardó 0,47 s y 0,81 s en una petición completa por el balanceador y el Gateway. Esa cifra no es una distribución.

**Arranque en frío.**

| Dónde | Medición |
|---|---|
| Local | La aplicación tardó 26,5 s en arrancar (`Started CuentasApplication in 26.477 seconds`). El primer registro después de arrancar tardó 2,93 s; los dos siguientes, 0,07 s |
| Staging (Cloud Run, por el balanceador y el Gateway) | El primer registro tras un periodo sin tráfico tardó 16,9 s; los siguientes, entre 0,3 y 0,8 s |

## 2. Mayúsculas y Unicode del correo

Se registró un correo y se consultó con otras grafías; el servicio normaliza el correo antes de consultar. Resultados con el emulador:

| Registro | Respuesta |
|---|---|
| `ana<n>@correo.co` (primera vez) | 201 |
| `ANA<n>@Correo.CO` | 200, el mismo `id` |
| `Ána<n>@correo.co` (primera vez; la tilde hace distinto el correo de `ana<n>`) | 201, otro `id` |
| `ána<n>@correo.co` | 200, el mismo `id` que `Ána<n>` |
| `ána<n>@correo.co` con la tilde descompuesta (NFD) | 200, el mismo `id` |
| `ána<n>@correo.co` con la tilde compuesta (NFC) | 200, el mismo `id` |

Las mayúsculas y la composición Unicode no crean cuentas distintas. Un correo con tilde es otro correo que uno sin ella, como corresponde.

## 3. Reproducción de la interrupción

Para observar el mecanismo se empaquetó una versión temporal del adaptador de Firebase que espera 11 s después de crear el usuario (sin los tiempos de espera del SDK de por medio). Esa modificación no se conserva ni se compromete. Con ella:

| Momento | Qué se vio |
|---|---|
| `curl --max-time 10 -X POST /api/v1/users` | El cliente cortó a los **10,0 s** sin respuesta (curl terminó con código 28, tiempo agotado) |
| Consulta SQL a los 10 s | Filas en la tabla de cuentas: 268 (la anterior a la prueba) |
| Consulta SQL a los ~14 s | **269**: la fila quedó creada, en estado `PENDING_VERIFICATION` |
| El mismo POST repetido | **200** con la cuenta recién creada (`id` y `firebaseUid`), en 0,05 s |
| Consulta SQL tras repetir | 269: no se creó otra fila |

## 4. Creación en Firebase sin respuesta (código real)

Con el código real, un proxy delante del emulador de Firebase Auth que retiene la respuesta de la primera creación:

| Petición | Respuesta |
|---|---|
| 1 (la creación no responde) | 503 `DEPENDENCY_UNAVAILABLE` a los **5,09 s**; el servicio borra su credencial antes de responder |
| 2 y 3 (el proxy solo retiene la primera creación) | 201 en 0,08 s y 0,09 s |

El servicio no deja una credencial sin cuenta cuando la creación no responde, y la respuesta llega antes de los 10 s del navegador.

## Qué queda sin verificar

- Los tiempos son del emulador y de una máquina de desarrollo, no de un proyecto real de Firebase. Las únicas cifras de staging son las del arranque en frío (una observación) y de dos registros repetidos.
- El arranque en frío de staging es una sola observación. Si el valor del tiempo de espera del navegador se quiere sustentar con una mediana, hace falta repetir la medición dejando el servicio sin tráfico entre cada una.
- La decisión sobre instancias mínimas para evitar el arranque en frío es de DevOps, por la tarea que abre Juan David Vela Coronado.
