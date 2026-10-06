# Plan — CM-36-ValidacionesRegistro

Base: `origin/develop` `908112c`. Estado: pendiente de aprobación de Paula. Este plan contiene el **bloque 1** (dos PR: 1A y 1B). Los planes de los bloques 2 a 6 se
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
