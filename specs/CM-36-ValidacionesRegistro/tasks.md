# Tareas — CM-36-ValidacionesRegistro

Estado: sin ejecutar; spec y plan pendientes de aprobación de Paula. Este archivo tiene las tarjetas del **bloque 1** (PR 1A y PR 1B) y del **bloque 2** (PR 2). Las tarjetas de los bloques 3 a 6 se
agregan aquí antes de ejecutar cada bloque. Se marca `[x]` solo con la salida real de las pruebas pegada en el informe del PR.

## Reglas para todas las tarjetas (el modelo que ejecuta no lee la spec ni el plan)

- Ruta base del código: `src/main/java/tech/cameia/cuentas/`; pruebas: `src/test/java/tech/cameia/cuentas/`. Identificadores en inglés; Javadoc, comentarios, mensajes y `@DisplayName` en español; **sin** `CM-NNN` ni rutas a otros archivos en comentarios; sin abreviaturas; en una clase existente se mantiene su estilo (nombres de pruebas en español camelCase como `elCorreoRepetidoDevuelve…`).
- Prohibido: agregar dependencias, tocar las migraciones V1 y V2, registrar contraseñas, correos o tokens, cambiar un texto de mensaje que la tarjeta no nombre, refactorizar fuera de la tarjeta.
- Comandos: `./mvnw.cmd -q -B -Dtest=<Clase> test` para una clase; `./mvnw.cmd -B test` para la suite; `./mvnw.cmd -B clean verify` al cerrar (genera `target/site/jacoco/`). Las pruebas con Testcontainers se omiten sin Docker (`disabledWithoutDocker`): si se omiten, decirlo en el informe.
- **Detenerse y reportar** si: la tarjeta contradice el código real, falta un dato, una prueba existente se rompe sin causa clara, el comportamiento de una librería difiere de lo que la tarjeta afirma, o hace falta algo no listado. No improvisar el diseño.
- Definición de terminado de cada tarjeta: pruebas nuevas en verde, suite completa en verde, `LayeredArchitectureTest` en verde, diff dentro de lo estimado.
- Rama: `CM-36-validaciones-registro` desde `origin/develop`. PR 1B sale de una rama nueva `CM-36-validaciones-registro-fecha-pronombre` creada desde `develop` **después** de fusionar 1A (o apilada sobre 1A si Paula lo autoriza).
- Mensaje de commit: `CM-36 | <tipo>(cuentas): <resultado> [IA-ASISTIDO]`.

---

# PR 1A — formato de error con `code`

## [ ] T-1A.1 · Catálogo `ErrorCode` — ≤ 20 min, ≈ 90 líneas

- **Cubre:** REQ-RV-05. **Crear:** `domain/exception/ErrorCode.java` y `src/test/.../domain/exception/ErrorCodeTest.java`. **No tocar** nada más.
- **Código de referencia** (cada constante con su Javadoc de una línea; agregar solo estas, ni una más):
  ```java
  package tech.cameia.cuentas.domain.exception;

  /**
   * Códigos de error estables que el servicio devuelve en el miembro {@code code} de cada respuesta de error.
   *
   * <p>Un código nunca se renombra ni se reutiliza una vez publicado. La forma es
   * {@code <SUJETO>_<CAUSA>} con un vocabulario de causas cerrado; {@code VALIDATION_FAILED} e
   * {@code INTERNAL_ERROR} son las dos excepciones, porque describen la operación entera.</p>
   */
  public enum ErrorCode {
      /** La petición tiene campos inválidos; el detalle va en la lista {@code errors}. */
      VALIDATION_FAILED,
      /** Falló algo que la persona no puede corregir. */
      INTERNAL_ERROR,
      /** Falta el nombre. */ FIRST_NAME_REQUIRED,
      /** El nombre supera 120 caracteres. */ FIRST_NAME_TOO_LONG,
      /** Falta el apellido. */ LAST_NAME_REQUIRED,
      /** El apellido supera 120 caracteres. */ LAST_NAME_TOO_LONG,
      /** Falta la fecha de nacimiento. */ BIRTH_DATE_REQUIRED,
      /** La fecha de nacimiento está en el futuro. */ BIRTH_DATE_IN_THE_FUTURE,
      /** La persona aún no cumple 18 años. */ BIRTH_DATE_UNDERAGE,
      /** La fecha implica una edad que ninguna persona alcanza. */ BIRTH_DATE_OUT_OF_RANGE,
      /** Falta el correo. */ EMAIL_REQUIRED,
      /** El correo ya tiene una cuenta. */ EMAIL_ALREADY_REGISTERED,
      /** Falta la contraseña. */ PASSWORD_REQUIRED,
      /** La contraseña tiene menos de 12 caracteres. */ PASSWORD_TOO_SHORT,
      /** La contraseña tiene más de 64 caracteres. */ PASSWORD_TOO_LONG,
      /** La contraseña figura entre las comunes. */ PASSWORD_TOO_COMMON,
      /** El cuerpo de la petición no se puede interpretar. */ REQUEST_BODY_INVALID_FORMAT,
      /** Falta el encabezado de identidad que pone el Gateway. */ IDENTITY_REQUIRED,
      /** El usuario no tiene cuenta local. */ ACCOUNT_NOT_FOUND,
      /** El correo aún no está verificado. */ EMAIL_NOT_VERIFIED
  }
  ```
  (Escribir cada Javadoc en su propia línea, como el resto del repo; aquí se compactó para ahorrar espacio.)
- **Prueba** `ErrorCodeTest` (en el estilo de `AgePolicyTest`): `@ParameterizedTest @EnumSource(ErrorCode.class) void todoCodigoUsaUnaCausaDelVocabularioCerrado(ErrorCode codigo)`: si el código es `VALIDATION_FAILED` o `INTERNAL_ERROR`, `return`; si no, `assertThat(codigo.name()).matches("^[A-Z]+(_[A-Z]+)+$")` y `assertThat(CAUSAS).anyMatch(causa -> codigo.name().endsWith("_" + causa))`, con `CAUSAS = List.of("REQUIRED","TOO_SHORT","TOO_LONG","INVALID_FORMAT","INVALID_CHARACTERS","INVALID_VALUE","OUT_OF_RANGE","IN_THE_FUTURE","UNDERAGE","NOT_FOUND","ALREADY_REGISTERED","NOT_VERIFIED")`. Más `@Test void noHayCodigosRepetidos` que compruebe `Arrays.stream(values()).map(Enum::name).distinct().count()` igual a `values().length`.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=ErrorCodeTest test` en verde.

## [ ] T-1A.2 · Las excepciones de negocio llevan su código — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-RV-01 (origen del código). **Modificar:** `domain/exception/BusinessException.java`, `EmailAlreadyRegisteredException.java`, `AccountNotFoundException.java`, `EmailNotVerifiedException.java`, `InvalidBirthDateException.java`, `WeakPasswordException.java`, `domain/policy/PasswordPolicy.java` (líneas 66, 70, 74), `domain/policy/AgePolicy.java` (sin cambio de código; se comprueba que compila), y la prueba `presentation/controller/UserRegistrationControllerTest.java` (línea 98).
- **`BusinessException`** (reemplaza el constructor de un parámetro; se quita el viejo, solo lo usaban las subclases):
  ```java
  private final ErrorCode errorCode;

  /**
   * @param errorCode código estable del error
   * @param mensaje texto en español, sin credenciales ni datos de la petición
   */
  protected BusinessException(ErrorCode errorCode, String mensaje) {
      super(mensaje);
      this.errorCode = errorCode;
  }

  /** @return código estable del error */
  public ErrorCode getErrorCode() { return errorCode; }
  ```
- **Subclases sin parámetros:** `super(ErrorCode.EMAIL_ALREADY_REGISTERED, MENSAJE)`; `ACCOUNT_NOT_FOUND`; `EMAIL_NOT_VERIFIED`.
- **`InvalidBirthDateException.Reason`:** cada constante lleva su código y el constructor lo pasa al padre:
  ```java
  public enum Reason {
      /** La persona todavía no cumple 18 años. */ UNDERAGE(ErrorCode.BIRTH_DATE_UNDERAGE),
      /** La fecha está en el futuro, así que es un dato erróneo. */ IN_THE_FUTURE(ErrorCode.BIRTH_DATE_IN_THE_FUTURE),
      /** La fecha implica una edad que ninguna persona alcanza. */ IMPLAUSIBLE(ErrorCode.BIRTH_DATE_OUT_OF_RANGE);

      private final ErrorCode code;
      Reason(ErrorCode code) { this.code = code; }
      ErrorCode code() { return code; }
  }
  public InvalidBirthDateException(Reason reason, String mensaje) {
      super(reason.code(), mensaje);
      this.reason = reason;
  }
  ```
  (Mantener el campo `reason` y `getReason()`; se quita `transient` solo si el compilador o la prueba de arquitectura no lo exigen: no cambiarlo.)
- **`WeakPasswordException`:** constructor `(ErrorCode errorCode, String mensaje)` → `super(errorCode, mensaje)`. En `PasswordPolicy` los tres lanzamientos pasan `ErrorCode.PASSWORD_TOO_SHORT`, `PASSWORD_TOO_LONG` y `PASSWORD_TOO_COMMON`; **los textos no cambian** en esta tarjeta.
- **Prueba existente a actualizar:** `UserRegistrationControllerTest` línea 98 `new WeakPasswordException("La contraseña debe tener al menos 12 caracteres")` → `new WeakPasswordException(ErrorCode.PASSWORD_TOO_SHORT, "La contraseña debe tener al menos 12 caracteres")` (agregar el import).
- **Pruebas nuevas** en `PasswordPolicyTest` (junto a las existentes, mismo estilo): `lasTresCausasDeContraseniaDebilLlevanSuCodigo`: contraseña `"corta"` → `getErrorCode()` es `PASSWORD_TOO_SHORT`; 65 caracteres → `PASSWORD_TOO_LONG`; `"123456789012"` → `PASSWORD_TOO_COMMON`. En `AgePolicyTest`: una prueba por `Reason` que compruebe `getErrorCode()` (`BIRTH_DATE_UNDERAGE`, `BIRTH_DATE_IN_THE_FUTURE`, `BIRTH_DATE_OUT_OF_RANGE`) usando el reloj fijo que ya usa esa clase.
- **Trampa:** `InvalidBirthDateException.Reason.code()` es de alcance de paquete; `BusinessException` y la excepción están en el mismo paquete, así que compila. No hacerlo público.
- **Verificación:** `./mvnw.cmd -B -Dtest='PasswordPolicyTest,AgePolicyTest,UserRegistrationControllerTest,AccountActivationControllerTest' test` en verde (el controlador aún no emite `code`, solo debe compilar y seguir pasando).

## [ ] T-1A.3 · El manejador escribe `code` y `errors[].code` — ≤ 30 min, ≈ 110 líneas

- **Cubre:** REQ-RV-01, 02, 04, 06. **Modificar:** `presentation/advice/BusinessExceptionHandler.java`. **No tocar** controladores ni DTO.
- **Cambios (antes → después):**
  1. Constante nueva (package-private, para que la lea la prueba):
     ```java
     /** Código de cada restricción del contrato, con la clave {@code campo.Restriccion}. */
     static final Map<String, ErrorCode> FIELD_ERROR_CODES = Map.ofEntries(
             Map.entry("firstName.NotBlank", ErrorCode.FIRST_NAME_REQUIRED),
             Map.entry("firstName.Size", ErrorCode.FIRST_NAME_TOO_LONG),
             Map.entry("lastName.NotBlank", ErrorCode.LAST_NAME_REQUIRED),
             Map.entry("lastName.Size", ErrorCode.LAST_NAME_TOO_LONG),
             Map.entry("birthDate.NotNull", ErrorCode.BIRTH_DATE_REQUIRED),
             Map.entry("email.NotBlank", ErrorCode.EMAIL_REQUIRED),
             Map.entry("password.NotBlank", ErrorCode.PASSWORD_REQUIRED));
     ```
  2. `problema(HttpStatus estado, String titulo, String detalle)` pasa a `problema(HttpStatus estado, String titulo, String detalle, ErrorCode codigo)`: agrega `problema.setProperty("code", codigo.name())` y, **solo si** `estado.is4xxClientError()`, `logger.warn("Error atendiendo la petición [status={}, code={}]", estado.value(), codigo)` (sin datos de la petición). Los 5xx ya se registran completos en `falloInterno`.
  3. `campo(String nombre, String mensaje)` pasa a `campo(String nombre, ErrorCode codigo, String mensaje)` y devuelve `Map.of("field", nombre, "code", codigo.name(), "message", mensaje == null ? "Valor no válido" : mensaje)`.
  4. Cada método usa el código de la excepción: `correoRepetido` → `error.getErrorCode()`; `fechaInvalida` y `contraseniaDebil` → `error.getErrorCode()` en el `problema` **y** en `campo(...)`; `correoSinVerificar` y `cuentaInexistente` → `error.getErrorCode()`.
  5. `camposInvalidos`: código de nivel superior `VALIDATION_FAILED`; **un elemento por campo** (el primer `FieldError` de cada campo) con
     ```java
     private ErrorCode codigoDe(FieldError fallo) {
         return FIELD_ERROR_CODES.getOrDefault(fallo.getField() + "." + fallo.getCode(), ErrorCode.VALIDATION_FAILED);
     }
     ```
     Recorrer `error.getBindingResult().getFieldErrors()` con un `LinkedHashMap<String, Map<String,String>>` y `putIfAbsent(fallo.getField(), campo(fallo.getField(), codigoDe(fallo), fallo.getDefaultMessage()))`; `errors` = `new ArrayList<>(mapa.values())`.
  6. `cuerpoIlegible` → código `REQUEST_BODY_INVALID_FORMAT`, **sin cambiar el texto** (el bloque 6 lo cambia). `peticionIncompleta` → `IDENTITY_REQUIRED`. `valorInvalido` (`IllegalArgumentException`) → `VALIDATION_FAILED`, sin `errors` y texto sin cambio (defecto conocido, lo resuelven los bloques 3, 5 y 6). `falloInterno` → `INTERNAL_ERROR` y `detalle` = `"Ocurrió un error. Inténtalo de nuevo."`.
- **Trampas:** el nombre de la restricción que devuelve `FieldError.getCode()` es el nombre simple de la anotación (`NotBlank`, `NotNull`, `Size`); `Map.of` solo admite 10 pares, por eso `Map.ofEntries`; el orden de `getFieldErrors()` no está garantizado, no se asume.
- **Verificación:** `./mvnw.cmd -B -Dtest='UserRegistrationControllerTest,AccountActivationControllerTest' test` en verde (los textos y estados no cambiaron).

## [ ] T-1A.4 · Pruebas del formato de error — ≤ 30 min, ≈ 160 líneas

- **Cubre:** REQ-RV-01, 02, 04, 06. **Crear:** `src/test/.../presentation/advice/BusinessExceptionHandlerTest.java` (mismo paquete que el manejador, para ver su constante). **Modificar:** `UserRegistrationControllerTest`, `AccountActivationControllerTest`.
- **`BusinessExceptionHandlerTest`:**
  1. `todaRestriccionDelContratoTieneCodigo`: para cada `Field campo : RegisterUserRequest.class.getDeclaredFields()` y cada `Annotation anotacion : campo.getAnnotations()` cuya `annotationType().isAnnotationPresent(jakarta.validation.Constraint.class)`, `assertThat(BusinessExceptionHandler.FIELD_ERROR_CODES).containsKey(campo.getName() + "." + anotacion.annotationType().getSimpleName())`. Y a la inversa: cada clave de la tabla corresponde a una restricción existente (evita códigos huérfanos).
  2. `unFalloTecnicoDevuelveElMensajeGenericoSinDetalle`: `MockMvc` con `standaloneSetup(new ControladorQueFalla())` y `setControllerAdvice(ProblemDetailTestSupport.manejadorDeErrores())`; el controlador es una clase interna `@RestController` con `@GetMapping("/falla")` que lanza `new IllegalStateException("detalle interno secreto")`. Esperar: estado 500; `$.code` = `INTERNAL_ERROR`; `$.detail` = `Ocurrió un error. Inténtalo de nuevo.`; el cuerpo **no** contiene `secreto` ni `IllegalStateException`.
  3. `unValorInvalidoDelDominioDevuelveCodigoDeValidacion`: controlador interno que lanza `new IllegalArgumentException("El correo electrónico no tiene un formato válido")` → 422, `$.code` = `VALIDATION_FAILED`.
- **`UserRegistrationControllerTest` (agregar a las pruebas existentes, no duplicarlas):**
  - 409 (línea 73): `jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED")`.
  - menor de edad (línea 84): `$.code` = `BIRTH_DATE_UNDERAGE`; `$.errors[0].code` = `BIRTH_DATE_UNDERAGE`; `$.errors[0].field` = `birthDate`.
  - contraseña débil (línea 96): `$.errors[0].code` = `PASSWORD_TOO_SHORT`.
  - campo obligatorio (línea 106): `$.code` = `VALIDATION_FAILED` y `$.errors[?(@.field=='firstName')].code` contiene `FIRST_NAME_REQUIRED` (buscar por `field`, nunca por posición).
  - fecha con otro formato (línea 120) y pronombre `OTRO` (línea 132): `$.code` = `REQUEST_BODY_INVALID_FORMAT`.
  - Un caso nuevo `variosCamposInvalidosDevuelvenUnElementoPorCampo`: cuerpo con `firstName` y `lastName` en blanco y `email` en blanco → `$.errors.length()` = 3 y un código por campo (`FIRST_NAME_REQUIRED`, `LAST_NAME_REQUIRED`, `EMAIL_REQUIRED`).
  - Un caso nuevo `unNombreEnBlancoYUnaFechaImposibleNoDuplicanElCampo`: `firstName` = `"   "` → exactamente un elemento con `field` = `firstName` (aunque fallen dos restricciones del mismo campo en otras tarjetas).
- **`AccountActivationControllerTest`:** línea 62 (403) → `$.code` = `EMAIL_NOT_VERIFIED`; línea 73 (404) → `$.code` = `ACCOUNT_NOT_FOUND`.
- **Verificación:** `./mvnw.cmd -B -Dtest='BusinessExceptionHandlerTest,UserRegistrationControllerTest,AccountActivationControllerTest,ErrorCodeTest' test` en verde.

## [ ] T-1A.5 · `requestId` en el cuerpo y en el encabezado — ≤ 30 min, ≈ 70 líneas — **BLOQUEADA por PD-08 (pregunta 4)**

Solo si Paula responde «sí, desde esta tarea». Si responde «no», se omite y el `requestId` lo agrega la pieza P2-05 de CM-283.

- **Cubre:** REQ-RV-03. **Modificar:** `BusinessExceptionHandler.java` y `BusinessExceptionHandlerTest`.
- **Código de referencia** (dentro de `problema(...)`; sin cambiar las firmas de los manejadores):
  ```java
  private static final Pattern REQUEST_ID_VALIDO = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

  /** Identificador de trazabilidad: el del Gateway si es válido; si no, uno nuevo. */
  private String resolverRequestId() {
      ServletRequestAttributes atributos = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
      String recibido = atributos.getRequest().getHeader("X-Request-Id");
      String requestId = recibido != null && REQUEST_ID_VALIDO.matcher(recibido).matches()
              ? recibido : UUID.randomUUID().toString();
      if (atributos.getResponse() != null) {
          atributos.getResponse().setHeader("X-Request-Id", requestId);
      }
      return requestId;
  }
  ```
  y `problema.setProperty("requestId", requestId)`; el `logger.warn` de T-1A.3 agrega `requestId`.
- **Pruebas:** encabezado válido `abc-123` → mismo valor en `$.requestId` y en el encabezado de la respuesta; ausente → UUID v4 (`matches("[0-9a-f-]{36}")`) y mismo valor en el encabezado; `abc 123` (con espacio) y 65 caracteres → UUID nuevo; el valor nunca es el enviado cuando no cumple la expresión.
- **Trampa:** con `standaloneSetup`, `RequestContextHolder` está disponible durante el despacho; si lanza `IllegalStateException`, **detenerse** y reportar (alternativa: recibir `HttpServletRequest` y `HttpServletResponse` como parámetros de cada manejador).

## [ ] T-1A.6 · Prueba de punta a punta y revisión del tipo de contenido — ≤ 20 min, ≈ 30 líneas

- **Cubre:** REQ-RV-01 de punta a punta. **Modificar:** `AccountRegistrationEndToEndTest` (`elSegundoRegistroConElMismoCorreoRespondeConflicto`, línea 83).
- **Agregar** a esa prueba: el cuerpo de la respuesta contiene `"code":"EMAIL_ALREADY_REGISTERED"`; y el encabezado `Content-Type` es `application/problem+json` con `charset=UTF-8` (`assertThat(respuesta.getHeaders().getContentType().toString()).containsIgnoringCase("charset=UTF-8")`).
- **Si la aserción del charset falla** (es posible: Spring y Tomcat no siempre lo declaran en `problem+json`): **no corregirlo aquí**. Dejar la aserción comentada, pegar el valor real del encabezado en el informe y reportarlo: es un defecto de ASVS 4.1.1 para decidir (misma clase que el del Gateway) y la corrección va en su propia tarea.
- **Verificación:** con Docker en marcha, `./mvnw.cmd -B -Dtest=AccountRegistrationEndToEndTest test` en verde; sin Docker, decir que se omitió.

## [ ] T-1A.7 · Cierre del PR 1A — ≤ 30 min

- `./mvnw.cmd -B clean verify` con salida real; número de pruebas; cobertura JaCoCo de `BusinessExceptionHandler`, `ErrorCode`, `BusinessException`, `InvalidBirthDateException`, `WeakPasswordException`, `PasswordPolicy`: líneas y ramas reales (meta ≥ 90 %), cada una sin cubrir con su razón; la cobertura global del repo no baja.
- Revisión (`backend-estandar` §6): `/simplify`, `/code-review high`, autochequeo (¿algún `getMessage()` de librería llega al cliente? ¿algún log con dato personal? ¿algún comentario con `CM-NNN`?).
- Documentación: si `docs/errores.md` existe en `develop`, agregar una fila por código emitido (`Código | HTTP | Mensaje | Origen | Prueba`); si no existe, declararlo en el PR (lo crea otra tarea). Aviso a Frontend: el cuerpo de error agrega `code` y `errors[].code`; el 500 cambia de texto; nada se elimina.
- Entrega: commits `CM-36 | feat(cuentas): código de error en las respuestas de registro [IA-ASISTIDO]`; PR con el título `CM-36 | feat(cuentas): código de error estable en las respuestas de error de Cuentas [IA-ASISTIDO]`; descripción con la plantilla completa, atributos de calidad (seguridad, compatibilidad de contrato, mantenibilidad, observabilidad), qué es mecánico (cambios de constructor en las excepciones y pruebas) y qué importa revisar (`BusinessExceptionHandler`, `ErrorCode`). Tarjeta de Jira a «En revisión» solo al abrir el PR.

---

# PR 1B — fecha estricta, contraseña común, pronombre obligatorio

## [ ] T-1B.1 · Restricción de formato de fecha — ≤ 30 min, ≈ 120 líneas — **BLOQUEADA por la pregunta 5 (D1)**

- **Cubre:** REQ-RV-10, 11. **Crear:** `presentation/dto/BirthDateFormat.java`, `presentation/dto/BirthDateFormatValidator.java`, `src/test/.../presentation/dto/BirthDateFormatValidatorTest.java`.
- **Código de referencia:**
  ```java
  /** Restricción: el texto, si no está vacío, es una fecha real con el formato {@code dd/MM/uuuu}. */
  @Documented
  @Constraint(validatedBy = BirthDateFormatValidator.class)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @Retention(RetentionPolicy.RUNTIME)
  public @interface BirthDateFormat {
      String message() default "Formato de fecha inválido.";
      Class<?>[] groups() default {};
      Class<? extends Payload>[] payload() default {};
  }

  /** Valida el formato de la fecha de nacimiento sin decidir nada sobre la edad. */
  public class BirthDateFormatValidator implements ConstraintValidator<BirthDateFormat, String> {

      /** Formato estricto: {@code uuuu} es el año calendario; con {@code yyyy} la resolución estricta rechaza toda fecha. */
      static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu")
              .withResolverStyle(ResolverStyle.STRICT).withLocale(Locale.ROOT);

      @Override
      public boolean isValid(String value, ConstraintValidatorContext context) {
          // La ausencia o el texto en blanco la reporta @NotBlank: así un campo no tiene dos errores
          return value == null || value.isBlank() || parse(value).isPresent();
      }

      /** @return la fecha, o vacío si el texto no es una fecha real en el formato exigido */
      static Optional<LocalDate> parse(String text) {
          try {
              return Optional.of(LocalDate.parse(text, FORMAT));
          } catch (DateTimeParseException fallo) {
              return Optional.empty();
          }
      }
  }
  ```
  Javadoc completo en español en las tres piezas (qué hace, por qué `uuuu`, por qué ignora el vacío).
- **Pruebas** (`BirthDateFormatValidatorTest`, `Validation.buildDefaultValidatorFactory().getValidator()` sobre un `record Caso(@BirthDateFormat String fecha)` interno):
  - Inválidas (`@ParameterizedTest @ValueSource`): `31/02/2000`, `29/02/2001`, `31/04/2000`, `15/13/2000`, `1/1/2000`, `00/01/2000`, `01/00/2000`, `abc`, `2000-01-01`, `12-04-1995`, `12/04/95`, ` 12/04/1995`, `12/04/1995 `, `12/04/1995T00:00`, `20000101` → una violación con mensaje `Formato de fecha inválido.`.
  - Válidas: `29/02/2000`, `12/04/1995`, `01/01/2000`, `29/02/1996` → sin violaciones.
  - Sin violación (la reporta otra restricción): `null`, `""`, `"   "`, `"\t"`.
  - `parse` devuelve `LocalDate.of(2000, 2, 29)` para `29/02/2000` y vacío para `31/02/2000`.
- **Trampa conocida:** con `yyyy` y resolución estricta se rechazan todas las fechas válidas; debe ser `uuuu` (verificado con la versión del proyecto el 6-oct). Si alguna fecha de la lista «válidas» se rechaza, **detenerse**.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=BirthDateFormatValidatorTest test` en verde.

## [ ] T-1B.2 · Contrato: `birthDate` texto, `pronoun` obligatorio, `toCommand()` — ≤ 30 min, ≈ 110 líneas — **BLOQUEADA por la pregunta 5 (D1)**

- **Cubre:** REQ-RV-10 a 12, 15. **Modificar:** `presentation/dto/RegisterUserRequest.java`, `presentation/controller/UserRegistrationController.java`, `presentation/advice/BusinessExceptionHandler.java` (tabla), `domain/exception/ErrorCode.java` (dos códigos), `domain/model/Pronoun.java` (Javadoc).
- **`ErrorCode`:** agregar `BIRTH_DATE_INVALID_FORMAT` y `PRONOUN_REQUIRED` (con su Javadoc).
- **Tabla del manejador:** `Map.entry("birthDate.NotNull", …)` → `Map.entry("birthDate.NotBlank", ErrorCode.BIRTH_DATE_REQUIRED)`; agregar `Map.entry("birthDate.BirthDateFormat", ErrorCode.BIRTH_DATE_INVALID_FORMAT)` y `Map.entry("pronoun.NotNull", ErrorCode.PRONOUN_REQUIRED)`.
- **`RegisterUserRequest`** (antes → después):
  ```java
  // antes
  @NotNull(message = "La fecha de nacimiento es obligatoria")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
  LocalDate birthDate,
  ...
  Pronoun pronoun) {}

  // después
  @NotBlank(message = "La fecha de nacimiento es obligatoria")
  @BirthDateFormat
  String birthDate,
  ...
  @NotNull(message = "Selecciona una opción.")
  Pronoun pronoun) {

      /**
       * Convierte el cuerpo ya validado en el comando del caso de uso.
       *
       * @return el comando con la fecha ya interpretada
       * @throws java.util.NoSuchElementException si se llama sin haber validado el formato de la fecha
       */
      public RegisterUserCommand toCommand() {
          LocalDate fecha = BirthDateFormatValidator.parse(birthDate).orElseThrow();
          return new RegisterUserCommand(firstName, lastName, fecha, email, password, phoneNumber, pronoun);
      }
  }
  ```
  Quitar los imports `JsonFormat` y los que queden sin uso. **Reescribir el Javadoc** del registro: `birthDate` «texto con formato `dd/MM/yyyy`, por ejemplo `12/04/1995`»; `pronoun` «obligatorio: `HE`, `SHE` o `THEY`»; quitar «opcional»; la nota sobre la contraseña sin límite en el borde se mantiene (su límite vive en `PasswordPolicy`).
- **Controlador:** `servicio.register(request.toCommand())` en lugar de armar el comando campo por campo; quitar imports sin uso.
- **`Pronoun`:** el Javadoc deja de decir «El dato es opcional»: «El registro lo exige; la cuenta guarda `null` solo después de la anonimización».
- **Verificación:** `./mvnw.cmd -B -Dtest='BusinessExceptionHandlerTest,BirthDateFormatValidatorTest' test` en verde (la prueba de la tabla ahora exige las tres claves nuevas). El resto de pruebas del controlador se ajusta en T-1B.3.

## [ ] T-1B.3 · Pruebas del contrato del registro — ≤ 30 min, ≈ 170 líneas — **BLOQUEADA por la pregunta 5 (D1)**

- **Cubre:** REQ-RV-10 a 12, 15, 16. **Modificar:** `UserRegistrationControllerTest`. Ayuda nueva: `private String cuerpoConFecha(String fecha)` = `cuerpoValido().replace("12/04/1995", fecha)`.
- **Pruebas nuevas** (cada una verifica además `verify(servicio, never()).register(any(RegisterUserCommand.class))`):
  1. `unaFechaImposibleSeRechazaEnElCampoDeLaFecha` (`@ParameterizedTest @ValueSource`): `31/02/2000`, `29/02/2001`, `31/04/2000`, `15/13/2000`, `1/1/2000`, `00/01/2000`, `01/00/2000`, `abc`, `2000-01-01`, `12-04-1995`, `12/04/95`, ` 12/04/1995`, `12/04/1995 ` → 422; `$.code` = `VALIDATION_FAILED`; exactamente un elemento con `field` = `birthDate`, `code` = `BIRTH_DATE_INVALID_FORMAT`, `message` = `Formato de fecha inválido.`.
  2. `unaFechaVaciaOEnBlancoEsObligatoria` (`""`, `"   "`): `code` del elemento = `BIRTH_DATE_REQUIRED`, **no** `BIRTH_DATE_INVALID_FORMAT`; y el caso con la clave ausente y con `"birthDate":null` (cuerpos literales sin pasar por `cuerpoConFecha`).
  3. `unaFechaQueNoEsTextoSeRechaza`: cuerpos literales con `"birthDate":20000101` y `"birthDate":true` → 422 y `BIRTH_DATE_INVALID_FORMAT` (Jackson los lee como texto); con `"birthDate":[]` y `"birthDate":{}` → 422 y `$.code` = `REQUEST_BODY_INVALID_FORMAT`. **Si un caso se comporta distinto, no ajustar la expectativa: detenerse y reportar el valor real.**
  4. `unaFechaValidaLlegaAlCasoDeUsoComoLocalDate` (`29/02/2000`, `12/04/1995`, `01/01/2000`, `29/02/1996`): 201 y, con `ArgumentCaptor<RegisterUserCommand>`, `birthDate()` igual a `LocalDate.of(…)`.
  5. `faltarElPronombreSeRechazaEnSuCampo`: sin la clave y con `"pronoun":null` → 422; un elemento con `field` = `pronoun`, `code` = `PRONOUN_REQUIRED`, `message` = `Selecciona una opción.`.
  6. `cadaPronombreValidoLlegaAlCasoDeUso` (`@ParameterizedTest @EnumSource(Pronoun.class)`): 201 y el comando lleva ese `Pronoun`.
  7. `unPronombreFueraDeLaListaSigueRechazandose`: `OTRO` → 422 (se mantiene la prueba existente de la línea 132 con su expectativa de `REQUEST_BODY_INVALID_FORMAT`; el bloque 6 la cambia).
- **Pruebas existentes a ajustar:** `faltarUnCampoObligatorioNoLlegaAlCasoDeUso` (línea 106): su cuerpo no trae `pronoun`, así que ahora hay dos errores; cambiar `$.errors[0].field` por `jsonPath("$.errors[?(@.field=='firstName')]").exists()`. `unaFechaConOtroFormatoDaErrorDeFormato…` (línea 120): ahora responde `$.errors[0].field` = `birthDate` y `BIRTH_DATE_INVALID_FORMAT`; renombrar a `unaFechaIsoSeRechazaPorFormatoEnSuCampo` y quitar la espera de `DD/MM/AAAA` en el `detail`.
- **Verificación:** `./mvnw.cmd -B -Dtest=UserRegistrationControllerTest test` en verde.

## [ ] T-1B.4 · Mensaje de contraseña común — ≤ 15 min, ≈ 25 líneas

- **Cubre:** REQ-RV-13, 14. **Modificar:** `domain/policy/PasswordPolicy.java` (línea 75) y `PasswordPolicyTest`.
- **Cambio:** el texto `"La contraseña es demasiado común brother, cambiala si no quieres que te terminen robando la cuenta"` → `"Esta contraseña es demasiado común, elige otra."`. Se declara como constante `COMMON_PASSWORD_MESSAGE` junto a las otras. **No tocar** los textos de longitud ni la lista.
- **Pruebas** (en `PasswordPolicyTest`): `@ParameterizedTest @ValueSource(strings = {"123456789012", "password1234", "qwertyuiop123", "PASSWORD1234", "  password1234  "})` → `WeakPasswordException` con `hasMessage("Esta contraseña es demasiado común, elige otra.")` y código `PASSWORD_TOO_COMMON`, y el mensaje no contiene `brother` ni la contraseña. Las dos pruebas existentes con `hasMessageContaining("demasiado común")` siguen pasando.
- **En `UserRegistrationControllerTest`:** el caso `laContraseniaDebilSeSenialaEnSuCampo` agrega una variante con `new WeakPasswordException(ErrorCode.PASSWORD_TOO_COMMON, "Esta contraseña es demasiado común, elige otra.")` y comprueba `field` = `password`, `code` = `PASSWORD_TOO_COMMON` y el mensaje literal.
- **Verificación:** `./mvnw.cmd -B -Dtest='PasswordPolicyTest,UserRegistrationControllerTest' test` en verde.

## [ ] T-1B.5 · Restricción de pronombres en la base — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-RV-17. **Crear:** `src/main/resources/db/migration/V3__restringir_pronombres.sql`, `src/test/.../infrastructure/persistence/MigracionPronombresConDatosTest.java`. **Modificar:** `CuentaSchemaMigrationTest`. **No tocar** V1 ni V2.
- **Migración:**
  ```sql
  -- Los pronombres admitidos son los del enumerado Pronoun del dominio. La columna sigue admitiendo
  -- NULL porque la anonimización vacía el dato. Una fila con otro valor impide aplicar esta migración.
  ALTER TABLE microcuentas.cuenta
      ADD CONSTRAINT ck_cuenta_pronombres_valor
      CHECK (pronombres IS NULL OR pronombres IN ('HE', 'SHE', 'THEY'));
  ```
- **`CuentaSchemaMigrationTest`** (sangría con tabuladores, como el resto del archivo): `pronombreFueraDeLaListaEsRechazado` → insertar `pronombres = 'OTRO'` lanza `DataIntegrityViolationException` cuyo mensaje contiene `ck_cuenta_pronombres_valor`; `losTresPronombresYElNuloSeAceptan` → cuatro inserciones con `uid` distintos (`HE`, `SHE`, `THEY`, `NULL`) sin error. Copiar la forma de `insertarCuenta` (línea 110) para un `insertarCuentaConPronombre(String uid, String pronombre)`.
- **`MigracionPronombresConDatosTest`** (base con datos, estándar §B.1): contenedor propio `PostgreSQLContainer("postgres:16-alpine")`; con `org.flywaydb.core.Flyway.configure().dataSource(url, usuario, clave).schemas("microcuentas").defaultSchema("microcuentas").target("2").load().migrate()` crear el esquema hasta V2; insertar dos filas (una con `'SHE'`, una con `NULL`); migrar a la última versión (`.target("latest")`) sin error; comprobar que las dos filas siguen y que una inserción con `'OTRO'` falla. `@Testcontainers(disabledWithoutDocker = true)`, sin Spring.
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest='CuentaSchemaMigrationTest,MigracionPronombresConDatosTest' test` en verde; sin Docker, decir que se omitió. **Detenerse** si `AccountRepositoryAdapterTest` (que inserta con `Pronoun.SHE` y `null`) falla.

## [ ] T-1B.6 · Pruebas de punta a punta — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-RV-10, 15, 16. **Modificar:** `AccountRegistrationEndToEndTest`. Copiar la forma de `unMenorDeEdadNoDejaRastroNiEnFirebaseNiEnLaBase` (línea 93) y del cuerpo de la línea 140.
- **Pruebas nuevas:** (1) `unaFechaImposibleNoDejaRastro`: `birthDate` = `31/02/2000` → 422, `$.errors[0].code` = `BIRTH_DATE_INVALID_FORMAT`, y ni la credencial en `InMemoryFirebaseUserDirectory`/emulador ni la fila en la base existen (misma comprobación que la prueba del menor). (2) `sinPronombreNoDejaRastro`: cuerpo sin `pronoun` → 422 `PRONOUN_REQUIRED` y sin rastro. (3) `cadaPronombreSeGuardaTalCual` (`@ParameterizedTest @EnumSource(Pronoun.class)`, correo distinto por valor): 201 y `SELECT pronombres` de esa fila igual al nombre del enumerado.
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest=AccountRegistrationEndToEndTest test` en verde; sin Docker, decirlo.

## [ ] T-1B.7 · Documentación OpenAPI del registro — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-RV-18. **Modificar:** `RegisterUserRequest.java` (Javadoc y `@Schema`), `UserRegistrationController.java` (`@Operation` y `@ApiResponses`), `RegisteredUserResponse.java` solo si su Javadoc no describe cada campo. El repo publica el Javadoc a OpenAPI con `therapi-runtime-javadoc`; los atributos que el Javadoc no expresa (ejemplo, límites, valores) van en `@Schema` de `io.swagger.v3.oas.annotations` (ya disponible por `springdoc-openapi-starter-webmvc-ui`; no se agrega dependencia).
- **Campos** (descripción en español; ejemplos con datos de prueba):
  | Campo | `@Schema` |
  |---|---|
  | `firstName` | `example = "María José"`, `maxLength = 120`, descripción «Nombres de la persona: letras, espacios, apóstrofo y guion» |
  | `lastName` | `example = "Gómez-Ruiz"`, `maxLength = 120` |
  | `birthDate` | `example = "12/04/1995"`, `pattern = "^\\d{2}/\\d{2}/\\d{4}$"`, «Formato dd/MM/yyyy; la persona debe tener entre 18 y 110 años cumplidos en UTC» |
  | `email` | `example = "ana@correo.co"`, `maxLength = 254`, `format = "email"` |
  | `password` | `minLength = 12`, `maxLength = 64`, `format = "password"`, sin ejemplo |
  | `phoneNumber` | `example = "+573000000000"`, `requiredMode = NOT_REQUIRED`, «Opcional; formato internacional E.164 sin espacios» |
  | `pronoun` | `allowableValues = {"HE", "SHE", "THEY"}`, `requiredMode = REQUIRED`, «Él, Ella o Elle» |
- **Controlador:** `@Operation(summary = "Registra una cuenta nueva", description = "…")` y `@ApiResponses` con `201` (cuenta creada), `409` (`EMAIL_ALREADY_REGISTERED`), `422` (`VALIDATION_FAILED`, `BIRTH_DATE_*`, `PASSWORD_*`, `PRONOUN_REQUIRED`) y `500` (`INTERNAL_ERROR`), cada una con `content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))` salvo la 201.
- **Verificación manual (justificada en el plan):** arrancar la aplicación con la documentación encendida (`API_DOCUMENTATION_ENABLED=true`, ver `CLAUDE.md` «Verificación»), guardar `GET /v3/api-docs` y comprobar que `paths./api/v1/users.post` tiene las cuatro respuestas y que cada campo del esquema tiene descripción, ejemplo y límites. Adjuntar el fragmento al PR. Si no se puede levantar la aplicación, decirlo y por qué.
- **Verificación automática:** `./mvnw.cmd -B test` en verde (el Javadoc nuevo no rompe la compilación de `therapi`).

## [ ] T-1B.8 · Cierre del PR 1B — ≤ 30 min

- `./mvnw.cmd -B clean verify` con salida real, número de pruebas y cobertura JaCoCo de `BirthDateFormat`, `BirthDateFormatValidator`, `RegisterUserRequest`, `UserRegistrationController`, `PasswordPolicy` (≥ 90 % de líneas y ramas, cada excepción justificada).
- Revisión: `/simplify`, `/code-review high` y **`/security-review`** (toca entradas de riesgo y la lista de contraseñas comunes); autochequeo.
- Prueba de API de punta a punta con Postman o `.http` (estándar §7): 201, 409, 422 por fecha imposible, 422 por pronombre ausente, 422 por contraseña común, 422 con varios campos; comprobar `Content-Type` y la forma de `errors[]`.
- Aviso a Frontend: cambia el texto de contraseña común; `birthDate` sigue siendo texto `dd/MM/yyyy` (sin cambio de contrato) pero ahora se rechazan las fechas imposibles; `pronoun` pasa a obligatorio. Acción para DevOps vía Vela (pregunta 12) antes del despliegue de V3.
- Entrega: PR `CM-36 | fix(cuentas): fecha estricta, mensaje de contraseña común y pronombre obligatorio en el registro [IA-ASISTIDO]`, con la plantilla completa, qué es mecánico y qué importa revisar (`BirthDateFormatValidator`, `RegisterUserRequest`, V3). Después del PR, comentario de Jira con el formato del `CLAUDE.md` y el siguiente paso (bloque 2).

---

# PR 2 — recorte, NFC y un mensaje por campo

Se ejecuta **después de fusionar 1A y 1B**; revalidar las rutas y líneas contra `origin/develop` ese día. Rama: `CM-36-recorte-nfc-registro` desde `develop`. Mensaje de commit: `CM-36 | fix(cuentas): <resultado> [IA-ASISTIDO]`. Aplican las reglas del inicio de este archivo.
**Todas las tarjetas del PR 2 están BLOQUEADAS por la pregunta 13** hasta que Paula confirme qué es un «espacio»; la recomendación (a) es la que se escribe aquí.

## [ ] T-2.1 · Objeto de valor `SingleLineText` — ≤ 30 min, ≈ 140 líneas

- **Cubre:** REQ-RV-20, 21, 22. **Crear:** `domain/model/SingleLineText.java` y `src/test/.../domain/model/SingleLineTextTest.java`. **No tocar** nada más. `domain` no importa Spring ni otras capas: solo `java.text.Normalizer`.
- **Código de referencia:**
  ```java
  package tech.cameia.cuentas.domain.model;

  import java.text.Normalizer;

  /**
   * Texto de una línea tal como el negocio lo entiende: sin espacios en los extremos y en forma
   * Unicode NFC, de modo que un mismo nombre escrito de dos maneras sea el mismo valor.
   *
   * <p>«Espacio» es lo que recorta {@code String.prototype.trim} de JavaScript en el cliente: los
   * de {@link Character#isWhitespace(int)}, los separadores de espacio de Unicode (incluido el
   * espacio duro U+00A0) y U+FEFF. Así cliente y servidor recortan igual. La longitud se cuenta
   * en puntos de código, no en unidades {@code char}, para que un carácter fuera del plano básico
   * valga uno.</p>
   *
   * @param value texto recortado y normalizado; vacío si no había nada más que espacios
   */
  public record SingleLineText(String value) {

      /**
       * Normaliza el texto recibido.
       *
       * @throws NullPointerException si el texto es nulo; quien admita la ausencia usa {@link #normalize(String)}
       */
      public SingleLineText {
          value = normalize(java.util.Objects.requireNonNull(value));
      }

      /**
       * Recorta y normaliza un texto sin rechazar la ausencia.
       *
       * @param raw texto recibido; puede ser {@code null}
       * @return el texto recortado y en NFC, o {@code null} si {@code raw} era {@code null}
       */
      public static String normalize(String raw) {
          if (raw == null) {
              return null;
          }
          return trim(Normalizer.normalize(raw, Normalizer.Form.NFC));
      }

      /** @return cantidad de puntos de código del texto */
      public int length() {
          return value.codePointCount(0, value.length());
      }

      /** @return {@code true} si no queda ningún carácter tras recortar */
      public boolean isEmpty() {
          return value.isEmpty();
      }

      private static String trim(String text) {
          int start = 0;
          int end = text.length();
          while (start < end && isSpace(text.codePointAt(start))) {
              start += Character.charCount(text.codePointAt(start));
          }
          while (end > start && isSpace(text.codePointBefore(end))) {
              end -= Character.charCount(text.codePointBefore(end));
          }
          return text.substring(start, end);
      }

      private static boolean isSpace(int codePoint) {
          return Character.isWhitespace(codePoint)
                  || Character.getType(codePoint) == Character.SPACE_SEPARATOR
                  || codePoint == 0xFEFF;
      }
  }
  ```
  (Importar `java.util.Objects` en lugar del nombre completo.)
- **Pruebas** (`SingleLineTextTest`, JUnit 5 y AssertJ sin Spring, nombres en español camelCase): una prueba o fila de `@ParameterizedTest` por cada valor de la matriz (`plan.md` §9.4, fila `SingleLineTextTest`): `"  Ana  "`→`Ana`; `"\u00A0Ana\u00A0"`→`Ana`; `"\tAna\n"`→`Ana`; `"\uFEFFAna"`→`Ana`; `"\u200BAna"` se conserva (no es espacio); `"María  José"` conserva el doble espacio interno; `"e\u0301"` (e + acento combinante) → `"é"` con `length()` 1; `"𝒜"` (U+1D49C) con `length()` 1 y `value().length()` 2; `""`, `"   "`, `"\u00A0"` → `isEmpty()`; `normalize(null)` → `null`; el constructor con `null` lanza `NullPointerException`.
- **Trampas:** en el código fuente usar escapes `\u00A0`, `\uFEFF`, `\u0301` (no pegar los caracteres: el editor o el formateador los puede cambiar); `Character.isWhitespace` no considera espacio al U+00A0, por eso la segunda condición; el recorte debe avanzar por puntos de código, no por `char`.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=SingleLineTextTest test` en verde; `LayeredArchitectureTest` en verde.

## [ ] T-2.2 · `EmailAddress` con `SingleLineText` — ≤ 20 min, ≈ 40 líneas

- **Cubre:** REQ-RV-20, 21, 22, 25. **Modificar:** `domain/model/EmailAddress.java` (constructor compacto, líneas 34-45) y `ValueObjectsTest` (clase anidada del correo, línea 23 en adelante).
- **Antes → después del constructor** (los mensajes de las excepciones **no cambian**):
  ```java
  // antes
  if (value == null || value.isBlank()) { throw new IllegalArgumentException("El correo electrónico es obligatorio"); }
  value = value.trim().toLowerCase(Locale.ROOT);
  if (value.length() > MAX_LENGTH) { throw new IllegalArgumentException("El correo electrónico es demasiado largo"); }

  // después
  SingleLineText recortado = value == null ? null : new SingleLineText(value);
  if (recortado == null || recortado.isEmpty()) { throw new IllegalArgumentException("El correo electrónico es obligatorio"); }
  // se vuelve a normalizar tras pasar a minúsculas: algunas mayúsculas (como «İ») cambian de forma al convertirlas
  value = SingleLineText.normalize(recortado.value().toLowerCase(Locale.ROOT));
  if (value.codePointCount(0, value.length()) > MAX_LENGTH) { throw new IllegalArgumentException("El correo electrónico es demasiado largo"); }
  ```
  El resto (patrón de formato) no cambia. Actualizar el Javadoc: «sin espacios en los extremos (los mismos que recorta el cliente), en NFC y en minúsculas; el límite es de 254 puntos de código».
- **Pruebas nuevas** (misma clase anidada, estilo de las existentes): `"  Ana@Correo.CO "` → `value()` = `ana@correo.co`; `"\u00A0ana@correo.co\u00A0"` → `ana@correo.co`; `"a".repeat(248) + "@b.com"` (254 puntos de código) se acepta; `"a".repeat(249) + "@b.com"` (255) lanza `IllegalArgumentException` con `hasMessage("El correo electrónico es demasiado largo")`; `"𝒜".repeat(126) + "@b.com"` (132 puntos de código y 258 unidades UTF-16) se acepta; `"\u00A0"` y `"   "` lanzan «es obligatorio»; dos correos que difieren solo en NFC/NFD (`"jose\u0301@correo.co"` y `"josé@correo.co"`) producen el mismo `value()`.
- **Debe seguir en verde:** `rechazaUnValorSinArroba`, `rechazaUnValorSinDominio`, `rechazaUnValorVacio`, `seNormalizaAMinusculasYSinEspacios`.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=ValueObjectsTest test` en verde.

## [ ] T-2.3 · `PasswordPolicy` mide en NFC — ≤ 20 min, ≈ 30 líneas

- **Cubre:** REQ-RV-22. **Modificar:** `domain/policy/PasswordPolicy.java` (método `verify`) y `PasswordPolicyTest`.
- **Cambio:** en `verify`, `int length = value.codePointCount(0, value.length());` pasa a
  ```java
  // La longitud se mide sobre la forma NFC para que un acento escrito con carácter combinante
  // cuente igual que el mismo acento precompuesto; la contraseña en sí no se modifica.
  String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
  int length = normalized.codePointCount(0, normalized.length());
  ```
  `esConocida(value)` sigue comparando el texto original (recortado, en minúsculas): **no** se normaliza para comparar con la lista en este bloque.
- **Pruebas nuevas:** `"e\u0301".repeat(12)` (24 puntos de código sin normalizar, 12 en NFC) → se acepta; `"e\u0301".repeat(11)` → `WeakPasswordException` con código `PASSWORD_TOO_SHORT`; `"e\u0301".repeat(64)` → se acepta; `"e\u0301".repeat(65)` → `PASSWORD_TOO_LONG`; el valor de la contraseña nunca aparece en el mensaje (prueba existente). Las pruebas de 11, 12, 64 y 65 caracteres y la del emoji **siguen en verde sin cambios**.
- **Trampa:** no pasar la contraseña normalizada a `RawPassword` ni a Firebase.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=PasswordPolicyTest test` en verde.

## [ ] T-2.4 · Restricción de longitud por puntos de código y normalización en el DTO — ≤ 30 min, ≈ 130 líneas

- **Cubre:** REQ-RV-20 a 23. **Crear:** `presentation/dto/CodePointSize.java`, `presentation/dto/CodePointSizeValidator.java`, `src/test/.../presentation/dto/CodePointSizeValidatorTest.java`. **Modificar:** `presentation/dto/RegisterUserRequest.java`, `domain/exception/ErrorCode.java` (agregar `EMAIL_TOO_LONG`, con Javadoc), `presentation/advice/BusinessExceptionHandler.java` (tabla).
- **Restricción** (mismo molde que `BirthDateFormat` del bloque 1):
  ```java
  @Documented
  @Constraint(validatedBy = CodePointSizeValidator.class)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @Retention(RetentionPolicy.RUNTIME)
  public @interface CodePointSize {
      /** @return máximo de puntos de código admitido */
      int max();
      String message() default "El texto supera el máximo de caracteres";
      Class<?>[] groups() default {};
      Class<? extends Payload>[] payload() default {};
  }

  public class CodePointSizeValidator implements ConstraintValidator<CodePointSize, String> {
      private int max;
      @Override public void initialize(CodePointSize anotacion) { this.max = anotacion.max(); }
      @Override public boolean isValid(String value, ConstraintValidatorContext context) {
          // La ausencia la reporta @NotBlank; aquí solo se mide lo que llegó
          return value == null || value.codePointCount(0, value.length()) <= max;
      }
  }
  ```
  Javadoc completo en español (por qué no `@Size`: cuenta unidades UTF-16).
- **`RegisterUserRequest`:** (1) constructor compacto al final del `record`:
  ```java
  public RegisterUserRequest {
      // El borde valida y el comando recibe el texto ya recortado y en NFC: una sola definición de
      // «espacio» y de «carácter». Los demás campos no se tocan; la contraseña nunca se recorta.
      firstName = SingleLineText.normalize(firstName);
      lastName = SingleLineText.normalize(lastName);
      email = SingleLineText.normalize(email);
  }
  ```
  (2) `@Size(max = 120, message = "Los nombres no pueden superar los 120 caracteres")` → `@CodePointSize(max = 120, message = "Los nombres no pueden superar los 120 caracteres")`; igual para `lastName` con su texto actual; (3) en `email`, agregar `@CodePointSize(max = 254, message = "El correo no puede superar los 254 caracteres.")` después de `@NotBlank`. Quitar el import de `Size` si queda sin uso.
- **Tabla del manejador:** quitar `firstName.Size` y `lastName.Size`; agregar `Map.entry("firstName.CodePointSize", ErrorCode.FIRST_NAME_TOO_LONG)`, `Map.entry("lastName.CodePointSize", ErrorCode.LAST_NAME_TOO_LONG)` y `Map.entry("email.CodePointSize", ErrorCode.EMAIL_TOO_LONG)`. La prueba `todaRestriccionDelContratoTieneCodigo` del bloque 1 debe seguir en verde y exigir exactamente estas claves.
- **Pruebas** (`CodePointSizeValidatorTest`, sobre un `record Caso(@CodePointSize(max = 5) String texto)`): `null`, `""`, 4, 5 → válidos; 6 → inválido; `"𝒜".repeat(5)` → válido (10 unidades UTF-16); `"𝒜".repeat(6)` → inválido.
- **Trampas:** Jackson construye el `record` por su constructor canónico, así que el compacto siempre corre; `birthDate` y `phoneNumber` **no** se normalizan aquí.
- **Verificación:** `./mvnw.cmd -B -Dtest='CodePointSizeValidatorTest,BusinessExceptionHandlerTest,UserRegistrationControllerTest' test` en verde.

## [ ] T-2.5 · Pruebas del contrato: recorte, NFC, límites y un mensaje por campo — ≤ 30 min, ≈ 180 líneas

- **Cubre:** REQ-RV-20 a 24; CA-1.1.9 a 1.1.13, 1.1.16 a 1.1.19, 1.1.22. **Modificar:** `UserRegistrationControllerTest`. Ayudas: `cuerpoConCampo(String campo, String valor)` que reemplaza el valor del campo en `cuerpoValido()` (escapar con `"\\u00A0"` en el texto Java, no el carácter pegado).
- **Pruebas nuevas** (las que dicen «→ 422» verifican un único elemento con el `field`, `code` y mensaje indicados y `verify(servicio, never()).register(any(RegisterUserCommand.class))`):
  1. `losTextosLlegaranRecortadosYEnNfcAlCasoDeUso`: `firstName` = `"  Ana  "`, `lastName` = `"\u00A0Pérez\u00A0"`, `email` = `"  Ana@Correo.CO "` → 201 y, con `ArgumentCaptor`, el comando lleva `Ana`, `Pérez` y `Ana@Correo.CO` (el correo se pasa a minúsculas en el dominio, no en el comando); `firstName` = `"Jose\u0301"` → el comando lleva `"José"` (NFC).
  2. `unTextoQueSoloTieneEspaciosEsObligatorio` (`@ParameterizedTest @ValueSource`): `""`, `"   "`, `"\t"`, `"\n"`, `"\u00A0"`, `"\uFEFF"` para `firstName`, `lastName` y `email` → 422 con `FIRST_NAME_REQUIRED`, `LAST_NAME_REQUIRED`, `EMAIL_REQUIRED`; y la clave ausente y `null`.
  3. `elNombreYElApellidoAdmitenExactamente120CaracteresYRechazan121`: letras `ñ` (119, 120 → 201; 121 → 422 `FIRST_NAME_TOO_LONG` / `LAST_NAME_TOO_LONG`); `"a".repeat(119) + "e\u0301"` → 201 (120 en NFC); `"𝒜".repeat(120)` → 201 y `"𝒜".repeat(121)` → 422.
  4. `elCorreoAdmiteExactamente254PuntosDeCodigo`: 253 y 254 → 201; 255 → 422 `EMAIL_TOO_LONG` con `message` = `El correo no puede superar los 254 caracteres.` (CA-1.1.22). Construir con `"a".repeat(n - 6) + "@b.com"`.
  5. `laContraseniaNoSeRecortaNiSeNormalizaAlLlegarAlCasoDeUso`: `"  frase secreta larga  "` → el comando lleva exactamente esos 23 caracteres con sus espacios; `"            "` (12 espacios) → 422 `PASSWORD_REQUIRED` con `field` = `password`.
  6. `variosCamposQueIncumplenReglasDevuelvenUnSoloMensajePorCampo`: (a) `firstName` = `"a".repeat(121)` → un elemento `FIRST_NAME_TOO_LONG` y ninguno `FIRST_NAME_REQUIRED`; (b) `email` = `"a".repeat(255)` (sin `@`) → un elemento `EMAIL_TOO_LONG`; (c) `firstName` = `"   "`, `lastName` = `"a".repeat(121)`, `email` = `"   "` → exactamente tres elementos, uno por campo, con `FIRST_NAME_REQUIRED`, `LAST_NAME_TOO_LONG` y `EMAIL_REQUIRED`.
- **Trampa:** el cuerpo es JSON escrito en un texto Java: un tabulador o un salto de línea reales dentro de un valor lo hacen ilegible (422 `REQUEST_BODY_INVALID_FORMAT`, no el error esperado). Para el tabulador y el salto de línea pasar el escape de JSON, que en el texto Java se escribe con dos barras (`"\\t"` y `"\\n"`); el espacio duro y el U+FEFF se escriben como escapes de Java (barra, `u` y cuatro dígitos) y llegan como el carácter real, que JSON admite.
- **Pruebas existentes a ajustar:** las que verificaban `Size` (si las hay) por el mismo texto no cambian; `elCorreoRepetido…` y las demás siguen igual.
- **Verificación:** `./mvnw.cmd -B -Dtest=UserRegistrationControllerTest test` en verde.

## [ ] T-2.6 · Pruebas de punta a punta — ≤ 30 min, ≈ 80 líneas

- **Cubre:** REQ-RV-21, 25; CA-1.1.16, 1.1.42. **Modificar:** `AccountRegistrationEndToEndTest` (copiar la forma de la prueba de la línea 83 para el segundo registro).
- **Pruebas nuevas:** (1) `unCorreoConEspaciosYMayusculasSeGuardaNormalizadoYBloqueaElSiguiente`: registrar con `"email":"  Ana@Correo.CO "` → 201; registrar de nuevo con `"ana@correo.co"` → 409 `EMAIL_ALREADY_REGISTERED`; el directorio de Firebase de pruebas tiene un solo usuario con `ana@correo.co`. (2) `unNombreDe120CaracteresSeGuardaCompleto`: `firstName` = `"ñ".repeat(120)` → 201 y `SELECT char_length(nombre) FROM microcuentas.cuenta WHERE firebase_uid = ?` igual a 120. (3) `unNombreEnNfdSeGuardaEnNfc`: `firstName` = `"Jose\u0301"` → 201 y `SELECT nombre` igual a `"José"` (cadena con `é`).
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest=AccountRegistrationEndToEndTest test` en verde; sin Docker, decirlo.

## [ ] T-2.7 · Cierre del PR 2 — ≤ 30 min

- `./mvnw.cmd -B clean verify` con salida real, número de pruebas y cobertura JaCoCo de `SingleLineText`, `EmailAddress`, `PasswordPolicy`, `CodePointSize`, `CodePointSizeValidator`, `RegisterUserRequest` (≥ 90 % de líneas y ramas; la rama `null` de `normalize` y las de `isSpace` se cubren con T-2.1).
- Revisión: `/simplify`, `/code-review high`, autochequeo (¿se normalizó la contraseña por descuido? ¿algún log con el correo?).
- Aviso a Frontend: el servidor recorta y normaliza nombre, apellido y correo igual que el cliente; el correo de más de 254 caracteres responde 422 en `email` con `EMAIL_TOO_LONG`.
- Entrega: PR `CM-36 | fix(cuentas): recorte, normalización NFC y límites por puntos de código en el registro [IA-ASISTIDO]`, plantilla completa, qué es mecánico (cambio de `@Size` a `@CodePointSize`) y qué importa revisar (`SingleLineText`, constructor compacto de `RegisterUserRequest`, `EmailAddress`).

---

# Bloques 3 a 6

Sus tarjetas se escriben antes de ejecutar cada uno. Orden y dependencias: `plan.md`, sección 7.
