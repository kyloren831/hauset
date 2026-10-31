-- =============================================================================
-- V15__cotizacion_sin_informe.sql
-- SRS v1.2: una cotización puede nacer en blanco desde un lead (RF-COT-12) y
-- recibir el informe técnico después (RF-COT-16). Los ítems pueden reemplazar
-- un producto por otro y recordar el original (RF-COT-14/15). Aceptar ya no
-- crea el proyecto: se inicia aparte y exige informe (RF-COT-09, RN-11).
-- =============================================================================

-- 1. Lead de la cotización (antes se deducía del informe).
ALTER TABLE cotizaciones ADD COLUMN lead_id UUID REFERENCES leads(id);
UPDATE cotizaciones c SET lead_id = i.lead_id
FROM informes_tecnicos i WHERE i.id = c.informe_id;
-- Las que V11 dejó sin informe: el lead más reciente del mismo cliente.
UPDATE cotizaciones c SET lead_id = (
    SELECT l.id FROM leads l WHERE l.cliente_id = c.cliente_id ORDER BY l.fecha DESC, l.id LIMIT 1)
WHERE c.lead_id IS NULL;
ALTER TABLE cotizaciones ALTER COLUMN lead_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_cotizaciones_lead ON cotizaciones(lead_id);

-- 2. Producto reemplazado (el que pedía el informe y el proveedor no tenía).
ALTER TABLE cotizacion_items ADD COLUMN producto_original_id UUID REFERENCES catalogo_productos(id);
ALTER TABLE cotizacion_items ADD COLUMN producto_original_nombre VARCHAR(255);
ALTER TABLE cotizacion_items ADD CONSTRAINT chk_cotizacion_items_original
    CHECK (producto_original_id IS NULL OR producto_original_id <> producto_id);

-- 3. Informe levantado después de la cotización: admite fotos mientras no haya proyecto.
ALTER TABLE informes_tecnicos ADD COLUMN desde_cotizacion BOOLEAN NOT NULL DEFAULT false;
