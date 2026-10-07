# Tareas — CM-36-ValidacionesRegistro

Estado: sin ejecutar; spec y plan aprobados por Paula el 7-oct-2026 («apruebo el plan»). La ejecución es meticulosa: ningún error se oculta (se reporta con su salida real), cada tarjeta se verifica con pruebas de camino feliz, de cada validación, de límites y de casos no previstos, y el código se revisa antes de cerrar. Este archivo tiene las tarjetas del **bloque 1** (PR 1A y PR 1B) y del **bloque 2** (PR 2). Las tarjetas de los bloques 3 a 6 se
agregan aquí antes de ejecutar cada bloque. Se marca `[x]` solo con la salida real de las pruebas pegada en el informe del PR.

## Reglas para todas las tarjetas (el modelo que ejecuta no lee la spec ni el plan)

- Ruta base del código: `src/main/java/tech/cameia/cuentas/`; pruebas: `src/test/java/tech/cameia/cuentas/`. Identificadores en inglés; Javadoc, comentarios, mensajes y `@DisplayName` en español; **sin** `CM-NNN` ni rutas a otros archivos en comentarios; sin abreviaturas; en una clase existente se mantiene su estilo (nombres de pruebas en español camelCase como `elCorreoRepetidoDevuelve…`).
- Prohibido: agregar dependencias, tocar las migraciones V1 y V2, registrar contraseñas, correos o tokens, cambiar un texto de mensaje que la tarjeta no nombre, refactorizar fuera de la tarjeta.
- Todo código de error nuevo, o cuyo estado o texto cambia, se agrega o actualiza en `docs/errores.md` en el mismo PR, con las columnas `Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba` (una fila por código; «Endpoints» lista cada método y ruta que lo emite). Si `docs/errores.md` aún no existe en `develop`, el PR lo dice y la pieza B de CM-283 lo recoge (su verificación V-12 compara el catálogo con el código).
- Comandos: `./mvnw.cmd -q -B -Dtest=<Clase> test` para una clase; `./mvnw.cmd -B test` para la suite; `./mvnw.cmd -B clean verify` al cerrar (genera `target/site/jacoco/`). Las pruebas con Testcontainers se omiten sin Docker (`disabledWithoutDocker`): si se omiten, decirlo en el informe.
- **Detenerse y reportar** si: la tarjeta contradice el código real, falta un dato, una prueba existente se rompe sin causa clara, el comportamiento de una librería difiere de lo que la tarjeta afirma, o hace falta algo no listado. No improvisar el diseño.
- Definición de terminado de cada tarjeta: pruebas nuevas en verde, suite completa en verde, `LayeredArchitectureTest` en verde, diff dentro de lo estimado.
- Rama: `CM-36-validaciones-registro` desde `origin/develop`. PR 1B sale de una rama nueva `CM-36-validaciones-registro-fecha-pronombre` creada desde `develop` **después** de fusionar 1A (o apilada sobre 1A si Paula lo autoriza).
- Mensaje de commit: `CM-36 | <tipo>(cuentas): <resultado>`, sin `[IA-ASISTIDO]` (lo lleva solo el título del PR, como pide el `CLAUDE.md` del repositorio) y con el trailer `Co-Authored-By` del modelo.

---

# PR 1A — formato de error con `code`

## [ ] T-1A.0 · Preparar la rama — ≤ 15 min, sin cambios de código

- **Hacer:** `git fetch`; comprobar que `git branch -r` no lista `CM-36-validaciones-registro` (la rama no está en el remoto); `git rebase origin/develop` **en local** (solo hay commits de documentación; si hay conflictos en `specs/`, resolverlos conservando la versión de la rama). Comprobar que existen `docs/errores.md`, `docs/estandar-backend.md` y `docs/adr/0001-codigo-de-error-y-request-id.md`.
- **Detenerse y reportar si:** la rama existe en el remoto (el rebase exigiría force-push, prohibido sin orden expresa), o falta alguno de los tres documentos.
- **Verificación:** `git log --oneline origin/develop..HEAD` solo lista commits `docs(...)`; `./mvnw.cmd -B test` en verde antes de tocar nada.

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
      /** Un objeto de valor rechazó un dato sin decir el campo; respaldo hasta que cada objeto de valor tenga su código. */
      REQUEST_INVALID_VALUE,
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
      /** El correo aún no está verificado. */ EMAIL_NOT_VERIFIED,
      /** Firebase no está disponible; la persona puede reintentar. */ DEPENDENCY_UNAVAILABLE,
      /** La ruta solicitada no existe. */ ROUTE_NOT_FOUND,
      /** El método HTTP no está permitido en esa ruta. */ METHOD_NOT_ALLOWED,
      /** El tipo de contenido de la petición no se admite. */ MEDIA_TYPE_NOT_ALLOWED
  }
  ```
  (Escribir cada Javadoc en su propia línea, como el resto del repo; aquí se compactó para ahorrar espacio.)
- **Prueba** `ErrorCodeTest` (en el estilo de `AgePolicyTest`): `@ParameterizedTest @EnumSource(ErrorCode.class) void todoCodigoUsaUnaCausaDelVocabularioCerrado(ErrorCode codigo)`: si el código es `VALIDATION_FAILED` o `INTERNAL_ERROR`, `return`; si no, `assertThat(codigo.name()).matches("^[A-Z]+(_[A-Z]+)+$")` y `assertThat(CAUSAS).anyMatch(causa -> codigo.name().endsWith("_" + causa))`, con `CAUSAS = List.of("REQUIRED","TOO_SHORT","TOO_LONG","TOO_COMMON","INVALID_FORMAT","INVALID_CHARACTERS","INVALID_VALUE","OUT_OF_RANGE","IN_THE_FUTURE","UNDERAGE","NOT_FOUND","ALREADY_REGISTERED","NOT_ALLOWED","NOT_VERIFIED","UNAVAILABLE")` (`TOO_COMMON` es la causa nueva aprobada con esta spec; las dos excepciones del `return` son las únicas permitidas). Más `@Test void noHayCodigosRepetidos` que compruebe `Arrays.stream(values()).map(Enum::name).distinct().count()` igual a `values().length`.
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
  2. `problema(HttpStatus estado, String titulo, String detalle)` pasa a `problema(HttpStatus estado, String titulo, String detalle, ErrorCode codigo)`: agrega `problema.setProperty("code", codigo.name())` y, **solo si** `estado.is4xxClientError()`, `logger.warn("Error atendiendo la petición [status={}, code={}]", estado.value(), codigo)` (sin datos de la petición; T-1A.5 agrega `requestId` y la lista de campos). Los 5xx se registran completos en `falloInterno`. Cada error se registra **una sola vez**: ningún servicio ni controlador registra el mismo error otra vez. Constante nueva `private static final String DETALLE_VALIDACION = "Revisa los campos marcados.";`.
  3. `campo(String nombre, String mensaje)` pasa a `campo(String nombre, ErrorCode codigo, String mensaje)` y devuelve `Map.of("field", nombre, "code", codigo.name(), "message", mensaje == null ? "Valor no válido" : mensaje)`.
  4. Cada método usa el código de la excepción: `correoRepetido` → `error.getErrorCode()` (409); `correoSinVerificar` y `cuentaInexistente` → `error.getErrorCode()`; **`fechaInvalida` y `contraseniaDebil` (422 de dominio de un solo campo)**: código de nivel superior `VALIDATION_FAILED`, `detail` fijo `"Revisa los campos marcados."` (constante `DETALLE_VALIDACION`, la misma de `camposInvalidos`) y `errors` con **un** elemento `campo("birthDate" o "password", error.getErrorCode(), error.getMessage())`. El texto del mensaje no cambia (hoy sale en `detail`; pasa solo a `errors[].message`).
  5. `camposInvalidos`: código de nivel superior `VALIDATION_FAILED`, `detail` = `DETALLE_VALIDACION`; **un elemento por campo** (el primer `FieldError` de cada campo) con
     ```java
     private ErrorCode codigoDe(FieldError fallo) {
         String clave = fallo.getField() + "." + fallo.getCode();
         ErrorCode codigo = FIELD_ERROR_CODES.get(clave);
         if (codigo == null) {
             // Nunca VALIDATION_FAILED en un elemento: ese código es de la operación entera.
             logger.error("Restricción del contrato sin código de error: {}", clave);
             return ErrorCode.REQUEST_INVALID_VALUE;
         }
         return codigo;
     }
     ```
     (La prueba `todaRestriccionDelContratoTieneCodigo` de T-1A.4 impide que esa rama ocurra; existe para que un descuido no deje pasar un código genérico en silencio.)
     Recorrer `error.getBindingResult().getFieldErrors()` con un `LinkedHashMap<String, Map<String,String>>` y `putIfAbsent(fallo.getField(), campo(fallo.getField(), codigoDe(fallo), fallo.getDefaultMessage()))`; `errors` = `new ArrayList<>(mapa.values())`.
  6. `cuerpoIlegible` → código `REQUEST_BODY_INVALID_FORMAT`, **sin cambiar el texto** (el bloque 6 lo cambia). `peticionIncompleta` → `IDENTITY_REQUIRED`. `valorInvalido` (`IllegalArgumentException`) → código propio `REQUEST_INVALID_VALUE`, **nunca** `VALIDATION_FAILED` (que significa «mira la lista `errors`»). El `detail` conserva el mensaje solo si la excepción nace en el dominio (`error.getStackTrace()[0].getClassName()` empieza por `tech.cameia.cuentas.domain`): son textos escritos para la persona. Si nace en otra parte (una librería), `detail` = «Revisa los datos enviados.». Se registra `WARN` «Valor rechazado sin campo: code=REQUEST_INVALID_VALUE requestId={} origen={}» con `clase.método` del primer elemento de la traza, **sin** el mensaje. Los bloques 3, 5 y 6 eliminan este camino. `falloInterno` → `INTERNAL_ERROR` y `detalle` = `"Ocurrió un error. Inténtalo de nuevo."`.
- **Trampas:** el nombre de la restricción que devuelve `FieldError.getCode()` es el nombre simple de la anotación (`NotBlank`, `NotNull`, `Size`); `Map.of` solo admite 10 pares, por eso `Map.ofEntries`; el orden de `getFieldErrors()` no está garantizado, no se asume.
- **Verificación:** `./mvnw.cmd -B -Dtest='UserRegistrationControllerTest,AccountActivationControllerTest' test` en verde (los textos y estados no cambiaron).

## [ ] T-1A.4 · Pruebas del formato de error — ≤ 30 min, ≈ 160 líneas

- **Cubre:** REQ-RV-01, 02, 04, 06. **Crear:** `src/test/.../presentation/advice/BusinessExceptionHandlerTest.java` (mismo paquete que el manejador, para ver su constante). **Modificar:** `UserRegistrationControllerTest`, `AccountActivationControllerTest`.
- **`BusinessExceptionHandlerTest`:**
  1. `todaRestriccionDelContratoTieneCodigo`: para cada `Field campo : RegisterUserRequest.class.getDeclaredFields()` y cada `Annotation anotacion : campo.getAnnotations()` cuya `annotationType().isAnnotationPresent(jakarta.validation.Constraint.class)`, `assertThat(BusinessExceptionHandler.FIELD_ERROR_CODES).containsKey(campo.getName() + "." + anotacion.annotationType().getSimpleName())`. Y a la inversa: cada clave de la tabla corresponde a una restricción existente (evita códigos huérfanos).
  2. `unFalloTecnicoDevuelveElMensajeGenericoSinDetalle`: `MockMvc` con `standaloneSetup(new ControladorQueFalla())` y `setControllerAdvice(ProblemDetailTestSupport.manejadorDeErrores())`; el controlador es una clase interna `@RestController` con `@GetMapping("/falla")` que lanza `new IllegalStateException("detalle interno secreto")`. Esperar: estado 500; `$.code` = `INTERNAL_ERROR`; `$.detail` = `Ocurrió un error. Inténtalo de nuevo.`; el cuerpo **no** contiene `secreto` ni `IllegalStateException`.
  3. `unValorInvalidoDelDominioTieneSuPropioCodigo`: `new EmailAddress("ana@")` invocado desde un controlador interno (la excepción nace en el dominio) → 422, `$.code` = `REQUEST_INVALID_VALUE`, `$.detail` = el mensaje del dominio, sin `$.errors`.
  4. `unValorInvalidoDeLibreriaNoMuestraSuMensaje`: el controlador interno lanza `new IllegalArgumentException("No enum constant tech.cameia.X")` → 422, `REQUEST_INVALID_VALUE`, `$.detail` = «Revisa los datos enviados.»; la salida del log (capturada) contiene `origen=` y no contiene `No enum constant`.
- **`UserRegistrationControllerTest` (agregar a las pruebas existentes, no duplicarlas):**
  - 409 (línea 73): `jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED")`.
  - menor de edad (línea 84): `$.code` = `VALIDATION_FAILED`; `$.detail` = «Revisa los campos marcados.»; `$.errors.length()` = 1; `$.errors[0].code` = `BIRTH_DATE_UNDERAGE`; `$.errors[0].field` = `birthDate`; `$.errors[0].message` conserva el texto vigente.
  - contraseña débil (línea 96): `$.code` = `VALIDATION_FAILED`; `$.errors[0].code` = `PASSWORD_TOO_SHORT`; `$.errors[0].field` = `password`.
  - campo obligatorio (línea 106): `$.code` = `VALIDATION_FAILED` y `$.errors[?(@.field=='firstName')].code` contiene `FIRST_NAME_REQUIRED` (buscar por `field`, nunca por posición).
  - fecha con otro formato (línea 120) y pronombre `OTRO` (línea 132): `$.code` = `REQUEST_BODY_INVALID_FORMAT`.
  - Un caso nuevo `variosCamposInvalidosDevuelvenUnElementoPorCampo`: cuerpo con `firstName` y `lastName` en blanco y `email` en blanco → `$.errors.length()` = 3 y un código por campo (`FIRST_NAME_REQUIRED`, `LAST_NAME_REQUIRED`, `EMAIL_REQUIRED`).
  - Un caso nuevo `unNombreEnBlancoYUnaFechaImposibleNoDuplicanElCampo`: `firstName` = `"   "` → exactamente un elemento con `field` = `firstName` (aunque fallen dos restricciones del mismo campo en otras tarjetas).
- **`AccountActivationControllerTest`:** línea 62 (403) → `$.code` = `EMAIL_NOT_VERIFIED`; línea 73 (404) → `$.code` = `ACCOUNT_NOT_FOUND`.
- **Verificación:** `./mvnw.cmd -B -Dtest='BusinessExceptionHandlerTest,UserRegistrationControllerTest,AccountActivationControllerTest,ErrorCodeTest' test` en verde.

## [ ] T-1A.5 · `requestId` en el cuerpo y en el encabezado — ≤ 30 min, ≈ 70 líneas

Decidido por Paula (6-oct-2026): el `requestId` entra desde 1A, sin filtro ni `MDC` (el `MDC` llega con CM-283, parte 2). La expresión del identificador quedó confirmada el 7-oct-2026.

- **Cubre:** REQ-RV-03 y REQ-RV-27 (registro seguro). **Modificar:** `BusinessExceptionHandler.java` y `BusinessExceptionHandlerTest`.
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
  y `problema.setProperty("requestId", requestId)`; el `logger.warn` de T-1A.3 agrega `requestId`, y `falloInterno` registra `logger.error("Fallo no controlado: code=INTERNAL_ERROR requestId={}", requestId, error)` (con la traza). Así el `requestId` que la persona ve en la respuesta lleva a una sola línea del log. Prueba: un 500 deja en el log capturado el mismo `requestId` del cuerpo.
- **Pruebas:** encabezado válido `abc-123` → mismo valor en `$.requestId` y en el encabezado de la respuesta; ausente → UUID v4 (`matches("[0-9a-f-]{36}")`) y mismo valor en el encabezado; `abc 123` (con espacio) y 65 caracteres → UUID nuevo; el valor nunca es el enviado cuando no cumple la expresión.
- **Registro seguro (REQ-RV-27), en esta misma tarjeta:** (a) el WARN de 4xx lleva `code`, `requestId` y, en `camposInvalidos`, los nombres de campo separados por coma (`fallo.getField()`, que sale del contrato); nunca `getRejectedValue()` ni `getDefaultMessage()` en el log; (b) `cuerpoIlegible` y los demás manejadores de deserialización registran solo `error.getClass().getSimpleName()` (ya es así: conservarlo); (c) el `requestId` solo se escribe en el log y en la respuesta después de pasar `REQUEST_ID_VALIDO`; (d) en `RegisterUserService.compensar` la línea `ERROR` existente ya lleva `firebaseUid`: no se toca, solo se le agrega la prueba siguiente.
- **Pruebas de registro (en `RegisterUserServiceTest`, con captura del log como en las pruebas existentes de esa clase; si no hay captura de log, usar `OutputCaptureExtension` de Spring Boot):** (1) si la base falla y `deleteUser` también falla, el log contiene `ERROR`, el `firebaseUid` y `conciliación manual`, y no contiene el correo ni la contraseña; (2) una respuesta 422 con contraseña débil no deja la contraseña en el log; (3) un 500 deja en el log el mismo `requestId` del cuerpo.
- **Trampa:** con `standaloneSetup`, `RequestContextHolder` está disponible durante el despacho; si lanza `IllegalStateException`, **detenerse** y reportar (alternativa: recibir `HttpServletRequest` y `HttpServletResponse` como parámetros de cada manejador).

## [ ] T-1A.6 · Prueba de punta a punta y `charset` del tipo de contenido — ≤ 30 min, ≈ 35 líneas

- **Cubre:** REQ-RV-01 de punta a punta. **Modificar:** `AccountRegistrationEndToEndTest` (`elSegundoRegistroConElMismoCorreoRespondeConflicto`, línea 83).
- **Agregar** a esa prueba: el cuerpo de la respuesta contiene `"code":"EMAIL_ALREADY_REGISTERED"`; y el encabezado `Content-Type` es `application/problem+json` con `charset=UTF-8` (`assertThat(respuesta.getHeaders().getContentType().toString()).containsIgnoringCase("charset=UTF-8")`).
- **Si la aserción del charset falla** (es lo esperado: Spring y Tomcat no lo declaran en `problem+json`), **se corrige en esta tarjeta** (ASVS 4.1.1): agregar a `src/main/resources/application.properties` la línea `spring.servlet.encoding.force-response=true` con un comentario de una línea («Declara charset=UTF-8 en toda respuesta, también en application/problem+json.»). Es el nombre de la propiedad en Spring Boot 4.1.1 (`spring-boot-servlet`, `spring.servlet.encoding.*`); no usar `server.servlet.encoding.*`, que en esta versión no existe. Volver a correr la prueba y pegar en el informe el valor del encabezado antes y después.
- **Si sigue fallando con la propiedad**, no cambiar cada método del manejador ni dejar la aserción comentada: **detenerse** y reportar el valor real del encabezado.
- **Verificación:** con Docker en marcha, `./mvnw.cmd -B -Dtest=AccountRegistrationEndToEndTest test` en verde; sin Docker, decir que se omitió.

## [ ] T-1A.8 · Errores del propio framework: 404, 405 y 415 — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-RV-08, V-08. **Modificar:** `presentation/advice/BusinessExceptionHandler.java`. **Crear:** `src/test/.../presentation/advice/FrameworkErrorsTest.java`.
- **Primero (V-08), sin cambiar código:** con `MockMvc` (contexto completo, como `UserRegistrationControllerTest`) enviar `GET /ruta-que-no-existe`, `DELETE /api/v1/users` y `POST /api/v1/users` con `Content-Type: text/plain`, y anotar estado y cuerpo reales en el informe. **Detenerse y reportar** si alguno responde 500 o un cuerpo que no sea `application/problem+json`: esa información decide si basta con los manejadores de abajo.
- **Código de referencia** (tres manejadores nuevos; Spring 6 y 7 lanzan `NoResourceFoundException`, `HttpRequestMethodNotSupportedException` y `HttpMediaTypeNotSupportedException`):
  ```java
  /** Ruta que no existe. */
  @ExceptionHandler(NoResourceFoundException.class)
  ProblemDetail rutaInexistente(NoResourceFoundException error) {
      return problema(HttpStatus.NOT_FOUND, "Ruta no encontrada", "No existe la ruta solicitada.", ErrorCode.ROUTE_NOT_FOUND);
  }

  /** Método HTTP no permitido en la ruta. */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ProblemDetail metodoNoPermitido(HttpRequestMethodNotSupportedException error) {
      return problema(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido", "Método no permitido.", ErrorCode.METHOD_NOT_ALLOWED);
  }

  /** Tipo de contenido que la ruta no admite. */
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ProblemDetail tipoDeContenidoNoAdmitido(HttpMediaTypeNotSupportedException error) {
      return problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido", "Tipo de contenido no admitido.", ErrorCode.MEDIA_TYPE_NOT_ALLOWED);
  }
  ```
  El `Allow` del 405 que pone Spring se conserva si la excepción lo trae: no sobrescribirlo.
- **Pruebas** (`FrameworkErrorsTest`, contexto completo): `GET /ruta-que-no-existe` → 404, `$.code` = `ROUTE_NOT_FOUND`; `DELETE /api/v1/users` → 405, `$.code` = `METHOD_NOT_ALLOWED`; `POST /api/v1/users` con `Content-Type: text/plain` → 415, `$.code` = `MEDIA_TYPE_NOT_ALLOWED`; los tres con `$.requestId` presente y sin el texto de la excepción de Spring en el cuerpo.
- **Trampa:** si Spring ya resuelve alguno con `ResponseStatusExceptionResolver` antes del manejador, la prueba lo detecta (el cuerpo no tendrá `code`); entonces agregar el manejador en vez de cambiar la prueba.
- **Verificación:** `./mvnw.cmd -B -Dtest='FrameworkErrorsTest,UserRegistrationControllerTest' test` en verde.

## [ ] T-1A.9 · Firebase no disponible responde 503 — ≤ 30 min, ≈ 110 líneas

- **Cubre:** REQ-RV-09, V-07. **Crear:** `domain/exception/DependencyUnavailableException.java`. **Modificar:** `infrastructure/client/FirebaseUserDirectoryAdapter.java` (métodos `createUser`, `assignFreePlanClaim`, `deleteUser`), `presentation/advice/BusinessExceptionHandler.java`, `infrastructure/client/FirebaseUserDirectoryAdapterTest.java`, `presentation/controller/UserRegistrationControllerTest.java`. **No tocar** `isEmailVerified` (el usuario inexistente en Firebase es de otra tarea y está listado en `docs/errores.md`).
- **Primero (V-07), sin cambiar código:** con el emulador detenido (o con `FIREBASE_AUTH_EMULATOR_HOST` apuntando a un puerto cerrado), llamar a `createUser` desde una prueba desechable y anotar en el informe la clase de excepción, `getErrorCode()` de Firebase y `getAuthErrorCode()`. **Detenerse y reportar** si no es `FirebaseAuthException` con `ErrorCode.UNAVAILABLE`, `DEADLINE_EXCEEDED` o `INTERNAL` (o una causa `IOException`): el criterio de abajo se ajusta con Paula antes de seguir.
- **Código de referencia:**
  ```java
  /** Una dependencia externa (Firebase) no está disponible; la persona puede reintentar. */
  public class DependencyUnavailableException extends BusinessException {
      public DependencyUnavailableException(Throwable causa) {
          super(ErrorCode.DEPENDENCY_UNAVAILABLE, "Ocurrió un error. Inténtalo de nuevo.");
          initCause(causa);
      }
  }
  ```
  En el adaptador, un método privado `indisponible(FirebaseAuthException error)` devuelve `true` si `error.getErrorCode()` es `UNAVAILABLE`, `DEADLINE_EXCEEDED` o `INTERNAL`, o si `error.getCause() instanceof IOException`. En `createUser`, `assignFreePlanClaim` y `deleteUser`: `if (indisponible(error)) throw new DependencyUnavailableException(error);` antes del `IllegalStateException` existente, que se conserva para cualquier otro rechazo (un dato propio que Firebase rechaza no es indisponibilidad: sigue siendo `INTERNAL_ERROR`). En el manejador: `@ExceptionHandler(DependencyUnavailableException.class)` → `problema(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible", "Ocurrió un error. Inténtalo de nuevo.", error.getErrorCode())`, que registra `WARN`.
- **Pruebas:** en `FirebaseUserDirectoryAdapterTest` con un `FirebaseAuth` simulado: `createUser` con `FirebaseAuthException` de `UNAVAILABLE` → `DependencyUnavailableException`; con `DEADLINE_EXCEEDED` → igual; con `INVALID_ARGUMENT` → `IllegalStateException` (no es indisponibilidad); con `EMAIL_ALREADY_EXISTS` → `EmailAlreadyRegisteredException` (sin cambio). En `UserRegistrationControllerTest`: el servicio lanza `DependencyUnavailableException` → 503, `$.code` = `DEPENDENCY_UNAVAILABLE`, `$.detail` = «Ocurrió un error. Inténtalo de nuevo.». En `RegisterUserServiceTest`: si `assignFreePlanClaim` lanza `DependencyUnavailableException`, se compensa (`deleteUser` llamado una vez) y la excepción se relanza.
- **Trampa:** la compensación de `RegisterUserService` captura `RuntimeException` y relanza la misma: `DependencyUnavailableException` pasa intacta; no envolverla.
- **Verificación:** `./mvnw.cmd -B -Dtest='FirebaseUserDirectoryAdapterTest,UserRegistrationControllerTest,RegisterUserServiceTest' test` en verde.

## [ ] T-1A.10 · Restricciones de la base: registro seguro y clasificación — ≤ 30 min, ≈ 100 líneas

- **Cubre:** REQ-RV-19, D11. **Modificar:** `presentation/advice/BusinessExceptionHandler.java`. **Crear:** `src/test/.../infrastructure/persistence/CuentaConstraintsClassificationTest.java` (con Testcontainers, como `CuentaSchemaMigrationTest`).
- **Manejador nuevo** (la excepción cita el valor de la columna en su mensaje: no se registra el mensaje ni la traza completa):
  ```java
  /** Violación de una restricción de la base: es un defecto, no un error de la persona. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail integridadDeDatos(DataIntegrityViolationException error) {
      String restriccion = error.getCause() instanceof org.hibernate.exception.ConstraintViolationException hibernate
              ? hibernate.getConstraintName() : "desconocida";
      // El mensaje de la base incluye el valor de la columna (dato personal): solo se registra el nombre.
      logger.error("Violación de restricción de la base [constraint={}, code={}]", restriccion, ErrorCode.INTERNAL_ERROR);
      return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", DETALLE_INTERNO, ErrorCode.INTERNAL_ERROR);
  }
  ```
  (`DETALLE_INTERNO` = «Ocurrió un error. Inténtalo de nuevo.», la constante que ya usa `falloInterno`; si no existe como constante, crearla y usarla en ambos.) T-1A.5 agrega el `requestId` a esta línea.
- **Prueba de la clasificación** (`CuentaConstraintsClassificationTest`): con la base migrada, `SELECT conname FROM pg_constraint WHERE conrelid = 'microcuentas.cuenta'::regclass` debe ser igual, como conjunto, a `CLASIFICADAS = Set.of("cuenta_pkey", "uq_cuenta_firebase_uid", "ck_cuenta_nombre", "ck_cuenta_apellido", "ck_cuenta_estado", "ck_cuenta_version", "ck_cuenta_telefono_e164", "ck_cuenta_pronombres_no_vacio", "ck_cuenta_fecha_actualizacion", "ck_cuenta_fecha_eliminacion", "ck_cuenta_anonimizacion")`. La clave primaria de la migración V1 no tiene nombre, así que PostgreSQL la llama `cuenta_pkey` (no sigue el prefijo `pk_` del estándar; una migración aplicada no se edita). El mensaje de falla dice: «Hay una restricción sin clasificar: agrégala a la sección 7 de la spec y decide si necesita código propio». La migración V3 de 1B agrega `ck_cuenta_pronombres_valor` a este conjunto.
- **Prueba del manejador:** un controlador interno que lanza `new DataIntegrityViolationException("duplicate key value (nombre)=(Ana Pérez) viola uq_cuenta_firebase_uid")` → 500, `$.code` = `INTERNAL_ERROR`, el cuerpo y el log capturado **no** contienen `Ana Pérez`.
- **Trampa:** `org.hibernate.exception.ConstraintViolationException` está en el classpath por Spring Data JPA; si `LayeredArchitectureTest` prohíbe a `presentation` importar Hibernate, mover solo la extracción del nombre a un método estático de `infrastructure/persistence` (por ejemplo `ConstraintNames.of(Throwable)`) y llamarlo desde el manejador solo si la regla lo permite; si tampoco, **detenerse y reportar**.
- **Verificación:** `./mvnw.cmd -B -Dtest='CuentaConstraintsClassificationTest,BusinessExceptionHandlerTest,LayeredArchitectureTest' test` en verde (con Docker; sin Docker decir que la clasificación se omitió).

## [ ] T-1A.11 · Cierre del PR 1A — ≤ 45 min

- **Documentación formal:** (1) `docs/errores.md`: una fila por código emitido (`Código | HTTP | Mensaje | Origen | Prueba`), quitar de «Códigos que el servicio emite» la frase «Todavía no emite el campo `code`», quitar de «Respuestas publicadas que difieren del estándar» el `charset` (corregido en T-1A.6), actualizar el texto del 500 y agregar `DEPENDENCY_UNAVAILABLE`, `ROUTE_NOT_FOUND`, `METHOD_NOT_ALLOWED` y `MEDIA_TYPE_NOT_ALLOWED`; (2) `docs/estandar-backend.md`, sección 6: en el ejemplo, `"detail": "Revisa los campos marcados."` en lugar de «La contraseña es demasiado común.», una línea que diga «`detail` de un 422 con `errors` es fijo; el mensaje de cada campo va en `errors[].message`» y la causa `TOO_COMMON` en el vocabulario cerrado; la misma corrección se aplica a la copia de la skill `backend-estandar` (`estandar-estricto.md`, sección A) y el PR declara que Perfil, Gateway y Entrevista reciben el archivo en el PR de cada uno (la copia de Cuentas queda distinta hasta entonces); (3) `ErrorCodeDocumentationTest`: cada valor de `ErrorCode` aparece en `docs/errores.md` (lee el archivo con `Files.readString(Path.of("docs/errores.md"))` y comprueba `contains(codigo.name())`).
- **Verificación:** `./mvnw.cmd -B clean verify` con salida real; número de pruebas; cobertura JaCoCo de `BusinessExceptionHandler`, `ErrorCode`, `BusinessException`, `InvalidBirthDateException`, `WeakPasswordException`, `PasswordPolicy`: líneas y ramas reales (meta ≥ 90 %), cada una sin cubrir con su razón; la cobertura global del repo no baja.
- Revisión (`backend-estandar` §6): `/simplify`, `/code-review high`, autochequeo (¿algún `getMessage()` de librería llega al cliente? ¿algún log con dato personal? ¿algún comentario con `CM-NNN`?).
- Aviso a Frontend (documento por rol de `comunicaciones/`): el cuerpo de error agrega `code`, `requestId` y `errors[].code`; el `detail` de los 422 con `errors` pasa a ser fijo; el 500 cambia de texto; Firebase no disponible responde 503 en lugar de 500; nada se elimina.
- Entrega: commit `CM-36 | feat(cuentas): código de error en las respuestas de registro`; PR con el título `CM-36 | feat(cuentas): código de error estable en las respuestas de error de Cuentas [IA-ASISTIDO]`; descripción con la plantilla completa, atributos de calidad (seguridad, compatibilidad de contrato, mantenibilidad, observabilidad), qué es mecánico (cambios de constructor en las excepciones y pruebas) y qué importa revisar (`BusinessExceptionHandler`, `ErrorCode`). Tarjeta de Jira a «En revisión» solo al abrir el PR.

---

# PR 1B — fecha estricta, contraseña común, pronombre obligatorio

## [ ] T-1B.1 · Restricción de formato de fecha — ≤ 30 min, ≈ 120 líneas

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

## [ ] T-1B.2 · Contrato: `birthDate` texto, `pronoun` obligatorio, `toCommand()` — ≤ 30 min, ≈ 110 líneas

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
- **`"pronoun":""` y `"pronoun":"   "` → `PRONOUN_REQUIRED`** (pregunta 15 de la spec). **Trampa:** por defecto Jackson no convierte una cadena vacía en un enumerado: falla al leer el cuerpo y saldría como cuerpo ilegible. Configurar la coerción **solo para enumerados** (`coercionConfigFor(LogicalType.Enum)`: cadena vacía → `null`, y aceptar la cadena en blanco como vacía) en la configuración de Jackson existente; así `@NotNull` lo reporta como obligatorio. Antes de escribirla, comprobar con una prueba que esa API existe con ese nombre en la versión de Jackson del proyecto (como V-06); si no existe o se comporta distinto, detenerse y avisar. Pruebas en T-1B.3: `""` y `"   "` → 422 `pronoun`/`PRONOUN_REQUIRED`; `"OTRO"` sigue fallando (no se convierte en `null`).
- **Verificación:** `./mvnw.cmd -B -Dtest='BusinessExceptionHandlerTest,BirthDateFormatValidatorTest' test` en verde (la prueba de la tabla ahora exige las tres claves nuevas). El resto de pruebas del controlador se ajusta en T-1B.3.

## [ ] T-1B.3 · Pruebas del contrato del registro — ≤ 30 min, ≈ 170 líneas

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

Se ejecuta **después de fusionar 1A y 1B**; revalidar las rutas y líneas contra `origin/develop` ese día. Rama: `CM-36-recorte-nfc-registro` desde `develop`. Mensaje de commit: `CM-36 | fix(cuentas): <resultado>`. Aplican las reglas del inicio de este archivo.
**Pregunta 13 respondida (Paula, 6-oct-2026): «espacio» es el conjunto de `trim` de JavaScript (opción a), que es lo que se escribe aquí.** Este bloque y el 3 van en el mismo PR.

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
- **Pruebas** (`SingleLineTextTest`, JUnit 5 y AssertJ sin Spring, nombres en español camelCase): una prueba o fila de `@ParameterizedTest` por cada valor de la matriz (`plan.md` §9.4, fila `SingleLineTextTest`): `"  Ana  "`→`Ana`; `"\u00A0Ana\u00A0"`→`Ana`; `"\tAna\n"`→`Ana`; `"\uFEFFAna"`→`Ana`; `"\u200BAna"` se conserva (no es espacio); `"María  José"` conserva el doble espacio interno con `normalize` y queda `"María José"` con `normalizeName` (método nuevo: `normalize` y después cada secuencia de espacios del mismo conjunto del recorte pasa a un U+0020; `normalizeName(null)` → `null`); `"Ana\u00A0\u00A0Luz"` → `"Ana Luz"` con `normalizeName`; `"e\u0301"` (e + acento combinante) → `"é"` con `length()` 1; `"𝒜"` (U+1D49C) con `length()` 1 y `value().length()` 2; `""`, `"   "`, `"\u00A0"` → `isEmpty()`; `normalize(null)` → `null`; el constructor con `null` lanza `NullPointerException`.
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
      firstName = SingleLineText.normalizeName(firstName);   // además une los espacios internos repetidos
      lastName = SingleLineText.normalizeName(lastName);
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

# PR 3 — `PersonName`: solo letras

Se ejecuta después de fusionar el PR 2. Rama `CM-36-nombre-solo-letras` desde `develop`. Aplican las reglas del inicio de este archivo. Revalidar rutas y líneas contra `origin/develop`.

## [ ] T-3.1 · Objeto de valor `PersonName` y su excepción — ≤ 30 min, ≈ 150 líneas

- **Cubre:** REQ-RV-31, 32. **Crear:** `domain/model/PersonName.java`, `domain/exception/InvalidPersonNameException.java`, `src/test/.../domain/model/PersonNameTest.java`. **Modificar:** `domain/exception/ErrorCode.java` (agregar `FIRST_NAME_INVALID_CHARACTERS` y `LAST_NAME_INVALID_CHARACTERS` con su Javadoc).
- **Código de referencia:**
  ```java
  /** Nombres o apellidos de una persona: solo letras, espacios, apóstrofo y guion. */
  public final class PersonName {

      /** Parte del nombre completo; lleva el código, el campo del contrato y el texto del rechazo. */
      public enum Part {
          FIRST_NAME(ErrorCode.FIRST_NAME_INVALID_CHARACTERS, "firstName",
                  "El nombre solo puede contener letras, espacios, apóstrofo y guion."),
          LAST_NAME(ErrorCode.LAST_NAME_INVALID_CHARACTERS, "lastName",
                  "El apellido solo puede contener letras, espacios, apóstrofo y guion.");
          // campos finales, constructor y accesores code(), field(), message()
      }

      private static final int MAX_LENGTH = 120;
      /** Letras Unicode, marcas combinantes, espacio, apóstrofo recto y tipográfico (’) y guion. */
      private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{M} '’-]+$");
      private static final Pattern HAS_LETTER = Pattern.compile("\\p{L}");

      private final String value;

      public PersonName(String raw, Part part) { ... }   // ver el párrafo siguiente
  }
  ```
  `PersonName` es una **clase final inmutable** (no un `record`: su constructor recibe el texto y la parte) con `private final String value`, constructor `PersonName(String raw, Part part)` y `value()`. El constructor: `SingleLineText text = new SingleLineText(raw)` (si `raw` es `null`, `IllegalArgumentException("El nombre es obligatorio")`); si `text.isEmpty()` → `IllegalArgumentException("El nombre es obligatorio")`; si `text.length() > 120` → `IllegalArgumentException("El nombre supera 120 caracteres")` (invariantes defensivas: el borde ya respondió); si `!ALLOWED.matcher(v).matches() || !HAS_LETTER.matcher(v).find()` → `throw new InvalidPersonNameException(part)`. `InvalidPersonNameException extends BusinessException` con `super(part.code(), part.message())` y `getField()` que devuelve `part.field()`. Javadoc completo en español (por qué se admiten las marcas combinantes, por qué el apóstrofo tipográfico).
- **Pruebas** (`PersonNameTest`, sin Spring; `@ParameterizedTest @ValueSource` para cada lista): inválidos con `Part.FIRST_NAME` y con `Part.LAST_NAME` (el mensaje y el código son los de cada parte): `Ana3`, `Pérez_`, `---`, `'`, `’`, `Ana.`, `Ana@`, `<script>`, `12345`, `Ana–Luz`, `Ana😀`, y el NUL como `"Ana" + (char) 0`. Válidos: `María José`, `O'Neill`, `O’Neill`, `Gómez-Ruiz`, `Müller`, `Muñoz`, `A`, `B`, `Ñandú`, `李`, `Åsa`, `María José`, `"a".repeat(120)`, `"Jose\u0301"` (su `value()` es `José`). Defensivas: `""`, `"   "` y 121 letras lanzan `IllegalArgumentException`. `toString` no se sobrescribe (el nombre no es secreto, pero no se registra: no hay logs en la clase).
- **Trampas:** en el fuente, escribir `\\p{L}` con dos barras dentro de la cadena Java; el apóstrofo tipográfico va como el carácter ’ (el proyecto compila en UTF-8); el guion al final de la clase `[...-]` no define un rango.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=PersonNameTest test` en verde; `LayeredArchitectureTest` en verde (el dominio no importa Spring).

## [ ] T-3.2 · El servicio valida los nombres antes de Firebase — ≤ 20 min, ≈ 30 líneas

- **Cubre:** REQ-RV-31. **Modificar:** `application/service/RegisterUserService.java` (método `register`, antes de `directorio.createUser`) y `RegisterUserServiceTest`.
- **Cambio:** después de construir `email`, `password` y `birthDate`, agregar
  ```java
  PersonName firstName = new PersonName(command.firstName(), PersonName.Part.FIRST_NAME);
  PersonName lastName = new PersonName(command.lastName(), PersonName.Part.LAST_NAME);
  ```
  y pasar `firstName.value()` y `lastName.value()` a `Account.register(...)`. Agregar `@throws InvalidPersonNameException` al Javadoc.
- **Pruebas** (con el doble `InMemoryFirebaseUserDirectory`, como las existentes): `unNombreConNumerosNoCreaCredencialNiCuenta` (`Ana3` → `InvalidPersonNameException`, el directorio queda sin usuarios y el repositorio sin cuentas); lo mismo con apellido `Pérez_`; `unNombreConTildesSeGuardaTalCual` (`María José` y `Gómez-Ruiz`).
- **Verificación:** `./mvnw.cmd -B -Dtest=RegisterUserServiceTest test` en verde.

## [ ] T-3.3 · Manejador y pruebas del contrato — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-RV-31; CA-1.1.31, 1.1.39, 1.1.40. **Modificar:** `presentation/advice/BusinessExceptionHandler.java`, `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest`.
- **Manejador** (mismo molde que `fechaInvalida`): `@ExceptionHandler(InvalidPersonNameException.class) ProblemDetail nombreInvalido(InvalidPersonNameException error)` → 422, título `Datos no válidos`, `code` de nivel superior `VALIDATION_FAILED`, `errors` con un elemento `campo(error.getField(), error.getErrorCode(), error.getMessage())`.
- **Pruebas del controlador:** el servicio simulado lanza `new InvalidPersonNameException(Part.FIRST_NAME)` → 422, `field` = `firstName`, `code` = `FIRST_NAME_INVALID_CHARACTERS`, `message` = «El nombre solo puede contener letras, espacios, apóstrofo y guion.»; igual con `LAST_NAME`.
- **E2E (Docker):** (1) `Ana3` y `Pérez_` → 422 en cada campo y sin rastro (misma comprobación que la prueba del menor de edad); (2) `María José`/`Gómez-Ruiz`, `O'Neill`/`Müller` y `A`/`B` → 201 y la fila guarda los textos tal cual.
- **Verificación:** `./mvnw.cmd -B -Dtest='UserRegistrationControllerTest,AccountRegistrationEndToEndTest,BusinessExceptionHandlerTest' test` en verde.

## [ ] T-3.4 · Cierre del PR 3 — ≤ 30 min

`clean verify` con salida real y cobertura de `PersonName`, `InvalidPersonNameException`, `RegisterUserService` (≥ 90 %); `/simplify`, `/code-review high`; aviso a Frontend (nuevos códigos de nombre y apellido; los textos son los del CA). PR: `CM-36 | feat(cuentas): nombre y apellido solo con letras, espacios, apóstrofo y guion [IA-ASISTIDO]`.

---

# PR 4 — lista de 3000 contraseñas comunes

Rama `CM-36-contrasenas-comunes` desde `develop`. **Fuente decidida (pregunta 3): SecLists, filtrada.** Los datos van en un commit aparte.

## [ ] T-4.1 · La política recibe la lista por constructor — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-RV-40. **Modificar:** `domain/policy/PasswordPolicy.java`, `PasswordPolicyTest`, `RegisterUserServiceTest` y cualquier otra prueba que haga `new PasswordPolicy()` (buscar con `git grep "new PasswordPolicy"`).
- **Cambio:** quitar el `Set.of(...)` de 32 entradas y el comentario que lo justifica; agregar `private final Set<String> commonPasswords;` y
  ```java
  /**
   * @param commonPasswords contraseñas rechazadas aunque cumplan la longitud, ya en minúsculas;
   *                        la lista que usa la aplicación se carga de un recurso versionado
   */
  public PasswordPolicy(Set<String> commonPasswords) {
      this.commonPasswords = Set.copyOf(commonPasswords);
  }
  ```
  y `esConocida` usa `commonPasswords`. Sin constructor sin parámetros. Actualizar el Javadoc de la clase (la lista ya no está en el código; el comparador ignora mayúsculas y recorta los extremos).
- **Pruebas:** en `PasswordPolicyTest` y `RegisterUserServiceTest`, `new PasswordPolicy(Set.of("123456789012", "password1234", "qwertyuiop123"))`. Mantener todas las pruebas existentes (11/12/64/65 caracteres, emoji, ignora mayúsculas y espacios, valor nunca en el mensaje) y agregar: una contraseña que no está en la lista pasa aunque se parezca (`password12345` con una lista que solo tiene `password1234`); la lista vacía no rechaza nada.
- **Verificación:** `./mvnw.cmd -B -Dtest='PasswordPolicyTest,RegisterUserServiceTest' test` en verde. Esta tarjeta deja la aplicación sin bean de política hasta T-4.2: no ejecutar el contexto completo entre ambas.

## [ ] T-4.2 · Cargador del recurso y ensamblado — ≤ 30 min, ≈ 130 líneas

- **Cubre:** REQ-RV-40, 41. **Crear:** `infrastructure/config/CommonPasswordsLoader.java`, `src/test/resources/security/common-passwords-test.txt` (20 contraseñas de 12 o más caracteres en minúsculas, una por línea, incluidas las tres del CA), `src/test/.../infrastructure/config/CommonPasswordsLoaderTest.java`. **Modificar:** `infrastructure/config/DomainPolicyConfiguration.java` (método `passwordPolicy`).
- **Código de referencia:**
  ```java
  /** Lee la lista de contraseñas comunes y comprueba que cumple lo que la política supone de ella. */
  public final class CommonPasswordsLoader {
      static final int MINIMUM_ENTRIES = 3000;
      static final int MINIMUM_LENGTH = 12;

      /**
       * @param resource recurso UTF-8 con una contraseña por línea
       * @param minimumEntries mínimo de entradas exigido
       * @return conjunto inmutable de contraseñas en minúsculas
       * @throws IllegalStateException con la causa exacta si el recurso falta, está vacío, tiene menos entradas
       *         de las exigidas, una línea vacía, una entrada de menos de 12 caracteres, con mayúsculas o repetida
       */
      public static Set<String> load(Resource resource, int minimumEntries) { ... }
  }
  ```
  Reglas: leer con `StandardCharsets.UTF_8`, una línea = una entrada; línea vacía, entrada de menos de 12 puntos de código, entrada distinta de su `toLowerCase(Locale.ROOT)` y entrada repetida son errores (con el número de línea, **sin imprimir la contraseña**); menos de `minimumEntries` es un error. En `DomainPolicyConfiguration`: `@Bean PasswordPolicy passwordPolicy(@Value("classpath:security/common-passwords.txt") Resource lista)` → `new PasswordPolicy(CommonPasswordsLoader.load(lista, CommonPasswordsLoader.MINIMUM_ENTRIES))`.
- **Pruebas** (`CommonPasswordsLoaderTest`, con `ByteArrayResource` y con el recurso de prueba): archivo válido de 20 → conjunto de 20; con `minimumEntries` 21 → falla; línea vacía; entrada de 11 caracteres; entrada con mayúsculas; entrada repetida; recurso inexistente (`ClassPathResource("security/no-existe.txt")`); archivo vacío. En todos, el mensaje nombra la causa y el número de línea y **no** contiene la contraseña. Usar `assertThatThrownBy(...).isInstanceOf(IllegalStateException.class).hasMessageContaining(...)`.
- **Trampa:** hasta que exista el recurso real (T-4.3), el contexto de Spring no arranca (`FileNotFoundException`): las pruebas con contexto (`CuentaSchemaMigrationTest`, E2E) **fallarán hasta T-4.3**; por eso T-4.2 y T-4.3 van en el mismo PR y se ejecutan juntas en CI. Para trabajar antes de tener los datos, usar temporalmente `src/main/resources/security/common-passwords.txt` con 3000 entradas generadas (`pass` + número rellenado a 12 caracteres) **sin comprometerlas** (`git update-index --assume-unchanged` o dejarlas fuera del commit).
- **Verificación:** `./mvnw.cmd -q -B -Dtest=CommonPasswordsLoaderTest test` en verde.

## [ ] T-4.3 · Datos: la lista de 3000 — ≤ 30 min de trabajo + revisión de licencia — fuente: SecLists filtrada a 12 o más caracteres (pregunta 3); al empezar se verifican la licencia MIT y que haya 3000 entradas, y si no, se detiene

- **Cubre:** REQ-RV-40, 42; CA-1.1.27. **Crear:** `src/main/resources/security/common-passwords.txt` y `src/main/resources/security/common-passwords.README.md`; **Crear (prueba):** `src/test/.../infrastructure/config/CommonPasswordsFileTest.java`.
- **Procedimiento (en una carpeta temporal vacía, fuera del repositorio; la fuente es un archivo no confiable: se lee, nunca se ejecuta):** (1) descargar la fuente elegida y registrar URL, fecha y licencia; (2) filtrar las entradas de 12 o más puntos de código, pasar a minúsculas, recortar, quitar repetidas y conservar el orden de popularidad de la fuente; (3) tomar las primeras 3000; si hay menos, completar con la segunda fuente y registrarla; (4) comprobar que están `123456789012`, `password1234` y `qwertyuiop123` (si falta alguna, **detenerse y reportar**); (5) escribir el archivo con saltos de línea `\n`, UTF-8 sin BOM. El `README` registra: fuente(s), URL, licencia, fecha, el comando exacto con que se generó y la frase «La lista se entrega a `cameia-web` de la forma decidida en la pregunta 9».
- **Prueba** (`CommonPasswordsFileTest`, sin contexto): cargar el recurso real con `CommonPasswordsLoader.load(..., 3000)`; exactamente 3000 entradas; las tres del CA presentes; `PasswordPolicy` con esa lista rechaza `123456789012`, `PASSWORD1234` y `  qwertyuiop123  ` con `PASSWORD_TOO_COMMON`.
- **Entrega:** commit aparte `CM-36 | chore(cuentas): lista de 3000 contraseñas comunes`; en el PR, decir qué es dato (no se revisa línea por línea) y qué es código.
- **Verificación:** `./mvnw.cmd -B test` en verde, contexto completo incluido.

## [ ] T-4.4 · Cierre del PR 4 — ≤ 30 min

`clean verify`, cobertura de `PasswordPolicy`, `CommonPasswordsLoader` y `DomainPolicyConfiguration` (≥ 90 %); **`/security-review`** (ASVS 6.2.4); medir el arranque antes y después (la carga de 3000 líneas) y reportarlo; aviso a Frontend con la ruta del archivo. PR: `CM-36 | feat(cuentas): lista de 3000 contraseñas comunes cargada desde un recurso versionado [IA-ASISTIDO]`.

---

# PR 5 — celular con `libphonenumber`

Rama `CM-36-celular-libphonenumber` desde `develop`. **Dependencia aprobada (pregunta 11) y tipos de número decididos (pregunta 9: los mismos que `isValid` del cliente).**

## [ ] T-5.1 · Dependencia — ≤ 15 min, ≈ 8 líneas

- **Cubre:** REQ-RV-50. **Modificar:** `pom.xml`. **Antes:** leer `https://repo1.maven.org/maven2/com/googlecode/libphonenumber/libphonenumber/maven-metadata.xml` y anotar el valor de `<release>`; usar **esa** versión, declarada como propiedad `<libphonenumber.version>` junto a `firebase-admin.version` (línea 32).
- **Cambio:** agregar a `<dependencies>`: `groupId` `com.googlecode.libphonenumber`, `artifactId` `libphonenumber`, `version` `${libphonenumber.version}`. **Detenerse** si las coordenadas no existen o si la licencia no es Apache 2.0, y reportar.
- **Verificación:** `./mvnw.cmd -q -B -DskipTests package` compila.

## [ ] T-5.2 · `PhoneNumber` valida con la librería — ≤ 30 min, ≈ 110 líneas

- **Cubre:** REQ-RV-50, 52. **Crear:** `domain/exception/InvalidPhoneNumberException.java` (`extends BusinessException`, `super(ErrorCode.PHONE_NUMBER_INVALID_FORMAT, "Revisa el número, no coincide con el formato del país elegido.")`, `getField()` = `phoneNumber`). **Modificar:** `domain/exception/ErrorCode.java` (agregar `PHONE_NUMBER_INVALID_FORMAT`), `domain/model/PhoneNumber.java`, `ValueObjectsTest` (clase anidada del celular, línea 53 en adelante).
- **Cambio en el constructor compacto** (después del recorte y de la comprobación E.164 actual): 
  ```java
  PhoneNumberUtil util = PhoneNumberUtil.getInstance();
  try {
      Phonenumber.PhoneNumber parsed = util.parse(value, null);   // el texto lleva «+», no hace falta región
      if (!util.isValidNumber(parsed) || !value.equals(util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164))) {
          throw new InvalidPhoneNumberException();
      }
  } catch (NumberParseException fallo) {
      throw new InvalidPhoneNumberException();
  }
  ```
  El `IllegalArgumentException` del valor nulo o vacío se conserva (inalcanzable por la API tras T-5.3). El texto «debe incluir el indicativo del país…» de la comprobación E.164 actual se sustituye: si no cumple `^\+[1-9][0-9]{7,14}$` también lanza `InvalidPhoneNumberException`.
- **Pruebas** (en la clase anidada del celular; se ajustan las existentes que esperaban `IllegalArgumentException`): acepta `+573000000000`, `+34612345678`, `+576012345678`, `+14155552671`; rechaza con `InvalidPhoneNumberException` (código y texto literales): `12345`, `+57300`, `3000000000`, `+57 300 000 0000`, `+99912345678`, `+5730000000000000`, `+573000000000abc`. `rechazaUnNumeroSinIndicativoDePais` y `rechazaUnNumeroConSeparadores` pasan a esperar la excepción nueva. `noRevelaSuValorAlConvertirseATexto` sigue en verde.
- **Trampa:** `PhoneNumberUtil.parse` con un texto sin `+` y región `null` lanza `NumberParseException`; aquí ya se rechazó antes por forma. Un valor que la librería da por válido pero cuyo formato canónico difiere (con ceros iniciales o extensión) se rechaza por la comparación.
- **Lectura de filas ya guardadas (no omitir):** `AccountMapper.toDomain` reconstruye con `new PhoneNumber(entity.getTelefono())`. Con la validación nueva, una fila antigua que solo cumplía la forma E.164 haría fallar la lectura (activación, registro repetido y purga responderían con error). Agregar a `PhoneNumber` una fábrica `static PhoneNumber rebuild(String stored)` que solo comprueba la forma E.164 (sin la librería) y usarla en `AccountMapper.toDomain`; prueba en `AccountRepositoryAdapterTest` o `ValueObjectsTest`: `rebuild("+57300")` no lanza mientras que `new PhoneNumber("+57300")` sí. Archivos adicionales: `domain/model/PhoneNumber.java` y `infrastructure/persistence/mapper/AccountMapper.java`.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=ValueObjectsTest test` en verde.

## [ ] T-5.3 · Sin celular, manejador y compatibilidad con la base — ≤ 30 min, ≈ 120 líneas

- **Cubre:** REQ-RV-51, 52, 53; CA-1.1.29. **Modificar:** `presentation/dto/RegisterUserRequest.java` (`toCommand()`), `presentation/advice/BusinessExceptionHandler.java`. **Crear:** `src/test/.../domain/model/PhoneNumberDatabaseCompatibilityTest.java`.
- **`toCommand()`:** el celular pasa por `SingleLineText.normalize`; si queda `null` o vacío, el comando lleva `null`.
- **Manejador:** `InvalidPhoneNumberException` → 422, `code` `VALIDATION_FAILED`, `errors` con `campo(error.getField(), error.getErrorCode(), error.getMessage())`.
- **`PhoneNumberDatabaseCompatibilityTest`** (sin Spring): para cada región de `PhoneNumberUtil.getSupportedRegions()` y para los tipos `MOBILE` y `FIXED_LINE`, `util.getExampleNumberForType(region, tipo)` (si no es `null`) formateado en E.164 debe cumplir `^\+[1-9][0-9]{7,14}$` (la restricción `ck_cuenta_telefono_e164`). Si algún número no la cumple, la prueba lista las regiones y **se detiene el bloque**: decidir con Paula entre una migración V4 que relaje el mínimo o rechazar esos números (no improvisar).
- **Pruebas del controlador:** celular ausente, `null`, `""`, `"   "` → 201 con `phoneNumber()` nulo en el comando; `"  +573000000000  "` → el comando lleva `+573000000000`; servicio simulado que lanza `InvalidPhoneNumberException` → 422, `field` = `phoneNumber`, `code` = `PHONE_NUMBER_INVALID_FORMAT`, mensaje literal.
- **Verificación:** `./mvnw.cmd -B -Dtest='PhoneNumberDatabaseCompatibilityTest,UserRegistrationControllerTest,BusinessExceptionHandlerTest' test` en verde.

## [ ] T-5.4 · Punta a punta y paridad con el cliente (V-05) — ≤ 30 min, ≈ 70 líneas

- **E2E (Docker):** `+573000000000` → 201 y la columna `telefono` guarda `+573000000000`; `+34612345678` → 201 y guarda `+34612345678`; `12345` → 422 `PHONE_NUMBER_INVALID_FORMAT` sin rastro; sin celular → 201 y `telefono` es `NULL`.
- **V-05:** la tabla con el resultado de `isValidNumber` de la versión Java para los 11 valores de la matriz (sección 12 del plan) va en la descripción del PR, con la nota de que Frontend debe comparar con `libphonenumber-js` (`isValid`); si algún valor del CA difiere, se reporta antes de abrir el PR.
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest=AccountRegistrationEndToEndTest test` en verde.

## [ ] T-5.5 · Cierre del PR 5 — ≤ 30 min

`clean verify`; cobertura de `PhoneNumber`, `InvalidPhoneNumberException` y `RegisterUserRequest` (≥ 90 %); `/security-review` (dependencia nueva: confirmar su licencia y que CodeQL/Dependabot la cubren); aviso a Frontend. PR: `CM-36 | feat(cuentas): validar el celular con libphonenumber [IA-ASISTIDO]`.

---

# PR 6 — etiquetas, textos y pruebas que faltan

Rama `CM-36-etiquetas-textos-registro` desde `develop`, al final. Estado base: después de los PR 1A a 5.

## [x] T-6.1 · `EmailAddress` con excepción tipada — ≤ 30 min, ≈ 80 líneas

- **Resultado (7-oct):** hecha. Además de la tarjeta, el correo rechaza los caracteres invisibles (separadores, de control y de formato). Pruebas en `ValueObjectsTest` (14 formas inválidas, 4 válidas, límite 254/255), `BusinessExceptionHandlerTest`, `UserRegistrationControllerTest` y `AccountRegistrationEndToEndTest`.

- **Cubre:** REQ-RV-60; CA-1.1.20. **Crear:** `domain/exception/InvalidEmailException.java` (con `getField()` = `email`; constructor `(ErrorCode code, String mensaje)`). **Modificar:** `domain/exception/ErrorCode.java` (`EMAIL_INVALID_FORMAT`), `domain/model/EmailAddress.java`, `presentation/advice/BusinessExceptionHandler.java`, `ValueObjectsTest`.
- **Cambio:** en `EmailAddress`, el formato inválido lanza `new InvalidEmailException(ErrorCode.EMAIL_INVALID_FORMAT, "Ingresa un correo electrónico válido.")` y el exceso de longitud `new InvalidEmailException(ErrorCode.EMAIL_TOO_LONG, "El correo no puede superar los 254 caracteres.")`; vacío o nulo sigue siendo `IllegalArgumentException` defensiva (el borde responde antes). El manejador: 422, `VALIDATION_FAILED`, un elemento con el campo, el código y el mensaje de la excepción.
- **Pruebas:** `ana`, `ana@correo`, `ana@@correo.co`, `ana@correo..co`, `ana @correo.co` → `InvalidEmailException` con `EMAIL_INVALID_FORMAT`; 255 puntos de código → `EMAIL_TOO_LONG`; válidos `ana@correo.co`, `ana.perez+cameia@correo.com`, `ANA@Correo.CO` (queda `ana@correo.co`); controlador: servicio simulado que lanza la excepción → 422, `field` = `email`. Ajustar las pruebas existentes de `ValueObjectsTest` que esperaban `IllegalArgumentException` por formato.
- **Verificación:** `./mvnw.cmd -B -Dtest='ValueObjectsTest,UserRegistrationControllerTest' test` en verde.

## [x] T-6.2 · Cuerpo ilegible, pronombre inválido y fin del manejador genérico — ≤ 30 min, ≈ 110 líneas

- **Resultado (7-oct):** hecha. V-06: en Jackson 3 no se usa la propiedad de `application.properties` de la tarjeta; la regla va en `JacksonConfiguration` con `EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS`, y `"pronoun":1`, `0`, `-1` y `1.5` ya responden `PRONOUN_INVALID_VALUE` (prueba del controlador con el mismo `JsonMapper` y prueba de punta a punta con la aplicación real). Corrección sobre la tarjeta: la ruta del campo se lee solo de `MismatchedInputException`; un JSON mal formado dentro del pronombre (`{"pronoun": SHE}`) trae la misma ruta y debe seguir siendo `REQUEST_BODY_INVALID_FORMAT`.

- **Cubre:** REQ-RV-30, 61, 65. **Modificar:** `presentation/advice/BusinessExceptionHandler.java`, `domain/exception/ErrorCode.java` (`PRONOUN_INVALID_VALUE`), `src/main/resources/application.properties`, `BusinessExceptionHandlerTest`, `UserRegistrationControllerTest`.
- **`cuerpoIlegible`:** si `error.getCause()` es `tools.jackson.databind.exc.MismatchedInputException` y el último elemento de `getPath()` tiene `getPropertyName()` igual a `"pronoun"` → 422, `VALIDATION_FAILED`, un elemento `campo("pronoun", PRONOUN_INVALID_VALUE, <texto de la pregunta 10 de la spec>)`; en cualquier otro caso → 422, `REQUEST_BODY_INVALID_FORMAT`, `detail` «Revisa el formato de los datos enviados.» (sin la mención a la fecha). El `logger.warn` sigue registrando solo el nombre de la clase de la excepción. (Si la pregunta 6 de la spec resulta en 400, el estado cambia solo en la rama «cualquier otro caso».)
- **Eliminar** el método `valorInvalido(IllegalArgumentException)` y su `import`; una `IllegalArgumentException` pasa a `falloInterno` (500 `INTERNAL_ERROR`). Verificar con `git grep -n "IllegalArgumentException" src/main` que no quedó una ruta alcanzable por la API (las de `RawPassword`, `BirthDate`, `SingleLineText`, `PersonName` y `PhoneNumber` son defensivas).
- **Propiedad de Jackson (V-06):** agregar a `application.properties` `spring.jackson.deserialization.fail-on-numbers-for-enums=true`. **Verificar primero** que Spring Boot 4.1.1 con Jackson 3 reconoce ese nombre (arrancar la prueba de la tarjeta y comprobar que `"pronoun":1` ya no se acepta). Si la propiedad no existe o no surte efecto, **detenerse y reportar**: la alternativa es un deserializador de enumerados estricto (decisión de Paula).
- **Pruebas:** `"pronoun":"OTRO"`, `"he"`, `1`, `true` → 422, `field` = `pronoun`, `code` = `PRONOUN_INVALID_VALUE`; `"pronoun":""` y `"   "` → 422 `PRONOUN_REQUIRED` (pregunta 15); JSON mal formado (`{"firstName":`), `birthDate` como `[]` y como `{}` → 422 `REQUEST_BODY_INVALID_FORMAT` y `detail` sin la palabra «fecha»; un `IllegalArgumentException("secreto de librería")` lanzado por un controlador de prueba → 500, `INTERNAL_ERROR`, sin el texto.
- **Verificación:** `./mvnw.cmd -B -Dtest='BusinessExceptionHandlerTest,UserRegistrationControllerTest' test` en verde.

## [x] T-6.3 · Textos del catálogo — ≤ 30 min, ≈ 90 líneas

- **Resultado (7-oct):** hecha, con `docs/errores.md` al día. Textos de la columna «Antes» que siguen en `cameia-web` (solo lectura, se avisa a Frontend): `src/mocks/handlers/auth.handlers.ts` líneas 82 y 163.

- **Cubre:** REQ-RV-62, 63; CA-1.1.2, 1.1.3, 1.1.6, 1.1.7, 1.1.9 a 1.1.13, 1.1.17, 1.1.19, 1.1.23, 1.1.26. **Modificar:** `RegisterUserRequest.java`, `PasswordPolicy.java`, `AgePolicy.java`, `EmailAlreadyRegisteredException.java` y las pruebas que comparan texto.
- **Tabla antes → después (solo cambia el texto; códigos y estados no):**
  | Dónde | Antes | Después |
  |---|---|---|
  | `firstName` `@NotBlank` | Los nombres son obligatorios | Ingresa tu nombre. |
  | `firstName` `@CodePointSize` | Los nombres no pueden superar los 120 caracteres | El nombre no puede superar los 120 caracteres. |
  | `lastName` `@NotBlank` | Los apellidos son obligatorios | Ingresa tu apellido. |
  | `lastName` `@CodePointSize` | Los apellidos no pueden superar los 120 caracteres | El apellido no puede superar los 120 caracteres. |
  | `birthDate` `@NotBlank` | La fecha de nacimiento es obligatoria | Ingresa tu fecha de nacimiento. |
  | `email` `@NotBlank` | El correo electrónico es obligatorio | Ingresa tu correo electrónico. |
  | `password` `@NotBlank` | La contraseña es obligatoria | Ingresa tu contraseña. |
  | `PasswordPolicy` corta | La contraseña debe tener al menos 12 caracteres | La contraseña debe tener al menos 12 caracteres. |
  | `PasswordPolicy` larga | La contraseña no puede superar los 64 caracteres | La contraseña no puede superar los 64 caracteres. |
  | `AgePolicy` futura | Fecha de nacimiento inválida | Fecha de nacimiento inválida. |
  | `AgePolicy` menor | Debes ser mayor de edad | Debes ser mayor de edad. |
  | `AgePolicy` más de 110 | La fecha de nacimiento no es plausible, por favor verifícala | Verifica tu fecha de nacimiento. |
  | `EmailAlreadyRegisteredException` | Este correo ya se encuentra registrado | Ese correo ya tiene una cuenta. |
  | Detalle del 422 de campos | Revisa los campos marcados | Revisa los campos marcados. |
  El texto de `Account.register` («Los nombres son obligatorios») es una invariante defensiva inalcanzable: no se toca.
- **Pruebas:** actualizar cada expectativa de texto de `UserRegistrationControllerTest`, `PasswordPolicyTest`, `AgePolicyTest`, `AccountRegistrationEndToEndTest`; agregar una prueba por texto de la tabla en el controlador (una por campo). **Detenerse** si una prueba de Frontend o un documento del repo citan un texto de la columna «Antes» y reportarlo.
- **Verificación:** `./mvnw.cmd -B test` en verde.

## [x] T-6.4 · Pruebas de edad con reloj fijo — ≤ 30 min, ≈ 90 líneas

- **Resultado (7-oct):** hecha: 12 filas en `AgePolicyTest.cadaLimiteDeEdadSeEvaluaConElDiaExacto` y la prueba de la hora de Colombia.

- **Cubre:** CA-1.1.3 a 1.1.7. **Modificar:** `domain/policy/AgePolicyTest.java` (reloj `Clock.fixed(... UTC)` como en la línea 32).
- **Casos** (hoy = 2026-10-06 UTC; cada uno con su `Reason` o aceptación): nacido 2008-10-06 (cumple 18 hoy) → acepta; 2008-10-07 (mañana) → `UNDERAGE`; 1915-10-07 (cumple 111 mañana) → acepta; 1915-10-06 (111 hoy) → `IMPLAUSIBLE` (código `BIRTH_DATE_OUT_OF_RANGE`); 2026-10-07 → `IN_THE_FUTURE`; 2026-10-06 → `UNDERAGE`; nacido el 2000-02-29 con hoy 2018-02-28 → `UNDERAGE` y con hoy 2018-03-01 → acepta (cumple el 1 de marzo en año no bisiesto); nacido el 2000-12-31 con hoy 2018-12-31 → acepta y con hoy 2018-12-30 → `UNDERAGE`; nacido el 2000-01-01 con hoy 2017-12-31 → `UNDERAGE` y con hoy 2018-01-01 → acepta; entre las 19:00 y las 24:00 de Colombia (`Instant.parse("2026-10-07T01:30:00Z")`) la fecha es 2026-10-07 (prueba existente `laEdadSeCalculaEnUtc…`, mantenerla). Una fila `@ParameterizedTest @CsvSource` para los pares de fecha de nacimiento y reloj.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=AgePolicyTest test` en verde.

## [x] T-6.5 · Casos que cumplen sin prueba (1.1.41, 1.1.42, 1.1.29, cumpleaños) — ≤ 30 min, ≈ 90 líneas

- **Resultado (7-oct):** hechas (1), (3), (4) y (5); el doble de Firebase ahora guarda la contraseña recibida para comprobar que llega sin recortar. **(2) no se escribió contra el 200:** CM-251 no está en `develop`. La prueba de bloque 2 (`unCorreoConEspaciosYMayusculasSeGuardaNormalizadoYBloqueaElSiguiente`) sigue esperando 409 y la tarea del registro repetido la cambia a 200 al fusionarse.

- **Cubre:** CA-1.1.29, 1.1.41, 1.1.42. **Modificar:** `UserRegistrationControllerTest`, `AccountRegistrationEndToEndTest`.
- **Pruebas:** (1) CA-1.1.41: contraseña `mi clave larga 🙂` (16 puntos de código) → 201 y el comando lleva exactamente ese texto; en el E2E, el registro se completa y la credencial se crea con esa contraseña (el doble de Firebase la recibe sin recortar). (2) CA-1.1.42: `  Ana@Correo.CO ` → 201 con `ana@correo.co` y un segundo registro con `ana@correo.co` → 200 con el mismo `id` y `firebaseUid` (la cuenta sigue pendiente: CA-1.1.30, que CM-251 ya dejó en `develop` antes de este bloque) y una sola credencial en el doble de Firebase. Si al empezar el bloque CM-251 no está fusionada, se detiene y se avisa: no se escribe la prueba contra el 409. (3) CA-1.1.29: sin celular → 201 y `telefono` `NULL`. (4) Cuerpo con `estado`:`ACTIVE` y `plan`:`PREMIUM` extra → 201, la cuenta queda `PENDING_VERIFICATION` y `FREE` (RT-01-CA06). (5) Un campo desconocido cualquiera se ignora.
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest='UserRegistrationControllerTest,AccountRegistrationEndToEndTest' test` en verde.

## [x] T-6.6 · Verificaciones V-01 y tamaño del cuerpo — ≤ 30 min, sin cambios de producción

- **Resultado (7-oct), con la aplicación empaquetada y el emulador:** correo de 254 puntos de código (parte local de 64, etiquetas de 63, 63, 58 y 2) → 201; de 255 → 422 `EMAIL_TOO_LONG`; parte local de 65 con 254 en total → 201 (el servicio y el emulador la aceptan aunque RFC 5321 limita la parte local a 64; falta confirmarlo con Firebase real en staging: si lo rechaza, hoy respondería 500). Cuerpo de 2 MB → 422 `FIRST_NAME_TOO_LONG` en 0,06 s; de 20 MB → 422 en 0,38 s y la memoria del proceso pasó de 308 a 459 MB con esa sola petición. Alimenta la pregunta 14 (límite del cuerpo, tarea aparte).

- **V-01:** con el emulador de Firebase Auth (ver `CLAUDE.md` y la spec de arranque con el emulador), registrar un correo de exactamente 254 puntos de código con parte local de 64 (`"a".repeat(64) + "@" + dominio de 189 caracteres en etiquetas de hasta 63`) y anotar la respuesta. Si Firebase lo rechaza, **no recortar ni cambiar el límite**: informar a Vela (el CA-1.1.21 usa el máximo que Firebase acepte).
- **Tamaño del cuerpo:** enviar a `POST /api/v1/users` un JSON de 2 MB y otro de 20 MB con `firstName` enorme y anotar estado, tiempo y memoria. No se corrige aquí: el resultado alimenta la pregunta 14 de la spec.
- **Evidencia:** las salidas (sin datos reales) van en el informe del PR.

## [ ] T-6.7 · Cierre del PR 6 y de la tarea — ≤ 45 min

`clean verify` con cobertura de toda la clase tocada (≥ 90 %); `/simplify`, `/code-review high`, `/security-review`; Postman o `.http` con los casos de la sección 5 de la spec; aviso a Frontend con el cambio de textos y de códigos; recorrido final de los 44 CA contra el código fusionado (lo hace B22); comentario de Jira con el formato del `CLAUDE.md`. PR: `CM-36 | fix(cuentas): etiquetas de campo, textos del catálogo y pruebas de borde del registro [IA-ASISTIDO]`.

- **Avance (7-oct):** `clean verify` en verde (654 pruebas, cobertura incluida). `/code-review high` y `/security-review` hechos: seguridad sin hallazgos; las correcciones de código quedaron en D14 a D18. Recorrido de los CA contra la aplicación empaquetada, el emulador de Firebase Auth y una base desechable: 94 casos, 93 cumplen; el que falta es CA-1.1.30 (200 en el registro repetido), que entrega CM-251. Ningún correo ni contraseña en el log de ese recorrido. Pendientes: aviso a Frontend (textos, códigos, el 406 y el pronombre como texto, que no cambia el contrato JSON), comentario de Jira y PR.
