# cameia-cuentas

## 1. Servicio

CAMEIA ofrece práctica y simulación de entrevistas virtuales para preparar entrevistas de trabajo, con foco en las preguntas de comportamiento. Este repositorio es el microservicio de Cuentas, dueño de la cuenta local, los planes, las suscripciones, los pagos y los entitlements de cada usuario, identificado por `firebaseUid`.

- Mantiene el estado local de la cuenta asociado al `firebaseUid`.
- Gestiona planes, suscripciones, transacciones y su idempotencia.
- Se integra con Wompi para pagos, una vez aprobado el contrato de integración.
- Se integra con Firebase Admin SDK para consultar usuarios, actualizar sus datos y permisos, y bloquear cuentas cuando corresponda.
- Publica cambios mediante contratos de eventos aprobados.
- Mantiene persistencia propia, sin claves foráneas hacia otros microservicios.

Las contraseñas y credenciales de autenticación permanecen en Firebase y nunca se almacenan aquí. El servicio no implementa la lógica de entrevistas ni registra el consumo de IA.

Las reglas comunes de Backend están en [docs/estandar-backend.md](docs/estandar-backend.md) y los principios no negociables, en [docs/constitution.md](docs/constitution.md).

## 2. Estructura y dependencias

Pila: Java 21, Spring Boot 4.1.1, Maven Wrapper, PostgreSQL 16 y Flyway.

Capas con Domain-Driven Design bajo `tech.cameia.cuentas`. Se crean subcarpetas solo con un motivo de cambio distinto.

```text
tech.cameia.cuentas
├── presentation
│   ├── controller          // adaptadores de entrada HTTP
│   ├── dto                 // request/response del contrato público
│   └── advice              // manejo de errores HTTP
├── application
│   ├── service             // casos de uso (orquestación, transacción)
│   └── command             // objetos de entrada de los casos de uso
├── domain
│   ├── model               // agregados, entidades, objetos de valor, enumerados
│   ├── service             // servicios de dominio
│   ├── policy              // políticas de dominio
│   ├── port                // interfaces que el dominio define y no implementa
│   ├── event               // eventos de dominio
│   └── exception           // excepciones de negocio
└── infrastructure
    ├── persistence
    │   ├── entity          // modelo JPA, distinto del modelo de dominio
    │   ├── repository      // Spring Data y adaptadores de los puertos
    │   └── mapper          // dominio <-> entidad
    ├── messaging
    │   ├── consumer        // @RabbitListener
    │   ├── publisher       // RabbitTemplate
    │   └── payload         // contratos de mensaje versionados
    ├── client              // clientes hacia sistemas externos
    ├── ia                  // adaptadores de LLM (solo si se requieren)
    └── config              // configuración de Spring
```

**Regla de dependencias.** `domain` no importa nada de `presentation`, `application` ni `infrastructure`; `application` depende de `domain` solo por puertos; `infrastructure` implementa los puertos de `domain`. La vigila `tech.cameia.cuentas.architecture.LayeredArchitectureTest`, y toda clase nueva debe pasarla.

## 3. Límites de confianza

El servicio solo debe aceptar peticiones autenticadas del API Gateway, con dos capas:

1. **IAM con OIDC en Cloud Run (base obligatoria).** El servicio se despliega con `--no-allow-unauthenticated`; solo la cuenta de servicio del Gateway tiene rol de invocador. El Gateway agrega a cada petición un token OIDC en `Authorization: Bearer`, y Cloud Run lo valida antes de enrutar. No cambia la lógica de negocio.
2. **VPC con ingreso interno (contextos sensibles, como Cuentas).** Encima de la capa 1: ingreso `internal`, sin ruta pública, y el Gateway llega por un conector privado de la VPC. El Gateway sigue siendo el único punto de entrada público.

**Identidad de entrada.** Cuentas recibe del Gateway solo lo necesario para la autorización de negocio, nunca el JWT completo, en estos encabezados:

| Encabezado | Obligatorio | Contenido |
|---|---|---|
| `X-User-Id` | Sí | `firebaseUid` del usuario |
| `X-User-Email` | Sí | Correo del usuario |
| `X-User-Roles` | Sí | Roles del usuario |
| `X-Request-Id` | Sí | Identificador de la petición |
| `X-User-Plan` | No | Plan del usuario; no respalda ningún derecho ni cuota hasta que exista la spec de planes |
| `X-User-Email-Verified` | No | Si el correo está verificado |

No se confía en un encabezado enviado directamente por un cliente externo, y no se agregan campos derivados del JWT sin justificar su necesidad y documentar el contrato.

**Rutas sin identidad.** Solo dos, cada una por una razón concreta:

- `GET /api/v1/users/health`: no lee ni modifica datos de la cuenta.
- `POST /api/v1/users` (registro): quien llama todavía no tiene cuenta. El Gateway la trata como ruta pública y borra los `X-User-*` y el `Authorization` que envíe el cliente.

Una ruta nueva no se suma a esta lista sin una spec que lo justifique.

## 4. Contrato y errores

- La versión va en la ruta: `/api/v1/...`.
- Los nombres JSON van en `camelCase`.
- Los errores son `ProblemDetail` (RFC 9457) con `Content-Type: application/problem+json`; los de validación agregan una lista `errors` con un elemento por campo rechazado. El formato y las reglas están en la [sección 6 del estándar](docs/estandar-backend.md#6-errores) y lo que el servicio emite hoy, en [docs/errores.md](docs/errores.md).
- Los mensajes de las excepciones de negocio llegan al usuario tal cual, porque le dicen qué corregir. Un fallo técnico se registra completo en el log y al cliente solo le llega un texto genérico.

**Documentación de la API.** OpenAPI en `http://localhost:8081/v3/api-docs` y Swagger UI en `http://localhost:8081/swagger-ui.html`. Ambos dependen de `API_DOCUMENTATION_ENABLED`, apagada por defecto; en desarrollo se enciende con la variable (viene en `.env.example`). El perfil `prod` la fija en `false` y no admite que una variable de entorno la encienda. Ver [specs/CM-103-DocumentacionApi/spec.md](specs/CM-103-DocumentacionApi/spec.md).

## 5. Datos

Base PostgreSQL propia, esquema `microcuentas`, migraciones Flyway en `src/main/resources/db/migration` (`V1__esquema_inicial_cuenta.sql`, `V2__ajustar_version_inicial_cuenta.sql`, `V3__restringir_pronombres.sql` y `V4__relajar_minimo_telefono_e164.sql`).

Flyway corre al arrancar la aplicación y anota cada migración aplicada en `microcuentas.flyway_schema_history`: reiniciar o
recrear el contenedor no vuelve a aplicar nada ni toca las filas, y una migración nueva se aplica una sola vez, dentro de una
transacción. Si falla, se deshace entera y la aplicación no arranca (en Cloud Run la revisión nueva no recibe tráfico y sigue la
anterior). Por eso una migración que restringe datos se prueba antes sobre una base con filas (`MigracionPronombresConDatosTest`,
`MigracionTelefonoConDatosTest`) y, antes de desplegarla, se consulta qué valores hay en el entorno. Una migración aplicada no se
edita: su suma de verificación cambiaría y la aplicación dejaría de arrancar en toda base que la tenga.

Tabla `microcuentas.cuenta`:

| Columna | Tipo | Regla |
|---|---|---|
| `id` | `uuid` | Clave primaria |
| `firebase_uid` | `varchar(128)` | No nulo; único (`uq_cuenta_firebase_uid`) |
| `nombre` | `varchar(120)` | No nulo y no vacío |
| `apellido` | `varchar(120)` | No nulo y no vacío |
| `fecha_nacimiento` | `date` | Opcional |
| `telefono` | `varchar(16)` | Opcional; formato E.164 de 6 a 15 dígitos (`ck_cuenta_telefono_e164`) |
| `pronombres` | `varchar(60)` | `HE`, `SHE` o `THEY` (`ck_cuenta_pronombres_valor`); nulo solo tras la anonimización |
| `estado` | `varchar(24)` | `PENDING_VERIFICATION` (por defecto), `ACTIVE`, `DISABLED` o `ANONYMIZED` |
| `version` | `bigint` | Control de concurrencia; ≥ 0 |
| `fecha_creacion`, `fecha_actualizacion` | `timestamptz` | No nulas, en UTC |
| `fecha_eliminacion` | `timestamptz` | Solo existe cuando el estado es `ANONYMIZED` |

La tabla no guarda correo ni contraseña: son de Firebase. Las restricciones llevan nombre con los prefijos del [estándar](docs/estandar-backend.md#7-base-de-datos); las tablas y columnas van en `snake_case` español y en singular.

## 6. Seguridad

- No registrar JWT, secretos, contraseñas, tokens de Firebase ni información de pago.
- Los secretos de Firebase y de Wompi viven solo en el gestor de secretos o en variables de entorno aprobadas, nunca en Git.
- **Emulador de Firebase Auth.** `FIREBASE_AUTH_EMULATOR_HOST` es solo para desarrollo local: con ella el Admin SDK acepta tokens sin firma. La aplicación no arranca si esa variable existe, aunque esté vacía, junto a `K_SERVICE` o al perfil `prod`. No se define en workflows ni en despliegues. Ver [specs/CM-191-EmuladorFirebaseAuth/spec.md](specs/CM-191-EmuladorFirebaseAuth/spec.md).
- La autenticidad de las peticiones del Gateway la garantizan IAM y el token OIDC (capa 1); no se firma el payload en la aplicación.
- **Wompi.** Se verifican la firma SHA-256 y la idempotencia de cada webhook antes de cambiar una suscripción o un entitlement. El webhook no llega directo a Cuentas: apunta al Gateway (`POST /webhooks/wompi`), que lo reenvía íntegro por red privada con token OIDC. Wompi se autentica con su checksum, no con un JWT de Firebase.
- La autorización se aplica por endpoint; un rol no equivale a un permiso de negocio.
- El único endpoint de operación expuesto es el de salud propio, `GET /api/v1/users/health`; el servicio no usa Actuator.
- Cada integración externa requiere pruebas de autenticidad, reintentos, manejo de errores e idempotencia antes de darla por completa.

## 7. Pruebas

- JUnit 5, con las pruebas de cada capa bajo `src/test/java/tech/cameia/cuentas` (`application`, `domain`, `infrastructure`, `presentation`) y una prueba de extremo a extremo del registro.
- Las pruebas de persistencia y de integración usan PostgreSQL real en Testcontainers (`postgres:16-alpine`) y las de integración, puerto aleatorio.
- `LayeredArchitectureTest` vigila las capas y `UntypedExceptionClassificationTest`, que toda clase que lance `IllegalStateException` o `IllegalArgumentException` (las que terminan en `500 INTERNAL_ERROR`) esté clasificada: un fallo previsible lleva su excepción de negocio y su código.
- Las clases `*Test` las ejecuta Surefire. Las reglas de pruebas y de cobertura están en la [sección 9 del estándar](docs/estandar-backend.md#9-pruebas-y-cobertura).

## 8. Verificación

Verificación completa: `./mvnw.cmd clean verify`. Genera el informe de cobertura en `target/site/jacoco/index.html`.

- Pruebas solas: `./mvnw.cmd test`.
- Con Docker: `docker compose up --build -d` levanta Cuentas y PostgreSQL 16.
- Con la aplicación en `http://localhost:8081`: salud en `/api/v1/users/health`, OpenAPI en `/v3/api-docs` y Swagger UI en `/swagger-ui.html` (con `API_DOCUMENTATION_ENABLED=true`).

## 9. Contribución

Rama, commit, tipos, título de PR, revisión y merge: rige [CONTRIBUTING.md](CONTRIBUTING.md). Lo que este repositorio añade:

- El título del PR lleva `[IA-ASISTIDO]` al final cuando hubo IA; el commit no lo lleva. Un commit asistido por IA lleva el trailer `Co-Authored-By` con el modelo.
- Una IA puede abrir un PR; nunca lo fusiona. El control humano lo marca la persona que revisa.
- Un PR cubre una pieza reconocible y no pasa de 1000 líneas entre agregadas y eliminadas.
- Antes del código hay una spec aprobada en `specs/CM-NNN-Descripcion/` (`spec.md`, `plan.md` y `tasks.md`); el flujo está en la sección 12 de [docs/estandar-backend.md](docs/estandar-backend.md).
- Las decisiones con peso humano se registran en [docs/bitacora-ia/](docs/bitacora-ia/README.md).

Las specs nuevas viven en `specs/CM-NNN-Descripcion/` con `Descripcion` en PascalCase; las carpetas de spec existentes con otro nombre no se renombran.

## 10. Pendientes

| Pendiente | Responsable | Qué bloquea |
|---|---|---|
| Contrato de integración con Wompi | Product Owner | La integración de pagos y los webhooks |
