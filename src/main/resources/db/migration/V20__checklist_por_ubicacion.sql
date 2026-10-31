-- ============================================================================
-- V20__checklist_por_ubicacion.sql
-- Cada ubicación del informe técnico puede traer su propia checklist (opcional).
-- Al iniciar el proyecto se COPIA a proyecto_checklist_items: editar el informe
-- después (que crea otra versión) no cambia un proyecto ya creado.
--
-- Ubicación con checklist: no genera tareas por producto; queda completada cuando
-- todos sus ítems están marcados y tiene foto de antes y de después (fotos de la
-- ubicación, en archivos_adjuntos con entidad PROYECTO_UBICACION). Ese estado se
-- calcula siempre a partir de los ítems y las fotos: no se guarda.
-- Ubicación sin checklist: igual que antes (una tarea por producto).
--
-- Datos existentes: no hay checklist por ubicación, así que informes y proyectos
-- actuales siguen con la regla anterior sin cambios.
-- ============================================================================

CREATE TABLE informe_ubicacion_checklist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ubicacion_id UUID NOT NULL REFERENCES informe_tecnico_ubicaciones(id) ON DELETE CASCADE,
    texto VARCHAR(300) NOT NULL,
    orden INTEGER NOT NULL,
    CONSTRAINT chk_informe_checklist_texto CHECK (length(btrim(texto)) > 0),
    CONSTRAINT chk_informe_checklist_orden CHECK (orden >= 0),
    CONSTRAINT uq_informe_checklist_orden UNIQUE (ubicacion_id, orden)
);

CREATE TABLE proyecto_checklist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id UUID NOT NULL REFERENCES proyectos(id),
    -- Las versiones del informe no cambian, así que la ubicación (nota y foto) queda fija.
    ubicacion_id UUID NOT NULL REFERENCES informe_tecnico_ubicaciones(id),
    origen_item_id UUID REFERENCES informe_ubicacion_checklist(id),
    texto VARCHAR(300) NOT NULL,
    orden INTEGER NOT NULL,
    marcado BOOLEAN NOT NULL DEFAULT false,
    marcado_por UUID REFERENCES usuarios(id),
    marcado_en TIMESTAMPTZ,
    marcado_en_visita_id UUID REFERENCES visitas(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_proyecto_checklist_texto CHECK (length(btrim(texto)) > 0),
    -- Marcado siempre dice quién y cuándo; desmarcado no guarda ninguno de los dos.
    CONSTRAINT chk_proyecto_checklist_marcado CHECK (
        (marcado AND marcado_por IS NOT NULL AND marcado_en IS NOT NULL)
        OR (NOT marcado AND marcado_por IS NULL AND marcado_en IS NULL AND marcado_en_visita_id IS NULL))
);
CREATE INDEX idx_proyecto_checklist_proyecto ON proyecto_checklist_items(proyecto_id, ubicacion_id, orden);
CREATE INDEX idx_proyecto_checklist_visita ON proyecto_checklist_items(marcado_en_visita_id)
    WHERE marcado_en_visita_id IS NOT NULL;
