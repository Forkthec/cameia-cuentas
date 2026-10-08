# Spec — CM-251-RegistroRepetido: registrarse de nuevo con una cuenta pendiente devuelve la cuenta existente

- **Tarea:** CM-251 · Subtarea «CM-241 – Backend: corrección del defecto» · padre CM-241 (Error DF-001, «Registro muestra "No hay conexión" pero la cuenta sí se crea», HU-1.1, staging, 24-sep-2026) · hermana de Frontend CM-250 · Sprint 2 · responsable: Paula Andrea Muñoz Delgado
- **Repositorio:** `cameia-cuentas`, rama `CM-251-registro-repetido-pendiente`, sobre `develop` (`e5e17e4`, con CM-36 completo fusionado)
- **Backlog vigente:** `05102026_01_Backlog.xlsx`, hoja `HE-01`: CA-1.1.30 (principal), CA-1.2.12 (remite a CA-1.1.30), CA-1.1.2, CA-1.2.8 y las reglas transversales RT-05 y RT-06; decisiones P-02 A y C-12 de «Cambios v4»
- **Estado:** spec, plan y tarjetas escritos; **pendiente de aprobación de Paula**
- **Atributos de calidad que toca:** los del anexo de restricciones y atributos de calidad de CAMEIA que la épica HE-01 declara (AC-0003 seguridad, AC-0004 fiabilidad, AC-0006 capacidad de interacción, FIA-02, IOP-04) más AC-0002 (desempeño) y los controles MAN-02, IOP-01 e IOP-02; la sección 8 bis dice cómo se mide cada uno.

## 1. Contexto y objetivo

Si el navegador no recibe la respuesta del registro (la conexión se corta, o `cameia-web` aborta a los 10 s y muestra «No hay conexión», CM-241), la cuenta ya existe completa en Firebase y en la tabla `cuenta`. El reintento de la persona con el mismo correo cae en `createUser` → `EmailAlreadyRegisteredException` → **409** «Ese correo ya tiene una cuenta.», y la persona cree que perdió su cuenta. El texto del CA-1.1.30 es: Cuentas responde con la cuenta existente, sin crear otra credencial ni otra fila; `cameia-web` muestra «Verifica tu correo» y reenvía el correo iniciando una sesión transitoria con la contraseña escrita; si esa contraseña no coincide, muestra «Ese correo ya tiene una cuenta.».

**Objetivo de Backend:**
1. Registrarse de nuevo con un correo cuya cuenta sigue `PENDING_VERIFICATION` responde **200 con la cuenta existente**, sin crear credencial ni fila y sin modificar nada.
2. Cualquier otro caso de correo existente responde **409** sin revelar el estado de la cuenta, con el mensaje en el campo `email`.
3. Ningún fallo previsible termina en el error genérico: Firebase lento o caído responde 503 con su código, y la credencial sin cuenta local deja un rastro que solo alerta cuando alguien debe actuar.
4. Los tiempos de espera de Firebase caben en el presupuesto del Gateway (30 s) para que el servicio responda antes de que nadie más se rinda.
5. Queda verificada la causa de CM-241 (V-02) y una colección de Postman reproduce cada caso.

Decisión de producto (P-02 A, 5-oct-2026): **Cuentas no comprueba la contraseña.** La comprueba `cameia-web` al reenviar el correo de verificación con una sesión transitoria. Se descartó verificar la contraseña con la API REST de Firebase (exigiría una clave web nueva y una tarea de DevOps). Detección de duplicados: por correo (único en Firebase) y por `firebase_uid` (único en `cuenta`); **sin** `Idempotency-Key` (C-12).

## 2. Alcance

Dentro: `POST /api/v1/users` (servicio, puerto de Firebase y su adaptador, tiempos de espera, controlador, manejador de errores, doble de pruebas, OpenAPI), `docs/errores.md` y un ADR, pruebas de servicio, de adaptador, de controlador y de punta a punta (incluida la concurrencia), la verificación V-02 y la carpeta `postman/` del repo.

Fuera: sección 14.

## 3. Situaciones y respuesta

Todas parten de un cuerpo válido (S7 es la excepción). El correo ya llega normalizado (recortado, NFC, minúsculas) por `EmailAddress`, así que la consulta y la creación usan el mismo valor.

| # | Situación | Respuesta | Datos |
|---|---|---|---|
| S1 | No existe credencial para el correo | **201** con la cuenta nueva (CA-1.1.1) | Crea credencial, plan `FREE` y fila |
| S2 | Existe la credencial, está habilitada, y la fila de ese `firebase_uid` está `PENDING_VERIFICATION` (menos de 7 días, o 7 o más y aún sin purgar: la purga diaria de CM-179 borra las de más de 168 h) | **200** con la cuenta existente (CA-1.1.30) | No escribe nada; `fecha_creacion` y `fecha_actualizacion` no cambian; los datos del cuerpo se ignoran |
| S3 | Existe la credencial y la fila está `ACTIVE`, `DISABLED` o `ANONYMIZED` | **409** `EMAIL_ALREADY_REGISTERED`, el mismo cuerpo para los tres estados | No escribe nada |
| S4 | Existe la credencial y **no** hay fila | **409** `EMAIL_ALREADY_REGISTERED`. Si la credencial se creó hace **menos de 300 s**, es un registro en curso: el log del error es `WARN`. Si tiene 300 s o más, es una credencial huérfana: el log del error es `ERROR` con el `firebase_uid` para conciliación manual (riesgo aceptado: CA-1.3.9 salió del MVP) | No escribe nada; no se completa la fila |
| S5 | Firebase no responde al consultar, al crear o al escribir el plan (tiempo agotado, no disponible, error de su lado) | **503** `DEPENDENCY_UNAVAILABLE`, «Ocurrió un error. Inténtalo de nuevo.» (RT-05-CA01) | Sin datos a medias: la consulta ocurre antes de crear nada; la compensación existente cubre el resto |
| S6 | Dos peticiones simultáneas con el mismo correo | La primera **201**; la segunda **200** si la fila ya existe cuando la evalúa, o **409** (log `WARN`) si todavía no | Una sola credencial y una sola fila |
| S7 | Cuerpo inválido (cualquier validación de CM-36) con un correo que ya existe | **422**, igual que sin existir | Las validaciones corren antes de consultar a Firebase |
| S8 | Existe la credencial, la fila está `PENDING_VERIFICATION` y el usuario de Firebase está **deshabilitado** (`disabled = true`) | **409** `EMAIL_ALREADY_REGISTERED`, el mismo cuerpo que S3 | No escribe nada |

**Por qué S8 es 409 y no 200.** CA-1.1.2 pide el mismo 409 para la cuenta «bloqueada» sin revelar su estado; un usuario deshabilitado en Firebase es una cuenta bloqueada. Devolver 200 entregaría el `id` y el `firebaseUid` de una cuenta bloqueada y llevaría a la persona a pedir un correo de verificación que Firebase no le dejará usar. Con el 409 la persona va a «Iniciar sesión», donde HU-1.3 le dice «Esta cuenta está deshabilitada.».

## 4. Requisitos funcionales (EARS)

- **REQ-RR-01.** Cuando llegue un registro válido y Firebase no tenga una credencial con el correo normalizado, el servicio debe crear la credencial, el plan y la fila y responder 201 con `{id, firebaseUid, status:"PENDING_VERIFICATION", plan:"FREE"}`.
- **REQ-RR-02.** Cuando llegue un registro válido y Firebase tenga una credencial habilitada con ese correo y la fila `cuenta` con el mismo `firebase_uid` esté `PENDING_VERIFICATION`, el servicio debe responder 200 con `{id, firebaseUid, status:"PENDING_VERIFICATION", plan:"FREE"}` de **esa** cuenta, sin llamar a `createUser`, `assignFreePlanClaim` ni `deleteUser`, sin guardar ninguna fila y sin cambiar `fecha_creacion`, `fecha_actualizacion` ni `version`.
- **REQ-RR-03.** Cuando exista la credencial y la fila esté `ACTIVE`, `DISABLED` o `ANONYMIZED`, o la credencial esté deshabilitada en Firebase, el servicio debe responder 409 con `code` `EMAIL_ALREADY_REGISTERED` y el mismo mensaje y cuerpo en todos los casos, sin revelar el estado, y sin escribir nada.
- **REQ-RR-04.** Cuando exista la credencial y no exista la fila, el servicio debe responder 409 `EMAIL_ALREADY_REGISTERED`, no crear ni completar la fila y registrar **una sola línea**, la del manejador de errores, con `code`, `requestId` y el `firebase_uid`: en nivel `WARN` («registro en curso») si la credencial se creó hace menos de 300 s, y en nivel `ERROR` («credencial sin cuenta local; requiere conciliación manual») si se creó hace 300 s o más. El servicio no registra nada por su cuenta: la excepción lleva el `firebase_uid` y si requiere conciliación, y el manejador decide el nivel. La antigüedad sale de la fecha de creación que informa Firebase y se compara con un reloj inyectable.
- **REQ-RR-05.** Mientras el servicio consulte la credencial por correo, debe clasificar los fallos de Firebase igual que al crearla: indisponibilidad (`UNAVAILABLE`, `DEADLINE_EXCEEDED`, `INTERNAL`, cuota o límite de uso agotado `RESOURCE_EXHAUSTED`, o conexión fallida sin respuesta HTTP) → 503 `DEPENDENCY_UNAVAILABLE`, también al crear la credencial, escribir el plan y borrarla; `INVALID_EMAIL` del servicio → 422 `EMAIL_INVALID_FORMAT` en `email`; `USER_NOT_FOUND` → «no existe» (no es un fallo); cualquier otro rechazo → 500 `INTERNAL_ERROR` con el código del servicio solo en el log. Nunca se crea nada si la consulta falla.
- **REQ-RR-06.** Si la creación de la credencial falla porque el correo ya existe (otra petición la creó entre la consulta y la creación), el servicio debe volver a consultar **una sola vez** y aplicar REQ-RR-02 a REQ-RR-04; si la segunda consulta no encuentra la credencial, debe responder 409 `EMAIL_ALREADY_REGISTERED` sin reintentar la creación. Si la credencial que aparece tiene el `uid` que esta misma petición generó y pasó a `createUser` (el SDK de Firebase reintentó una creación que sí se completó), el servicio debe tratarla como propia y completar el registro (plan y cuenta local, con compensación si falla) y responder 201.
- **REQ-RR-07.** El servicio debe aplicar todas las validaciones del cuerpo (formato, edad, contraseña, nombres, celular, pronombre) **antes** de consultar a Firebase.
- **REQ-RR-08.** El servicio no debe comparar, enviar ni registrar la contraseña en la vía de un registro repetido (S2, S3, S4, S8).
- **REQ-RR-09.** Cuando atienda S2, el servicio debe registrar en nivel `INFO` «Registro repetido atendido con la cuenta pendiente del usuario {firebaseUid}» (solo el `firebase_uid`).
- **REQ-RR-10.** Mientras no haya cambios entre dos registros repetidos, ambas respuestas deben ser 200 con el mismo cuerpo (`id`, `firebaseUid`, `status`, `plan`). El encabezado `X-Request-Id` y el miembro `requestId` son propios de cada petición y no cuentan en la comparación.
- **REQ-RR-11.** El contrato de `POST /api/v1/users` debe documentar en OpenAPI las respuestas 200, 201, 409, 422, 500 y 503, aclarando que 200 y 201 son éxito y que el cuerpo es el mismo, con un ejemplo con `code` para cada error.
- **REQ-RR-12.** Ninguna respuesta ni registro debe incluir el correo, la contraseña ni el estado de una cuenta ajena.
- **REQ-RR-13.** En S2, los datos del cuerpo distintos de los de la cuenta existente (nombre, apellido, fecha de nacimiento, celular, pronombre, contraseña) deben ignorarse: ni se modifica la fila ni se cambia la credencial. Ya validados, no cuentan para decidir la respuesta.
- **REQ-RR-14.** El 409 `EMAIL_ALREADY_REGISTERED` debe agregar `errors: [{field:"email", code:"EMAIL_ALREADY_REGISTERED", message:"Ese correo ya tiene una cuenta."}]`, además de su `code` de nivel superior, su estado y su mensaje, que no cambian (CA-1.1.2: el mensaje va «en el campo Correo electrónico»).
- **REQ-RR-15.** Toda llamada a Firebase debe tener un tiempo de espera de **3 s** para conectar y **5 s** para leer la respuesta.
- **REQ-RR-16.** El repositorio debe incluir `postman/` con una colección v2.1 en UTF-8 y un entorno local sin secretos que reproduzcan los casos de la sección 11.
- **REQ-RR-17.** El catálogo `docs/errores.md` debe documentar cada código y cada ruta que esta tarea toca, y un ADR debe registrar el cambio aditivo del contrato (el 200 y el `errors` del 409).
- **REQ-RR-18.** Si la creación de la credencial termina con `DEPENDENCY_UNAVAILABLE`, el servicio debe intentar borrar la credencial con el `uid` que generó antes de responder 503; un borrado que tampoco responde debe dejar un `WARN` con el `uid`, sin el correo, y no cambiar la respuesta. Un borrado de una credencial que no existe no es un error.

## 5. Errores previsibles de esta tarea

Todo fallo previsible tiene su excepción, su código estable, su estado, su mensaje y su prueba. Ninguno termina en el genérico.

| # | Fallo | Excepción (existente salvo indicación) | Código | HTTP | Campo / mensaje | Prueba |
|---|---|---|---|---|---|---|
| E1 | Correo con cuenta no recuperable (S3, S4, S6 sin fila, S8) | `EmailAlreadyRegisteredException` | `EMAIL_ALREADY_REGISTERED` | 409 | `email` · «Ese correo ya tiene una cuenta.» (nuevo: `errors[]`) | servicio, controlador, manejador, punta a punta |
| E2 | Firebase no responde al consultar, crear o escribir el plan | `DependencyUnavailableException` | `DEPENDENCY_UNAVAILABLE` | 503 | — · «Ocurrió un error. Inténtalo de nuevo.» | adaptador, servicio, controlador, punta a punta |
| E3 | Firebase rechaza el correo como inválido al consultar | `InvalidEmailException` | `EMAIL_INVALID_FORMAT` | 422 | `email` · «Ingresa un correo electrónico válido.» | adaptador |
| E4 | Cuerpo inválido con correo existente | las del dominio y del contrato | las de CM-36 | 422 | uno por campo | servicio, punta a punta |
| E5 | Firebase rechaza la consulta por una configuración (cualquier otro rechazo) | `IllegalStateException` clasificada como fallo imprevisto | `INTERNAL_ERROR` | 500 | — · «Ocurrió un error. Inténtalo de nuevo.» | adaptador |
| E6 | Violación de `uq_cuenta_firebase_uid` | `DataIntegrityViolationException` | `INTERNAL_ERROR` | 500 | — | inalcanzable por esta vía (sección 6); la clasificación de restricciones ya la cubre |
| E7 | Base de datos caída al leer la fila | excepción de acceso a datos | `INTERNAL_ERROR` | 500 | — | **PENDIENTE de Paula** (pregunta 1) |

Códigos nuevos: ninguno. `DEPENDENCY_UNAVAILABLE` ya está publicado y sigue el vocabulario cerrado (`<SUJETO>_UNAVAILABLE`); un código publicado no se renombra ni se reutiliza con otro sentido.

## 6. Datos

- Sin migración: `uq_cuenta_firebase_uid` ya existe y la consulta es por esa clave (con índice).
- Se agrega una lectura a Firebase por registro (`getUserByEmail`); no hay cambios en la base.
- **Una violación de `uq_cuenta_firebase_uid` no puede producirse por esta vía.** El servicio nunca inserta una fila cuando la credencial ya existía; una credencial nueva siempre trae un `firebase_uid` nuevo, también tras una compensación. `CuentaConstraintsClassificationTest` ya clasifica la restricción como invariante interna: si ocurriera por otro motivo, responde `INTERNAL_ERROR` y el log lleva el nombre de la restricción. No se agrega captura específica (decisión D5).

## 7. Contrato

- Ruta y cuerpo de entrada sin cambios. La respuesta de éxito puede ser **200** o **201** con el mismo cuerpo `{id, firebaseUid, status, plan}`.
- El 409 mantiene `code`, estado y mensaje, y agrega `errors[{field:"email", code, message}]`. Es aditivo: quien lea solo `code` no nota nada.
- Consumidor: `cameia-web` (`register.api.ts` trata cualquier 2xx como éxito y el 409 como «Ese correo ya tiene una cuenta.»). Se avisa a Frontend de que 200 también es éxito, del `errors` del 409 y de los tiempos (sección 9).

## 8. Seguridad y calidad

- **Exposición en S2.** Cualquiera que conozca un correo con cuenta pendiente y envíe un cuerpo válido recibe el `id` y el `firebaseUid` de esa cuenta (identificadores internos, no secretos; no permiten actuar sin credenciales). Es lo que pide el CA y `cameia-web` los guarda en su modelo. **Decisión D2, aceptada por Paula el 6-oct-2026**; el riesgo se registra en la matriz de seguridad del PR. S8 no lo expone.
- **Enumeración de correos.** El 409 y el 200 revelan que un correo está registrado: aceptado por producto (CA-1.1.2); el control compensatorio es el límite de peticiones antes del Gateway. No se revela el estado (REQ-RR-03).
- **Contraseña.** No se compara (P-02 A): un tercero no obtiene nada que no tenga al enviar el correo, y no puede crear ni modificar nada por esta vía (REQ-RR-13).
- **OWASP API.** API1: el registro es público y no expone más que lo anterior; API3: DTO explícito, campos no esperados ignorados; API4: tiempos de espera y una consulta más por registro; API6: sin cambios de estado en S2 a S4 y S8; API8: sin trazas en respuestas; API10: lo que responde Firebase se clasifica antes de usarse.
- **Datos sensibles en logs:** solo `firebase_uid`; nunca el correo ni la contraseña. El mensaje de la excepción de librería no llega al cliente ni al log.
- **Consumo de recursos (API4).** El registro es público y ahora hace una consulta más a Firebase: un abuso gasta cuota. El control es el límite de peticiones antes del Gateway; si Firebase agota la cuota, el servicio responde 503 con su código y no 500.
- **Riesgo que no crea esta tarea: registro con el correo de otra persona.** Quien registra el correo de una víctima con una contraseña propia deja una cuenta pendiente con esa contraseña. Esta tarea no lo agrava: el 200 no cambia la contraseña ni los datos (REQ-RR-13) y `cameia-web` solo continúa si la contraseña escrita coincide con la de la credencial; si la víctima restablece su contraseña (HU-1.4), se revocan las sesiones previas. Queda registrado en la matriz de seguridad del PR; la mitigación de fondo (confirmar que quien se registra posee el correo antes de dejar utilizable la contraseña) es una decisión de producto que va a Vela.
- **Concurrencia:** sección 10; la unicidad la imponen Firebase (correo) y `uq_cuenta_firebase_uid`.

## 8 bis. Atributos de calidad de CAMEIA y su evidencia

Fuente: anexo «Restricciones y atributos de calidad de CAMEIA» (30-ago-2026). La épica HE-01 declara AC-0003, AC-0004, AC-0006, FIA-02 e IOP-04.

| Atributo o control | Qué exige | Cómo lo cumple esta tarea | Evidencia |
|---|---|---|---|
| FIA-01 (AC-0004) Un fallo conserva el último estado confirmado | Timeout o fallo no deja datos a medias | La consulta ocurre antes de crear; el resto lo cubre la compensación existente (S5) | Pruebas de servicio y de punta a punta con Firebase caído: cero filas, cero credenciales |
| FIA-02 (AC-0004) Repetir una operación no produce efectos adicionales | Cero efectos en 20 repeticiones por tipo | Un registro y 20 repeticiones del mismo cuerpo: una credencial, una fila, fechas y `version` iguales | Prueba de punta a punta de 20 repeticiones (tarjeta T-7) |
| IOP-04 / RT-06 (HE-01) Envíos repetidos | El backend no duplica la operación | Detección por correo y `firebase_uid` (C-12); doble envío simultáneo sin duplicar | Prueba de concurrencia (tarjeta T-8) |
| AC-0003 / SEG-04 Sin datos sensibles en logs | Cero contraseñas, tokens o datos personales | Solo `firebase_uid` y `requestId`; nunca correo ni contraseña | Pruebas de salida de log (T-5, T-6) |
| AC-0003 / SEG-02 Pruebas negativas | Accesos y entradas no autorizados rechazados | S3, S8 y cuerpo con datos ajenos sin efecto | Pruebas de servicio y de punta a punta |
| AC-0003 / SEG-01 ASVS Nivel 1 | Requisitos aplicables aprobados | Sección 8 y matriz del PR | Línea de la matriz en el PR |
| AC-0002 / DES-02 Desempeño | p95 ≤ 2 s en operaciones JSON propias, 100 observaciones tras calentamiento, separando latencia propia y externa | V-02 mide 100 registros nuevos y 100 repetidos con el emulador y reporta p95; el tiempo de Firebase se reporta aparte | Informe de V-02 |
| AC-0002 / DES-04 | 5xx propios < 1 % | La indisponibilidad de Firebase es externa y sale como 503 con su código; lo imprevisible sigue siendo 500 | Pruebas de los códigos |
| MAN-02 Adaptadores con pruebas de éxito, formato inválido, timeout y cuota agotada | 100 % de los adaptadores | La consulta por correo se prueba con: éxito, `INVALID_EMAIL`, tiempo agotado con un servidor mudo del SDK y cuota agotada (`RESOURCE_EXHAUSTED`) | `FirebaseUserDirectoryAdapterTest` y la prueba con el SDK |
| IOP-01 OpenAPI 100 % | Contrato documentado | REQ-RR-11 | OpenAPI revisado contra la spec |
| IOP-02 Integraciones encapsuladas en adaptadores | Firebase solo por su adaptador | La consulta nueva vive en el puerto y el adaptador; `LayeredArchitectureTest` en verde | Prueba de arquitectura |
| AC-0006 Capacidad de interacción | Errores comprensibles y estados perceptibles (RT-05) | Cada error trae mensaje literal del criterio y, en el 409, en su campo; el 503 usa el texto de RT-05-CA01 | Pruebas de los textos |
| MAN-01 Cobertura > 70 % (rúbrica) | Lo nuevo ≥ 90 % por la regla del backend | Medido con JaCoCo | Reporte de `clean verify` |

## 9. Tiempos de espera

**Cadena de tiempos vigente.** Cada eslabón debe esperar más que el que llama, para que la respuesta del de abajo llegue antes de que el de arriba se rinda:

| Eslabón | Tiempo | Origen |
|---|---|---|
| Llamada de Cuentas a Firebase | 3 s conectar y 5 s leer (REQ-RR-15) | `FirebaseConfiguration` |
| Registro completo en Cuentas | Sin fallos, unos segundos. Con Firebase degradado, el SDK reintenta hasta 4 veces cada llamada (503 y errores de conexión) con espera creciente, y cada intento usa 3 s + 5 s: una llamada puede tardar ≈ 48 s y las cuatro de un registro ≈ 190 s | `ApiClientUtils.DEFAULT_RETRY_CONFIG` del SDK, no configurable desde `FirebaseOptions` (D14) |
| Gateway hacia Cuentas | 30 s | `GATEWAY_TIMEOUT_MS` |
| Navegador hacia el Gateway | **10 s** | `httpClient.ts` de `cameia-web`, igual para todas las peticiones |

El eslabón roto es el último: el navegador se rinde a los 10 s, antes de que Cuentas termine y antes de que el Gateway pueda devolver su error. Con 5 s y 10 s de lectura en Firebase el problema era peor: cuatro llamadas podían pasar de 40 s y el Gateway cortaba antes del 503.

**Qué resuelve cada cambio.**
- Los 3 s y 5 s (Backend, esta tarea) hacen que, si Firebase está lento o caído, Cuentas responda un 503 con su código en vez de quedarse colgada, y que el peor caso quepa en los 30 s del Gateway.
- El 200 de esta tarea resuelve lo que el CA pide: si el navegador se rinde igual, el reintento no falla.
- Un tiempo de espera mayor en el navegador para el registro (Frontend, CM-250) resuelve el síntoma de CM-241: la persona ve el resultado real en el primer intento en lugar de un «No hay conexión» falso. No lo exige el CA, que acepta el aviso y el reintento (RT-05-CA02). Su costo es que, en el peor caso, la persona espera con el botón deshabilitado (RT-06-CA01) tanto como dure ese tiempo.
- El arranque en frío de Cuentas (`--min-instances=0`) es la causa más probable de superar los 10 s sin que Firebase esté lento; su solución es de DevOps (solicitud del 5-oct, punto 3) y V-02 la mide.

**El valor del navegador sale de la medición de V-02** (decisión de Paula, 8-oct-2026, opción b): la mediana con arranque en frío más un margen, y no más que lo que la persona tolera esperando con el botón deshabilitado. Mientras no se mida, el 200 garantiza que el reintento no falle. Con la medición, el valor y su razón van a Frontend (CM-250) antes del 9-oct.

## 10. Concurrencia

Dos peticiones A y B con el mismo correo nuevo:

| Orden | Resultado de B |
|---|---|
| A termina (fila guardada) antes de que B consulte | B consulta, encuentra la credencial y la fila pendiente → **200** (S2) |
| La creación de A se completa en Firebase, la respuesta se pierde y el SDK reintenta | Firebase responde «correo existe» al reintento; A consulta, ve su propio `uid` → completa el registro → **201** (D17) |
| B consulta antes de que A cree la credencial, y A crea primero | B recibe «correo existe» al crear → nueva consulta → credencial presente; si la fila ya existe → **200**, si aún no → **409** con `WARN` (REQ-RR-06) |
| A creó la credencial pero aún no guarda la fila, y B consulta por primera vez | B ve credencial sin fila de menos de 300 s → **409** con `WARN`, sin alarma de conciliación (REQ-RR-04) |
| A y B crean a la vez | Firebase acepta una; la otra recibe «correo existe» y sigue el caso anterior |
| La purga borra la credencial entre la consulta y la creación de B | B crea una nueva: es un registro nuevo válido (S1) |
| La purga borra la credencial justo después de que B leyó la fila | B responde 200 por una cuenta que está por desaparecer; se acepta, igual que la ventana de la purga (CM-179, D6): la persona se registra de nuevo y obtiene un 201 |

En ningún orden se inserta una fila para una credencial preexistente.

## 11. Casos borde recorridos (estándar §C)

| Grupo | Aplica | Caso literal y resultado |
|---|---|---|
| Presencia y texto del cuerpo | Sí, por S7 | Contraseña `123` con un correo existente → 422 `PASSWORD_TOO_SHORT`; el doble cuenta 0 consultas |
| Duplicados (mayúsculas, espacios) | Sí | 1.º `ana@correo.co`, 2.º `"  Ana@Correo.CO "` → 200 con el mismo `id` |
| Unicode | Sí | Correo con mayúscula no ASCII (`ÁNA@correo.co` y `ána@correo.co`) → el mismo correo normalizado; se comprueba contra el emulador |
| Estado | Sí | S2 a S4 y S8 con `PENDING_VERIFICATION`, `ACTIVE`, `DISABLED`, `ANONYMIZED` y usuario deshabilitado |
| Idempotencia | Sí | Un registro y 20 repeticiones del mismo cuerpo → 201 y 20 veces 200; todos con el mismo `id`; una credencial, una fila; `fecha_creacion`, `fecha_actualizacion` y `version` iguales |
| Datos distintos en el reintento | Sí | 2.º registro con nombre `Luz`, fecha `01/01/1990`, otra contraseña y celular distinto → 200 con el `id` original; la fila y la contraseña de Firebase no cambian |
| Concurrencia y doble envío | Sí | Dos hilos y 20 repeticiones: un 201 y un 200 o 409; nunca 500; una credencial y una fila |
| Credencial en creación | Sí | Credencial de hace 299 s sin fila → 409, la excepción no pide conciliación y el manejador registra `WARN`; de 300 s y de 301 s → 409, la excepción pide conciliación y el manejador registra `ERROR`; el registro lleva `code`, `requestId` y `firebase_uid` y no el correo |
| Falla parcial | Sí | La consulta falla: sin credencial ni fila. El plan falla tras crear: la compensación existente borra la credencial |
| Dependencias lentas, caídas o sin cuota | Sí | Servidor mudo → el adaptador real corta a los 5 s con 503; Firebase caído → 503 `DEPENDENCY_UNAVAILABLE`; cuota agotada (`RESOURCE_EXHAUSTED`) → 503 |
| Rechazos de Firebase | Sí | `INVALID_EMAIL` al consultar → 422 `EMAIL_INVALID_FORMAT`; `PERMISSION_DENIED` → 500 `INTERNAL_ERROR` con el código del servicio en el log |
| Secuencias combinadas | Sí | Registrar → activar → registrar de nuevo → 409; registrar → deshabilitar el usuario en Firebase → registrar → 409 |
| Propiedad, colecciones, paginación | No | Registro público sin identidad ni colecciones |
| Carga | No cambia | El tamaño del cuerpo es un hallazgo de CM-36 (pregunta 14 de su spec) |

## 12. Verificación V-02 (causa de CM-241)

**Qué se quiere saber.** Si el defecto nace porque el navegador se rinde a los 10 s mientras el servidor sigue trabajando y termina creando la cuenta.

**Cómo se comprueba, en local, sin cambiar código del repositorio:**
1. **Medir.** Con el emulador de Firebase Auth y la base local, tras calentar, se miden 100 registros nuevos y 100 registros repetidos (colección de Postman, con Newman) y se reporta mínimo, mediana y p95 de cada uno, separando el tiempo que pasa en Firebase del tiempo propio (DES-02); y una medición aparte del primer registro tras arrancar la aplicación (arranque en frío).
2. **Reproducir.** Con una subclase temporal del adaptador que espera 11 s al crear el usuario (descartada al terminar), una petición con `curl --max-time 10` se corta en el cliente. Se verifica con una consulta SQL que la fila **sí** quedó, y el mismo POST repetido responde 200 con la cuenta.
3. **Informar.** Tiempos medidos, qué vio el cliente y qué quedó en la base. Si se confirma, el hallazgo va a Frontend (CM-250: tiempo de espera de 30 s para el registro y, si el equipo lo decide, el texto «La solicitud tardó más de lo esperado. Inténtalo de nuevo.» como RT-05-CA04). Si no se confirma, se reporta lo observado y se detiene.

La verificación no cambia código por sí misma; los tiempos de espera de Firebase (REQ-RR-15) se aplican por su propio requisito.

## 13. Decisiones

| # | Decisión | Porqué | Alternativas descartadas | Decisión humana |
|---|---|---|---|---|
| D1 | Consultar con `getUserByEmail` **antes** de crear, en vez de crear y capturar el conflicto | Distingue «no existe» de «existe» sin depender de un error y evita escribir en Firebase en un reintento. El conflicto de `createUser` se conserva solo para la carrera (REQ-RR-06) | Solo capturar el conflicto de `createUser`; verificar la contraseña con la API REST de Firebase (P-02 A) | Producto: P-02 A (5-oct-2026). Técnica: aprobada por Paula el 7-oct-2026 (Q3) |
| D2 | El cuerpo del 200 es el de la cuenta existente, con `id` y `firebaseUid` | Lo exige el CA y lo guarda el frontend | Devolver solo `status` y `plan` | Aprobada por Paula (6-oct-2026) |
| D3 | La credencial sin fila responde 409 y se registra; no se completa la fila | Riesgo aceptado por el PO; completar la fila exigiría los datos del registro original y la contraseña no se verifica | Completar la fila con el nuevo cuerpo (cualquiera podría crear la fila de una cuenta ajena) | Producto: P-02 A. Nivel `ERROR`: aprobado por Paula (6-oct-2026) |
| D4 | El servicio devuelve `{cuenta, creada}` y el controlador elige 201 o 200 | El estado HTTP es de presentación; el servicio no conoce HTTP | Lanzar una excepción para el 200; devolver siempre 201 | Aprobada por Paula el 7-oct-2026 (Q3) |
| D5 | Sin captura de `DataIntegrityViolationException` | La vía nunca inserta para una credencial preexistente (sección 6); la restricción ya está clasificada como invariante interna | Capturar y releer (código inalcanzable que solo se probaría forzándolo) | Aprobada por Paula el 7-oct-2026 (Q3); ratificada con el estándar el 8-oct-2026 |
| D6 | La credencial sin fila se clasifica por su antigüedad (300 s) para elegir `WARN` o `ERROR` | Un registro en curso no es una huérfana: sin esto, el reintento inmediato del escenario de CM-241 dispararía una alarma de conciliación falsa. 300 s cubre el peor caso del servicio con los reintentos del SDK (cuatro llamadas de hasta ≈ 48 s; D14) y el 409 ya es la respuesta aceptada de la carrera (REQ-RR-06) | Releer la fila tras una pausa (agrega espera al hilo de la petición); `WARN` siempre (se pierde la alarma de las huérfanas); `ERROR` siempre (falsas alarmas) | Delegada por Paula en el estándar el 8-oct-2026; confirma al aprobar |
| D7 | El 503 reutiliza `DEPENDENCY_UNAVAILABLE` | Está publicado, documentado y sigue el vocabulario cerrado; un código publicado no se renombra. El mensaje es el de RT-05-CA01 | Crear `ACCOUNTS_PROVIDER_UNAVAILABLE` (rompe la forma `<SUJETO>_<CAUSA>` y exige ADR y aviso) | Delegada por Paula en el estándar el 8-oct-2026; sustituye el nombre propuesto en la revisión del 7-oct; confirma al aprobar |
| D8 | Tiempos de espera de 3 s para conectar y 5 s para leer, en todas las llamadas a Firebase | Un intento cabe en los 30 s del Gateway; con 10 s de lectura no cabían ni dos. Los reintentos del SDK se tratan en D14 | Dejar 5 s y 10 s | Aprobada por Paula el 7-oct-2026 (Q1) |
| D9 | El 409 agrega `errors[]` con el campo `email` | CA-1.1.2 pide el mensaje en el campo; es aditivo | Dejar el 409 sin `errors` | Decidida por Paula en la revisión final de CM-36 (7-oct-2026) |
| D10 | S8 (usuario deshabilitado en Firebase con fila pendiente) responde 409 | CA-1.1.2 incluye la cuenta bloqueada; evita exponer identificadores de una cuenta bloqueada | 200 (la fila manda) | Delegada por Paula en el estándar el 8-oct-2026; confirma al aprobar |
| D11 | Los datos de un segundo registro se ignoran (REQ-RR-13) | La contraseña no se verifica: aceptar cambios permitiría a un tercero modificar una cuenta ajena | Actualizar los datos | Aprobada por Paula el 7-oct-2026 (Q2) |
| D12 | `postman/` en el repo, con colección y entorno sin secretos | Evidencia del PR reutilizable por Tester | Colección fuera del repo | Aprobada por Paula el 7-oct-2026 (Q7) |
| D13 | La purga no se vuelve a consultar antes de responder 200 | No se puede cerrar la ventana (la purga puede correr justo después); es la misma que la purga acepta (CM-179, D6) | Releer la fila (una lectura más sin cerrar la ventana) | Delegada por Paula en el estándar el 8-oct-2026; confirma al aprobar |
| D14 | El umbral de la credencial sin fila es de 300 s, no de 60 s | El SDK de Firebase reintenta cada llamada hasta 4 veces ante un 503 o un error de conexión, con espera de hasta 60 s, y no ofrece configurarlo: una llamada puede durar ≈ 48 s y un registro de cuatro llamadas ≈ 190 s. Con 60 s, un registro lento todavía en curso se clasificaría como residuo y dispararía una alarma falsa. Un residuo real se alerta 5 min después, que es aceptable porque solo cambia el nivel del registro | Limitar el tiempo desde Cuentas con un hilo aparte (no cancela la escritura en Firebase y dejaría estado a medias); sustituir el cliente de Auth del SDK (código propio sin necesidad) | Decidida por Paula el 8-oct-2026 en la revisión del PR A («solucionar») |
| D15 | El 200 conserva `id` y `firebaseUid` en el cuerpo | No son credenciales (la identidad sale del token de Firebase); que el correo está registrado ya lo revelan los 409 (CA-1.1.2); quien se registró ya los recibió. Quitarlos rompería el contrato tipado en `cameia-web` y exigiría avisar a Frontend | Devolver solo `status` y `plan` en el 200 | Decidida por Paula el 8-oct-2026 (revisión del PR A) |
| D16 | La credencial huérfana (sin cuenta local, con la compensación fallida) sigue en 409 y se concilia en la purga de cuentas sin verificar | El 409 es la respuesta aceptada por el PO (P-02 A); borrar la credencial al reintentar es destructivo y puede equivocarse con un registro lento; un código nuevo no desbloquea a la persona | Código propio `REGISTRATION_INCOMPLETE`; borrado automático al reintentar | Decidida por Paula el 8-oct-2026 (revisión del PR A). Destino: pregunta 16 de la spec de verificación de correo (bloque 2) |
| D17 | El servicio genera el `uid` de la credencial (32 caracteres hexadecimales aleatorios) y lo pasa a `createUser`; un conflicto con el `uid` propio se toma como credencial propia | Es la única forma de distinguir «el SDK reintentó mi creación» de «otra petición creó el mismo correo»: la fecha de creación no lo distingue y tratar como propia a la de otra petición haría que la perdedora borrara la credencial de la ganadora al compensar. Cubre el reintento del SDK tras un 503. La huérfana más probable tras bajar la lectura a 5 s (D8) es otra: la creación que Firebase completa y cuya respuesta no llega; el SDK no la reintenta (comprobado con el emulador) y la cubre REQ-RR-18, que borra la credencial propia | Tratar como propia toda credencial creada tras iniciar la petición; documentar la huérfana y dejarla a la purga | Aprobada por Paula el 8-oct-2026; REQ-RR-18 se agregó tras la prueba de punta a punta con el emulador |
| D18 | Una credencial sin cuenta local con fecha de creación desconocida cuenta como antigua (alarma de conciliación) | Firebase siempre informa la fecha; si faltara, una alarma de más cuesta menos que una huérfana silenciosa para siempre | Tratarla como reciente (`WARN` indefinido) | Aprobada por Paula el 8-oct-2026 |

## 14. Preguntas abiertas

| # | Pregunta | A quién | Recomendación | Bloquea |
|---|---|---|---|---|
| 1 | E7: si la base de datos no responde al leer la fila, el servicio responde 500 `INTERNAL_ERROR` igual que en el resto de sus endpoints. ¿Se crea una tarea aparte para un 503 de base de datos en todo el servicio? | Paula, con Vela para la tarea | Sí, tarea aparte: cambia a todos los endpoints y exige un código nuevo (por ejemplo `DATABASE_UNAVAILABLE`) y su ADR; no cabe en esta tarea | Nada de CM-251 |
| 2 | Tiempo de espera del navegador para el registro (CM-250) | Paula | **Respondida (Paula, 8-oct-2026): el valor que mida V-02** (opción b) | Nada de código; el aviso a Frontend lleva el valor medido |

## 15. Fuera de alcance

Completar la fila de una credencial huérfana · verificar la contraseña con la API REST de Firebase (descartado) · reenvío del correo de verificación y la sesión transitoria (`cameia-web`) · el tiempo de espera de 10 s del navegador y su texto (CM-250) · la purga de cuentas sin verificar (CM-179, bloque 2) · el límite de tamaño del cuerpo (CM-36, pregunta 14 de su spec) · un 503 por base de datos caída (pregunta 1) · colecciones de Postman de otros servicios.

## 16. Estimación

Dos PR, ambos bajo el tope de 1000 líneas, el segundo sobre el primero:
- **PR A (código):** puerto y adaptador, tiempos de espera, servicio con reloj, controlador, `errors[]` del 409, doble de pruebas, pruebas, `docs/errores.md` y ADR. ≈ 750 líneas, ≈ 6 h.
- **PR B (Postman y V-02):** `postman/` con colección y entorno, informe de V-02. ≈ 450 líneas, ≈ 2 h.

Total ≈ 8 h. La ficha original estimaba 1,5 h, antes de la consulta previa, el clasificador de antigüedad, los tiempos de espera, el `errors[]`, la concurrencia con base real, V-02 y Postman. **Al terminar se vuelve a estimar con las horas reales** y se informa a Vela.
