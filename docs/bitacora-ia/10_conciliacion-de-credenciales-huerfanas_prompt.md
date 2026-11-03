# Entrada 10 · Alcance de la conciliación de credenciales huérfanas

- **Fecha:** 2026-10-11 · **Herramienta:** Claude Code · **Responsable:** Backend
- **Salida:** [Spec de CM-179](../../specs/CM-179-VerificacionCorreo/spec.md), decisión pregunta 18
- **Decisión humana (HITL):** Paula, 11-oct-2026: opción C. Revisión de esta entrada: PENDIENTE (Paula)

## Qué cambió el rumbo

Al revalidar la spec, la IA encontró que la conciliación parte de la lista de usuarios de Firebase y no de la base: si una base y un proyecto de Firebase no corresponden, el criterio «más de 300 s sin fila» borraría credenciales reales. Propuso la opción C: solo credenciales con proveedor `password`, correo sin verificar, de más de 168 h y sin fila.

## Prompt

```
Rol: eres ingeniero de Backend de CAMEIA y trabajas para la dueña del Backend. Resuelves la verificación del correo de cameia-cuentas
conforme a su estándar de calidad y a su regla de errores visibles.

Contexto: la conciliación de credenciales que el registro deja en Firebase cuando falla también su borrado compensatorio.

Tarea: compara tres alcances: (A) 300 s sin fila con proveedor `password` y tope de 1000, (B) sin conciliación automática, (C) solo credenciales sin verificar, de más de 168 h; recomienda uno con su riesgo.

Condiciones: una credencial verificada o de otro proveedor nunca se borra sola; sin evento, porque nunca hubo `cuenta.creada`; toda afirmación sobre un sistema externo se prueba o se declara como no conocida; la decisión queda en la spec con
su porqué y las alternativas descartadas.

Formato: una tabla de alternativas con su veredicto y la redacción final del requisito que cambia.
```
