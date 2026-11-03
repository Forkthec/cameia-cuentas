-- CM-14 | La version de una cuenta nueva empieza en 0, no en 1.
--
-- El DDL original definia version BIGINT DEFAULT 1 con CHECK (version >= 1), pensado para
-- un disparador que la incrementaba en cada UPDATE. Ese disparador no existe: el bloqueo
-- optimista lo maneja JPA con @Version, y JPA siembra el valor inicial en 0. Con la
-- restriccion anterior, insertar cualquier cuenta fallaba.
--
-- Se relaja el minimo a 0 y el valor por defecto acompana a JPA. La restriccion sigue
-- cumpliendo su papel: impide una version negativa, que solo puede venir de un error.
--
-- No se modifica V1 porque ya esta aplicada: Flyway guarda su suma de verificacion y un
-- archivo alterado rompe el arranque en toda base que lo tenga.

ALTER TABLE microcuentas.cuenta
    ALTER COLUMN version SET DEFAULT 0;

ALTER TABLE microcuentas.cuenta
    DROP CONSTRAINT ck_cuenta_version;

ALTER TABLE microcuentas.cuenta
    ADD CONSTRAINT ck_cuenta_version CHECK (version >= 0);
