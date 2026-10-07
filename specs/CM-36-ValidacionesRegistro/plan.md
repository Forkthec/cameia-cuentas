# Plan — CM-36-ValidacionesRegistro

Base: `origin/develop` `908112c`. Estado: pendiente de aprobación de Paula. Este plan contiene los seis bloques: el **bloque 1** (dos PR: 1A y 1B, secciones 1 a 8), el **bloque 2** (sección 9) y los **bloques 3 a 6**
(secciones 10 a 14). El orden y las dependencias están en la sección 7. Antes de ejecutar cada bloque se revalidan rutas y líneas contra el estado real de `develop` ese día.

## 1. Cómo se aborda el bloque 1

**1A — formato de error.** Se agrega un catálogo `ErrorCode` junto a las excepciones; `BusinessException` pasa a recibir su código; el único manejador
(`BusinessExceptionHandler`) lo escribe en el miembro `code` de cada respuesta y en cada elemento de `errors[]`, y asigna el código de cada restricción del
contrato con una tabla `campo + restricción`. No cambia el mensaje de ningún campo; cambian tres `detail` (500, 422 con `errors` y el nuevo 503) y el estado cuando
Firebase no está disponible (500 → 503, REQ-RV-09). Además: códigos de 404, 405 y 415 (REQ-RV-08), registro seguro de cada error (REQ-RV-27), clasificación de las restricciones
de la base (REQ-RV-19) y corrección del ejemplo del estándar en la copia de Cuentas. Una prueba garantiza que ninguna restricción del DTO quede sin código y otra, que todo código
esté documentado en `docs/errores.md`.

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
| 1A | Modificar | `presentation/advice/BusinessExceptionHandler.java` | `code`, `errors[].code`, tabla de restricciones, `detail` fijo, texto del 500, `requestId`, registro seguro, manejadores de 404, 405, 415, 503 y de integridad de la base |
| 1A | Crear | `domain/exception/DependencyUnavailableException.java` | Firebase no disponible (503) |
| 1A | Modificar | `infrastructure/client/FirebaseUserDirectoryAdapter.java` | `createUser`, `assignFreePlanClaim` y `deleteUser` lanzan `DependencyUnavailableException` ante indisponibilidad |
| 1A | Modificar | `docs/errores.md`, `docs/estandar-backend.md` | Una fila por código emitido; ejemplo de `detail` del estándar |
| 1A | Modificar | `src/test/.../presentation/controller/UserRegistrationControllerTest.java`, `AccountActivationControllerTest.java`, `domain/policy/*Test.java` | Aserciones de `code`; constructores nuevos |
| 1A | Crear | `src/test/.../domain/exception/ErrorCodeTest.java`, `src/test/.../presentation/advice/BusinessExceptionHandlerTest.java` | Vocabulario cerrado; tabla completa; 500 genérico |
| 1B | Crear | `presentation/dto/BirthDateFormat.java`, `presentation/dto/BirthDateFormatValidator.java` | Restricción y validador de formato estricto |
| 1B | Modificar | `presentation/dto/RegisterUserRequest.java` | `birthDate` texto, `pronoun` obligatorio, `toCommand()`, Javadoc completo |
| 1B | Modificar | `presentation/controller/UserRegistrationController.java` | Usa `request.toCommand()`; OpenAPI |
| 1B | Modificar | `domain/policy/PasswordPolicy.java` | Mensaje literal de contraseña común |
| 1B | Crear | `src/main/resources/db/migration/V3__restringir_pronombres.sql` | `ck_cuenta_pronombres_valor` |
| 1B | Modificar | `domain/model/Pronoun.java` | Javadoc: ya no es opcional en el registro |
| 1B | Pruebas | `src/test/.../presentation/dto/BirthDateFormatValidatorTest.java` (nuevo), `UserRegistrationControllerTest`, `PasswordPolicyTest`, `CuentaSchemaMigrationTest`, `AccountRegistrationEndToEndTest` | Ver `tasks.md` |
| 1A | Crear (pruebas) | `ErrorCodeDocumentationTest`, `FirebaseUserDirectoryAdapterTest` (o ampliar el existente), `CuentaConstraintsClassificationTest` (Testcontainers), `FrameworkErrorsTest` | Ver tarjetas T-1A.8 a T-1A.10 |
| No se tocan | `application/**`, `pom.xml`, `Dockerfile`, `.github/**`, `docker-compose.yml`, migraciones V1 y V2; `infrastructure/**` solo lo de la tabla (y la migración V3 de 1B) | |

## 3. Reutiliza / por qué se crea algo nuevo

- Se **amplían**: la jerarquía de `BusinessException`, `InvalidBirthDateException.Reason` (en vez de crear un enumerado de motivos aparte), los ayudantes `problema` y `campo` del manejador, `ProblemDetailTestSupport`, los dobles `InMemoryFirebaseUserDirectory` y `FirebaseTestConfiguration`, `CuentaSchemaMigrationTest.insertarCuenta`.
- Se **crean**: `ErrorCode` (no existe catálogo) y el validador de fecha (Bean Validation no trae `dd/MM/uuuu` estricto). El validador va en `presentation/dto` y no en `domain` porque el formato de texto es sintaxis del contrato HTTP; la edad sigue en `AgePolicy`.

## 4. Decisiones técnicas y alternativas descartadas

Ver spec, sección 13 (D1 a D6). Resumen de lo que el plan agrega:

- `ErrorCode` sin textos: los textos de los mensajes siguen donde están (anotaciones, excepciones). Así 1A no cambia ningún texto antes de la pregunta 2.
- Tabla `campo + restricción → ErrorCode` como constante package-private del manejador (`Map.ofEntries`), para que la prueba la lea. Un `switch` sobre el nombre de la restricción se descartó: no se puede recorrer en una prueba.
- La restricción de fecha **ignora** el texto vacío o en blanco (devuelve «válido»): la ausencia la reporta `@NotBlank`, así no hay dos errores para el mismo campo.
- El control de que el formato de la restricción y la conversión de `toCommand()` no se separen: ambos usan **el mismo** `DateTimeFormatter` (constante package-private del validador), nunca dos patrones.
- `requestId` desde 1A (PD-08 respondida por Paula el 6-oct-2026); el resto de 1A no depende de ella.

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| El cuerpo de error cambia (aditivo) y Frontend lo consume | Se agrega `code` sin quitar nada; aviso a Frontend en el documento por rol; el 500 cambia de texto (RT-05) |
| Jackson convierte número/booleano a texto para `birthDate` y la restricción lo rechaza por formato | Es el comportamiento deseado (REQ-RV-12); las tarjetas lo comprueban con valores literales y reportan si la versión de Jackson se comporta distinto |
| `Map.of` admite 10 pares y la tabla llega a 10 en 1B | Se usa `Map.ofEntries` |
| El orden de `FieldError` de Spring no está garantizado | Las pruebas buscan por `field` (`$.errors[?(@.field=='x')]`), nunca por posición |
| Un nombre de componente del `record` cambia | La prueba de la tabla recorre los componentes del DTO por reflexión y falla |
| V3 falla en una base con datos distintos de `HE`, `SHE`, `THEY` | El código solo escribe el enumerado; acción para DevOps (pregunta 12); la migración se prueba contra una base con filas |
| 1A + 1B superan 1000 líneas | Dos PR: 1A (≈ 850) y 1B (≈ 550). Si 1A supera 900 líneas medidas, las tarjetas T-1A.8 a T-1A.10 salen como un PR 1A-bis apilado sobre 1A |
| `docs/errores.md`, ADR 0001 y `docs/estandar-backend.md` | Ya existen en `develop` (CM-283, #37). 1A agrega las filas de sus códigos, corrige el ejemplo del `detail` en la copia de Cuentas (Perfil, Gateway y Entrevista la reciben en su PR; mientras tanto la copia de Cuentas es distinta, se declara en el PR) y quita de «Respuestas publicadas que difieren» lo que corrige (500 sin `charset`, texto del 500) |
| El SDK de Firebase no lanza lo que supone la tarjeta T-1A.9 | V-07 antes de escribir el código; si no coincide, se detiene y se reporta |
| `cameia-web` lee el `detail` del 422 del registro | La búsqueda en su `develop` no encontró lectura de `detail` fuera de los mocks; `ADR-0007` de Frontend solo exige `title`, `detail`, `status` y `errors`. Se avisa en el documento a Frontend |
| Rebase de la rama sobre `develop` | La rama solo tiene commits de documentación y no está en el remoto; la tarjeta T-1A.0 la rebasa en local (nunca force-push) |

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
2. **Bloques 2 y 3 en un solo PR** (decidido por Paula el 6-oct-2026: tocan los mismos archivos y suman ≈ 650 líneas), después de 1B. Dentro del PR, primero el recorte y NFC (secciones 9) y después `PersonName` (sección 10), porque el nombre recortado y en NFC entra a `PersonName`. Pregunta 8 decidida: toda letra `\p{L}` y los espacios internos repetidos se unen en uno (`SingleLineText.normalizeName`).
3. Bloque 4 en paralelo con 2 y 3 (fuente decidida: SecLists filtrada, pregunta 3).
4. Bloque 5 cuando se quiera (dependencia aprobada, pregunta 11; tipos de número decididos, pregunta 9).
5. Bloque 6 al final: usa las tablas de todos los bloques, exige que CM-251 esté fusionada (su prueba de CA-1.1.42 espera 200).
Paralelo posible: bloque 4 con 2 y 3; el resto es secuencial porque comparten `RegisterUserRequest` y el manejador.

**Fuera de esta tarea, pero dependen de ella:** CM-251 y el bloque 1 de CM-179 empiezan en cuanto se fusione **1A** (no esperan a 1B, 2 ni 3: decidido por Paula el 6-oct-2026). Lo que se fusione después hace un rebase pequeño.

**Bloque 1 sin bloqueos:** PD-08 y la pregunta 5 (D1) quedaron respondidas por Paula el 6-oct-2026.

## 8. Estimación

1A ≈ 7,5 h (≈ 850 líneas con pruebas) · 1B ≈ 4,5 h (≈ 550 líneas). Total del bloque 1 ≈ 12 h (la HU entera estima 5 h; se informa a Vela cuando Paula lo decida). El bloque 4 va en dos PR: 4a (código) y 4b (solo el archivo de datos).

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
| `Normalizer` sobre textos enormes (carga) | `normalize` recorre el texto una vez; hoy no existe un límite de tamaño del cuerpo (pregunta 14 de la spec, medido en T-6.6): el riesgo es el ya existente, no uno nuevo |
| El constructor compacto cambia el valor que ve `toString()` o el registro de errores | `RegisterUserRequest` no se imprime nunca (lleva la contraseña); la prueba existente `laRespuestaNoDevuelveElCorreoNiLaContrasenia` sigue vigente |
| Un correo con mayúsculas no ASCII (p. ej. `İ`) cambia de forma al pasar a minúsculas | Se vuelve a normalizar a NFC después de `toLowerCase(Locale.ROOT)` y se prueba |
| Orden de las restricciones cuando un campo falla dos a la vez | El manejador conserva un elemento por campo (1A); las restricciones de vacío y de longitud no pueden fallar juntas |

### 9.6 Orden y dependencias

T-2.1 → T-2.2 y T-2.3 (independientes entre sí) → T-2.4 → T-2.5 → T-2.6 → T-2.7. (pregunta 13 respondida: el conjunto de `trim()` de JavaScript) (conjunto de «espacios»): T-2.1 y todo lo que usa `SingleLineText`; si Paula elige la opción (b), el único cambio es el cuerpo del método de recorte y la lista de casos de T-2.1. Estimación: 3 h, ≈ 350 líneas con pruebas.

Las referencias «pregunta N» de los bloques 3 a 6 son las de la sección 15 de la spec.

## 10. Bloque 3 — `PersonName`: nombre y apellido solo con letras (PR 3)

Base: estado después de fusionar 1A, 1B y 2 (revalidar rutas al empezar). Cubre REQ-RV-31, 32; CA-1.1.31, 1.1.39, 1.1.40.

**Cómo se aborda.** Un objeto de valor `PersonName` en `domain/model`, probado sin Spring, que recibe el texto ya recortado y en NFC (`SingleLineText`) y una parte (`FIRST_NAME` o `LAST_NAME`). Si el texto tiene un carácter fuera de `\p{L}`, `\p{M}`, espacio, apóstrofo recto, apóstrofo tipográfico (’) o guion `-`, o no tiene ninguna letra, lanza `InvalidPersonNameException` (nueva, hereda de `BusinessException`) con el código y el texto literal del CA y el nombre del campo. Vacío y más de 120 siguen siendo del borde (bloque 2); el objeto de valor los exige también como invariante defensiva con `IllegalArgumentException` (inalcanzable por la API: el borde responde antes). `RegisterUserService` crea los dos `PersonName` antes de llamar a Firebase y sigue pasando las cadenas a `Account.register`, que conserva su propia comprobación. No cambia `Account` ni la persistencia.

| Acción | Archivo (base `src/main/java/tech/cameia/cuentas`) | Qué |
|---|---|---|
| Crear | `domain/model/PersonName.java` | Objeto de valor con `enum Part` (`FIRST_NAME`, `LAST_NAME`) que lleva el código, el nombre del campo JSON y el texto |
| Crear | `domain/exception/InvalidPersonNameException.java` | Excepción con `getField()` |
| Modificar | `domain/exception/ErrorCode.java` | `FIRST_NAME_INVALID_CHARACTERS`, `LAST_NAME_INVALID_CHARACTERS` |
| Modificar | `application/service/RegisterUserService.java` | Valida los nombres antes de Firebase |
| Modificar | `presentation/advice/BusinessExceptionHandler.java` | Manejador de la excepción nueva |
| Pruebas | `PersonNameTest` (nueva), `RegisterUserServiceTest`, `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` | Ver `tasks.md` |

**Decisiones y descartes.** Objeto de valor en el dominio y no `@Pattern` en el DTO: la regla de negocio se prueba sin Spring y el límite de caracteres de `\p{M}` evita rechazar letras que solo existen con acento combinante. Se permiten todas las letras Unicode y los espacios internos tal como se escribieron (recomendación de la pregunta 8 de la spec, Vela): si Vela restringe el alfabeto, solo cambian la expresión y la lista de casos de T-3.1. Excepción propia y no `IllegalArgumentException`: el manejador de `IllegalArgumentException` desaparece en el bloque 6 y mostraba textos de cualquier librería (defecto 6).

**Riesgos.** Una expresión regular con retroceso sobre 120 caracteres: se usa una clase de caracteres con `+` sin anidar, lineal. Nombres reales con caracteres no admitidos (`.` en «Jr.»): el CA los rechaza; se documenta en el PR.

**Matriz.** Rechaza: `Ana3`, `Pérez_`, `---`, `'`, `’`, `Ana.`, `Ana@`, `<script>`, `12345`, `Ana–Luz` (guion largo U+2013), `Ana\u0000` (NUL), `Ana😀`. Acepta: `María José`, `O'Neill`, `O’Neill`, `Gómez-Ruiz`, `Müller`, `Muñoz`, `A`, `B`, `Ñandú`, `李`, `Åsa`, `María  José` (se guarda `María José`: `normalizeName` une los espacios), un nombre de 120 letras, `e` + acento combinante (queda en NFC). Pregunta 8 decidida por Paula (6-oct-2026). Estimación: 2,5 h, ≈ 300 líneas.

## 11. Bloque 4 — lista de 3000 contraseñas comunes (PR 4)

Cubre REQ-RV-40 a 42; CA-1.1.27 (lista). Fuente decidida (pregunta 3: SecLists filtrada; V-03 verifica licencia y conteo al empezar) (fuente, licencia y entrega a `cameia-web`) en su parte de datos; el código se puede construir y probar antes con una lista de prueba.

**Cómo se aborda.** `PasswordPolicy` deja de tener la lista en el código: recibe un `Set<String>` por su constructor (el patrón del repo: los datos entran por el constructor y los ensambla una configuración). `DomainPolicyConfiguration` carga el recurso `security/common-passwords.txt` (UTF-8, una contraseña por línea, minúsculas, sin líneas vacías) al arrancar, valida que haya al menos 3000 entradas, que cada una tenga 12 o más caracteres y que no haya repetidas, y falla el arranque con un mensaje claro si no. La carga vive en una clase de infraestructura (`CommonPasswordsLoader`) que se prueba sin contexto. Los datos van en un **commit aparte** (para que se revise como datos, no como código) junto con un `README` del recurso con la fuente, la licencia, la fecha y el comando que la generó.

| Acción | Archivo | Qué |
|---|---|---|
| Crear | `infrastructure/config/CommonPasswordsLoader.java` | Lee, normaliza y valida el recurso |
| Modificar | `domain/policy/PasswordPolicy.java` | Constructor `PasswordPolicy(Set<String>)`; se quita el `Set.of` y el constructor sin parámetros |
| Modificar | `infrastructure/config/DomainPolicyConfiguration.java` | Ensambla la política con el cargador |
| Crear (datos) | `src/main/resources/security/common-passwords.txt` y `src/main/resources/security/common-passwords.README.md` | Lista y procedencia |
| Crear (pruebas) | `src/test/resources/security/common-passwords-test.txt` (20 entradas), `CommonPasswordsLoaderTest`, `CommonPasswordsFileTest` | Cargador y archivo real |
| Modificar (pruebas) | `PasswordPolicyTest`, `RegisterUserServiceTest` (donde use `new PasswordPolicy()`) | Pasan una lista pequeña |

**Decisiones y descartes.** Recurso en el classpath y no tabla de base de datos: es dato versionado de solo lectura que `cameia-web` debe poder tomar del repositorio. Se descarta una lista aleatoria de 3000 sin criterio de popularidad: se toman las 3000 más frecuentes de la fuente elegida que cumplen 12 o más caracteres. Si la fuente tiene menos de 3000 candidatas, se completa con una segunda fuente y se registran ambas.

**Riesgos.** Diff de ≈ 3000 líneas de datos (se mide aparte, regla de la sección 17 de la spec); licencia de la fuente; los tres datos de prueba del CA (`123456789012`, `password1234`, `qwertyuiop123`) deben estar en la lista final (si la fuente no los trae, se detiene y se reporta); memoria: 3000 cadenas en un `Set`, despreciable.

**Matriz.** Cargador: archivo válido de 20 → `Set` de 20; línea vacía, entrada de 11 caracteres, entrada con mayúsculas, entrada repetida, archivo con menos de 3000 (en la configuración real), archivo ausente → falla con mensaje que nombra la causa. Política: las tres contraseñas del CA, `PASSWORD1234` y `  password1234  ` se rechazan con `PASSWORD_TOO_COMMON`; una frase larga no. Archivo real: exactamente 3000 entradas, todas de 12 o más puntos de código, minúsculas, sin repetidas, con las tres de prueba. Estimación: 2 h, ≈ 150 líneas de código y ≈ 3000 de datos.

## 12. Bloque 5 — celular con `libphonenumber` (PR 5)

Cubre REQ-RV-50 a 53; CA-1.1.29, 1.1.32, 1.1.37, 1.1.38; V-05. Dependencia aprobada (pregunta 11) y tipos de número decididos (pregunta 9) (dependencia nueva) y por la 9 (tipos de número).

**Cómo se aborda.** (1) Se agrega la dependencia `com.googlecode.libphonenumber:libphonenumber` con la última versión estable que muestre `maven-metadata.xml` de Maven Central ese día. (2) `PhoneNumber` conserva la forma E.164 (`^\+[1-9][0-9]{7,14}$`) y agrega la comprobación de que `PhoneNumberUtil.parse(valor, null)` produce un número válido (`isValidNumber`) cuyo formato E.164 es igual al texto recibido (así `+57 300 000 0000` con espacios se rechaza). Si no lo es, lanza `InvalidPhoneNumberException` (nueva) con el código `PHONE_NUMBER_INVALID_FORMAT` y el texto del CA. (3) `RegisterUserRequest.toCommand()` convierte el celular vacío o en blanco en `null` (sin celular), y recorta los extremos. (4) Una prueba comprueba que todo número de ejemplo de `libphonenumber` (móvil y fijo de cada región) cumple la restricción `ck_cuenta_telefono_e164` de la base: REQ-RV-53.

**Decisión D8 (pendiente de Paula, dentro de la pregunta 11 de la spec).** La librería se usa **directamente en el dominio** (sin puertos ni adaptadores) porque es una función pura, sin E/S ni marco, y es la forma más pequeña. El estándar dice «`domain` no importa Spring, JPA, Rabbit ni Google»; el paquete de la librería es `com.google.i18n.phonenumbers`, así que se pide confirmar que esa regla se refiere a los SDK de Google y no a una librería de cálculo. Alternativa si Paula dice no: un puerto `PhoneNumberChecker` en `domain/port`, un adaptador en `infrastructure/client` y un parámetro más en `RegisterUserService` (que ya tiene 4: se agruparía en un `RegistrationPolicies`); cuesta ≈ 120 líneas más.

| Acción | Archivo | Qué |
|---|---|---|
| Modificar | `pom.xml` | Dependencia nueva (la única del bloque) |
| Modificar | `domain/model/PhoneNumber.java` | Comprobación con `libphonenumber` |
| Crear | `domain/exception/InvalidPhoneNumberException.java` | Con `getField()` = `phoneNumber` |
| Modificar | `domain/exception/ErrorCode.java` | `PHONE_NUMBER_INVALID_FORMAT` |
| Modificar | `presentation/dto/RegisterUserRequest.java` | `toCommand()` convierte vacío en `null` |
| Modificar | `presentation/advice/BusinessExceptionHandler.java` | Manejador de la excepción nueva |
| Pruebas | `ValueObjectsTest`, `PhoneNumberDatabaseCompatibilityTest` (nueva), `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest` | Ver `tasks.md` |

**Riesgos.** La restricción de la base exige entre 8 y 15 dígitos en total: un número válido más corto (algunos territorios tienen números de 7 dígitos con el indicativo) haría fallar el `INSERT` con un 500; la prueba de compatibilidad lo detecta y, si ocurre, se detiene y se pregunta (migración V4 que relaje el mínimo, o rechazarlo). Diferencias de versión entre la librería Java y `libphonenumber-js`: V-05 las compara con los valores del CA y las informa a Frontend. Tamaño del artefacto (la librería pesa ≈ 0,5 MB): aceptable. Rendimiento: `PhoneNumberUtil.getInstance()` es un singleton; no hay E/S.

**Matriz.** Acepta: `+573000000000`, `+34612345678`, un fijo colombiano (`+576012345678`), `+14155552671`. Rechaza (`PHONE_NUMBER_INVALID_FORMAT`): `12345`, `+57300`, `3000000000` (sin `+`), `+57 300 000 0000` (espacios), `+99912345678` (código de país inexistente), `+5730000000000000` (demasiado largo), `+573000000000abc`. Sin celular: ausente, `null`, `""`, `"   "`. Estimación: 3 h, ≈ 350 líneas.

## 13. Bloque 6 — etiquetas, textos y pruebas que faltan (PR 6)

Cubre REQ-RV-30, 60 a 65; CA-1.1.2 a 1.1.7, 1.1.20 a 1.1.26, 1.1.41; V-01, V-06. Preguntas 2, 6 y 10 cerradas (textos de los CA, 422 y «Selecciona una opción.») para sus tarjetas T-6.3, T-6.2 y T-6.2 respectivamente; T-6.1, T-6.4, T-6.5 y T-6.6 no dependen de ellas.

**Cómo se aborda.** (1) `EmailAddress` lanza `InvalidEmailException` (nueva, con `EMAIL_INVALID_FORMAT` y `EMAIL_TOO_LONG` como invariante) y deja de lanzar `IllegalArgumentException`. (2) Se **elimina** el manejador de `IllegalArgumentException` (defecto 6): una `IllegalArgumentException` inesperada pasa a ser un 500 genérico con `INTERNAL_ERROR`, que es lo correcto. (3) `cuerpoIlegible` distingue: si la ruta del error de Jackson apunta a `pronoun`, responde un elemento `{field:"pronoun", code:"PRONOUN_INVALID_VALUE"}`; si no, `REQUEST_BODY_INVALID_FORMAT` con el texto «Revisa el formato de los datos enviados.» (sin la afirmación sobre la fecha). (4) Se rechaza el número como valor de un enumerado con la propiedad de Jackson que corresponda (V-06 comprueba su nombre). (5) Se alinean los textos con el catálogo de la sección 5 de la spec (tabla de la tarjeta T-6.3). (6) Se completan las pruebas de edad con reloj fijo y las de los casos 1.1.41 y 1.1.42.

| Acción | Archivo | Qué |
|---|---|---|
| Crear | `domain/exception/InvalidEmailException.java` | Con `getField()` = `email` |
| Modificar | `domain/model/EmailAddress.java` | Lanza la excepción nueva |
| Modificar | `domain/exception/ErrorCode.java` | `EMAIL_INVALID_FORMAT`, `PRONOUN_INVALID_VALUE` |
| Modificar | `presentation/advice/BusinessExceptionHandler.java` | Manejador de `InvalidEmailException`; se elimina `valorInvalido`; `cuerpoIlegible` |
| Modificar | `application.properties` | Propiedad de Jackson contra números como enumerado |
| Modificar | `RegisterUserRequest`, `PasswordPolicy`, `AgePolicy`, `EmailAlreadyRegisteredException`, `Pronoun` (Javadoc) | Textos del catálogo |
| Pruebas | `AgePolicyTest`, `ValueObjectsTest`, `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest`, `BusinessExceptionHandlerTest` | Ver `tasks.md` |

**Decisiones y descartes.** Eliminar el manejador de `IllegalArgumentException` en vez de dejarlo: cada objeto de valor ya tiene su excepción tipada, y dejarlo reintroduce la fuga de mensajes de librerías. Mensajes del catálogo: los pide el CA y RT-01 (pregunta 2 de la spec); si Vela dice que no, esta tarjeta se omite y los textos quedan como están.

**Hallazgo sobre el tamaño del cuerpo.** No existe hoy un límite de tamaño del cuerpo de `POST /api/v1/users`: `server.tomcat.max-http-form-post-size` aplica solo a formularios, no a JSON; el servidor lee el cuerpo completo (OWASP API4, consumo de recursos). En este bloque solo se **mide** (T-6.6); el límite se propone como tarea aparte (pregunta 14 de la spec).

**Riesgos.** El cambio de textos rompe las pruebas de Frontend que comparan texto (se avisa); la propiedad contra números como enumerado puede no existir con ese nombre en Jackson 3 (V-06: si no existe, se detiene y se propone un deserializador de enumerados estricto); los 422 sin `field` que hoy ve Frontend ahora llevan `field` (aditivo).

**Matriz.** Correo: `ana`, `ana@correo`, `ana@@correo.co`, `ana@correo..co`, `ana @correo.co` → `EMAIL_INVALID_FORMAT` en `email`; válidos `ana@correo.co`, `ana.perez+cameia@correo.com`, `ANA@Correo.CO`. Pronombre: `OTRO`, `he`, `""`, `1`, `true` → `PRONOUN_INVALID_VALUE`. Edad con reloj fijo: cumple 18 hoy (acepta) y mañana (rechaza); cumple 111 mañana (acepta) y hoy (rechaza `BIRTH_DATE_OUT_OF_RANGE`); nacido el 29/02/2000 con hoy 28/02/2018 (17 años, rechaza) y 01/03/2018 (18, acepta); nacido el 31/12 y el 01/01; fecha de mañana (`BIRTH_DATE_IN_THE_FUTURE`). CA-1.1.41: contraseña `mi clave larga 🙂` (16 puntos de código) se acepta sin recortar. CA-1.1.42: `  Ana@Correo.CO ` y luego `ana@correo.co` → 201 y 409. Estimación: 5 h, ≈ 700 líneas.

## 14. Orden y dependencias de los bloques 3 a 6

3 después de 2 · 4 independiente (puede ir en paralelo con 2 y 3 cuando haya fuente) · 5 después de 3 (comparten el manejador y `toCommand()`) · 6 al final. Los cuatro tocan `BusinessExceptionHandler`, `RegisterUserRequest` y `ErrorCode`: un solo PR abierto a la vez en esos archivos para no tener choques de fusión.
