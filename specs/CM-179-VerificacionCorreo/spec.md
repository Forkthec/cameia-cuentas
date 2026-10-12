# Spec — CM-179-VerificacionCorreo: verificación de correo en Cuentas y en el Gateway

- **Tarea:** CM-179 · Subtarea «HU-1.2 – Backend: verificación de correo» · padre CM-178 «HU-1.2 Verificación de Correo Electrónico» (hermanas: CM-180 Frontend, «En revisión»; CM-255 DevOps, «En curso») · responsable: Paula Andrea Muñoz Delgado
- **Jira (leído el 11-oct-2026):** «Por hacer», Sprint 2 (activo, termina el 11-oct a las 23:00 hora de Colombia), sin estimación de horas, sin comentarios ni enlaces. La descripción remite al DoD de la HU. El único comentario de la historia CM-178 (Vela, 5-oct) resume los cambios del backlog v4. El traslado de CM-179 al Sprint 3 se pidió a Vela el 10-oct (`comunicaciones/enviadas/10102026_v1_requisitos-de-perfil-para-registrar-en-jira-para-po.md`, parte 4.1) y en Jira sigue en el Sprint 2.
- **Repositorios y base:** `cameia-cuentas` (esta spec; `origin/develop` `621a846`) y `cameia-gateway` (`origin/develop` `53fbe30`; su spec corta está en `cameia-gateway/specs/CM-179-VerificacionCorreo/` y remite a esta).
- **Backlog vigente:** `09102026_01_Backlog_v6.xlsx`, hoja `HE-01`, HU-1.2 (CA-1.2.1 a 1.2.14, observaciones y notas técnicas), RT-02 (CA06) y RT-06, más CA-1.1.30 y CA-1.3.7 y 1.3.11 citados. 9 h y 9 puntos para toda la HU.
- **Estado:** spec revalidada el 11-oct-2026 contra el backlog v6, Jira y `develop`. **Aprobada por Paula el 11-oct-2026**, junto con la pregunta 18 (opción C) y las decisiones técnicas D10 y D12. Plan y tarjetas de toda la tarea en `plan.md` y `tasks.md`.
- **Puerta de listo: `LISTA PARA EJECUTAR`** (11-oct-2026). Las condiciones se comprueban en la sección 21. La lista de verificación que el ejecutor corre fila por fila antes de entregar está en la sección 20.
- **Reglas del repo:** `cameia-cuentas` no tiene `AGENTS.md`; sus reglas están en `CLAUDE.md` y `docs/constitution.md`. No bloquea nada.
- **Atributos de calidad:** seguridad (AC-0003: SEG-01, SEG-02, SEG-04), fiabilidad (AC-0004: FIA-01, FIA-02), desempeño (AC-0002: DES-02), interacción (AC-0006: códigos estables), interoperabilidad y mantenibilidad (IOP-01, IOP-04, MAN-01), privacidad (REST-0003). La métrica y la prueba de cada uno están en la sección 8.

## 1. Contexto y objetivo

Una cuenta registrada queda `PENDING_VERIFICATION` y no se puede usar. Al abrir el enlace del correo solo se verifica en Firebase (`applyActionCode`, sin llamada a Cuentas: CA-1.2.1). La cuenta pasa a `ACTIVE` con el Plan Free **en el primer inicio de sesión posterior**: `cameia-web` llama a `POST /api/v1/users/me/verification`; si falla, el ingreso sigue y la llamada se repite en el siguiente (CA-1.2.14). El Gateway responde 403 a todo token con `email_verified=false`, salvo en esa ruta (CA-1.2.13). Un proceso diario a las 03:00 hora de Colombia elimina las cuentas sin verificar con más de 168 h y publica `cuenta.eliminada` (CA-1.2.9 y 1.2.10).

**Objetivo de Backend**, en cuatro piezas:

1. **Activación robusta (Cuentas):** la verificación se toma **solo** del ID Token que propaga el Gateway; ningún estado de cuenta ni encabezado produce un 500; la activación es idempotente y segura ante concurrencia.
2. **Purga diaria (Cuentas):** elimina credencial y fila de las cuentas sin verificar con más de 168 h y publica `cuenta.eliminada` por la tabla de salida. Activa en lugar de borrar las que Firebase ya tiene verificadas.
3. **Bloqueo en el Gateway:** 403 `EMAIL_NOT_VERIFIED` a todo token con `email_verified=false` en rutas privadas, salvo la ruta de verificación.
4. **Modo de tarea (Cuentas):** la purga corre como Cloud Run Job disparado por Cloud Scheduler, sin ruta HTTP, con el mismo patrón que el Job de relevo de eventos que ya existe.

**Alerta que atiende:** el defecto crítico DF-003 de Testing (`comunicaciones/recibidas/10102026_v1_aviso-testing-hu13-hu12.md`) mostró que una sesión sin verificar llegaba al área privada y leía el perfil. Frontend lo corrigió en el cliente (`cameia-web` PR #70, `comunicaciones/recibidas/respuesta-df003-reenvio-verificacion.md`). Testing y Frontend piden expresamente la segunda barrera del servidor (CA-1.2.13), que es el bloque 3 de esta tarea.

## 2. Trazabilidad de los 14 CA (backlog v6)

| CA | Dueño (columna Técnico) | Estado en `develop` | Brecha | Pieza |
|---|---|---|---|---|
| 1.2.1 verificación exitosa | F | — | Nada de Backend | — |
| 1.2.2 enlace vencido o usado | F | — | Nada de Backend. CM-180 está «En revisión» y Testing prueba el CA en staging. La verificación V-04 con el emulador (pregunta 4) ya no tiene a quién servir: Frontend entregó sus textos | — |
| 1.2.3 reenvío | F | — | — | — |
| 1.2.4 confirmación repetida | B | 200 sin escribir si ya está `ACTIVE` | Falta la prueba con base real que compare `fecha_actualizacion` y `version` antes y después | 1 |
| 1.2.5 límite de reenvíos | F | — | — | — |
| 1.2.6 la activación exige el correo verificado | B | **Defecto:** con `X-User-Email-Verified` en `false` o ausente, el servicio consulta a Firebase y puede activar (`ActivateAccountService.java:87-92`) | 403 estricto, sin consulta, cuerpo ignorado | 1 |
| 1.2.7 inicio de sesión tras verificar | F | — | — | — |
| 1.2.8 correo desde cameia.app | F (DevOps) | — | CM-255 | — |
| 1.2.9 menos de 7 días | B | No existe | Purga: se conserva a 168 h exactas o menos | 2 |
| 1.2.10 más de 7 días | B | No existe | Purga: activa si Firebase dice verificado; si no, borra credencial, fila y publica `cuenta.eliminada` | 2 y 4 |
| 1.2.11 correo libre tras la purga | F y B | No aplica hoy | Registro 201 tras la purga | 2 |
| 1.2.12 registrarse de nuevo | F y B | Hecho en CM-251 (CA-1.1.30, «Finalizado») | Nada | — |
| 1.2.13 403 del Gateway | B (Gateway) | **Defecto:** `FirebaseAuthGlobalFilter` propaga `false` y reenvía; `CLAUDE.md` del Gateway lo lista abierto (`GW-TBD-17`) | 403 con cuerpo y charset, sin reenviar | 3 |
| 1.2.14 activación en el primer ingreso | F y B | Backend funciona con `true`; **`cameia-web` aún no hace la llamada** (acción A6) | Robustez (pieza 1 y sección 3); la parte F es de Frontend | 1 |

Notas técnicas de HU-1.2 que obligan a Backend: «la verificación se toma del ID Token que propaga el Gateway, nunca del cuerpo»; proceso diario «elimina … con más de 168 h desde fecha_creacion (a 168 h exactas se conserva)», «antes consulta Firebase y activa las que ya están verificadas», «por cuenta borra primero la credencial (Admin SDK deleteUser) y después la fila; un fallo no detiene a las demás y se reintenta en la siguiente ejecución», «publica cuenta.eliminada en cuentas.events», «no escribe datos personales en el log»; «sin columna correo_verificado».

## 3. Defectos que ya hay en el código (`develop` `621a846` y `53fbe30`)

| # | Defecto | Evidencia | Destino |
|---|---|---|---|
| 1 | `Account.activate()` lanza `IllegalStateException` si la cuenta está `DISABLED` o `ANONYMIZED` → 500 `INTERNAL_ERROR` | `domain/model/Account.java:101-110`; `UntypedExceptionClassificationTest` lo clasifica «pendiente de CM-179»; `docs/errores.md` fila de deuda | Pieza 1 |
| 2 | Con el encabezado en `false` o ausente se consulta a Firebase y se puede activar contra lo que dice el token | `ActivateAccountService.java:87-92`; la prueba `noSeFiaDeUnEncabezadoEnFalsoSiFirebaseDiceQueSiEstaVerificado` lo fija | Pieza 1 |
| 3 | `isEmailVerified` lanza `IllegalStateException` sin distinguir usuario inexistente de Firebase caído → 500 | `FirebaseUserDirectoryAdapter.java:273-280`; clasificado «pendiente de CM-179» | Pieza 1 deja de usarlo; pieza 2 lo reemplaza por un método tipado |
| 4 | La activación no valida `X-User-Id` vacío, solo espacios o de más de 128 caracteres (la columna es `VARCHAR(128)`); `GET /api/v1/users/me` sí lo valida | `AccountActivationController.java:44` frente a `GetCurrentAccountService.java:48-50` | Pieza 1 |
| 5 | `X-User-Email-Verified: abc` produce un error de conversión de tipo → 500 | `@RequestHeader(...) Boolean` (`AccountActivationController.java:45`); `MethodArgumentTypeMismatchException` cae en `@ExceptionHandler(Exception.class)`; `docs/errores.md` fila de deuda | Pieza 1 |
| 6 | Dos activaciones simultáneas: la perdedora lanza `ObjectOptimisticLockingFailureException`, sin manejador → 500 (inferido; la primera prueba de la pieza 1 lo confirma) | `@Version` en `AccountEntity.java:66`; `@Transactional` en `ActivateAccountService.activate` | Pieza 1 |
| 7 | Textos de la activación sin el punto final que exige el estilo del catálogo (D2-01, observación de RT-01) | `EmailNotVerifiedException`, `AccountNotFoundException`, `IdentityRequiredException` y `BusinessExceptionHandler.java:264` | Pieza 1 |
| 8 | El Gateway no rechaza `email_verified=false` | `FirebaseAuthGlobalFilter.java:244`; `CLAUDE.md` del Gateway, líneas 129, 134 y 232 | Pieza 3 |
| 9 | La guarda del emulador de Firebase solo reconoce `K_SERVICE`; en un **Cloud Run Job** la variable es `CLOUD_RUN_JOB`. Afecta ya al Job de relevo `account-events-relay` | `FirebaseConfiguration.java:63` y `:172` | Pieza 4 |

## 4. Requisitos funcionales (EARS)

### Pieza 1 — activación robusta (Cuentas)

- **REQ-VC-01 (CA-1.2.6).** Cuando la cuenta esté `PENDING_VERIFICATION` y llegue `POST /api/v1/users/me/verification` con `X-User-Email-Verified` distinto del texto exacto `true` (`false`, ausente, vacío, `TRUE`, `abc` o cualquier otro; el Gateway siempre escribe `true` en minúsculas), el servicio debe responder 403 con `code` `EMAIL_NOT_VERIFIED` y el mensaje «Tu correo aún no está verificado.», no debe activar la cuenta, no debe consultar a Firebase y debe ignorar el cuerpo de la petición.
- **REQ-VC-02 (CA-1.2.14).** Cuando llegue la petición con `X-User-Email-Verified: true` y la cuenta esté `PENDING_VERIFICATION`, el servicio debe pasarla a `ACTIVE`, actualizar `fecha_actualizacion`, conservar el plan y responder 200 con `{"id":"<uuid>","status":"ACTIVE"}`.
- **REQ-VC-03 (CA-1.2.4).** Cuando la cuenta ya esté `ACTIVE`, con o sin la afirmación `X-User-Email-Verified: true` (un token anterior a la verificación sigue diciendo `false`), el servicio debe responder 200 con `{"id":"<uuid>","status":"ACTIVE"}` sin escribir: `fecha_actualizacion` y `version` no cambian.
- **REQ-VC-04.** Cuando `X-User-Id` no tenga cuenta local, el servicio debe responder 404 con `code` `ACCOUNT_NOT_FOUND` y el mensaje «No encontramos una cuenta para este usuario.».
- **REQ-VC-05.** Cuando la cuenta esté `DISABLED`, el servicio debe responder 403 con `code` `ACCOUNT_DISABLED` y el mensaje «Esta cuenta está deshabilitada.» (texto de CA-1.3.11), sin cambiar la cuenta.
- **REQ-VC-06.** Cuando la cuenta esté `ANONYMIZED`, con o sin la afirmación `X-User-Email-Verified: true`, el servicio debe responder exactamente lo mismo que si no existiera (404 `ACCOUNT_NOT_FOUND`), sin cambiar la cuenta. Es la misma regla que ya aplica `GET /api/v1/users/me`.
- **REQ-VC-07.** Cuando falte `X-User-Id`, o llegue vacío, solo con espacios o con más de 128 caracteres, el servicio debe responder 400 con `code` `IDENTITY_REQUIRED` y el mensaje «La petición no incluye los datos que exige esta ruta.», sin consultar la base ni Firebase. Es la regla que ya aplica `GET /api/v1/users/me` (`GetCurrentAccountService`). Con 128 caracteres exactos la petición sigue.
- **REQ-VC-08.** Cuando dos activaciones de la misma cuenta lleguen a la vez, la cuenta debe escribirse una sola vez y ninguna debe responder 500. La que gana responde 200. La que pierde la carrera de versiones vuelve a leer la cuenta **una vez**: si la encuentra `ACTIVE` (el caso esperado), responde 200 con `{id, status:"ACTIVE"}`. Si la encuentra en otro estado (muy improbable), responde 409 con `code` `ACCOUNT_CONFLICT` y el mensaje «La cuenta se está actualizando. Inténtalo de nuevo.». El guardado compara la versión de la fila que se leyó con la actual: si otra petición cambió la cuenta en medio (por ejemplo, la deshabilitó), no se guarda nada y se responde igual (200 si quedó `ACTIVE`, 409 si no).
- **REQ-VC-09.** Mientras Firebase no responda, la activación con `X-User-Email-Verified: true` debe seguir funcionando (el servicio no consulta a Firebase).
- **REQ-VC-10.** El servicio debe registrar la activación en `INFO` con el `firebaseUid` y cada rechazo previsible en `WARN` con `code` y `requestId` (lo hace el manejador), sin correo, nombre ni otro dato personal.
- **REQ-VC-11.** La documentación OpenAPI de la ruta debe describir las respuestas 200, 400, 403 (`EMAIL_NOT_VERIFIED` y `ACCOUNT_DISABLED`), 404, 409 y 500 con su `code`, y los dos encabezados (los pone el Gateway; el navegador no los envía; solo `true` activa).

### Pieza 2 — purga diaria (Cuentas)

- **REQ-VC-20.** El servicio de purga debe ejecutarse una vez por invocación y no depender de la hora de reloj: la hora (03:00 hora de Colombia) la fija Cloud Scheduler.
- **REQ-VC-21 (CA-1.2.9, 1.2.10).** Cuando se ejecute, el servicio debe seleccionar las cuentas con estado `PENDING_VERIFICATION` y `fecha_creacion < ahora(UTC) − 168 h`, de la más antigua a la más nueva. Una cuenta de 167 h 59 min o de exactamente 168 h se conserva; una de 168 h 1 min es candidata. Las cuentas `ACTIVE`, `DISABLED` y `ANONYMIZED` no se tocan.
- **REQ-VC-22 (CA-1.2.10).** Para cada candidata, el servicio debe consultar primero a Firebase:
  - si el correo figura verificado, debe activar la cuenta y no eliminarla;
  - si no está verificado, debe eliminar **primero la credencial** y **después la fila**, y publicar `cuenta.eliminada` en la misma transacción que borra la fila;
  - si la credencial ya no existe, debe eliminar solo la fila y publicar `cuenta.eliminada`.
- **REQ-VC-23.** Cuando se elimine una credencial que Firebase ya no tiene, el servicio debe tratarlo como éxito (`deleteUser` ya es idempotente en `develop`).
- **REQ-VC-24.** Cuando falle el procesamiento de una cuenta por un rechazo de Firebase o de la base, el servicio debe contarla como fallida, registrarla en `WARN` con el `firebaseUid` y el nombre de la clase de la excepción (nunca su mensaje: puede traer datos personales) y seguir con las demás. La cuenta se reintenta en la siguiente ejecución. Si al terminar las cuentas candidatas todas las revisadas resultaron fallidas, el servicio debe registrar además un `ERROR` («Todas las cuentas revisadas fallaron»), porque apunta a una causa común, casi siempre un permiso de Firebase que falta en la cuenta de servicio; el Job sigue saliendo con 0 (decisión D-12, opción A).
- **REQ-VC-25.** Cuando la fila no se pueda borrar después de borrar la credencial porque ya no está pendiente (la persona la activó entre la consulta y el borrado), el servicio debe registrar en `ERROR` «Cuenta activa sin credencial tras la purga» con el `firebaseUid`, contarla como fallida y no publicar el evento. Es la ventana aceptada en D6: deja una fila activa sin credencial que alguien debe conciliar.
- **REQ-VC-26.** Cuando Firebase no esté disponible (`DependencyUnavailableException`), el servicio debe dejar de procesar en esa misma cuenta, registrar el resumen y lanzar la excepción, para que el Job termine con un código distinto de 0 y Cloud Run lo reintente. Las cuentas que faltan no se cuentan como fallidas.
- **REQ-VC-27.** El evento `cuenta.eliminada` versión 1 debe guardarse en `microcuentas.evento_saliente` con `tipo` `cuenta.eliminada`, `agregado_id` igual al `firebaseUid` y la carga `{"usuarioId":"<firebaseUid>","eliminadaEn":"<instante UTC con milisegundos y sufijo Z>"}`. Lo publica el Job de relevo existente (`account-events-relay`, cada 5 min) en el exchange `cuentas.events` con la clave `cuenta.eliminada`. Es el contrato que ya consume Perfil (`AccountDeletedPayloadV1`, cola `perfil.cuenta-eliminada`, ADR 0004 de Perfil).
- **REQ-VC-28.** El servicio no debe escribir datos personales en el log (solo `firebaseUid`). Al terminar debe registrar en `INFO` «Purga de cuentas sin verificar: revisadas={}, eliminadas={}, activadas={}, fallidas={}, credencialesHuerfanas={}, quedanCandidatas={}».
- **REQ-VC-29.** El servicio debe procesar como máximo **1000** candidatas por ejecución (`cuentas.purge.max-accounts-per-run`, de 1 a 1000); si hay más, el resumen lo indica y el resto se procesa en la siguiente ejecución.
- **REQ-VC-30 (CA-1.2.11).** Después de eliminar la credencial y la fila, un registro con ese correo debe crear una cuenta nueva (201), sin «Ese correo ya tiene una cuenta.».
- **REQ-VC-31.** Cuando no haya candidatas, el servicio debe terminar con un resumen en ceros y sin error.
- **REQ-VC-32.** La consulta de candidatas debe usar el índice parcial `ix_cuenta_pendiente_fecha_creacion` sobre `fecha_creacion` donde `estado = 'PENDING_VERIFICATION'`.
- **REQ-VC-33 (pregunta 18, opción C).** Conciliación de credenciales huérfanas: credencial en Firebase sin fila en `cuenta`, que el registro deja cuando falla también su borrado compensatorio (`RegisterUserService.borrarSiQuedoCreada` ya promete que «la concilia la purga»). Cuando se ejecute la purga, el servicio debe borrar como máximo 1000 credenciales que cumplan a la vez cuatro condiciones: único proveedor `password`, correo sin verificar, creadas antes de `ahora − 168 h` y sin fila en `cuenta`. No publica evento, porque nunca hubo `cuenta.creada`. Una huérfana con el correo verificado se conserva para conciliarla a mano con el `uid` que deja el log del registro.

### Pieza 3 — 403 del Gateway (CA-1.2.13)

- **REQ-VC-40.** Cuando una petición a una ruta privada traiga un ID Token válido cuyo claim `email_verified` sea `false`, el Gateway debe responder 403 con el cuerpo exacto `{"code":"EMAIL_NOT_VERIFIED","message":"Tu correo aún no está verificado."}` y `Content-Type` `application/json` con `charset=UTF-8`, sin reenviar la petición a ningún microservicio.
- **REQ-VC-41.** `POST /api/v1/users/me/verification` (método y ruta exactos) queda exceptuada: con `email_verified=false` se reenvía con `X-User-Email-Verified: false`.
- **REQ-VC-42.** Cuando el token no traiga el claim `email_verified`, el Gateway no debe bloquear (se reenvía sin la cabecera, como hoy).
- **REQ-VC-43.** Cuando `email_verified` sea `true`, el Gateway debe reenviar con `X-User-Email-Verified: true`.
- **REQ-VC-44.** Las rutas públicas no se ven afectadas; una cabecera `X-User-Email-Verified` enviada por el cliente se sigue borrando y la decisión sale solo del claim.
- **REQ-VC-45.** El Gateway debe registrar el rechazo en `WARN` con el `X-Request-Id` y el código, sin token, correo ni uid; la respuesta lleva el encabezado `X-Request-Id`.

El detalle (REQ-EV-01 a 10, decisiones y pruebas) está en la spec del Gateway.

### Pieza 4 — modo de tarea (Cuentas)

- **REQ-VC-50.** Cuando se arranque la aplicación con el perfil `purge-job`, debe iniciar sin servidor web, ejecutar la purga una vez, registrar el resumen y terminar el proceso.
- **REQ-VC-51.** Mientras la purga termine (aunque haya cuentas fallidas o candidatas pendientes), el proceso debe salir con código 0. Si no pudo empezar (configuración inválida, base inaccesible) o si la purga lanzó (Firebase no disponible, falla la consulta de candidatas), debe salir con un código distinto de 0.
- **REQ-VC-52.** El servicio no debe exponer ninguna ruta HTTP nueva ni usar `@Scheduled`.
- **REQ-VC-53.** Cuando exista `FIREBASE_AUTH_EMULATOR_HOST` junto a `CLOUD_RUN_JOB`, la aplicación no debe arrancar, igual que hoy con `K_SERVICE` o el perfil `prod` (protege también al Job de relevo).
- **REQ-VC-54.** El `CLAUDE.md` del repo debe documentar el comando exacto del modo tarea, su perfil y sus variables, junto al del Job de relevo; `.env.example` debe traer la variable del tope, comentada.

## 5. Errores y catálogo

Formato del repo: `application/problem+json; charset=UTF-8` con `type`, `title`, `status`, `detail`, `instance`, `code` y `requestId` (CM-36). Cada fila se agrega o actualiza en `docs/errores.md` y se quita de su sección de deuda la fila que esta tarea cierra.

| Código | HTTP | Título | Mensaje (`detail`) | Origen | Pieza |
|---|---|---|---|---|---|
| `EMAIL_NOT_VERIFIED` | 403 | Correo sin verificar | «Tu correo aún no está verificado.» (el mismo del Gateway) | `EmailNotVerifiedException`: encabezado distinto de `true` | 1 |
| `ACCOUNT_DISABLED` | 403 | Cuenta deshabilitada | «Esta cuenta está deshabilitada.» | `AccountDisabledException` (nueva): cuenta `DISABLED` | 1 |
| `ACCOUNT_NOT_FOUND` | 404 | Cuenta no encontrada | «No encontramos una cuenta para este usuario.» | `AccountNotFoundException`: sin cuenta o `ANONYMIZED` | 1 |
| `ACCOUNT_CONFLICT` | 409 | Conflicto de cuenta | «La cuenta se está actualizando. Inténtalo de nuevo.» | `AccountConflictException` (nueva): pierde la carrera y la relectura no está `ACTIVE` | 1 |
| `IDENTITY_REQUIRED` | 400 | Petición incompleta | «La petición no incluye los datos que exige esta ruta.» | `ServletRequestBindingException` (falta) o `IdentityRequiredException` (vacío, espacios, > 128) | 1 |
| `EMAIL_NOT_VERIFIED` (Gateway) | 403 | — (formato `{code,message}`) | «Tu correo aún no está verificado.» | `FirebaseAuthGlobalFilter` | 3 |

Los dos textos con punto nuevo (`ACCOUNT_NOT_FOUND`, `IDENTITY_REQUIRED`) también los emite `GET /api/v1/users/me`: cambian en ambas rutas y se avisa a Frontend, que resuelve por `code`. Las causas pertenecen al vocabulario cerrado del estándar (`NOT_VERIFIED`, `DISABLED`, `NOT_FOUND`, `CONFLICT`, `REQUIRED`). La purga no responde HTTP: sus fallos van al log y al código de salida.

## 6. Contrato

- `POST /api/v1/users/me/verification`: sin cuerpo (cualquier cuerpo se ignora). La respuesta 200 `{id, status}` no cambia. Códigos nuevos: 403 `ACCOUNT_DISABLED` y 409 `ACCOUNT_CONFLICT`. Cambian de comportamiento: el encabezado ausente o en `false` da 403 sin consultar a Firebase (antes podía activar); un `X-User-Id` en blanco o largo da 400 (antes 404); un `abc` da 403 (antes 500). Cambian tres textos (punto final y el del 403).
- Consumidor: `cameia-web` (llama tras el ingreso con un token verificado; si falla, reintenta en el siguiente: CA-1.2.14).
- Evento `cuenta.eliminada` v1 en `cuentas.events` (sección 4, REQ-VC-27): contrato ya fijado por el catálogo de eventos y consumido por Perfil; esta tarea es el primer productor.
- Gateway: 403 nuevo en toda ruta privada para tokens con `email_verified=false`.
- Todo es aditivo o corrige un defecto. Se avisa a Frontend (sección 13).

## 7. Datos

- **Migración A (pieza 2):** `V<n>__indice_cuenta_pendiente_fecha_creacion.sql` con `CREATE INDEX ix_cuenta_pendiente_fecha_creacion ON microcuentas.cuenta (fecha_creacion) WHERE estado = 'PENDING_VERIFICATION';`. Es un filtro diario fijo sobre una tabla que crece con cada registro (estándar §B.5); la prueba comprueba que el índice existe y es parcial.
- **Migración B (pieza 2):** `V<n+1>__evento_cuenta_eliminada.sql`: reemplaza `ck_evento_saliente_tipo` por `CHECK (tipo IN ('cuenta.creada', 'cuenta.eliminada'))` en una sola sentencia `ALTER TABLE … DROP CONSTRAINT …, ADD CONSTRAINT …`. La spec de CM-279 lo dejó previsto («`tipo` crecerá con una migración nueva en CM-179»). `V5` no se edita. `<n>` es el siguiente número libre en `develop` el día del PR (hoy, `V6`).
- Sin columnas nuevas (sin `correo_verificado`, nota técnica de HU-1.2). `fecha_creacion` es `timestamptz` que fija `@PrePersist` con `Instant.now()` (`AccountEntity.java:120`). `fecha_actualizacion` se actualiza con `@PreUpdate`.
- **Borrado de la fila:** `DELETE` por `firebase_uid` **y** `estado = 'PENDING_VERIFICATION'` en la misma sentencia, en la misma transacción que el `INSERT` del evento. Si no borra nada, no hay evento. La tabla `cuenta` no tiene claves foráneas que la apunten. `evento_saliente` no tiene clave foránea a `cuenta`, a propósito: el evento sobrevive al borrado.
- **Unicidad del evento:** `uq_evento_saliente_tipo_agregado (tipo, agregado_id)` impide dos `cuenta.eliminada` del mismo `firebaseUid`. Un registro nuevo tras la purga usa otro `firebaseUid` (lo genera el registro), así que no choca.

## 8. Seguridad y atributos de calidad

**OWASP API Top 10 y ASVS N1.** API1 y API5 (autorización por objeto y por función): la identidad y la verificación salen solo de los encabezados del Gateway (ASVS 8.2.2); el cuerpo no cuenta. API3 y API6: `DISABLED` y `ANONYMIZED` no se reactivan; `ANONYMIZED` no se distingue de «sin cuenta». API4: el tope de 1000 y el índice acotan la purga. API8: ningún 500 previsible; respuestas sin traza (RT-05-CA03). API10: la purga trata cada respuesta de Firebase con su código y nunca registra su mensaje. ASVS 4.1.1: charset en el 403 del Gateway. **Doble control:** el Gateway rechaza los tokens sin verificar en toda ruta privada y Cuentas vuelve a comprobar en la activación.

**Ventanas aceptadas.** D6: entre «¿verificada?» y el borrado de la credencial hay milisegundos; si la persona activa en ese momento, queda una fila activa sin credencial que REQ-VC-25 registra en `ERROR`. Registro repetido (CA-1.1.30) justo cuando la purga borra la credencial: la persona ve «Verifica tu correo» y su cuenta desaparece; al siguiente registro obtiene una cuenta nueva.

**Riesgo residual conocido (pregunta 17, cerrada).** Alguien puede registrar primero el correo de otra persona con su propia contraseña. El backlog v6 ya fija el comportamiento: con el registro repetido en 200, `cameia-web` muestra «Ese correo ya tiene una cuenta.» cuando la contraseña no coincide (CA-1.1.30), y la cuenta del intruso no se puede usar sin abrir el enlace que llega al correo de la víctima (CA-1.3.7, CA-1.2.13). Si nadie la verifica, la purga la elimina a los 7 días (CA-1.2.10). Las otras mitigaciones contradicen criterios vigentes: purgar en horas choca con CA-1.2.9, y forzar un cambio de contraseña requiere HU-1.4, que está en «MVP si alcanza». No hay cambio en esta tarea.

| Atributo | Métrica | Límite | Cómo lo cumple | Prueba |
|---|---|---|---|---|
| AC-0003 | SEG-01 (ASVS N1 8.2.2, 4.1.1) | 100 % | Verificación solo del token; 403 con charset | `ActivateAccountServiceTest`, `AccountActivationControllerTest`, `FirebaseAuthGlobalFilterTest`, Newman |
| AC-0003 | SEG-02 (accesos rechazados) | 100 % del catálogo | Token sin verificar → 403 en toda ruta privada; encabezado falsificado ignorado | Gateway: rutas privadas parametrizadas; cabecera del cliente `true` con claim `false` → 403 |
| AC-0003 | SEG-04 (cero datos personales en telemetría) | 0 | Logs solo con `firebaseUid` y `requestId` | `OutputCaptureExtension` en activación, purga y Gateway |
| REST-0003 | Tratamiento mínimo | Credencial y fila borradas; el evento lleva solo `usuarioId` e instante | Purga y carga v1 | `PurgeEndToEndTest`, prueba de la carga |
| AC-0004 | FIA-01 (estado confirmado ante fallos) | 100 % | Borrado de fila y evento en una transacción; un fallo no deshace las demás cuentas; Firebase caído detiene y el Job reintenta | `PurgeUnverifiedAccountsServiceTest` (casos 5, 6 y 10), prueba transaccional |
| AC-0004 | FIA-02 (efectos por repetir) | 0 en 20 repeticiones | Activación idempotente; purga idempotente; evento único por `(tipo, agregado_id)` | E2E: activar dos veces y 20 veces; purgar dos veces |
| AC-0002 | DES-02 | p95 ≤ 2 s | La activación deja de llamar a Firebase; una lectura y una escritura por índice único | Newman `responseTime < 2000` en la carpeta de activación |
| AC-0006 | INT (estado identificable) | `code` estable en cada error | Catálogo | Pruebas de controlador y Newman |
| IOP-01 | Contratos documentados | Ruta en OpenAPI; evento con JSON Schema versionado | Anotaciones; `docs/eventos/cuenta-eliminada-v1.md` y `cuenta-eliminada-v1.schema.json`, como `cuenta-creada-v1` | Newman: `/v3/api-docs` publica la ruta; prueba que valida la carga contra el esquema |
| IOP-04 | Outbox en productores | 100 % | `cuenta.eliminada` por `evento_saliente` | Prueba transaccional |
| MAN-01 | Cobertura de lo nuevo o modificado | ≥ 90 % de líneas y de ramas | — | JaCoCo en `clean verify` |

## 9. Casos borde recorridos (estándar §C)

| Grupo | Aplica | Cómo se cubre |
|---|---|---|
| Presencia y forma | `X-User-Id` ausente, vacío, solo espacios, 128 y 129 caracteres; `X-User-Email-Verified` ausente, vacío, `true`, `false`, `TRUE`, `abc` | Controlador y servicio |
| Texto (n−1, n, n+1) | `X-User-Id` | 127 y 128 pasan, 129 no (ASCII: unidades UTF-16 = puntos de código) |
| Fechas (UTC, reloj fijo) | Purga | 167 h 59 min, 168 h exactas, 168 h 1 min; reloj fijo `2026-10-10T08:00:00Z` (03:00 en Colombia) |
| Estados | Activación y purga | `PENDING_VERIFICATION`, `ACTIVE`, `DISABLED`, `ANONYMIZED` |
| Idempotencia | Activación y purga | Activar dos veces; purgar dos veces seguidas; `deleteUser` de un inexistente |
| Concurrencia | Activación; purga frente a activación | Dos activaciones con barrera, 10 repeticiones; fila activada entre la consulta y el borrado (REQ-VC-25) |
| Falla a medias | Purga | Firebase rechaza una cuenta; la fila no se borra tras borrar la credencial; la transacción de fila y evento se deshace si falla el `INSERT` |
| Dependencia caída | Activación sin Firebase; purga con Firebase caído | REQ-VC-09 y REQ-VC-26 |
| Colecciones | Purga | Lote vacío, una, `max` y `max + 1` candidatas |
| Datos sin fecha de nacimiento | Purga y activación | Cuentas antiguas con `fecha_nacimiento` nula (CM-297 D2): activar y borrar no la leen; la carga del evento no la lleva |
| Propiedad | Activación | Identidad solo del encabezado; ninguna otra entrada elige la cuenta |
| Carga grande | Activación | Cuerpo ignorado: no se lee |
| Ataque | Activación y Gateway | `X-User-Id` con forma de inyección SQL → 404 (consulta parametrizada); cabecera de verificación falsificada |

## 10. Reutilización

Se reutilizan: `ActivateAccountService`, `ActivateAccountCommand`, `AccountActivationController`, `ActivatedAccountResponse`, `Account.activate()`, `AccountRepository` y su adaptador, `FirebaseUserDirectory` y su adaptador (`unavailableOrRejection`, `deleteUser` idempotente), `IdentityRequiredException` y `GetCurrentAccountService.MAX_FIREBASE_UID_LENGTH`, `BusinessException`, `BusinessExceptionHandler` (`rechazo`), `ErrorCode`, `DependencyUnavailableException`, `OutboxRepository` y su adaptador (`insertIfAbsent`), `CorrelationId.fromRequestIdOrElse`, el patrón de `AccountRecordingService` (transacción de fila y evento, reloj por constructor), el patrón del Job de relevo (`AccountEventsRelayJobRunner`, `CuentasApplication.exitWhenOneShotJob`, `application-account-events-relay.properties`), el de `AccountEventsProperties` (`@ConfigurationProperties` + `@Validated`), y los dobles `InMemoryAccountRepository`, `InMemoryOutboxRepository`, `InMemoryFirebaseUserDirectory` (`marcarCorreoVerificado`, `fallarAlConsultar`, `crearSinCuentaLocal`, `reloj`).

Se crean, porque no existe nada para esos casos: `AccountDisabledException` y `AccountConflictException` (cada una con código y estado propios); `AccountDeleted` y `AccountDeletedPayloadV1` (segundo tipo de evento); `UserVerificationStatus` y `PurgeSummary`; `PendingAccountDeletionService` (transacción de borrado y evento, como `AccountRecordingService` lo es para la creación); `PurgeUnverifiedAccountsService`, `PurgeProperties` y `PurgeJobRunner`.

## 11. Decisiones

| # | Decisión | Porqué | Alternativas descartadas | Decisión humana |
|---|---|---|---|---|
| D1 | Token estricto: se elimina la consulta de respaldo a Firebase en la activación | CA-1.2.6: la verificación se toma solo del token | Mantener la consulta (contradice el CA) | PO (backlog v4, 5-oct-2026; vigente en v6) |
| D2 | `ANONYMIZED` responde como «sin cuenta» (404) y `DISABLED` 403 con el texto de CA-1.3.11 | No revelar que existió; el texto ya existe en el backlog | 409 para ambas; 500 (defecto actual) | Cerrada sin consulta (CA-1.3.11; misma regla en `GET /users/me`) |
| D3 | Se quita `@Transactional` de `activate` y el adaptador traduce el conflicto de versión a una excepción de dominio | El fallo de versión ocurre al confirmar, fuera del método; sin transacción, el servicio puede releer y responder 200 | Capturarlo en el manejador global (no permite releer); bloqueo pesimista (innecesario para una fila) | Paula, 6-oct-2026 (pregunta 5) |
| D4 | La purga es un servicio de aplicación que se ejecuta una vez por invocación; la dispara un Cloud Run Job con Cloud Scheduler | Cloud Run no garantiza instancia viva para `@Scheduled` | `@Scheduled`; ruta HTTP interna | Paula, 5-oct-2026 |
| D5 | Tope de 1000 cuentas por ejecución | Acota tiempo y consumo | Sin tope; paginar hasta agotar | Paula, 6-oct-2026 (pregunta 6) |
| D6 | Se acepta la ventana entre «¿verificada?» y el borrado | Milisegundos; el orden «credencial y después fila» lo fija la nota técnica de HU-1.2 | Volver a consultar justo antes de borrar (reduce, no elimina) | Paula, 6-oct-2026 (pregunta 8) |
| D7 | Un PR por repositorio: uno en Cuentas (piezas 1, 2 y 4, con un commit por capacidad) y uno en el Gateway (pieza 3) | Regla de un PR por CM (10-oct-2026); el Gateway y Cuentas se despliegan por separado | Cuatro PR (regla anterior) | Regla de Paula, 10-oct-2026 |
| D8 | La identidad vacía o larga reutiliza `IDENTITY_REQUIRED` | `GET /users/me` ya lo publica así (CM-297, `docs/errores.md`); dos rutas iguales no deben responder distinto | Un código nuevo `IDENTITY_INVALID_VALUE` (dos contratos para la misma falta) | Cambio sin alternativa: precedente fusionado |
| D9 | `cuenta.eliminada` se guarda en la tabla de salida en la transacción del borrado y lo publica el Job de relevo existente | Backlog v6 (CA-1.2.10, cambio v5 O52); reutiliza el outbox de CM-279; el Job de purga no necesita hablar con el broker | Publicar directo desde la purga (pierde eventos si el broker cae) | Cambio sin alternativa (PO, 8-oct-2026, cambios v5) |
| D10 | Firebase caído detiene la purga y el Job sale con código distinto de 0 | Con el directorio caído fallarían todas; el reintento del Job es el mecanismo de recuperación, como en el Job de relevo | Seguir con todas (1000 llamadas inútiles) | Técnica, con precedente (`AccountEventsRelayJobRunner`); aprobada por Paula con la spec (11-oct-2026) |
| D11 | Textos de la activación con punto final | Estilo del catálogo (D2-01, observación de RT-01); Paula pidió en CM-36 cambiarlos aquí una sola vez | Dejarlos | Paula, 7-oct-2026 (sección «Pendientes que deja CM-36») |
| D12 | Una fila activa sin credencial tras la purga se registra en `ERROR` | Requiere conciliación manual («ERROR = alguien debe actuar») | `WARN` (no alerta) | Técnica; aprobada por Paula con la spec (11-oct-2026) |
| D13 | El estado de la cuenta decide primero y la afirmación del Gateway solo cuenta para la cuenta `PENDING_VERIFICATION` | CA-1.2.6 habla de la cuenta pendiente; CA-1.2.4 pide dar por buena la confirmación repetida de una cuenta activa; REQ-VC-06 pide que la anonimizada no se distinga de la inexistente. Un token anterior a la verificación sigue diciendo `false` y no debe rechazar a quien ya está activo | Rechazar con 403 todo encabezado distinto de `true` sin mirar el estado (filtra la anonimizada y rechaza a la activa) | Paula, 11-oct-2026 ("elige lo que sea mejor") |
| D14 | La cuenta lleva la versión de la fila que se leyó y el guardado la compara con la actual | El adaptador recargaba la fila con su versión vigente y pisaba el cambio ajeno sin que el bloqueo optimista lo notara (una cuenta deshabilitada en medio se reactivaba) | Dejar solo el bloqueo optimista de JPA (no cubre la ventana entre la lectura del servicio y la recarga del adaptador); bloqueo pesimista | Paula, 11-oct-2026 ("corregimos") |

## 12. Pruebas (resumen; el detalle está en `tasks.md`)

- **Pieza 1:** activación feliz; ya activa (sin escritura: `fecha_actualizacion` y `version` iguales con base real); sin cuenta 404; `DISABLED` 403; `ANONYMIZED` 404 idéntico; encabezado `false`, ausente, vacío, `TRUE` y `abc` → 403 sin llamar a Firebase; `X-User-Id` ausente, vacío, solo espacios, 128 y 129; cuerpo `{"emailVerified":true}` ignorado; Firebase caído con `true` → 200; dos activaciones simultáneas → 200 y 200, `version` +1 una vez; 20 activaciones repetidas sin efecto; el log no contiene correo.
- **Pieza 2:** 167 h 59 min, 168 h y 168 h 1 min; verificada → activada; no verificada → credencial antes que fila, y evento; credencial ausente → fila y evento; fallo en una de tres; fila ya activa tras borrar la credencial → `ERROR` y sin evento; Firebase caído → se detiene y lanza; lote vacío; `max` y `max + 1`; correo libre (registro 201); dos ejecuciones seguidas; transacción de fila y evento (si falla el evento, la fila sigue); migraciones (índice parcial; `CHECK` acepta `cuenta.eliminada` y sigue rechazando un tipo desconocido); carga del evento exacta; el log sin datos personales.
- **Pieza 3:** spec del Gateway, sección 7.
- **Pieza 4:** el ejecutor llama una vez; avisa con fallidas; el perfil arranca sin servidor web; `exitWhenOneShotJob` también termina con `purge-job`; si la purga lanza, el arranque falla; `CLOUD_RUN_JOB` con emulador → no arranca.
- **Postman/Newman:** carpeta «Activación» en Cuentas y carpeta «Correo sin verificar» en el Gateway (con el emulador).
- `LayeredArchitectureTest`, `UntypedExceptionClassificationTest` y `ErrorCodeDocumentationTest` en verde. Cobertura ≥ 90 % de líneas y de ramas de lo nuevo o modificado.

## 13. Fuera de alcance y acciones de otros

**Fuera:** plantilla del correo, remitente y DNS (CM-255) · pantallas y llamadas de `cameia-web` (CM-180) · recuperación de contraseña (HU-1.4) · sincronización de una cuenta verificada sin fila (CA-1.3.9, fuera del MVP) · el Job, el Scheduler y la cuenta de servicio en `cameia-infra` (DevOps) · RT-01 no aplica a esta HU · registro repetido (CM-251, cerrado).

**Acciones con destino** (nada se envía desde esta spec; cada una va al documento de su rol):

| # | Para | Qué | Estado y fuente |
|---|---|---|---|
| A1 | DevOps (Juan Diego Gomez), por tarea que abre Vela | Cloud Run Job de la purga con la imagen de Cuentas, `SPRING_PROFILES_ACTIVE=prod,purge-job`, Cloud Scheduler a las 03:00 `America/Bogota`, y las **mismas variables que el servicio, incluidas `SPRING_RABBITMQ_*`**: `BrokerSettingsGuard` impide arrancar el perfil `prod` sin ellas aunque la purga no publique. La cuenta de servicio necesita leer, listar y borrar usuarios de Firebase Auth | Pedido el 6-oct (`comunicaciones/enviadas/05102026_v1_solicitudes-a-devops.md`, punto 1; vence el 13-oct). Sin respuesta (`comunicaciones/seguimiento/respuestas-y-pendientes.md`, fila 46). Falta agregar las variables del broker y el permiso de listar |
| A2 | DevOps | Job `account-events-relay` cada 5 min: publica también `cuenta.eliminada` | Pedido el 9 y 10-oct; sin respuesta (`cronograma/cronograma.md`, fila 88) |
| A3 | Frontend (Ana Sofía y Juan José) | Aviso al desplegar: 403 `EMAIL_NOT_VERIFIED` del Gateway en rutas privadas (aplicar RT-02-CA06); activación con 403 `ACCOUNT_DISABLED` y 409 `ACCOUNT_CONFLICT` nuevos; textos con punto en `ACCOUNT_NOT_FOUND` e `IDENTITY_REQUIRED` (también en `GET /users/me`); 403 de Cuentas con «Tu correo aún no está verificado.» | Se redacta al abrir los PR. El 11-oct ya se envió a Frontend que el 403 del Gateway no existía aún |
| A4 | Vela | Mover CM-179 al Sprint 3 y registrar su estimación (sección 15); en Jira no tiene horas | Traslado pedido el 10-oct (parte 4.1); estimación sin informar |
| A5 | CM-296 (tanda 2) | `docs/estandar-backend.md` de los cuatro repos y la skill dicen que `EMAIL_NOT_VERIFIED` es un código «reservado que ningún servicio emite»; deja de serlo al desplegar el Gateway | Destino: CM-296 |
| A6 | Frontend | `cameia-web` no llama a `POST /api/v1/users/me/verification` tras el ingreso (su `src/features/auth/SPEC.md`, línea 276, lo deja «para una iteración posterior»). Falta la parte F de CA-1.2.14. Mientras no exista, la cuenta queda `PENDING_VERIFICATION` aunque la persona haya verificado. No bloquea nada, porque ningún servicio exige `ACTIVE`, y la purga la activa al revisarla (REQ-VC-22), hasta 8 días después | Comprobado en `cameia-web` `develop` `4b3f915` el 11-oct. Va en el mismo aviso que A3 |
| A7 | Frontend | **El registro deja una sesión sin verificar y lleva al área privada.** `useRegister` envía el correo (línea 67) pero guarda la sesión, abre el modal y navega a `/inicio` (líneas 109 a 120; también tras un error de red, líneas 97 a 99). CA-1.1.1 pide no iniciar la sesión y mostrar «Verifica tu correo». Lo reprodujo Paula el 11-oct al registrarse. Con el 403 del Gateway desplegado, la primera llamada privada de esa sesión recibe 403 `EMAIL_NOT_VERIFIED`, y la persona solo sale bien si `cameia-web` aplica RT-02-CA06 | Comprobado en el código el 11-oct. Es de Frontend (HU-1.1, pantalla «Verifica tu correo»). Va en el mismo aviso que A3, como corrección que conviene desplegar antes o junto con el Gateway |
| A8 | Paula (Backend) | Probar en staging CA-1.2.9 y CA-1.2.10 ajustando la fecha de creación de cuentas de prueba sintéticas (sección 22) | Paula tiene acceso a la base de staging (lo dijo el 11-oct). Se hace después del despliegue del Job |

## 14. Preguntas

| # | Pregunta | Estado | Bloquea |
|---|---|---|---|
| 1 | ¿GW-TBD-26 («propagar», 18-sep) queda sin efecto frente a CA-1.2.13? | Cerrada sin consulta: CA-1.2.13 (v4 y v6) es posterior y explícito | — |
| 2 | Estado, código y texto de `DISABLED` y `ANONYMIZED` en la activación | Cerrada sin consulta (D2) | — |
| 3 | ¿El 403 `EMAIL_NOT_VERIFIED` de Cuentas usa el texto del Gateway? | Cerrada sin consulta: un código, un mensaje | — |
| 4 | V-04: respuesta de `applyActionCode` con el emulador | Respondida (Paula, 6-oct: sí). Sin trabajo de Backend: CM-180 está «En revisión» y Testing prueba CA-1.2.2 en staging | — |
| 5 | D3 | Respondida (Paula, 6-oct) | — |
| 6 | D5 | Respondida (Paula, 6-oct) | — |
| 7 | Índice parcial | Respondida (Paula, 6-oct: sí) | — |
| 8 | D6 | Respondida (Paula, 6-oct) | — |
| 9 | Job y Scheduler de la purga | PENDIENTE de Juan Diego Gomez (A1) | Prueba en staging de la pieza 4 |
| 10 | Estimación | No es pregunta: se informa a Vela (A4) | — |
| 11 | Cuatro PR | Sustituida por D7 (regla del 10-oct) | — |
| 12 | CM-260 antes del Gateway | Cumplida: CM-260 está «Finalizado» y su constante `APPLICATION_JSON_UTF8` está en `develop` | — |
| 13 | Orden respecto de CM-36 y CM-251 | Cumplida: ambas fusionadas | — |
| 14 | `CLOUD_RUN_JOB` en la guarda del emulador | Respondida (Paula, 6-oct: sí) | — |
| 15 | Nivel del log de una cuenta fallida | Respondida (Paula, 6-oct: `WARN` sin traza) | — |
| 16 | ¿La purga concilia las credenciales huérfanas del registro? | Respondida (Paula, 8-oct: sí, con más de 300 s). Su alcance se vuelve a preguntar en la 18 por un riesgo hallado al revalidar | — |
| 17 | Registro previo del correo de otra persona | Cerrada por el backlog v6 (sección 8, «Riesgo residual») | — |
| 18 | **Alcance de la conciliación de credenciales huérfanas.** La conciliación parte de la lista de usuarios de Firebase, no de la base. Si una base y un proyecto de Firebase no corresponden (una base local con credenciales reales, o un proyecto de Firebase compartido entre dos ambientes), el criterio «más de 300 s sin fila» borraría credenciales reales de personas. (A) 300 s sin fila, como se decidió el 8-oct, más proveedor `password` y tope de 1000. (B) Sin conciliación automática: el registro ya deja el `uid` en el log para conciliación manual. (C) Solo credenciales con proveedor `password`, correo **sin verificar**, de más de 168 h y sin fila. Esas credenciales no pueden usarse (CA-1.3.7, CA-1.2.13), así que borrarlas es inofensivo aunque las bases no correspondan; una huérfana verificada queda para conciliación manual. **Recomendación: C.** | **Respondida (Paula, 11-oct-2026): C** | — |

## 15. Estimación

Pieza 1 ≈ 4 h · pieza 2 ≈ 9 h (purga 6 h, evento y migración 2 h, conciliación con la opción C 1 h) · pieza 3 ≈ 3 h · pieza 4 ≈ 1,5 h (el patrón del Job ya existe) · Postman y Newman de las dos ≈ 1,5 h · verificación final y PR ≈ 1 h. Total ≈ **20 h** frente a 9 h de toda la HU en el backlog. Se informa a Vela (A4).

## 16. Trazabilidad CA → requisito → prueba → Postman (R12)

| CA | Requisito | Prueba automática (nombre exacto, tarjeta) | Petición de Postman o Newman |
|---|---|---|---|
| 1.2.1, 1.2.2, 1.2.3, 1.2.5, 1.2.7 | — (dueño F) | No es de Backend: los cubre CM-180 (Frontend) y los prueba Testing en staging | — |
| 1.2.4 | REQ-VC-03 | `ActivateAccountServiceTest#activarDosVecesNoFallaNiVuelveAGuardar`; `AccountRegistrationEndToEndTest#activate_shouldNotWrite_whenAlreadyActive` (T-1.3, T-1.5) | `A-02 · 200 activación repetida` |
| 1.2.6 | REQ-VC-01 | `ActivateAccountServiceTest#activate_shouldRejectWithoutCallingFirebase_whenHeaderIsNotTrue`; `AccountActivationControllerTest#activate_shouldPassNotVerified_whenHeaderIsNotTrue`, `#activate_shouldPassNotVerified_whenHeaderIsMissing` y `#activate_shouldIgnoreBody_whenItClaimsVerification`; `AccountRegistrationEndToEndTest#activate_shouldReturn403AndKeepPending_whenHeaderIsNotTrue` | `A-03`, `A-04`, `A-05`, `A-06` |
| 1.2.8 | — (dueño F, configuración de DevOps) | CM-255 | — |
| 1.2.9 | REQ-VC-21 | `PurgeUnverifiedAccountsServiceTest#purge_shouldAskForCandidatesOlderThan168Hours_whenRun`; `AccountRepositoryAdapterTest` (168 h exactas no entra; 169 h sí) (T-2.4, T-2.7) | Sin ruta HTTP (Job): prueba manual de T-4.4 con la salida del resumen |
| 1.2.10 | REQ-VC-22, 24 a 27 | `PurgeUnverifiedAccountsServiceTest` casos 2 a 6 y 10; `PendingAccountDeletionTransactionTest`; `AccountDeletedPayloadV1Test`; `PurgeEndToEndTest` (1) y (3) | Prueba manual de T-4.4 (fila `cuenta.eliminada` en `evento_saliente`) |
| 1.2.11 | REQ-VC-30 | `PurgeEndToEndTest` (1): registrar de nuevo → 201 | Prueba manual de T-4.4: `Registro · 1` con el mismo correo tras la purga |
| 1.2.12 | (CA-1.1.30, hecho en CM-251) | `AccountRegistrationEndToEndTest#elSegundoRegistroConElMismoCorreoDevuelveLaCuentaPendiente` (existente) | `Registro · 2 · 200 el mismo registro otra vez` (existente) |
| 1.2.13 | REQ-VC-40 a 45 (REQ-EV-01 a 10) | `FirebaseAuthGlobalFilterTest` (spec del Gateway, T-3.1) | Gateway: `V-01`, `V-02`, `V-03` |
| 1.2.14 | REQ-VC-02 | `ActivateAccountServiceTest#activaLaCuentaCuandoElGatewayConfirmaElCorreoVerificado`; `AccountRegistrationEndToEndTest#activate_shouldChangeStatusAndUpdateDate_whenPending` | `A-01 · 200 activación` |

**Requisitos sin CA y su razón:**
- **REQ-VC-04 a 08** cierran rutas al 500 genérico (defectos 1, 4, 5 y 6; regla de errores visibles).
- **REQ-VC-09 y 10** responden a fiabilidad y privacidad.
- **REQ-VC-11 y 54** son documentación (R8).
- **REQ-VC-20, 23, 28, 29, 31 a 33 y 50 a 53** concretan las notas técnicas de HU-1.2 y la decisión de la pregunta 18.

Sus pruebas: `AccountActivationControllerTest#activate_shouldReturn403AccountDisabled_whenAccountIsDisabled`, `#activate_shouldReturn409AccountConflict_whenConflictPersists`, `#activate_shouldReturn400IdentityRequired_whenIdentityIsBlank`; `ActivateAccountServiceTest` casos 7 a 10; `AccountRegistrationEndToEndTest` casos 3, 4, 6 y 7; `PurgeUnverifiedAccountsServiceTest` casos 7 a 9; `PurgePropertiesTest`; `PurgeJobRunnerTest`; `CuentasApplicationTest`; `FirebaseConfigurationTest`. Postman: `A-07` a `A-11`.

## 17. Matriz de validación por entrada (R2)

La ruta no tiene cuerpo: solo dos encabezados que pone el Gateway. No hay DTO de entrada ni Bean Validation; la validación vive en el servicio y en el controlador.

| Entrada | Valor literal | Resultado | `code` | Mensaje | Prueba |
|---|---|---|---|---|---|
| `X-User-Id` | ausente | 400 | `IDENTITY_REQUIRED` | «La petición no incluye los datos que exige esta ruta.» | controlador (existente); `A-07` |
| `X-User-Id` | `""` | 400 | `IDENTITY_REQUIRED` | ídem | servicio caso 8; `A-08` |
| `X-User-Id` | tres espacios y un tabulador | 400 | `IDENTITY_REQUIRED` | ídem | servicio caso 8 (el `@ValueSource` incluye el tabulador) |
| `X-User-Id` | 127 y 128 caracteres ASCII sin cuenta | 404 (pasó la validación) | `ACCOUNT_NOT_FOUND` | «No encontramos una cuenta para este usuario.» | servicio caso 8 |
| `X-User-Id` | 129 caracteres | 400 | `IDENTITY_REQUIRED` | ídem | servicio caso 8; `A-09` |
| `X-User-Id` | texto con forma de inyección SQL (comilla, `OR`, `1=1`) | 404 (consulta parametrizada) | `ACCOUNT_NOT_FOUND` | ídem | `A-10` con ese valor |
| `X-User-Email-Verified` | `true` | 200 | — | — | `A-01` |
| `X-User-Email-Verified` | ausente, vacío, `false`, `TRUE`, `true` con un espacio delante, `abc` | 403 | `EMAIL_NOT_VERIFIED` | «Tu correo aún no está verificado.» | controlador (parametrizada; incluye el espacio delante); `A-03` a `A-05` |
| Cuerpo | `{"emailVerified":true}`, `{"status":"ACTIVE"}` o texto que no es JSON | Se ignora (no se lee) | — | — | controlador `#activate_shouldIgnoreBody_whenItClaimsVerification`; `A-06` |

**Orden de evaluación:**
1. Identidad: 400.
2. Cuenta: 404.
3. Estado: `ACTIVE` 200 sin mirar el encabezado, `DISABLED` 403, `ANONYMIZED` 404 (decisión D13).
4. Verificación, solo para la cuenta `PENDING_VERIFICATION`: 403.
5. Guardado: 409 o 200.

La purga no recibe entradas externas. Su único parámetro es `cuentas.purge.max-accounts-per-run`, de 1 a 1000; `PurgePropertiesTest` prueba que 0 y 1001 no arrancan.

## 18. Plan de evidencia por regla R1–R14

Lo que el ejecutor entrega en la tabla «regla → evidencia» de `ejecucion/evidencia/CM-179/resumen.md`. Una fila sin salida real cuenta como `incumple`.

| Regla | Qué la demuestra en esta CM |
|---|---|
| R1 Idioma | `git diff` sin identificadores en español nuevos; Javadoc, logs, `@DisplayName` y OpenAPI en español; pruebas nuevas en inglés |
| R2 Validación | Matriz de la sección 17, cada fila con su prueba en verde |
| R3 Errores | Las cuatro filas de deuda de `docs/errores.md` cerradas; `UntypedExceptionClassificationTest` y `ErrorCodeDocumentationTest` en verde; la petición real `A-05` (`abc`) responde 403 y no 500 |
| R4 Base de datos | Migraciones `V6` y `V7` con sus pruebas (`CuentaSchemaMigrationTest`, `EventoSalienteSchemaMigrationTest`, `MigracionEventoSalienteConDatosTest`); borrado condicionado probado con base real |
| R5 Casos borde | Sección 9, celda por celda, con su prueba |
| R6 Seguridad | ASVS 1.2.4 (consultas parametrizadas: `A-10`), 2.2.1 (sección 17), 4.1.1 (charset en el 403 del Gateway), 7.2.1 y 8.2.1 (verificación solo del token), 8.2.2 (identidad solo del encabezado), 8.3.1 (control en el servidor), 14.2.1 (nada sensible en la URL). OWASP API1, API3, API4, API5, API6, API8 y API10 (sección 8). `/security-review` sin hallazgos abiertos |
| R7 Código lean | Reutilización de la sección 10; métodos ≤ 20 líneas; `LayeredArchitectureTest` en verde; `/simplify` aplicado |
| R8 Documentación | OpenAPI de la ruta (`A-11`); Javadoc en cada clase y método nuevo; `docs/eventos/cuenta-eliminada-v1.*`; `docs/errores.md`; `CLAUDE.md` y `.env.example` |
| R9 Pruebas | Matriz de `plan.md` y tarjetas; suite completa en verde en `clean verify` |
| R10 Cobertura | JaCoCo ≥ 90 % de líneas y de ramas por clase nueva o modificada, con cada hueco justificado |
| R11 Postman y Newman | Carpeta `Activación` (`A-00` a `A-12`) más `Registro` y `Mi cuenta`, corridas con Newman y salida guardada; en el Gateway, `V-00` a `V-03` |
| R12 Backlog | Sección 16, cada fila en verde |
| R13 Atributos | Tabla de la sección 8, cada métrica con su prueba en verde |
| R14 Prohibiciones | Sin push, PR, merge ni cambios de Jira fuera de lo permitido; sin `[IA-ASISTIDO]` en commits; sin secretos leídos |

## 19. Línea base

Antes de tocar nada, en la rama ya rebasada:

- **Cuentas:** `./mvnw.cmd -B -ntp clean verify` con Docker encendido (Testcontainers). Esperado: BUILD SUCCESS, 933 pruebas, 0 fallos. Se midió sobre `621a846` el 11-oct-2026 (`analisis/revisiones/hallazgos-cobertura_11oct2026.md`, fila C-2).
- **Gateway:** `./mvnw.cmd -B clean verify` o `docker compose run --rm verify`. Esperado: BUILD SUCCESS, 114 pruebas. Se midió sobre `53fbe30` el 11-oct-2026 (misma fuente, fila C-3).
- Si `develop` avanzó, el número de pruebas puede ser mayor; lo que vale es 0 fallos. Si la línea base no está en verde, el ejecutor se detiene y lo reporta.

## 20. Lista de verificación de la CM

El ejecutor la corre fila por fila antes de entregar y anota la salida real. El verificador la repite como piso.

| # | Qué se comprueba | Cómo (prueba, comando o petición) | Resultado esperado |
|---|---|---|---|
| L-01 | Línea base de Cuentas y del Gateway | Sección 19 | 0 fallos |
| L-02 | CA-1.2.14: activa una cuenta pendiente | `A-01`; E2E `activate_shouldChangeStatusAndUpdateDate_whenPending` | 200 `{"id":…,"status":"ACTIVE"}`; `fecha_actualizacion` posterior; `version` + 1 |
| L-03 | CA-1.2.4: repetir no escribe | `A-02`; E2E `activate_shouldNotWrite_whenAlreadyActive` (20 repeticiones) | 200; `fecha_actualizacion` y `version` iguales |
| L-04 | CA-1.2.6: manda el encabezado, no el cuerpo ni Firebase | `A-03` a `A-06`; pruebas de servicio y de controlador | 403 `EMAIL_NOT_VERIFIED`, «Tu correo aún no está verificado.»; la cuenta sigue `PENDING_VERIFICATION`; cero llamadas a Firebase |
| L-05 | Identidad inválida | `A-07` a `A-09`; servicio caso 8 | 400 `IDENTITY_REQUIRED` con punto final; sin lectura de la base |
| L-06 | Cuenta deshabilitada, anonimizada o inexistente | E2E casos 3 y 4; `A-10` | 403 `ACCOUNT_DISABLED`; 404 idéntico para anonimizada e inexistente |
| L-07 | Concurrencia de la activación | E2E `activate_shouldSucceedOnce_whenTwoRequestsRace`, tres corridas | 200 y 200; `version` + 1 exacto; nunca 500 |
| L-08 | Firebase caído no afecta la activación | E2E `activate_shouldNotNeedFirebase_whenHeaderIsTrue` | 200 |
| L-09 | CA-1.2.9: límite de 168 h | `AccountRepositoryAdapterTest`; servicio de purga caso 1 | 168 h exactas se conserva; 168 h 1 min y 169 h entran |
| L-10 | CA-1.2.10: la verificada se activa; la no verificada se borra con evento | Servicio de purga casos 2 a 4; `PurgeEndToEndTest` (1) y (3) | Credencial antes que fila; un `cuenta.eliminada` por cuenta borrada; ninguno por la activada |
| L-11 | El evento cumple el contrato | `AccountDeletedPayloadV1Test` contra el esquema | `{"usuarioId":"uid-1","eliminadaEn":"2026-10-10T08:00:00.000Z"}` exacto |
| L-12 | Fila y evento en la misma transacción | `PendingAccountDeletionTransactionTest` | Si falla el evento, la fila sigue; si la cuenta está activa, no se borra |
| L-13 | CA-1.2.11: el correo queda libre | `PurgeEndToEndTest` (1) | Nuevo registro 201 con otro `firebaseUid` |
| L-14 | Falla a medias y dependencia caída | Servicio de purga casos 5, 6 y 10 | Rechazo: sigue con las demás. Fila activada entre medias: `ERROR` sin evento. Firebase caído: se detiene y lanza |
| L-15 | Tope y lote vacío | Servicio de purga casos 7 y 8; `PurgePropertiesTest` | `max` respetado y `candidatesLeft` correcto; ceros sin llamadas; 0 y 1001 no arrancan |
| L-16 | Conciliación de huérfanas (opción C) | T-2.8: cinco casos y E2E | Solo se borra la huérfana sin verificar, de más de 168 h y con proveedor `password` |
| L-17 | Modo tarea de punta a punta | Prueba manual de T-4.4; `PurgeJobRunnerTest`; `CuentasApplicationTest` | Sin puerto abierto; resumen en el log; código de salida 0; con Firebase caído, distinto de 0 |
| L-18 | Emulador bloqueado en un Job | `FirebaseConfigurationTest` con `CLOUD_RUN_JOB` | El arranque falla con el mensaje nuevo |
| L-19 | CA-1.2.13: 403 del Gateway | Lista de la spec del Gateway (G-01 a G-10) | Ver allí |
| L-20 | Ningún dato personal en logs | Pruebas con `OutputCaptureExtension` (activación, purga, Gateway) y revisión a mano de cada log nuevo en el diff | Sin correo, nombre, token ni mensaje de Firebase |
| L-21 | Ninguna ruta previsible llega al 500 | Peticiones reales `A-05` (`abc`), `A-06` (cuerpo no JSON) y `A-09` (129 caracteres); `UntypedExceptionClassificationTest` | Ninguna responde 500 |
| L-22 | Catálogo y documentación | `ErrorCodeDocumentationTest`; `A-11` (OpenAPI); revisión de `docs/eventos/` | En verde; la ruta publica 200, 400, 403, 404, 409 y 500 |
| L-23 | Desempeño (DES-02) | Newman, carpeta `Activación` | Cada petición en menos de 2000 ms |
| L-24 | Cobertura | `clean verify` y `target/site/jacoco/jacoco.csv` | ≥ 90 % de líneas y de ramas por clase tocada |
| L-25 | Arquitectura | `LayeredArchitectureTest` | En verde: la purga no importa Firebase ni JPA |
| L-26 | Adversarial: recurso ajeno | `A-12` con el uid de otra cuenta en `X-User-Id` y sin `X-User-Email-Verified: true` | 403: solo el Gateway puede afirmar identidad y verificación, y lo hace desde el token |
| L-27 | Adversarial: doble envío | `A-01` dos veces seguidas | 200 y 200, una sola escritura |
| L-28 | Adversarial: Unicode en la identidad | `X-User-Id` con 128 «ñ» | 404 (pasó la validación y no hay cuenta), nunca 500 |

## 21. Puerta de listo (comprobada el 11-oct-2026)

| # | Condición (reglas obligatorias, «Puerta de listo») | Dónde | Cumple |
|---|---|---|---|
| 1 | CA → requisito → prueba → Postman, y atributos con métrica y prueba | Secciones 16 y 8 | Sí |
| 2 | Matriz por entrada, casos borde, ruta al 500 cerrada y plan de seguridad con ASVS y OWASP | Secciones 17, 9, 3, 8 y 18 (R6) | Sí |
| 3 | Contraste con `origin/develop` (`621a846`, `53fbe30`): cada archivo, clase, firma, migración y prueba de las tarjetas existe o se crea donde dice | `tasks.md`, revisado archivo por archivo el 11-oct | Sí |
| 4 | Integración: próxima migración `V6`; `ErrorCode` y `docs/errores.md` sembrados en T-0; colección de Postman; un PR por repositorio con commits por capacidad | `plan.md` §1 | Sí |
| 5 | Línea base verde | Sección 19 | Sí |
| 6 | Lista de verificación | Sección 20 | Sí |
| 7 | Ninguna duda sin respuesta que bloquee | Sección 14: la pregunta 18 está respondida. La 9 (Job de DevOps) queda PENDIENTE de Juan Diego Gomez; no bloquea el código, solo la prueba en staging | Sí |

**Hechos, inferencias y decisiones.**
- **Hechos confirmados** (con ruta y SHA): los defectos 1 a 5 y 7 a 9 de la sección 3, y todo lo que la sección 2 dice del código.
- **Inferencia:** el defecto 6 (500 en la carrera de dos activaciones). Lo confirma la primera prueba de T-1.5.
- **Decisiones humanas:** sección 11 y columna «Estado» de la sección 14.

## 22. Cómo se prueba en cada ambiente

| Ambiente | Quién | Cómo se crea y se verifica una cuenta | Cómo se prueba la purga |
|---|---|---|---|
| Pruebas automáticas | Backend y Frontend | Sin correo: dobles de Firebase (`InMemoryFirebaseUserDirectory` en Cuentas, `mockToken` en el Gateway, `vi.fn()` en `cameia-web`). `marcarCorreoVerificado(uid)` simula la verificación | Reloj fijo y filas envejecidas por SQL con Testcontainers (T-2.4, T-2.7) |
| Local | Backend y Frontend | Emulador de Firebase Auth del compose del Gateway (puerto 9099). `cameia-web` ya lo usa con `VITE_FIREBASE_AUTH_EMULATOR_HOST`. El emulador no envía correos: el enlace se lee en `GET http://localhost:9099/emulator/v1/projects/<proyecto>/oobCodes` y, al abrirlo, el correo queda verificado | Registrar, envejecer la fila en la base local (`update microcuentas.cuenta set fecha_creacion = now() - interval '169 hours', fecha_actualizacion = now() - interval '169 hours' where firebase_uid = '<uid>'`) y correr el modo tarea (T-4.4) |
| Staging | Testing y Backend | Firebase real: el correo llega de verdad (hoy a spam, hasta que se cierre CM-255). Un solo buzón sirve para muchas cuentas con alias (`buzon+1@gmail.com`, `buzon+2@gmail.com`): Firebase las trata como correos distintos y todas llegan al mismo buzón | **Paula** (A8), que tiene acceso a la base: aplicar el mismo `update` a cuentas sintéticas creadas para eso. Para CA-1.2.9, una a 167 h 59 min; para CA-1.2.10, una sin verificar y otra verificada a 169 h. Esperar la ejecución de las 03:00 o lanzar el Job a mano, y comprobar que la primera sigue, la segunda desapareció con su fila `cuenta.eliminada` y la tercera quedó `ACTIVE` |

**Datos de staging que la purga tocará.** El 11-oct había 66 cuentas en staging, todas `PENDING_VERIFICATION`, en parte porque `cameia-web` no llama a la activación (A6). En su primera ejecución, la purga activará las que ya estén verificadas en Firebase y borrará las que lleven más de 7 días sin verificar, junto con su credencial. Antes del primer despliegue del Job se avisa a Testing: sus cuentas de prueba sin verificar desaparecerán, y las verificadas no se tocan.

**Lo que se ve hoy al registrarse (A7).** La app manda el correo pero inicia la sesión y lleva a `/inicio`, en vez de mostrar «Verifica tu correo» (CA-1.1.1). Por eso parece que no pide verificar. Con el 403 del Gateway desplegado, esa sesión no podrá leer ni escribir nada privado. Lo correcto es que Frontend corrija el registro antes o junto con el despliegue del Gateway.

**`LISTA PARA EJECUTAR`.**
