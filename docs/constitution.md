# Constitución de cameia-cuentas

Principios no negociables. Toda spec y todo PR los cumple; en conflicto, esta lista prevalece sobre cualquier otro documento del repositorio. El detalle vive en el [CLAUDE.md](../CLAUDE.md) y en el [estándar](estandar-backend.md).

1. **Stack:** Java 21, Spring Boot 4.1.1 y Maven Wrapper, más PostgreSQL 16; subir una versión mayor exige spec aprobada. → `pom.xml`.
2. **Alcance y datos:** solo lo que es del servicio; base y rol propios y sin claves foráneas hacia otros servicios. → revisión del PR y del esquema.
3. **Capas y dependencias:** las reglas de la sección 3 del estándar. → prueba de arquitectura en verde.
4. **Entrada de confianza:** solo se acepta el tráfico del Gateway; la identidad llega en los encabezados `X-User-*` y nunca del cuerpo ni de la ruta. → configuración de despliegue y pruebas del controlador.
5. **Datos sensibles:** secretos solo por variable de entorno o gestor de secretos; nada sensible en logs, URL ni errores. → escaneo de secretos en CI y búsqueda de `System.out`.
6. **Errores:** formato común con `code` y `requestId`; cada error previsible con código, estado, mensaje y prueba. → `docs/errores.md` y pruebas del manejador.
7. **Base de datos:** solo por migración; el esquema repite lo que valida el código. → migración probada con Testcontainers.
8. **Desarrollo guiado por especificación:** nada se implementa sin spec aprobada. → revisión del PR contra la spec.
9. **Puerta de ambigüedad:** ante una ambigüedad de seguridad, contrato, datos o arquitectura se detiene el trabajo y se pregunta (hasta 6 preguntas por ronda). → decisión en la spec.
10. **Pruebas:** JUnit 5, un caso por camino y por rama, PostgreSQL real con Testcontainers y puerto aleatorio, cobertura de lo nuevo ≥ 90 %. → informe de JaCoCo.
11. **Verde antes del PR:** `./mvnw.cmd clean verify` pasa. → salida del comando.
12. **Tamaño del cambio:** un PR no pasa de 1000 líneas entre agregadas y eliminadas, salvo una excepción documentada en la spec. → `git diff --shortstat`.
13. **Idioma y nombres:** identificadores en inglés y documentación en español. → revisión del PR.
14. **Documentación formal:** OpenAPI, Javadoc y comentarios completos. → revisión del PR y OpenAPI generado.
15. **Contribución:** rige `CONTRIBUTING.md`; la revisión la hace una persona distinta del autor y el merge siempre lo hace una persona. → reglas de rama y validadores de PR.
16. **Pagos:** la firma SHA-256 y la idempotencia de Wompi se verifican antes de cambiar una suscripción o un entitlement. → pruebas de firma, de idempotencia y de reintentos.
17. **Ingreso de terceros:** los webhooks de terceros llegan por el Gateway, nunca directo al servicio. → configuración de rutas del Gateway.
