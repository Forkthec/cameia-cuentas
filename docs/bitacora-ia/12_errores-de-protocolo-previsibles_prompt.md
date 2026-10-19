# Entrada 12 · Errores de protocolo previsibles

- **Fecha:** 2026-10-17 · **Herramienta:** Claude Code · **Responsable:** Backend
- **Salida:** [Spec de CM-335](../../specs/CM-335-ErroresDeProtocoloPrevisibles/spec.md), decisiones D-1 a D-7
- **Decisión humana (HITL):** PENDIENTE (Paula)

## Qué cambió el rumbo

Al revisar con peticiones reales qué entradas previsibles terminaban en el error genérico, la IA propuso cómo responder un formulario
mal codificado, una identidad con caracteres de control y lo que el contenedor reporta con `sendError`. Las decisiones que cambian el
comportamiento visible quedaron en la spec con su porqué y sus alternativas descartadas:

- **D-1:** el formulario mal codificado responde 415 con el código del formulario bien formado, y no con un código nuevo.
- **D-3:** Cuentas no agrega el código de consulta mal codificada, porque ninguna ruta lee parámetros de consulta.
- **D-4:** la identidad con caracteres de control responde 400 como la identidad ausente, y no 401.
- **D-5:** un controlador de errores propio atiende `/error` para que `TRACE` y cualquier `sendError` salgan con la forma común.

## Prompt

```
Rol: eres ingeniero de Backend de CAMEIA y trabajas para la dueña del Backend. Revisas cameia-cuentas contra su regla de errores
visibles: todo fallo previsible tiene su excepción, su código estable y su mensaje; el 500 genérico es solo para lo que nadie pudo prever.

Contexto: Cuentas es un microservicio Spring Boot 4.1.1 detrás del Gateway. Solo lee la identidad del encabezado X-User-Id y no lee
parámetros de consulta. Tomcat y Spring responden por su cuenta a algunas peticiones mal formadas antes del manejador de errores.

Tarea: con peticiones reales contra el jar empaquetado, lista cada entrada previsible que hoy termina en un 500, en una página HTML o
en la forma por defecto de Spring (formularios mal codificados, multipart sin boundary, Accept ilegible, TRACE, /error, identidad con
caracteres de control). Para cada una propone el estado, el código, el mensaje y la prueba; si hay más de una opción válida con efecto
visible, presenta las opciones con su recomendación y consecuencias.

Condiciones: no se cambia un contrato publicado sin avisar a Frontend; un código nuevo solo si alguna ruta lo puede producir; lo que
el contenedor rechaza antes de la aplicación se documenta con su razón; ninguna respuesta ni registro repite el valor recibido.

Formato: una tabla de hallazgos con la petición, el resultado de hoy, el resultado propuesto y la prueba; después las decisiones con
su porqué y las alternativas descartadas.
```
