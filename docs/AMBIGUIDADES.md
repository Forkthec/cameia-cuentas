# Ambigüedades y decisiones pendientes

Este documento es el registro de decisiones que pueden bloquear diseño o implementación. Una entrada deja de bloquear cuando tiene una respuesta explícita, responsable, fecha y evidencia del cambio aplicado.

## Cómo usarlo

- El agente debe revisar este archivo antes de editar.
- Las ambigüedades críticas bloquean cambios en la frontera o el dominio afectado.
- Cada resolución debe actualizar la pregunta, la decisión, el impacto y la spec relacionada.
- No borrar decisiones históricas; marcar su estado como `Resuelta` o `Reemplazada`.

## Registro

### A-001: Autenticación entre API Gateway y Cuentas

- Estado: `Bloqueante`
- Tema: seguridad y entrada de peticiones
- Pregunta: ¿Qué estándar se usará para demostrar que la petición proviene del API Gateway?
- Pendiente de definir: algoritmo, headers, canon de método/ruta/cuerpo, secreto o certificados, ventana temporal, tolerancia de replay y respuesta ante firma inválida.
- Restricción actual: no implementar el filtro de entrada ni considerar confiables headers del cliente hasta resolverlo.

### A-002: Integración de pagos con Wompi

- Estado: `Bloqueante`
- Tema: pagos y suscripciones
- Pregunta: ¿Wompi notificará directamente mediante webhook, el gateway intermediará, o existirán ambos flujos?
- Pendiente de definir: eventos aceptados, verificación de firma, estados válidos, reintentos, idempotency key, conciliación y transición de entitlements.
- Restricción actual: no cambiar suscripciones o permisos basándose en eventos no respaldados por este contrato.

### A-003: Contrato de respuesta y versionado

- Estado: `Pendiente`
- Tema: API pública interna
- Pregunta: ¿Qué estrategia de versionado, nombres JSON y formato de error se adoptará para los endpoints del gateway?
- Base propuesta: estrategia nativa de versionado de Spring Boot, JSON en `camelCase` y Problem Details, pendiente de aprobación.

## Decisiones confirmadas

- Idioma operativo de este documento: español.
- Payload mínimo del gateway: `firebase_uid`, `email`, `roles` y `request_id` obligatorios; `display_name`, `correlation_id` e `issued_at` opcionales.
- Las credenciales y contraseñas no se custodian en Cuentas.
- La documentación Spec-Driven se organizará bajo `docs/specs/`.
- El límite de 1000 líneas se mide sobre el diff total agregado y eliminado por solicitud.