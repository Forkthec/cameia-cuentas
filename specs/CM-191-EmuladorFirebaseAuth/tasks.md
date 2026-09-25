# Tasks — CM-191-EmuladorFirebaseAuth

- **Spec de referencia:** `specs/CM-191-EmuladorFirebaseAuth/spec.md`
- **Plan de referencia:** `specs/CM-191-EmuladorFirebaseAuth/plan.md`
- **Fecha:** 19/09/2026
- **Rama:** `CM-191-arranque-con-emulador-firebase-auth` → `develop`

## Cómo usar esta lista

- Una tarea a la vez, de 30 minutos o menos; marcar `[x]` antes de la siguiente.
- Al cerrar el bloque de pruebas: `./mvnw.cmd test` y `./mvnw.cmd clean package` en verde.

---

## Bloque 1 — Código

- [x] T-01 · `FirebaseConfiguration`: constructor con el `Environment`, campo `private final` (plan §1) — 19/09/2026: constructor con `Environment`, campo `private final`
- [x] T-02 · `rejectEmulatorInDeployment()` y su llamada al inicio de `firebaseApp()` (`REQ-EMU-02`) — 19/09/2026: `rejectEmulatorInDeployment()` corre primero en `firebaseApp()`; cuenta la variable definida aunque esté vacía
- [x] T-03 · `resolverCredenciales()` con el orden nuevo, `defaultCredentials()` y las credenciales ficticias (`REQ-EMU-01`, `REQ-EMU-03`) — 19/09/2026: `resolverCredenciales()` con el emulador primero, `defaultCredentials()` (punto de sustitución para pruebas) y credenciales ficticias

## Bloque 2 — Pruebas

- [x] T-04 · `FirebaseConfigurationTest` con los nueve casos del plan §3 — 19/09/2026: nueve casos: los ocho previstos más `keyPath` inexistente sin variable, que prueba que se sigue leyendo el archivo como hoy
- [x] T-05 · `FirebaseEmulatorContextTest`: contexto con la variable y `K_SERVICE` (falla), con perfil `prod` (falla) y con la variable sola (arranca) — 19/09/2026: `FirebaseEmulatorContextTest`: 3 pruebas (falla con `K_SERVICE`, falla con perfil `prod`, arranca sin despliegue con `keyPath` inexistente)
- [x] T-06 · `./mvnw.cmd test` y `./mvnw.cmd clean package` en verde; anotar el número de pruebas antes y después — 19/09/2026: 110 pruebas, 0 fallos, `BUILD SUCCESS` con `clean package` (98 previas + 12 nuevas). Sin PostgreSQL en el 5432, `CuentasApplicationTests.contextLoads` falla por la base, como en cualquier equipo sin ella; el CI la provee con un servicio `postgres:16-alpine`, reproducido en local
- [x] T-07 · Comprobar que las pruebas discriminan: quitar a propósito la llamada a la guardia y ver cuáles fallan; restaurar — 19/09/2026: sin la guardia fallan exactamente las 5 pruebas de la guardia; con el orden ingenuo (`keyPath` primero) fallan exactamente las 2 del caso de Docker; restaurado el archivo

## Bloque 3 — Compose, ejemplos y documentación

- [x] T-08 · `docker-compose.yml` (`REQ-EMU-04`) y `.env.example`; comprobar con `docker compose config` y con el entorno real del contenedor, con y sin la variable — 19/09/2026: `docker compose config` con y sin la variable, y entorno real del contenedor (0 líneas de la variable sin definirla, 1 con ella)
- [x] T-09 · `CLAUDE.md` (regla de seguridad) y este spec al día — 19/09/2026: regla de seguridad en `CLAUDE.md` y sección de ejecución local del `README.md`

## Bloque 4 — Extremo a extremo y cierre

- [x] T-10 · Prueba de extremo a extremo con el emulador real (plan §3); registrar la salida en el PR — 19/09/2026: emulador real + Cuentas + PostgreSQL en Docker: registro `201`, usuario en el emulador con `{"plan":"FREE"}` y fila en PostgreSQL con el mismo `firebase_uid`; la guardia impide el arranque en el contenedor real (variable con valor, vacía y perfil `prod`); la línea base sin variable falla como antes
- [ ] T-11 · Commit, PR hacia `develop` con `[IA-ASISTIDO]` en el título, revisor Juan Vela, comentario de cierre en Jira
