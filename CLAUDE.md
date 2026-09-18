# cameia-cuentas

## Contexto del proyecto

CAMEIA ofrece práctica y simulación de entrevistas virtuales para preparar entrevistas de trabajo. Aunque puede incluir preguntas técnicas, el foco principal son las preguntas de comportamiento.

Este repositorio contiene el microservicio de cuentas dentro de una arquitectura de microservicios. Su responsabilidad es mantener la cuenta local, planes, suscripciones, pagos y entitlements. No implementa la lógica de entrevistas ni registra el consumo detallado de IA.

## Responsabilidad del servicio

- Mantener el estado local asociado al `firebase_uid`.
- Gestionar planes, suscripciones, transacciones y su idempotencia.
- Integrarse con Wompi para pagos, una vez aprobado el contrato de integración.
- Integrarse con Firebase Admin SDK para consultar usuarios, actualizar sus datos y claims/permisos, y bloquear o deshabilitar cuentas cuando corresponda, por ejemplo ante impago.
- Publicar cambios mediante contratos de eventos aprobados.
- Mantener persistencia propia, sin claves foráneas hacia otros microservicios.

Las contraseñas y credenciales de autenticación permanecen en Firebase. Nunca se almacenan en este servicio.

## Estructura de carpetas (Estilo DDD)

Cuentas sigue el patrón de capas con Domain-Driven Design. Las carpetas están predefinidas para garantizar cohesión alta y acoplamiento bajo. Crear subcarpetas solo si existe evidencia de un motivo de cambio distinto.

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
│   ├── model               // agregados, entidades, objetos de valor, enums
│   ├── service             // servicios de dominio
│   ├── policy              // políticas de dominio
│   ├── port                // interfaces que el dominio define y NO implementa
│   ├── event               // eventos de dominio
│   └── exception           // excepciones de negocio
└── infrastructure
    ├── persistence
    │   ├── entity          // modelo JPA — NO es el modelo de dominio
    │   ├── repository      // Spring Data + adaptadores de los puertos
    │   └── mapper          // dominio <-> entity
    ├── messaging
    │   ├── consumer        // @RabbitListener
    │   ├── publisher       // RabbitTemplate
    │   └── payload         // contratos de mensaje versionados
    ├── client              // WebClient hacia sistemas externos
    ├── ia                  // adaptadores de LLM (solo si se requiere)
    └── config              // configuración de Spring
```

**Regla de dependencias:** `domain` no importa nada de `presentation`, `application` ni `infrastructure`. `application` depende de `domain` a través de puertos. `infrastructure` implementa los puertos que `domain` define. Esta regla se valida automáticamente con la prueba ArchUnit `LayeredArchitectureTest`; toda nueva clase debe pasarla.

## Límites de entrada y confianza

El servicio solo debe aceptar peticiones autenticadas del API Gateway. La estrategia tiene dos capas (decidida el 2026-09-04):

### Capa 1: IAM + OIDC en Cloud Run (base obligatoria)
- El microservicio se despliega con `--no-allow-unauthenticated`.
- Solo la cuenta de servicio del API Gateway tiene rol de invocador (binding de IAM).
- El Gateway obtiene un token OIDC del metadata server de Cloud Run y lo agrega a cada petición saliente en el header `Authorization: Bearer <token>`.
- Cloud Run valida el token antes de enrutar la petición al microservicio.
- No implica cambios en la lógica de negocio: es configuración de infraestructura.

### Capa 2: VPC e ingress interno (solo para contextos sensibles como Cuentas)
- Se agrega encima de la Capa 1 para microservicios donde se procesan pagos o permisos críticos.
- El microservicio queda con `ingress: internal`, sin ruta pública desde internet.
- El Gateway accede al microservicio por un conector privado dentro de la VPC.
- El Gateway sigue siendo público, como punto de entrada único para React.

**Contrato de entrada:**

Cuentas recibe del Gateway únicamente los datos necesarios para la autorización de negocio, nunca el JWT completo:

- **Obligatorios:** `firebase_uid`, `email`, `roles`, `request_id`.
- **Opcionales:** `display_name`, `correlation_id`, `issued_at`.

No agregar campos derivados del JWT sin justificar su necesidad y documentar su contrato. No confiar en headers enviados directamente por clientes externos.

## Reglas de seguridad

- No registrar JWT, secretos, contraseñas, tokens de Firebase ni información sensible de pago.
- Guardar secretos de Firebase y Wompi únicamente en el gestor de secretos o variables de entorno aprobadas; nunca en Git.
- La autenticidad de peticiones del Gateway se garantiza mediante IAM y tokens OIDC (Capa 1). No es necesario firmar el payload en la aplicación.
- Verificar firma SHA-256 e idempotencia de webhooks de Wompi antes de cambiar una suscripción o entitlement.
- Wompi no se conecta directo a Cuentas: su webhook apunta al API Gateway (`POST /webhooks/wompi`), que lo reenvía íntegro por red privada con token OIDC. Wompi autentica con su checksum SHA-256, no con un JWT de Firebase. Así se mantiene un único punto de ingreso externo (decidido el 2026-09-04).
- Aplicar autorización por endpoint provado y no asumir que `roles` equivale automáticamente a permisos de negocio.
- Exponer solo los endpoints de Actuator necesarios para salud.
- Cada integración externa (Firebase, Wompi, etc.) requiere pruebas de autenticidad, reintentos, manejo de errores e idempotencia antes de considerarla completa.

## Constitución

Los principios no negociables del proyecto (stack, calidad, tests y límites) están consolidados en [docs/constitution.md](docs/constitution.md). Toda spec y todo PR los cumple; en caso de conflicto, esa lista prevalece sobre el resto de este documento.

## Metodología Spec-Driven Development

El repositorio sigue Spec-Driven Development clásico. Las especificaciones viven bajo `specs/`, organizadas por funcionalidad según la estructura descrita más abajo. Antes de implementar una capacidad nueva:

1. Crear o actualizar la spec con contexto, alcance, requisitos, reglas, casos de éxito y casos de error.
2. Registrar las decisiones y contratos relevantes en la spec; los prompts que las motivaron van a la bitácora de IA.
3. Implementar únicamente lo respaldado por una spec aprobada.
4. Añadir pruebas que demuestren los escenarios de la spec.
5. Actualizar la documentación si cambian contratos, configuración, datos, eventos o comandos.

No crear carpetas o especificaciones ficticias para aparentar que una decisión está tomada.

Estructura y organizacion:
- Los specs viven en una carpeta que esta en la raiz llamada specs
- dentro de la carpeta specs viven specs especificos que siguen el nombrado CM-NNN-Descripcion (por ejemplo `CM-14-RegistroUsuario`)
- cada spec especifico tiene tres artefactos [spec.md], [plan.md] y [tasks.md]
    - spec.md representa el alcance del spec con el contexto y a veces diagramas que hagan entender mejor el spec. Contiene  requisitos funcionales a abordar, CADA REQUISITO DEBE SEGUIR LA NOTACION EARS, Requisitos no funcionales y lo que queda fuera del alcance
    - plan.md contiene el plan tecnico de como se va a abordar el spec
    - tasks.md contiene las tareas divididas por bloques en donde cada tarea se puede hacer en 30 minutos o menos

## Restricción de ambigüedades

Si una petición contiene una ambigüedad que puede afectar seguridad, contrato, datos, pagos, permisos, arquitectura o comportamiento observable, el agente debe detenerse antes de editar. Debe formular preguntas concretas y resolverlas ahí mismo con máximo 6 preguntas.

La trazabilidad de esas decisiones no vive en un registro aparte: la decisión técnica queda en la spec afectada y el prompt con la decisión humana queda en la bitácora de IA (ver "Bitácora de IA por spec").

No asumir defaults silenciosos en decisiones críticas. Una tarea puede continuar solo si las partes ambiguas son irrelevantes para el cambio o si ya existe una decisión documentada y aprobada.

### Preguntas abiertas

- **Contrato de respuesta y versionado de API (pendiente desde 2026-09-04).** ¿Qué estrategia de versionado (path, header o query parameter), nombres JSON y formato de error se adopta para los endpoints que consume el Gateway? Base propuesta: versionado nativo de Spring Boot, JSON en `camelCase` y Problem Details (RFC 7807). Requiere aprobación de arquitectura y la resuelve la spec del primer endpoint de negocio de Cuentas.

## Límite de tamaño de cambios

Se prohíben cambios cuyo diff total agregado y eliminado supere 1000 líneas por solicitud. Antes de editar, estimar el tamaño. Si se supera el umbral:

- Detener la implementación.
- Informar al usuario que debe revisar cada cambio.
- Recomendar dividir la petición en incrementos pequeños, por responsabilidad o por spec.
- Proponer un orden de modularización y esperar confirmación.

No usar este límite para ocultar cambios relacionados en commits separados: cada incremento debe ser revisable y funcional.

## Reglas de nombrado

- Clases e interfaces: `PascalCase`.
- Métodos, campos y variables: `camelCase`.
- Constantes: `UPPER_SNAKE_CASE`.
- Paquetes: `lowercase`, sin guiones bajos, sin plurales inventados.
- **`CONFIRMADO`** Sin abreviaturas: `configuracion`, no `config`; `sesion`, no `ses`; `perfilProfesional`, no `pp`. Los identificadores cortos de los diagramas (`ctrl_ses`, `app_eval`) son etiquetas del dibujo, no nombres de clase.

## Convenciones de idioma

**Regla fundamental:** El compilador lee código en inglés; las personas leen documentación en español.

| Elemento | Idioma | Ejemplo |
|---|---|---|
| Paquetes, clases, métodos, variables | **Inglés** | `Account`, `createSubscription()`, `firebaseUid` |
| Constantes | **Inglés** `UPPER_SNAKE` | `MAX_RETRY_ATTEMPTS`, `WEBHOOK_TIMEOUT_MS` |
| Nombres de tablas/columnas | **Español** `snake_case` | `cuenta`, `fecha_creacion`, `id_firebase` |
| **Comentarios de código (Javadoc)** | **Español** | Ver sección "Documentación de código" |
| **Descripciones OpenAPI** | **Español** | Ver sección "Documentación de código" |
| Mensajes de log | **Español**, sin datos sensibles | `logger.info("Suscripción creada para usuario")` |
| Excepciones (mensaje) | **Español** | `throw new SubscriptionNotFoundException("Suscripción no encontrada")` |
| Commits y PRs | **Español** | `git commit -m "CM-105: Implementar renovación automática"` |

**Justificación:** El código convive con compiladores, intérpretes y dependencias internacionales; el inglés es el estándar. La documentación la lee el equipo en un contexto donde el español es natural.

## Documentación de código

### Javadoc en español

Todo método público en `domain`, `application` y los adaptadores de `infrastructure` lleva Javadoc en español con descripción clara de qué hace, parámetros, retorno y excepciones.

### OpenAPI en español

Cada endpoint expone su contrato mediante OpenAPI 3.0.

**Acceso a documentación:**
- JSON OpenAPI: `http://localhost:8081/v3/api-docs`
- Swagger UI: `http://localhost:8081/swagger-ui.html`

Ambos recursos dependen de `API_DOCUMENTATION_ENABLED`, apagada por defecto. En desarrollo se enciende con la variable (viene en `.env.example`); el perfil `prod` la fija en `false` y no admite que una variable de entorno la encienda (ver [specs/CM-103-DocumentacionApi/spec.md](specs/CM-103-DocumentacionApi/spec.md)).

## Convenciones técnicas

**Stack base:**
- Java 21, Spring Boot 4.1.1 y Maven Wrapper.
- Sigue las indicaciones de [guidelines.md](guidelines.md).
- Usa Javadoc en español; código y método/clase en inglés (ver sección "Convenciones de idioma").
- Usa JUnit 5 para pruebas unitarias.

## Verificación de cambios de codigo

1. Pruebas Unitarias
```powershell
./mvnw.cmd test
```
2. Limpieza y construccion del projecto
```powershell
./mvnw.cmd clean package
```

## Flujo de contribución

- Usar ramas `CM-<numero>-<descripcion-kebab-case>`.
- `NNN` y `<numero>` son el número de la clave Jira sin ceros a la izquierda: `CM-14`, no `CM-014`.
- Todo commit incluye la clave Jira; no hay commits sin `CM-NNN`.
- Todo cambio ordinario entra mediante PR y revisión de una persona distinta del autor.
- Mantener `main` estable y promover cambios desde `develop` mediante Merge commit.
- Integrar ramas de trabajo en `develop` mediante Squash.
- Actualizar la spec y este documento en el mismo PR cuando cambien reglas o contratos.

## Bitácora de IA por spec — OBLIGATORIA

Se llena el mismo día del trabajo, en `..\..\Entregables\<ddMMyyyy>_BitacoraIA_Codigo_E2.md` (relativo a la raíz del repositorio), hoja **`Bitacora_Codigo_Vela`**. El archivo del día es compartido con los demás repositorios: cameia-cuentas agrega su propia sección sin tocar las de otros.

Incluye los siguientes ítems:
- Prompts más importantes en la toma de decisiones con criterio humano: prompt, qué propuso la IA y la decisión humana con su porqué.
- Resumen de lo que se hizo.

## Título de PR — formato obligatorio

```text
CM-NNN | tipo(scope): resultado [IA-ASISTIDO]
```

El `[IA-ASISTIDO]` va **siempre al final**, nunca al inicio.

## Título y plantilla de commit

```text
CM-NNN | tipo(scope): resultado [IA-ASISTIDO]

Descripción de un párrafo.

Comentario del modelo usado.
```