-- Indice parcial para la purga diaria: solo las cuentas sin verificar, por antiguedad.
CREATE INDEX ix_cuenta_pendiente_fecha_creacion
    ON microcuentas.cuenta (fecha_creacion)
    WHERE estado = 'PENDING_VERIFICATION';
