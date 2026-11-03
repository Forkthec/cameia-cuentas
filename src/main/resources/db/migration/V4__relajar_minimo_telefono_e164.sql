-- El celular admite de 6 a 15 digitos en total, contando el indicativo del pais.
--
-- La restriccion anterior exigia al menos 8. Hay numeros validos mas cortos: Tristan da Cunha
-- (+290), Tokelau (+690) y Niue (+683) tienen numeros de 7 digitos, y el numero posible mas corto
-- de cualquier pais tiene 6. El formulario web los acepta con la misma libreria de validacion
-- que usa el servicio, asi que la base no debe rechazarlos con un error interno. El maximo de 15
-- es el de E.164 y no cambia.
--
-- Solo relaja la regla: toda fila que cumplia la anterior cumple esta, asi que ningun dato
-- cambia ni se revisa. Se borra y se vuelve a crear con el mismo nombre en una sola transaccion
-- (Flyway envuelve cada migracion de PostgreSQL en una), de modo que no hay un instante sin
-- restriccion. La tabla se bloquea solo mientras se comprueban las filas existentes.

ALTER TABLE microcuentas.cuenta
    DROP CONSTRAINT ck_cuenta_telefono_e164;

ALTER TABLE microcuentas.cuenta
    ADD CONSTRAINT ck_cuenta_telefono_e164
    CHECK (telefono IS NULL OR telefono ~ '^\+[1-9][0-9]{5,14}$');
