# Entrada 05 · Activación sin transacción y conflicto de versión como excepción de dominio

- **Fecha:** 2026-10-11 · **Herramienta:** Claude Code · **Responsable:** Backend
- **Salida:** [Spec de CM-179](../../specs/CM-179-VerificacionCorreo/spec.md), decisión D3
- **Decisión humana (HITL):** Paula, 6-oct-2026 (pregunta 5). Revisión de esta entrada: PENDIENTE (Paula)

## Qué cambió el rumbo

La IA propuso quitar `@Transactional` de `ActivateAccountService.activate` y traducir en el adaptador el fallo de versión optimista a `AccountConflictException`, porque ese fallo ocurre al confirmar, fuera del método, y sin transacción el servicio puede releer y responder 200 a la petición que pierde la carrera.

## Prompt

```
Rol: eres ingeniero de Backend de CAMEIA y trabajas para la dueña del Backend. Resuelves la verificación del correo de cameia-cuentas
conforme a su estándar de calidad y a su regla de errores visibles.

Contexto: la activación de una cuenta en `cameia-cuentas`, que hoy es transaccional y responde 500 cuando dos activaciones de la misma cuenta se cruzan.

Tarea: compara tres alternativas: capturar el fallo en el manejador global, bloqueo pesimista de la fila o quitar la transacción y traducir el conflicto en el adaptador; decide cuál cumple que la cuenta se escriba una sola vez y que ninguna petición reciba 500.

Condiciones: sin dependencias nuevas; el dominio no conoce JPA; todo fallo previsible tiene excepción de negocio, código estable y prueba; toda afirmación sobre un sistema externo se prueba o se declara como no conocida; la decisión queda en la spec con
su porqué y las alternativas descartadas.

Formato: una tabla de alternativas con su veredicto y la redacción final del requisito que cambia.
```
