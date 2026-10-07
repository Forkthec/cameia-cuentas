# Errores del servicio

## Formato

Las respuestas de error siguen la [sección 6 del estándar](estandar-backend.md#6-errores): `ProblemDetail` (RFC 9457) con
`Content-Type: application/problem+json; charset=UTF-8`, el código estable en `code` y el identificador de la petición en
`requestId` (el `X-Request-Id` recibido si cumple `^[A-Za-z0-9._-]{1,64}$`; si no, un UUID v4 nuevo, que también se devuelve en el
encabezado `X-Request-Id`). Los errores de validación agregan `errors`, con un elemento `field`, `code` y `message` por campo
rechazado; en ellos `code` es `VALIDATION_FAILED` y `detail` es siempre «Revisa los campos marcados.». La decisión está en el
[ADR 0001](adr/0001-codigo-de-error-y-request-id.md).

Los errores de forma de cada campo salen todos a la vez, un elemento por campo: los de Bean Validation (`@NotBlank`,
`@CodePointSize`, `@BirthDateFormat`) y las reglas de forma del dominio (caracteres del nombre, formato del correo y del celular,
longitud de la contraseña, opción del pronombre), que `@DomainRule` ejecuta en el borde tomando el código y el mensaje de la
excepción del dominio. Las reglas de negocio (edad, contraseña común, correo repetido) se aplican después y de una en una.

Todas las respuestas las produce `BusinessExceptionHandler`. Cada error se registra una sola vez: los 4xx en `WARN` sin traza, con
`code`, `requestId` y, en validación, los nombres de campo y sus códigos; los 5xx en `ERROR` con traza. Nunca se registran el valor de
un campo, el correo, la contraseña ni el mensaje de una excepción de deserialización o de base de datos.

## Códigos que el servicio emite

Rutas: **registro** es `POST /api/v1/users`; **activación** es `POST /api/v1/users/me/verification`; **cualquiera** es toda ruta del
servicio. Las pruebas citadas están en `src/test/java/tech/cameia/cuentas/`.

| Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba |
|---|---|---|---|---|---|---|
| `VALIDATION_FAILED` | 422 | registro | — (lista `errors`) | Revisa los campos marcados. | Lista `errors` no vacía | `UserRegistrationControllerTest` |
| `FIRST_NAME_REQUIRED` | 422 | registro | `firstName` | Ingresa tu nombre. | `@NotBlank`, tras recortar los espacios que recorta el cliente | `UserRegistrationControllerTest` |
| `FIRST_NAME_TOO_LONG` | 422 | registro | `firstName` | El nombre no puede superar los 120 caracteres. | `@CodePointSize(max = 120)`, en puntos de código tras recortar y normalizar a NFC | `UserRegistrationControllerTest` |
| `FIRST_NAME_INVALID_CHARACTERS` | 422 | registro | `firstName` | El nombre solo puede contener letras, espacios, apóstrofo y guion. | `PersonName` (`InvalidPersonNameException`): un carácter que no es letra, marca combinante, espacio, apóstrofo (recto o tipográfico) ni guion, o ninguna letra | `PersonNameTest`, `RegisterUserServiceTest`, `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `LAST_NAME_REQUIRED` | 422 | registro | `lastName` | Ingresa tu apellido. | `@NotBlank`, tras recortar los espacios que recorta el cliente | `UserRegistrationControllerTest` |
| `LAST_NAME_TOO_LONG` | 422 | registro | `lastName` | El apellido no puede superar los 120 caracteres. | `@CodePointSize(max = 120)`, en puntos de código tras recortar y normalizar a NFC | `UserRegistrationControllerTest` |
| `LAST_NAME_INVALID_CHARACTERS` | 422 | registro | `lastName` | El apellido solo puede contener letras, espacios, apóstrofo y guion. | `PersonName` (`InvalidPersonNameException`), misma regla que el nombre | `PersonNameTest`, `RegisterUserServiceTest`, `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `BIRTH_DATE_REQUIRED` | 422 | registro | `birthDate` | Ingresa tu fecha de nacimiento. | `@NotBlank` (ausente, `null`, vacía o en blanco) | `UserRegistrationControllerTest` |
| `BIRTH_DATE_INVALID_FORMAT` | 422 | registro | `birthDate` | Formato de fecha inválido. | `@BirthDateFormat`: no es una fecha real con el formato `dd/MM/aaaa` (incluye `31/02/2000`, espacios, otro formato, número o booleano) | `BirthDateFormatValidatorTest`, `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `BIRTH_DATE_IN_THE_FUTURE` | 422 | registro | `birthDate` | Fecha de nacimiento inválida. | `AgePolicy` (`InvalidBirthDateException`): una fecha posterior a hoy en UTC | `AgePolicyTest` |
| `BIRTH_DATE_UNDERAGE` | 422 | registro | `birthDate` | Debes ser mayor de edad. | `AgePolicy` (`InvalidBirthDateException`): menos de 18 años cumplidos en UTC | `AgePolicyTest`, `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `BIRTH_DATE_OUT_OF_RANGE` | 422 | registro | `birthDate` | Verifica tu fecha de nacimiento. | `AgePolicy` (`InvalidBirthDateException`): más de 110 años cumplidos en UTC | `AgePolicyTest` |
| `EMAIL_REQUIRED` | 422 | registro | `email` | Ingresa tu correo electrónico. | `@NotBlank`, tras recortar los espacios que recorta el cliente | `UserRegistrationControllerTest` |
| `EMAIL_TOO_LONG` | 422 | registro | `email` | El correo no puede superar los 254 caracteres. | `@CodePointSize(max = 254)`, en puntos de código tras recortar y normalizar a NFC; el dominio repite el límite (`InvalidEmailException`) | `UserRegistrationControllerTest`, `ValueObjectsTest` |
| `EMAIL_INVALID_FORMAT` | 422 | registro | `email` | Ingresa un correo electrónico válido. | `EmailAddress` (`InvalidEmailException`): sin una sola arroba, sin dominio con punto, con espacios o con caracteres invisibles (de control, de formato o separadores, como el espacio duro o el de ancho cero). También un correo que pasó `EmailAddress` y que Firebase rechaza como inválido (`INVALID_EMAIL` del servicio, que el SDK entrega como `INVALID_ARGUMENT` sin código de autenticación). Es una defensa: no se conoce un correo que pase la regla propia y Firebase rechace, y el emulador los acepta todos | `ValueObjectsTest`, `FirebaseUserDirectoryAdapterTest`, `BusinessExceptionHandlerTest`, `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `EMAIL_ALREADY_REGISTERED` | 409 | registro | — | Ese correo ya tiene una cuenta. | `EmailAlreadyRegisteredException` | `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `PASSWORD_REQUIRED` | 422 | registro | `password` | Ingresa tu contraseña. | `@NotBlank` | `UserRegistrationControllerTest` |
| `PASSWORD_TOO_SHORT` | 422 | registro | `password` | La contraseña debe tener al menos 12 caracteres. | `PasswordPolicy` (`WeakPasswordException`) | `PasswordPolicyTest`, `UserRegistrationControllerTest` |
| `PASSWORD_TOO_LONG` | 422 | registro | `password` | La contraseña no puede superar los 64 caracteres. | `PasswordPolicy` (`WeakPasswordException`) | `PasswordPolicyTest` |
| `PASSWORD_TOO_COMMON` | 422 | registro | `password` | Esta contraseña es demasiado común, elige otra. | `PasswordPolicy` (`WeakPasswordException`) | `PasswordPolicyTest`, `UserRegistrationControllerTest` |
| `PRONOUN_REQUIRED` | 422 | registro | `pronoun` | Selecciona una opción. | `@NotBlank` (ausente, `null`, vacío o en blanco) | `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `PRONOUN_INVALID_VALUE` | 422 | registro | `pronoun` | Selecciona una opción. | Un valor que no es `HE`, `SHE` ni `THEY` escrito igual (otro texto, otras mayúsculas, número, booleano, arreglo u objeto); un número no se lee como la posición de la opción | `BusinessExceptionHandlerTest`, `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `PHONE_NUMBER_INVALID_FORMAT` | 422 | registro | `phoneNumber` | Revisa el número, no coincide con el formato del país elegido. | `PhoneNumber.fromInput` (`InvalidPhoneNumberException`): no cumple E.164 sin espacios (de 6 a 15 dígitos con el indicativo, la misma regla de `ck_cuenta_telefono_e164`) o no es un número válido para su país según `libphonenumber`. Vacío o en blanco es «sin celular» | `ValueObjectsTest`, `PhoneNumberDatabaseCompatibilityTest`, `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` |
| `REQUEST_BODY_INVALID_FORMAT` | 422 | registro | — | Revisa el formato de los datos enviados. | Cuerpo ilegible (`HttpMessageNotReadableException`): JSON mal formado o un arreglo u objeto donde va un texto, salvo en `pronoun`, que responde `PRONOUN_INVALID_VALUE` | `BusinessExceptionHandlerTest`, `UserRegistrationControllerTest` |
| `REQUEST_INVALID_VALUE` | 422 | registro | El campo de la restricción | Valor no válido | Respaldo que no debe emitirse: una restricción del contrato sin código asignado. `todaRestriccionDelContratoTieneCodigo` lo impide y, si ocurriera, se registra en `ERROR` | `BusinessExceptionHandlerTest` |
| `IDENTITY_REQUIRED` | 400 | activación | — | La petición no incluye los datos que exige esta ruta | Falta `X-User-Id` (`ServletRequestBindingException`) | `AccountActivationControllerTest` |
| `EMAIL_NOT_VERIFIED` | 403 | activación | — | Primero debes verificar tu correo con el enlace que te enviamos | `EmailNotVerifiedException` | `AccountActivationControllerTest` |
| `ACCOUNT_NOT_FOUND` | 404 | activación | — | No encontramos una cuenta para este usuario | `AccountNotFoundException` | `AccountActivationControllerTest` |
| `DEPENDENCY_UNAVAILABLE` | 503 | registro | — | Ocurrió un error. Inténtalo de nuevo. | `DependencyUnavailableException`: Firebase no respondió o falló de su lado (`UNAVAILABLE`, `DEADLINE_EXCEEDED`, `INTERNAL` o causa de E/S) | `FirebaseUserDirectoryAdapterTest`, `UserRegistrationControllerTest`, `RegisterUserServiceTest` |
| `ROUTE_NOT_FOUND` | 404 | cualquiera | — | No existe la ruta solicitada. | `NoResourceFoundException` | `FrameworkErrorsTest` |
| `METHOD_NOT_ALLOWED` | 405 | cualquiera | — | Método no permitido. | `HttpRequestMethodNotSupportedException` (con `Allow`) | `FrameworkErrorsTest` |
| `MEDIA_TYPE_NOT_ALLOWED` | 415 | cualquiera | — | Tipo de contenido no admitido. | `HttpMediaTypeNotSupportedException` (con `Accept`) | `FrameworkErrorsTest` |
| `MEDIA_TYPE_NOT_ACCEPTABLE` | 406 | cualquiera | — | Tipo de respuesta no admitido. | `HttpMediaTypeNotAcceptableException` (el `Accept` no admite ningún tipo que el servicio produzca) | `FrameworkErrorsTest` |
| `INTERNAL_ERROR` | 500 | cualquiera | — | Ocurrió un error. Inténtalo de nuevo. | Cualquier fallo imprevisto, incluida la violación de una restricción de la base | `BusinessExceptionHandlerTest` |

Un rechazo de Firebase no es indisponibilidad. Si Identity Toolkit responde `INVALID_EMAIL`, la persona recibe 422
`EMAIL_INVALID_FORMAT` en `email`. Cualquier otro rechazo (`WEAK_PASSWORD`, `PASSWORD_DOES_NOT_MEET_REQUIREMENTS`,
`OPERATION_NOT_ALLOWED`: una política o un proveedor configurados en Firebase) es un defecto de configuración que la persona no puede
corregir: responde `INTERNAL_ERROR` y el log lleva el código del servicio, nunca el correo.

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
| 500 `INTERNAL_ERROR` al activar una cuenta bloqueada o anonimizada | Un caso previsible tiene su excepción de negocio, su código y su estado | La tarea de verificación de correo: 403 `ACCOUNT_DISABLED` para la cuenta bloqueada y 404 `ACCOUNT_NOT_FOUND` para la anonimizada |
| 500 `INTERNAL_ERROR` en la activación cuando `X-User-Email-Verified` no es `true` ni `false` (por ejemplo `abc`) | Un encabezado inválido es un error del cliente con su código | La tarea de verificación de correo, que define cómo se trata ese encabezado |
| 500 `INTERNAL_ERROR` en la activación cuando Firebase no responde al consultar si el correo está verificado (cuando `X-User-Email-Verified` no es `true`) | La indisponibilidad de Firebase es 503 `DEPENDENCY_UNAVAILABLE`, como en el registro | La tarea de verificación de correo. CA-1.2.6 toma la verificación solo del token, así que al alinear la activación con ese criterio la consulta a Firebase desaparece, y con ella este caso |
| 500 `INTERNAL_ERROR` en la activación cuando el usuario no existe en Firebase | Un caso previsible tiene su excepción de negocio, su código y su estado | La tarea de verificación de correo, por la misma razón que la fila anterior |
| 409 `EMAIL_ALREADY_REGISTERED` al reintentar un registro cuya credencial quedó en Firebase sin cuenta local (Firebase venció el tiempo después de crearla, o la compensación no pudo borrarla) | — (el comportamiento es el aceptado) | El backlog v4 (P-02 A) acepta el 409 para la credencial sin fila y pide registrar el identificador para conciliarla a mano. Ese registro lo agrega la tarea del registro repetido (CA-1.1.30); la compensación fallida ya se registra en `ERROR` con el `firebaseUid` |
| Sin límite de tamaño del cuerpo: un registro de 50 KB responde 201 | OWASP API4: un tope de tamaño para la petición | La tarea pedida a Vela (pregunta 14 de la spec de CM-36): filtro de 16 KB en Cuentas para este endpoint y tope en el Gateway o en Cloud Run |
| 500 `INTERNAL_ERROR` ante una violación de restricción de la base | El estándar pide traducirla a su código específico | Se conserva por decisión de la spec de validaciones del registro: ninguna restricción es alcanzable por una entrada, y una tabla de traducción sería código sin camino que la active |

## Cómo se agrega un código

Un código nuevo se agrega aquí con la spec que lo introduce, junto a su excepción de negocio, su estado, su mensaje y su prueba. Un
código publicado no se reutiliza ni se renombra. `ErrorCodeDocumentationTest` falla si un valor de `ErrorCode` no aparece en esta
página.
