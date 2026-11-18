# Spec — Lista de correos permitidos para el registro

- **Tarea:** CM-360 («Backend – Cuentas: en preproducción, aceptar el registro solo de correos permitidos (incluidas sus subdirecciones con +)»), Sprint 5. Responsable: Juan Vela (Backend).
- **Relacionada:** CM-359 (DevOps – restringir el acceso a preproducción al equipo de CAMEIA).
- **Base:** `origin/develop` de `cameia-cuentas` en `823648f` (12-nov-2026). No hay migración.
- **Estado:** APROBADA por Juan Vela el 14-nov-2026.

## 1. Objetivo

Preproducción (`cameia-e245f`, `api-staging.cameia.app`) acepta registros de cualquier persona, y quien se registra puede usar Gemini con el crédito del proyecto. El registro de la aplicación no pasa por el registro de Firebase del navegador: `cameia-web` llama a `POST /api/v1/users` y Cuentas crea la credencial con el Admin SDK. Por eso cerrar el registro en Firebase Authentication no lo cierra (CM-359). El control debe estar en `POST /api/v1/users`.

Cuentas agrega un filtro configurable por variable de entorno que, si tiene valor, solo deja registrarse a los correos o dominios de la lista, y lo aplica antes de crear nada en Firebase o en la base.

## 2. Alcance

| Dentro | Fuera (con destino) |
|---|---|
| Variable `SIGNUP_ALLOWLIST`, su lectura y validación al arrancar | Poner la variable en el paso de preproducción de `.github/workflows/despliegue-continuo.yml` y las pruebas negativa y positiva: DevOps, CM-359 |
| Rechazo 403 `SIGNUP_NOT_ALLOWED` en `POST /api/v1/users` | Bloquear las cuentas que ya se crearon en preproducción con correos ajenos: CM-359 (DevOps) |
| Código en `ErrorCode`, `docs/errores.md`, OpenAPI y `.env.example` | Aplicar la lista en otras rutas (activación, consulta, cierre de sesión): no las usa quien no tiene cuenta |
| Pruebas unitarias, de configuración y del controlador | Producción: no lleva la variable y no cambia |

## 3. Hechos, inferencias y decisiones

| # | Afirmación | Tipo | Fuente |
|---|---|---|---|
| H-1 | `RegisterUserService.validar` construye `EmailAddress` (recorte, NFC y minúsculas) antes de llamar a Firebase | Hecho | `RegisterUserService.java`, `EmailAddress.java` |
| H-2 | `EmailAddress` garantiza una sola `@` y un dominio con al menos un punto | Hecho | Patrón `FORMAT` de `EmailAddress` |
| H-3 | El CD despliega con `--set-env-vars`, que separa variables con coma | Hecho | CM-360 |
| H-4 | El manejador registra cada 4xx en `WARN` con `code` y `requestId`, sin el cuerpo ni el correo | Hecho | `BusinessExceptionHandler`, `docs/errores.md` |
| H-5 | `SIGNUP_NOT_ALLOWED` cumple la forma `<SUJETO>_<CAUSA>`; `NOT_ALLOWED` está en el vocabulario cerrado | Hecho | `docs/estandar-backend.md` §6 |
| D-1 | Una entrada mal formada impide arrancar el servicio; el error nombra la variable y no muestra el valor. Alternativa descartada: ignorarla con un aviso, porque un error de tipeo dejaría el registro abierto o cerrado sin que nadie lo note | Decisión de Juan Vela, 14-nov-2026 | Chat |
| D-2 | Una entrada de dominio (`@unicauca.edu.co`) acepta solo ese dominio exacto, no sus subdominios. Alternativa descartada: incluir `*.unicauca.edu.co`, más amplio de lo que pide la tarjeta | Decisión de Juan Vela, 14-nov-2026 | Chat |
| D-3 | El rechazo ocurre después de los 422 de forma del borde y antes de las reglas de negocio (edad, contraseña, versión de la política) y de consultar Firebase. Así, quien no está en la lista no recibe pistas de las demás reglas ni de si el correo ya existe. Alternativa descartada: aplicarlo justo antes de Firebase | Decisión de Juan Vela, 14-nov-2026 | Chat |
| D-4 | Mensaje del 403: «No es posible crear una cuenta con este correo.» No revela que hay una lista ni quién está en ella. Alternativa descartada: «El registro no está disponible.» | Decisión de Juan Vela, 14-nov-2026 | Chat |
| D-5 | Nombre de la variable `SIGNUP_ALLOWLIST` (propiedad `cuentas.signup.allowlist`) y código `SIGNUP_NOT_ALLOWED`: los que propone la tarjeta | Decisión de Juan Vela, 14-nov-2026 | CM-360 |
| I-1 | El registro repetido de una cuenta pendiente cuyo correo no está en la lista también responde 403: el filtro va antes de consultar si el correo existe (D-3) | Inferencia de D-3 | — |
| I-2 | El perfil `prod` no fija ni ignora la variable: producción no la lleva (CM-360) y, si alguien la pusiera por error, el efecto es cerrar el registro, visible de inmediato y sin riesgo de seguridad | Inferencia | CM-360 |

## 4. Requisitos (EARS)

### 4.1 Configuración

- **REQ-LRP-01.** El sistema deberá leer la lista de la propiedad `cuentas.signup.allowlist`, que toma `${SIGNUP_ALLOWLIST:}`.
- **REQ-LRP-02.** El sistema deberá separar las entradas por punto y coma (`;`), recortar los espacios de cada una y descartar las vacías (`a@x.co;;` y `;` producen entradas vacías que no cuentan).
- **REQ-LRP-03.** Cada entrada deberá normalizarse igual que `EmailAddress` (recorte, NFC y minúsculas) y ser de una de dos formas:
  - correo base `usuario@dominio`, que cumple `EmailAddress` y cuya parte local no tiene `+`;
  - dominio completo `@dominio`, donde `x@dominio` cumple `EmailAddress`.
- **REQ-LRP-04.** Si alguna entrada no cumple REQ-LRP-03, el servicio no deberá arrancar. El mensaje del error nombra `SIGNUP_ALLOWLIST` y la posición de la entrada, nunca su valor (D-1).
- **REQ-LRP-05.** Si la propiedad falta, está vacía o solo tiene espacios y separadores, el sistema deberá dejar el registro abierto como hoy.

### 4.2 Registro (`POST /api/v1/users`)

- **REQ-LRP-10.** Cuando la lista no está vacía, el sistema deberá comparar el correo ya normalizado por `EmailAddress` quitando de la parte local todo lo que va desde el primer `+`. Ejemplo: `JuanGomezMu+q5a@Unicauca.edu.co` se compara como `juangomezmu@unicauca.edu.co`.
- **REQ-LRP-11.** El correo está permitido si su forma comparada (REQ-LRP-10) es igual a un correo base de la lista, o si su dominio es igual a un dominio de la lista (D-2).
- **REQ-LRP-12.** Si el correo no está permitido, el sistema deberá responder 403 con `code` `SIGNUP_NOT_ALLOWED`, `title` «Registro no permitido» y `detail` «No es posible crear una cuenta con este correo.» (D-4), sin campo ni lista `errors`.
- **REQ-LRP-13.** El rechazo deberá ocurrir antes de las reglas de negocio del registro (edad, contraseña, versión de la política) y antes de cualquier llamada a Firebase o a la base (D-3). No se crea ni se consulta nada.
- **REQ-LRP-14.** Los 422 de forma del borde (Bean Validation y `@DomainRule`) siguen respondiendo antes que el 403: un correo mal escrito responde `EMAIL_INVALID_FORMAT`, esté o no en la lista.
- **REQ-LRP-15.** El rechazo se registra una sola vez, en `WARN`, con `code` y `requestId`, sin el correo (H-4).

## 5. Casos borde con datos literales

Lista de ejemplo: `paulamunoz@unicauca.edu.co; juanvela@unicauca.edu.co ;juangomezmu@unicauca.edu.co`.

| Correo recibido | Resultado | Por qué |
|---|---|---|
| `juangomezmu@unicauca.edu.co` | Permitido | Base exacta |
| `juangomezmu+q5a@unicauca.edu.co` | Permitido | Subdirección con `+` |
| `JuanGomezMu+Q5A@UNICAUCA.edu.co` | Permitido | Mayúsculas normalizadas |
| `juanvela@unicauca.edu.co` | Permitido | Entrada con espacios alrededor |
| `otro@unicauca.edu.co` | 403 | Mismo dominio, base no listada |
| `juangomezmu@gmail.com` | 403 | Otro dominio |
| `juangomezmu@mail.unicauca.edu.co` | 403 | Subdominio (D-2) |

Lista `@unicauca.edu.co`: `cualquiera+x@unicauca.edu.co` permitido; `ana@mail.unicauca.edu.co` y `ana@gmail.com`, 403.

Lista vacía, ausente o `" ; "`: todo correo sigue el flujo de hoy.

Entradas que impiden arrancar: `paulamunoz` (sin `@`), `a@b@c.co` (dos `@`), `juan+x@unicauca.edu.co` (`+` en la base), `@unicauca` (dominio sin punto), `@` (vacío).

## 6. Fuera de la API

- Valor para preproducción, que fija DevOps: `paulamunoz@unicauca.edu.co;juanvela@unicauca.edu.co;juangomezmu@unicauca.edu.co;anaarangowt@unicauca.edu.co;jjarias@unicauca.edu.co`. Respaldo sin cambiar código: `@unicauca.edu.co`.
- Al terminar, se comunica en CM-360 el nombre de la variable y el código de error (criterio de terminado).

## 7. Pendientes

Ninguno de Backend. La parte de DevOps está en CM-359.
