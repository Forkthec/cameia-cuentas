-- La tabla de salida admite el evento de cuenta eliminada que publica la purga.
ALTER TABLE microcuentas.evento_saliente
    DROP CONSTRAINT ck_evento_saliente_tipo,
    ADD CONSTRAINT ck_evento_saliente_tipo CHECK (tipo IN ('cuenta.creada', 'cuenta.eliminada'));
