# Bitácora de IA

Esta carpeta guarda la **entrada** de cada decisión en la que una persona cambió el rumbo de una propuesta de la IA (alcance, arquitectura, seguridad, costo, calidad o proceso): el prompt armado con rol, contexto, tarea, condiciones y formato. La **salida** es la spec del repositorio (`specs/CM-NNN-*/spec.md`, sección de decisiones, con la decisión humana, el porqué y las alternativas descartadas); la bitácora la enlaza y no la copia.

Cada entrada es un archivo `NN_tema_prompt.md`, con `NN` consecutivo.

| Entrada | Decisión | Salida |
|---|---|---|
| [01_alinear-estandar-backend_prompt.md](01_alinear-estandar-backend_prompt.md) | Alinear los documentos y el estándar de los cuatro repositorios de Backend | [Spec de CM-283](../../specs/CM-283-AlinearEstandar/spec.md) |
| [02_errores-conocidos-en-el-respaldo_prompt.md](02_errores-conocidos-en-el-respaldo_prompt.md) | Errores conocidos que terminaban en `INTERNAL_ERROR`, cerrados con los CA y una prueba de guarda | [Spec de CM-36](../../specs/CM-36-ValidacionesRegistro/spec.md), D14 a D17 |
| [03_verificar-con-evidencia-real_prompt.md](03_verificar-con-evidencia-real_prompt.md) | Verificar cada CA y cada afirmación sobre Firebase con peticiones reales antes de darlos por resueltos | [Spec de CM-36](../../specs/CM-36-ValidacionesRegistro/spec.md), D14 y D18 |
| [04_credencial-con-uid-propio_prompt.md](04_credencial-con-uid-propio_prompt.md) | Credencial propia reconocida por su `uid` y borrada cuando la creación en Firebase no responde | [Spec de CM-251](../../specs/CM-251-RegistroRepetido/spec.md), D17, D18 y REQ-RR-18 |
| [05_activacion-sin-transaccion_prompt.md](05_activacion-sin-transaccion_prompt.md) | Activación sin transacción y conflicto de versión como excepción de dominio | [Spec de CM-179](../../specs/CM-179-VerificacionCorreo/spec.md), D3 |
| [06_tope-de-la-purga_prompt.md](06_tope-de-la-purga_prompt.md) | Tope de 1000 cuentas por ejecución de la purga | [Spec de CM-179](../../specs/CM-179-VerificacionCorreo/spec.md), D5 |
| [07_ventana-entre-consulta-y-borrado_prompt.md](07_ventana-entre-consulta-y-borrado_prompt.md) | Ventana aceptada entre consultar la verificación y borrar la credencial | [Spec de CM-179](../../specs/CM-179-VerificacionCorreo/spec.md), D6 |
| [08_firebase-caido-detiene-la-purga_prompt.md](08_firebase-caido-detiene-la-purga_prompt.md) | Firebase caído detiene la purga y el Job termina con error | [Spec de CM-179](../../specs/CM-179-VerificacionCorreo/spec.md), D10 |
| [09_fila-activa-sin-credencial_prompt.md](09_fila-activa-sin-credencial_prompt.md) | Fila activa sin credencial tras la purga se registra en ERROR | [Spec de CM-179](../../specs/CM-179-VerificacionCorreo/spec.md), D12 |
| [10_conciliacion-de-credenciales-huerfanas_prompt.md](10_conciliacion-de-credenciales-huerfanas_prompt.md) | Alcance de la conciliación de credenciales huérfanas | [Spec de CM-179](../../specs/CM-179-VerificacionCorreo/spec.md), pregunta 18 |
