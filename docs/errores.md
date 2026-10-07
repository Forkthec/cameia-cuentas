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
| 500 | Error interno | Cualquier fallo no previsto; el detalle va solo al log y al cliente le llega un mensaje genérico |

## Cómo se agrega un código

Un código nuevo se agrega aquí con la spec que lo introduce, junto a su excepción de negocio, su estado, su mensaje y su prueba. Un código publicado no se reutiliza ni se renombra.
