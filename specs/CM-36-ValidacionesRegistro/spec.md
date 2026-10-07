# Spec — CM-36-ValidacionesRegistro: el registro valida cada campo como exigen los criterios de HU-1.1

- **Tarea:** CM-36 · Subtarea · «HU-1.1 – Backend: estado inicial de la cuenta nueva» · padre CM-14 «HU-1.1 Registro de Nuevo Usuario» · Sprint 2 · responsable: Paula Andrea Muñoz Delgado
- **Repositorio:** `cameia-cuentas`, rama `CM-36-validaciones-registro`, creada desde `origin/develop` (`908112c`)
- **Backlog vigente:** `05102026_01_Backlog.xlsx`, hoja `HE-01`, HU-1.1 (44 criterios) y su apartado «Cambios v4» (5-oct-2026)
- **Estado:** spec completa de la tarea, **aprobada por Paula el 7-oct-2026** («apruebo el plan»), ya revisada con sus respuestas de ese día; el push y los PR siguen pidiéndose a Paula cada vez. Las preguntas de Paula están respondidas (sección 15); quedan abiertas las de Vela y DevOps, que solo afectan a textos y nombres de los bloques 3, 5 y 6 y al despliegue de 1B.
- **Atributos de calidad que toca:** seguridad (ASVS 6.2.4, 5.1, API3 y API6), compatibilidad de contrato (aditiva, con un cambio de textos), mantenibilidad y testabilidad (códigos de error y validación por capas), fiabilidad (sin 500 por entrada inválida).

## 1. Contexto y objetivo

HU-1.1 permite a un Invitado crear su cuenta con nombre, apellido, fecha de nacimiento, correo, contraseña, pronombres y celular
opcional. Cuentas valida, crea la credencial en Firebase Auth, guarda la cuenta en `cuenta` con estado `PENDING_VERIFICATION` y Plan
Free, y compensa si falla la base. Eso ya funciona y no se toca. Esta tarea es **la única subtarea de Backend de HU-1.1 que queda**
(CM-187 salió del sprint; el registro repetido es CM-251 y la edad, CM-254) y recoge las correcciones de código del registro que los
criterios del backlog v4 exigen, más el formato de error de Backend (`code`) que el estándar de Backend pide en la primera tarea de
código de cada servicio.

Objetivo de Backend: que `POST /api/v1/users` rechace con 422, en el campo correcto, con el mensaje literal del criterio y con un
`code` estable, todo dato que los CA declaran inválido; que acepte todo lo que declaran válido; y que nunca responda 500 por una entrada.

Qué ya cumple y no se toca (verificado en `908112c`): contraseña de 11, 12, 64 y 65 caracteres (se cuentan puntos de código), emoji
= 1 carácter, sin reglas de composición, edad en UTC (`AgePolicy`, `age > 110` rechaza 111 cumplidos y `age < 18` rechaza menores),
correo en minúsculas y recortado, celular opcional, la contraseña no se guarda ni se registra, la cuenta nace siempre
`PENDING_VERIFICATION`, compensación en Firebase si la base falla, campos desconocidos ignorados (probado: `{"estado":"ACTIVE"}` no
cambia nada), restricciones de la tabla `cuenta` (migración V1).

## 2. Alcance y bloques de PR

Una sola spec; seis bloques, cada uno un PR hacia `develop` de menos de 1000 líneas, en este orden. El **bloque 1 se divide en dos PR**
(1A y 1B) porque su estimación honesta supera 900 líneas con pruebas y documentación.

| Bloque | Qué entrega | Corrección (análisis) | CA que cierra |
|---|---|---|---|
| 1A | Formato de error con `code` en toda respuesta de error y en cada elemento de `errors[]`, `requestId`, `detail` fijo, 503 si Firebase no está disponible, códigos de 404, 405 y 415, registro seguro de cada error y clasificación de las restricciones de la base | H-14 de CM-283, estándar §3.6 | transversal (RT-01-CA05) |
| 1B | Fecha estricta · mensaje de contraseña común · pronombre obligatorio · restricción de la base para los pronombres · documentación OpenAPI del registro | 1, 2 (mensaje) y 3 | CA-1.1.8, 1.1.15, 1.1.27 (mensaje), 1.1.43 |
| 2 (mismo PR que 3) | Recorte y NFC de nombre, apellido y correo; cuenta de caracteres por puntos de código (incluida la contraseña); `EMAIL_TOO_LONG` en el borde; un mensaje por campo en el orden fijado | 5 | CA-1.1.9 a 1.1.13 (espacios), 1.1.16 a 1.1.19, 1.1.22, 1.1.42 |
| 3 (mismo PR que 2) | `PersonName` (solo letras) | 4 | CA-1.1.31, 1.1.39, 1.1.40 |
| 4 (dos PR) | 4a: código que carga y valida la lista · 4b: solo el archivo de datos de 3000 entradas (excepción al tope de 1000 líneas pedida por Paula el 7-oct-2026, porque es un archivo mecánico) | 2 (lista) | CA-1.1.27 (lista) |
| 5 | Celular con `libphonenumber` | 6 | CA-1.1.32, 1.1.37, 1.1.38, 1.1.29 |
| 6 | Etiqueta de campo y `code` en los 422 que hoy no lo tienen; textos del catálogo; correo duplicado; pruebas de edad y de los casos 1.1.41 y 1.1.42 | 7, 8, 9 | CA-1.1.2, 1.1.3 a 1.1.7, 1.1.20 a 1.1.26, 1.1.41 |

Fuera de alcance: ver sección 16.

## 3. Trazabilidad de los 44 CA

«Dueño»: F frontend, B backend, F y B ambos (columna «Técnico» del backlog). Los CA solo de F no tienen trabajo aquí.

| CA | Dueño | Estado en `908112c` | Brecha | Bloque |
|---|---|---|---|---|
| 1.1.1 registro exitoso | F y B | Cumple: 201, `PENDING_VERIFICATION`, plan `FREE` (`UserRegistrationControllerTest`, E2E) | Verificar lo guardado con `GET /api/v1/users/me` (CM-42, no existe aún) | — |
| 1.1.2 correo ya registrado | F y B | 409 con «Este correo ya se encuentra registrado» | Texto «Ese correo ya tiene una cuenta.»; mismo 409 con cuenta activa, bloqueada o anonimizada | 6 (+CM-251) |
| 1.1.3 cumple 18 mañana | F y B | `AgePolicy` correcto; mensaje «Debes ser mayor de edad» sin punto | Texto y prueba del borde | 6 |
| 1.1.4 cumple 18 hoy | F y B | Cumple | Prueba del borde con reloj fijo | 6 |
| 1.1.5 cumple 111 mañana | F y B | Cumple por la regla | Falta la prueba del borde | 6 |
| 1.1.6 111 cumplidos | F y B | 422 con «La fecha de nacimiento no es plausible, por favor verifícala» | Texto «Verifica tu fecha de nacimiento.» | 6 |
| 1.1.7 fecha futura | F y B | 422 «Fecha de nacimiento inválida» | Texto con punto final | 6 |
| 1.1.8 formato de fecha | B | **Defecto:** `31/02/2000` se guarda como 29/02/2000 | Fecha estricta con 422 en `birthDate` | **1B** |
| 1.1.9 / 1.1.10 nombre y apellido vacíos | F y B | 422 `firstName`/`lastName` con «Los nombres son obligatorios» | Texto del CA; vacío y solo espacios | 2 y 6 |
| 1.1.11 fecha vacía | F y B | 422 con «La fecha de nacimiento es obligatoria» (por `@NotNull`) | Texto; cadena vacía y solo espacios también son «vacía» | 1B (comportamiento) y 6 (texto) |
| 1.1.12 / 1.1.13 correo y contraseña vacíos | F y B | 422 con textos propios | Textos del CA | 6 |
| 1.1.14 confirmación vacía | F | No es parte del contrato (RT-01) | — | — |
| 1.1.15 pronombres sin elegir | F y B | **Defecto:** opcional, se acepta sin pronombre | 422 en `pronoun` | **1B** |
| 1.1.16 / 1.1.18 nombre y apellido de 120 | F y B | Se aceptan (`@Size(max=120)` cuenta unidades UTF-16) | Cuenta por puntos de código en NFC; prueba | 2 |
| 1.1.17 / 1.1.19 de 121 | F y B | 422 con «Los nombres no pueden superar los 120 caracteres» | Texto del CA y conteo correcto | 2 y 6 |
| 1.1.20 correo con formato inválido | F y B | `IllegalArgumentException` → 422 **sin `field`** | Etiqueta `email` y texto | 6 |
| 1.1.21 correo de 254 | F y B | Se acepta | Verificación V-01 (límite de Firebase) | 6 |
| 1.1.22 correo de 255 | F y B | 422 sin `field` | Etiqueta, código y texto | 2 |
| 1.1.23 a 1.1.26 contraseña de 11, 12, 64 y 65 | F y B | Cumple; textos sin punto | Textos del CA | 6 |
| 1.1.27 contraseña común | F y B | 32 entradas; mensaje informal («…común brother…») | Mensaje literal (1B) y lista de 3000 (4) | 1B y 4 |
| 1.1.28 confirmación distinta | F | — | — | — |
| 1.1.29 sin celular | F y B | Cumple | Prueba, incluidos solo espacios y país sin número | 5 |
| 1.1.30 registro repetido | F y B | 409 hoy | 200 con la cuenta existente | CM-251 |
| 1.1.31 / 1.1.39 / 1.1.40 nombre y apellido: solo letras | F y B | No se valida el contenido | `PersonName` | 3 |
| 1.1.32 / 1.1.37 / 1.1.38 celular | F y B | Solo regex E.164; `+571…` de cualquier país pasa | `libphonenumber` | 5 |
| 1.1.33 a 1.1.36 navegación y correo de verificación | F | — | — | — |
| 1.1.41 contraseña con espacios y emoji | F y B | Cumple sin prueba | Prueba | 6 |
| 1.1.42 correo con espacios y mayúsculas | F y B | Cumple sin prueba | Prueba (incluye NFC) | 2 y 6 |
| 1.1.43 cada opción de pronombres | F y B | Cumple sin prueba por opción | Una prueba por `HE`, `SHE`, `THEY` | 1B |
| 1.1.44 autorización de datos | F y B | — | **Sprint 3**, fuera | — |

## 4. Requisitos funcionales (EARS)

Convenciones: «el servicio» es `cameia-cuentas`; el cuerpo de error es `application/problem+json; charset=UTF-8`; «campo» es el
nombre del contrato JSON. Los mensajes van entre comillas y con su punto final, tal como los escribe el backlog.

### Bloque 1A — formato de error

- **REQ-RV-01.** Cuando el servicio responda cualquier error (4xx o 5xx), el cuerpo debe incluir el miembro `code` de nivel superior con un valor del catálogo de la sección 6, sin reemplazar `type`, `title`, `status`, `detail` ni `instance`.
- **REQ-RV-02.** Cuando el servicio responda 422 con una lista `errors`, cada elemento debe tener `field`, `code` y `message`, un elemento por campo (no dos para el mismo campo), `code` de nivel superior `VALIDATION_FAILED` y `detail` fijo «Revisa los campos marcados.». Esto incluye el error de dominio de un solo campo (edad, contraseña común): lleva `errors` de un elemento, nunca el código específico arriba y sin lista. El mensaje para la persona va en `errors[].message`; el `code` de cada elemento nunca es `VALIDATION_FAILED`.
- **REQ-RV-03.** Cuando el servicio responda un error, debe incluir el miembro `requestId` con el `X-Request-Id` recibido o, si falta o no cumple `^[A-Za-z0-9._-]{1,64}$`, un UUID v4 generado, y devolver el mismo valor en el encabezado `X-Request-Id`. (PD-08 respondida por Paula el 6-oct-2026: desde el PR 1A.)
- **REQ-RV-04.** Si un fallo no es de negocio, el servicio debe responder 500 con `code` `INTERNAL_ERROR` y el `detail` «Ocurrió un error. Inténtalo de nuevo.», sin traza, SQL, nombre de clase ni mensaje de excepción; el detalle va al log. (Texto del RT-05-CA01: hoy dice «No pudimos completar la operación. Inténtalo de nuevo en unos minutos».)
- **REQ-RV-05.** Mientras el servicio emita un código, el catálogo no debe reutilizar ni renombrar un código publicado; todo valor de `ErrorCode` debe cumplir `^[A-Z]+(_[A-Z]+)+$` y terminar en una causa del vocabulario cerrado del estándar (§A), con dos excepciones fijas y nombradas en la prueba: `VALIDATION_FAILED` e `INTERNAL_ERROR` (describen la operación entera, no una causa). La causa `TOO_COMMON` (contraseña común) se agrega al vocabulario con esta spec y al estándar de Cuentas.
- **REQ-RV-07 (trazabilidad y ningún error conocido en un código genérico).** Ningún código debe significar dos cosas: `VALIDATION_FAILED` solo acompaña a una lista `errors` no vacía y `INTERNAL_ERROR` solo lo emite el manejador de respaldo ante un fallo imprevisto. Todo fallo previsible tiene su excepción, su código específico y su prueba; si un fallo conocido llegara a `INTERNAL_ERROR` o `VALIDATION_FAILED`, es un defecto. Los fallos conocidos que hoy terminan en 500 y pertenecen a otra tarea quedan listados con su destino en `docs/errores.md` (sección «Respuestas publicadas que difieren del estándar»); esta tarea no agrega ninguno. `UntypedExceptionClassificationTest` exige que todo método que pueda lanzar una excepción sin código (cualquier `RuntimeException` que no sea de negocio, `Objects.requireNonNull` con mensaje y `Optional.orElseThrow()`; las que llegan al respaldo) esté clasificado como invariante defensiva, configuración al arrancar, fallo imprevisto o pendiente de otra tarea (D17). Un dato rechazado sin campo usa `REQUEST_INVALID_VALUE` (respaldo temporal que los bloques 3, 5 y 6 eliminan).
- **REQ-RV-06.** Si una restricción de Bean Validation del contrato no tiene `code` asignado, una prueba debe fallar. Si por un descuido llegara una sin código a producción, el elemento lleva `REQUEST_INVALID_VALUE` y se registra un `ERROR` con la restricción: nunca cae a `VALIDATION_FAILED`.
- **REQ-RV-08 (errores del propio framework).** Cuando la ruta no exista, el método HTTP no esté permitido, el tipo de contenido no sea compatible o el `Accept` no admita ningún tipo que el servicio produzca, el servicio debe responder con el formato de error del estándar y los códigos `ROUTE_NOT_FOUND` (404), `METHOD_NOT_ALLOWED` (405), `MEDIA_TYPE_NOT_ALLOWED` (415) y `MEDIA_TYPE_NOT_ACCEPTABLE` (406), nunca 500 ni la página de error de Spring. Texto de `detail` de cada uno: «No existe la ruta solicitada.», «Método no permitido.», «Tipo de contenido no admitido.» y «Tipo de respuesta no admitido.». El servicio solo produce JSON: `application/xml` también responde 406 (D16).
- **REQ-RV-09 (Firebase no disponible).** Cuando Firebase no responda o falle del lado del servidor al crear la credencial o escribir el claim del plan (error de transporte, `UNAVAILABLE`, `DEADLINE_EXCEEDED` o `INTERNAL` del SDK), el servicio debe compensar y responder 503 con `code` `DEPENDENCY_UNAVAILABLE` y el `detail` «Ocurrió un error. Inténtalo de nuevo.». Un rechazo de Firebase por un dato que pasó la validación propia no es indisponibilidad. Si Identity Toolkit responde `INVALID_EMAIL` al crear la credencial (el código va en el cuerpo de la respuesta; el SDK lo entrega como `INVALID_ARGUMENT` sin código de autenticación), el servicio debe responder 422 `EMAIL_INVALID_FORMAT` en `email` con el texto de CA-1.1.20 y registrar un `WARN` sin el correo, porque para la persona es un correo inválido (D14). Cualquier otro rechazo (`WEAK_PASSWORD`, `PASSWORD_DOES_NOT_MEET_REQUIREMENTS`, `OPERATION_NOT_ALLOWED`: una política o un proveedor configurados en Firebase) es un defecto de configuración: `INTERNAL_ERROR`, con el código del servicio en el log. Una causa de E/S cuenta como indisponibilidad solo si no hubo respuesta HTTP: ante un rechazo, el SDK también adjunta la respuesta como causa de E/S. No se distingue el tiempo agotado con 504 (el SDK no lo separa de forma fiable). El cliente de Firebase espera como máximo 5 s para conectar y 10 s para leer (pregunta 33); vencido cualquiera de los dos, el SDK informa `DEADLINE_EXCEEDED` y la respuesta es este 503.
- **REQ-RV-27 (registro de cada error).** Cada error se registra una sola vez, en el manejador, en español y sin datos personales: los 4xx de negocio en `WARN` sin traza con `code`, `requestId` y, en validación, la lista de nombres de campo (salen del contrato, nunca de lo que escribe quien llama); los 5xx y las compensaciones fallidas en `ERROR` con traza, `code` y `requestId`. `firebaseUid` se agrega solo cuando existe (en el registro anónimo que falla antes de crear la credencial no existe). Nunca se registran el correo, la contraseña, el valor de un campo ni el mensaje de una excepción de deserialización o de base de datos (puede citar el valor); de esas se registra la clase. El `requestId` solo entra al log después de cumplir la expresión de REQ-RV-03, para impedir inyección de líneas. Si la compensación falla (la base falló y Firebase no pudo borrar la credencial), se registra `ERROR` con `firebaseUid` y `requestId`, y hay una prueba.
- **REQ-RV-19 (base de datos).** Una violación de restricción de la base nunca devuelve el mensaje de la base ni se registra con él (puede contener el valor de la columna). El manejador responde 500 `INTERNAL_ERROR` y registra solo el nombre de la restricción, el SQLState (código de cinco caracteres, para diagnosticar una violación sin nombre de restricción), el `requestId` y la clase de la excepción. Cada restricción de la tabla `cuenta` está clasificada por escrito en la sección 7 y una prueba falla si aparece una restricción sin clasificar.

### Bloque 1B — tres correcciones de validación

- **REQ-RV-10 (fecha estricta, CA-1.1.8).** Cuando `birthDate` sea un texto que no sea una fecha real en el formato dd/mm/aaaa (dos dígitos de día, dos de mes y cuatro de año, sin signo ni espacios; así lo escriben CA-1.1.8 y RT-07, D15), el servicio debe responder 422 con un solo elemento `{field:"birthDate", code:"BIRTH_DATE_INVALID_FORMAT", message:"Formato de fecha inválido."}` y no debe llamar al caso de uso ni a Firebase.
  - Rechaza (cada valor es una prueba): `31/02/2000`, `29/02/2001`, `31/04/2000`, `15/13/2000`, `1/1/2000`, `00/01/2000`, `01/00/2000`, `abc`, `2000-01-01`, `12-04-1995`, `12/04/95`, ` 12/04/1995` (con espacio inicial), `12/04/1995 ` (con espacio final), `12/04/1995T00:00`, `20000101`, `01/01/-2000`, `01/01/+12345`, `12/04/10000`.
  - Acepta el formato (y luego decide la edad): `29/02/2000`, `12/04/1995`, `01/01/2000`, `29/02/1996`.
  - El año es el calendario (`YEAR`) con ancho fijo de 4 y sin signo, nunca el de la era (con resolución estricta exige la era y rechaza toda fecha). El patrón `uuuu` no sirve: admite `-2000` y `+12345`, que pasarían como fechas reales y la política de edad las rechazaría con otro código.
- **REQ-RV-11.** Cuando `birthDate` esté ausente, sea `null`, una cadena vacía o solo espacios, el servicio debe responder 422 con `{field:"birthDate", code:"BIRTH_DATE_REQUIRED"}` y el mensaje vigente («La fecha de nacimiento es obligatoria»; el texto del CA se aplica en el bloque 6 según la pregunta 2). La fecha de formato inválido y la vacía se distinguen: vacía → `BIRTH_DATE_REQUIRED`, con texto no vacío y mal formada → `BIRTH_DATE_INVALID_FORMAT`.
- **REQ-RV-12.** Cuando `birthDate` llegue como número, booleano, arreglo u objeto JSON, el servicio debe responder 422 sin llamar al caso de uso: los números y booleanos se leen como texto y fallan por formato (REQ-RV-10); el arreglo y el objeto fallan como cuerpo ilegible (REQ-RV-30).
- **REQ-RV-13 (contraseña común, CA-1.1.27).** Cuando la contraseña, comparada sin mayúsculas y con los extremos recortados, figure en la lista de contraseñas comunes, el servicio debe responder 422 con `{field:"password", code:"PASSWORD_TOO_COMMON", message:"Esta contraseña es demasiado común, elige otra."}`. La lista de este bloque es la vigente (32 entradas); el bloque 4 la sustituye. Datos de prueba: `123456789012`, `password1234`, `qwertyuiop123`, `PASSWORD1234` y `  password1234  ` (mismo resultado).
- **REQ-RV-14.** El mensaje de la excepción de contraseña común no debe contener la contraseña ni ningún texto informal.
- **REQ-RV-15 (pronombre obligatorio, CA-1.1.15).** Cuando `pronoun` esté ausente, sea `null`, una cadena vacía o solo espacios (pregunta 15), el servicio debe responder 422 con `{field:"pronoun", code:"PRONOUN_REQUIRED", message:"Selecciona una opción."}` y no debe llamar al caso de uso.
- **REQ-RV-16 (CA-1.1.43).** Cuando `pronoun` sea `HE`, `SHE` o `THEY`, el servicio debe crear la cuenta guardando exactamente ese valor en la columna `pronombres` (una prueba por valor).
- **REQ-RV-17.** El esquema debe repetir la regla: la columna `pronombres` solo admite `NULL`, `HE`, `SHE` o `THEY` (restricción `ck_cuenta_pronombres_valor`, migración V3). `NULL` se conserva porque la anonimización vacía el dato.
- **REQ-RV-18.** La documentación OpenAPI de `POST /api/v1/users` debe describir cada campo (descripción, límites, ejemplo y obligatoriedad) y cada respuesta (201, 409, 422, 500) con sus códigos de error.

### Bloque 2 — reglas comunes

- **REQ-RV-20.** Cuando `firstName`, `lastName` o `email` lleguen con espacios al inicio o al final, el servicio debe recortarlos antes de validar y de guardar. «Espacio» es lo que recorta `String.prototype.trim` de JavaScript en el cliente: los de `Character.isWhitespace`, los separadores de espacio de Unicode (categoría Zs, incluido el NBSP U+00A0) y U+FEFF; no lo son U+200B ni los caracteres de control no listados (pregunta 13). Un texto que queda vacío tras recortar cuenta como vacío (`FIRST_NAME_REQUIRED`, `LAST_NAME_REQUIRED`, `EMAIL_REQUIRED`). En el correo los espacios internos no se tocan; en `firstName` y `lastName` cada secuencia de espacios internos se reduce a un espacio (`María  José` → `María José`; decisión de Paula, 6-oct-2026). La contraseña no se recorta nunca.
- **REQ-RV-21.** Cuando un texto llegue en forma Unicode distinta de NFC (por ejemplo `e` + U+0301), el servicio debe normalizarlo a NFC antes de contar, validar y guardar.
- **REQ-RV-22.** Cuando se midan límites, «caracteres» son puntos de código Unicode del texto en NFC. Nombre y apellido: 1 a 120. Correo: 1 a 254. Contraseña: 12 a 64 (la contraseña se normaliza a NFC solo para contarla: a Firebase llega tal como se escribió). Si el nombre, el apellido o el correo superan el límite, el servicio debe responder 422 en su campo con `FIRST_NAME_TOO_LONG`, `LAST_NAME_TOO_LONG` o `EMAIL_TOO_LONG` («El correo no puede superar los 254 caracteres.»).
- **REQ-RV-23.** Cuando un campo incumpla varias reglas, el servicio debe devolver solo el primer mensaje, en este orden. Nombre y apellido: vacío, más de 120, caracteres no permitidos. Correo: vacío, más de 254, formato. Contraseña: vacía, menos de 12, más de 64, común. Fecha: vacía, formato, futura, menor de 18, más de 110.
- **REQ-RV-24.** Cuando `password` sea solo espacios (los de `String.isBlank`: espacio, tabulador, saltos de línea), el servicio debe tratarla como vacía (`PASSWORD_REQUIRED`); una contraseña formada solo por NBSP no cuenta como vacía y se mide como cualquier otra (diferencia aceptada con el cliente); una contraseña con espacios y otros caracteres (`mi clave larga 🙂`, 16 puntos de código) se acepta sin recortar (CA-1.1.41).
- **REQ-RV-25.** Cuando el correo recortado y en minúsculas ya exista, un segundo registro con `  Ana@Correo.CO ` y otro con `ana@correo.co` deben resolverse como el mismo correo (CA-1.1.42).

### Bloque 3 — `PersonName`

- **REQ-RV-31.** Cuando `firstName` o `lastName` (recortados y en NFC) contengan algún carácter que no sea una letra Unicode (`\p{L}`), una marca combinante (`\p{M}`), un espacio, el apóstrofo recto `'`, el apóstrofo tipográfico `’` o el guion `-`, o no contengan ninguna letra, el servicio debe responder 422 con `FIRST_NAME_INVALID_CHARACTERS` o `LAST_NAME_INVALID_CHARACTERS` y el mensaje «El nombre solo puede contener letras, espacios, apóstrofo y guion.» o «El apellido solo puede contener letras, espacios, apóstrofo y guion.».
  - Rechaza: `Ana3`, `Pérez_`, `---`, `'`, `Ana.`, `Ana@`, `<script>`, `Ana\u0000`, `12345`, `Ana–Luz` (guion largo).
  - Acepta: `María José`, `O'Neill`, `O’Neill`, `Gómez-Ruiz`, `Müller`, `Muñoz`, `A`, `B`, `Ñandú`, `María  José` (se guarda como `María José`), `李`, `Åsa`.
- **REQ-RV-32.** La regla debe vivir en el objeto de valor `PersonName` del dominio, probado sin Spring; el DTO no repite la expresión.

### Bloque 4 — lista de contraseñas

- **REQ-RV-40.** El servicio debe cargar la lista de contraseñas comunes desde un recurso versionado de 3000 entradas, cada una de 12 a 64 caracteres (los límites de `PasswordPolicy`), en minúsculas y sin duplicados, y compararla con la contraseña en minúsculas (`Locale.ROOT`) y recortada.
- **REQ-RV-41.** Si el recurso falta, está vacío, tiene menos de 3000 entradas o una entrada de menos de 12 o más de 64 caracteres, el servicio debe negarse a arrancar con un mensaje claro (falla al inicio).
- **REQ-RV-42.** El recurso debe ser el único origen de la lista y debe poder consumirlo `cameia-web` (pregunta 3: fuente, licencia y entrega).

### Bloque 5 — celular

- **REQ-RV-50.** Cuando `phoneNumber` llegue en E.164 sin espacios y sea un número válido según la versión Java de `libphonenumber`, el servicio debe guardarlo tal cual (`+573000000000`, `+34612345678`).
- **REQ-RV-51.** Cuando `phoneNumber` esté ausente, sea `null`, vacío o solo espacios, el servicio debe crear la cuenta sin celular (CA-1.1.29).
- **REQ-RV-52.** Cuando `phoneNumber` no sea un número válido (`12345`, `+57300`, `3000000000` sin `+`, `+57 300 000 0000` con espacios, `+99912345678`), el servicio debe responder 422 con `{field:"phoneNumber", code:"PHONE_NUMBER_INVALID_FORMAT", message:"Revisa el número, no coincide con el formato del país elegido."}`.
- **REQ-RV-53.** La restricción `ck_cuenta_telefono_e164` de la base debe aceptar todo valor que el servicio acepta. La migración V4 la cambia a `^\+[1-9][0-9]{5,14}$` (6 a 15 dígitos), porque hay números válidos de siete dígitos (+290, +683, +690) que la forma anterior (8 a 15) rechazaba (pregunta 32).

### Bloque 6 — etiquetas, textos y pruebas

- **REQ-RV-30.** Cuando el cuerpo no se pueda leer (JSON mal formado, un arreglo u objeto donde va un texto, `Content-Type` ilegible), el servicio debe responder 422 con `code` `REQUEST_BODY_INVALID_FORMAT` y el `detail` «Revisa el formato de los datos enviados.», sin el texto de la excepción de Jackson y sin la afirmación fija sobre el formato de la fecha que hoy incluye. (Pregunta 6: el estándar dice 400; el contrato publicado dice 422.)
- **REQ-RV-60.** Cuando `email` no tenga formato válido, el servicio debe responder 422 con `field:"email"`, `code` `EMAIL_INVALID_FORMAT` y el texto «Ingresa un correo electrónico válido.» (la longitud la resuelve el bloque 2).
- **REQ-RV-61.** Cuando `pronoun` sea un valor fuera de la lista (`OTRO`, `he`, `1`, `true`; la cadena vacía es REQ-RV-15), el servicio debe responder 422 con `field:"pronoun"` y `code` `PRONOUN_INVALID_VALUE`. Texto: «Selecciona una opción.» (RT-01).
- **REQ-RV-62.** Cuando el correo ya tenga credencial y la cuenta esté activa, bloqueada o anonimizada, el servicio debe responder 409 con `code` `EMAIL_ALREADY_REGISTERED` y el mensaje «Ese correo ya tiene una cuenta.», sin revelar el estado de la cuenta. La cuenta **pendiente de verificación no** responde 409: responde 200 con la cuenta existente (CA-1.1.30, tarea CM-251).
- **REQ-RV-63.** Los mensajes de los demás campos deben ser los del catálogo de la sección 6 (pregunta 2).
- **REQ-RV-64.** Cuando se envíen varios campos inválidos a la vez, el servicio debe responder un elemento por campo para los campos que fallan en la validación del borde (sintaxis, longitud, formato, caracteres); las reglas del dominio (edad, contraseña común, correo duplicado) se aplican después y de una en una (decisión D3, pregunta 7). Las reglas de forma escritas en el dominio (caracteres del nombre, formato del correo y del celular, longitud de la contraseña, opción del pronombre) son del borde: `@DomainRule` las ejecuta junto con Bean Validation (D18).
- **REQ-RV-65.** El valor numérico de un enumerado no debe aceptarse (hoy `"pronoun":1` se lee como `SHE`): el servicio debe responder 422 `PRONOUN_INVALID_VALUE`.

## 5. Campo por campo (contrato final del registro)

| Campo | Obligatorio | Normalización | Reglas (en orden) | Código · estado · mensaje |
|---|---|---|---|---|
| `firstName` | Sí | recorte + NFC | vacío · más de 120 · solo letras | `FIRST_NAME_REQUIRED` 422 «Ingresa tu nombre.» · `FIRST_NAME_TOO_LONG` 422 «El nombre no puede superar los 120 caracteres.» · `FIRST_NAME_INVALID_CHARACTERS` 422 «El nombre solo puede contener letras, espacios, apóstrofo y guion.» |
| `lastName` | Sí | ídem | ídem | `LAST_NAME_REQUIRED` «Ingresa tu apellido.» · `LAST_NAME_TOO_LONG` «El apellido no puede superar los 120 caracteres.» · `LAST_NAME_INVALID_CHARACTERS` «El apellido solo puede contener letras, espacios, apóstrofo y guion.» |
| `birthDate` | Sí | ninguna (texto exacto dd/mm/aaaa) | vacía · formato · futura · menor de 18 · más de 110 | `BIRTH_DATE_REQUIRED` «Ingresa tu fecha de nacimiento.» · `BIRTH_DATE_INVALID_FORMAT` «Formato de fecha inválido.» · `BIRTH_DATE_IN_THE_FUTURE` «Fecha de nacimiento inválida.» · `BIRTH_DATE_UNDERAGE` «Debes ser mayor de edad.» · `BIRTH_DATE_OUT_OF_RANGE` «Verifica tu fecha de nacimiento.» |
| `email` | Sí | recorte + NFC + minúsculas | vacío · más de 254 · formato | `EMAIL_REQUIRED` «Ingresa tu correo electrónico.» · `EMAIL_TOO_LONG` «El correo no puede superar los 254 caracteres.» · `EMAIL_INVALID_FORMAT` «Ingresa un correo electrónico válido.» · duplicado: `EMAIL_ALREADY_REGISTERED` 409 «Ese correo ya tiene una cuenta.» |
| `password` | Sí | ninguna (no se recorta; NFC solo para contar) | vacía (incluye solo espacios) · menos de 12 · más de 64 · común | `PASSWORD_REQUIRED` «Ingresa tu contraseña.» · `PASSWORD_TOO_SHORT` «La contraseña debe tener al menos 12 caracteres.» · `PASSWORD_TOO_LONG` «La contraseña no puede superar los 64 caracteres.» · `PASSWORD_TOO_COMMON` «Esta contraseña es demasiado común, elige otra.» |
| `pronoun` | Sí | ninguna | ausente, `null`, vacío o solo espacios · fuera de la lista | `PRONOUN_REQUIRED` «Selecciona una opción.» · `PRONOUN_INVALID_VALUE` «Selecciona una opción.» |
| `phoneNumber` | No | recorte | vacío = sin celular · formato | `PHONE_NUMBER_INVALID_FORMAT` «Revisa el número, no coincide con el formato del país elegido.» |
| `estado`, `plan` y cualquier otro | — | — | se ignoran (RT-01-CA06) | — |

Límites a probar por texto (n−1, n, n+1): nombre y apellido 119/120/121; correo 253/254/255; contraseña 11/12/13 y 63/64/65, siempre en puntos
de código y con una variante multibyte (121 `ñ`; 12 emojis son 12 caracteres; el mismo texto en NFD que en NFC mide igual).

Respuesta 201 (sin cambios): `{id, firebaseUid, status:"PENDING_VERIFICATION", plan:"FREE"}`; nunca el correo ni la contraseña.

Ejemplo de error de validación (formato final, 1B en adelante). El miembro `requestId` está desde el PR 1A (pregunta 4, PD-08).

```json
{
  "type": "about:blank",
  "title": "Datos no válidos",
  "status": 422,
  "detail": "Revisa los campos marcados.",
  "instance": "/api/v1/users",
  "code": "VALIDATION_FAILED",
  "requestId": "3c14c237-9fb8-4c25-86c6-cd81501d44f2",
  "errors": [
    { "field": "birthDate", "code": "BIRTH_DATE_INVALID_FORMAT", "message": "Formato de fecha inválido." }
  ]
}
```

## 6. Catálogo de errores del registro

Se agrega `ErrorCode` junto a las excepciones (`domain.exception`). Un código ↔ un estado ↔ un mensaje ↔ una excepción o restricción ↔ una prueba.
Los códigos nuevos son una **propuesta** que queda aprobada con esta spec; las causas usan el vocabulario cerrado del estándar (`OUT_OF_RANGE` en
lugar de la causa nueva «implausible»), con una causa nueva aprobada por Paula el 7-oct-2026: `TOO_COMMON` (`PASSWORD_TOO_COMMON`).

| Código | HTTP | Origen | Bloque que lo emite |
|---|---|---|---|
| `VALIDATION_FAILED` | 422 | lista `errors` no vacía | 1A |
| `FIRST_NAME_REQUIRED`, `FIRST_NAME_TOO_LONG` | 422 | `@NotBlank`, `@Size` de `firstName` | 1A (códigos); 2 (conteo) |
| `FIRST_NAME_INVALID_CHARACTERS` | 422 | `PersonName` | 3 |
| `LAST_NAME_REQUIRED`, `LAST_NAME_TOO_LONG`, `LAST_NAME_INVALID_CHARACTERS` | 422 | ídem de `lastName` | 1A, 2, 3 |
| `BIRTH_DATE_REQUIRED`, `BIRTH_DATE_INVALID_FORMAT` | 422 | restricciones de `birthDate` | 1A y 1B |
| `BIRTH_DATE_IN_THE_FUTURE`, `BIRTH_DATE_UNDERAGE`, `BIRTH_DATE_OUT_OF_RANGE` | 422 | `AgePolicy` (`InvalidBirthDateException.Reason`) | 1A |
| `EMAIL_REQUIRED` | 422 | `@NotBlank` de `email` | 1A |
| `EMAIL_TOO_LONG` | 422 | restricción de longitud de `email` | 2 |
| `EMAIL_INVALID_FORMAT` | 422 | `EmailAddress`; también el correo que Firebase rechaza (D14) | 6 y revisión final |
| `EMAIL_ALREADY_REGISTERED` | 409 | `EmailAlreadyRegisteredException` | 1A |
| `PASSWORD_REQUIRED` | 422 | `@NotBlank` de `password` | 1A |
| `PASSWORD_TOO_SHORT`, `PASSWORD_TOO_LONG`, `PASSWORD_TOO_COMMON` | 422 | `PasswordPolicy` (`WeakPasswordException`) | 1A |
| `PRONOUN_REQUIRED` | 422 | `@NotBlank` de `pronoun` (texto desde D18; antes `@NotNull` sobre el enumerado) | 1B |
| `PRONOUN_INVALID_VALUE` | 422 | valor fuera del enumerado | 6 |
| `PHONE_NUMBER_INVALID_FORMAT` | 422 | `PhoneNumber` | 5 |
| `REQUEST_BODY_INVALID_FORMAT` | 422 | cuerpo ilegible (hoy 422 sin código) | 1A (código) y 6 (texto) |
| `IDENTITY_REQUIRED` | 400 | encabezado de identidad ausente (`ServletRequestBindingException`) | 1A |
| `ACCOUNT_NOT_FOUND` | 404 | `AccountNotFoundException` (activación) | 1A |
| `EMAIL_NOT_VERIFIED` | 403 | `EmailNotVerifiedException` (activación; código reservado por el contrato) | 1A |
| `REQUEST_INVALID_VALUE` | 422 | `IllegalArgumentException` de un objeto de valor sin campo (respaldo; deja de emitirse en el bloque 6) | 1A |
| `INTERNAL_ERROR` | 500 | cualquier otro fallo imprevisto (incluye la violación de una restricción de la base, REQ-RV-19) | 1A |
| `DEPENDENCY_UNAVAILABLE` | 503 | `DependencyUnavailableException` (Firebase no disponible, REQ-RV-09) | 1A |
| `ROUTE_NOT_FOUND` | 404 | ruta inexistente | 1A |
| `METHOD_NOT_ALLOWED` | 405 | método HTTP no permitido | 1A |
| `MEDIA_TYPE_NOT_ALLOWED` | 415 | tipo de contenido no admitido | 1A |
| `MEDIA_TYPE_NOT_ACCEPTABLE` | 406 | el `Accept` no admite ningún tipo que el servicio produzca (causa nueva `NOT_ACCEPTABLE`) | revisión final |

En 1A, los códigos se agregan **sin cambiar el mensaje de ningún campo** de los vigentes. Cambian solo tres `detail`: el del 500 (texto literal del RT-05-CA01, REQ-RV-04), el de los 422 con `errors` (fijo, REQ-RV-02) y el del nuevo 503 (REQ-RV-09). Los textos del catálogo se aplican en el bloque 6 (pregunta 2, ya cerrada).
Las tres correcciones con texto literal en el backlog (formato de fecha, contraseña común y pronombre) usan su texto desde 1B.

Los códigos `IDENTITY_REQUIRED`, `ACCOUNT_NOT_FOUND` y `EMAIL_NOT_VERIFIED` **no los emite el registro**: los emite la activación (`POST /api/v1/users/me/verification`). Están en esta tabla porque el PR 1A asigna código a toda respuesta de error del servicio; sus cambios de comportamiento son de CM-179.

## 7. Datos

- Sin cambio de columnas. Todo límite validado ya tiene su `VARCHAR(n)`: `nombre`/`apellido` `VARCHAR(120)` (cuenta caracteres, no bytes), `pronombres` `VARCHAR(60)`, `telefono` `VARCHAR(16)` con `ck_cuenta_telefono_e164` (`^\+[1-9][0-9]{7,14}$` en V1; `^\+[1-9][0-9]{5,14}$` desde V4, REQ-RV-53), `estado` con `ck_cuenta_estado`. El correo no se guarda (es de Firebase).
- **Migración nueva V3** (bloque 1B): `ALTER TABLE microcuentas.cuenta ADD CONSTRAINT ck_cuenta_pronombres_valor CHECK (pronombres IS NULL OR pronombres IN ('HE','SHE','THEY'));`. Probada con `CuentaSchemaMigrationTest` (una fila con `OTRO` viola la restricción y la prueba comprueba el nombre de la restricción) y con el E2E. Riesgo: una fila existente con otro valor haría fallar el arranque; el código solo escribe el enumerado con `EnumType.STRING`, así que no debería existir. Acción para DevOps (vía Vela): confirmar `SELECT DISTINCT pronombres` en staging antes del despliegue.
- Restricción de caracteres de nombre y apellido: no se replica en la base (el estándar §B.2 pide `CHECK` para enums y rangos; una clase `\p{L}` no se puede expresar de forma equivalente en las expresiones regulares POSIX de PostgreSQL, que dependen del idioma de la base). Se mantiene `ck_cuenta_nombre`/`ck_cuenta_apellido` (`btrim <> ''`). Justificado.
- Índices y claves foráneas: sin cambios (la tabla no tiene FK a otro servicio; `uq_cuenta_firebase_uid` ya existe).
- **Clasificación de las restricciones de `cuenta` (REQ-RV-19).** Ninguna se puede violar hoy por una entrada de la persona, porque la validación del borde y del dominio actúa antes de llegar a la base; una violación sería un defecto y responde 500 `INTERNAL_ERROR` con el nombre de la restricción en el log (el estándar §6.3 pide un código específico, y aquí queda justificado por escrito: no hay un camino alcanzable que lo produzca). Clasificadas como invariantes internas: `cuenta_pkey`, `uq_cuenta_firebase_uid`, `ck_cuenta_nombre`, `ck_cuenta_apellido`, `ck_cuenta_estado`, `ck_cuenta_version`, `ck_cuenta_telefono_e164`, `ck_cuenta_pronombres_no_vacio`, `ck_cuenta_fecha_actualizacion`, `ck_cuenta_fecha_eliminacion`, `ck_cuenta_anonimizacion` y, desde 1B, `ck_cuenta_pronombres_valor`. `uq_cuenta_firebase_uid` pasa a alcanzable cuando CM-251 haga idempotente el registro: esa tarea le asigna su código (`EMAIL_ALREADY_REGISTERED` o el 200 según el caso). Una prueba con la base real (Testcontainers) lista las restricciones de la tabla y falla si alguna no está en esta clasificación.

## 8. Seguridad y calidad

- **ASVS 6.2.4 (contraseñas comunes):** la lista de 3000 y su comparación (bloque 4). **ASVS 6.2.1/6.2.2:** longitud 12–64 y sin reglas de composición: ya se cumple. **ASVS 5.1 / OWASP API3 (asignación masiva):** campos desconocidos ignorados, probado con `estado` y `plan`. **API6 (flujos sensibles):** el 409 revela si un correo existe, aceptado por producto (CA-1.1.2); el control compensatorio es el límite de peticiones antes del Gateway. **API8:** el `code` y el cuerpo no exponen trazas ni textos de librerías.
- **Datos sensibles en logs:** el registro de una entrada inválida no incluye el valor; `RawPassword.toString()` ya está oculto. Prueba: ninguna respuesta de error contiene la contraseña ni el correo enviados.
- **Concurrencia:** sin cambios (la unicidad la impone Firebase y `uq_cuenta_firebase_uid`; el registro repetido es CM-251).
- **Observabilidad:** `code` y `requestId` en la respuesta y en el log de cada 4xx (`WARN`, sin traza) y 5xx (`ERROR`, con traza).
- **Rendimiento:** la lista de 3000 se carga una vez al arrancar en un `Set`; la validación no hace E/S. La respuesta del registro depende de Firebase (fuera de alcance).
- **Atributos y evidencia:** seguridad → pruebas de cada rechazo y revisión `/security-review` del bloque 1B y 4; compatibilidad de contrato → pruebas del cuerpo de error con `code` y aviso a Frontend; testabilidad → reglas de dominio probadas sin Spring; fiabilidad → ninguna entrada produce 500.

## 9. Casos borde recorridos (estándar §C)

| Grupo | Aplica | Cómo se cubre |
|---|---|---|
| Presencia (ausente, `null`, vacío, solo espacios, tab y salto de línea) | Sí, cada campo | Una prueba por valor y campo; `\t` y `\n` cuentan como espacio |
| Texto (n−1, n, n+1; puntos de código; tildes, ñ, ü, emoji; combinantes; espacios; control; `<script>`) | Sí | Límites de la sección 5; REQ-RV-31 |
| Números | Solo `birthDate`/`pronoun` enviados como número | REQ-RV-12 y REQ-RV-65 |
| Fechas (31/02, bisiesto, hoy, mañana, borde de mes y año, UTC, reloj fijo) | Sí | REQ-RV-10; pruebas de edad con `Clock` fijo (bloque 6): cumple 18 hoy/mañana, cumple 111 hoy/mañana, nacido el 29/02 en año no bisiesto, 31/12 y 01/01 |
| Enumerados (válido, desconocido, minúsculas, vacío) | `pronoun` | `HE`, `SHE`, `THEY`, `OTRO`, `he`, `""` (obligatorio), `"   "` (obligatorio), `null`, `1`, `true` |
| Duplicados (mayúscula, tilde, espacios; concurrente) | Correo | CA-1.1.42, REQ-RV-25; concurrencia: CM-251 |
| Estado, propiedad, colecciones, paginación | No | Registro público sin identidad ni colecciones |
| Concurrencia (doble envío) | No en esta tarea | CM-251 (registro repetido) |
| Falla parcial | Sí | Ya cubierto por `RegisterUserServiceTest` (compensación); una validación que falla nunca llega a Firebase (`verify(directorio, never())`) |
| Dependencias (Firebase lento o caído) | Sí (REQ-RV-09) | 503 `DEPENDENCY_UNAVAILABLE` con compensación; un rechazo de Firebase por un dato propio sigue en `INTERNAL_ERROR`; V-07 comprueba qué lanza el SDK |
| Carga (cuerpo grande, campos extra, JSON mal formado, tipo de contenido erróneo) | Sí | Campos extra ignorados; JSON mal formado y `text/plain` → REQ-RV-30 y 415 existente; cuerpo grande: **hoy no hay límite** (`max-http-form-post-size` solo aplica a formularios): se corrige en una tarea aparte que se pide a Vela (pregunta 14) |

## 10. Reutilización

Se reutilizan y amplían: `BusinessException` y sus subclases, `InvalidBirthDateException.Reason`, `BusinessExceptionHandler` (métodos `problema` y `campo`),
`ProblemDetailTestSupport`, `PasswordPolicy`, `AgePolicy`, `EmailAddress`, `PhoneNumber`, `InMemoryFirebaseUserDirectory`, `UserRegistrationControllerTest`, `RegisterUserServiceTest`,
`PasswordPolicyTest`, `AgePolicyTest`, `ValueObjectsTest`, `CuentaSchemaMigrationTest`, `AccountRegistrationEndToEndTest`. Se **crean** (no servía lo existente): `ErrorCode`
(no existe un catálogo), el validador de formato de fecha (Bean Validation no trae fecha estricta con `dd/MM/uuuu`), `PersonName` (no existe un objeto de valor de nombre) y la carga de la
lista de contraseñas (el `Set.of` en código no escala a 3000 y no lo puede leer el frontend).

## 11. Matriz de pruebas

Por bloque, en `tasks.md` (una fila por valor literal). Resumen: dominio sin Spring (`PersonName`, `PasswordPolicy`, `AgePolicy`, `ErrorCode`), aplicación con el doble en memoria (el servicio
no llega a Firebase ante una validación fallida), controlador con el manejador real (`code`, `field`, `message`, `requestId`), esquema con Testcontainers (V3) y un E2E con puerto aleatorio por bloque.
Cobertura: ≥ 90 % de líneas y ramas de lo nuevo o modificado, medida con JaCoCo (`clean verify`); cada línea o rama sin cubrir se justifica en el PR.

## 12. Defectos que ya hay en el código que esta tarea toca

| # | Defecto | Evidencia | Destino |
|---|---|---|---|
| 1 | `31/02/2000` se guarda como 29/02/2000 (también `29/02/2001`→28/02 y `31/04/2000`→30/04) | Prueba desechable con la versión de Jackson del proyecto, 6-oct | 1B |
| 2 | Pronombre opcional en el registro | `RegisterUserRequest.java:55` (sin restricción) | 1B |
| 3 | Mensaje informal de contraseña común | `PasswordPolicy.java:75` | 1B |
| 4 | **Un número se acepta como pronombre**: `"pronoun":1` se lee como `SHE` (índice del enumerado) | Prueba desechable, 6-oct | 6 (REQ-RV-65) |
| 5 | Ningún error lleva `code` ni `requestId` | `BusinessExceptionHandler.java` | 1A |
| 6 | `valorInvalido(IllegalArgumentException)` devuelve a la persona el texto de **cualquier** `IllegalArgumentException`, también de librerías (viola «ningún mensaje de librería llega al cliente») y sin `field` | `BusinessExceptionHandler.java:163-166` | 3, 5 y 6 (cada objeto de valor lanza su excepción propia) |
| 7 | `cuerpoIlegible` afirma «La fecha de nacimiento usa el formato DD/MM/AAAA» ante cualquier cuerpo ilegible | `BusinessExceptionHandler.java:135` | 6 |
| 8 | El 500 dice «…en unos minutos» en vez del texto de RT-05 | `BusinessExceptionHandler.java:178` | 1A |
| 9 | Límites de nombre y correo cuentan unidades UTF-16 (un emoji vale 2) | `RegisterUserRequest.java:36,40`, `EmailAddress.java:39` | 2 |
| 10 | `[]` se lee como fecha ausente con `LocalDate` (Jackson desempaqueta el arreglo vacío) | Prueba desechable, 6-oct | Deja de ocurrir con el DTO de texto (1B) |
| 11 | Contraseña común: 32 entradas | `PasswordPolicy.java:41-49` | 4 |
| 12 | Texto de `RegisterUserRequest` dice que el pronombre es opcional y que la contraseña no tiene límite en el borde | Javadoc del registro | 1B (se reescribe) |
| 13 | Toda falla de Firebase en el registro (también la indisponibilidad) termina en `IllegalStateException` y por tanto en el 500 genérico | `FirebaseUserDirectoryAdapter.java:73-75, 94` | 1A (REQ-RV-09) |
| 14 | Una violación de restricción de la base cae al manejador genérico, que registra la excepción completa: el mensaje de PostgreSQL incluye el valor de la columna (dato personal en el log) | `BusinessExceptionHandler` (`falloInterno`) | 1A (REQ-RV-19) |
| 15 | Rutas inexistentes, método no permitido y tipo de contenido no admitido no pasan por el formato de error con `code` | por comprobar en 1A (T-1A.8) | 1A (REQ-RV-08) |

## 13. Decisiones

| # | Decisión | Porqué | Alternativas descartadas | Decisión humana |
|---|---|---|---|---|
| D1 | `birthDate` llega como **texto** y se valida con una restricción propia de formato estricto `dd/MM/uuuu`; el controlador lo convierte con `toCommand()` | La receta del análisis (`@JsonFormat` con `lenient=FALSE` sobre `LocalDate`) rechaza bien las fechas imposibles pero **trata la cadena vacía y los espacios como error de formato** (probado: `""` y `"   "` lanzan «not allowed because 'strict' mode», la misma excepción que un número). Eso impediría responder «vacía» (CA-1.1.11) distinto de «formato» (CA-1.1.8) sin leer el texto de la excepción de Jackson, que cambia entre versiones. Con texto, todo el borde pasa por Bean Validation: un elemento por campo, junto con los demás errores del borde, y el contrato JSON no cambia | Receta del análisis con distinción por mensaje (frágil); registrar un deserializador propio (más código y estado global); analizar la fecha dentro del dominio (el formato es sintaxis y llegaría después del resto de errores del borde) | Aprobada por Paula (6-oct-2026), pregunta 5 |
| D2 | `code` desde `ErrorCode` en `domain.exception`; el manejador asigna el `code` de cada restricción del borde con una tabla `campo + restricción` | Un solo lugar; una prueba falla si una restricción nueva no tiene código (REQ-RV-06) | Texto del código dentro del `message` de cada anotación (acopla mensaje y código); enumerado con el texto incluido (cambia los textos antes de la pregunta 2) | Aprobada por Paula (6-oct-2026), con la spec |
| D3 | Validación en dos fases: el borde devuelve todos sus errores a la vez; las reglas del dominio (edad, contraseña común, duplicado) se aplican después, de una en una | Es el diseño vigente y el más simple; el frontend ya valida todo antes de enviar. Agregarlo todo exigiría un validador que ejecute el dominio sin crear la cuenta | Agregar los errores de dominio a la lista (más código y el servicio dejaría de lanzar al primer fallo) | Aprobada por Paula (6-oct-2026), pregunta 7 |
| D4 | El cuerpo ilegible sigue en **422** | Es el contrato publicado que consume Frontend (jerarquía: contrato publicado primero); el estándar sugiere 400 | Cambiar a 400: rompe lo publicado sin pedirlo ningún CA | Aprobada por Paula (6-oct-2026), pregunta 6 |
| D5 | El pronombre obligatorio vive en la API; la columna sigue admitiendo `NULL` | La anonimización vacía el dato; el CA lo pide a nivel de API | `NOT NULL` en la base: rompe la anonimización | Aprobada por Paula (6-oct-2026), con la spec |
| D7 | El recorte y la normalización NFC viven en el objeto de valor `SingleLineText` del dominio; el DTO los aplica en su constructor compacto (antes de la validación) y la restricción `@CodePointSize` cuenta puntos de código | Una sola definición de «espacio» y de «carácter» para el borde y el dominio; `@NotBlank` ve el texto ya recortado (así un NBSP solo cuenta como vacío); `@Size` cuenta unidades UTF-16 y no sirve para el límite en puntos de código | Normalizar solo en el controlador (la validación vería el texto sin recortar); una clase de utilidades estáticas (prohibida por el estándar); copiar la lógica en cada validador | Aprobada por Paula (6-oct-2026), pregunta 13 |
| D6 | Bloque 1 en dos PR (1A formato de error, 1B correcciones) | Un solo PR superaría 900 líneas y mezclaría un refactor transversal con tres correcciones de comportamiento | Un PR único | Aprobada por Paula (6-oct-2026), con la spec |
| D8 | `VALIDATION_FAILED` e `INTERNAL_ERROR` quedan como excepciones fijas de la forma `<SUJETO>_<CAUSA>`, y ningún error conocido puede terminar en ellos (REQ-RV-05 y 07) | Son genéricos por definición (una agrupa causas, la otra es una causa desconocida); el estándar y el ADR 0001 los nombran así | Agregar `FAILED` y `ERROR` al vocabulario cerrado (es lo mismo escrito distinto); renombrarlos (rompe el estándar y el Gateway) | Paula, 7-oct-2026: «ok, pero no quiero que errores conocidos se metan en estos errores» |
| D9 | `detail` fijo en todo 422 con `errors`, también el de un solo campo; el error de dominio de un campo lleva `errors` de un elemento (REQ-RV-02). El ejemplo del estándar se corrige en el mismo PR 1A (copia de Cuentas y de la skill); Perfil, Gateway y Entrevista reciben el mismo archivo en el PR de cada uno | Un comportamiento único para Frontend; con varios errores no hay un mensaje único para `detail`. La copia del estándar debe ser idéntica en los cuatro repos: mientras los otros tres no la actualicen, Cuentas queda distinta y se declara en el PR | Mensaje del primer error en `detail` (dos comportamientos); código específico arriba y sin lista (Frontend leería dos formas); corregir el ejemplo en un PR aparte (descartado por Paula); no tocar el ejemplo hasta la verificación final del 23-oct | Paula, 7-oct-2026 (respuestas 1, 2 y 8: «estándar siempre», «texto fijo pero no un PR aparte», «A») |
| D10 | Firebase no disponible responde 503 `DEPENDENCY_UNAVAILABLE` con el texto de RT-05, no 500; sin 504 | Es más específico y es el mapa de estados del estándar; cambia el criterio del backlog (P-02 dice 500), que se comunica a Vela y a Frontend | 500 `INTERNAL_ERROR` como dice el CA; 504 para el tiempo agotado (el SDK no lo distingue de forma fiable) | Paula, 7-oct-2026 («entre más específico mejor»; comunicar) |
| D11 | Las restricciones de la tabla `cuenta` se clasifican por escrito como invariantes internas y no se traducen a códigos; el log lleva solo el nombre de la restricción; una prueba falla ante una restricción sin clasificar. `uq_cuenta_firebase_uid` recibe su código en CM-251, donde pasa a ser alcanzable | Ninguna es alcanzable por una entrada de la persona (la validación va antes); una tabla de traducción sería código muerto y el mensaje de la base contiene el valor de la columna | Tabla restricción → código en el manejador (el estándar dice que el manejador no inventa códigos); excepciones nuevas por restricción (≈ 40 líneas sin camino que las active) | Paula, 7-oct-2026 («lo mejor, más seguro y usable»); el criterio lo fijó Claude |
| D12 | Registro de errores según REQ-RV-27: WARN sin valores, solo `code`, `requestId` y nombres de campo; el mensaje de Jackson y el de la base nunca se registran; `firebaseUid` solo cuando existe | Seguridad ante todo: el endpoint es público y los mensajes de las librerías citan los valores enviados | Registrar el mensaje de la excepción para depurar mejor (filtraría la contraseña o el correo al log) | Paula, 7-oct-2026 («lo más seguro») |
| D13 | El bloque 4 va en dos PR: 4a el código y 4b solo el archivo de datos | El tope de 1000 líneas se aplica al código; el archivo de 3000 líneas es mecánico y se revisa como datos | Un solo PR de ≈ 3300 líneas; generar el archivo en el arranque desde una fuente externa (pierde la lista versionada que consume Frontend) | Paula, 7-oct-2026 («ok»). La excepción al tope de 1000 líneas del PR 4b es de Paula |
| D14 | Un correo que Firebase rechaza como dato inválido responde 422 `EMAIL_INVALID_FORMAT` en `email`, no 500 | Para la persona es un correo inválido (CA-1.1.20 y RT-01: mismas reglas y mensaje en cliente y servidor). REQ-RV-09 lo mandaba a `INTERNAL_ERROR`, y eso contradecía REQ-RV-07 y D8 («no quiero que errores conocidos se metan en estos errores»); la revisión de la spec no cruzó los tres textos. El SDK no tiene código propio: el `INVALID_EMAIL` del servidor llega como `INVALID_ARGUMENT` sin código de autenticación, igual que `WEAK_PASSWORD` u `OPERATION_NOT_ALLOWED`, así que el adaptador lee el código documentado en el cuerpo de la respuesta y solo `INVALID_EMAIL` es un correo inválido (corregido el 7-oct tras la revisión independiente: la primera versión trataba todo 400 como correo inválido). La misma revisión, con el SDK real ante un transporte simulado, encontró que todo rechazo HTTP respondía 503: el SDK adjunta la respuesta como causa de E/S y se confundía con una conexión fallida; corregido. Verificado el 7-oct con el SDK real ante un servidor que responde como Identity Toolkit (`400 INVALID_EMAIL`): el SDK lo entrega como `INVALID_ARGUMENT` sin código de autenticación y la persona recibe 422 en `email`, sin el correo en el log. Es una defensa: el emulador acepta todo correo con una arroba y no se conoce un correo que pase la regla propia y el Firebase real rechace | Dejarlo en `INTERNAL_ERROR` (contradice D8); tratar todo 400 como correo inválido (diría «correo inválido» ante un defecto de configuración); leer el mensaje que arma el SDK (cambia entre versiones; se lee el código documentado del cuerpo) | Paula, 7-oct-2026, revisión final («revisa qué preguntas se resuelven con los CA»: lo resuelve CA-1.1.20) |
| D15 | El año de `birthDate` tiene exactamente cuatro dígitos y sin signo | CA-1.1.8 y RT-07 dicen dd/mm/aaaa. Con `uuuu`, `01/01/-2000` y `01/01/+12345` pasaban como fechas reales y respondían `BIRTH_DATE_OUT_OF_RANGE` o `BIRTH_DATE_IN_THE_FUTURE` en vez de `BIRTH_DATE_INVALID_FORMAT`; REQ-RV-10 decía «4 o más dígitos» y no coincidía con el CA ni con el patrón OpenAPI (`\d{4}`) | Mantener `uuuu` y dejar que la edad rechace (otro código y otro mensaje que el del CA) | Lo resuelve CA-1.1.8 (revisión final, 7-oct-2026) |
| D16 | Código `MEDIA_TYPE_NOT_ACCEPTABLE` (406) con la causa nueva `NOT_ACCEPTABLE`; el servicio deja de producir XML | Con un `Accept` que el servicio no produce, la respuesta era 500 `INTERNAL_ERROR` (comprobado con `image/png`). Además, `jackson-dataformat-xml` llegaba por `firebase-admin` → `google-cloud-storage` (que el servicio no usa) y con `Accept: application/xml` el servicio respondía 200 en XML, fuera del contrato JSON: se excluye en el `pom.xml` | Reutilizar `MEDIA_TYPE_NOT_ALLOWED` con 406 (un código no significa dos cosas, REQ-RV-07); quitar el convertidor XML en la configuración de Spring (más código que una exclusión) | Paula, 7-oct-2026 (pregunta 4: «sí») y «haz todas las mejoras» |
| D17 | Prueba de guarda `UntypedExceptionClassificationTest`: todo método que puede lanzar una excepción sin código está clasificado (toda `RuntimeException` que no sea de negocio, `Objects.requireNonNull` con mensaje y `Optional.orElseThrow()`; por método, porque una clase puede tener motivos distintos) | Esas excepciones llegan al respaldo `INTERNAL_ERROR`. Es el mismo patrón que la clasificación de restricciones (D11): una clase nueva que lance una hace fallar la prueba hasta que alguien decida si es un defecto, una invariante, un fallo al arrancar o un caso previsible que necesita su código | Solo el inventario de `docs/errores.md` (depende de que alguien lo recuerde); prohibirlas (las invariantes defensivas y la configuración al arrancar las necesitan) | Paula, 7-oct-2026 («haz todas las mejoras») |
| D18 | Las reglas de forma del dominio se ejecutan en el borde con `@DomainRule`, que toma el código y el mensaje de la excepción del dominio; el pronombre llega como texto | La auditoría en vivo del 7-oct encontró que REQ-RV-64 no se cumplía: con varios campos inválidos, el formato del correo, la longitud de la contraseña, los caracteres del nombre, el formato del celular y la opción del pronombre salían de a uno y solo después de que el borde pasara. La regla sigue escrita una vez, en el dominio, que vuelve a aplicarla en el caso de uso. El pronombre pasa a texto por la misma razón que la fecha (D1): un valor fuera de la lista era un error de lectura de Jackson que ocultaba los demás; `JacksonConfiguration` (enumerados vacíos como ausentes y números rechazados) queda sin uso y se elimina | Copiar cada regla en una anotación de Bean Validation (dos fuentes de verdad); un validador de clase que construya el comando (mezcla todas las causas en una) | Paula, 7-oct-2026 («confirma que todo lo que piden los CA está resuelto»: lo exige REQ-RV-64, ya aprobado) |
| D19 | Un arreglo u objeto JSON donde va un texto (`firstName`, `lastName`, `email`, `password`, `birthDate`, `phoneNumber`) sigue respondiendo 422 `REQUEST_BODY_INVALID_FORMAT` sin campo, aunque oculte los demás errores; el pronombre es la excepción porque es una lista de opciones | Jackson corta la lectura del cuerpo entero ante un tipo incompatible, así que no hay «demás campos» que validar. Solo lo envía un cliente que no es el formulario, que siempre manda texto. Responder cada campo exigiría leer el cuerpo campo por campo y un código nuevo por campo, varios cientos de líneas para un caso que el formulario no produce. Es lo que ya decían REQ-RV-30 y REQ-RV-12 | Leer el cuerpo como árbol JSON y validar el tipo de cada campo con un código `<CAMPO>_INVALID_FORMAT` | Paula, 7-oct-2026 (hallazgo 3 de la revisión independiente: «mantenerlo pero documentar por qué») |

## 14. Verificaciones previas de Backend

- **V-01 (correo de 254 caracteres).** Comprobar con el emulador de Firebase Auth que `createUser` acepta un correo de 254 caracteres con parte local de hasta 64; si lo rechaza, se informa a Vela y no se recorta. Se hace en el bloque 6. **Resultado (7-oct):** el emulador acepta y guarda completo un correo de 254 caracteres. **Pendiente: verificar en staging con Firebase real** (proyecto de desarrollo, nunca producción) el correo de 254 y los dudosos `ana..perez@correo.co`, `.ana@correo.co`, `"ana"@correo.co`, `ñandú@correo.co` y uno con dominio `ñandú.co`; anotar qué responde Firebase y qué devuelve Cuentas, y borrar los usuarios de prueba. El emulador los acepta todos, así que no prueba lo que hará el Firebase real (D14).
- **V-03 (lista de contraseñas).** Fuente y licencia de la lista de 3000 antes del bloque 4 (pregunta 3).
- **V-05 (celular igual en cliente y servidor).** Comparar el resultado de `libphonenumber` Java con `libphonenumber-js` sobre los valores de los CA (`+573000000000`, `+34612345678`, `12345`) en el bloque 5; si difieren, se informa a Frontend.
- **V-06.** Confirmar que la propiedad de Jackson que rechaza números como enumerado existe con su nombre en la versión del proyecto (bloque 6).
- **V-07 (excepción del SDK de Firebase con el servicio caído).** Con el emulador detenido o con un host inalcanzable, comprobar qué `FirebaseAuthException` lanza `createUser` (su `getErrorCode()` de Firebase y su `getAuthErrorCode()`), para fijar qué excepciones cuentan como indisponibilidad (REQ-RV-09). Se hace en T-1A.9; si el resultado no coincide con el supuesto de la tarjeta, se detiene y se reporta.
- **V-08 (comportamiento del framework).** Comprobar qué responde hoy Cuentas a una ruta inexistente, a `DELETE /api/v1/users` y a un `Content-Type: text/plain` en el registro, antes de escribir los manejadores de REQ-RV-08 (T-1A.8).

## 15. Preguntas

### Respondidas (Paula, 6 de octubre de 2026)

| # | Pregunta | Respuesta | Efecto |
|---|---|---|---|
| 3 | Fuente de la lista de 3000 contraseñas | SecLists (licencia MIT), lista de un millón filtrada a 12 o más caracteres, las 3000 más frecuentes. Al empezar el bloque 4 se verifican la licencia y el conteo; si no alcanza, se detiene y se avisa | Desbloquea el bloque 4. Frontend recibe el mismo archivo |
| 4 | PD-08: ¿`requestId` desde esta tarea? | Sí, desde el PR 1A, sin filtro ni `MDC` | REQ-RV-03 y T-1A.5 |
| 5 | D1: ¿`birthDate` como texto con restricción propia? | Sí | Bloque 1B |
| 6 | D4: ¿cuerpo ilegible en 422 o 400? | Sigue en 422; la diferencia con el estándar se documenta en el ADR del `code` | REQ-RV-30 |
| 7 | D3: ¿edad, contraseña común y duplicado de uno en uno? | Sí | REQ-RV-64 |
| 11 | ¿Dependencia `libphonenumber` (Java)? | Aprobada; la versión, la última estable verificada en Maven Central el día del bloque | Bloque 5 |
| 13 | ¿Qué es un «espacio» al recortar? | El conjunto de `trim` de JavaScript (opción a) | REQ-RV-20 |
| 14 | Cuerpo sin límite de tamaño (OWASP API4) | Tarea aparte, que se pide a Vela: filtro de Cuentas de 16 KB para este endpoint y tope en el Gateway o Cloud Run (DevOps) | Fuera de CM-36 |
| 15 | `"pronoun":""` contradecía REQ-RV-15 (obligatorio) y REQ-RV-61 (valor inválido) | Vacío y solo espacios son `PRONOUN_REQUIRED`, como en los demás campos | REQ-RV-15 y REQ-RV-61 |
| 16 | ¿Bloques 2 y 3 en un solo PR? | Sí (≈ 650 líneas, mismos archivos) | Plan, sección 7 |
| 17 | ¿CM-251 y CM-179 esperan a 1A–3 o solo a 1A? | Solo a 1A | Plan, sección 7 |
| 18 | Forma del 422 de un error de dominio de un solo campo | `VALIDATION_FAILED` arriba y `errors` de un elemento («estándar siempre») | REQ-RV-02, D9 |
| 19 | `detail` del 422 | Texto fijo; la corrección del ejemplo del estándar va en el PR 1A, no en uno aparte | REQ-RV-02, D9 |
| 20 | Causa `TOO_COMMON` | Aprobada | REQ-RV-05, sección 6 |
| 21 | `VALIDATION_FAILED` e `INTERNAL_ERROR` fuera de la forma `<SUJETO>_<CAUSA>`; que ningún error conocido caiga en ellos | Se conservan como excepciones; ningún error conocido puede terminar en ellos | REQ-RV-05 y 07, D8 |
| 22 | Errores del framework (404 de ruta, 405, 415) | Se agregan `ROUTE_NOT_FOUND`, `METHOD_NOT_ALLOWED` y `MEDIA_TYPE_NOT_ALLOWED` | REQ-RV-08 |
| 23 | Firebase caído: 500 o 503 | 503 `DEPENDENCY_UNAVAILABLE`; comunicarlo | REQ-RV-09, D10 |
| 24 | Violación de restricciones de la base | «Lo mejor, más seguro y usable»: clasificación por escrito y sin traducción (explicado a Paula) | REQ-RV-19, D11 |
| 25 | Registro de errores (`firebaseUid`, nivel, contenido) y compensación fallida | Lo más seguro; prueba de la compensación fallida | REQ-RV-27, D12 |
| 26 | Regex del `requestId` | Confirmada (`^[A-Za-z0-9._-]{1,64}$`; UUID v4 si no cumple); el `MDC` queda para CM-283 | REQ-RV-03 |
| 27 | Bloque 4 y el tope de 1000 líneas | Dos PR (código y datos); la excepción del PR de datos es de Paula | D13 |
| 28 | Estimación de 25 h, fecha de fin del Sprint 2, CM-246 y libro de pruebas v4 | No importa por ahora; se comunica a Vela cuando Paula lo decida | Sección 17 |

### Respondidas (Paula, 7 de octubre de 2026, durante la ejecución)

| # | Pregunta | Respuesta | Efecto |
|---|---|---|---|
| 29 | El PR 1A mide 1653 líneas y `specs/` 1526: ¿cómo se parten? | De acuerdo con la propuesta: la spec en un PR propio, solo de documentación; 1A (catálogo, formato de error, `requestId` y `charset`) y 1A-bis (404, 405 y 415, 503 de Firebase, restricciones de la base y catálogo de errores), apilado | Plan, sección 7; los commits locales se reordenan sin force-push (nada está en el remoto) |
| 30 | El PR de código de la lista (4a) no puede fusionarse antes que el de datos (4b): la aplicación no arranca sin la lista | De acuerdo: primero datos y después código | D13 |
| 31 | `libphonenumber` directo en el dominio o detrás de un puerto | «Como el código sea más limpio y mantenible»: directo en el dominio. Es una función pura de cálculo, sin E/S ni marco, y un puerto agregaría una interfaz, un adaptador y un parámetro más al caso de uso (≈ 120 líneas) sin una segunda implementación real | REQ-RV-50; la regla «el dominio no importa Google» se refiere a los SDK con E/S (Firebase) |
| 32 | Números válidos de siete dígitos (Tristan da Cunha, Tokelau, Niue) que la restricción de la base rechaza | «Si lo necesitan los CA; si no, informar a Frontend». La nota técnica de CA-1.1.32 dice «Formato válido = el que acepta libphonenumber (misma librería y reglas en cliente y servidor)», así que sí: se aceptan. La migración V4 cambia `ck_cuenta_telefono_e164` a `^\+[1-9][0-9]{5,14}$` (6 a 15 dígitos: 6 es el mínimo de un número posible en `libphonenumber` 9.0.40 y 15 el máximo de E.164) | REQ-RV-53, sección 7 |
| 33 | Firebase sin tiempo de espera (el SDK trae 0: sin límite) | En esta misma tarea: conectar 5 s y leer 10 s; vencido el tiempo, 503 `DEPENDENCY_UNAVAILABLE` | REQ-RV-09 |
| 34 | Fuente de la lista de 3000 contraseñas | «Como sea mejor»: `Pwdb_top-1000000` de SecLists (MIT, ordenada por frecuencia), sin las entradas que son restos de codificación de la fuente | REQ-RV-40 |
| 35 | Commits con o sin `[IA-ASISTIDO]` | Sin: lo pide el `CLAUDE.md` del repositorio; el título del PR sí lo lleva | Reglas de `tasks.md` |

### Respondidas (Paula, 7 de octubre de 2026, revisión final del stack)

La revisión de código (`/code-review high`) y de seguridad (`/security-review`) encontró fallos previsibles que terminaban en el respaldo `INTERNAL_ERROR`. Las preguntas que resolvían los CA se cerraron leyéndolos.

| # | Pregunta | Respuesta | Efecto |
|---|---|---|---|
| 36 | Correo que Firebase rechaza: ¿500 o 422? | Lo resuelve CA-1.1.20: 422 `EMAIL_INVALID_FORMAT` | REQ-RV-09, D14 |
| 37 | Activación con Firebase caído o con un uid que no existe en Firebase (500) | Lo resuelve CA-1.2.6 (la verificación se toma solo del token): es de CM-179, y al alinearla la consulta a Firebase desaparece | `docs/errores.md` con destino CM-179 |
| 38 | Activar una cuenta bloqueada o anonimizada y `X-User-Email-Verified: abc` | Son de HU-1.2 (CM-179); el texto de la cuenta bloqueada está en CA-1.3.11 | Sin cambio: ya estaban en `docs/errores.md` |
| 39 | Tipo de respuesta no admitido (406) | Sí: código y causa nuevos | REQ-RV-08, D16 |
| 40 | Credencial que queda en Firebase sin cuenta local (503 y después 409) | Sí: es el riesgo aceptado de P-02 A (backlog v4); registrar el identificador para conciliación es de CM-251 | `docs/errores.md` con destino CM-251 |
| 41 | ¿Texto propio para el 503? | Sí, se queda el de RT-05: RT-05-CA01 lo fija para todo error del servidor y P-02 A para «Firebase no responde» | Sin cambio |
| 42 | Spec, bitácora, guarda, tope de 64 en la lista, SQLState en el log, base común de las excepciones de campo, XML | «Haz todas las mejoras para que el código quede limpio, consistente y bien resuelto» | D14 a D17, REQ-RV-19, REQ-RV-40 y 41; `docs/bitacora-ia/02_*` |
| 43 | REQ-RV-64 no se cumplía para las reglas de forma del dominio (auditoría en vivo) | Se corrige: lo exige el requisito aprobado | REQ-RV-64, D18 |
| 44 | Un arreglo u objeto en un campo de texto oculta los demás errores | Se mantiene y se documenta | D19 |

### Abiertas (otras personas)

| # | Pregunta | A quién | Recomendación | Bloquea |
|---|---|---|---|---|
| 1 | ¿Las correcciones de HU-1.1 viven en CM-36? Jira no tiene una tarea «Ajustes v4 – Backend» de Cuentas para HU-1.1 | **No era pregunta: se comunica. Las correcciones de HU-1.1 se registran en CM-36; se informa a Vela** | Sí, en CM-36 | Dónde se registra y cierra el trabajo; nada de código |
| 2 | ¿El servidor devuelve los textos del catálogo de los CA en todos los campos (sección 5)? | **Cerrada sin consulta (cambio sin alternativa):** RT-01 ya lo dice: las mismas reglas en la interfaz y en el backend y «el mensaje literal de cada campo está en su CA» | Sí: RT-01 pide las mismas reglas en cliente y servidor | Bloque 6 (T-6.3) |
| 8 | Nombre y apellido: ¿letras de otros alfabetos (`\p{L}`: «李», «Åsa») y espacios dobles internos («María  José»)? El CA dice «letras (con tildes, ñ y ü)» | **Decidida por Paula (6-oct-2026): se acepta toda letra (`\p{L}`); los espacios internos repetidos de nombre y apellido se unen en uno** | Permitir todo `\p{L}` y los espacios internos sin colapsar | La parte de `PersonName` del PR 2+3 |
| 9 | Celular: ¿todos los tipos que `libphonenumber` da por válidos o solo móvil y fijo? | **Decidida por Paula (6-oct-2026): los mismos números que el cliente (`isValid`)** | Igual que el cliente (`isValid`) | Bloque 5 |
| 10 | Texto de `PRONOUN_INVALID_VALUE` (el CA no lo define) | **Cerrada sin consulta (cambio sin alternativa):** RT-01 fija el texto para listas: «Selecciona una opción.» | «Selecciona una opción.» | Bloque 6 (T-6.2) |
| 12 | Confirmar en staging `SELECT DISTINCT pronombres FROM microcuentas.cuenta` antes de desplegar la migración V3 | **PENDIENTE de Juan Diego Gomez**, vía Vela | Pedirlo en el documento a DevOps | Despliegue de 1B (no el código) |

Además, en el documento a Product Owner: la fecha de fin del Sprint 2 (Jira 12-oct, backlog 23-oct) y la estimación (la HU dice 5 h; las piezas suman ≈ 25 h, sección 17).

## 16. Fuera de alcance

CA-1.1.36 (falla el envío del correo: cameia-web) · CA-1.1.44 (autorización de datos: Sprint 3) · CA-1.1.14 y 1.1.28 (confirmación de contraseña: solo cliente) · CA-1.1.33 a 1.1.35 (navegación) · registro repetido (CM-251) ·
evento de cuenta creada (CM-187) · `GET /api/v1/users/me` (CM-42) · edad como tarea propia (CM-254: solo las pruebas de borde, bloque 6) · cualquier cambio en el Gateway o en `cameia-web` · limitación de peticiones (se aplica antes del Gateway) ·
Idempotency-Key (HU-1.1 no la usa: C-12).

## 17. Estimación y riesgos

| Bloque | Código | Pruebas y documentación | Total estimado | Líneas de diff (aprox.) |
|---|---|---|---|---|
| 1A | 4,5 h | 3 h | 7,5 h | 850 (si supera 1000, las tarjetas T-1A.8 a T-1A.10 salen como un PR 1A-bis apilado) |
| 1B | 2 h | 2,5 h | 4,5 h | 550 |
| 2 | 1,5 h | 1,5 h | 3 h | 350 |
| 3 | 1 h | 1,5 h | 2,5 h | 300 |
| 4 | 1 h | 1 h | 2 h | 3300 (3000 son datos) → el archivo de datos se mide aparte; la parte de código, 150 |
| 5 | 1,5 h | 1,5 h | 3 h | 350 |
| 6 | 2 h | 3 h | 5 h | 700 |

Total ≈ 27,5 h, frente a las 5 h de la HU: se informa a Vela. Riesgos: el diff del bloque 4 por los datos (se propone entregarlos en un commit aparte, que Paula revisa como datos y no como código); la dependencia nueva del bloque 5; el cambio de textos del bloque 6 afecta a las pruebas de Frontend (se avisa).
