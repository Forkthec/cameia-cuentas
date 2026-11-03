# Entrada 04 · Credencial con `uid` propio y borrado cuando la creación no responde

- **Fecha:** 2026-10-08 · **Herramienta:** Claude Code · **Responsable:** Backend
- **Salida:** [Spec de CM-251](../../specs/CM-251-RegistroRepetido/spec.md), decisiones D17 y D18 y REQ-RR-18

## Qué cambió el rumbo

La IA dio por resuelto el caso de la credencial huérfana con el `uid` propio (D17) y con la premisa de que el SDK de Firebase
reintenta una creación cuyo tiempo de lectura venció. La dueña del Backend pidió elegir la mejor opción y no la más barata, y exigió
una última revisión de punta a punta. Con la aplicación real, el emulador y un proxy que retenía la respuesta de la creación, el SDK
no reintentó: la persona recibía 503 y su reintento, 409. Se agregó el borrado de la credencial propia antes del 503 (REQ-RR-18).

## Prompt

```
Rol: eres ingeniero de Backend de CAMEIA y trabajas para la dueña del Backend. Revisas el registro repetido de cameia-cuentas contra
su regla de errores visibles y su estándar de calidad.

Contexto: el registro crea primero la credencial en Firebase Auth y después la cuenta local. Si la creación de la credencial termina
sin respuesta (por ejemplo, vence el tiempo de lectura de 5 s), Firebase puede haberla completado igual y la credencial queda sin
cuenta local: el reintento de la persona recibe 409 por una credencial que ella nunca vio. Una revisión independiente señaló este
riesgo. La dueña del Backend pidió elegir la mejor opción y no la que ahorre trabajo si después cuesta más corregirla.

Tarea: (1) compara las alternativas para reconocer y limpiar la credencial propia: que el servicio genere el uid y lo pase a la
creación, tratar como propia toda credencial creada después de iniciar la petición, o dejarla a la purga; decide qué hacer cuando
Firebase no informa la fecha de creación de una credencial sin cuenta local; (2) comprueba cada afirmación sobre el comportamiento
del SDK y de Firebase con la aplicación real, el emulador de Firebase Auth y un proxy que retenga la respuesta de la creación;
(3) corrige lo que la comprobación desmienta.

Condiciones: toda afirmación sobre un sistema externo se prueba o se declara como no conocida; ningún mensaje ni código de error
nuevo sin el criterio del backlog; sin correos ni contraseñas en los logs; al terminar se borran los datos de prueba.

Formato: una tabla de alternativas con su veredicto, los requisitos que cambian con su redacción final y una lista de los casos
comprobados con lo esperado y lo obtenido.
```
