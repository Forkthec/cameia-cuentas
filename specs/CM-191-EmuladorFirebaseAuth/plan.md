# Plan — CM-191-EmuladorFirebaseAuth

- **Spec de referencia:** `specs/CM-191-EmuladorFirebaseAuth/spec.md`
- **Fecha:** 19/09/2026

---

## 1. Cambios en `FirebaseConfiguration` (`REQ-EMU-01` a `REQ-EMU-03`)

Es la única clase de producción que cambia. No se crea ninguna clase nueva.

- **Constructor:** recibe además el `Environment` de Spring (campo `private final`). El `Environment`
  incluye las variables de entorno del sistema, así que las pruebas pueden fijar la variable, `K_SERVICE`
  y el perfil sin tocar el entorno del proceso.
- **`firebaseApp()`:** llama primero a `rejectEmulatorInDeployment()`; el resto queda igual.
- **`rejectEmulatorInDeployment()`:** si `environment.containsProperty("FIREBASE_AUTH_EMULATOR_HOST")` y
  (`K_SERVICE` presente o perfil `prod` activo), lanza `IllegalStateException` con un mensaje en español
  que nombra la variable. `containsProperty` es verdadero aunque el valor esté vacío (`REQ-EMU-02`).
- **`resolverCredenciales()`** (se conserva el nombre para no ampliar el diff): el orden pasa a ser
  1) variable del emulador con valor → credenciales ficticias, **aunque `keyPath` tenga valor**;
  2) `keyPath` vacío → `defaultCredentials()`; 3) `keyPath` con valor → el JSON, como hoy.
- **`defaultCredentials()`:** una sola línea, `GoogleCredentials.getApplicationDefault()`. Método
  aparte, visible en el paquete, para que una prueba lo sustituya y compruebe de dónde salen las
  credenciales sin depender de que la máquina tenga o no credenciales de Google.
- **Credenciales ficticias:** `GoogleCredentials.create(new AccessToken("emulador-sin-credenciales", null))`,
  el mismo objeto con el que se verificó el SDK (spec §1.1). El SDK las sustituye por
  `EmulatorCredentials`.
- **Registro:** una línea `WARN` en español con la dirección del emulador (`REQ-NF-EMU-03`).

## 2. Compose y ejemplos (`REQ-EMU-04`)

- `docker-compose.yml`, servicio `cuentas`: `FIREBASE_AUTH_EMULATOR_HOST:` **sin valor** (paso directo).
  Cuentas ya está en la red `cameia-net`, así que alcanza el emulador por nombre sin más cambios.
- `.env.example`: el camino por defecto pasa a ser el emulador (`FIREBASE_PROJECT_ID=demo-cameia` y la
  variable apuntando a `cameia-firebase-emulator:9099`); usar un proyecto real queda como alternativa.
- `CLAUDE.md`: una viñeta en "Reglas de seguridad" (el spec pide actualizarlo cuando cambien reglas).

## 3. Pruebas (JUnit 5 y AssertJ, como el resto del repositorio)

| Prueba | Tipo | Cubre |
|---|---|---|
| `FirebaseConfigurationTest` | Unitaria, sin contexto de Spring (`MockEnvironment`) | `REQ-EMU-01`, `-02`, `-03` |
| `FirebaseEmulatorContextTest` | `ApplicationContextRunner` con solo `FirebaseConfiguration` | `REQ-EMU-01`, `-02` |
| Suite existente | Sin modificar | `REQ-NF-EMU-02` |

No se usa Testcontainers en estas pruebas: no necesitan base de datos. El `pom.xml` fija
`FIREBASE_ENABLED=false` para la suite, pero `ApplicationContextRunner` no carga `application.properties`,
así que la condición `matchIfMissing` deja activa la configuración real.

Casos de `FirebaseConfigurationTest`: (1) variable con valor, sin despliegue → ficticias y no pide las de
Google; (2) variable con valor y `keyPath` inexistente → no lo lee (el orden); (3) variable y `K_SERVICE`
→ falla, sin `FirebaseApp`; (4) variable y perfil `prod` → falla; (5) variable **vacía** y `K_SERVICE` →
falla; (6) sin variable y `keyPath` vacío → credenciales por defecto; (7) sin variable y `keyPath` con
valor inexistente → intenta leer el archivo y falla como hoy; (8) variable vacía sin despliegue → como
hoy; (9) `K_SERVICE` sin variable → arranca como hoy. Cada prueba borra el `FirebaseApp` estático al
terminar.

**Prueba de extremo a extremo, manual y registrada en el PR:** emulador real en Docker (servicio del
Gateway) y Cuentas contra él, con PostgreSQL: el registro crea el usuario en el emulador con el claim
`plan=FREE`, y la guardia impide el arranque en el contenedor con `K_SERVICE`.

## 4. Riesgos

| Riesgo | Mitigación |
|---|---|
| La variable llega a un despliegue y Cuentas acepta tokens sin firma | `REQ-EMU-02`: el arranque falla; probado a nivel de clase, de contexto y en el contenedor real |
| En Docker se lee `/dev/null` como llave y falla | El emulador se comprueba antes que `keyPath` (`REQ-EMU-01`); probado con una ruta inexistente |
| La matriz ASVS (`cameia-infra`) dice que no hay fuente de claves configurable | Pendiente de DevOps actualizar `9.1.1`, `9.1.2` y `9.1.3` con esta guardia, fuera de este repositorio |
