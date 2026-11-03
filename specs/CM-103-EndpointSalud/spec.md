# Spec: endpoint de salud y ejecución en contenedor

- Estado: `Aprobada`
- Responsable: Juan David Vela Coronado
- Fecha: 2026-09-09 · **Actualizada el 2026-09-17 por CM-14:** la ruta pasa de `/health` a `/api/v1/users/health`
- Decisiones relacionadas: frontera de confianza ([CLAUDE.md § Límites de entrada y confianza](../../CLAUDE.md)), versionado aún pendiente ([CLAUDE.md § Preguntas abiertas](../../CLAUDE.md))

## Contexto

El microservicio ya se despliega como contenedor OCI. Antes de que exista cualquier
endpoint de negocio, la plataforma y Docker necesitan una señal para saber si el
proceso está vivo. `HealthController` cubría esa necesidad devolviendo un `Map` sin
contrato declarado, sin pruebas y sin aparecer documentado en Swagger UI.

## Alcance

- `GET /api/v1/users/health` con un DTO propio como respuesta.
- Javadoc en español que springdoc publica en `/v3/api-docs` y Swagger UI muestra en `/swagger-ui.html`.
- Pruebas unitarias del contrato HTTP.
- `Dockerfile` multietapa y `docker-compose.yml` con base de datos propia.

Fuera de alcance: verificar PostgreSQL, Firebase, Wompi o RabbitMQ; Spring Boot
Actuator; versionado de la ruta, que sigue como pregunta abierta en `CLAUDE.md`.

## Requisitos

1. `GET /api/v1/users/health` responde `200 OK` con `{"status":"UP"}` y `Content-Type: application/json`.
2. La respuesta es el record `HealthResponse`, no un `Map`. El JSON expone únicamente el campo `status`.
3. El endpoint no consulta la base de datos ni ninguna integración externa. Responde igual con PostgreSQL caído.
4. El endpoint queda documentado en OpenAPI a partir del Javadoc, sin anotaciones de Swagger en el código.
5. La imagen declara `HEALTHCHECK` contra `GET /api/v1/users/health` con `--interval=30s --timeout=5s --start-period=60s --retries=3`.
6. `docker-compose.yml` levanta la aplicación y su propio PostgreSQL 16. El puerto publicado de la base es configurable con `DB_PORT_HOST` para no chocar con un PostgreSQL local.

## Reglas

- Es el único endpoint exento del contrato de entrada del Gateway (`firebase_uid`, `email`, `roles`, `request_id`), porque no lee ni modifica datos de la cuenta.
- El cuerpo no incluye versión, nombre de host, ni datos de configuración: sería información de infraestructura expuesta sin necesidad.
- La ruta lleva el prefijo `/api/v1/users` porque el API Gateway enruta `Path=/api/v1/users/**` sin `StripPrefix` ni `RewritePath`: reenvía la ruta completa. Una sonda en `/health` responde `404` a través del Gateway, así que el prefijo no es decoración sino la única forma de que la lista de health del Gateway alcance a Cuentas. No se conserva `/health` como alias: dos rutas para lo mismo obligan a mantener dos pruebas.
- `HEALTHCHECK` apunta a este endpoint y no a Actuator: verificar la base de datos aquí haría que un fallo de PostgreSQL reiniciara la aplicación en lugar de dejarla reportar el error.

## Casos de éxito

| Caso | Resultado esperado |
|---|---|
| `GET /api/v1/users/health` con la aplicación arriba | `200 OK`, cuerpo exacto `{"status":"UP"}` |
| `GET /api/v1/users/health` con PostgreSQL detenido | `200 OK`, mismo cuerpo |
| Contenedor arrancado hace más de 60s | Estado `healthy` en `docker ps` |

## Casos de error

| Caso | Resultado esperado |
|---|---|
| `POST /api/v1/users/health` | `405 Method Not Allowed` |
| Proceso caído o saturado | Sin respuesta; tras 3 intentos fallidos el contenedor queda `unhealthy` |
| `docker compose up` sin `DB_PASSWORD` | Compose aborta con el mensaje de variable obligatoria |

## Verificación

- `HealthControllerTest` cubre los casos 1, 2 y `POST /api/v1/users/health`.
- `./mvnw.cmd test` y `./mvnw.cmd clean package` en verde.
- `docker compose up --build` seguido de `docker ps` mostrando `healthy`.
