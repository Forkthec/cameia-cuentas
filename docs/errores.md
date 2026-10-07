# Errores del servicio

## Formato

Las respuestas de error siguen la [sección 6 del estándar](estandar-backend.md#6-errores). Esta página lista lo que el servicio emite hoy.

## Códigos que el servicio emite

Todavía no emite el campo `code`; se adopta en la primera tarea de código del servicio (ver [ADR 0001](adr/0001-codigo-de-error-y-request-id.md)).

## Respuestas sin código

Todas las produce `BusinessExceptionHandler` como `ProblemDetail` con `Content-Type: application/problem+json`. Las de validación agregan `errors`, con un elemento `field` y `message` por campo rechazado.

| HTTP | Título | Cuándo |
|---|---|---|
| 400 | Petición incompleta | A la ruta le falta un encabezado o un parámetro que exige |
| 403 | Correo sin verificar | La cuenta aún no verificó su correo |
| 404 | Cuenta no encontrada | El usuario autenticado no tiene cuenta local |
| 409 | Correo ya registrado | Se intenta registrar un correo que ya tiene cuenta |
| 422 | Datos no válidos | Un campo incumple las validaciones del contrato (con `errors`), el cuerpo no se puede interpretar o el dominio rechaza un valor al construirlo |
| 422 | Fecha de nacimiento no válida | La fecha de nacimiento no permite registrarse (`errors` con el campo `birthDate`) |
| 422 | Contraseña no válida | La contraseña incumple la política (`errors` con el campo `password`) |
| 500 | Error interno | Cualquier fallo no previsto; el detalle va solo al log y al cliente le llega «No pudimos completar la operación. Inténtalo de nuevo en unos minutos» |

Respuestas publicadas que difieren del [estándar](estandar-backend.md#6-errores). Se conservan porque Frontend ya las consume, y cada una tiene su destino:

| Respuesta actual | Qué pide el estándar | Destino |
|---|---|---|
| 422 cuando el cuerpo no se puede interpretar | 400 para un cuerpo ilegible | Se conserva (decisión de contrato); la tarea de validaciones del registro le agrega el código `REQUEST_BODY_INVALID_FORMAT`. Pasarlo a 400 sería un cambio de contrato con Frontend, con su propia spec |
| 422 con el mensaje de cualquier `IllegalArgumentException` | Ninguna respuesta lleva el mensaje de una excepción de librería | La tarea de validaciones del registro le asigna el código de respaldo `REQUEST_INVALID_VALUE` y, al terminar, cada objeto de valor lanza su excepción de negocio con su código |
| 500 al activar una cuenta bloqueada o anonimizada | Un caso previsible tiene su excepción de negocio, su código y su estado | La tarea de verificación de correo: 403 `ACCOUNT_DISABLED` para la cuenta bloqueada y 404 `ACCOUNT_NOT_FOUND` para la anonimizada |
| `Content-Type: application/problem+json`, sin `charset` | `application/problem+json; charset=UTF-8` | La tarea de validaciones del registro, en su primer PR |

## Cómo se agrega un código

Un código nuevo se agrega aquí con la spec que lo introduce, junto a su excepción de negocio, su estado, su mensaje y su prueba. Un código publicado no se reutiliza ni se renombra.
