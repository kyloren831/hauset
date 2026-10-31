-- ============================================================================
-- V12__proyectos_checklist.sql
-- Checklist de instalación: cada tarea dice qué producto, cuántos y en qué
-- ubicación del informe técnico; quién la completó y cuándo. Evidencia de antes
-- y después en archivos_adjuntos (entidad CHECKLIST_TAREA). Bitácora del
-- proyecto de solo inserción (RF-PRY-07).
-- ============================================================================

ALTER TABLE checklist_tareas
    ADD COLUMN ubicacion_id UUID REFERENCES informe_tecnico_ubicaciones(id),
    ADD COLUMN producto_id UUID REFERENCES catalogo_productos(id),
    ADD COLUMN cantidad INTEGER,
    ADD COLUMN vigente BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN origen_tarea_id UUID REFERENCES checklist_tareas(id),
    ADD COLUMN completada_por UUID REFERENCES usuarios(id),
    ADD COLUMN completada_en TIMESTAMPTZ;
ALTER TABLE checklist_tareas ADD CONSTRAINT chk_checklist_cantidad CHECK (cantidad IS NULL OR cantidad > 0);
ALTER TABLE checklist_tareas ADD CONSTRAINT chk_checklist_estado
    CHECK (estado IN ('PENDIENTE', 'COMPLETADA', 'NO_APLICA'));

CREATE TABLE proyecto_eventos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id UUID NOT NULL REFERENCES proyectos(id),
    tipo VARCHAR(40) NOT NULL,
    usuario_id UUID REFERENCES usuarios(id),
    -- El nombre se guarda tal cual: si el usuario se desactiva, el historial no cambia.
    usuario_nombre VARCHAR(150),
    detalle TEXT,
    fecha TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_proyecto_eventos_proyecto ON proyecto_eventos(proyecto_id, fecha);
CREATE TRIGGER trg_proyecto_eventos_inmutable BEFORE UPDATE OR DELETE ON proyecto_eventos
    FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();

-- Bitácora de los proyectos que ya existían.
INSERT INTO proyecto_eventos (proyecto_id, tipo, detalle, fecha)
SELECT id, 'PROYECTO_CREADO', 'Creado al aprobar la cotización', fecha_creacion FROM proyectos;
