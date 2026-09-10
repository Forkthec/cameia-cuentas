# Spec: documentación de la API con Swagger UI

- Estado: `Aprobada`
- Responsable: Juan David Vela Coronado
- Fecha: 2026-09-10
- Ambigüedades relacionadas: [A-001](../AMBIGUIDADES.md) (frontera de confianza), [A-005](../AMBIGUIDADES.md) (interruptor de documentación)

## Contexto

El microservicio publica su contrato OpenAPI con springdoc. La interfaz navegable era
Scalar, servida en `/scalar`, que exigía importar a mano una autoconfiguración pensada
para Spring Boot 3. Se reemplaza por Swagger UI, que springdoc autoconfigura sin código
propio en Spring Boot 4.

Publicar ese contrato en producción es un problema aparte. Cuentas administra pagos,
suscripciones y permisos, así que el documento OpenAPI describe exactamente las rutas
que un atacante querría enumerar. La red ya lo protege (A-001), pero esa protección es
configuración de despliegue y no debe ser la única barrera.

## Alcance

- Swagger UI como interfaz de referencia, servida en `/swagger-ui.html`.
- Documento OpenAPI en `/v3/api-docs`, generado por springdoc a partir del Javadoc.
- Un interruptor único, `API_DOCUMENTATION_ENABLED`, que gobierna ambos recursos.
- El perfil `prod` deja los dos apagados de forma rígida.
- Pruebas del valor que resuelve cada entorno.

Fuera de alcance: autenticación propia de la ruta de documentación, publicación del
contrato hacia el API Gateway y versionado del documento, que depende de A-003.

## Requisitos

1. `GET /swagger-ui.html` redirige a `/swagger-ui/index.html` y muestra el contrato del microservicio cuando la documentación está encendida.
2. `GET /v3/api-docs` devuelve el documento OpenAPI 3.1 con los metadatos de `OpenAPIConfiguration` y las descripciones derivadas del Javadoc.
3. `API_DOCUMENTATION_ENABLED` vale `false` por defecto en `application.properties`. Un entorno que no diga nada no publica el contrato.
4. En desarrollo la documentación se enciende con `API_DOCUMENTATION_ENABLED=true`. `.env.example` trae la variable y `docker-compose.yml` la pasa al contenedor con `true` por defecto.
5. El perfil `prod` fija ambos en `false` con un valor literal. Ninguna variable de entorno los enciende.
6. Con la documentación apagada, `/swagger-ui.html`, `/swagger-ui/index.html` y `/v3/api-docs` responden `404`.
7. Apagar la documentación no afecta `GET /health` ni ningún otro endpoint.

## Reglas

- El interruptor cubre la interfaz y el JSON a la vez. Ocultar solo la interfaz no protege nada: el documento en `/v3/api-docs` basta para reconstruir el contrato completo.
- El valor de `prod` es literal y no una variable con default. Una variable mal puesta en el despliegue no debe poder exponer el contrato de un servicio de pagos.
- `guidelines.md` prefiere variables de entorno sobre perfiles para configurar entornos. Por eso el mecanismo primario es la variable; el perfil `prod` solo agrega la segunda barrera.
- El interruptor de desarrollo no vive en `application-local.properties`. Ese archivo está en `.gitignore` y cambia de una máquina a otra, así que el comportamiento del repositorio no puede depender de él.
- `OpenAPIConfiguration` se registra en todos los perfiles. El bean solo aporta metadatos; cuando springdoc está apagado, nada de eso se sirve por HTTP.
- Sin anotaciones de Swagger en los controladores: las descripciones salen del Javadoc en español vía therapi-runtime-javadoc.

## Casos de éxito

| Caso | Resultado esperado |
|---|---|
| Desarrollo con `API_DOCUMENTATION_ENABLED=true` | `/swagger-ui.html` → `302`, `/swagger-ui/index.html` → `200`, `/v3/api-docs` → `200` |
| Desarrollo con `API_DOCUMENTATION_ENABLED=false` | `/swagger-ui.html` y `/v3/api-docs` → `404` |
| Perfil `prod` | `/swagger-ui.html` y `/v3/api-docs` → `404` |
| Perfil `prod` con `API_DOCUMENTATION_ENABLED=true` | `/swagger-ui.html` y `/v3/api-docs` → `404`; la variable se ignora |
| Cualquier perfil, documentación apagada | `GET /health` sigue devolviendo `200` con `{"status":"UP"}` |

## Casos de error

| Caso | Resultado esperado |
|---|---|
| Entorno sin archivo de propiedades propio ni variable, por ejemplo `staging` | Documentación apagada; hereda el default seguro de `application.properties` |
| `API_DOCUMENTATION_ENABLED` con un valor no booleano, por ejemplo `quiza` | La aplicación no arranca: springdoc evalúa la propiedad en una expresión condicional y falla en el arranque. El despliegue se detiene en vez de publicar el contrato por accidente |
| Petición a `/scalar` | `404`; la ruta anterior ya no existe |

## Verificación

- `ApiDocumentationFlagTest` cubre los cinco escenarios de entorno y variable. Ninguno usa el perfil `local`, para que el resultado no dependa de un archivo ignorado por Git.
- `./mvnw.cmd test` y `./mvnw.cmd clean package` en verde.
- Ejecución del jar en los perfiles `local` y `prod` comprobando los códigos HTTP de la tabla de casos de éxito.
