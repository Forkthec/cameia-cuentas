# Plan — Aceptación de la política de tratamiento de datos en Cuentas

Spec: `spec.md` de esta carpeta. Base: `origin/develop` `c94f20c`. Rama: `CM-367-aceptacion-politicas-cuentas` desde `origin/develop`, en un worktree aparte (`cameia-worktrees\cameia-cuentas-CM-367`). Un PR hacia `develop`, sin stack: la tarea no depende de otra CM sin fusionar.

## 1. Foco de revisión (lo que más probablemente falle)

| # | Situación | Prueba que lo vigila | En el plan |
|---|---|---|---|
| F-1 | `dataAuthorization` con `null`: `@AssertTrue` lo daría por válido. Por eso se usa `@ExplicitConsent` sobre `Object` | `…_whenFieldIsNull`, `…_whenFieldIsTextTrue` | Sí (T4) |
| F-2 | El validador con la versión inyectada falla en las pruebas `standaloneSetup` (no hay contexto de Spring que lo construya) | Toda la clase `UserRegistrationControllerTest` en verde con `setValidator(...)` | Sí (T4, trampa) |
| F-3 | `.env` con `CAMEIA_POLICY_VERSION=` vacía anula el valor por defecto y el servicio no arranca | `PolicyPropertiesTest.shouldFailStartup_whenBlank`; `.env.example` comentado | Sí (T3) |
| F-4 | El adaptador `save` sobre una fila existente solo copia `estado`: la aceptación nueva no se guardaría | `AccountRepositoryAdapterTest.save_shouldUpdatePolicyAcceptance_whenAccountExists` | Sí (T2) |
| F-5 | Migración sobre filas: `version` o `fecha_actualizacion` cambian por el `UPDATE` | `MigracionPoliticaConDatosTest.shouldKeepVersionAndUpdatedAt` (no hay disparador que toque `fecha_actualizacion`: `@PreUpdate` es de JPA) | Sí (T1) |
| F-6 | Doble envío de la aceptación: el segundo pierde por `@Version` | `accept_shouldReturnStoredAcceptance_whenConcurrentSaveWon`; E2E concurrente | Sí (T5) |
| F-7 | Los Cloud Run Jobs (`purge-job`, `account-events-relay`) arrancan el mismo contexto y también exigen la versión | `PurgeJobProfileTest` en verde (el valor por defecto lo cubre) | Sí (T3) |

## 2. Archivos

### Crear

| Ruta (bajo `src/main/java/tech/cameia/cuentas/`, salvo que diga otra) | Tarjeta |
|---|---|
| `src/main/resources/db/migration/V8__aceptacion_politica_cuenta.sql` | T1 |
| `domain/model/PolicyAcceptance.java`, `domain/model/CurrentPolicy.java` | T2 |
| `domain/exception/PolicyVersionOutdatedException.java` | T2 |
| `infrastructure/config/PolicyProperties.java`, `infrastructure/config/PolicyConfiguration.java` | T3 |
| `presentation/dto/ExplicitConsent.java`, `ExplicitConsentValidator.java`, `CurrentPolicyVersion.java`, `CurrentPolicyVersionValidator.java` | T4 |
| `application/command/AcceptPolicyCommand.java`, `application/service/AcceptPolicyService.java` | T5 |
| `presentation/dto/PolicyAcceptanceRequest.java`, `PolicyAcceptanceResponse.java`, `presentation/controller/PolicyAcceptanceController.java` | T6 |
| `docs/adr/0006-aceptacion-de-la-politica.md` | T8 |
| Pruebas nombradas en cada tarjeta | T1–T7 |

### Modificar

| Ruta | Qué | Tarjeta |
|---|---|---|
| `domain/model/Account.java` | Campo `policyAcceptance`, parámetro en `register` y en los dos `rebuild`, método `acceptPolicy` | T2 |
| `domain/exception/ErrorCode.java` | Tres códigos | T2 |
| `infrastructure/persistence/entity/AccountEntity.java`, `mapper/AccountMapper.java`, `repository/AccountRepositoryAdapter.java` | Columnas, mapeo y copia en `save` | T2 |
| Los 18 archivos de prueba que llaman a `Account.register(` o `Account.rebuild(` (`git grep -l "Account.register(\|Account.rebuild(" -- src/test`) | Agregar el argumento | T2 (mecánico) |
| `src/main/resources/application.properties`, `.env.example` | Propiedad y variable | T3 |
| `presentation/dto/RegisterUserRequest.java`, `application/command/RegisterUserCommand.java`, `application/service/RegisterUserService.java` | Campos nuevos y aceptación al crear | T4 |
| `presentation/advice/BusinessExceptionHandler.java` | Dos entradas en `FIELD_ERROR_CODES` | T4 |
| `presentation/controller/UserRegistrationController.java` | OpenAPI (422 con los códigos nuevos) | T4 |
| `presentation/dto/CurrentAccountResponse.java`, `presentation/controller/CurrentAccountController.java` | `policyAcceptance` | T6 |
| `docs/errores.md`, `CLAUDE.md` (§5), `postman/*.json` | Documentación y colección | T7, T8 |

### No se tocan

`cameia-gateway`, `cameia-web`, el evento `cuenta.creada`, migraciones `V1` a `V7`, `/.github/`.

## 3. Decisiones técnicas y alternativas descartadas

| Decisión | Alternativa descartada | Por qué |
|---|---|---|
| `dataAuthorization` como `Object` con `@ExplicitConsent` (`Boolean.TRUE.equals(value)`) | `Boolean` con `@NotNull` + `@AssertTrue` | `@AssertTrue` acepta `null`, y Jackson convierte `"true"` a `true` antes de validar: no se cumpliría D-3 |
| | `@JsonFormat(lenient = FALSE)` | El error de Jackson corta la validación: no saldrían todos los errores a la vez (REQ-POL-05) |
| `CurrentPolicyVersionValidator` con `CurrentPolicy` inyectado, que lanza la excepción del dominio y la publica con carga dinámica (como `DomainRuleValidator`) | Comprobar la versión en el controlador o en el servicio | Saldría después de los demás errores y no junto a ellos |
| Formato de la versión solo en `CurrentPolicy` (dominio) | `@Pattern` en `PolicyProperties` | Una sola definición de la regla; el error al construir el bean también impide arrancar |
| `PolicyAcceptance` como objeto de valor del agregado `Account` | Dos campos sueltos | Versión y fecha van siempre juntas; `isAccepted()` vive en un solo lugar |
| `acceptPolicy` devuelve `boolean` (cambió o no) | Comparar en el servicio | La regla de idempotencia es del dominio |
| Sin índice nuevo | Índice en `politica_version` | Ninguna consulta filtra por ella (spec §8) |

## 4. Orden y commits (uno por capacidad, cada uno compila)

| Commit | Tarjetas | Mensaje |
|---|---|---|
| 1 | T0 | (sin commit: verificaciones previas, resultado en `ejecucion/evidencia/CM-367/T0.md`) |
| 2 | T1 | `CM-367 \| feat(datos): agregar a la cuenta la versión y la fecha de aceptación de la política` |
| 3 | T2 | `CM-367 \| feat(dominio): guardar la aceptación de la política en la cuenta` |
| 4 | T3 | `CM-367 \| feat(config): exigir al arrancar la versión vigente de la política` |
| 5 | T4 | `CM-367 \| feat(api): exigir la autorización de datos y la versión de la política al registrarse` |
| 6 | T5 + T6 | `CM-367 \| feat(api): consultar y aceptar la versión vigente de la política` |
| 7 | T7 | `CM-367 \| test(postman): agregar los casos de aceptación de la política` |
| 8 | T8 | `CM-367 \| docs(politica): documentar códigos, ADR y esquema de la aceptación` |

Sin paralelo: cada tarjeta usa lo de la anterior. Revisión independiente temprana al terminar T3 (primer tercio).

## 5. Línea base verde

Antes de T1, en el worktree recién creado: `./mvnw.cmd clean verify` sobre `origin/develop` `c94f20c`, con Docker encendido (Testcontainers). Si falla, es un hallazgo con destino y no se empieza.

## 6. Matriz de pruebas por capa

| Capa | Clase de prueba | Casos |
|---|---|---|
| Dominio | `PolicyAcceptanceTest` (nueva) | Válida; versión nula, en blanco, de 20 y de 21 caracteres; fecha nula; `isAccepted` con `sin-aceptar` y con versión |
| | `CurrentPolicyTest` (nueva) | `2026-11-v1` válida; `""`, `"2026-13-v1"`, `"2026-00-v1"`, `"2026-11-v0"`, `"2026-11-v01"`, `"2026-11-v1000"`, `"sin-aceptar"` inválidas; `requireCurrent` igual, distinto, mayúsculas y nulo |
| | `AccountTest` (ampliar) | `register` guarda la aceptación; `acceptPolicy` con la misma versión devuelve `false`; con otra, `true`; deshabilitada lanza `AccountDisabledException`; anonimizada, `AccountNotFoundException` |
| Aplicación | `AcceptPolicyServiceTest` (nueva) | Feliz; idempotente sin `save`; versión vieja; sin cuenta; anonimizada; deshabilitada; conflicto que relee y gana; conflicto que relee y pierde; uid inválido; registro sin versión ni fecha |
| | `RegisterUserServiceTest` (ampliar) | La cuenta creada lleva la versión del comando y `clock.instant()`; el registro repetido no cambia la aceptación |
| Web | `UserRegistrationControllerTest` (ampliar) | Toda la §7 de la spec |
| | `PolicyAcceptanceControllerTest` (nueva) | 200, 400 sin `X-User-Id`, 422 de los tres casos, `Cache-Control: no-store`, cuerpo con `firebaseUid` ignorado |
| | `CurrentAccountControllerTest` (ampliar) | `policyAcceptance` con cuenta aceptada y con `sin-aceptar` (`acceptedAt` nulo) |
| | `ExplicitConsentValidatorTest`, `CurrentPolicyVersionValidatorTest` (nuevas) | Cada rama del validador |
| Persistencia | `CuentaSchemaMigrationTest` (ampliar) | Columnas, tipos, `NOT NULL` y `ck_cuenta_politica_version_no_vacia` |
| | `MigracionPoliticaConDatosTest` (nueva) | Relleno sobre filas, sin cambiar `version` ni `fecha_actualizacion` |
| | `AccountRepositoryAdapterTest`, `AccountMapperTest` (ampliar) | Ida y vuelta; actualización en fila existente |
| | `CuentaConstraintsClassificationTest` (revisar) | Si recorre las restricciones de `cuenta`, clasificar la nueva |
| Configuración | `PolicyPropertiesTest` (nueva) | Arranca con `2026-11-v1`; no arranca con vacío, `2026-13-v1` o `sin-aceptar` |
| Integración (Testcontainers, puerto aleatorio) | `AccountRegistrationEndToEndTest` (ampliar), `AcceptPolicyEndToEndTest` (nueva), `CurrentAccountEndToEndTest` (ampliar) | Columna guardada al registrar; aceptar, repetir, concurrencia, p95 y registros |
| Contrato | `OpenApiPolicyContractTest` (nueva) | Los tres endpoints muestran campos y códigos |
| Arquitectura | `LayeredArchitectureTest`, `UntypedExceptionClassificationTest` | En verde; clasificar los métodos nuevos que lancen sin código |

## 7. Regla → evidencia

| Regla | Evidencia |
|---|---|
| R1 idioma | Revisión del diff: identificadores en inglés y textos en español |
| R2 validación por campo | Spec §7 y sus pruebas |
| R3 errores | Spec §10; `ErrorCodeDocumentationTest`; ninguna ruta previsible al 500 |
| R4 base de datos | T1 y sus pruebas |
| R5 casos borde | Spec §9 |
| R6 seguridad | Spec §12; pruebas de identidad, asignación masiva y registros |
| R7 código lean | Revisión con `/simplify` y `/code-review high` (P6) |
| R8 documentación | T8; OpenAPI; Javadoc en cada clase y método nuevos |
| R9 pruebas por capa | §6 |
| R10 cobertura | JaCoCo ≥ 90 % de líneas y ramas en las clases de §2, con cada hueco justificado |
| R11 Postman | T7; Newman con 0 fallos |
| R12 causa raíz | No aplica a una funcionalidad nueva; se aplica si una prueba falla |
| R13 atributos | Spec §14 |
| R14 prohibiciones | Sin dependencias nuevas, sin `CM-NNN` en comentarios, sin editar migraciones aplicadas |

## 8. Riesgos

| Riesgo | Mitigación |
|---|---|
| Frontend (CM-368) no envía los campos nuevos cuando Cuentas se despliegue: todo registro responde 422 | D-5: el PR de Cuentas se abre en borrador y se fusiona el mismo día que el de CM-368. El aviso a Frontend sale al abrir el PR. Queda escrito en «Hallazgos que aplican a esta tarea» del PR |
| Tope del 15-nov | Si la spec no está aprobada el 13-nov, se recorta en este orden: (1) la prueba de p95 pasa a medirse en preproducción; (2) la clase `OpenApiPolicyContractTest` se reemplaza por la comprobación en Newman; (3) el ADR se reduce a las decisiones D-1 a D-4. Los criterios 3 a 6 no se recortan |
