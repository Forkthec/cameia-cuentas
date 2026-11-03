# Entrada 03 · Verificar con evidencia real antes de dar algo por resuelto

- **Fecha:** 2026-10-07 · **Herramienta:** Claude Code · **Responsable:** Backend
- **Salida:** [Spec de CM-36](../../specs/CM-36-ValidacionesRegistro/spec.md), sección 13 (D14, redacción corregida, y D18) y
  sección 15 (pregunta 43)

## Qué cambió el rumbo

Tras la revisión final, la IA dio por cerrados los CA leyendo el código y las pruebas, y afirmó que el rechazo de un correo por
Firebase «solo puede ocurrir contra el Firebase real» sin haberlo comprobado. La dueña del Backend preguntó cómo lo sabía y pidió
confirmar cada mensaje y comportamiento de los CA. Al comprobarlo con peticiones reales:

- El recorrido de los CA contra la aplicación empaquetada encontró que REQ-RV-64 no se cumplía: varias reglas de forma salían de a
  una. Las pruebas no lo cubrían porque ninguna combinaba reglas del dominio con reglas del contrato (D18).
- La afirmación sobre Firebase era una suposición. Se reemplazó por lo verificado: el SDK real ante un servidor que responde como
  Identity Toolkit entrega `INVALID_ARGUMENT` y la persona recibe 422; que Firebase rechace un correo que pasa la regla propia sigue
  sin conocerse (D14).

## Prompt

```
Rol: eres el verificador de Backend de CAMEIA y trabajas para la dueña del Backend.

Contexto: el stack CM-36 tiene las pruebas en verde y una revisión que da los CA por cumplidos. Algunas conclusiones salieron de leer
el código y de suponer cómo se comporta Firebase.

Tarea: (1) levanta la aplicación empaquetada con el emulador de Firebase Auth y una base desechable; (2) ejecuta por HTTP cada CA de
HU-1.1 que sea de Backend, con el estado, el campo, el código y el mensaje literal del backlog, y comprueba lo guardado en la base y
en Firebase; (3) combina varios campos inválidos en una misma petición; (4) toda afirmación sobre un sistema externo se comprueba
con el SDK real (si el emulador no reproduce el caso, con un servidor que responda como el servicio) o se declara como no conocida.

Condiciones: no se corrige lo que es de otra tarea, se anota con su destino; el log del recorrido no debe tener correos ni
contraseñas; al terminar se borran los datos de prueba.

Formato: una línea por caso con OK o NO, lo esperado y lo obtenido; el total; lo que no cumple, con su causa y su corrección.
```
