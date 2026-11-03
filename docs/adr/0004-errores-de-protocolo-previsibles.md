# ADR 0004 · Errores de protocolo previsibles

## Estado

Aceptada. Decisión de Backend, 18 de octubre de 2026; aprobada por Paula Andrea Muñoz Delgado el 19 de octubre de 2026.

## Contexto

Cuentas respondía con un 500 sin formato, con la página HTML del contenedor o con el cuerpo por defecto de Spring a peticiones que un
cliente puede enviar por error o a propósito y que no son un fallo del servicio:

- un formulario con una codificación inválida (`a=%zz`) con `PUT`, `PATCH` o `DELETE` (lo lee `FormContentFilter` antes de que exista un
  manejador de errores) y con `POST /api/v1/users` (Tomcat lanza `InvalidParameterException` al leer el cuerpo);
- un `multipart/*` sin `boundary` en cualquier ruta, que el contenedor parte antes de elegir el controlador;
- un `Accept` ilegible (`;;;`), que respondía 406 sin cuerpo;
- lo que Tomcat reporta con `sendError`, como un `TRACE`, y pedir `/error` directamente, que salían con el cuerpo por defecto de Spring
  Boot;
- un `X-User-Id` con caracteres de control (un tabulador, `0x85`), que se aceptaba como identidad.

Un fallo previsible no debe terminar en el error genérico: cada uno necesita su estado, su código estable y su mensaje.

## Decisión

- **D-1 · Un formulario mal codificado responde 415 `MEDIA_TYPE_NOT_ALLOWED`**, igual que un formulario bien formado: el problema es el
  tipo de contenido, no el dato.
- **D-2 · Se apaga `FormContentFilter`** (`spring.mvc.formcontent.filter.enabled=false`). Cuentas no recibe formularios y la excepción
  ocurre dentro del filtro, antes del manejador.
- **D-3 · Cuentas no agrega `REQUEST_QUERY_INVALID_FORMAT`**: ninguna ruta lee parámetros de consulta, así que el código no se podría
  producir. El primer `@RequestParam` que se agregue usa el código que ya define Perfil, con el mismo texto.
- **D-4 · La identidad con caracteres de control responde 400 `IDENTITY_REQUIRED`**, igual que la identidad ausente. No cambia el
  contrato; el 400 de Cuentas frente al 401 de Perfil es una decisión anterior del equipo.
- **D-5 · `ContainerErrorController` atiende `/error`.** Lanza la excepción del framework que corresponde al estado original
  (404, 405, 406, 415, 400) y la resuelve `BusinessExceptionHandler`, de modo que el `requestId`, el `charset` y el registro salen del
  mismo lugar que el resto. Un estado sin código propio termina en `INTERNAL_ERROR`.
- **D-6 · Objeto de valor `FirebaseUid`** en `domain/model`, con `required`, usado por los tres casos de uso que leen la identidad.
- **D-7 · La excepción de Tomcat se reconoce por el nombre de su clase** (`BadParameterEncoding`), para que `presentation` no importe el
  contenedor.
- **D-8 · Un `multipart` o un cuerpo demasiado grande responde 413 `REQUEST_BODY_TOO_LARGE`.** Los topes se declaran en
  `application.properties` (`spring.servlet.multipart.max-file-size=1MB` y `max-request-size=10MB`, es decir 1 MiB por parte y 10 MiB por
  petición, los valores por defecto de Spring). `BusinessExceptionHandler` atrapa `MaxUploadSizeExceededException` antes que
  `MultipartException`, que la absorbía y la respondía como un 415 engañoso. Un `multipart` que falla por lectura y no por tamaño sigue
  siendo 415 `REQUEST_MULTIPART_INVALID_FORMAT`. El detalle no cita el límite ni lo recibido y se registra en `WARN`. Un 413 que reporta el
  contenedor sale igual por `ContainerErrorController`.
- **Los errores del contenedor llevan la ruta original en `instance`** (no `/error`) y la petición se registra una sola vez: el despacho
  a `/error` reutiliza el `requestId` que ya quedó en el registro y no escribe otra línea.
- **Código nuevo `REQUEST_MULTIPART_INVALID_FORMAT` (415)** para un `multipart/*` que no se pudo leer. Lo pide la tarea que cierra la
  respuesta a las peticiones mal formadas por tipo de contenido.
- **Un `Accept` ilegible responde 406 `MEDIA_TYPE_NOT_ACCEPTABLE` con cuerpo.** El manejador fija `application/problem+json` de forma
  explícita, porque Spring no puede negociar un tipo cuando el `Accept` no se interpreta.

## Por qué

Un formulario mal codificado, un `multipart` roto y un `TRACE` son datos o decisiones del cliente. Responderlos con la misma forma que
el resto de los errores deja a Frontend un solo camino para leerlos y deja el 500 genérico para lo que nadie pudo prever.

## Alternativas descartadas

| Alternativa | Por qué se descartó |
|---|---|
| Un código de cuerpo o de consulta nuevo para el formulario | El problema es el tipo de contenido; ya existe `MEDIA_TYPE_NOT_ALLOWED` |
| Un filtro propio que capture la excepción de `FormContentFilter` | Más código con el mismo resultado que apagarlo |
| Capturar `IllegalStateException` para el rechazo de Tomcat | Atraparía excepciones ajenas |
| Importar la clase de Tomcat en `presentation` | La capa no depende del contenedor |
| Dejar `TRACE` como limitación documentada | Un controlador de errores cubre `TRACE` y cualquier `sendError` futuro |
| Cambiar la identidad a 401 | Cambia el contrato publicado sin que lo pida esta tarea |
| Agregar ya `REQUEST_QUERY_INVALID_FORMAT` | Sería un código que ninguna ruta puede producir |
| Seguir con el 415 `REQUEST_MULTIPART_INVALID_FORMAT` para un `multipart` grande | Dice «tipo de contenido no admitido» cuando la causa es el tamaño: engaña a quien depura y a Frontend |
| Apagar la lectura de `multipart` (`spring.servlet.multipart.enabled=false`) | `REQUEST_MULTIPART_INVALID_FORMAT` deja de poder producirse, y un `multipart` roto pasaría a otro error sin código propio |
| Dejarlo solo documentado | Deja un 415 con una causa falsa y sin una señal para depurar; el tope de tamaño del cuerpo JSON sí es de otra tarea, pero este rechazo ya lo hace Spring |

## Consecuencias

- Peticiones que daban 500 o una respuesta sin formato pasan a 4xx con su código: 415 (formulario y `multipart`), 405 (`TRACE` y
  formulario en un método no admitido) y 406 con cuerpo.
- Frontend recibe dos códigos nuevos, `REQUEST_MULTIPART_INVALID_FORMAT` (415) y `REQUEST_BODY_TOO_LARGE` (413); no se reemplaza ningún
  código existente. Un `multipart` grande deja de ser 415 y pasa a 413.
- `ContainerErrorController` y `FirebaseUid` quedan como puntos de extensión: un estado nuevo del contenedor se agrega a su `switch`.
- Lo que Tomcat rechaza antes de la aplicación (`%zz` en la ruta, encabezados o URL desmedidos, bytes de control `0x00`–`0x08`,
  `0x0A`–`0x1F` y `0x7F` en un encabezado) sigue respondiendo 400 con HTML y se documenta en `docs/errores.md`.
