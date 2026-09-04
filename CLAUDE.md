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

## Límites de entrada y confianza

El servicio solo debe aceptar peticiones del API Gateway. Las peticiones directas deben rechazarse. La autenticación entre gateway y servicio requiere un mecanismo de firma; el estándar, los headers y el canon del mensaje están pendientes y bloquean la implementación de esta frontera. Consultar [docs/AMBIGUIDADES.md](docs/AMBIGUIDADES.md).

El gateway no debe reenviar el JWT completo como contrato de negocio. Cuentas recibe únicamente los datos necesarios:

- Obligatorios: `firebase_uid`, `email`, `roles`, `request_id`.
- Opcionales: `display_name`, `correlation_id`, `issued_at`.

No agregar campos derivados del JWT sin justificar su necesidad y documentar su contrato. No confiar en headers enviados directamente por clientes externos.

## Reglas de seguridad

- No registrar JWT, secretos, contraseñas, tokens de Firebase ni información sensible de pago.
- Guardar secretos de Firebase y Wompi únicamente en el gestor de secretos o variables de entorno aprobadas; nunca en Git.
- Verificar autenticidad, timestamp y tolerancia de replay de peticiones del gateway cuando el contrato sea aprobado.
- Verificar firma e idempotencia de webhooks de Wompi antes de cambiar una suscripción o entitlement.
- Aplicar autorización por operación y no asumir que `roles` equivale automáticamente a permisos de negocio.
- Exponer solo los endpoints de Actuator necesarios para salud, información y métricas.

## Metodología Spec-Driven Development

El repositorio sigue Spec-Driven Development clásico. Las especificaciones vivirán bajo `docs/specs/`, organizadas por funcionalidad. Antes de implementar una capacidad nueva:

1. Crear o actualizar la spec con contexto, alcance, requisitos, reglas, casos de éxito y casos de error.
2. Registrar las decisiones y contratos relevantes.
3. Implementar únicamente lo respaldado por una spec aprobada.
4. Añadir pruebas que demuestren los escenarios de la spec.
5. Actualizar la documentación si cambian contratos, configuración, datos, eventos o comandos.

No crear carpetas o especificaciones ficticias para aparentar que una decisión está tomada. Las preguntas abiertas se mantienen en [docs/AMBIGUIDADES.md](docs/AMBIGUIDADES.md).

## Restricción de ambigüedades

Si una petición contiene una ambigüedad que puede afectar seguridad, contrato, datos, pagos, permisos, arquitectura o comportamiento observable, el agente debe detenerse antes de editar. Debe formular preguntas concretas y registrar la resolución en `docs/AMBIGUIDADES.md`.

No asumir defaults silenciosos en decisiones críticas. Una tarea puede continuar solo si las partes ambiguas son irrelevantes para el cambio o si ya existe una decisión documentada y aprobada.

## Límite de tamaño de cambios

Se prohíben cambios cuyo diff total agregado y eliminado supere 1000 líneas por solicitud. Antes de editar, estimar el tamaño. Si se supera el umbral:

- Detener la implementación.
- Informar al usuario que debe revisar cada cambio.
- Recomendar dividir la petición en incrementos pequeños, por responsabilidad o por spec.
- Proponer un orden de modularización y esperar confirmación.

No usar este límite para ocultar cambios relacionados en commits separados: cada incremento debe ser revisable y funcional.

## Convenciones técnicas

- Java 21, Spring Boot 4.1.1 y Maven Wrapper.
- Preferir inyección por constructor y dependencias `final`.
- Preferir visibilidad package-private en componentes Spring cuando no sea necesaria la pública.
- Usar `@ConfigurationProperties` tipadas y validadas para configuración.
- Definir límites transaccionales pequeños; usar `readOnly = true` para consultas.
- Mantener `spring.jpa.open-in-view=false`.
- Separar web y persistencia con DTOs/records y validación Jakarta; no exponer entidades.
- Usar comandos específicos para operaciones de negocio.
- Centralizar errores con `@RestControllerAdvice` y respuestas consistentes, preferiblemente Problem Details.
- Usar APIs versionadas y respuestas JSON con objeto raíz consistente.
- Usar SLF4J; nunca `System.out.println()` para logging.
- Usar Testcontainers en pruebas de integración y puertos aleatorios.

## Verificación de cambios

Antes de terminar una tarea, ejecutar el comando Maven más estrecho que valide el cambio y, cuando corresponda, la suite completa. Como mínimo, comprobar compilación y pruebas con:

```powershell
./mvnw.cmd test
```

No declarar implementada una integración externa sin pruebas de autenticidad, errores, reintentos e idempotencia cuando aplique.

## Flujo de contribución

- Usar ramas `CM-<numero>-<descripcion-kebab-case>`.
- Todo cambio ordinario entra mediante PR y revisión de una persona distinta del autor.
- Mantener `main` estable y promover cambios desde `develop` mediante Merge commit.
- Integrar ramas de trabajo en `develop` mediante Squash.
- Actualizar la spec y este documento en el mismo PR cuando cambien reglas o contratos.