# Spec — CM-251-RegistroRepetido: registrarse de nuevo con una cuenta pendiente devuelve la cuenta existente

- **Tarea:** CM-251 · Subtarea «CM-241 – Backend: corrección del defecto» · padre CM-241 (Error DF-001, «Registro muestra "No hay conexión" pero la cuenta sí se crea», HU-1.1, staging, 24-sep-2026) · hermana de Frontend CM-250 · Sprint 2 · responsable: Paula Andrea Muñoz Delgado
- **Repositorio:** `cameia-cuentas`, rama `CM-251-registro-repetido-pendiente`, creada desde `origin/develop` (`908112c`)
- **Backlog vigente:** `05102026_01_Backlog.xlsx`, hoja `HE-01`: CA-1.1.30 (principal), CA-1.2.12 (remite a CA-1.1.30), CA-1.1.2, CA-1.2.11 y las reglas transversales RT-05 y RT-06
- **Estado:** spec, plan y tarjetas escritos; **pendiente de aprobación de Paula**. Las preguntas 1 y 2 están respondidas (6-oct-2026); la 3 es de Vela y no afecta al código
- **Atributos de calidad que toca:** fiabilidad (un reintento no deja datos a medias ni falla), seguridad (no revelar el estado de otras cuentas; exposición de identificadores), compatibilidad de contrato (el registro puede devolver 200 además de 201), observabilidad (rastro de credenciales sin cuenta).
- **Orden:** el plan parte del estado de `develop` **después** de fusionar el PR 1A de CM-36 (formato de error con `code` y `ErrorCode`), que toca el mismo manejador (decidido por Paula el 6-oct-2026: no espera a 1B, 2 ni 3; si alguno se fusiona antes, se hace rebase); las rutas y líneas se revalidan al empezar.

## 1. Contexto y objetivo

Si el navegador no recibe la respuesta del registro (la conexión se corta, o `cameia-web` aborta a los 10 s y muestra «No hay conexión», CM-241), la cuenta ya existe completa en Firebase y en la tabla `cuenta`. El reintento de la persona con el mismo correo hoy cae en `createUser` → `EmailAlreadyRegisteredException` → **409** «Este correo ya se encuentra registrado», y la persona cree que perdió su cuenta.

**Objetivo de Backend:** que registrarse de nuevo con un correo cuya cuenta sigue `PENDING_VERIFICATION` responda **200 con la cuenta existente**, sin crear otra credencial ni otra fila y sin modificar nada; que cualquier otro caso de correo existente responda **409** sin revelar el estado de la cuenta; y que la causa de CM-241 quede verificada (V-02).

Decisión de producto ya tomada (P-02 A, 5-oct-2026): **Cuentas no comprueba la contraseña.** La comprueba `cameia-web` al reenviar el correo de verificación con una sesión transitoria; si no coincide, muestra «Ese correo ya tiene una cuenta.». Se descartó verificar la contraseña con la API REST de Firebase (exigiría una clave web nueva y una tarea de DevOps). Detección de duplicados: por correo (único en Firebase) y por `firebase_uid` (único en `cuenta`); **sin** `Idempotency-Key` (C-12).

## 2. Alcance

Dentro: `POST /api/v1/users` (servicio, puerto de Firebase y su adaptador, controlador, doble de pruebas, OpenAPI), pruebas de servicio, de adaptador, de controlador y de punta a punta (incluida la concurrencia), y la verificación V-02 de la causa de CM-241.

Fuera: ver sección 13.

## 3. Situaciones y respuesta

| # | Situación al llegar un registro válido | Respuesta | Datos |
|---|---|---|---|
| S1 | No existe credencial para el correo | **201** con la cuenta nueva (CA-1.1.1, sin cambios) | Crea credencial, plan `FREE` y fila |
| S2 | Existe la credencial y la fila `cuenta` de ese `firebase_uid` está `PENDING_VERIFICATION` (menos de 7 días, o 7 o más y aún sin purgar: la purga diaria de CM-179, bloque 2, borra las de más de 168 h, y desde entonces el correo es S1) | **200** con la cuenta existente (CA-1.1.30) | No escribe nada: ni credencial ni fila; `fecha_creacion` y `fecha_actualizacion` no cambian |
| S3 | Existe la credencial y la fila está `ACTIVE`, `DISABLED` o `ANONYMIZED` | **409** `EMAIL_ALREADY_REGISTERED`; el mismo cuerpo para los tres estados | No escribe nada |
| S4 | Existe la credencial y **no** hay fila | **409** `EMAIL_ALREADY_REGISTERED` y se registra el `firebase_uid` para conciliación manual (riesgo aceptado: CA-1.3.9 salió del MVP) | No escribe nada; no se completa la fila |
| S5 | Firebase no responde al consultar o al crear | **500** `INTERNAL_ERROR`, «Ocurrió un error. Inténtalo de nuevo.» (RT-05) | Sin datos a medias: la consulta ocurre antes de crear nada; la compensación existente cubre el resto |
| S6 | Dos peticiones simultáneas con el mismo correo | La primera **201**; la segunda **200** si la fila ya existe cuando la evalúa, o **409** si todavía no | Una sola credencial y una sola fila |
| S7 | Cuerpo inválido (cualquiera de las validaciones de CM-36) con un correo que ya existe | **422**, igual que sin existir | Las validaciones corren antes de consultar a Firebase |

## 4. Requisitos funcionales (EARS)

- **REQ-RR-01.** Cuando llegue un registro válido y Firebase no tenga una credencial con el correo normalizado, el servicio debe crear la credencial, el plan y la fila y responder 201 con `{id, firebaseUid, status:"PENDING_VERIFICATION", plan:"FREE"}`.
- **REQ-RR-02.** Cuando llegue un registro válido y Firebase tenga una credencial con ese correo y la fila `cuenta` con el mismo `firebase_uid` esté `PENDING_VERIFICATION`, el servicio debe responder 200 con `{id, firebaseUid, status:"PENDING_VERIFICATION", plan:"FREE"}` de **esa** cuenta, sin llamar a `createUser`, `assignFreePlanClaim` ni `deleteUser`, sin guardar ninguna fila y sin cambiar `fecha_creacion` ni `fecha_actualizacion`.
- **REQ-RR-03.** Cuando exista la credencial y la fila esté `ACTIVE`, `DISABLED` o `ANONYMIZED`, el servicio debe responder 409 con `code` `EMAIL_ALREADY_REGISTERED` y el mismo mensaje y cuerpo en los tres casos, sin revelar el estado, y sin escribir nada.
- **REQ-RR-04.** Cuando exista la credencial y no exista la fila, el servicio debe responder 409 `EMAIL_ALREADY_REGISTERED`, registrar en nivel `ERROR` una línea con el `firebase_uid` y la frase «credencial sin cuenta local; requiere conciliación manual», y no crear ni completar la fila.
- **REQ-RR-05.** Si la consulta de la credencial falla por una causa distinta de «usuario no existe», el servicio debe responder 500 `INTERNAL_ERROR` sin crear nada.
- **REQ-RR-06.** Si la creación de la credencial falla porque el correo ya existe (otra petición la creó entre la consulta y la creación), el servicio debe volver a consultar **una sola vez** y aplicar REQ-RR-02 a REQ-RR-04; si la segunda consulta no encuentra la credencial, debe responder 409 `EMAIL_ALREADY_REGISTERED` sin reintentar la creación. En el caso REQ-RR-04 de esta vía, la línea se registra en nivel `WARN` (es transitoria: la otra petición aún no guardó su fila).
- **REQ-RR-07.** Mientras el servicio atienda un registro, debe aplicar todas las validaciones del cuerpo (formato, edad, contraseña, nombres) **antes** de consultar a Firebase.
- **REQ-RR-08.** El servicio no debe comparar, enviar ni registrar la contraseña en la vía de un registro repetido (S2 a S4).
- **REQ-RR-09.** Cuando atienda S2, el servicio debe registrar en nivel `INFO` «Registro repetido atendido con la cuenta pendiente del usuario {firebaseUid}» (solo el `firebase_uid`).
- **REQ-RR-10.** Mientras no haya cambios entre dos registros repetidos, ambas respuestas deben ser 200 con el mismo cuerpo (`id`, `firebaseUid`, `status`, `plan`). El encabezado `X-Request-Id` y el miembro `requestId` (si existe) son propios de cada petición y no cuentan en la comparación.
- **REQ-RR-11.** El contrato de `POST /api/v1/users` debe documentar en OpenAPI las respuestas 200, 201, 409, 422 y 500, aclarando que 200 y 201 son éxito y que el cuerpo es el mismo.
- **REQ-RR-12.** Ninguna respuesta ni registro debe incluir el correo, la contraseña ni el estado de una cuenta ajena.

## 5. Datos

- Sin migración: `uq_cuenta_firebase_uid` ya existe y la consulta es por esa clave.
- Se agrega una lectura a Firebase por registro (`getUserByEmail`); no hay cambios en la base.
- Una violación de `uq_cuenta_firebase_uid` no puede producirse por esta vía: el servicio **nunca inserta** una fila cuando la credencial ya existía. Si ocurriera por otra causa, la compensación existente borra la credencial recién creada y el error sale como 500; no se agrega captura específica (justificado: inalcanzable y no hay un error de negocio que traducir).

## 6. Contrato

- Ruta y cuerpo sin cambios. La respuesta puede ser **200** o **201** con el mismo cuerpo `{id, firebaseUid, status, plan}`.
- Consumidor: `cameia-web` (`register.api.ts` trata cualquier 2xx como éxito y el 409 como «Ese correo ya tiene una cuenta.» —`SPEC.md` de auth, línea 696—). No hay que cambiar nada en Frontend; sí avisar de que 200 también es éxito.
- El mensaje del 409 pasa a «Ese correo ya tiene una cuenta.» en el PR 6 de CM-36; esta tarea no cambia textos. Por eso las pruebas de esta tarea afirman el estado y el `code` (`EMAIL_ALREADY_REGISTERED`), **nunca el texto literal del 409**: así no dependen de si el PR 6 de CM-36 ya está fusionado.

## 7. Seguridad y calidad

- **Exposición en S2.** Cualquiera que conozca un correo con cuenta pendiente y envíe un cuerpo válido recibe el `id` y el `firebaseUid` de esa cuenta (identificadores internos, no secretos; no permiten actuar sin credenciales). Es lo que pide el CA («responde con la cuenta existente») y `cameia-web` los guarda en su modelo. La alternativa (devolver solo `status` y `plan`) contradice el CA y rompe el tipo del frontend. **Decisión D2: aceptada por Paula el 6-oct-2026 (pregunta 1)**; el riesgo se registra en la matriz de seguridad del PR.
- **Enumeración de correos.** El 409 y el 200 revelan que un correo está registrado: aceptado por producto (CA-1.1.2); el control compensatorio es el límite de peticiones antes del Gateway. No se revela el estado (REQ-RR-03).
- **Contraseña.** No se compara (decisión P-02 A): un tercero no obtiene nada que no tenga al enviar el correo; no se puede crear ni modificar nada por esta vía.
- **ASVS 4.1.1/4.2.1 (sin cambios), API1 (autorización por objeto):** el registro es público y no expone datos más allá de lo anterior; **API6 (flujos sensibles):** sin cambios de estado en S2 a S4; **API4:** una consulta más a Firebase por registro, con el tiempo de espera del SDK.
- **Datos sensibles en logs:** solo `firebase_uid`; nunca el correo.
- **Rendimiento:** la consulta suma una llamada externa (≈ 100–300 ms) al registro nuevo; el registro pasa de 3 a 4 operaciones externas. V-02 mide el tiempo con el emulador.
- **Concurrencia:** ver sección 8; la unicidad la imponen Firebase (correo) y `uq_cuenta_firebase_uid`.

## 8. Concurrencia

Dos peticiones A y B con el mismo correo nuevo:

| Orden | Resultado de B |
|---|---|
| A termina (fila guardada) antes de que B consulte | B consulta, encuentra la credencial y la fila pendiente → **200** (S2) |
| B consulta antes de que A cree la credencial, y A crea primero | B recibe «correo existe» al crear → nueva consulta → credencial presente; si la fila ya existe → **200**, si aún no → **409** con `WARN` (REQ-RR-06) |
| A y B crean a la vez | Firebase acepta una; la otra recibe «correo existe» y sigue el caso anterior |
| La purga borra la credencial entre la consulta y la creación de B | B crea una nueva: es un registro nuevo válido (S1) |

En ningún orden se inserta una fila para una credencial preexistente; por eso no hay violación de unicidad que capturar (sección 5).

## 9. Casos borde recorridos (estándar §C)

| Grupo | Aplica | Cómo se cubre |
|---|---|---|
| Presencia y texto del cuerpo | Sí, por S7 | Las validaciones son las de CM-36; aquí solo se prueba que corren antes de consultar a Firebase |
| Duplicados (mayúsculas, espacios) | Sí | Correo `  Ana@Correo.CO ` y `ana@correo.co` son el mismo correo normalizado: el segundo es S2 |
| Estado | Sí | S2 a S4 con las cuatro situaciones de la fila (`PENDING_VERIFICATION`, `ACTIVE`, `DISABLED`, `ANONYMIZED`) |
| Idempotencia | Sí | REQ-RR-10: dos repeticiones seguidas dan 200 idénticos y `fecha_creacion` intacta |
| Concurrencia y doble envío | Sí | Prueba de punta a punta con dos hilos y 20 repeticiones |
| Falla parcial | Sí | Firebase falla al consultar (S5): sin credencial ni fila; la compensación existente se mantiene |
| Dependencias lentas o caídas | Sí | S5 con el doble que falla; la lentitud se reproduce en V-02 |
| Propiedad, colecciones, paginación | No | Registro público sin identidad ni colecciones |
| Carga | No cambia | El tamaño del cuerpo es un hallazgo de CM-36 (pregunta 14 de su spec) |

## 10. Verificación V-02 (causa de CM-241)

Hipótesis: `httpClient.ts` de `cameia-web` aborta a los 10 s y muestra «No hay conexión»; el registro encadena operaciones externas y el servidor termina igual, de modo que un arranque en frío puede superar 10 s. **Se reproduce en local**: con el emulador de Firebase Auth y un doble que espera 11 s al crear el usuario, una petición con `curl --max-time 10` se corta en el cliente y, aun así, la fila queda guardada; el reintento devuelve 200 con la cuenta. Si se confirma, el hallazgo se entrega a Frontend (CM-250: tiempo de espera y, si el equipo lo decide, un texto propio «La solicitud tardó más de lo esperado. Inténtalo de nuevo.» como RT-05-CA04). **No hay cambio de código por V-02.**

## 11. Decisiones

| # | Decisión | Porqué | Alternativas descartadas | Decisión humana |
|---|---|---|---|---|
| D1 | Consultar con `getUserByEmail` **antes** de crear, en vez de crear y capturar el conflicto | La consulta permite distinguir «no existe» de «existe» sin depender de un error y evita escribir en Firebase en un reintento. La captura del conflicto se conserva solo para la carrera (REQ-RR-06) | Solo capturar el conflicto de `createUser` (no distingue la carrera de un reintento y obliga a leer igual); verificar la contraseña con la API REST de Firebase (descartada por P-02 A) | Producto: P-02 A (5-oct-2026); técnica: PENDIENTE (Paula) |
| D2 | El cuerpo del 200 es el de la cuenta existente, con `id` y `firebaseUid` | Lo exige el CA y lo guarda el frontend | Devolver solo `status` y `plan` (contradice el CA) | Aprobada por Paula (6-oct-2026), pregunta 1 |
| D3 | Una credencial sin fila responde 409 y se registra en `ERROR`; no se completa la fila | Riesgo aceptado por el PO; completar la fila exigiría los datos del registro original y la contraseña no se verifica | Completar la fila con los datos del nuevo cuerpo (cualquiera podría crear la fila de una cuenta ajena) | Producto: P-02 A; nivel del registro: aprobado por Paula (6-oct-2026), pregunta 2 |
| D4 | El servicio devuelve un resultado `{cuenta, creada}` y el controlador elige 201 o 200 | El estado HTTP es de presentación; el servicio no conoce HTTP | Lanzar una excepción para el 200 (flujo de control con excepciones); devolver siempre 201 (contradice el CA) | PENDIENTE (Paula) |
| D5 | Sin captura de `DataIntegrityViolationException` | La vía nunca inserta para una credencial preexistente (sección 5) | Capturar y releer (código inalcanzable que no se podría probar sin forzarlo) | PENDIENTE (Paula) |

## 12. Preguntas abiertas

| # | Pregunta | A quién | Recomendación | Bloquea |
|---|---|---|---|---|
| 1 | D2: ¿el 200 devuelve `id` y `firebaseUid` de la cuenta existente a quien conozca el correo (literal del CA)? | **Respondida (Paula, 6-oct): sí, literal del CA**; el riesgo se registra en la matriz de seguridad del PR | Sí, literal; el riesgo es bajo (identificadores no secretos, solo cuentas pendientes de menos de 8 días) y se registra en la matriz de seguridad del PR | La forma del cuerpo del 200 (tarjeta T-2) |
| 2 | D3: ¿la credencial sin cuenta se registra en `ERROR` (alguien debe actuar) y la vía de carrera en `WARN`? | **Respondida (Paula, 6-oct): sí** | Sí | Solo el nivel del registro |
| 3 | ¿CM-251 es el hogar del CA-1.1.30? (Jira no tiene una «Ajustes v4 – Backend» de Cuentas para HU-1.1) | **No era pregunta: se comunica. CA-1.1.30 se registra en CM-251; se informa a Vela** | Sí, CM-251 | Dónde se registra y cierra el trabajo; nada de código |

### Pendientes que deja CM-36 (revisión final, 7-oct-2026)

- **El 409 en su campo** (decisión de Paula). CA-1.1.2 pide el mensaje «en el campo Correo electrónico». El 409
  `EMAIL_ALREADY_REGISTERED` conserva su estado y su `code` arriba (lo que Frontend ya lee) y **agrega**
  `errors: [{field: "email", code: "EMAIL_ALREADY_REGISTERED", message: "Ese correo ya tiene una cuenta."}]`, igual que los demás errores
  de un campo (D9 de CM-36). Es aditivo; se avisa a Frontend junto con el 200 de esta tarea.
- **Firebase caído responde 503, no 500.** S5 y REQ-RR-05 dicen 500 `INTERNAL_ERROR`, pero CM-36 fijó 503 `DEPENDENCY_UNAVAILABLE`
  (D10) y la regla de Paula es que ningún error previsible termine en el genérico. La consulta `getUserByEmail` debe usar la misma
  clasificación de `FirebaseUserDirectoryAdapter` (indisponibilidad solo sin respuesta HTTP; `USER_NOT_FOUND` es «no existe», no un fallo).
- **Credencial sin cuenta** (S4): CM-36 dejó en `docs/errores.md` que esta tarea agrega el registro del `firebase_uid` para conciliación.

## 13. Fuera de alcance

Completar la fila de una credencial huérfana · verificar la contraseña con la API REST de Firebase (descartado) · reenvío del correo de verificación y la sesión transitoria (`cameia-web`) · el tiempo de espera de 10 s del navegador y su texto (CM-250) · textos del 409 (CM-36, PR 6) · la purga de cuentas sin verificar (CM-179, bloque 2) · el límite de tamaño del cuerpo (CM-36, pregunta 14 de su spec).

## 14. Estimación

La ficha de análisis estimaba 1,5 h. Con la consulta a Firebase, el resultado `{cuenta, creada}`, las pruebas de las siete situaciones, la prueba de concurrencia con base real y V-02, la estimación honesta es ≈ **5 h** y ≈ 600 líneas de diff con pruebas, en un solo PR. Se informa a Vela.
