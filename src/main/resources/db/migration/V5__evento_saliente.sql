-- Tabla de salida (outbox) de los eventos que publica Cuentas.
-- El evento se guarda en la misma transaccion que el cambio que lo origina y se publica despues;
-- asi un fallo del broker no pierde eventos. Al confirmarse la publicacion la carga se borra
-- (datos personales minimos) y la fila queda como marca de que el evento ya existio.

CREATE TABLE microcuentas.evento_saliente (
    id                UUID          NOT NULL,
    tipo              VARCHAR(64)   NOT NULL,
    version           SMALLINT      NOT NULL,
    agregado_id       VARCHAR(128)  NOT NULL,
    carga             JSONB         NULL,
    id_correlacion    VARCHAR(64)   NOT NULL,
    fecha_creacion    TIMESTAMPTZ   NOT NULL,
    fecha_publicacion TIMESTAMPTZ   NULL,
    intentos          INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_evento_saliente PRIMARY KEY (id),
    CONSTRAINT uq_evento_saliente_tipo_agregado UNIQUE (tipo, agregado_id),
    CONSTRAINT ck_evento_saliente_tipo CHECK (tipo IN ('cuenta.creada')),
    CONSTRAINT ck_evento_saliente_version CHECK (version >= 1),
    CONSTRAINT ck_evento_saliente_agregado_id CHECK (btrim(agregado_id) <> ''),
    CONSTRAINT ck_evento_saliente_id_correlacion CHECK (id_correlacion ~ '^[A-Za-z0-9._-]{1,64}$'),
    CONSTRAINT ck_evento_saliente_intentos CHECK (intentos >= 0),
    CONSTRAINT ck_evento_saliente_carga_pendiente CHECK ((fecha_publicacion IS NULL) = (carga IS NOT NULL)),
    CONSTRAINT ck_evento_saliente_fecha_publicacion CHECK (fecha_publicacion IS NULL OR fecha_publicacion >= fecha_creacion)
);

-- Sirve al relevo: solo recorre los eventos pendientes, del mas antiguo al mas reciente.
CREATE INDEX ix_evento_saliente_pendiente ON microcuentas.evento_saliente (fecha_creacion)
    WHERE fecha_publicacion IS NULL;

COMMENT ON TABLE microcuentas.evento_saliente IS
    'Tabla de salida (outbox): eventos guardados junto con el cambio que los origina, pendientes de publicar o ya publicados';
COMMENT ON COLUMN microcuentas.evento_saliente.carga IS 'JSON del evento; nulo una vez publicado';
COMMENT ON COLUMN microcuentas.evento_saliente.agregado_id IS 'firebase_uid de la cuenta del evento';
