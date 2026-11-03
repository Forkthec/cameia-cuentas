# Plan · CM-290 503 `DATABASE_UNAVAILABLE` en Cuentas

Estado: **APROBADO por Paula el 19-oct-2026.** Todo lo que depende de una decisión pendiente (spec §17) se construye con la opción recomendada; si Paula elige otra, solo cambian las tarjetas indicadas.

## 1. Enfoque

Un clasificador paquete-privado, `DatabaseUnavailability`, recorre la excepción, sus causas y sus excepciones suprimidas y dice si es una indisponibilidad de la base (spec REQ-02). El manejador general (`falloInterno`) lo consulta después del formulario mal codificado y antes del 500: si coincide, responde 503 `DATABASE_UNAVAILABLE`. El pool de conexiones espera 5 s y no 30 s. Nada más cambia.

## 2. Reutilizar → extender → crear

| Pieza | Decisión | Por qué |
|---|---|---|
| `BusinessExceptionHandler.falloInterno`, `problema`, `primerRegistro`, `sqlStateOf`, `claseMasEspecifica` | Se **extienden** (una rama nueva y un método privado `baseNoDisponible`) | Ya arman la forma común, el `requestId` y el registro único |
| `BadParameterEncoding` | **Modelo de estilo** del clasificador nuevo | Mismo problema: reconocer una excepción de una librería sin acoplar la capa |
| `ErrorCode` | Se **extiende** con `DATABASE_UNAVAILABLE` (junto a `DEPENDENCY_UNAVAILABLE`) | Catálogo cerrado; `ErrorCodeDocumentationTest` lo vigila |
| `DependencyUnavailableException` | **No sirve**: la causa está en Firebase y nace en un puerto; la de la base nace en Hibernate | Spec §10 |
| `RawHttpClient`, `ProtocolErrorsEndToEndTest` | **Modelo** de la prueba de punta a punta | Ya existen |
| `OpenApiProtocolErrorsTest` | **Modelo** de la prueba de OpenAPI | Ya existe |
| `DatabaseUnavailability` | Se **crea** | Ningún componente existente recorre `getSuppressed()` |

`sqlStateOf` solo sigue `getCause()`; para el registro del 503 se usa el SQLState del primer `SQLException` del árbol que devuelve el clasificador (una vía, sin duplicar el recorrido).

## 3. Archivos

| Acción | Ruta |
|---|---|
| Crear | `src/main/java/tech/cameia/cuentas/presentation/advice/DatabaseUnavailability.java` |
| Modificar | `src/main/java/tech/cameia/cuentas/presentation/advice/BusinessExceptionHandler.java` |
| Modificar | `src/main/java/tech/cameia/cuentas/domain/exception/ErrorCode.java` |
| Modificar | `src/main/resources/application.properties` |
| Modificar | `src/main/java/tech/cameia/cuentas/presentation/controller/{UserRegistrationController,CurrentAccountController,AccountActivationController}.java` (503 en OpenAPI) |
| Modificar | `docs/errores.md`, `CLAUDE.md` (sección 4, una línea), `postman/cameia-cuentas.postman_collection.json`, `postman/README.md` |
| Crear | `docs/adr/0005-base-de-datos-no-disponible.md` |
| Crear (pruebas) | `src/test/.../presentation/advice/DatabaseUnavailabilityTest.java`, `src/test/.../presentation/DatabaseUnavailableEndToEndTest.java`, `src/test/.../presentation/OpenApiDatabaseUnavailableTest.java`, `src/test/.../infrastructure/config/DatabasePoolConfigurationTest.java` |
| Ampliar (pruebas) | `src/test/.../presentation/advice/BusinessExceptionHandlerTest.java` |
| No tocar | Migraciones, `.github/`, `pom.xml`, `docker-compose.yml`, `.env.example` |

## 4. Orden y commits (un PR, un commit por capacidad)

| Commit | Tarjetas | Capacidad |
|---|---|---|
| 1 | T1 | Pool de conexiones con espera de 5 s y su prueba |
| 2 | T2 | Clasificador de indisponibilidad y sus pruebas unitarias |
| 3 | T3 | Código `DATABASE_UNAVAILABLE`, rama del manejador y prueba del manejador |
| 4 | T4 | Pruebas de punta a punta con PostgreSQL real |
| 5 | T5 | OpenAPI: el 503 en las tres rutas que usan la base |
| 6 | T6 | Postman, ADR, `docs/errores.md` y `CLAUDE.md` |

T2 y T1 son independientes (paralelo posible); T3 depende de T2; T4 de T1 y T3; T5 de T3; T6 al final. T7 (verificación final, una sola vez sobre la punta) no es commit.

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| Un falso 503 sobre un fallo que sí es defecto | La lista de REQ-02 es cerrada y se prueba con negativos medidos (23502, 22012, 42601, 55P03) |
| 5 s son pocos en el arranque en frío de Cloud Run | D-3 lo deja a decisión de Paula; la variable estándar permite subirlo en el despliegue sin tocar código |
| `org.hibernate.TransactionException` demasiado amplia | La lanza Hibernate al fallar abrir, confirmar o deshacer sobre JDBC (medido en E-3, E-4 y E-5), pero también puede envolver un fallo del dato al confirmar: no cuenta si una causa trae un SQLState que no es de conexión; probado con `23505` y `42601` |
| La prueba con base detenida es lenta | `connection-timeout=1500` por propiedad en la prueba; el tiempo real (5 s) se comprueba en la verificación manual con Newman |
| Contenedor detenido dentro de Testcontainers deja el pool roto para otras pruebas | La clase de prueba usa su propio contexto y su propio contenedor; no se comparte |
