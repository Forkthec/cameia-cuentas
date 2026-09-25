# Plan — CM-188-correcciones

- **Spec de referencia:** `specs/CM-188-correcciones/spec.md`
- **Fecha:** 24/09/2026

## 1. Documentación (REQ-EMC-C01, C02)

- `README.md`, sección "Ejecución local": párrafo sobre la Emulator UI (`http://localhost:4000`,
  provista por `cameia-gateway` desde `CM-188-correcciones`), y un paso de verificación tras el
  registro: pestaña *Authentication* → usuario → *Custom claims* con `{"plan":"FREE"}`.
- `.env.example`: una línea junto a `FIREBASE_AUTH_EMULATOR_HOST` que apunte a la UI.

## 2. `FirebaseConfiguration` (REQ-EMC-C03 a C05, solo si se aprueba `CM-TBD-01`)

Mismo diseño que `FirebaseConfig` del Gateway, para que ambos servicios se comporten igual:

- `processVariable(String)`: lee la fuente `systemEnvironment` del `ConfigurableEnvironment` en
  bruto; es el punto de sustitución en las pruebas. El constructor pasa a `ConfigurableEnvironment`.
- `isEmulatorMode()`: variable del proceso con valor. La usan `resolverCredenciales()` y las guardias.
- `rejectEmulatorOutsideProcessEnvironment()`: Spring la ve y el proceso no, o con valor distinto.
- `rejectNonDemoProjectInEmulator()`: `projectId` sin prefijo `demo-` en modo emulador.
- `rejectEmulatorInDeployment()` sin cambios: sigue usando `environment.containsProperty`.
- Log `info` con el `projectId` al entrar en modo emulador.

Orden en `firebaseApp()`: despliegue → fuente → proyecto → credenciales.

## 3. Pruebas

- `FirebaseConfigurationTest`: un caso por guardia nueva, positivo y negativo.
- `FirebaseEmulatorContextTest`: el caso positivo usa una variable de proceso simulada.
- Suite completa en verde; anotar el número de pruebas antes y después.
