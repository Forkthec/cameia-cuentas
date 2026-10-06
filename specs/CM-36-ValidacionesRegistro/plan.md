# Plan — CM-36-ValidacionesRegistro

Base: `origin/develop` `908112c`. Estado: pendiente de aprobación de Paula. Este plan contiene el **bloque 1** (dos PR: 1A y 1B, secciones 1 a 8) y el **bloque 2** (sección 9). Los planes de los bloques 3 a 6 se
agregan a este archivo y a `tasks.md` antes de ejecutar cada uno, con el estado real del repositorio ese día; hoy solo se fijan su orden y sus dependencias (sección 7).

## 1. Cómo se aborda el bloque 1

**1A — formato de error.** Se agrega un catálogo `ErrorCode` junto a las excepciones; `BusinessException` pasa a recibir su código; el único manejador
(`BusinessExceptionHandler`) lo escribe en el miembro `code` de cada respuesta y en cada elemento de `errors[]`, y asigna el código de cada restricción del
contrato con una tabla `campo + restricción`. No cambia ningún texto ni ningún estado HTTP, salvo el texto del 500 (RT-05-CA01). Una prueba garantiza que ninguna
restricción del DTO quede sin código.

**1B — tres correcciones.** (a) `birthDate` pasa a texto con una restricción propia de formato estricto; el controlador lo convierte con `RegisterUserRequest.toCommand()`.
(b) El mensaje de contraseña común pasa al literal del backlog. (c) El pronombre pasa a obligatorio en la API y la base repite los valores admitidos (migración V3).
Se documenta el contrato en OpenAPI.

## 2. Archivos del bloque 1 (rutas completas; paquete base `src/main/java/tech/cameia/cuentas`)

| PR | Acción | Archivo | Qué |
|---|---|---|---|
| 1A | Crear | `domain/exception/ErrorCode.java` | Enumerado del catálogo (solo los códigos que 1A y 1B emiten) |
| 1A | Modificar | `domain/exception/BusinessException.java` | Constructor `(ErrorCode, String)` y `getErrorCode()` |
| 1A | Modificar | `domain/exception/EmailAlreadyRegisteredException.java`, `AccountNotFoundException.java`, `EmailNotVerifiedException.java` | Pasan su código al padre |
| 1A | Modificar | `domain/exception/InvalidBirthDateException.java` | Cada `Reason` lleva su `ErrorCode` |
| 1A | Modificar | `domain/exception/WeakPasswordException.java`, `domain/policy/PasswordPolicy.java`, `domain/policy/AgePolicy.java` | Reciben/pasan el código |
| 1A | Modificar | `presentation/advice/BusinessExceptionHandler.java` | `code`, `errors[].code`, tabla de restricciones, texto del 500, registro 4xx con `code` |
| 1A | Modificar | `src/test/.../presentation/controller/UserRegistrationControllerTest.java`, `AccountActivationControllerTest.java`, `domain/policy/*Test.java` | Aserciones de `code`; constructores nuevos |
| 1A | Crear | `src/test/.../domain/exception/ErrorCodeTest.java`, `src/test/.../presentation/advice/BusinessExceptionHandlerTest.java` | Vocabulario cerrado; tabla completa; 500 genérico |
| 1B | Crear | `presentation/dto/BirthDateFormat.java`, `presentation/dto/BirthDateFormatValidator.java` | Restricción y validador de formato estricto |
| 1B | Modificar | `presentation/dto/RegisterUserRequest.java` | `birthDate` texto, `pronoun` obligatorio, `toCommand()`, Javadoc completo |
| 1B | Modificar | `presentation/controller/UserRegistrationController.java` | Usa `request.toCommand()`; OpenAPI |
| 1B | Modificar | `domain/policy/PasswordPolicy.java` | Mensaje literal de contraseña común |
| 1B | Crear | `src/main/resources/db/migration/V3__restringir_pronombres.sql` | `ck_cuenta_pronombres_valor` |
| 1B | Modificar | `domain/model/Pronoun.java` | Javadoc: ya no es opcional en el registro |
| 1B | Pruebas | `src/test/.../presentation/dto/BirthDateFormatValidatorTest.java` (nuevo), `UserRegistrationControllerTest`, `PasswordPolicyTest`, `CuentaSchemaMigrationTest`, `AccountRegistrationEndToEndTest` | Ver `tasks.md` |
| No se tocan | `application/**`, `infrastructure/**` (salvo la migración), `pom.xml`, `Dockerfile`, `.github/**`, `docker-compose.yml`, migraciones V1 y V2 | |

## 3. Reutiliza / por qué se crea algo nuevo

- Se **amplían**: la jerarquía de `BusinessException`, `InvalidBirthDateException.Reason` (en vez de crear un enumerado de motivos aparte), los ayudantes `problema` y `campo` del manejador, `ProblemDetailTestSupport`, los dobles `InMemoryFirebaseUserDirectory` y `FirebaseTestConfiguration`, `CuentaSchemaMigrationTest.insertarCuenta`.
- Se **crean**: `ErrorCode` (no existe catálogo) y el validador de fecha (Bean Validation no trae `dd/MM/uuuu` estricto). El validador va en `presentation/dto` y no en `domain` porque el formato de texto es sintaxis del contrato HTTP; la edad sigue en `AgePolicy`.

## 4. Decisiones técnicas y alternativas descartadas

Ver spec, sección 13 (D1 a D6). Resumen de lo que el plan agrega:

- `ErrorCode` sin textos: los textos de los mensajes siguen donde están (anotaciones, excepciones). Así 1A no cambia ningún texto antes de la pregunta 2.
- Tabla `campo + restricción → ErrorCode` como constante package-private del manejador (`Map.ofEntries`), para que la prueba la lea. Un `switch` sobre el nombre de la restricción se descartó: no se puede recorrer en una prueba.
- La restricción de fecha **ignora** el texto vacío o en blanco (devuelve «válido»): la ausencia la reporta `@NotBlank`, así no hay dos errores para el mismo campo.
- El control de que el formato de la restricción y la conversión de `toCommand()` no se separen: ambos usan **el mismo** `DateTimeFormatter` (constante package-private del validador), nunca dos patrones.
- Sin `requestId` hasta que se responda la pregunta 4: la tarjeta T-1A.5 queda **BLOQUEADA por PD-08**; el resto de 1A no depende de ella.

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| El cuerpo de error cambia (aditivo) y Frontend lo consume | Se agrega `code` sin quitar nada; aviso a Frontend en el documento por rol; el 500 cambia de texto (RT-05) |
| Jackson convierte número/booleano a texto para `birthDate` y la restricción lo rechaza por formato | Es el comportamiento deseado (REQ-RV-12); las tarjetas lo comprueban con valores literales y reportan si la versión de Jackson se comporta distinto |
| `Map.of` admite 10 pares y la tabla llega a 10 en 1B | Se usa `Map.ofEntries` |
| El orden de `FieldError` de Spring no está garantizado | Las pruebas buscan por `field` (`$.errors[?(@.field=='x')]`), nunca por posición |
| Un nombre de componente del `record` cambia | La prueba de la tabla recorre los componentes del DTO por reflexión y falla |
| V3 falla en una base con datos distintos de `HE`, `SHE`, `THEY` | El código solo escribe el enumerado; acción para DevOps (pregunta 12); la migración se prueba contra una base con filas |
| 1A + 1B superan 1000 líneas | Dos PR: 1A (≈ 600) y 1B (≈ 550). Si 1A supera 900 líneas medidas, la tarjeta T-1A.5 pasa a un PR aparte |
| Choque con CM-283 (`docs/errores.md`, ADR del `code`) | CM-283 va primero en Cuentas (orden A, B, C1, C2). Si `docs/errores.md` existe en `develop` al abrir el PR, 1A agrega las filas de sus códigos; si no, el PR lo declara y no lo crea |

## 6. Matriz de pruebas del bloque 1

| Prueba | Capa | Requisito |
|---|---|---|
| `ErrorCodeTest.todoCodigoUsaUnaCausaDelVocabularioCerrado` | dominio | REQ-RV-05 |
| `BusinessExceptionHandlerTest.todaRestriccionDelContratoTieneCodigo` | presentación | REQ-RV-06 |
| `BusinessExceptionHandlerTest.unFalloTecnicoDevuelveElMensajeGenericoSinDetalle` (500) | presentación | REQ-RV-04 |
| `UserRegistrationControllerTest` con `code` y `errors[].code` por cada error existente (409, 422 fecha, 422 contraseña, 422 campos, 422 cuerpo ilegible) | presentación | REQ-RV-01, 02 |
| `AccountActivationControllerTest` con `code` (403, 404) | presentación | REQ-RV-01 |
| `BirthDateFormatValidatorTest` (15 rechazos y 4 aceptaciones, vacío y `null` válidos) | presentación/dto | REQ-RV-10, 11 |
| `UserRegistrationControllerTest`: fecha imposible, vacía, solo espacios, número, booleano, arreglo | presentación | REQ-RV-10 a 12 |
| `PasswordPolicyTest`: mensaje literal y `code` | dominio | REQ-RV-13, 14 |
| `UserRegistrationControllerTest`: pronombre ausente y `null` | presentación | REQ-RV-15 |
| `AccountRegistrationEndToEndTest`: fecha `31/02/2000` → 422 sin cuenta ni credencial; pronombre ausente → 422; `HE`, `SHE`, `THEY` → 201 con el valor guardado | integración (Docker) | REQ-RV-10, 15, 16 |
| `CuentaSchemaMigrationTest`: `pronombres = 'OTRO'` viola `ck_cuenta_pronombres_valor`; `NULL`, `HE`, `SHE`, `THEY` pasan | esquema (Docker) | REQ-RV-17 |
| Verificación manual de `/v3/api-docs` con la documentación encendida (archivo adjunto al PR) | manual | REQ-RV-18 |

Justificación del caso manual: comprobar el JSON de OpenAPI exige levantar el contexto completo con base y Firebase; las pruebas actuales del repo no lo hacen y hacerlo agrega un contexto completo de integración a cada ejecución; se deja la evidencia en el PR.

## 7. Orden y dependencias de toda la tarea

1. **1A → 1B** (1B emite códigos nuevos y usa la tabla).
2. Bloque 2 después de 1B (usa el orden de mensajes por campo y el `code`).
3. Bloque 3 después de 2 (el nombre recortado y en NFC entra a `PersonName`).
4. Bloque 4 después de V-03 (pregunta 3); es independiente de 2 y 3 y puede ir en paralelo con ellos.
5. Bloque 5 después de la pregunta 11 (dependencia).
6. Bloque 6 al final: usa las tablas de todos los bloques y exige la respuesta de las preguntas 2, 6 y 10.
Paralelo posible: bloque 4 con 2 y 3; el resto es secuencial porque comparten `RegisterUserRequest` y el manejador.

**Marcas BLOQUEADO del bloque 1:** T-1A.5 (`requestId`) por **PD-08 / pregunta 4**; T-1B.1, T-1B.2 y T-1B.3 por **pregunta 5** (si Paula rechaza D1, se reemplazan por la receta del análisis con la distinción por mensaje, y se avisa antes de escribir código).

## 8. Estimación

1A ≈ 5 h (≈ 600 líneas con pruebas) · 1B ≈ 4,5 h (≈ 550 líneas). Total del bloque 1 ≈ 9,5 h (la HU entera estima 5 h; se informa a Vela).

## 9. Bloque 2 — recorte, NFC y un mensaje por campo (PR 2)

Base: el estado del repositorio **después de fusionar 1A y 1B** (las rutas y líneas de las tarjetas son las de `908112c` más lo que cambian esos PR; se revalidan al empezar). Se apoya en el
`ErrorCode`, en la tabla de restricciones y en `RegisterUserRequest.toCommand()` del bloque 1. Cubre REQ-RV-20 a 25 y CA-1.1.9 a 1.1.13 (espacios), 1.1.16 a 1.1.19, 1.1.22 y 1.1.42.

### 9.1 Cómo se aborda

1. **`SingleLineText`** (dominio): objeto de valor que recorta con el conjunto de `trim` de JavaScript, normaliza a NFC y mide en puntos de código. Es la única definición de «espacio» y de «carácter».
2. **`RegisterUserRequest`**: su constructor compacto aplica `SingleLineText.normalize(...)` a `firstName`, `lastName` y `email` **antes** de que Bean Validation los vea (los demás campos no se tocan). Así `@NotBlank` trata un NBSP solo como vacío y el comando ya lleva el texto recortado y en NFC.
3. **`@CodePointSize(max, message)`** (borde): cuenta puntos de código del texto ya normalizado; sustituye a `@Size` en `firstName` y `lastName` y se agrega a `email` con 254 (nuevo código `EMAIL_TOO_LONG`, texto literal del CA «El correo no puede superar los 254 caracteres.»). Los textos existentes de nombre y apellido no cambian (bloque 6).
4. **`EmailAddress`**: recorta con `SingleLineText`, pasa a minúsculas con `Locale.ROOT`, vuelve a normalizar a NFC y mide en puntos de código (invariante del dominio; sus excepciones y textos no cambian, los etiqueta el bloque 6).
5. **`PasswordPolicy`**: cuenta los puntos de código del texto **normalizado a NFC solo para medirlo**; la contraseña que llega a Firebase no se modifica.
6. **Un mensaje por campo en el orden fijado.** Se consigue por construcción: el borde (vacío, más de N) responde antes que el dominio (caracteres no permitidos —bloque 3—, formato de correo —bloque 6—, corta, larga, común). La matriz de la sección 9.4 lo fija con pruebas de campos que incumplen varias reglas a la vez.

### 9.2 Archivos (paquete base `src/main/java/tech/cameia/cuentas`)

| Acción | Archivo | Qué |
|---|---|---|
| Crear | `domain/model/SingleLineText.java` | Objeto de valor: `normalize(String)` y `length()` |
| Modificar | `domain/model/EmailAddress.java` | Usa `SingleLineText`; longitud en puntos de código; NFC tras pasar a minúsculas |
| Modificar | `domain/policy/PasswordPolicy.java` | Mide en puntos de código de NFC |
| Crear | `presentation/dto/CodePointSize.java`, `presentation/dto/CodePointSizeValidator.java` | Restricción de longitud en puntos de código |
| Modificar | `presentation/dto/RegisterUserRequest.java` | Constructor compacto; `@CodePointSize` en nombre, apellido y correo |
| Modificar | `domain/exception/ErrorCode.java` | `EMAIL_TOO_LONG` |
| Modificar | `presentation/advice/BusinessExceptionHandler.java` | Tabla: `firstName.CodePointSize`, `lastName.CodePointSize`, `email.CodePointSize`; se quitan las claves `.Size` |
| Pruebas | `domain/model/SingleLineTextTest.java` (nuevo), `ValueObjectsTest`, `PasswordPolicyTest`, `presentation/dto/CodePointSizeValidatorTest` (nuevo), `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` | Ver `tasks.md` |
| No se tocan | `application/**`, `infrastructure/**`, `pom.xml`, migraciones | |

### 9.3 Decisiones y alternativas (ver D7 de la spec)

- Constructor compacto del `record` y no `@JsonDeserialize` ni un `Converter`: el `record` se construye siempre por su constructor canónico (Jackson y las pruebas), así la normalización no puede saltarse.
- `SingleLineText.normalize(null)` devuelve `null`: `@NotBlank` sigue reportando la ausencia.
- El límite de la base (`VARCHAR(120)`, que cuenta caracteres de PostgreSQL = puntos de código) coincide con el del código: sin migración.
- No se recortan los demás campos: la contraseña nunca; `phoneNumber` en el bloque 5; `birthDate` es texto exacto (REQ-RV-10).

### 9.4 Matriz de pruebas del bloque 2

| Prueba | Valores literales | Requisito |
|---|---|---|
| `SingleLineTextTest` | `"  Ana  "`→`Ana`; `"\u00A0Ana\u00A0"`→`Ana`; `"\tAna\n"`→`Ana`; `"\uFEFFAna"`→`Ana`; `"\u200BAna"` se conserva; `"María  José"` conserva el doble espacio; `"e\u0301"` → `"é"` (NFC) con `length()` 1; `"𝒜"` (U+1D49C) con `length()` 1; `""`, `"   "`, `"\u00A0"` → `""`; `null` → `null` | REQ-RV-20, 21, 22 |
| `ValueObjectsTest` (correo) | `"  Ana@Correo.CO "` → `ana@correo.co`; con NBSP en los extremos; `"a".repeat(248) + "@b.com"` (254) se acepta; 255 se rechaza; `"𝒜".repeat(126) + "@b.com"` (132 puntos de código, 258 unidades UTF-16) se acepta | REQ-RV-20 a 22, 25 |
| `PasswordPolicyTest` | `"e\u0301".repeat(12)` (12 en NFC, 24 sin normalizar) se acepta; `"e\u0301".repeat(11)` → `PASSWORD_TOO_SHORT`; `"e\u0301".repeat(64)` se acepta; 65 → `PASSWORD_TOO_LONG` | REQ-RV-22 |
| `CodePointSizeValidatorTest` | n−1, n, n+1 para 120 y 254; `null` válido; `𝒜`×120 válido y ×121 inválido | REQ-RV-22 |
| `UserRegistrationControllerTest` | nombre `"  Ana  "` → el comando lleva `Ana`; `"\u00A0"` → `FIRST_NAME_REQUIRED`; 119/120/121 letras `ñ` → 201/201/`FIRST_NAME_TOO_LONG`; 119 `a` + `e\u0301` (NFD) → 201; `𝒜`×120 → 201 y ×121 → `FIRST_NAME_TOO_LONG`; apellido ídem; correo 253/254/255 → 201/201/`EMAIL_TOO_LONG` con texto literal; correo `"   "` → `EMAIL_REQUIRED`; contraseña `"  frase secreta larga  "` llega al comando **sin recortar**; contraseña de 12 espacios → `PASSWORD_REQUIRED` | REQ-RV-20 a 24 |
| Un mensaje por campo | `firstName` = 121 `a` → un elemento `FIRST_NAME_TOO_LONG` (no también `…REQUIRED`); `email` = 255 `a` sin `@` → un elemento `EMAIL_TOO_LONG` (el formato no se evalúa); `firstName` `"   "` + `lastName` de 121 + `email` `"   "` → tres elementos, uno por campo | REQ-RV-23 |
| E2E (Docker) | registrar `"  Ana@Correo.CO "` y luego `ana@correo.co` → 201 y 409; nombre de 120 `ñ` → 201 y la columna `nombre` guarda los 120 sin truncar; nombre en NFD → se guarda en NFC | REQ-RV-21, 25; CA-1.1.16, 1.1.42 |

### 9.5 Riesgos del bloque 2

| Riesgo | Mitigación |
|---|---|
| `Normalizer` sobre textos enormes (carga) | El cuerpo ya está limitado por el servidor (`maxPostSize`, verificado en el bloque 6); `normalize` recorre el texto una vez |
| El constructor compacto cambia el valor que ve `toString()` o el registro de errores | `RegisterUserRequest` no se imprime nunca (lleva la contraseña); la prueba existente `laRespuestaNoDevuelveElCorreoNiLaContrasenia` sigue vigente |
| Un correo con mayúsculas no ASCII (p. ej. `İ`) cambia de forma al pasar a minúsculas | Se vuelve a normalizar a NFC después de `toLowerCase(Locale.ROOT)` y se prueba |
| Orden de las restricciones cuando un campo falla dos a la vez | El manejador conserva un elemento por campo (1A); las restricciones de vacío y de longitud no pueden fallar juntas |

### 9.6 Orden y dependencias

T-2.1 → T-2.2 y T-2.3 (independientes entre sí) → T-2.4 → T-2.5 → T-2.6 → T-2.7. **BLOQUEADO por la pregunta 13** (conjunto de «espacios»): T-2.1 y todo lo que usa `SingleLineText`; si Paula elige la opción (b), el único cambio es el cuerpo del método de recorte y la lista de casos de T-2.1. Estimación: 3 h, ≈ 350 líneas con pruebas.
