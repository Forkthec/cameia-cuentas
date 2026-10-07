# Spec — CM-188-correcciones: Cuentas y la Emulator UI de Firebase Auth

- **HU asociada:** CM-188 — correcciones sobre CM-191 (`908112c`). Soporte de desarrollo local, no HU de negocio
- **Sprint:** 1 (implementación); integración a `develop` en el Sprint 2
- **Fecha:** 24/09/2026
- **Estado:** Decisiones de §4 cerradas el 24/09/2026
- **Rama:** `CM-188-correcciones` → `develop`
- **Alcance:** documentación (`README.md`, `.env.example`) y, si se aprueban las decisiones de §4, `FirebaseConfiguration`

## 1. Contexto

CM-191 dejó a Cuentas arrancando contra el emulador de Firebase Auth, pero **la Emulator UI no
está habilitada en ningún punto que Cuentas use**:

- El emulador no vive en este repositorio: lo levanta el `docker-compose.yml` de `cameia-gateway`.
- En `cameia-gateway/develop` (`c0824c8`) el emulador no expone la UI. Se habilitó en la rama
  `CM-188-correcciones` del Gateway (`1de0671`: `firebase setup:emulators:ui`, `"ui"` en
  `firebase.json` y puerto `127.0.0.1:4000`), que todavía no está en `develop`.
- El `README.md` y el `.env.example` de Cuentas no mencionan la UI, ni cómo comprobar que un
  registro dejó el usuario y el claim `plan=FREE` en el emulador.

Además, la revisión del Gateway en esa misma rama encontró dos defectos que `FirebaseConfiguration`
de Cuentas también tiene, porque se escribió igual:

- **Fuente de la variable:** el modo emulador se decide con el `Environment` de Spring, pero el
  Admin SDK solo lee la variable del entorno del proceso. Si llega por `-D` o YAML (arranque desde
  el IDE), Cuentas entrega credenciales ficticias y el SDK habla con Google.
- **ID de proyecto:** nada impide arrancar en modo emulador con un `FIREBASE_PROJECT_ID` real. El
  usuario se crea en un proyecto del emulador distinto al que valida el Gateway.

### 1.1 Cuentas depende de que el Gateway esté arriba

Hay **un solo emulador**, el contenedor `cameia-firebase-emulator` del compose de `cameia-gateway`.
Cuentas no tiene uno propio: lo alcanza por nombre en la red `cameia-net`. Por eso, para desarrollar
o probar Cuentas en local, **primero hay que levantar el compose del Gateway**. Si no, el registro
falla porque no hay servidor de Auth. Lo único que debe coincidir a mano entre el emulador, el
Gateway y Cuentas es el ID de proyecto (`demo-cameia`).

El emulador es infraestructura compartida, y su lugar natural sería un compose general en
`cameia-infra`. **Por ahora no se mueve allí** (decisión de Juan David Vela Coronado del
24/09/2026): el proyecto está en etapa de pruebas y no se quiere subir un `.yml` de desarrollo a
ese repositorio todavía. Se retomará en una spec propia, porque el cambio afecta a varios
repositorios.

## 2. Requisitos funcionales (EARS)

- **REQ-EMC-C01** — Cuando quien desarrolla siga el `README.md`, el documento debe indicar que la
  Emulator UI está en `http://localhost:4000`, que la provee `cameia-gateway` y desde qué versión.
- **REQ-EMC-C02** — Cuando se registre un usuario contra el emulador, el `README.md` debe indicar
  cómo verificar en la UI el usuario y su claim `plan`.
- **REQ-EMC-C03** — Mientras Cuentas corra en modo emulador, si `FIREBASE_PROJECT_ID` no empieza por
  `demo-`, el sistema no debe arrancar y el mensaje debe nombrar ambas variables. *(sujeto a §4)*
- **REQ-EMC-C04** — Si `FIREBASE_AUTH_EMULATOR_HOST` está en el `Environment` de Spring pero no en el
  entorno del proceso, o con otro valor, el sistema no debe arrancar. *(sujeto a §4)*
- **REQ-EMC-C05** — Cuando Cuentas arranque en modo emulador, debe registrar en el log el ID de
  proyecto. *(sujeto a §4)*

La guardia de despliegue de CM-191 (`REQ-EMU-02`) no cambia y sigue viendo ambas fuentes.

## 3. Fuera del alcance

- Habilitar la UI: es del Gateway y ya está en su rama `CM-188-correcciones`.
- Cambiar el código HTTP del registro cuando el emulador está caído (hoy `500` genérico).
- Tocar `cameia-web` o `cameia-gateway`.
- Mover el emulador a un compose general en `cameia-infra` (ver §1.1).

## 4. Decisiones pendientes

| ID | Pregunta | Decisión |
|---|---|---|
| `CM-TBD-01` | ¿Se replican en Cuentas las guardias del Gateway (`REQ-EMC-C03` a `C05`)? | **Sí**, en este mismo spec (Juan David Vela Coronado, 24/09/2026) |
| `CM-TBD-02` | ¿Se integra esta rama antes o después del merge del Gateway a `develop`? | **Después**, para que el README no apunte a una UI que `develop` no tiene (Juan David Vela Coronado, 24/09/2026). El PR del Gateway está abierto y no está en `develop` por la etapa de pruebas. Los dos PR se integran en el **Sprint 2**, primero el Gateway y luego Cuentas; mientras tanto Cuentas se implementa y prueba contra la rama `CM-188-correcciones` del Gateway |
