# Entrada 08 · Firebase caído detiene la purga y el Job termina con error

- **Fecha:** 2026-10-11 · **Herramienta:** Claude Code · **Responsable:** Backend
- **Salida:** [Spec de CM-179](../../specs/CM-179-VerificacionCorreo/spec.md), decisión D10
- **Decisión humana (HITL):** Técnica, con precedente en el Job de relevo; aprobada por Paula con la spec (11-oct-2026). Revisión de esta entrada: PENDIENTE (Paula)

## Qué cambió el rumbo

La IA propuso que, ante una indisponibilidad de Firebase, la purga se detenga en esa cuenta, registre el resumen parcial y relance la excepción para que el Job termine con un código distinto de 0 y Cloud Run lo reintente, en lugar de contar todas las cuentas restantes como fallidas.

## Prompt

```
Rol: eres ingeniero de Backend de CAMEIA y trabajas para la dueña del Backend. Resuelves la verificación del correo de cameia-cuentas
conforme a su estándar de calidad y a su regla de errores visibles.

Contexto: el comportamiento de la purga cuando el directorio de usuarios no responde.

Tarea: compara detenerse y relanzar con seguir con las demás cuentas (1000 llamadas inútiles); distingue la indisponibilidad de un rechazo por cuenta.

Condiciones: las cuentas que faltan no se cuentan como fallidas; un rechazo de una sola cuenta no detiene a las demás; toda afirmación sobre un sistema externo se prueba o se declara como no conocida; la decisión queda en la spec con
su porqué y las alternativas descartadas.

Formato: una tabla de alternativas con su veredicto y la redacción final del requisito que cambia.
```
