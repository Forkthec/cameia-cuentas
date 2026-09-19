-- CM-14 | Esquema inicial de MicroCuentas: solo lo que el registro de usuario necesita.
--
-- Deriva del DDL aprobado de MicroCuentas v1.0 (30/08/2026), con tres diferencias
-- deliberadas documentadas en specs/CM-14-RegistroUsuario/spec.md (CU-1, CU-7 y CU-8):
--
--  1. El estado PENDING_VERIFICATION existe y es el valor por defecto. Una cuenta no puede
--     nacer ACTIVE: hasta que el correo se verifique, la plataforma no debe tratarla como
--     utilizable.
--  2. No se instalan pgcrypto ni btree_gist. Los identificadores los genera la aplicacion y
--     el HMAC pertenece a la anonimizacion, que no entra en esta historia; btree_gist sirve a
--     las suscripciones, que tampoco.
--  3. No se crean funciones ni triggers. Las reglas de negocio viven en el paquete domain con
--     pruebas propias. Aqui quedan solo las restricciones que protegen la integridad aunque el
--     codigo falle.
--
-- Las tablas de planes, suscripciones, pagos y Outbox llegaran con la especificacion que las
-- use, cada una en su propia migracion.

CREATE SCHEMA IF NOT EXISTS microcuentas;

CREATE TABLE microcuentas.cuenta (
    id UUID PRIMARY KEY,
    firebase_uid VARCHAR(128) NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    apellido VARCHAR(120) NOT NULL,
    fecha_nacimiento DATE NULL,
    telefono VARCHAR(16) NULL,
    pronombres VARCHAR(60) NULL,
    estado VARCHAR(24) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    version BIGINT NOT NULL DEFAULT 1,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_eliminacion TIMESTAMPTZ NULL,

    CONSTRAINT uq_cuenta_firebase_uid UNIQUE (firebase_uid),
    CONSTRAINT ck_cuenta_nombre CHECK (btrim(nombre) <> ''),
    CONSTRAINT ck_cuenta_apellido CHECK (btrim(apellido) <> ''),
    CONSTRAINT ck_cuenta_estado
        CHECK (estado IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED', 'ANONYMIZED')),
    CONSTRAINT ck_cuenta_version CHECK (version >= 1),
    CONSTRAINT ck_cuenta_telefono_e164
        CHECK (telefono IS NULL OR telefono ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT ck_cuenta_pronombres_no_vacio
        CHECK (pronombres IS NULL OR btrim(pronombres) <> ''),
    CONSTRAINT ck_cuenta_fecha_actualizacion
        CHECK (fecha_actualizacion >= fecha_creacion),
    CONSTRAINT ck_cuenta_fecha_eliminacion
        CHECK (fecha_eliminacion IS NULL OR fecha_eliminacion >= fecha_creacion),
    CONSTRAINT ck_cuenta_anonimizacion
        CHECK ((estado = 'ANONYMIZED') = (fecha_eliminacion IS NOT NULL))
);

COMMENT ON TABLE microcuentas.cuenta IS
    'Cuenta local asociada a un usuario de Firebase. No guarda correo ni contrasena: esos datos son de Firebase Auth.';
COMMENT ON COLUMN microcuentas.cuenta.firebase_uid IS
    'Identificador del usuario en Firebase Auth. Es la unica referencia a la identidad.';
COMMENT ON COLUMN microcuentas.cuenta.estado IS
    'PENDING_VERIFICATION al registrarse, ACTIVE tras verificar el correo, DISABLED si se bloquea la cuenta, ANONYMIZED tras ejercer el derecho al olvido.';
