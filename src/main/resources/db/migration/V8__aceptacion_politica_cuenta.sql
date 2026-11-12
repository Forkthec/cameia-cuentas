-- Versión de la política de tratamiento de datos que aceptó la persona y el instante en que la aceptó.
-- Las cuentas creadas antes de esta migración no aceptaron ninguna versión: quedan con 'sin-aceptar'
-- y la fecha de su creación, y la aplicación les pide aceptar la versión vigente.
-- Se agregan como nulas, se rellenan y después se exigen, para poder aplicarla sobre una tabla con filas.
ALTER TABLE microcuentas.cuenta ADD COLUMN politica_version VARCHAR(20);
ALTER TABLE microcuentas.cuenta ADD COLUMN politica_aceptada_en TIMESTAMPTZ;

UPDATE microcuentas.cuenta
   SET politica_version = 'sin-aceptar',
       politica_aceptada_en = fecha_creacion;

ALTER TABLE microcuentas.cuenta
    ALTER COLUMN politica_version SET NOT NULL,
    ALTER COLUMN politica_aceptada_en SET NOT NULL,
    ADD CONSTRAINT ck_cuenta_politica_version_no_vacia CHECK (btrim(politica_version) <> '');

COMMENT ON COLUMN microcuentas.cuenta.politica_version IS
    'Versión de la política aceptada (AAAA-MM-vN) o sin-aceptar en las cuentas anteriores a la aceptación';
COMMENT ON COLUMN microcuentas.cuenta.politica_aceptada_en IS
    'Instante UTC de la aceptación; en las cuentas sin-aceptar es la fecha de creación';
