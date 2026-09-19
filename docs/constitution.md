# Constitución de cameia-cuentas

Principios no negociables. Toda spec y todo PR los cumple; en conflicto, esta lista prevalece. El detalle vive en [CLAUDE.md](../CLAUDE.md) y [guidelines.md](../guidelines.md).

1. **Stack:** Java 21 + Spring Boot 4.1.1 + Maven Wrapper + PostgreSQL 16; subir una versión mayor exige spec aprobada. → `pom.xml`.
2. **Alcance y datos:** solo cuenta local, planes, suscripciones, pagos y entitlements por `firebaseUid`; base y rol propios, sin FKs externas; nunca contraseñas ni consumo de IA. → revisión de PR y de esquema.
3. **Capas DDD:** `domain` sin dependencias de otras capas y `application` → `domain` solo por puertos. → `LayeredArchitectureTest` en verde.
4. **Entrada de confianza:** solo el API Gateway (IAM+OIDC, VPC interna); contrato mínimo `firebase_uid, email, roles, request_id`; sin campos nuevos del JWT sin contrato documentado. → config de despliegue + DTO de entrada.
5. **Datos sensibles:** secretos de Firebase/Wompi solo en gestor de secretos o variables de entorno, nunca en Git; logs SLF4J en español sin JWT, tokens, contraseñas ni datos de pago. → escaneo de secretos en CI + `grep` de `System.out`.
6. **Wompi:** firma SHA-256 e idempotencia verificadas antes de cambiar cualquier suscripción o entitlement. → pruebas de firma, idempotencia y reintentos.
7. **Spec-Driven:** toda capacidad nace de una spec aprobada en `specs/CM-<numero>-<Descripcion>/` (`spec.md` con requisitos EARS, `plan.md`, `tasks.md`); prohibido crear implementacion si no esta en una spec.
8. **Puerta de ambigüedad:** detenerse ante ambigüedad de seguridad, contrato, datos, pagos, permisos o arquitectura; máx. 6 preguntas; la decisión queda en la spec afectada y el prompt en la bitácora de IA. → spec + bitácora del día.
9. **Tests:** JUnit 5; cada spec con casos de éxito y de error; integraciones externas con pruebas de autenticidad, reintentos, errores e idempotencia; integración con Testcontainers y puerto aleatorio. → `./mvnw.cmd test`.
10. **Verde antes de PR:** `./mvnw.cmd test` y `./mvnw.cmd clean package` pasan localmente. → ejecución de ambos comandos.
11. **Tamaño de cambio:** diff agregado + eliminado ≤ 1000 líneas por solicitud; si se supera, dividir en incrementos revisables y esperar confirmación. → `git diff --stat`.
12. **Idioma y nombres:** identificadores en inglés; documentación, Javadoc, OpenAPI, logs, excepciones, commits y PRs en español; tablas y columnas en `snake_case` español; sin abreviaturas. → revisión de PR.
13. **Contribución:** ramas `CM-<n>-<kebab-case>`; PR revisado por otra persona; `develop` por Squash y `main` por Merge commit; spec y documentación actualizadas en el mismo PR. → reglas de rama + checklist de PR.
