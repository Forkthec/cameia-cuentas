# Errores del servicio

## Formato

Las respuestas de error siguen la [sección 6 del estándar](estandar-backend.md#6-errores): `ProblemDetail` (RFC 9457) con
`Content-Type: application/problem+json; charset=UTF-8`, el código estable en `code` y el identificador de la petición en
`requestId` (el `X-Request-Id` recibido si cumple `^[A-Za-z0-9._-]{1,64}$`; si no, un UUID v4 nuevo, que también se devuelve en el
encabezado `X-Request-Id`). Los errores de validación agregan `errors`, con un elemento `field`, `code` y `message` por campo
rechazado; en ellos `code` es `VALIDATION_FAILED` y `detail` es siempre «Revisa los campos marcados.». La decisión está en el
[ADR 0001](adr/0001-codigo-de-error-y-request-id.md).

Todas las respuestas las produce `BusinessExceptionHandler`. Cada error se registra una sola vez: los 4xx en `WARN` sin traza, con
`code`, `requestId` y, en validación, los nombres de campo y sus códigos; los 5xx en `ERROR` con traza. Nunca se registran el valor de
un campo, el correo, la contraseña ni el mensaje de una excepción de deserialización o de base de datos.

## Códigos que el servicio emite

Rutas: **registro** es `POST /api/v1/users`; **activación** es `POST /api/v1/users/me/verification`; **cualquiera** es toda ruta del
servicio. Las pruebas citadas están en `src/test/java/tech/cameia/cuentas/`.

| Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba |
|---|---|---|---|---|---|---|
| `VALIDATION_FAILED` | 422 | registro | — (lista `errors`) | Revisa los campos marcados. | Lista `errors` no vacía | `UserRegistrationControllerTest` |
| `FIRST_NAME_REQUIRED` | 422 | registro | `firstName` | Los nombres son obligatorios | `@NotBlank`, tras recortar los espacios que recorta el cliente | `UserRegistrationControllerTest` |
| `FIRST_NAME_TOO_LONG` | 422 | registro | `firstName` | Los nombres no pueden superar los 120 caracteres | `@CodePointSize(max = 120)`, en puntos de código tras recortar y normalizar a NFC | `UserRegistrationControllerTest` |
| `FIRST_NAME_INVALID_CHARACTERS` | 422 | registro | `firstName` | El nombre solo puede contener letras, espacios, apóstrofo y guion. | `PersonName` (`InvalidPersonNameException`): un carácter que no es letra, marca combinante, espacio, apóstrofo (recto o tipográfico) ni guion, o ninguna letra | `PersonNameTest`, `RegisterUserServiceTest`, `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `LAST_NAME_REQUIRED` | 422 | registro | `lastName` | Los apellidos son obligatorios | `@NotBlank`, tras recortar los espacios que recorta el cliente | `UserRegistrationControllerTest` |
| `LAST_NAME_TOO_LONG` | 422 | registro | `lastName` | Los apellidos no pueden superar los 120 caracteres | `@CodePointSize(max = 120)`, en puntos de código tras recortar y normalizar a NFC | `UserRegistrationControllerTest` |
| `LAST_NAME_INVALID_CHARACTERS` | 422 | registro | `lastName` | El apellido solo puede contener letras, espacios, apóstrofo y guion. | `PersonName` (`InvalidPersonNameException`), misma regla que el nombre | `PersonNameTest`, `RegisterUserServiceTest`, `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `BIRTH_DATE_REQUIRED` | 422 | registro | `birthDate` | La fecha de nacimiento es obligatoria | `@NotBlank` (ausente, `null`, vacía o en blanco) | `UserRegistrationControllerTest` |
| `BIRTH_DATE_INVALID_FORMAT` | 422 | registro | `birthDate` | Formato de fecha inválido. | `@BirthDateFormat`: no es una fecha real con el formato `dd/MM/aaaa` (incluye `31/02/2000`, espacios, otro formato, número o booleano) | `BirthDateFormatValidatorTest`, `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `BIRTH_DATE_IN_THE_FUTURE` | 422 | registro | `birthDate` | Fecha de nacimiento inválida | `AgePolicy` (`InvalidBirthDateException`) | `AgePolicyTest` |
| `BIRTH_DATE_UNDERAGE` | 422 | registro | `birthDate` | Debes ser mayor de edad | `AgePolicy` (`InvalidBirthDateException`) | `AgePolicyTest`, `UserRegistrationControllerTest` |
| `BIRTH_DATE_OUT_OF_RANGE` | 422 | registro | `birthDate` | La fecha de nacimiento no es plausible, por favor verifícala | `AgePolicy` (`InvalidBirthDateException`) | `AgePolicyTest` |
| `EMAIL_REQUIRED` | 422 | registro | `email` | El correo electrónico es obligatorio | `@NotBlank`, tras recortar los espacios que recorta el cliente | `UserRegistrationControllerTest` |
| `EMAIL_TOO_LONG` | 422 | registro | `email` | El correo no puede superar los 254 caracteres. | `@CodePointSize(max = 254)`, en puntos de código tras recortar y normalizar a NFC | `UserRegistrationControllerTest` |
| `EMAIL_ALREADY_REGISTERED` | 409 | registro | — | Este correo ya se encuentra registrado | `EmailAlreadyRegisteredException` | `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `PASSWORD_REQUIRED` | 422 | registro | `password` | La contraseña es obligatoria | `@NotBlank` | `UserRegistrationControllerTest` |
| `PASSWORD_TOO_SHORT` | 422 | registro | `password` | La contraseña debe tener al menos 12 caracteres | `PasswordPolicy` (`WeakPasswordException`) | `PasswordPolicyTest`, `UserRegistrationControllerTest` |
| `PASSWORD_TOO_LONG` | 422 | registro | `password` | La contraseña no puede superar los 64 caracteres | `PasswordPolicy` (`WeakPasswordException`) | `PasswordPolicyTest` |
| `PASSWORD_TOO_COMMON` | 422 | registro | `password` | Esta contraseña es demasiado común, elige otra. | `PasswordPolicy` (`WeakPasswordException`) | `PasswordPolicyTest`, `UserRegistrationControllerTest` |
| `PRONOUN_REQUIRED` | 422 | registro | `pronoun` | Selecciona una opción. | `@NotNull` (ausente, `null`, vacío o en blanco) | `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `REQUEST_BODY_INVALID_FORMAT` | 422 | registro | — | Revisa el formato de los datos enviados. La fecha de nacimiento usa el formato DD/MM/AAAA | Cuerpo ilegible (`HttpMessageNotReadableException`): JSON mal formado, un arreglo u objeto donde va un texto, o un pronombre fuera de la lista | `UserRegistrationControllerTest` |
| `REQUEST_INVALID_VALUE` | 422 | registro | — | El texto del objeto de valor que rechazó el dato, o «Revisa los datos enviados.» si la excepción no nació en el dominio | `IllegalArgumentException` (respaldo temporal) | `BusinessExceptionHandlerTest`, `UserRegistrationControllerTest` |
| `IDENTITY_REQUIRED` | 400 | activación | — | La petición no incluye los datos que exige esta ruta | Falta `X-User-Id` (`ServletRequestBindingException`) | `AccountActivationControllerTest` |
| `EMAIL_NOT_VERIFIED` | 403 | activación | — | Primero debes verificar tu correo con el enlace que te enviamos | `EmailNotVerifiedException` | `AccountActivationControllerTest` |
| `ACCOUNT_NOT_FOUND` | 404 | activación | — | No encontramos una cuenta para este usuario | `AccountNotFoundException` | `AccountActivationControllerTest` |
| `DEPENDENCY_UNAVAILABLE` | 503 | registro | — | Ocurrió un error. Inténtalo de nuevo. | `DependencyUnavailableException`: Firebase no respondió o falló de su lado (`UNAVAILABLE`, `DEADLINE_EXCEEDED`, `INTERNAL` o causa de E/S) | `FirebaseUserDirectoryAdapterTest`, `UserRegistrationControllerTest`, `RegisterUserServiceTest` |
| `ROUTE_NOT_FOUND` | 404 | cualquiera | — | No existe la ruta solicitada. | `NoResourceFoundException` | `FrameworkErrorsTest` |
| `METHOD_NOT_ALLOWED` | 405 | cualquiera | — | Método no permitido. | `HttpRequestMethodNotSupportedException` (con `Allow`) | `FrameworkErrorsTest` |
| `MEDIA_TYPE_NOT_ALLOWED` | 415 | cualquiera | — | Tipo de contenido no admitido. | `HttpMediaTypeNotSupportedException` (con `Accept`) | `FrameworkErrorsTest` |
| `INTERNAL_ERROR` | 500 | cualquiera | — | Ocurrió un error. Inténtalo de nuevo. | Cualquier fallo imprevisto, incluida la violación de una restricción de la base | `BusinessExceptionHandlerTest` |

Un rechazo de Firebase por un dato que pasó la validación propia (por ejemplo, un correo que Firebase considera inválido) no es
indisponibilidad: responde `INTERNAL_ERROR` y queda en el log como defecto de validación.

**Restricciones de la tabla `cuenta`.** Ninguna se viola por una entrada de la persona, porque la validación del contrato y del dominio
actúa antes; todas están clasificadas como invariantes internas (`cuenta_pkey`, `uq_cuenta_firebase_uid`, `ck_cuenta_nombre`,
`ck_cuenta_apellido`, `ck_cuenta_estado`, `ck_cuenta_version`, `ck_cuenta_telefono_e164`, `ck_cuenta_pronombres_no_vacio`,
`ck_cuenta_fecha_actualizacion`, `ck_cuenta_fecha_eliminacion`, `ck_cuenta_anonimizacion` y `ck_cuenta_pronombres_valor`). Una violación es un defecto: responde
`INTERNAL_ERROR` y el log lleva solo el nombre de la restricción, nunca la fila. `CuentaConstraintsClassificationTest` falla si
aparece una restricción sin clasificar. `uq_cuenta_firebase_uid` solo se alcanza con dos registros simultáneos del mismo usuario; su
código lo fija la tarea del registro repetido.

## Respuestas publicadas que difieren del estándar

Se conservan porque Frontend ya las consume o porque su corrección es de otra tarea; cada una tiene su destino.

| Respuesta actual | Qué pide el estándar | Destino |
|---|---|---|
| 422 cuando el cuerpo no se puede interpretar | 400 para un cuerpo ilegible | Se conserva (decisión de contrato). Pasarlo a 400 sería un cambio de contrato con Frontend, con su propia spec |
| 422 `REQUEST_INVALID_VALUE` sin `field` cuando un objeto de valor rechaza un dato (correo mal formado, celular sin indicativo o vacío) | Un código específico y su campo por cada causa | La tarea de validaciones del registro: cada objeto de valor lanza su excepción de negocio con su código y su campo, y el respaldo desaparece |
| 500 `INTERNAL_ERROR` al activar una cuenta bloqueada o anonimizada | Un caso previsible tiene su excepción de negocio, su código y su estado | La tarea de verificación de correo: 403 `ACCOUNT_DISABLED` para la cuenta bloqueada y 404 `ACCOUNT_NOT_FOUND` para la anonimizada |
| 500 `INTERNAL_ERROR` en la activación cuando `X-User-Email-Verified` no es `true` ni `false` (por ejemplo `abc`) | Un encabezado inválido es un error del cliente con su código | La tarea de verificación de correo, que define cómo se trata ese encabezado |
| 500 `INTERNAL_ERROR` ante una violación de restricción de la base | El estándar pide traducirla a su código específico | Se conserva por decisión de la spec de validaciones del registro: ninguna restricción es alcanzable por una entrada, y una tabla de traducción sería código sin camino que la active |

## Cómo se agrega un código

Un código nuevo se agrega aquí con la spec que lo introduce, junto a su excepción de negocio, su estado, su mensaje y su prueba. Un
código publicado no se reutiliza ni se renombra. `ErrorCodeDocumentationTest` falla si un valor de `ErrorCode` no aparece en esta
página.
