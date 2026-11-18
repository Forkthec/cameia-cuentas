# ADR 0006 · Aceptación de la política de tratamiento de datos

## Estado

Propuesta. Decisión de Backend; las decisiones D-1 a D-5 fueron tomadas por Paula Andrea Muñoz Delgado el 12 de noviembre de 2026.
Pasa a Aceptada cuando se revise el PR.

## Contexto

La Ley 1581 de 2012 exige la autorización previa, expresa e informada de la persona para tratar sus datos. El criterio CA-1.1.44 del
backlog pide que Cuentas solo cree una cuenta cuando la persona la da, y que guarde qué versión de la política aceptó y cuándo. Los
textos de la política viven en `cameia-web`; Cuentas solo conoce la versión.

Antes de este cambio, `POST /api/v1/users` no recibía ni guardaba la autorización, y una cuenta no decía qué versión de la política
había aceptado.

## Decisión

- **D-1 · Dos campos nuevos en el registro:** `dataAuthorization` y `policyVersion`. Cada fallo tiene su código estable y sale junto con
  los demás errores del formulario, un elemento por campo: `DATA_AUTHORIZATION_REQUIRED`, `POLICY_VERSION_REQUIRED` (mensaje «Recarga la
  página y vuelve a aceptar la política.») y `POLICY_VERSION_OUTDATED`.
- **D-2 · `GET /api/v1/users/me` informa `policyAcceptance`** con la versión aceptada, la vigente y la fecha. Una cuenta anterior a la
  migración responde `acceptedAt: null`; la base conserva la fecha de creación.
- **D-3 · Solo el valor JSON `true` autoriza.** El texto `"true"`, el número `1`, un arreglo o un objeto responden
  `DATA_AUTHORIZATION_REQUIRED` en el campo.
- **D-4 · La versión vigente sale de la configuración,** con el valor por defecto `2026-11-v1` en `application.properties` y la variable
  `CAMEIA_POLICY_VERSION` para cambiarla. Cambiar de versión exige un PR de Backend coordinado con la web.
- **D-5 · Despliegue coordinado:** el PR de Cuentas se fusiona el mismo día que el de Frontend, para que el registro de la web no falle
  con 422 entre los dos despliegues.
- **I-1 · Un registro repetido sobre una cuenta pendiente** (ADR 0002) no cambia la aceptación guardada.
- **I-2 · Aceptar con la cuenta deshabilitada** responde 403 `ACCOUNT_DISABLED`; con una anonimizada o inexistente, 404
  `ACCOUNT_NOT_FOUND`.
- **I-3 · La versión se recorta** (espacios al inicio y al final) antes de compararse.
- **I-4 · Una versión de más de 20 caracteres** responde `POLICY_VERSION_OUTDATED`: la vigente tiene como máximo 12, así que todo valor
  más largo es distinto de ella.
- **Persistencia.** Migración `V8__aceptacion_politica_cuenta.sql`: `politica_version` (`VARCHAR(20)`, no nula, no vacía) y
  `politica_aceptada_en` (`TIMESTAMPTZ`, no nula) en `microcuentas.cuenta`. Las cuentas anteriores quedan con `sin-aceptar` y la fecha
  de su creación.
- **Ruta nueva.** `POST /api/v1/users/me/policy-acceptance` registra la aceptación de la versión vigente. Es idempotente: repetirla
  responde 200 con la misma fecha, y la escritura concurrente se resuelve releyendo la cuenta.
- **Privacidad.** Los registros nombran la cuenta, nunca la versión ni la fecha. El evento `cuenta.creada` no cambia.

## Por qué

La autorización y la versión se validan en el borde junto con el resto del formulario para que la persona vea todos sus errores a la
vez, y la regla de qué versión es vigente vive en un solo lugar del dominio. La aceptación se guarda en la misma fila de la cuenta
porque versión y fecha viajan siempre juntas y no hay consulta que filtre por ellas.

## Alternativas descartadas

| Alternativa | Por qué se descartó |
|---|---|
| `dataAuthorization` como `Boolean` con `@NotNull` y `@AssertTrue` | `@AssertTrue` acepta `null` y Jackson convierte `"true"` en `true` antes de validar: no se cumpliría D-3 |
| `@JsonFormat(lenient = FALSE)` | El error de Jackson corta la validación y no saldrían todos los errores a la vez |
| Comprobar la versión en el controlador o en el servicio | El error saldría después de los demás y no junto a ellos |
| Definir el formato de la versión con `@Pattern` en la configuración | Duplica la regla; una sola definición en `CurrentPolicy` y el error al construir el bean también impide arrancar |
| Dos campos sueltos en `Account` | Versión y fecha van siempre juntas; `PolicyAcceptance` las agrupa |
| Índice en `politica_version` | Ninguna consulta filtra por ella |
| Solo la variable de entorno para la versión vigente | Obligaba a esperar una tarea de DevOps antes de desplegar |
| Aceptar por unos días el registro sin los campos | Código temporal que incumple el criterio 3 |
| Fusionar Cuentas antes que la web | El registro de la web fallaría con 422 hasta su despliegue |
| Bloquear en el servidor a quien no aceptó la versión vigente | Fuera de alcance: lo resuelve la web; en el servidor sería una tarea nueva que Perfil y Entrevista tendrían que respetar |

## Consecuencias

- Cambiar la versión de la política exige cambiar la web y Cuentas a la vez; entre un despliegue y otro, el registro con la versión
  anterior responde 422 `POLICY_VERSION_OUTDATED`.
- Las cuentas anteriores a la migración aparecen con la versión `sin-aceptar` hasta que la persona acepte la vigente; la web decide cuándo
  pedírselo.
- Frontend maneja tres códigos nuevos en el registro y uno en la ruta de aceptación (`docs/errores.md`).
- Límite conocido: aceptar una versión solo compara por igualdad con la vigente. Si alguien configura una versión anterior a la que
  una cuenta ya aceptó, la aceptación guardada se sobrescribe con la anterior. La variable es de confianza (la define DevOps) y el
  formato `AAAA-MM-vN` permite ordenar versiones si algún día hace falta impedirlo.
- La migración es irreversible sin una nueva: se probó sobre una base con filas (`MigracionPoliticaConDatosTest`).
