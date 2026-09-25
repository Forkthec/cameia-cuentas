# Spec — CM-191-EmuladorFirebaseAuth: Cuentas arranca contra el emulador de Firebase Auth sin abrir la puerta en despliegue

- **HU asociada:** CM-191 — subtarea de CM-188. No es una HU de negocio: es soporte de desarrollo local
- **Sprint:** 1
- **Fecha:** 19/09/2026
- **Estado:** Fases 1 y 2 cerradas con la decisión de Paula Andrea Muñoz Delgado del 19/09/2026 y la autorización de Juan Vela para modificar este repositorio (la misma decisión que `GW-TBD-28` del Gateway)
- **Rama:** `CM-191-arranque-con-emulador-firebase-auth` → `develop` (creada desde `origin/develop`)
- **Alcance:** **solo `FirebaseConfiguration`**, más el compose, `.env.example`, este `CLAUDE.md` y la documentación. Ni el puerto `FirebaseUserDirectory`, ni el adaptador, ni el dominio del registro
- **Fuentes:** el propio `FirebaseConfiguration.java`, `docker-compose.yml`, `.env.example`, `CLAUDE.md` (reglas de seguridad) y `docs/constitution.md`

---

## 1. Contexto

Hoy `FirebaseConfiguration` resuelve las credenciales de una de dos maneras: si `FIREBASE_KEY_PATH`
está vacío, pide `GoogleCredentials.getApplicationDefault()` (Cloud Run); si no, lee el JSON de esa
ruta (desarrollo). Sin una llave de service account ni `gcloud` configurado, Cuentas no arranca. Por
eso, para desarrollar en local, cada integrante necesita credenciales de un proyecto real de
Firebase (hoy, las de un proyecto personal), que no está bajo control del equipo y cuyos tokens no
sirven contra staging.

La alternativa decidida es el **emulador de Firebase Auth**: un servicio local, en un contenedor del
`docker-compose.yml` de `cameia-gateway`, que no exige credenciales y cuyos usuarios solo existen en
la máquina. Cuentas es quien registra a los usuarios y escribe el claim `plan`, así que sin este
cambio el registro en local contra el emulador no funciona.

### 1.1 Qué hace el Admin SDK con el emulador (verificado, no supuesto)

Verificado el 19/09/2026 con `firebase-admin` 9.10.0 (la versión de este repositorio), inspeccionando
el `.jar` y con un programa Java contra un emulador real en Docker:

- Si existe la variable de entorno `FIREBASE_AUTH_EMULATOR_HOST`, el SDK la detecta **por sí solo**
  leyendo el entorno del proceso: las peticiones de `FirebaseUserManager` van a
  `http://<host>/identitytoolkit.googleapis.com/...` y `FirebaseTokenVerifierImpl` cambia al modo
  emulador.
- En ese modo el SDK sustituye las credenciales por `EmulatorCredentials`, **pero**
  `FirebaseOptions.builder().build()` sigue exigiendo un objeto de credenciales no nulo.
- Sin llave ni `gcloud`, `GoogleCredentials.getApplicationDefault()` lanza `IOException`. Con
  credenciales ficticias, en cambio, el SDK crea usuarios, escribe el claim `plan=FREE`, verifica un
  token del emulador y rechaza un token basura.

Conclusión: lo único que falta en Cuentas son unas credenciales **ficticias** cuando el emulador está
configurado.

### 1.2 El detalle que decide el orden: dentro de Docker `keyPath` nunca está vacío

En `docker-compose.yml`, `FIREBASE_KEY_PATH` está **fijo** a `/run/secrets/firebase-service-account.json`
y el volumen monta `${FIREBASE_KEY_PATH:-/dev/null}` en esa ruta. Sin llave real, esa ruta es un
archivo vacío (`/dev/null`) y `GoogleCredentials.fromStream` falla. Por eso la comprobación del
emulador tiene que ir **antes** que la de `keyPath`: si fuera después, en Docker nunca se llegaría a
ella.

### 1.3 Por qué el despliegue exige una guardia

En modo emulador los tokens **no van firmados** (`alg: none`) y el SDK los acepta. Si
`FIREBASE_AUTH_EMULATOR_HOST` llegara a un despliegue, cualquiera podría fabricar un token y
suplantar a cualquier usuario: se anularía lo que la matriz ASVS registra como cumplido en `9.1.1`
(validar la firma) y `9.1.2` (nunca aceptar `none`). Cuentas **no puede** neutralizar la variable
ignorándola, porque el SDK la lee directamente del entorno del proceso. La única defensa que
controla Cuentas es **negarse a arrancar** si la variable aparece donde no debe. "Despliegue" significa
lo mismo que en el Gateway: `K_SERVICE` definida (Cloud Run la inyecta) o el perfil `prod` activo.

### Lo que este spec NO hace

- No cambia el registro, el puerto `FirebaseUserDirectory`, el adaptador ni ninguna respuesta HTTP.
- No cambia el comportamiento cuando la variable **no** está definida (`REQ-EMU-03`).
- No toca `cameia-gateway` (que tiene su propio spec, `CM-190-emulador-firebase-auth`) ni `cameia-web`.

---

## 2. Requisitos funcionales — notación EARS

Numeración propia: `REQ-EMU-NN`.

### REQ-EMU-01 — Con el emulador configurado, Cuentas arranca sin credenciales de Google

```
Cuando FIREBASE_AUTH_EMULATOR_HOST esté definida con un valor no vacío y el sistema no corra en un
despliegue, debe inicializar Firebase con credenciales ficticias, sin pedir las credenciales por
defecto de Google y sin leer el archivo de FIREBASE_KEY_PATH, aunque esa ruta tenga valor.
```

### REQ-EMU-02 — En un despliegue, la variable impide el arranque

```
Mientras FIREBASE_AUTH_EMULATOR_HOST esté definida —aunque su valor esté vacío— y exista K_SERVICE
o esté activo el perfil prod, el sistema no debe arrancar. El mensaje del error debe nombrar la
variable y explicar por qué.
```

> Se cuenta como "definida" aunque esté vacía: es el criterio más conservador, y evita que un valor
> en blanco deje pasar una configuración que el SDK podría interpretar de otra manera.

### REQ-EMU-03 — Sin la variable, nada cambia

```
Mientras FIREBASE_AUTH_EMULATOR_HOST no esté definida, o esté vacía fuera de un despliegue, el
sistema debe resolver las credenciales exactamente como hasta ahora: las credenciales por defecto
de Google si FIREBASE_KEY_PATH está vacío, y el JSON de esa ruta si tiene valor.
```

### REQ-EMU-04 — El compose puede pasar la variable sin obligar a definirla

```
El docker-compose.yml debe pasar FIREBASE_AUTH_EMULATOR_HOST al servicio cuentas únicamente cuando
esté definida en el entorno o en el archivo .env; si no lo está, el contenedor no debe recibirla.
```

> Pasarla vacía por defecto activaría sin querer la guardia de `REQ-EMU-02` y dejaría una variable
> "definida pero vacía" dentro del contenedor.

## 3. Requisitos no funcionales

### REQ-NF-EMU-01 — Ningún secreto

Las credenciales ficticias son un literal sin valor real. No se versiona ninguna llave ni token
(`docs/constitution.md`, punto 5).

### REQ-NF-EMU-02 — Ninguna respuesta HTTP cambia

Sin la variable, Cuentas se comporta igual que antes; con ella, solo cambia de dónde salen las
credenciales de arranque.

### REQ-NF-EMU-03 — Registro sin datos sensibles

Al usar el emulador se registra una advertencia en español con la dirección del emulador y sin ningún
dato de credenciales (`CLAUDE.md`, reglas de seguridad).

---

## 4. Trazabilidad requisito → prueba

| Requisito | Prueba |
|---|---|
| `REQ-EMU-01` | `FirebaseConfigurationTest` (unitaria, con `keyPath` inexistente) y `FirebaseEmulatorContextTest` (contexto con la configuración real) |
| `REQ-EMU-02` | `FirebaseConfigurationTest` (`K_SERVICE`, perfil `prod`, variable vacía) y `FirebaseEmulatorContextTest` |
| `REQ-EMU-03` | `FirebaseConfigurationTest` (sin variable con `keyPath` vacío y con valor, variable vacía, `K_SERVICE` sin variable) |
| `REQ-EMU-04` | Comprobación con `docker compose config` y con el entorno del contenedor (registrada en el PR) |
| `REQ-NF-EMU-02` | Suite existente sin modificar, en verde |
