# Tasks — CM-188-correcciones

- **Spec de referencia:** `specs/CM-188-correcciones/spec.md`
- **Plan de referencia:** `specs/CM-188-correcciones/plan.md`
- **Fecha:** 24/09/2026
- **Rama:** `CM-188-correcciones` → `develop`

Una tarea a la vez; marcar `[x]` antes de pasar a la siguiente. Cada tarea cabe en 30 minutos.

---

## Bloque 0 — Decisiones

- [x] T-00 · Cerrar `CM-TBD-01` y `CM-TBD-02` con el equipo y anotar la decisión en el spec §4 — 24/09/2026: `CM-TBD-01` sí; `CM-TBD-02` después del Gateway
- [x] T-01 · Confirmar que `cameia-gateway` `CM-188-correcciones` (`1de0671`) está en `develop` o
      fijar desde qué rama se levanta el emulador para probar la UI — 24/09/2026: no está en
      `develop`; se levantó desde un worktree en `origin/CM-188-correcciones` (`c2aa0d1`)

## Bloque 1 — Emulator UI (REQ-EMC-C01, C02)

- [x] T-02 · Levantar el emulador desde el compose del Gateway y comprobar que `http://localhost:4000`
      responde y que la pestaña *Authentication* carga — 24/09/2026: contenedor `healthy` en
      `cameia-net`; `:4000` y `:9099` responden `200`; log "View Emulator UI at http://127.0.0.1:4000/"
- [x] T-03 · Levantar Cuentas con `.env.example` por defecto, registrar un usuario y verificar en la
      UI el usuario y el claim `plan=FREE`; guardar la evidencia para el PR — 24/09/2026: `201`
      con `plan=FREE`; en el emulador (`accounts:lookup`, la API que usa la UI) el mismo `localId` con
      `customAttributes {"plan":"FREE"}`. No se revisó visualmente en el navegador
- [x] T-04 · `README.md`: párrafo de la Emulator UI y paso de verificación del registro (`REQ-EMC-C01`, `C02`)
- [x] T-05 · `.env.example`: línea que apunte a la UI junto a `FIREBASE_AUTH_EMULATOR_HOST`

## Bloque 2 — Guardias de `FirebaseConfiguration` (solo si `CM-TBD-01` = sí)

- [ ] T-06 · Constructor con `ConfigurableEnvironment`, `processVariable` e `isEmulatorMode`;
      `resolverCredenciales()` pasa a usar `isEmulatorMode` (`REQ-EMC-C04`)
- [ ] T-07 · `rejectEmulatorOutsideProcessEnvironment()` y sus pruebas en `FirebaseConfigurationTest` (`REQ-EMC-C04`)
- [ ] T-08 · `rejectNonDemoProjectInEmulator()`, log del `projectId` y sus pruebas (`REQ-EMC-C03`, `C05`)
- [ ] T-09 · `FirebaseEmulatorContextTest`: caso positivo con variable de proceso simulada; comprobar
      que `REQ-EMU-02` de CM-191 sigue en verde
- [ ] T-10 · Quitar a propósito cada guardia y confirmar que fallan solo sus pruebas; restaurar

## Bloque 3 — Cierre

- [ ] T-11 · `./mvnw.cmd clean package` en verde; anotar pruebas antes y después (hoy 110)
- [ ] T-12 · Actualizar `CLAUDE.md` si cambian las reglas del emulador (Reglas de seguridad)
- [ ] T-13 · Bitácora de IA del día, commit y PR hacia `develop` — **solo con autorización expresa**.
      La descripción del PR debe decir que Cuentas necesita el compose del Gateway arriba y por qué
      el emulador no se movió a `cameia-infra` (spec §1.1)
