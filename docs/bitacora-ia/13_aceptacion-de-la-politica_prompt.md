# Entrada 13 · Aceptación de la política de tratamiento de datos

- **Fecha:** 2026-11-12 · **Herramienta:** Claude Code · **Responsable:** Backend
- **Salida:** [Spec de CM-367](../../specs/CM-367-AceptacionPoliticas/spec.md), decisiones D-1 a D-5
- **Decisión humana (HITL):** PENDIENTE (Paula)

## Qué cambió el rumbo

Al armar la spec de la aceptación de la política, la IA propuso cómo responder cada caso del registro y de la ruta nueva. Paula decidió
el 12 de noviembre de 2026 lo que cambia el comportamiento visible, y quedó en la spec con su porqué y sus alternativas descartadas:

- **D-1:** el mensaje de la versión ausente es «Recarga la página y vuelve a aceptar la política.».
- **D-2:** una cuenta anterior a la migración responde `acceptedAt: null`, aunque la base conserve la fecha de creación.
- **D-3:** solo el valor JSON `true` cuenta como autorización; `"true"`, `1`, un arreglo o un objeto la rechazan.
- **D-4:** la versión vigente sale de la configuración con un valor por defecto en el código, y no solo de una variable de entorno.
- **D-5:** el PR de Cuentas se fusiona el mismo día que el de Frontend.

## Prompt

```
Rol: eres ingeniero de Backend de CAMEIA y trabajas para la dueña del Backend. Escribes la spec de la subtarea de Cuentas de la
aceptación de la política de tratamiento de datos (Ley 1581 de 2012) bajo la regla de errores visibles: todo fallo previsible tiene su
excepción, su código estable y su mensaje.

Contexto: Cuentas es un microservicio Spring Boot 4.1.1 detrás del Gateway. El registro valida el formulario completo y devuelve un
elemento por campo. Los textos de la política viven en la web; Cuentas solo guarda la versión aceptada y el instante.

Tarea: a partir de los criterios 3 a 7 de la tarea padre, redacta la spec con los requisitos en EARS, la matriz de validación por campo,
la migración, los casos borde, los códigos de error y las pruebas. Para cada punto que admita más de una respuesta válida con efecto
visible (mensajes, tratamiento de las cuentas anteriores, qué valores cuentan como autorización, de dónde sale la versión vigente,
orden de despliegue), presenta las opciones con tu recomendación y sus consecuencias, y no decidas en silencio.

Condiciones: sin datos personales en los registros; idempotencia de la ruta de aceptación; migración probada sobre una base con filas.
```
