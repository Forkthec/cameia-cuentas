# Ambigüedades y decisiones pendientes

Este documento es el registro de decisiones que pueden bloquear diseño o implementación. Una entrada deja de bloquear cuando tiene una respuesta explícita, responsable, fecha y evidencia del cambio aplicado.

## Cómo usarlo

- El agente debe revisar este archivo antes de editar.
- Las ambigüedades críticas bloquean cambios en la frontera o el dominio afectado.
- Cada resolución debe actualizar la pregunta, la decisión, el impacto y la spec relacionada.
- No borrar decisiones históricas; marcar su estado como `Resuelta` o `Reemplazada`.

## Registro

### A-001: Autenticación entre API Gateway y Cuentas

- Estado: `Resuelta`
- Responsable: Juan David Vela Coronado
- Fecha: 2026-09-04
- Tema: seguridad y entrada de peticiones
- Decisión adoptada: **Dos capas de autenticación**

  **Capa 1 (IAM + OIDC en Cloud Run)** — Base obligatoria para todos los microservicios:
  - El microservicio se despliega con `--no-allow-unauthenticated`.
  - Solo la cuenta de servicio del API Gateway tiene rol de invocador (binding de IAM).
  - El Gateway obtiene un token OIDC del metadata server de Cloud Run (sin llaves en el código) y lo agrega a cada petición en `Authorization: Bearer <token>`.
  - Cloud Run valida el token automáticamente antes de enrutar al microservicio.
  - No requiere cambios en la lógica de negocio; es configuración de infraestructura.

  **Capa 2 (VPC e ingress interno)** — Solo para microservicios críticos (Cuentas, pagos, permisos):
  - Se agrega encima de la Capa 1.
  - El microservicio se despliega con `ingress: internal`, sin ruta pública desde internet.
  - El Gateway accede por un conector privado dentro de la VPC.
  - El Gateway sigue siendo público (acceso desde React).

- Impacto en código: mínimo. La validación de origen la hace Cloud Run; Cuentas solo recibe peticiones ya autenticadas.
- Especificación afectada: ninguna todavía; no hay endpoints implementados. Cuando se cree el primer endpoint que dependa de esta frontera, su spec en `docs/specs/` debe referenciar esta decisión (A-001).

### A-002: Integración de pagos con Wompi

- Estado: `Resuelta`
- Responsable: Juan David Vela Coronado
- Fecha: 2026-09-04
- Tema: pagos y suscripciones
- Decisión adoptada: **Wompi → API Gateway → Cuentas (vía red privada)**
  
  - La URL de webhook de Wompi apunta al API Gateway, no directo a Cuentas.
  - Wompi envía un checksum SHA-256 en el header (su propio mecanismo de autenticidad, no Firebase JWT).
  - El Gateway valida la firma SHA-256 en la ruta pública, o la reenvía íntegra al microservicio por red privada.
  - Cuentas verifica firma e idempotencia de la petición antes de cambiar suscripciones o entitlements.
  - Esto mantiene un único punto de ingreso externo (el Gateway) y evita abrir otro puerto públicamente.

- Impacto en código:
  - `infrastructure.client.WompiClient`: valida webhook y reintenta en caso de error.
  - `infrastructure.messaging.consumer.PaymentListener`: procesa eventos de Wompi y publica cambios internos.
  - `application.service.SuscripcionAppService`: orquesta cambios de estado.
  - Pruebas: verificar firma SHA-256, idempotencia, reintentos y transiciones de estado.

- Especificación afectada: crear `docs/specs/integracion-wompi.md` cuando el contrato de Wompi sea firmado.

### A-003: Contrato de respuesta y versionado de API

- Estado: `Pendiente` (menos urgente que A-001 y A-002)
- Tema: API pública interna
- Pregunta: ¿Qué estrategia de versionado, nombres JSON y formato de error se adoptará para los endpoints del Gateway?
- Base propuesta: estrategia nativa de versionado de Spring Boot (path, header o query parameter), JSON en `camelCase` y Problem Details (RFC 7807).
- Decisión: Requiere aprobación de arquitectura y debe constar en la spec de la funcionalidad afectada.
- Especificación: la documentará la primera funcionalidad que cree un endpoint de Cuentas.

## Decisiones confirmadas

- Idioma operativo de este documento: español.
- Payload mínimo del gateway: `firebase_uid`, `email`, `roles` y `request_id` obligatorios; `display_name`, `correlation_id` e `issued_at` opcionales.
- Las credenciales y contraseñas no se custodian en Cuentas.
- La documentación Spec-Driven se organizará bajo `docs/specs/`.
- El límite de 1000 líneas se mide sobre el diff total agregado y eliminado por solicitud.
- **Seguridad resuelta (A-001):** IAM + OIDC de Cloud Run como base; VPC + ingress internal para Cuentas como capa adicional.
- **Wompi resuelta (A-002):** webhook apunta al Gateway; validación de firma SHA-256; reenvío vía red privada al microservicio.
- **Arquitectura confirmada:** Estilo DDD con `domain`, `application`, `infrastructure`, `presentation`. Las reglas de dependencia se verifican con la prueba ArchUnit `LayeredArchitectureTest` (`tech.cameia.cuentas.architecture`).
- **Paquete base:** `tech.cameia.cuentas` (groupId de Maven: `tech.cameia`).