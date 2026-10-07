# Entrada 02 · Errores conocidos que terminaban en el respaldo `INTERNAL_ERROR`

- **Fecha:** 2026-10-07 · **Herramienta:** Claude Code · **Responsable:** Backend
- **Salida:** [Spec de CM-36](../../specs/CM-36-ValidacionesRegistro/spec.md), sección 13 (D14 a D17) y sección 15 (preguntas 36 a 42)

## Qué cambió el rumbo

La revisión final del stack encontró casos previsibles que respondían 500 `INTERNAL_ERROR`. Uno de ellos lo había decidido la propia
spec: REQ-RV-09 mandaba al respaldo el correo que Firebase rechaza, aunque REQ-RV-07 y D8 prohibían que un error conocido terminara
ahí. La revisión de la spec no cruzó esos textos. La dueña del Backend pidió revisar el detalle, cerrar con los criterios de
aceptación lo que ellos resolvieran y no con una consulta, y dejar el código limpio y consistente.

## Prompt

```
Rol: eres el revisor final de Backend de CAMEIA y trabajas para la dueña del Backend.

Contexto: el stack CM-36 (validaciones del registro de HU-1.1) está terminado. /code-review y /security-review señalaron fallos
que terminan en el manejador de respaldo con el texto genérico. La spec pide que ningún error conocido termine en INTERNAL_ERROR
(REQ-RV-07, D8) y CLAUDE.md §4 pide que el mensaje de negocio llegue tal cual a la persona.

Tarea: (1) inventaría todo fallo previsible que hoy llega al respaldo, con su origen y si está documentado; (2) explica por qué la
revisión de la spec no lo vio; (3) haz todas las preguntas; (4) cierra con los CA de HU-1.1, HU-1.2 y las reglas transversales del
backlog v4 las que ellos resuelvan; (5) corrige y deja una prueba que impida que vuelva a pasar.

Condiciones: lo que pertenece a otra tarea (CM-179, CM-251) se documenta con su destino y no se corrige aquí; nada se publica ni se
reescribe la historia sin permiso; cada mensaje es el literal del CA.

Formato: inventario en tabla, preguntas numeradas con recomendación, y la confirmación CA por CA de que cada mensaje y
comportamiento está resuelto, con la prueba que lo demuestra.
```
