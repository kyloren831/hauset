-- ============================================================================
-- V9__informe_ubicaciones.sql
-- El informe técnico se organiza por ubicaciones: cada ubicación tiene una foto
-- marcada (opcional), una nota y uno o varios productos. Así, 4 cámaras en 4
-- lugares distintos son 4 ubicaciones, cada una con su foto.
-- ============================================================================

CREATE TABLE informe_tecnico_ubicaciones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    informe_id UUID NOT NULL REFERENCES informes_tecnicos(id) ON DELETE CASCADE,
    nota TEXT,
    orden INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_informe_ubicaciones_informe ON informe_tecnico_ubicaciones(informe_id, orden);

-- Datos existentes: cada ítem pasa a ser su propia ubicación. Se reutiliza el id del
-- ítem como id de la ubicación para que su foto quede asociada sin copiar archivos.
INSERT INTO informe_tecnico_ubicaciones (id, informe_id, orden)
SELECT id, informe_id, orden FROM informe_tecnico_items;

ALTER TABLE informe_tecnico_items ADD COLUMN ubicacion_id UUID
    REFERENCES informe_tecnico_ubicaciones(id) ON DELETE CASCADE;
UPDATE informe_tecnico_items SET ubicacion_id = id, orden = 0;
ALTER TABLE informe_tecnico_items ALTER COLUMN ubicacion_id SET NOT NULL;
CREATE INDEX idx_informe_items_ubicacion ON informe_tecnico_items(ubicacion_id);
-- Un producto no se repite dentro de la misma ubicación (sí en ubicaciones distintas).
ALTER TABLE informe_tecnico_items
    ADD CONSTRAINT uq_informe_items_ubicacion_producto UNIQUE (ubicacion_id, producto_id);

UPDATE archivos_adjuntos SET entidad_tipo = 'INFORME_TECNICO_UBICACION'
WHERE entidad_tipo = 'INFORME_TECNICO_ITEM';
