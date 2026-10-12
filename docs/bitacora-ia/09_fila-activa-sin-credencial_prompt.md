# Entrada 09 · Fila activa sin credencial tras la purga se registra en ERROR

- **Fecha:** 2026-10-11 · **Herramienta:** Claude Code · **Responsable:** Backend
- **Salida:** [Spec de CM-179](../../specs/CM-179-VerificacionCorreo/spec.md), decisión D12
- **Decisión humana (HITL):** Técnica; aprobada por Paula con la spec (11-oct-2026). Revisión de esta entrada: PENDIENTE (Paula)

## Qué cambió el rumbo

La IA propuso registrar en `ERROR` (y no en `WARN`) el caso en que la credencial ya se borró pero la fila dejó de estar pendiente, porque queda una cuenta activa sin credencial que alguien debe conciliar a mano.

## Prompt

```
Rol: eres ingeniero de Backend de CAMEIA y trabajas para la dueña del Backend. Resuelves la verificación del correo de cameia-cuentas
conforme a su estándar de calidad y a su regla de errores visibles.

Contexto: la ventana de la decisión anterior y el nivel con que se registra.

Tarea: decide el nivel del log, el mensaje, que la cuenta cuente como fallida y que no se publique evento.

Condiciones: sin datos personales en el log: solo el identificador de la cuenta; toda afirmación sobre un sistema externo se prueba o se declara como no conocida; la decisión queda en la spec con
su porqué y las alternativas descartadas.

Formato: una tabla de alternativas con su veredicto y la redacción final del requisito que cambia.
```
