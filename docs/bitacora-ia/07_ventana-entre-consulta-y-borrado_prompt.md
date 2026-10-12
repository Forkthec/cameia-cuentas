# Entrada 07 · Ventana aceptada entre consultar la verificación y borrar la credencial

- **Fecha:** 2026-10-11 · **Herramienta:** Claude Code · **Responsable:** Backend
- **Salida:** [Spec de CM-179](../../specs/CM-179-VerificacionCorreo/spec.md), decisión D6
- **Decisión humana (HITL):** Paula, 6-oct-2026 (pregunta 8). Revisión de esta entrada: PENDIENTE (Paula)

## Qué cambió el rumbo

La IA señaló que entre «¿el correo está verificado?» y el borrado de la credencial hay milisegundos en los que la persona puede verificar; el orden credencial primero y fila después lo fija la nota técnica de HU-1.2. Propuso aceptar la ventana, borrar la fila solo si sigue pendiente y registrar en `ERROR` el caso residual.

## Prompt

```
Rol: eres ingeniero de Backend de CAMEIA y trabajas para la dueña del Backend. Resuelves la verificación del correo de cameia-cuentas
conforme a su estándar de calidad y a su regla de errores visibles.

Contexto: el orden de borrado de la purga (credencial y después fila con su evento) y el caso en que la persona activa la cuenta durante la purga.

Tarea: compara aceptar la ventana con volver a consultar justo antes de borrar (que la reduce pero no la elimina); define qué queda en el estado final y qué se registra.

Condiciones: el estado final es coherente; ningún evento anuncia un borrado que no ocurrió; sin datos personales en el log; toda afirmación sobre un sistema externo se prueba o se declara como no conocida; la decisión queda en la spec con
su porqué y las alternativas descartadas.

Formato: una tabla de alternativas con su veredicto y la redacción final del requisito que cambia.
```
