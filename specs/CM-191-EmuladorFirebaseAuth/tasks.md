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

- [ ] T-01 · `FirebaseConfiguration`: constructor con el `Environment`, campo `private final` (plan §1)
- [ ] T-02 · `rejectEmulatorInDeployment()` y su llamada al inicio de `firebaseApp()` (`REQ-EMU-02`)
- [ ] T-03 · `resolverCredenciales()` con el orden nuevo, `defaultCredentials()` y las credenciales ficticias (`REQ-EMU-01`, `REQ-EMU-03`)

## Bloque 2 — Pruebas

- [ ] T-04 · `FirebaseConfigurationTest` con los nueve casos del plan §3
- [ ] T-05 · `FirebaseEmulatorContextTest`: contexto con la variable y `K_SERVICE` (falla), con perfil `prod` (falla) y con la variable sola (arranca)
- [ ] T-06 · `./mvnw.cmd test` y `./mvnw.cmd clean package` en verde; anotar el número de pruebas antes y después
- [ ] T-07 · Comprobar que las pruebas discriminan: quitar a propósito la llamada a la guardia y ver cuáles fallan; restaurar

## Bloque 3 — Compose, ejemplos y documentación

- [ ] T-08 · `docker-compose.yml` (`REQ-EMU-04`) y `.env.example`; comprobar con `docker compose config` y con el entorno real del contenedor, con y sin la variable
- [ ] T-09 · `CLAUDE.md` (regla de seguridad) y este spec al día

## Bloque 4 — Extremo a extremo y cierre

- [ ] T-10 · Prueba de extremo a extremo con el emulador real (plan §3); registrar la salida en el PR
- [ ] T-11 · Commit, PR hacia `develop` con `[IA-ASISTIDO]` en el título, revisor Juan Vela, comentario de cierre en Jira
