-- ============================================================================
-- V11__versiones_informe.sql
-- El informe técnico se corrige creando una versión nueva (v.1, v.2, ...): las
-- anteriores quedan intactas. Cada versión puede tener una cotización (como mucho una).
-- La cotización rechazada se puede corregir y reenviar con el mismo número; cada
-- envío suma una revisión (Rev. 1, Rev. 2, ...).
-- ============================================================================

-- Todas las versiones de un informe comparten grupo_id.
ALTER TABLE informes_tecnicos ADD COLUMN grupo_id UUID;
ALTER TABLE informes_tecnicos ADD COLUMN numero_version INTEGER NOT NULL DEFAULT 1;
UPDATE informes_tecnicos SET grupo_id = id;
ALTER TABLE informes_tecnicos ALTER COLUMN grupo_id SET NOT NULL;
ALTER TABLE informes_tecnicos ADD CONSTRAINT uq_informes_grupo_version UNIQUE (grupo_id, numero_version);
ALTER TABLE informes_tecnicos ADD CONSTRAINT chk_informes_numero_version CHECK (numero_version >= 1);

-- Una cotización por versión del informe. Antes se podía volver a cotizar un informe tras un
-- rechazo; si un informe quedó con varias, se conserva el vínculo de la más reciente y las
-- anteriores quedan sin informe (no se borran).
UPDATE cotizaciones c SET informe_id = NULL
WHERE informe_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM cotizaciones o
              WHERE o.informe_id = c.informe_id
                AND (o.fecha > c.fecha OR (o.fecha = c.fecha AND o.id > c.id)));
ALTER TABLE cotizaciones ADD CONSTRAINT uq_cotizaciones_informe UNIQUE (informe_id);

-- Revisión: cuántas veces se envió (0 = nunca).
ALTER TABLE cotizaciones ADD COLUMN revision INTEGER NOT NULL DEFAULT 0;
UPDATE cotizaciones SET revision = 1 WHERE numero IS NOT NULL;
ALTER TABLE cotizaciones ADD CONSTRAINT chk_cotizaciones_revision CHECK (revision >= 0);
