# ADR 0002 · Registro repetido con una cuenta pendiente de verificar

## Estado

Aceptada. Decisión de Backend con aprobación de la dueña del Backend, 8 de octubre de 2026.

## Contexto

`POST /api/v1/users` respondía 409 a cualquier correo que ya tuviera credencial. Si la persona perdía la respuesta de un registro que sí se completó (red lenta, pestaña cerrada, el navegador cortó a los 10 s), al reintentar quedaba bloqueada: la cuenta existía, pero estaba pendiente de verificar y ella no lo sabía. El 409 tampoco decía a qué campo se refería, a diferencia del 422.

## Decisión

- **200 además de 201.** Si el correo ya tiene una cuenta pendiente de verificar y su credencial está habilitada, el registro devuelve esa misma cuenta con el mismo cuerpo que el 201 y no escribe nada: ni la contraseña se verifica ni los demás datos del cuerpo se usan. No entrega nada que la persona no hubiera recibido al registrarse. Un cuerpo inválido sigue siendo 422 antes de consultar a Firebase.
- **409 con `errors`.** El conflicto conserva estado, `title`, `detail` y `code`, y agrega `errors` con un elemento `field=email`, `code=EMAIL_ALREADY_REGISTERED`, para que el cliente lo muestre junto al campo. Una cuenta activa, bloqueada o anonimizada, una credencial deshabilitada y una credencial sin cuenta local dan el mismo 409, para no distinguir entre ellos; el 200 sí revela que la cuenta sigue pendiente (ver consecuencias).
- **Clasificador por antigüedad.** Una credencial sin cuenta local de menos de 300 s puede ser un registro en curso: se registra un `WARN`. Con 300 s o más es un residuo que alguien debe conciliar: un solo `ERROR`. El umbral es alto porque el SDK de Firebase reintenta cada llamada hasta cuatro veces y no es configurable: un registro lento puede durar unos 190 s. Una fecha de creación desconocida se toma por antigua: Firebase siempre la informa, y si faltara, avisar de más es mejor que dejar la credencial sin conciliar para siempre. Tras perder una carrera nunca se marca para conciliar, porque la credencial es recién creada.
- **Carrera.** Si otra petición crea la credencial entre la consulta y la creación, se consulta una sola vez más y se evalúa igual. Nunca termina en 500.
- **Credencial propia reconocible.** El servicio genera el `uid` de la credencial y se lo da a Firebase al crearla. Si el SDK reintenta por su cuenta una creación que sí se completó (la respuesta se perdió por el tiempo de espera), Firebase responde que el correo ya existe; al consultar, la credencial trae el `uid` propio y el registro continúa (plan y cuenta local) en lugar de dejarla sin cuenta. Si trae otro `uid`, es de otra petición y se aplica la regla de la carrera.
- **Creación sin respuesta.** Si la creación no responde (por ejemplo, vence el tiempo de lectura de 5 s), Firebase puede haberla completado igual; comprobado con el emulador, el SDK no reintenta en ese caso. El servicio borra la credencial propia antes de responder 503, para que el reintento de la persona no reciba un 409 por una credencial que ella nunca vio. Si el borrado tampoco responde, deja un `WARN` con el `uid` y la concilia la purga.
- **Tiempos de espera de Firebase** de 3 s para conectar y 5 s para cada respuesta, por intento. Con Firebase degradado el SDK reintenta y una llamada puede superar los 30 s del Gateway; el navegador y el Gateway cortan antes y la persona reintenta.
- **Cuota de Firebase** (`RESOURCE_EXHAUSTED`) es indisponibilidad (503), no un defecto.

## Consecuencias

- Frontend debe tratar el 200 de este endpoint como éxito igual que el 201, y puede leer `errors[0]` del 409.
- La respuesta 200 distingue «pendiente» de los demás estados y entrega el `id` y el `firebaseUid` a quien conozca el correo. Se acepta: no son credenciales (la identidad sale del token de Firebase), que el correo está registrado ya lo revelan los 409, quien se registró ya los recibió, y el control contra el abuso es el límite de peticiones previo al Gateway.
- El registro hace una llamada más a Firebase (la consulta por correo) en cada petición.
- Si dos peticiones idénticas llegan a la vez, la que pierde la carrera puede recibir 409 mientras la otra aún no guarda la cuenta; al reintentar recibe 200. Es el comportamiento aceptado.
- Quien conozca un correo con una credencial sin cuenta local de más de 300 s puede provocar un `ERROR` por petición. Se acepta: la ruta es pública pero el límite de peticiones previo al Gateway lo acota, y el aviso es el que pide la conciliación.
- **Registro previo del correo de otra persona.** Quien registra antes el correo de una víctima, con su propia contraseña, deja una cuenta pendiente; cuando la víctima se registra recibe un 200 en lugar de un 409 y no se entera. El riesgo ya existía (el atacante podía registrar primero), pero el 409 alertaba a la víctima. El criterio CA-1.1.30 ya lo cubre en el cliente: al recibir el 200, `cameia-web` inicia una sesión transitoria con la contraseña escrita y, si no coincide, muestra «Ese correo ya tiene una cuenta.» con los enlaces «Iniciar sesión» y «Recuperar contraseña» (CA-1.1.2); nunca debe mostrar un error genérico. La mitigación de fondo (qué pasa cuando se verifica el correo de una cuenta que nunca usó su dueño) es una decisión de producto que se lleva a la tarea de verificación de correo.
- Una credencial que queda sin cuenta local porque también falló el borrado compensatorio bloquea ese correo con el 409 hasta que se concilie. La conciliación (borrar la credencial que lleva más de 300 s sin cuenta local) es trabajo de la purga de cuentas sin verificar de la tarea de verificación de correo.

## Alternativas descartadas

- Tratar como propia toda credencial creada después de que empezó la petición: no distingue la de otra petición concurrente con el mismo correo, y la que pierde la carrera al guardar la compensaría borrando la credencial de la ganadora. El `uid` generado por la petición sí lo distingue.
- No borrar la credencial propia cuando la creación no responde: deja el correo bloqueado con 409 para la persona que reintenta, que es el problema que esta decisión resuelve. Con Firebase caído el borrado suma espera a una petición que ya falló con 503, y la huérfana que quede la concilia la purga.
- Devolver solo `status` y `plan` en el 200: es lo más cerrado, pero rompe el contrato que `cameia-web` ya tipa y obliga a avisar a Frontend.
- Un código propio para la credencial huérfana (`REGISTRATION_INCOMPLETE`) o borrarla sola al reintentar: lo primero no desbloquea a la persona y lo segundo es destructivo y puede equivocarse con un registro lento.
- Seguir con 409 y pedir a Frontend que inicie sesión: no resuelve a quien aún no puede iniciarla porque no verificó el correo.
- Verificar la contraseña en el reintento: exigiría una llamada adicional a Firebase y revelaría si la contraseña coincide.
- Un contador de tiempo en la base para el registro en curso: añade estado y una migración para un caso que el tiempo de la credencial ya distingue.
