# Spec — CM-179-VerificacionCorreo: verificación de correo en Cuentas y en el Gateway

- **Tarea:** CM-179 · Subtarea «HU-1.2 – Backend: verificación de correo» · padre CM-178 «HU-1.2 Verificación de Correo Electrónico» (subtareas CM-179 Backend, CM-180 Frontend, CM-255 configuración de Firebase) · Sprint 2 · responsable: Paula Andrea Muñoz Delgado
- **Repositorios:** `cameia-cuentas` (esta spec, `908112c`) y `cameia-gateway` (bloque 3, `4c79b6e`; su spec corta está en `cameia-gateway/specs/CM-179-VerificacionCorreo/` y remite a esta)
- **Backlog vigente:** `05102026_01_Backlog.xlsx`, hoja `HE-01`, HU-1.2 (CA-1.2.1 a 1.2.14), 9 h para toda la HU
- **Estado:** spec de **toda la tarea**; pendiente de aprobación de Paula. Sus preguntas (4 a 8 y 11 a 13) están respondidas desde el 6-oct-2026; las abiertas son de Vela (1, 2, 3, 10) y de DevOps (9). Plan y tarjetas hechos para el bloque 1 (Cuentas) y el bloque 3 (Gateway); los bloques 2 y 4 se planean antes de ejecutarlos.
- **Atributos de calidad que toca:** seguridad (token estricto, bloqueo en el Gateway, borrado de datos personales), fiabilidad (idempotencia, concurrencia, un fallo no detiene a los demás), observabilidad (resumen de la purga), compatibilidad de contrato (códigos y textos nuevos), privacidad (sin datos personales en logs).

## 1. Contexto y objetivo

Una cuenta registrada queda `PENDING_VERIFICATION` y no se puede usar. Al abrir el enlace del correo solo se verifica en Firebase (`applyActionCode`, sin llamada a Cuentas). La cuenta pasa a `ACTIVE` con Plan Free **en el primer inicio de sesión posterior** (P-01 A): `cameia-web` llama a `POST /api/v1/users/me/verification`; si falla, el ingreso no se bloquea y se reintenta en el siguiente (CA-1.2.14). Una cuenta que no se verifica en 7 días se elimina (PO, 3-oct-2026).

**Objetivo de Backend**, en cuatro piezas:

1. **Activación robusta (Cuentas):** el estado de verificación se toma **solo** del ID Token que propaga el Gateway; ningún estado de cuenta produce un 500; la activación es idempotente y segura ante concurrencia.
2. **Purga diaria (Cuentas):** elimina las cuentas sin verificar con más de 168 h, salvo las que Firebase ya tiene verificadas, que activa.
3. **Bloqueo en el Gateway:** todo token con `email_verified=false` recibe 403 en rutas privadas, excepto la ruta de verificación.
4. **Modo de arranque de tarea (Cuentas):** la purga corre siempre como Cloud Run Job disparado por Cloud Scheduler, sin ruta HTTP.

## 2. Trazabilidad de los 14 CA

| CA | Dueño | Estado en `908112c` | Brecha | Bloque |
|---|---|---|---|---|
| 1.2.1 verificación exitosa | F (Firebase) | — | Nada de Backend | — |
| 1.2.2 enlace vencido o usado | F | — | V-04 (respuesta de `applyActionCode`) | verificación, bloque 2 |
| 1.2.3 reenvío | F | — | — | — |
| 1.2.4 sincronización repetida | F y B | 200 sin escribir si ya está `ACTIVE` | Falta la prueba con base real que compare `fecha_actualizacion` antes y después | 1 |
| 1.2.5, 1.2.7 | F | — | — | — |
| 1.2.6 estado tomado del token | B | **Defecto:** con `X-User-Email-Verified: false` o ausente, el servicio consulta a Firebase y puede activar | 403 estricto, sin consulta | 1 |
| 1.2.8 correo desde cameia.app | DevOps | — | CM-255 | — |
| 1.2.9 menos de 7 días | B | No existe | Purga | 2 |
| 1.2.10 más de 7 días | B | No existe | Purga | 2 y 4 |
| 1.2.11 correo libre tras la purga | F y B | No aplica hoy | Registro 201 tras la purga | 2 |
| 1.2.12 registrarse de nuevo | F y B | 409 | Es CA-1.1.30 | CM-251 |
| 1.2.13 403 del Gateway | B (Gateway) | **Defecto:** el Gateway propaga `false` y no rechaza | 403 con cuerpo y charset | 3 |
| 1.2.14 activación en el primer ingreso | F y B | Funciona con `true` | Robustez (sección 4) | 1 |

## 3. Defectos que ya hay en el código

| # | Defecto | Evidencia | Destino |
|---|---|---|---|
| 1 | `Account.activate()` lanza `IllegalStateException` si la cuenta está `DISABLED` o `ANONYMIZED` y nadie la maneja: **500** | `domain/model/Account.java` (método `activate`) y `BusinessExceptionHandler` | Bloque 1 |
| 2 | Con el encabezado en `false` o ausente se consulta a Firebase (`estaVerificado`) y se puede activar contra el token | `ActivateAccountService.java:87-92`; la prueba `noSeFiaDeUnEncabezadoEnFalsoSiFirebaseDiceQueSiEstaVerificado` lo fija | Bloque 1 |
| 3 | `isEmailVerified` lanza `IllegalStateException` si el usuario no existe o Firebase falla: 500 | `FirebaseUserDirectoryAdapter.java` | Bloque 1 (se deja de usar en la activación) y bloque 2 (nuevo método tipado) |
| 4 | `X-User-Id` sin límite de longitud ni validación de contenido (la columna es `VARCHAR(128)`) | `AccountActivationController.java:44` | Bloque 1 |
| 5 | Un valor de `X-User-Email-Verified` que no sea `true`/`false` (por ejemplo `abc`) produce un error de conversión de tipo → 500 | `@RequestHeader(...) Boolean` lanza `MethodArgumentTypeMismatchException`; `BusinessExceptionHandler` no la atiende (no es `ServletRequestBindingException` ni `IllegalArgumentException`) y cae en `@ExceptionHandler(Exception.class)` → 500. La primera prueba del bloque 1 lo confirma antes de corregir | Bloque 1 |
| 6 | Dos activaciones simultáneas: `ObjectOptimisticLockingFailureException` sin manejador → 500 (inferido; se confirma con prueba) | `@Transactional` en `ActivateAccountService.activate` | Bloque 1 |
| 7 | El Gateway no rechaza `email_verified=false` | `FirebaseAuthGlobalFilter` y `AGENTS.md` del Gateway (GW-TBD-17) | Bloque 3 |

## 4. Requisitos funcionales (EARS)

### Bloque 1 — activación robusta (Cuentas)

- **REQ-VC-01 (CA-1.2.6).** Cuando llegue `POST /api/v1/users/me/verification` con `X-User-Email-Verified` distinto de `true` (`false`, ausente, vacío o cualquier otro valor; la comparación es exacta y distingue mayúsculas, así que `TRUE` también se rechaza: el Gateway siempre escribe `true` en minúsculas), el servicio debe responder 403 con `code` `EMAIL_NOT_VERIFIED`, no debe activar la cuenta, no debe consultar a Firebase y debe ignorar el cuerpo de la petición.
- **REQ-VC-02 (CA-1.2.14).** Cuando llegue la petición con `X-User-Email-Verified: true` y la cuenta esté `PENDING_VERIFICATION`, el servicio debe pasarla a `ACTIVE`, actualizar `fecha_actualizacion`, conservar el plan y responder 200 con `{id, status:"ACTIVE"}`.
- **REQ-VC-03 (CA-1.2.4).** Cuando la cuenta ya esté `ACTIVE`, el servicio debe responder 200 con `{id, status:"ACTIVE"}` sin escribir: `fecha_actualizacion` y `version` no cambian.
- **REQ-VC-04.** Cuando `X-User-Id` no tenga cuenta local, el servicio debe responder 404 con `code` `ACCOUNT_NOT_FOUND`.
- **REQ-VC-05.** Cuando la cuenta esté `DISABLED`, el servicio debe responder 403 con `code` `ACCOUNT_DISABLED` y el mensaje «Esta cuenta está deshabilitada.» (texto de CA-1.3.11; **pregunta 2**), sin cambiar la cuenta.
- **REQ-VC-06.** Cuando la cuenta esté `ANONYMIZED`, el servicio debe responder lo mismo que si no existiera (404 `ACCOUNT_NOT_FOUND`), para no revelar que existió (**pregunta 2**), sin cambiar la cuenta.
- **REQ-VC-07.** Cuando falte `X-User-Id`, el servicio debe responder 400 con `code` `IDENTITY_REQUIRED`; cuando esté vacío, solo con espacios o supere 128 caracteres, 400 con `code` `IDENTITY_INVALID_VALUE`; ninguna de las dos debe consultar la base ni Firebase.
- **REQ-VC-08.** Cuando dos activaciones de la misma cuenta lleguen a la vez, la cuenta debe escribirse una sola vez con efecto y ninguna debe responder 500. La que gana responde 200. La que pierde la carrera de versiones debe volver a leer la cuenta **una vez**: si la relectura la encuentra `ACTIVE` (el caso esperado), responde 200 con `{id, status:"ACTIVE"}`; si la encuentra en cualquier otro estado (caso muy improbable: la otra escritura no llegó a confirmarse o el estado cambió entre medias), responde 409 con `code` `ACCOUNT_CONFLICT` y el mensaje «La cuenta se está actualizando. Inténtalo de nuevo.». La prueba de dos activaciones simultáneas espera 200 y 200.
- **REQ-VC-09.** Mientras Firebase no responda, la activación con `X-User-Email-Verified: true` debe seguir funcionando (el servicio no consulta a Firebase).
- **REQ-VC-10.** El servicio debe registrar la activación en `INFO` y cada rechazo en `WARN` con `code` y `firebaseUid`, sin correo ni otros datos personales.
- **REQ-VC-11.** La documentación OpenAPI de la ruta debe describir las respuestas 200, 400, 403, 404, 409 y 500 con sus códigos y el significado del encabezado.

### Bloque 2 — purga diaria (Cuentas)

- **REQ-VC-20.** El servicio de purga debe ejecutarse una vez por invocación y no depender de la hora de reloj: la hora (03:00 de Colombia) la fija quien lo dispara.
- **REQ-VC-21 (CA-1.2.9, 1.2.10).** Cuando se ejecute, el servicio debe seleccionar las cuentas con estado `PENDING_VERIFICATION` y `fecha_creacion < ahora(UTC) − 168 h` (comparación estricta). Una cuenta de 167 h 59 min o de **exactamente 168 h** se conserva; una de 168 h 1 min es candidata; las cuentas `ACTIVE`, `DISABLED` y `ANONYMIZED` no se tocan.
- **REQ-VC-22.** Para cada candidata, el servicio debe consultar primero a Firebase. Si el correo figura verificado, debe activar la cuenta y no eliminarla. Si no está verificado, debe eliminar **primero la credencial** y **después la fila**. Si la credencial ya no existe, debe eliminar solo la fila.
- **REQ-VC-23.** Cuando se elimine una credencial que Firebase ya no tiene, el servicio debe tratarlo como éxito (idempotencia).
- **REQ-VC-24.** Cuando falle el procesamiento de una cuenta, el servicio debe contarla como fallida, registrarla y seguir con las demás; la cuenta se reintenta en la siguiente ejecución. Si falla la eliminación de la fila tras borrar la credencial, la siguiente ejecución termina (REQ-VC-22, tercer caso).
- **REQ-VC-25.** El servicio no debe publicar ningún evento ni escribir datos personales en el log (solo `firebaseUid`); al terminar debe registrar en `INFO` un resumen con las cantidades revisadas, eliminadas, activadas y fallidas, y si quedaron candidatas sin procesar.
- **REQ-VC-26.** El servicio debe procesar como máximo **1000** candidatas por ejecución, de la más antigua a la más nueva; si hay más, el resumen lo indica y las demás se procesan en la siguiente (**pregunta 6**).
- **REQ-VC-27 (CA-1.2.11).** Después de eliminar la credencial y la fila, un registro con ese correo debe crear una cuenta nueva (201), sin «Ese correo ya tiene una cuenta.».
- **REQ-VC-28.** Cuando no haya candidatas, el servicio debe terminar con un resumen en ceros y sin error.
- **REQ-VC-29.** La consulta de candidatas debe usar el índice parcial `ix_cuenta_pendiente_fecha_creacion` sobre `fecha_creacion` donde `estado = 'PENDING_VERIFICATION'` (migración nueva; **pregunta 7**).

### Bloque 3 — 403 del Gateway (CA-1.2.13)

- **REQ-VC-30.** Cuando una petición a una ruta privada traiga un ID Token válido cuyo claim `email_verified` sea `false`, el Gateway debe responder 403 con el cuerpo `{"code":"EMAIL_NOT_VERIFIED","message":"Tu correo aún no está verificado."}` y `Content-Type: application/json;charset=UTF-8`, sin reenviar la petición a ningún microservicio.
- **REQ-VC-31.** `POST /api/v1/users/me/verification` queda exceptuada: con `email_verified=false` debe reenviarse.
- **REQ-VC-32.** Cuando el token no traiga el claim `email_verified`, el Gateway no debe bloquear (se propaga sin la cabecera, como hoy).
- **REQ-VC-33.** Cuando `email_verified` sea `true`, el Gateway debe reenviar con la cabecera `X-User-Email-Verified: true`.
- **REQ-VC-34.** Las rutas públicas (registro y webhook) no se ven afectadas; una cabecera `X-User-Email-Verified` enviada por el cliente se sigue ignorando y reemplazando.
- **REQ-VC-35.** El Gateway debe registrar el rechazo en `WARN` con el `X-Request-Id`, sin token, correo ni uid.
- **REQ-VC-36.** La documentación `AGENTS.md` del Gateway debe cerrar GW-TBD-17 y registrar que GW-TBD-26 («propagar») queda sustituida por esta regla (**pregunta 1**).

### Bloque 4 — modo de arranque de tarea (Cuentas)

- **REQ-VC-40.** Cuando se arranque la aplicación con el perfil `purge-job`, debe iniciar sin servidor web, ejecutar el servicio de purga una vez, registrar el resumen y terminar.
- **REQ-VC-41.** Mientras termine con éxito (incluidas las cuentas fallidas que se reintentan después), el proceso debe salir con código 0; si no pudo ni empezar (configuración inválida, base de datos inaccesible), debe salir con un código distinto de 0 para que el Job reintente.
- **REQ-VC-42.** El servicio no debe exponer ninguna ruta HTTP nueva, no debe usar `@Scheduled` y no debe cambiar el contrato de entrada del servicio.
- **REQ-VC-43.** El perfil `purge-job` debe estar documentado (`CLAUDE.md` del repo y `.env.example` si hay variables nuevas) con el comando exacto de arranque y la variable que lo activa.

## 5. Errores y catálogo

Se apoya en `ErrorCode` y el formato de CM-36 (PR 1A). Códigos de esta tarea:

| Código | HTTP | Mensaje | Origen | Bloque |
|---|---|---|---|---|
| `EMAIL_NOT_VERIFIED` | 403 | «Tu correo aún no está verificado.» (el mismo del Gateway; hoy dice «Primero debes verificar tu correo con el enlace que te enviamos»; **pregunta 3**) | token sin verificar | 1 |
| `ACCOUNT_NOT_FOUND` | 404 | «No encontramos una cuenta para este usuario» (sin cambios) | sin cuenta o anonimizada | 1 |
| `ACCOUNT_DISABLED` | 403 | «Esta cuenta está deshabilitada.» | cuenta bloqueada | 1 |
| `ACCOUNT_CONFLICT` | 409 | «La cuenta se está actualizando. Inténtalo de nuevo.» | pérdida de la carrera de versiones | 1 |
| `IDENTITY_REQUIRED` | 400 | (sin cambios de CM-36 1A) | falta `X-User-Id` | 1 |
| `IDENTITY_INVALID_VALUE` | 400 | «La identidad de la petición no es válida.» | `X-User-Id` vacío o demasiado largo | 1 |
| `EMAIL_NOT_VERIFIED` (Gateway) | 403 | «Tu correo aún no está verificado.» | `email_verified=false` | 3 |

Causas del vocabulario cerrado del estándar: `NOT_VERIFIED`, `NOT_FOUND`, `DISABLED`, `CONFLICT`, `REQUIRED`, `INVALID_VALUE`. Un código ↔ un estado ↔ un mensaje ↔ una excepción ↔ una prueba.

## 6. Contrato

- `POST /api/v1/users/me/verification`: sin cuerpo (cualquier cuerpo se ignora). Respuesta 200 `{id, status}` sin cambios; códigos nuevos 400 (`IDENTITY_INVALID_VALUE`), 403 (`ACCOUNT_DISABLED`), 409 (`ACCOUNT_CONFLICT`); el 403 `EMAIL_NOT_VERIFIED` ya existía.
- Consumidor: `cameia-web` (llama tras el ingreso; si falla, reintenta en el siguiente: CA-1.2.14). El 403 de Cuentas ya no ocurre con tokens verificados; con tokens sin verificar el Gateway responde antes.
- Cambia un texto (403 `EMAIL_NOT_VERIFIED`) y se agregan códigos: aditivo; se avisa a Frontend.

## 7. Datos

- **Migración nueva** (bloque 2): índice parcial `ix_cuenta_pendiente_fecha_creacion ON microcuentas.cuenta (fecha_creacion) WHERE estado = 'PENDING_VERIFICATION'`. Justificación: filtro diario fijo sobre una tabla que crece con cada registro (estándar §B.5). Se prueba con Testcontainers y `EXPLAIN` no se exige (tabla pequeña); la prueba comprueba que el índice existe. El número de versión es el siguiente libre cuando se fusione (CM-36 usa V3 para la restricción de pronombres).
- Sin columnas nuevas (P-03: sin `correo_verificado`). `fecha_creacion` ya es `timestamptz` y `fecha_actualizacion` se actualiza con `@PreUpdate`.
- Borrado de fila: `DELETE` por `firebase_uid` **y** `estado = 'PENDING_VERIFICATION'` en la misma sentencia (si la persona activó la cuenta entre la consulta y el borrado, la fila no se borra); la tabla no tiene claves foráneas hacia ella en este servicio.

## 8. Seguridad y calidad

- **Token estricto (C-16, K-08):** la verificación se toma solo de lo que firma el Gateway; el cuerpo no cuenta; ausencia = no probado = 403. ASVS 8.2.2 (identidad solo de encabezados del Gateway) y API5 (autorización por función) cumplidos.
- **Doble control:** el Gateway rechaza los tokens sin verificar en toda ruta privada y Cuentas vuelve a comprobar en la ruta de activación (defensa en profundidad).
- **API6 (flujos sensibles):** `DISABLED` y `ANONYMIZED` no se reactivan por verificar un correo; `ANONYMIZED` no se distingue de «sin cuenta».
- **Borrado de datos personales:** la purga elimina credencial y fila sin conservar nada; el log solo lleva `firebaseUid`.
- **Ventana conocida de la purga:** entre la consulta de «¿verificada?» y el borrado de la credencial hay milisegundos en los que una persona podría verificar su correo; se acepta (consecuencia: puede registrarse de nuevo con el mismo correo). **Decisión D6, pregunta 8.**
- **Privilegios del Job:** la cuenta de servicio del Job necesita borrar usuarios de Firebase y acceso a la base; la define DevOps (pedido del 5-oct). Backend no lee ni escribe secretos.
- **Rendimiento:** la purga procesa hasta 1000 cuentas con dos llamadas a Firebase cada una; con 1000 candidatas son ≈ 2000 llamadas secuenciales (minutos), aceptable para un Job diario. La activación deja de llamar a Firebase: ≈ 200 ms menos.
- **Atributos y evidencia:** seguridad → pruebas de cada rechazo + `/security-review` de los bloques 1 y 3; fiabilidad → pruebas de concurrencia, de falla parcial y de reintento; observabilidad → resumen de la purga; privacidad → prueba de que el log no contiene correo.

## 9. Casos borde recorridos (estándar §C)

| Grupo | Aplica | Cómo se cubre |
|---|---|---|
| Presencia | `X-User-Id` ausente, vacío, solo espacios, 128 y 129 caracteres; `X-User-Email-Verified` ausente, vacío, `true`, `false`, `TRUE`, `abc` | Pruebas de controlador (bloque 1) |
| Texto (n−1, n, n+1) | Sí, `X-User-Id` | 128 pasa y 129 no |
| Fechas (UTC, reloj fijo) | Sí, purga | 167 h 59 min, 168 h, 168 h 1 min, cambio de día, hora de Colombia frente a UTC |
| Estado | Sí | Cuatro estados de cuenta en activación y en purga |
| Idempotencia | Sí | Activar dos veces; purgar dos veces seguidas |
| Concurrencia | Sí | Dos activaciones; purga mientras alguien activa |
| Falla parcial | Sí | Firebase falla en una cuenta; falla el borrado de la fila tras borrar la credencial |
| Dependencias | Sí | Firebase caído en la purga; activación sin Firebase |
| Colecciones | Sí, purga | Lote vacío, una, 1000 y 1001 candidatas |
| Propiedad | Sí | La identidad es solo la del encabezado; no hay otro identificador |
| Carga | Sí | Cuerpo cualquiera ignorado (incluido grande: no se lee) |

## 10. Reutilización

Se reutilizan y amplían: `ActivateAccountService`, `ActivateAccountCommand`, `AccountActivationController`, `ActivatedAccountResponse`, `Account.activate()`, `AccountRepository` y su adaptador, `FirebaseUserDirectory` y su adaptador, `BusinessException`, `BusinessExceptionHandler`, `ErrorCode`, `ProblemDetailTestSupport`, `InMemoryFirebaseUserDirectory`, `FirebaseTestConfiguration` y `AccountRegistrationEndToEndTest`. Se crean: `AccountDisabledException` y `AccountConflictException` (no existen excepciones para esos casos), `InvalidIdentityException`, `PurgeUnverifiedAccountsService` y su resumen (no hay proceso similar), y el modo de arranque de tarea.

## 11. Decisiones

| # | Decisión | Porqué | Alternativas descartadas | Decisión humana |
|---|---|---|---|---|
| D1 | Token estricto: se elimina la consulta de respaldo a Firebase en la activación | C-16 / K-08 (PO): el estado se toma solo del token; la consulta permitía activar contra lo que dice el token | Mantener la consulta (contradice el CA-1.2.6) | PO (5-oct-2026); técnica: PENDIENTE (Paula) |
| D2 | `ANONYMIZED` responde como «sin cuenta» (404) y `DISABLED` 403 con texto propio | No revelar que existió la cuenta; el texto de `DISABLED` es el de CA-1.3.11 | 409 para ambas; 500 (defecto actual) | Cerrada sin consulta (CA-1.3.11 y RT-03), pregunta 2 |
| D3 | Se quita `@Transactional` de `activate` y el adaptador traduce el conflicto de versión a una excepción de dominio | El fallo de versión ocurre al confirmar la transacción, fuera del método; sin transacción en el servicio, el servicio puede capturarlo, releer y responder 200 | Capturar la excepción en el manejador global (no permite releer ni responder 200); bloqueo pesimista (innecesario para una sola fila) | Aprobada por Paula (6-oct-2026), pregunta 5 |
| D4 | La purga es un servicio de aplicación que se ejecuta una vez por invocación; el disparo es de infraestructura | Cloud Run no garantiza instancia viva para `@Scheduled`; el Job se dispara por Cloud Scheduler | `@Scheduled` (descartado por el PO y por Paula el 5-oct); endpoint HTTP interno (excepción al contrato de entrada) | Decidido (5-oct-2026, Paula) |
| D5 | Tope de 1000 cuentas por ejecución | Acota el tiempo y el consumo; el resto se procesa al día siguiente | Sin tope (riesgo de ejecución larga); paginar hasta agotar (no acota) | Aprobada por Paula (6-oct-2026), pregunta 6 |
| D6 | Se acepta la ventana entre la consulta y el borrado | Milisegundos; la consecuencia es un nuevo registro | Verificar de nuevo justo antes de borrar (reduce, no elimina, la ventana; agrega una llamada) | Aprobada por Paula (6-oct-2026), pregunta 8 |
| D7 | Bloques en cuatro PR: 1 (Cuentas, activación), 2 (Cuentas, purga), 3 (Gateway), 4 (Cuentas, Job) | Cada uno es revisable y desplegable solo | Un PR por repositorio | Aprobada por Paula (6-oct-2026) |

## 12. Pruebas (resumen por bloque; el detalle va en `tasks.md`)

- **Bloque 1:** activación feliz; ya activa (no escribe: `fecha_actualizacion` y `version` iguales con base real); sin cuenta (404); `DISABLED` (403); `ANONYMIZED` (404 idéntico al de sin cuenta); encabezado `false`, ausente, vacío y `abc` (403, sin consulta a Firebase); `X-User-Id` ausente, vacío, solo espacios, 128 y 129; cuerpo `emailVerified:true` ignorado; Firebase caído con `true` (200); dos activaciones simultáneas (200 y 200, nunca 500); el log no contiene correo.
- **Bloque 2:** 167 h 59 min se conserva; 168 h exactas se conserva; 168 h 1 min se elimina; verificada en Firebase → se activa y no se borra; Firebase ya sin ese usuario → se borra la fila; falla Firebase en una cuenta → las demás se procesan y se cuenta la fallida; falla el borrado de la fila tras borrar la credencial → la corrida siguiente termina; lote vacío; no toca `ACTIVE`/`DISABLED`/`ANONYMIZED`; 1000 y 1001 candidatas; el correo queda libre (registro 201); dos ejecuciones seguidas.
- **Bloque 3:** ver la spec del Gateway.
- **Bloque 4:** el perfil arranca sin servidor web, ejecuta una vez y sale con 0; con base inaccesible sale distinto de 0; no hay ruta nueva (`GET /` y el resto responden conexión rehusada).
- `LayeredArchitectureTest` en verde (`PurgeUnverifiedAccountsService` no importa Firebase ni JPA). Cobertura ≥ 90 % de líneas y ramas de lo nuevo.

## 13. Fuera de alcance

Plantilla del correo y DNS (CM-255) · pantallas y llamadas de `cameia-web` · eventos hacia RabbitMQ (CM-187) · recuperación de contraseña (HU-1.4) · sincronización de una cuenta verificada sin fila (CA-1.3.9, fuera del MVP por P-07) · el Job de Cloud Run, su cuenta de servicio y el Cloud Scheduler (DevOps, tarea que abre Vela) · RT-01 no aplica a esta HU · registro repetido (CM-251).

## 14. Preguntas abiertas (cada una nombra a quién y qué bloquea)

**Ronda 1**

| # | Pregunta | A quién | Recomendación | Bloquea |
|---|---|---|---|---|
| 1 | El Gateway responde 403 en vez de propagar: la decisión GW-TBD-26 (18-sep, «propagar») ¿queda sin efecto frente a CA-1.2.13? | **Cerrada sin consulta (cambio sin alternativa):** CA-1.2.13 (backlog v4, 5-oct-2026) es posterior y explícito y reemplaza a GW-TBD-26 (18-sep) | Sí: CA-1.2.13 (v4) es posterior y explícito | Bloque 3 |
| 2 | Estado, código y texto de `DISABLED` (403 `ACCOUNT_DISABLED`, «Esta cuenta está deshabilitada.») y de `ANONYMIZED` (404 como «sin cuenta») en la activación | **Cerrada sin consulta (cambio sin alternativa):** el texto de `DISABLED` es el literal de CA-1.3.11; `ANONYMIZED` responde igual que una cuenta inexistente para no revelar que existió (RT-03) | Los propuestos | Bloque 1 |
| 3 | ¿El 403 `EMAIL_NOT_VERIFIED` de Cuentas usa el mismo texto que el del Gateway, «Tu correo aún no está verificado.»? | **Cerrada sin consulta (cambio sin alternativa):** estándar §A: un código ↔ un mensaje; `EMAIL_NOT_VERIFIED` tiene un solo texto en los dos servicios | Sí: un código, un mensaje | Texto del bloque 1 |
| 4 | V-04: ¿la respuesta de `applyActionCode` con usuario inexistente o deshabilitado y la vigencia del enlace se verifican con el emulador de Firebase (puede diferir del real) o con un proyecto de prueba real? | **Respondida (Paula, 6-oct): sí, la recomendación** | Emulador primero y confirmar en staging con un proyecto real | Textos de errores de Frontend (CA-1.2.2) |
| 5 | D3: ¿se aprueba quitar `@Transactional` de la activación y traducir el conflicto de versión en el adaptador? | **Respondida (Paula, 6-oct): sí, la recomendación** | Sí | Tarjetas T-1.3 y T-1.4 |
| 6 | D5: ¿tope de 1000 cuentas por ejecución? | **Respondida (Paula, 6-oct): sí, la recomendación** | Sí, con aviso en el resumen | Bloque 2 |

**Ronda 2**

| # | Pregunta | A quién | Recomendación | Bloquea |
|---|---|---|---|---|
| 7 | Índice parcial `ix_cuenta_pendiente_fecha_creacion` (migración nueva): ¿se aprueba o se difiere hasta que haya un problema medido? | **Respondida (Paula, 6-oct): sí, la recomendación** | Aprobarlo: es un filtro diario fijo y el estándar lo pide | Bloque 2 |
| 8 | D6: ¿se acepta la ventana entre «¿verificada?» y el borrado? | **Respondida (Paula, 6-oct): sí, la recomendación** | Sí | Bloque 2 |
| 9 | Crear el Cloud Run Job de la purga y su Cloud Scheduler (03:00 America/Bogota) y su cuenta de servicio | **PENDIENTE de Juan Diego Gomez**, por una tarea que abre Vela | Pedirlo en el documento a DevOps ya existente (pedido del 5-oct) | Prueba en staging del bloque 4 |
| 10 | Estimación: Backend suma ≈ 16 h (bloque 1: 4 h; bloque 2: 6 h; bloque 3: 3 h; bloque 4: 3 h) frente a 9 h de toda la HU | **No era pregunta: se comunica. la estimación se informa a Vela** | Informar el sobrecosto y repartir | — |
| 11 | D7: ¿cuatro PR (1, 2, 3, 4)? | **Respondida (Paula, 6-oct): sí, la recomendación** | Sí | Orden de entrega |
| 12 | Orden: el bloque 3 (Gateway) depende de CM-260 (charset); ¿se hace CM-260 antes? | **Respondida (Paula, 6-oct): sí, la recomendación** | Sí, CM-260 primero (≈ 1 h) | Bloque 3 |
| 13 | ¿Los bloques 1 y 2 de Cuentas se ejecutan después de los PR de CM-36 y CM-251, que tocan el mismo manejador de errores? | **Respondida (Paula, 6-oct): sí, la recomendación** | Solo el PR 1A de CM-36 antes (no CM-251) | Orden del bloque 1 |
| 14 | P-JOB-1: en un Cloud Run Job no existe `K_SERVICE` (existe `CLOUD_RUN_JOB`) y la guarda del emulador de Firebase solo protege por el perfil `prod`. ¿Se agrega `CLOUD_RUN_JOB` a la detección en el bloque 4? | **Respondida (Paula, 6-oct-2026): sí, en el bloque 4** | Sí (≈ 10 líneas y una prueba) | Tarjeta T-4.3 |
| 15 | Nivel del log de una cuenta que la purga no pudo procesar: `WARN` sin traza, con el `firebaseUid` y solo el nombre de la clase de la excepción (el mensaje de Firebase puede traer datos personales). El resumen va en `INFO` y el aviso de fallidas en `WARN` | **Respondida (Paula, 6-oct-2026): WARN sin traza, como se propone** | Aceptar: la cuenta se reintenta sola al día siguiente; `ERROR` alertaría por algo que se recupera solo | T-2.5 (una línea) |

### Pendientes que deja CM-36 (revisión final, 7-oct-2026)

- **Textos de la activación con el estilo del catálogo** (tuteo y punto final, D2-01). Hoy, al terminar CM-36, siguen sin punto:
  400 `IDENTITY_REQUIRED` «La petición no incluye los datos que exige esta ruta», 403 `EMAIL_NOT_VERIFIED` «Primero debes verificar tu
  correo con el enlace que te enviamos» y 404 `ACCOUNT_NOT_FOUND` «No encontramos una cuenta para este usuario». Esta tarea los
  define con sus CA; CM-36 no los tocó para no cambiarlos dos veces (decisión de Paula). Cambia textos: aviso a Frontend.
- **Ningún error previsible en `INTERNAL_ERROR`** (regla de Paula para todo Backend). `docs/errores.md` de CM-36 deja con destino
  esta tarea: activar una cuenta bloqueada o anonimizada, `X-User-Email-Verified` con un valor que no es `true` ni `false`, Firebase
  caído al consultar el correo (debe ser 503 `DEPENDENCY_UNAVAILABLE`, como en el registro) y un `uid` inexistente en Firebase.
  `UntypedExceptionClassificationTest` los tiene como «pendiente de CM-179»: al corregirlos, se quitan de su clasificación.

## 15. Estimación

Bloque 1 ≈ 4 h (≈ 450 líneas) · bloque 2 ≈ 6 h (≈ 700) · bloque 3 ≈ 3 h (≈ 250) · bloque 4 ≈ 3 h (≈ 300). Total ≈ **16 h** frente a 9 h de la HU (pregunta 10).
