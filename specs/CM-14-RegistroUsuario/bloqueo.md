# Bloqueo CM-14 — Cuentas que nunca verifican el correo

- **Estado:** ⛔ Abierto, a la espera del Product Owner
- **Planteado por:** Juan David Vela Coronado
- **Fecha:** 17/09/2026
- **Spec:** [spec.md](spec.md) · clarificación `CU-TBD-04`
- **Bloquea:** el cierre de la HU. No bloquea empezar a implementar el registro.

---

## La pregunta

Una cuenta registrada nace en estado `PENDING_VERIFICATION` y solo pasa a `ACTIVE` cuando la persona
abre el enlace que Firebase le envía por correo.

**¿Qué pasa con una cuenta que nunca se verifica?**

Ni los criterios de aceptación ni la HU lo dicen, y no es una decisión técnica: es una decisión de
producto sobre qué se hace con los datos de alguien que se registró y no volvió.

## Por qué importa

1. **El correo queda ocupado para siempre.** Firebase ya guarda la credencial, así que ese correo no
   se puede volver a registrar. Si la persona se equivocó al escribirlo, o si alguien registra un
   correo ajeno, el dueño real de ese correo queda sin poder crear su cuenta y sin manera de
   liberarlo por sí mismo.
2. **Se acumulan datos personales sin consentimiento confirmado.** La tabla `cuenta` guarda nombres,
   apellidos, fecha de nacimiento y, si los dio, celular y pronombres, de alguien que nunca confirmó
   que ese correo es suyo.
3. **Es una vía de abuso barata.** Sin caducidad, registrar cuentas sin verificar no cuesta nada y
   nadie las limpia.

## Opciones que el equipo técnico puede implementar

| Opción | Qué implica |
|---|---|
| **A. Caducidad con borrado** | Tras N días sin verificar, se elimina la credencial en Firebase y la fila en `cuenta`. El correo queda libre. Necesita un proceso programado y definir N |
| **B. Caducidad con anonimización** | Igual que A, pero la fila se anonimiza en vez de borrarse, como ya prevé el modelo de datos. Conserva la traza, no libera el correo salvo que también se borre en Firebase |
| **C. Reenvío del enlace** | Sin caducidad, pero con una forma de pedir de nuevo el correo de verificación. Reduce el problema del enlace perdido, no el del correo ajeno ni el de la acumulación |
| **D. Sin caducidad** | Se acepta el estado actual y se revisa cuando haya datos de uso. Decisión válida, pero conviene que sea explícita y no un olvido |

## Lo que se necesita del Product Owner

1. Cuál de las opciones se adopta, o una distinta.
2. Si hay caducidad, **cuántos días** dura el estado `PENDING_VERIFICATION`.
3. Si el borrado alcanza también a la credencial en Firebase, es decir, si el correo vuelve a quedar
   libre.
4. Si se avisa por correo antes de caducar.

## Mientras tanto

CM-14 se implementa tal como está especificado: la cuenta queda en `PENDING_VERIFICATION` de forma
indefinida y no hay proceso de limpieza. Lo que se decida aquí se implementa en una HU propia, y
esta spec queda referenciada desde ella.
