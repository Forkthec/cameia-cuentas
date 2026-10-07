# CM-283 · Alinear los documentos y el estándar de calidad — cameia-cuentas

| Campo | Valor |
|---|---|
| Jira | CM-283 (Tarea, Sprint 2) |
| Repositorio | cameia-cuentas |
| Rama | `CM-283-alinear-estandar` |
| Base | `origin/develop` @ `908112c` (5 de octubre de 2026) |
| Estado | Aprobada por Paula Andrea Muñoz Delgado el 6 de octubre de 2026 |
| Documentos hermanos | [plan.md](plan.md) · [tasks.md](tasks.md) |

## 1. Contexto y alcance

Los cuatro microservicios Spring de CAMEIA (`cameia-cuentas`, `cameia-gateway`, `cameia-perfil` y `cameia-entrevista`) se escribieron con documentos de agente distintos que se contradicen entre sí, con `CONTRIBUTING.md`, con el validador de PR y con el código. Esta spec los lleva a un mismo conjunto de reglas de código, de documentación y de configuración técnica, de modo que cada repositorio se pueda leer solo y todos sean consistentes. Hay una spec completa en cada repositorio; las secciones 1 a 5 y 8 a 10 son iguales en los cuatro, y las secciones 6 y 7 son propias de este.

**Parte 1, documentos.** Tres piezas por repositorio y ningún PR de más de 1000 líneas entre agregadas y eliminadas (D-16). La pieza A va sola en el primer PR; B y C van juntas en un segundo PR cuando entre las dos no pasan de 1000 líneas, y si pasan, cada una en el suyo:

| Pieza | Contenido |
|---|---|
| A | `specs/CM-283-AlinearEstandar/` con `spec.md`, `plan.md` y `tasks.md` |
| B | Estándar común: `docs/estandar-backend.md`, `docs/errores.md`, `docs/adr/`, `docs/constitution.md` y `docs/bitacora-ia/` |
| C | Documentos de agente: `CLAUDE.md`, `AGENTS.md`, `CONTRIBUTING.md`, `README.md` y los documentos que se retiran. Se parte en las piezas C1, C2, etc. que hagan falta para no pasar de 1000 líneas; la sección 3 del plan indica el reparto de este repositorio |

**Parte 2, configuración técnica**, del 13 al 23 de octubre de 2026: una pieza por PR y por repositorio (sección 7).

**Fuente única de cada regla.** Cada regla se define en un solo documento y los demás lo enlazan sin copiarla (REQ-DOC-10). Es lo que impide que dos documentos vuelvan a contradecirse.

## 2. Decisiones

Todas son decisiones de Backend. D-01 a D-15 se tomaron el 5 de octubre de 2026. D-16 a D-22 salen de la revisión del 6 de octubre de 2026; Backend las aprobó el mismo 6 de octubre de 2026. Cada una indica por qué y qué se descartó.

| # | Decisión | Por qué | Alternativa descartada |
|---|---|---|---|
| D-01 | `CONTRIBUTING.md` manda en rama, commit y título de PR; los cuatro quedan idénticos: el commit lleva siempre `CM-NNN \| tipo(scope): resultado` | Un solo flujo para los cuatro y el criterio «no se contradicen» sin matices | Dejar los tres que solo recomiendan Conventional Commits |
| D-02 | Una spec completa en cada repositorio | Cada repositorio debe poder leerse solo | Una sola spec en Cuentas |
| D-03 | Se trabaja en `git worktree` | No se tocan las ramas abiertas de otras tareas | Cambiar de rama en cada repositorio |
| D-04 | `docs/errores.md` lista solo los códigos que el código emite hoy | Documentar códigos sin implementar afirmaría una capacidad pendiente | Crear el catálogo con códigos futuros |
| D-05 | El ADR del código de error y del `requestId` entra en la Parte 1 | Es documentación y no depende de código | Escribirlo en la Parte 2 |
| D-06 | `[IA-ASISTIDO]` va solo al final del título del PR; el commit no lo lleva | Con Squash and merge el título del PR es el commit que queda en `develop` | La marca en cada commit |
| D-07 | Una sola fuente de verdad: se retira el documento de reglas del equipo de Perfil y lo que lo duplica | Un documento antiguo por encima del estándar produce contradicciones | Conservarlo con una nota de «no vigente» |
| D-08 | `guidelines.md` se retira de Cuentas y Entrevista; lo que tenga de más pasa al estándar | Perfil y Gateway no lo tienen y choca con decisiones tomadas | Conservarlo en los cuatro |
| D-09 | Perfil de producción `prod`, bandera `API_DOCUMENTATION_ENABLED` y puerto único (el mismo dentro y fuera del contenedor) en los cuatro, sin romper nada y avisando a DevOps solo lo necesario | Mismas variables y mismo comportamiento en todos | Que cada servicio conserve el suyo |
| D-10 | ArchUnit en Entrevista como pieza de la Parte 2 | La prueba de arquitectura ya existe en los otros tres | Dejar el `CLAUDE.md` de Entrevista con la prueba como pendiente |
| D-11 | Perfil conserva el paquete base `co.edu.unicauca.cameia.perfil`; la diferencia se documenta | Renombrar toca todos los archivos y supera con mucho las 1000 líneas | Tarea aparte para renombrarlo |
| D-12 | Todo hallazgo tiene un destino: un PR, una pieza de la Parte 2, una tarea existente o un pendiente con responsable | Nada queda sin resolver | — |
| D-13 | Los documentos del repositorio nombran roles (Backend, DevOps, Frontend, Product Owner), no personas | Una regla no depende de quien la dijo | Nombres propios |
| D-14 | Las pruebas nuevas se nombran `metodo_shouldResultado_whenCondicion` en inglés, con `@DisplayName` en español; las existentes se renombran cuando una tarea las toca | Los nombres de método son identificadores y van en inglés; hoy hay cuatro convenciones | Renombrar todas ahora (diff enorme sin valor funcional) |
| D-15 | No se usa `TODO` en el código; lo pendiente se registra en Jira o en el apartado de pendientes del `CLAUDE.md` | El estándar prohíbe claves Jira en comentarios y un `TODO` sin clave no se puede seguir | `TODO` con clave Jira (regla actual de Perfil) |
| D-16 | Ningún PR pasa de 1000 líneas entre agregadas y eliminadas, tampoco los de solo documentos: la pieza A va sola, y B y C van juntas solo si entre las dos no pasan de 1000 | El límite existe para que cada línea se lea de verdad, y un documento también se revisa línea por línea; un límite con excepciones deja de ser un límite | Un solo PR por repositorio con las tres piezas y una excepción documentada |
| D-17 | La precedencia entre documentos se define una sola vez, en la sección 1 del estándar: (1) la seguridad y los contratos ya publicados; (2) `docs/constitution.md`; (3) `docs/estandar-backend.md`; (4) `CONTRIBUTING.md` en rama, commit y PR; (5) `CLAUDE.md` en lo propio del servicio. Ningún otro documento declara su propia precedencia | Un principio no negociable va por encima del detalle que lo desarrolla, y dos listas de precedencia que se contradicen crean el choque que deberían resolver | Constitución por debajo del estándar (sus principios dejarían de ser no negociables); que cada documento declare su precedencia |
| D-18 | El estándar rige el código nuevo y el modificado. Una diferencia del código existente con el estándar no detiene el trabajo: es un hallazgo con destino (D-12) y se corrige cuando una tarea toca ese archivo | El estándar reúne reglas de los cuatro repositorios y ninguno las cumplía todas; detener cada tarea por código previo bloquearía el sprint sin mejorar nada | Ajustar todo el código ahora (diff fuera de alcance); rebajar el estándar a lo que el código hace hoy |
| D-19 | Sufijos: servicio de aplicación `<Concept>AppService`, adaptador `<Port>Adapter` y configuración `<Topic>Configuration`; `AppService` es el único sufijo abreviado que se admite. Las clases existentes con otra forma conservan su nombre (D-18) | `AppService` lo exige la prueba de arquitectura de Perfil y separa el servicio de aplicación del de `domain.service`; `<Port>Adapter` es la forma de todos los adaptadores existentes; `Configuration` no abrevia y ya la usan Cuentas y Entrevista | `<UseCase>Service` (choca con la prueba de Perfil y con `domain.service`); `Config` (abreviatura); `JpaAdapter` (no lo usa ningún repositorio) |
| D-20 | Visibilidad de paquete en controladores, clases `@Configuration`, métodos `@Bean`, `JpaRepository` y adaptadores; públicos el modelo de dominio, los puertos, los servicios de aplicación, los comandos y los DTO | El controlador vive en `presentation.controller` y llama al servicio de `application.service`: un servicio con visibilidad de paquete no compila | Servicios de aplicación con visibilidad de paquete |
| D-21 | El estándar fija la forma del error y el mapa base de estados para lo nuevo, no el texto literal: el mensaje del 500 es genérico y cada servicio lo fija en su `docs/errores.md`; un estado ya publicado que difiere del mapa base se conserva y se anota en `docs/errores.md` con su destino | Un contrato publicado que consume Frontend está por encima del estándar (D-17); cambiarlo es una decisión de contrato con su propia spec | Cambiar los estados publicados en esta tarea; un texto único del 500 en el estándar |
| D-22 | La identidad nunca sale del cuerpo ni de la ruta, salvo en las rutas sin identidad que el `CLAUDE.md` del servicio declara con su spec (por ejemplo, el registro, que crea la identidad en vez de afirmarla) | Sin la excepción, el principio prohibiría el registro, que es anterior a toda identidad | Principio sin excepción; excepción abierta a cualquier ruta pública |

## 3. Requisitos comunes (EARS)

**REQ-DOC-01.** El repositorio deberá tener un `CLAUDE.md` en la raíz con exactamente diez secciones de nivel 2, en este orden y con estos títulos: `## 1. Servicio`, `## 2. Estructura y dependencias`, `## 3. Límites de confianza`, `## 4. Contrato y errores`, `## 5. Datos`, `## 6. Seguridad`, `## 7. Pruebas`, `## 8. Verificación`, `## 9. Contribución`, `## 10. Pendientes`.

**REQ-DOC-02.** El repositorio deberá tener un `AGENTS.md` en la raíz cuyo único contenido sea la línea `Las reglas de este repositorio están en [CLAUDE.md](CLAUDE.md).`

**REQ-DOC-03.** El repositorio deberá tener `docs/estandar-backend.md` con las quince secciones de la sección 4.1, y su suma SHA-256 deberá ser idéntica en los cuatro repositorios.

**REQ-DOC-04.** El repositorio deberá tener `docs/constitution.md` con los principios de la sección 4.3, cada uno con la forma en que se comprueba.

**REQ-DOC-05.** El repositorio deberá tener `docs/errores.md` con la estructura de la sección 4.4, que lista solo lo que el código emite en el SHA de `origin/develop` indicado en el encabezado de esta spec.

**REQ-DOC-06.** El repositorio deberá tener `docs/adr/0001-codigo-de-error-y-request-id.md` con el contenido de la sección 4.5.

**REQ-DOC-07.** El repositorio deberá tener `docs/bitacora-ia/README.md` y `docs/bitacora-ia/01_alinear-estandar-backend_prompt.md`, con el contenido de la sección 4.6.

**REQ-DOC-08.** Cuando el `CONTRIBUTING.md` de un repositorio difiera del de `cameia-cuentas`, el repositorio deberá quedar con un `CONTRIBUTING.md` byte a byte igual al de `cameia-cuentas`.

**REQ-DOC-09.** El `README.md` del repositorio deberá remitir a `CONTRIBUTING.md` para rama, commit, tipos y revisión, sin repetirlos; deberá usar `./mvnw.cmd clean verify` como comando de verificación; y no deberá contener enlaces rotos.

**REQ-DOC-10.** Cada regla deberá definirse en el documento que la sección 4.7 le asigna; los demás documentos deberán enlazarlo y no copiarla.

**REQ-DOC-11.** Ningún documento del repositorio deberá: nombrar a una persona como fuente de una regla; citar hojas de bitácora personales ni rutas fuera del repositorio; afirmar datos que envejecen (cantidad de pruebas, porcentajes de cobertura, estado de ramas o de PR, fechas de adopción); ni afirmar como existente algo que el código no tiene.

**REQ-DOC-12.** Todo enlace relativo de los documentos que esta spec crea o modifica deberá apuntar a un archivo que exista.

**REQ-DOC-13.** Mientras exista un documento retirado por esta spec, ningún otro documento deberá enlazarlo.

**REQ-DOC-14.** El `CLAUDE.md` del repositorio deberá indicar que las specs nuevas viven en `specs/CM-NNN-Descripcion/` (`Descripcion` en PascalCase), y las carpetas de spec existentes con otro nombre no se renombrarán.

**REQ-DOC-15.** Cada PR de la Parte 1 deberá tener título `CM-283 | docs(scope): resultado [IA-ASISTIDO]`, no superar 1000 líneas de diff entre agregadas y eliminadas, llenar los ocho campos de la plantilla de PR del repositorio y dejar «Control humano» en pendiente.

## 4. Contenido de los documentos

### 4.1 `docs/estandar-backend.md` (idéntico en los cuatro)

Está escrito en español, es autosuficiente (no depende de ningún archivo fuera del repositorio) y no contiene datos que envejecen. Quince secciones, en este orden:

| # | Sección | Qué debe contener |
|---|---|---|
| 1 | Alcance y precedencia | A quién aplica. Precedencia única (D-17): la seguridad y los contratos ya publicados; `docs/constitution.md` en principios no negociables; este estándar en calidad de código; `CONTRIBUTING.md` en rama, commit y PR; `CLAUDE.md` en lo propio del servicio. Si dos fuentes chocan, se detiene el trabajo y se corrige el documento en un PR. El estándar rige el código nuevo y el modificado; una diferencia del código existente es un hallazgo con destino, no un choque (D-18) |
| 2 | Idioma y nombres | Identificadores en inglés; documentación, Javadoc, OpenAPI, logs, mensajes, `@DisplayName`, commits y PR en español; tablas y columnas en `snake_case` español y singular; sin abreviaturas salvo siglas técnicas establecidas (`dto`, `id`, `uid`, `url`, `api`, `jwt`) y el sufijo `AppService`; sufijo por capa (`Controller`, `Request`/`Response`, `AppService`, `Command`, `Policy`, `Entity`, `JpaRepository`, `Adapter`, `Listener`, `PayloadV<n>`, `Configuration`), y las clases existentes conservan su nombre (D-19); verbos (`can…`/`is…`/`has…` sin efectos, `create`/`of` para fábricas, `require…` para guardias, políticas que devuelven un enumerado y no un `boolean`); nombre de pruebas nuevas `metodo_shouldResultado_whenCondicion` con `@DisplayName` en español; sin `I` ni `Impl` |
| 3 | Arquitectura y dependencias | Capas `presentation`, `application`, `domain`, `infrastructure` y sus reglas; `domain` sin Spring, JPA, Rabbit ni Google; persistencia en tres piezas (puerto, Spring Data, adaptador); mapeo a mano; `@Transactional` solo en `application.service` y `readOnly = true` en lecturas; ninguna llamada externa dentro de una transacción; la prueba ArchUnit del repositorio en verde, y la regla que aún no vigila se comprueba en la revisión del PR; prohibido `Utils`, Singleton manual, Service Locator, herencia de más de dos niveles, `Map<String,Object>` como payload, importar clases de otro microservicio |
| 4 | Código limpio y mantenible | Lo mínimo que cumple la spec; reutilizar, extender, crear; método ≤ 20 líneas (15 en agregados y objetos de valor); ≤ 3 parámetros; anidamiento ≤ 2; complejidad ciclomática ≤ 8; clase ≤ 200 líneas; sin parámetro booleano que cambie el comportamiento; inyección por constructor con dependencias `private final` y sin `@Autowired`; visibilidad de paquete por defecto en controladores, `@Configuration`, `@Bean`, `JpaRepository` y adaptadores, y pública para modelo de dominio, puertos, servicios de aplicación, DTO y comandos (D-20); objetos de valor inmutables (`record`); sin setters públicos en el dominio; agregados que no exponen sus colecciones; `ResponseEntity<T>` con el estado explícito (201 en la creación); raíz JSON siempre un objeto; paginación obligatoria en colecciones sin cota; sin `TODO` en el código; logs con SLF4J y guarda o `Supplier` en los costosos de `DEBUG` y `TRACE`; antipatrones que bloquean un PR (dominio anémico, entidad JPA como modelo de dominio, agregado anotado con `@Entity`, lógica de negocio en el controlador, entidad o agregado devuelto por el controlador, clave foránea hacia otro contexto, `JpaRepository` inyectado desde `application`) |
| 5 | Validación y casos borde | Validación en dos capas (DTO y dominio); la lista de casos borde por grupo (presencia, texto en NFC, números, fechas en UTC, enumerados, duplicados, estado, propiedad, colecciones, concurrencia, falla parcial, dependencias, carga) con la regla «cada celda aplicable es una prueba; las que no aplican se justifican en la spec» |
| 6 | Errores | Respuesta `application/problem+json; charset=UTF-8` con `type`, `title`, `status`, `detail`, `instance`, `code`, `requestId` y `errors[{field,code,message}]`; códigos `<SUJETO>_<CAUSA>` con vocabulario de causas cerrado; reglas del catálogo (un código ↔ un estado ↔ un mensaje ↔ una excepción ↔ una prueba); mapa base de estados para lo nuevo, y un estado ya publicado que difiere se conserva y se anota en `docs/errores.md` (D-21); `INTERNAL_ERROR` con un mensaje genérico que fija cada servicio; el Gateway conserva su formato `{code,message}`; los códigos `EMAIL_NOT_VERIFIED`, `PLAN_LIMIT` y `LLM_UNAVAILABLE` están reservados por el contrato del proyecto y se publican cuando el código los emita; cada error se registra una vez con `code`, `requestId` e identificador interno, sin datos personales |
| 7 | Base de datos | Solo por migración Flyway; esquema que repite el código (`NOT NULL`, `CHECK`, `UNIQUE`, `VARCHAR(n)` del mismo tamaño que el límite validado); tipos (`uuid`, `timestamptz` en UTC, `date`, `numeric`); claves foráneas solo dentro del servicio con `ON DELETE` explícito; índices; `@Version` y bloqueo pesimista; nombres con prefijos `pk_`, `fk_`, `uq_`, `ck_`, `ix_`; datos sensibles; una prueba por restricción |
| 8 | Seguridad | ASVS nivel 1 y OWASP API Security Top 10 (tabla API1 a API10 con qué se comprueba); identidad solo de los encabezados `X-User-*` del Gateway, salvo las rutas sin identidad del `CLAUDE.md` (D-22); consultas parametrizadas; respuestas por DTO; `Content-Type` con charset; Actuator público solo en `health` e `info`; secretos solo por variable de entorno y `.env.example` sin valores; nada sensible en logs, URL ni errores |
| 9 | Pruebas y cobertura | Tipos por capa; PostgreSQL real con Testcontainers y puerto aleatorio en todo servicio con base de datos; H2 prohibido; `*Test` con Surefire y `*IT` con Failsafe; cobertura de lo nuevo o modificado ≥ 90 % de líneas y de ramas medida con JaCoCo, con cada línea sin cubrir justificada; la cobertura global del repositorio no baja; build terminado = `./mvnw.cmd clean verify` con salida real |
| 10 | Documentación | OpenAPI/Swagger completo (cada endpoint, parámetro, respuesta de error y DTO con descripción, ejemplo, obligatoriedad, límites, patrón y valores permitidos, coherentes con la validación y la columna); Javadoc en toda clase, interfaz, `record`, enumerado y método; `package-info.java` por paquete; comentarios de bloque en la lógica importante y de línea cortos; ADR por decisión de contrato o arquitectura; `docs/errores.md`; `README.md` y `.env.example` al día; sin `CM-NNN` ni referencias a otros archivos en los comentarios |
| 11 | Reglas del proyecto | Idempotencia, LLM y APIs externas, eventos, datos entre servicios, propiedad (el dueño sale del token; Perfil 403 y 404, Entrevista 404 en ambos), fechas en UTC con reloj inyectable, texto libre, rendimiento (p95 ≤ 2 s en operaciones propias sin IA, 5xx propios < 1 %), privacidad (Ley 1581), servicio desplegable (`Dockerfile` de dos etapas, usuario sin privilegios y `HEALTHCHECK`; `.env.example`; ingreso interno), evidencia del PR |
| 12 | Desarrollo guiado por especificación | Análisis, spec, plan y tareas con aprobación entre fases; sin spec aprobada no hay código; `specs/CM-NNN-Descripcion/` con `spec.md` (requisitos EARS numerados, cada campo con regla, límites, mensaje, código y estado HTTP), `plan.md` y `tasks.md` (cada tarea ≤ 30 minutos, marcada `[x]` en el momento de terminarla); decisiones con porqué, alternativas descartadas y decisión humana; lo que no se sabe se pregunta (hasta 6 preguntas por ronda) y lo sin respuesta queda como pendiente con su responsable; un PR no pasa de 1000 líneas |
| 13 | Contribución y entrega | Remite a `CONTRIBUTING.md`; `[IA-ASISTIDO]` solo al final del título del PR; la IA puede abrir un PR y nunca fusionarlo; el control humano lo marca quien revisa; plantilla de PR de ocho campos; bitácora de IA solo con la entrada de las decisiones con peso humano en `docs/bitacora-ia/` |
| 14 | Guía de Spring Boot: qué se adopta | Tabla de los catorce puntos de la guía de Spring Boot: se adoptan inyección por constructor, visibilidad de paquete, propiedades tipadas y validadas, fronteras de transacción, `open-in-view=false`, separación web y persistencia, comandos, manejo centralizado de excepciones, Actuator mínimo, Testcontainers, puerto aleatorio y logging; **no se adopta** el versionado nativo de API (la versión va en la ruta `/api/v1/...`, contrato consumido por Gateway, Web y Entrevista) ni la internacionalización con `ResourceBundles` (el producto es monolingüe en español y no hay requisito); sobre configuración por variables de entorno frente a perfiles, rige el perfil `prod` de la decisión D-09 |
| 15 | Atributos de calidad | Tabla de los siete atributos (seguridad, fiabilidad, rendimiento, mantenibilidad, testabilidad, observabilidad, compatibilidad de contrato) con la evidencia típica; cada PR declara los que toca |

**Límite de la sección 4.1.** Las reglas propias de un servicio (por ejemplo, el modelo de seguridad del Gateway o la regla de no calcular cuota en Perfil) no entran aquí: viven en su `CLAUDE.md`.

### 4.2 `CLAUDE.md`

Sigue la estructura de REQ-DOC-01. Lo común:

- La sección 8 abre con: «Verificación completa: `./mvnw.cmd clean verify`. Genera el informe de cobertura en `target/site/jacoco/index.html`.»
- La sección 9 es idéntica en los cuatro y dice exactamente:

```
## 9. Contribución

Rama, commit, tipos, título de PR, revisión y merge: rige [CONTRIBUTING.md](CONTRIBUTING.md). Lo que este repositorio añade:

- El título del PR lleva `[IA-ASISTIDO]` al final cuando hubo IA; el commit no lo lleva. Un commit asistido por IA lleva el trailer `Co-Authored-By` con el modelo.
- Una IA puede abrir un PR; nunca lo fusiona. El control humano lo marca la persona que revisa.
- Un PR cubre una pieza reconocible y no pasa de 1000 líneas entre agregadas y eliminadas.
- Antes del código hay una spec aprobada en `specs/CM-NNN-Descripcion/` (`spec.md`, `plan.md` y `tasks.md`); el flujo está en la sección 12 de [docs/estandar-backend.md](docs/estandar-backend.md).
- Las decisiones con peso humano se registran en [docs/bitacora-ia/](docs/bitacora-ia/README.md).
```

- La sección 10 lista cada pendiente con su responsable (rol) y lo que bloquea. Solo lo que sigue abierto en el código y en Jira; nada resuelto.
- Todo hecho que afirma (puertos, perfiles, rutas, tablas, componentes) se comprobó contra el código de `origin/develop`.
- El contenido propio de cada servicio sale del documento de agente actual y se detalla en la sección 6.

### 4.3 `docs/constitution.md`

Lista numerada de principios no negociables. Cada uno termina con `→` y la forma de comprobarlo. El párrafo inicial no declara precedencia propia: remite a la sección 1 del estándar (D-17). Los comunes, en este orden:

1. **Stack:** Java 21, Spring Boot 4.1.1 y Maven Wrapper (más PostgreSQL 16 si el servicio tiene base de datos); subir una versión mayor exige spec aprobada. → `pom.xml`.
2. **Alcance y datos:** solo lo que es del servicio; base y rol propios y sin claves foráneas hacia otros servicios (cuando haya base). → revisión del PR y del esquema.
3. **Capas y dependencias:** las reglas de la sección 3 del estándar. → prueba de arquitectura en verde.
4. **Entrada de confianza:** solo se acepta el tráfico del Gateway; la identidad llega en los encabezados `X-User-*` y nunca del cuerpo ni de la ruta, salvo en las rutas sin identidad que el `CLAUDE.md` declara con su spec. → configuración de despliegue y pruebas del controlador.
5. **Datos sensibles:** secretos solo por variable de entorno o gestor de secretos; nada sensible en logs, URL ni errores. → escaneo de secretos en CI y búsqueda de `System.out`.
6. **Errores:** formato común con `code` y `requestId`; cada error previsible con código, estado, mensaje y prueba. → `docs/errores.md` y pruebas del manejador.
7. **Base de datos:** solo por migración; el esquema repite lo que valida el código. → migración probada con Testcontainers (cuando haya base).
8. **Desarrollo guiado por especificación:** nada se implementa sin spec aprobada. → revisión del PR contra la spec.
9. **Puerta de ambigüedad:** ante una ambigüedad de seguridad, contrato, datos o arquitectura se detiene el trabajo y se pregunta (hasta 6 preguntas por ronda). → decisión en la spec.
10. **Pruebas:** JUnit 5, un caso por camino y por rama, PostgreSQL real con Testcontainers y puerto aleatorio, cobertura de lo nuevo ≥ 90 %. → informe de JaCoCo.
11. **Verde antes del PR:** `./mvnw.cmd clean verify` pasa. → salida del comando.
12. **Tamaño del cambio:** un PR no pasa de 1000 líneas entre agregadas y eliminadas. → `git diff --shortstat`.
13. **Idioma y nombres:** identificadores en inglés y documentación en español. → revisión del PR.
14. **Documentación formal:** OpenAPI, Javadoc y comentarios completos. → revisión del PR y OpenAPI generado.
15. **Contribución:** rige `CONTRIBUTING.md`; la revisión la hace una persona distinta del autor y el merge siempre lo hace una persona. → reglas de rama y validadores de PR.

Además, los principios propios del servicio (sección 6), numerados a continuación. El detalle de cada principio vive en el estándar y en el `CLAUDE.md`; la constitución solo lo enuncia y enlaza.

### 4.4 `docs/errores.md`

Cuatro apartados: (1) «Formato», un párrafo que remite a la sección 6 del estándar; (2) «Códigos que el servicio emite» con la tabla `Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba` (solo códigos que el código emite hoy; «Endpoints» lista cada método y ruta que lo emite y «Campo» el `field` de `errors[]` cuando es un error de campo, para que un código lleve a un solo lugar del código); (3) «Respuestas sin código» con la tabla `HTTP | Título | Cuándo`, para los servicios que responden `ProblemDetail` sin `code`; (4) «Cómo se agrega un código»: con la spec que lo introduce, junto a su excepción y su prueba, sin reutilizar ni renombrar uno publicado.

### 4.5 `docs/adr/0001-codigo-de-error-y-request-id.md`

Secciones: `Estado` (Aceptada), `Contexto`, `Decisión`, `Consecuencias` y `Alternativas descartadas`. Contexto: el contrato base de error es `ProblemDetail` (RFC 9457) con `errors[{field,message}]` y JSON en `camelCase`, y los servicios necesitan un identificador estable para depurar y para que Frontend actúe por causa. Decisión: se agregan `code`, `requestId` y `errors[].code` como miembros de extensión, sin reemplazar nada de lo publicado; `requestId` es el `X-Request-Id` que pone el Gateway. Consecuencias: cambio aditivo y compatible; Frontend puede empezar a leer los campos nuevos cuando cada servicio los emita; cada servicio los adopta en su primera tarea de código. Alternativas descartadas: `codigoCameia` y `correlationId` (nombres propios sin ventaja sobre los miembros de extensión de la norma) y un formato propio distinto de `ProblemDetail`. La variante del Gateway describe que conserva `{code,message}` y que es quien genera el `requestId`. Decisión humana: Backend, 5 de octubre de 2026.

### 4.6 `docs/bitacora-ia/`

`README.md` explica, en un párrafo, que la carpeta guarda solo la entrada (el prompt armado con rol, contexto, tarea, condiciones y formato) de cada decisión en la que una persona cambió el rumbo de una propuesta de la IA, que la salida es la spec del repositorio (`specs/CM-NNN-*/spec.md`, sección de decisiones) y que el nombre de cada archivo es `NN_tema_prompt.md`. `01_alinear-estandar-backend_prompt.md` contiene la fecha, la herramienta y el prompt de la decisión de alinear los cuatro repositorios, redactado de forma autosuficiente y con el enlace a `specs/CM-283-AlinearEstandar/spec.md`.

### 4.7 Fuente única de cada regla

| Regla | Documento donde se define | Los demás |
|---|---|---|
| Rama, commit, tipos, título de PR, revisión, merge | `CONTRIBUTING.md` | Enlazan |
| Ubicación de `[IA-ASISTIDO]`, merge de la IA, tamaño del PR | `CLAUDE.md` sección 9 | Enlazan |
| Calidad de código, errores, base de datos, seguridad, pruebas, documentación | `docs/estandar-backend.md` | Enlazan |
| Principios no negociables | `docs/constitution.md` | Enlazan |
| Rutas, estructura, datos y seguridad propios del servicio | `CLAUDE.md` | — |
| Códigos de error del servicio | `docs/errores.md` | Enlazan |
| Decisiones de contrato o arquitectura | `docs/adr/` y la spec que las toma | Enlazan |
| Instalación, comandos y puertos de uso | `README.md` | — |

## 5. Hallazgos comunes y su destino

Cada hallazgo se comprobó contra `origin/develop` y tiene un destino. «P1» es la Parte 1 y «P2» la Parte 2.

| ID | Hallazgo | Evidencia | Destino |
|---|---|---|---|
| H-01 | `CONTRIBUTING.md` de gateway, perfil y entrevista no exige la clave Jira en el commit; el de Cuentas sí | `CONTRIBUTING.md` líneas 33 a 35 y 41 | P1 C (REQ-DOC-08) |
| H-02 | `perf` figura como tipo en READMEs y documentos de agente; el validador de PR acepta ocho tipos sin `perf` | `.github/scripts/pr_policy.py` línea 9 | P1 C |
| H-03 | La identidad se describe como `firebase_uid, email, roles, request_id`; el código usa `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-User-Plan`, `X-User-Email-Verified` y `X-Request-Id` | controladores y filtro del Gateway | P1 B y C |
| H-04 | RFC 7807 y JSON `snake_case` en documentos; el código usa RFC 9457 y `camelCase` | documentos de agente | P1 C |
| H-05 | Personas nombradas como fuente de reglas y hojas de bitácora personales fuera del repositorio | documentos de agente | P1 C (REQ-DOC-11) |
| H-06 | Perfil y Gateway ponen un «documento de reglas del equipo» por encima de su propio documento | encabezado de sus `AGENTS.md` | P1 C |
| H-07 | `TODO` con clave Jira: regla de Perfil y 8 en su código de producción | `AGENTS.md` §6; `git grep TODO` | Regla: P1 B. Código: P2 de Perfil |
| H-08 | «Sin abreviaturas» con ejemplos en español, frente a identificadores en inglés | `CLAUDE.md` de Cuentas y Entrevista | P1 B |
| H-09 | Verificación con `test` y `clean package`, sin la cobertura de `clean verify` | `CLAUDE.md`, constituciones, READMEs | P1 B y C |
| H-10 | Carpetas de spec con tres formas de nombre | `specs/` de cada repositorio | P1 C (REQ-DOC-14) |
| H-11 | Cuatro convenciones de nombre de prueba | `src/test` de cada repositorio | P1 B (D-14) |
| H-12 | `guidelines.md` y los `AGENTS.md` de Perfil y Gateway traen reglas que el estándar no recogía (inyección por constructor con `final`, visibilidad de paquete, `ResponseEntity` explícito, raíz JSON, paginación, guardas de log, sufijos por capa, verbos, antipatrones) | documentos de agente | P1 B (sección 4.1) |
| H-13 | `EMAIL_NOT_VERIFIED`, `PLAN_LIMIT` y `LLM_UNAVAILABLE` se describían como publicados; ningún código fuente los emite | `git grep` en los cuatro repositorios | P1 B (sección 6 del estándar) |
| H-14 | Ningún servicio emite `code`, `requestId` ni `errors[].code` | `git grep` | Cada servicio, en su primera tarea de código: Cuentas CM-36, Perfil CM-271, Entrevista en la pieza de 15 de octubre; el Gateway conserva su formato |
| H-15 | Las reglas comunes que se tomaron de los documentos de cada repositorio no coincidían con el código de ninguno: sufijos `Config` y `JpaAdapter`, servicios de aplicación con visibilidad de paquete, un texto literal del 500 y un estado único para el cuerpo ilegible | Clases de `application.service`, `infrastructure.config` y adaptadores, y manejadores de errores de los cuatro repositorios | P1 B (D-19, D-20 y D-21) |
| H-16 | La constitución declaraba prevalecer sobre todo documento mientras el estándar la ponía cuarta, y su principio de identidad no admitía las rutas sin identidad | Revisión de la pieza B | P1 B (D-17 y D-22) |
| H-17 | La prueba de arquitectura de Cuentas no vigila que `presentation` no importe `infrastructure` ni que `domain` no importe Rabbit ni Google, aunque la sección 3 del estándar lo pide | `LayeredArchitectureTest` de Cuentas; la de Perfil ya vigila ambas reglas | P2-02 (REQ-P2-02-5) |

## 6. Cambios propios de cameia-cuentas

### 6.1 Archivos

| Archivo | Acción | Detalle |
|---|---|---|
| `CLAUDE.md` | Reescribir | Diez secciones (REQ-DOC-01) con el contenido de REQ-CU-01 a REQ-CU-05 |
| `AGENTS.md` | Crear | La línea de REQ-DOC-02 |
| `docs/estandar-backend.md` | Crear | Sección 4.1 |
| `docs/constitution.md` | Reescribir | Principios comunes (4.3) y los dos propios de REQ-CU-06 |
| `docs/errores.md` | Crear | Sección 4.4; REQ-CU-07 |
| `docs/adr/0001-codigo-de-error-y-request-id.md` | Crear | Sección 4.5 |
| `docs/bitacora-ia/README.md` y `01_alinear-estandar-backend_prompt.md` | Crear | Sección 4.6 |
| `guidelines.md` | Eliminar | D-08; lo que tenía de más está en la sección 4.1 |
| `docs/specs/.gitkeep` | Eliminar | Carpeta vacía; las specs viven en `specs/` |
| `README.md` | Modificar | REQ-DOC-09 y REQ-CU-08 |
| `CONTRATO-GATEWAY-CM-14.md` | Modificar | REQ-CU-09 |
| `CONTRIBUTING.md` | Sin cambios | Es el modelo de REQ-DOC-08 |

### 6.2 Requisitos

- **REQ-CU-01 (sección 1 del `CLAUDE.md`).** El `CLAUDE.md` deberá describir el servicio como el dueño de la cuenta local, los planes, las suscripciones, los pagos y los entitlements por `firebaseUid`, y deberá decir que las contraseñas y credenciales permanecen en Firebase y que el servicio no implementa entrevistas ni registra consumo de IA.
- **REQ-CU-02 (sección 2).** El `CLAUDE.md` deberá conservar el árbol de paquetes `tech.cameia.cuentas` (incluida `infrastructure.persistence.mapper`), la regla de dependencias y la prueba `LayeredArchitectureTest` que la vigila; y deberá indicar la pila (Java 21, Spring Boot 4.1.1, Maven Wrapper, PostgreSQL 16, Flyway).
- **REQ-CU-03 (sección 3).** El `CLAUDE.md` deberá conservar las dos capas de confianza (IAM con OIDC en Cloud Run, y VPC con ingreso interno), y deberá describir la identidad de entrada con los encabezados que el Gateway envía: `X-User-Id`, `X-User-Email`, `X-User-Roles` y `X-Request-Id`, más `X-User-Plan` y `X-User-Email-Verified`, que son opcionales. No deberá mencionar `firebase_uid`, `request_id`, `display_name`, `correlation_id` ni `issued_at` como campos de entrada. Deberá conservar las dos rutas que no exigen identidad (`GET /api/v1/users/health` y `POST /api/v1/users`) y la regla de que una ruta nueva no se suma sin spec.
- **REQ-CU-04 (secciones 4 y 5).** El `CLAUDE.md` deberá declarar el contrato de respuesta con la versión en la ruta (`/api/v1/...`), JSON en `camelCase` y errores `ProblemDetail` según RFC 9457 con `errors` por campo, deberá remitir a `docs/errores.md`, y deberá describir la documentación de la API (`/v3/api-docs` y `/swagger-ui.html` en `http://localhost:8081`, gobernadas por `API_DOCUMENTATION_ENABLED`, apagada por defecto y rígidamente apagada con el perfil `prod`). La sección 5 deberá listar las tablas y columnas reales de las migraciones Flyway y la convención de nombres.
- **REQ-CU-05 (secciones 6, 7, 8 y 10).** El `CLAUDE.md` deberá conservar las reglas de seguridad propias: el emulador de Firebase Auth solo en desarrollo local y el arranque rechazado si `FIREBASE_AUTH_EMULATOR_HOST` existe junto a `K_SERVICE` o al perfil `prod`; la firma SHA-256 y la idempotencia de los webhooks de Wompi antes de cambiar una suscripción; y que el webhook de Wompi llega por el Gateway. La sección 8 deberá dar los comandos y las URL locales con el puerto 8081. La sección 10 deberá listar como pendiente la integración con Wompi hasta que el contrato de integración esté aprobado.
- **REQ-CU-06 (constitución).** La constitución deberá incluir además: «16. **Pagos:** la firma SHA-256 y la idempotencia de Wompi se verifican antes de cambiar una suscripción o un entitlement. → pruebas de firma, de idempotencia y de reintentos» y «17. **Ingreso de terceros:** los webhooks de terceros llegan por el Gateway, nunca directo al servicio. → configuración de rutas del Gateway».
- **REQ-CU-07 (errores).** `docs/errores.md` deberá listar en «Respuestas sin código» cada estado que produce `BusinessExceptionHandler` en `origin/develop`, leído del manejador, y deberá decir que Cuentas aún no emite `code`. En el mismo apartado deberá anotar, con su destino, cada respuesta publicada que difiere del estándar (D-21): el 422 ante un cuerpo ilegible, el texto del 500, la `IllegalArgumentException` genérica que llega con su mensaje y la cuenta bloqueada o anonimizada que termina en 500 (H-C8 y H-C9).
- **REQ-CU-08 (README).** El `README.md` deberá reemplazar sus líneas de contribución (rama `<tipo>/CM-NNN`, tipos con `perf`) por un enlace a `CONTRIBUTING.md`, y deberá usar `./mvnw.cmd clean verify` como comando de verificación.
- **REQ-CU-09 (contrato con el Gateway).** `CONTRATO-GATEWAY-CM-14.md` deberá citar RFC 9457 donde cita RFC 7807 y deberá nombrar al rol (Product Owner) en la línea «Responsable de llevarlo al Gateway»; el resto del documento no se modifica.
- **REQ-CU-10.** El `CLAUDE.md` no deberá conservar: la sección de metodología, la de ambigüedades, la de límite de tamaño, las de nombrado, idioma y documentación de código, la de bitácora por hoja personal, la de título y plantilla de commit con `[IA-ASISTIDO]`, ni el ejemplo de commit `CM-105: …`; todo eso vive ahora en el estándar y en la sección 9.

### 6.3 Hallazgos propios y destino

| ID | Hallazgo | Destino |
|---|---|---|
| H-C1 | `README.md` indica ramas `<tipo>/CM-NNN` y tipos con `perf`, y su `CLAUDE.md` indica `CM-<n>`: el repositorio se contradice a sí mismo | P1 C |
| H-C2 | El `CLAUDE.md` dice que el commit lleva `[IA-ASISTIDO]` y su ejemplo de idioma usa `CM-105: …` | P1 C |
| H-C3 | Carpeta `docs/specs/` vacía junto a `specs/` | P1 B |
| H-C4 | `CONTRATO-GATEWAY-CM-14.md` cita RFC 7807 | P1 C |
| H-C5 | Las pruebas de integración se llaman `*Test` y las ejecuta Surefire con las unitarias | P2-02 |
| H-C6 | Cuentas no emite `code` ni `requestId` | Primera tarea de código de Cuentas; PD-08 respondida: `requestId` desde CM-36 (PR 1A) |
| H-C7 | `ActivateAccountService.activate` es `@Transactional` y consulta Firebase dentro de la transacción, contra la sección 3 del estándar | CM-179, bloque 1 (decisión D3 de su spec): la activación deja de ser `@Transactional` y de consultar Firebase, y el adaptador traduce el conflicto de versión a una excepción de dominio |
| H-C8 | `BusinessExceptionHandler` responde 422 ante un cuerpo ilegible (el mapa base del estándar dice 400), su 500 lleva «No pudimos completar la operación. Inténtalo de nuevo en unos minutos» y las respuestas de error no declaran `charset=UTF-8` | CM-36, PR 1A: el 422 se conserva (decisión D4 de su spec) con el código `REQUEST_BODY_INVALID_FORMAT`; el 500 pasa a `INTERNAL_ERROR` con «Ocurrió un error. Inténtalo de nuevo.» (REQ-RV-04); el `charset`, en el mismo PR 1A |
| H-C9 | `BusinessExceptionHandler` traduce toda `IllegalArgumentException` a 422 con el mensaje de la excepción, y la activación de una cuenta bloqueada o anonimizada (`IllegalStateException`) termina en 500 | La `IllegalArgumentException`: CM-36, PR 1A (código `REQUEST_INVALID_VALUE` de respaldo), y deja de emitirse en el bloque 6, cuando cada objeto de valor tiene su excepción. La cuenta bloqueada o anonimizada: CM-179, bloque 1 (REQ-VC-05: 403 `ACCOUNT_DISABLED`; REQ-VC-06: 404 `ACCOUNT_NOT_FOUND`) |

## 7. Parte 2 en cameia-cuentas

Una pieza por PR, del 13 al 23 de octubre de 2026, después de los bloques de historias del cronograma. Cada PR cumple REQ-DOC-15 (título `CM-283 | tipo(scope): resultado [IA-ASISTIDO]`, no más de 1000 líneas y plantilla de ocho campos). Los requisitos de cada pieza van a continuación y los valores propios de este repositorio, al final.

#### P2-02 · Failsafe para separar las pruebas de integración (13 de octubre)

- **REQ-P2-02-1.** El `pom.xml` deberá declarar `maven-failsafe-plugin`, con la versión que gestiona `spring-boot-starter-parent`, y sus objetivos `integration-test` y `verify`.
- **REQ-P2-02-2.** Surefire deberá ejecutar las clases `*Test` y Failsafe las clases `*IT`.
- **REQ-P2-02-3.** Cuando una clase de prueba arranque el contexto completo de Spring (`@SpringBootTest`) o use Testcontainers, deberá llamarse `*IT`; las existentes que lo cumplan se renombran en esta pieza. La lista sale de `git grep -lE "@SpringBootTest|@Testcontainers" -- src/test`.
- **REQ-P2-02-4.** JaCoCo deberá medir las dos fases: la cobertura de líneas y de ramas del informe de `clean verify` no deberá ser menor que la medida en el SHA de partida.
- **REQ-P2-02-5.** En los servicios con las cuatro capas, la prueba de arquitectura deberá vigilar además que `presentation` no importa `infrastructure` y que `domain` no importa Rabbit (`org.springframework.amqp..`, `com.rabbitmq..`) ni Google (`com.google..`). Si una de esas reglas falla con el código actual, se corrige el código en la misma pieza cuando el cambio no pasa de 1000 líneas; si pasa, se detiene la pieza y se pregunta a Backend. Ninguna regla se desactiva ni se excluye para pasar.
- **Verificación.** `./mvnw.cmd clean verify` en verde; la salida lista `maven-surefire-plugin:test` y `maven-failsafe-plugin:integration-test`; la cobertura de antes y de después se reporta con números; `Skipped: 0` en ambas fases.
- **Trampa conocida.** Una clase `*IT` no la ejecuta Surefire: sin Failsafe quedaría sin correr, como hoy pasa con la prueba `*IT` de Perfil.

#### P2-03 · Plugin de formato (decisión el 13 de octubre; aplicación el 21 de octubre)

- **REQ-P2-03-1.** Cuando Backend apruebe un plugin de formato (PD-01), el `pom.xml` deberá declararlo con una ejecución que falle `clean verify` si el código no cumple el formato de `.editorconfig` (4 espacios en Java, LF), y el código existente se formateará en un commit aparte que no cambie comportamiento.
- **REQ-P2-03-2.** Si Backend descarta el plugin, esta pieza se cierra sin cambios y la decisión se registra en el apartado «Pendientes» del `CLAUDE.md`.
- **Verificación.** `./mvnw.cmd clean verify` en verde; una línea con formato incorrecto hace fallar la verificación; el diff del formateo se revisa por separado.
- **Bloqueada por PD-01.**

#### P2-05 · `requestId` en el `MDC` y registros sin datos personales (15 de octubre)

- **REQ-P2-05-1.** Cuando llegue una petición, un filtro de máxima precedencia deberá leer `X-Request-Id`; si falta o no cumple el formato de PD-02, deberá generar uno nuevo; deberá poner el valor en el `MDC` con la clave de PD-02, devolverlo en el encabezado `X-Request-Id` de la respuesta y limpiar el `MDC` al terminar la petición, también si falla.
- **REQ-P2-05-2.** El patrón de log (`logging.pattern.level`) deberá incluir el `requestId` en cada línea.
- **REQ-P2-05-3.** Ningún registro de log deberá contener contraseñas, tokens, correos, nombres, CV ni cuerpos de petición; los registros actuales que lo incumplan se corrigen en esta pieza. La lista sale de `git grep -nE "log(ger)?\.(trace|debug|info|warn|error)\(" -- src/main`, revisada línea por línea.
- **Pruebas por servicio:** (a) con `X-Request-Id: abc-123`, la respuesta lo devuelve y el log lo contiene; (b) sin encabezado, se genera un UUID y se devuelve; (c) con un valor de 65 caracteres o con espacios, se genera uno nuevo; (d) tras la petición el `MDC` queda vacío; (e) cuando la petición falla, el `MDC` también se limpia.
- **Bloqueada por PD-02**.

#### P2-07 · `.env.example`, perfiles y propiedades tipadas (19 de octubre)

- **REQ-P2-07-1.** `.env.example` deberá listar cada variable que lee la aplicación o el `docker-compose.yml`, con un comentario, sin valores sensibles y en este orden: puerto, perfil, documentación de la API, base de datos, mensajería, integraciones.
- **REQ-P2-07-2.** La configuración propia del servicio deberá enlazarse con `@ConfigurationProperties` y `@Validated`, de modo que la aplicación no arranque si falta o es inválida. El Gateway conserva `@Value` donde su `CLAUDE.md` lo justifica.
- **REQ-P2-07-3.** La aplicación deberá tener un perfil `prod` que fije `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false` con valor rígido, de modo que ninguna variable de entorno los encienda.
- **REQ-P2-07-4.** Cuando el perfil no sea `prod`, la documentación de la API deberá publicarse si y solo si `API_DOCUMENTATION_ENABLED=true`, con valor por defecto `false`; el `docker-compose.yml` local la enciende.
- **REQ-P2-07-5.** El puerto de escucha deberá resolverse como `${PORT:${SERVER_PORT:<puerto>}}`, el `Dockerfile` deberá declarar `ENV SERVER_PORT=<puerto>` y `EXPOSE <puerto>`, y el `docker-compose.yml` deberá publicar `${SERVER_PORT:-<puerto>}:${SERVER_PORT:-<puerto>}`. Puertos: gateway 8080, cuentas 8081, perfil 8082, entrevista 8083.
- **REQ-P2-07-6.** Antes de abrir el PR, Backend deberá comprobar que las pruebas de arranque, de bandera y de resolución de puerto del repositorio pasan; que `docker compose config` valida; y que el despliegue no cambia de comportamiento (en Cloud Run manda la variable `PORT` que inyecta la plataforma). Lo que no se pueda ejecutar se reporta.
- **Casos borde.** `API_DOCUMENTATION_ENABLED` ausente, vacía, `true`, `false`, `TRUE`, `1` y `yes`; con perfil `prod` y bandera `true` (la documentación sigue cerrada); `PORT` y `SERVER_PORT` ausentes, solo una definida y ambas definidas.
- **Aviso a DevOps solo si hace falta:** únicamente el cambio de nombre del perfil de Entrevista (pedido 14).

#### P2-10 · Umbral de cobertura de JaCoCo (22 de octubre)

- **REQ-P2-10-1.** Cuando se mida la cobertura el 22 de octubre con `clean verify`, el `pom.xml` deberá declarar la ejecución `check` de JaCoCo con una regla de líneas y otra de ramas (`COVEREDRATIO`), cuyo mínimo sea la cobertura medida ese día redondeada según PD-06, y nunca menor que la cobertura del SHA de partida.
- **REQ-P2-10-2.** Cuando la cobertura baje del mínimo, `clean verify` deberá fallar con un mensaje que nombre la regla y el valor.
- **Verificación.** Se reportan los valores medidos de líneas y de ramas y el mínimo fijado; una prueba temporal de que el build falla al subir el mínimo por encima de lo medido (no se commitea).
- **Bloqueada por PD-06.** Se informa a DevOps del valor fijado en cada repositorio, sin pedido, porque su CI ejecuta `verify`.

#### P2-11 · Revisión de los PR de Dependabot (16 y 23 de octubre)

- **REQ-P2-11-1.** Cuando haya PR de Dependabot abiertos que modifiquen `pom.xml`, Backend deberá entregar un informe con, por PR: la dependencia, la versión anterior y la nueva, el tipo de salto (parche, menor o mayor), si es compatible con Spring Boot 4.1.1 y el resultado de `clean verify` sobre una copia de la rama.
- **REQ-P2-11-2.** Ningún PR de Dependabot se integra por la IA: la integración la decide y la hace una persona.
- **Verificación.** El informe cubre todos los PR abiertos de ese día; los que no se pudieron probar se marcan `BLOQUEADO` con la razón.

#### P2-14 · Verificación final (23 de octubre)

- **REQ-P2-14-1.** Cuando termine el 23 de octubre, cada servicio deberá cumplir los casos V-01 a V-13 de la sección 8 y los de cada pieza de esta sección; lo que no cumpla se lista con su responsable y su tarea en Jira.

### Valores propios de cameia-cuentas

| Pieza | Aplica | Valor propio |
|---|---|---|
| P2-02 Failsafe | Sí | Renombrar a `*IT` las pruebas que usan `@Testcontainers` o `@SpringBootTest` (por ejemplo `AccountRegistrationEndToEndTest`, `AccountRepositoryAdapterTest` y `CuentaSchemaMigrationTest`; la lista completa sale del `git grep` de REQ-P2-02-3) |
| P2-03 Plugin de formato | Condicionada a PD-01 | — |
| P2-05 `requestId` | Sí | Filtro servlet; el cuerpo de error ya lleva `requestId` desde CM-36 (PD-08); P2-05 agrega el filtro y el `MDC` y reutiliza ese valor |
| P2-07 Configuración | Sí | Puerto 8081 (ya cumple); `prod` y bandera `API_DOCUMENTATION_ENABLED` (ya cumplen: solo se verifican y se ordena `.env.example`) |
| P2-10 JaCoCo | Sí | — |
| P2-11 Dependabot | Sí | — |
| P2-14 Verificación | Sí | — |

**PD-08 (respondida por Paula el 6-oct-2026):** el cuerpo de error de Cuentas lleva `requestId` desde la primera tarea de código (CM-36, PR 1A), tomado de `X-Request-Id` o generado; el filtro de P2-05 llega después y no cambia el contrato.

## 8. Casos de verificación

Cada caso se ejecuta con salida real y se reporta en el PR. Los comandos son de Git Bash sobre la raíz del repositorio.

| ID | Qué comprueba | Comando | Resultado esperado |
|---|---|---|---|
| V-01 | `docs/estandar-backend.md` idéntico en los cuatro (REQ-DOC-03) | `git show HEAD:docs/estandar-backend.md \| sha256sum` en cada repositorio | La misma suma en los cuatro |
| V-02 | `CONTRIBUTING.md` idéntico en los cuatro (REQ-DOC-08) | `git show HEAD:CONTRIBUTING.md \| sha256sum` en cada repositorio | La misma suma en los cuatro |
| V-03 | Diez secciones del `CLAUDE.md` (REQ-DOC-01) | `grep -nE '^## ' CLAUDE.md` | Exactamente 10 líneas, con los títulos del REQ-DOC-01 |
| V-04 | `AGENTS.md` de una línea (REQ-DOC-02) | `cat AGENTS.md` | La línea exacta del REQ-DOC-02 |
| V-05 | Sin nombres de personas, hojas personales ni reglas obsoletas (REQ-DOC-11) | `git grep -nEi "Sof[ií]a\|Vela\|Paula\|Bitacora_Codigo\|Entregables\|CA-[0-9N]\|RFC ?7807\|<tipo>/CM\|snake_case" -- "*.md" ":!specs"` | Sin coincidencias, salvo `snake_case` de tablas y columnas de base de datos |
| V-06 | Sin datos que envejecen (REQ-DOC-11) | `git grep -nEi "[0-9]+ pruebas\|hoy son\|27/27\|adoptad[oa] el" -- "*.md" ":!specs"` | Sin coincidencias |
| V-07 | Enlaces relativos válidos (REQ-DOC-12 y REQ-DOC-13) | Comprobación de enlaces de las tarjetas de verificación (T-08, T-13 y T-15) | Cero enlaces rotos |
| V-08 | Tipo `perf` ausente de los documentos (H-02) | `git grep -nE "perf" -- "*.md" ":!specs"` | Sin coincidencias de tipo de commit o de PR |
| V-09 | Título y rama válidos para el validador de PR | `python .github/scripts/pr_policy.py <json>` con el título del PR y la rama `CM-283-alinear-estandar` | `errors` vacío |
| V-10 | Tamaño del PR (REQ-DOC-15) | `git diff --shortstat origin/develop` | Agregadas + eliminadas ≤ 1000 |
| V-11 | Ningún documento enlaza uno retirado (REQ-DOC-13) | `git grep -nE "guidelines\.md\|AMBIGUIDADES\.md\|reglas-codigo-backend\|handoff-implementacion\|docs/sdd\.md" -- "*.md" ":!specs"` | Sin coincidencias; las specs no se reescriben porque registran las decisiones de su tarea |
| V-12 | Afirmaciones del `CLAUDE.md` comprobadas contra el código | Para cada puerto, perfil, ruta, tabla y componente del `CLAUDE.md`, el comando `git grep` de la tarjeta T-05 | Cada afirmación tiene su línea de código |
| V-13 | La Parte 1 no cambia código | `git diff --name-only origin/develop` | Solo archivos `*.md`, `CONTRIBUTING.md`, `docs/**` y `specs/**` |

Casos de la Parte 2: los de cada pieza en la sección 7.

**Parte 1 terminada** = V-01 a V-13 en verde con su salida real en el PR de cada repositorio. **Secciones comunes:** las secciones que repiten las cuatro specs se cambian en las cuatro en el mismo bloque de trabajo; V-01 compara el estándar, no la redacción de las specs.

**V-14 (solo lectura, no condiciona la Parte 1).** Con el servicio en local, `curl -si` a una ruta que responda un error (por ejemplo, un 404 o un 422) y anotar el `Content-Type`. Si no declara `charset=UTF-8` (ASVS 4.1.1), el hallazgo se registra con su evidencia y se corrige en la primera tarea de código de este servicio, no en CM-283.

## 9. Fuera de alcance

- Cambios de lógica de negocio y de contrato público (los cambios aditivos de error se adoptan en las tareas de código de cada servicio, no aquí).
- El workflow de despliegue, los recursos de Cloud Run y todo lo de `/.github/`: son de DevOps y se piden por separado. La única petición que esta spec genera es la de la pieza P2-07 de Entrevista.
- `cameia-web`, `cameia-infra` y `cameia-metricas`.
- Renombrar paquetes, carpetas de spec o pruebas existentes (D-11, D-14 y REQ-DOC-14).
- Llevar la cobertura de Perfil al 90 % (la Parte 2 la lleva a más de 70 %).

## 10. Preguntas respondidas y pendientes

### Respondidas (Backend, 5 de octubre de 2026)

| # | Pregunta | Respuesta |
|---|---|---|
| Q-01 | Regla de commit en los cuatro `CONTRIBUTING.md` | Los cuatro iguales, con la clave Jira (D-01) |
| Q-02 | Dónde vive la spec | Una completa en cada repositorio (D-02) |
| Q-03 | Cómo trabajar sin tocar las ramas abiertas | `git worktree` (D-03) |
| Q-04 | Contenido de `docs/errores.md` | Solo los códigos que el código emite hoy (D-04) |
| Q-05 | ADR del código de error y del `requestId` | En la Parte 1 (D-05) |
| Q-06 | `[IA-ASISTIDO]` en el commit | Solo en el título del PR (D-06) |
| Q-07 | Documento de reglas del equipo de Perfil | Se retira; una sola fuente de verdad (D-07) |
| Q-08 | `guidelines.md` | Se retira de Cuentas y Entrevista y lo que tenga de más pasa al estándar (D-08) |
| Q-09 | Perfil de producción, bandera de Swagger y puertos | `prod`, `API_DOCUMENTATION_ENABLED` y puerto único en los cuatro; sin romper nada y avisando a DevOps solo lo necesario (D-09) |
| Q-10 | ArchUnit en Entrevista | Sí, en la Parte 2 (D-10) |
| Q-11 | Paquete base de Perfil | Se conserva y se documenta (D-11) |

### Pendientes

| ID | Qué falta decidir | Responsable | Fecha | Recomendación | Bloquea |
|---|---|---|---|---|---|
| PD-01 | Si se aprueba el plugin de formato (dependencia nueva) y cuál | Backend | 13 oct | Decidir con una comparación de alternativas reales y su compatibilidad con Spring Boot 4.1.1 | P2-03 |
| PD-02 | Formato del `X-Request-Id` entrante y nombre de la clave del `MDC` | Backend | 15 oct | Aceptar `^[A-Za-z0-9._-]{1,64}$`; si no cumple, generar un UUID v4; clave `requestId` | P2-05 |
| PD-06 | Redondeo del umbral de JaCoCo: al entero inferior o a dos decimales | Backend | 22 oct | Al entero inferior | P2-10 |
