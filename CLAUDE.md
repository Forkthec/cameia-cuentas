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

El servicio solo debe aceptar peticiones autenticadas del API Gateway. La estrategia tiene dos capas, ambas documentadas en [docs/AMBIGUIDADES.md](docs/AMBIGUIDADES.md):

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
- Verificar firma SHA-256 e idempotencia de webhooks de Wompi antes de cambiar una suscripción o entitlement. Wompi se conecta directo a Cuentas.
- Aplicar autorización por endpoint provado y no asumir que `roles` equivale automáticamente a permisos de negocio.
- Exponer solo los endpoints de Actuator necesarios para salud.
- Cada integración externa (Firebase, Wompi, etc.) requiere pruebas de autenticidad, reintentos, manejo de errores e idempotencia antes de considerarla completa.

## Constitución

Los principios no negociables del proyecto (stack, calidad, tests y límites) están consolidados en [docs/constitution.md](docs/constitution.md). Toda spec y todo PR los cumple; en caso de conflicto, esa lista prevalece sobre el resto de este documento.

## Metodología Spec-Driven Development

El repositorio sigue Spec-Driven Development clásico. Las especificaciones vivirán bajo `docs/specs/`, organizadas por funcionalidad. Antes de implementar una capacidad nueva:

1. Crear o actualizar la spec con contexto, alcance, requisitos, reglas, casos de éxito y casos de error.
2. Registrar las decisiones y contratos relevantes.
3. Implementar únicamente lo respaldado por una spec aprobada.
4. Añadir pruebas que demuestren los escenarios de la spec.
5. Actualizar la documentación si cambian contratos, configuración, datos, eventos o comandos.

No crear carpetas o especificaciones ficticias para aparentar que una decisión está tomada.

## Restricción de ambigüedades

Si una petición contiene una ambigüedad que puede afectar seguridad, contrato, datos, pagos, permisos, arquitectura o comportamiento observable, el agente debe detenerse antes de editar. Debe formular preguntas concretas y resolverlas ahí mismo con máximo 6 preguntas.

Toda ambigüedad cuya resolución tenga impacto en la arquitectura, sea de alto impacto en seguridad, o comprometa una buena práctica (por ejemplo, omitir pruebas unitarias) debe quedar registrada en [docs/AMBIGUIDADES.md](docs/AMBIGUIDADES.md) con la pregunta, la decisión adoptada, el impacto y la especificación relacionada. Ambigüedades menores, sin ese impacto, pueden resolverse en la conversación sin dejar constancia formal allí.

No asumir defaults silenciosos en decisiones críticas. Una tarea puede continuar solo si las partes ambiguas son irrelevantes para el cambio o si ya existe una decisión documentada y aprobada.

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

Ambos recursos dependen de `API_DOCUMENTATION_ENABLED`, apagada por defecto. En desarrollo se enciende con la variable (viene en `.env.example`); el perfil `prod` la fija en `false` y no admite que una variable de entorno la encienda (ver A-005 y [docs/specs/documentacion-api.md](docs/specs/documentacion-api.md)).

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
- Todo cambio ordinario entra mediante PR y revisión de una persona distinta del autor.
- Mantener `main` estable y promover cambios desde `develop` mediante Merge commit.
- Integrar ramas de trabajo en `develop` mediante Squash.
- Actualizar la spec y este documento en el mismo PR cuando cambien reglas o contratos.