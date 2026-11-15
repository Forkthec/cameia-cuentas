# Plan — Lista de correos permitidos para el registro (CM-360)

## Enfoque

Una política de dominio nueva, `SignupAllowlistPolicy`, con el mismo patrón que `AgePolicy` y `PasswordPolicy`: vive en `domain/policy`, no lleva anotaciones de Spring y la publica una configuración propia, `SignupAllowlistConfiguration`, como `PolicyConfiguration` publica `CurrentPolicy`. `RegisterUserService.validar` la llama justo después de construir `EmailAddress` y antes de las demás políticas (D-3).

## Archivos

| Capa | Archivo | Cambio |
|---|---|---|
| domain | `domain/policy/SignupAllowlistPolicy.java` | Nuevo. Constructor con las entradas ya separadas; valida y normaliza (REQ-LRP-02 a 05). `verify(EmailAddress)` lanza `SignupNotAllowedException` |
| domain | `domain/exception/SignupNotAllowedException.java` | Nuevo. `BusinessException` con `SIGNUP_NOT_ALLOWED` y el mensaje de D-4 |
| domain | `domain/exception/ErrorCode.java` | Agrega `SIGNUP_NOT_ALLOWED` |
| application | `application/service/RegisterUserService.java` | Recibe la política y la llama en `validar` tras `new EmailAddress` |
| infrastructure | `infrastructure/config/SignupProperties.java` | Nuevo. `@ConfigurationProperties("cuentas.signup")` con `allowlist` (texto, puede faltar) |
| infrastructure | `infrastructure/config/SignupAllowlistConfiguration.java` | Nuevo. Bean `signupAllowlistPolicy` que separa por `;`. Clase propia y no `DomainPolicyConfiguration`, para probarla con `ApplicationContextRunner` sin cargar la lista de contraseñas comunes |
| presentation | `presentation/advice/BusinessExceptionHandler.java` | `@ExceptionHandler` 403 «Registro no permitido» |
| presentation | `presentation/controller/UserRegistrationController.java` | `@ApiResponse` 403 en OpenAPI |
| recursos | `application.properties`, `.env.example` | `cuentas.signup.allowlist=${SIGNUP_ALLOWLIST:}` y la variable comentada |
| docs | `docs/errores.md` | Fila de `SIGNUP_NOT_ALLOWED` |

## Qué se reutiliza

- `EmailAddress` es la única definición de normalización y forma: el correo base se valida con `new EmailAddress(entrada)` y el dominio con `new EmailAddress("x" + entrada)`. Una `InvalidEmailException` al validar una entrada se convierte en `IllegalArgumentException` (fallo de arranque, no un 422).
- El manejador ya registra los 4xx sin el correo; solo se agrega el método del 403.

## Decisiones técnicas

- **La separación por `;` va en la configuración, no en el dominio.** El separador es una restricción del CD (H-3), no una regla de negocio. La política recibe una lista. Alternativa descartada: pasar el texto crudo al dominio.
- **Fallo al arrancar con `IllegalArgumentException` desde el bean**, igual que `CurrentPolicy`. El mensaje cita la posición (`entrada 3`), no el valor, porque la lista contiene correos.
- **Comparación por conjuntos** (`Set<String>` de bases y de dominios): búsqueda O(1) y sin expresiones regulares construidas desde la variable.
- **La política vacía es «abierta»**: `verify` no hace nada. No hay una bandera aparte que pueda contradecir la lista.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Una entrada con error de tipeo cierra el registro a esa persona | D-1: las entradas sin forma válida impiden arrancar; un correo válido pero mal escrito no se puede detectar y lo verifica la prueba positiva de DevOps (CM-359) |
| El constructor nuevo de `RegisterUserService` rompe pruebas existentes | Se actualizan en la misma tarea; la política abierta mantiene su comportamiento |
| `UntypedExceptionClassificationTest` exige clasificar la `IllegalArgumentException` nueva | Se clasifica como fallo de arranque, igual que la de `CurrentPolicy` |

## Matriz de pruebas

| Prueba | Clase | Requisito |
|---|---|---|
| Base permitida, subdirección `+`, mayúsculas, entrada con espacios | `SignupAllowlistPolicyTest` | REQ-LRP-02, 10, 11 |
| Mismo dominio no permitido, otro dominio, subdominio | `SignupAllowlistPolicyTest` | REQ-LRP-11, D-2 |
| Entrada de dominio completo | `SignupAllowlistPolicyTest` | REQ-LRP-11 |
| Lista vacía, ausente o solo separadores = abierta | `SignupAllowlistPolicyTest`, `SignupPropertiesTest` | REQ-LRP-05 |
| Entradas mal formadas impiden crear la política, sin el valor en el mensaje | `SignupAllowlistPolicyTest` | REQ-LRP-03, 04 |
| Rechazo antes de Firebase y de la base (contadores del directorio en memoria en cero) y antes de la política de edad | `RegisterUserServiceTest` | REQ-LRP-13 |
| 403 con la forma común, `code`, `title` y `detail` | `UserRegistrationControllerTest` | REQ-LRP-12 |
| Correo mal formado responde 422 aunque haya lista | `UserRegistrationControllerTest` | REQ-LRP-14 |
| Lectura de `cuentas.signup.allowlist` y fallo de arranque | `SignupPropertiesTest` | REQ-LRP-01, 04 |
| Código documentado | `ErrorCodeDocumentationTest` (existente) | — |
