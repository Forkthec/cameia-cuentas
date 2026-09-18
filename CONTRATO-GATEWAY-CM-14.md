# CM-14 — Lo que el API Gateway debe tener en cuenta para el registro

- **Origen:** [specs/CM-14-RegistroUsuario/spec.md](specs/CM-14-RegistroUsuario/spec.md)
- **Fecha:** 17/09/2026
- **Responsable de llevarlo al Gateway:** Juan David Vela Coronado
- **Destinatario:** quien mantenga `cameia-gateway`

Este documento no cambia nada en cameia-cuentas. Recoge lo que la spec del registro decidió y que
solo puede cumplirse con una acción del lado del Gateway.

---

## 1. Ruta nueva: activación por verificación de correo

```text
POST /api/v1/users/me/verification
```

Cierra `CU-TBD-01` de Cuentas y toca el tema que el Gateway dejó abierto como `GW-TBD-17`.

| Aspecto | Valor |
|---|---|
| Caso (§6 de `AGENTS.md`) | **Caso A**: exige ID Token de Firebase válido |
| Destino | `${CAMEIA_CUENTAS_URL}` |
| Ruta YAML | No hace falta una nueva: `Path=/api/v1/users/**` ya la cubre |
| Encabezados esperados | `X-User-Id`, `X-User-Email`, `X-User-Roles` y `X-Request-Id`, como cualquier Caso A |
| Cuerpo | Vacío. La identidad y el estado de verificación salen del token |

**El dato que decide la operación es el claim `email_verified` del token.** Cuentas lo exige y
responde `403` si es `false`. Para que eso funcione, el Gateway tiene que cumplir dos cosas:

1. **Dejar llegar la petición aunque el correo no esté verificado.** Si más adelante el Gateway
   implementa la regla de `GW-TBD-17` y bloquea las rutas de Caso A cuando `email_verified` es
   `false`, esta ruta debe quedar exceptuada: es justamente la que el usuario usa para salir de ese
   estado.
2. **Propagar el estado de verificación.** Hoy el contrato de §6.4 no incluye ningún encabezado con
   `email_verified`. Hay dos salidas y conviene acordar una:
   - Agregar `X-User-Email-Verified` al contrato de §6.4, con el valor del claim; o
   - Que el Gateway rechace con `403` las peticiones a esta ruta cuando el claim sea `false`, y
     Cuentas confíe en esa validación.

   Cuentas implementa hoy la primera opción por omisión: si el encabezado no llega, trata la
   verificación como no probada y responde `403`.

---

## 2. Lo que ya está decidido y no cambia

- `POST /api/v1/users` sigue siendo **Caso B**, comparado por método y ruta exactos. Cuentas no
  valida ID Token en el registro porque el usuario todavía no existe.
- Cuentas nunca recibe la contraseña por ningún otro canal: viaja en el cuerpo de esa petición y de
  ninguna otra. El Gateway no debe registrar el cuerpo, como ya fija `REQ-REG-08`.
- Las respuestas de error del registro pasan intactas al cliente (`REQ-REG-09`). Con una precisión
  nueva: **el tipo de contenido es `application/problem+json`** (RFC 7807), no
  `application/json`. Si algún filtro del Gateway reescribe cuerpos o tipos de contenido según el
  `Content-Type`, hay que comprobar que este pasa sin tocarse.
- Códigos que Cuentas devuelve en el registro: `201`, `409` (correo ya registrado), `422`
  (validaciones) y `500` (fallo de infraestructura, ya compensado en Firebase).

---

## 3. Resuelto en Cuentas: la ruta de la sonda de salud

El spec del Gateway declara `GET /api/v1/users/health` en su lista de desarrollo, y su
`application.yml` enruta con `Path=/api/v1/users/**` **sin `StripPrefix` ni `RewritePath`**: la
petición llega a Cuentas con la ruta completa.

Cuentas exponía su sonda en `GET /health`, así que esa llamada respondía `404`. **Ya está
corregido del lado de Cuentas:** la sonda se movió a `GET /api/v1/users/health` (T-00 de CM-14) y
la spec [CM-103-EndpointSalud](specs/CM-103-EndpointSalud/spec.md) quedó actualizada. No se dejó
alias en `/health`.

El Gateway no necesita hacer nada: su lista de desarrollo ya apunta a la ruta correcta y no hay que
agregar `RewritePath`. Solo conviene comprobarlo de extremo a extremo cuando ambos servicios estén
arriba.

---

## 4. Claim `plan`

El registro escribe el custom claim `plan=FREE` en Firebase, así que `X-User-Plan` empezará a llegar
con ese valor a los microservicios.

**Ese claim no respalda ningún derecho ni cuota todavía.** No existe suscripción, tarifa ni
característica detrás: las semillas comerciales siguen pendientes y quedan fuera de CM-14. Ningún
componente debe usarlo para autorizar o limitar nada hasta que exista la spec de planes.

Además, el claim solo aparece en el token después de que el cliente lo refresque: el token emitido
en el primer inicio de sesión puede no traerlo.
