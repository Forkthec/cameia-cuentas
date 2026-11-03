# Entrada 06 · Tope de 1000 cuentas por ejecución de la purga

- **Fecha:** 2026-10-11 · **Herramienta:** Claude Code · **Responsable:** Backend
- **Salida:** [Spec de CM-179](../../specs/CM-179-VerificacionCorreo/spec.md), decisión D5
- **Decisión humana (HITL):** Paula, 6-oct-2026 (pregunta 6). Revisión de esta entrada: PENDIENTE (Paula)

## Qué cambió el rumbo

La IA propuso acotar cada ejecución de la purga a 1000 cuentas (`cuentas.purge.max-accounts-per-run`, de 1 a 1000) y decir en el resumen si quedan candidatas, en lugar de recorrer todas hasta agotarlas.

## Prompt

```
Rol: eres ingeniero de Backend de CAMEIA y trabajas para la dueña del Backend. Resuelves la verificación del correo de cameia-cuentas
conforme a su estándar de calidad y a su regla de errores visibles.

Contexto: la purga diaria de cuentas sin verificar, que consulta a Firebase una vez por cuenta candidata.

Tarea: compara un tope configurable con paginar hasta agotar, midiendo tiempo por ejecución y llamadas a Firebase; propone el valor y su rango.

Condiciones: el valor se valida al arrancar; fuera de rango la aplicación no inicia; el resto se procesa en la siguiente ejecución; toda afirmación sobre un sistema externo se prueba o se declara como no conocida; la decisión queda en la spec con
su porqué y las alternativas descartadas.

Formato: una tabla de alternativas con su veredicto y la redacción final del requisito que cambia.
```
