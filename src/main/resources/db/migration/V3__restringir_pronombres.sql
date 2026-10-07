-- Los pronombres admitidos son los del enumerado Pronoun del dominio. La columna sigue admitiendo
-- NULL porque la anonimizacion vacia el dato. Una fila con otro valor impide aplicar esta migracion.
ALTER TABLE microcuentas.cuenta
    ADD CONSTRAINT ck_cuenta_pronombres_valor
    CHECK (pronombres IS NULL OR pronombres IN ('HE', 'SHE', 'THEY'));
