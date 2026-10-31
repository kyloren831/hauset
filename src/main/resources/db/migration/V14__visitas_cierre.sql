-- ============================================================================
-- V14__visitas_cierre.sql
-- Visitas con su propio avance (RF-PRY-11), "requiere retorno" con motivo
-- (RF-PRY-12, RN-09), firma de conformidad al cerrar (RF-PRY-13, RN-07) y
-- reporte de entrega guardado (RF-PRY-15).
-- ============================================================================

-- Un retorno espera fecha y persona hasta que un jefe o el ingeniero industrial lo agenda (RF-AGE-04);
-- una visita que el equipo inicia en sitio sin agenda no tiene quién la agendó.
ALTER TABLE visitas ALTER COLUMN fecha_hora DROP NOT NULL;
ALTER TABLE visitas ALTER COLUMN asignado_a DROP NOT NULL;
ALTER TABLE visitas ALTER COLUMN agendado_por DROP NOT NULL;
ALTER TABLE visitas
    ADD COLUMN motivo TEXT,                      -- para qué es la visita (p. ej. el motivo del retorno)
    ADD COLUMN iniciada_en TIMESTAMPTZ,
    ADD COLUMN iniciada_por UUID REFERENCES usuarios(id),
    ADD COLUMN terminada_en TIMESTAMPTZ,
    ADD COLUMN terminada_por UUID REFERENCES usuarios(id);
ALTER TABLE visitas ADD CONSTRAINT chk_visitas_estado CHECK (estado IN
    ('PENDIENTE_AGENDAR', 'AGENDADA', 'EN_CURSO', 'COMPLETADA', 'REQUIERE_RETORNO', 'CANCELADA'));
ALTER TABLE visitas ADD CONSTRAINT chk_visitas_agendada
    CHECK (estado <> 'AGENDADA' OR (fecha_hora IS NOT NULL AND asignado_a IS NOT NULL));
ALTER TABLE visitas ADD CONSTRAINT uq_visitas_numero UNIQUE (proyecto_id, numero_visita);
-- Una sola visita en curso por proyecto.
CREATE UNIQUE INDEX uq_visitas_en_curso ON visitas(proyecto_id) WHERE estado = 'EN_CURSO';

-- Avance por visita: en qué visita se completó (o se marcó "no aplica") cada tarea.
ALTER TABLE checklist_tareas ADD COLUMN completada_en_visita_id UUID REFERENCES visitas(id);
CREATE INDEX idx_checklist_tareas_visita ON checklist_tareas(completada_en_visita_id);

-- Firma: la imagen va en archivos_adjuntos (FIRMA_CONFORMIDAD / FIRMA_CLIENTE). No se edita ni se borra.
ALTER TABLE firmas_conformidad
    ADD COLUMN archivo_id UUID REFERENCES archivos_adjuntos(id),
    ADD COLUMN registrada_por UUID REFERENCES usuarios(id);
CREATE TRIGGER trg_firmas_conformidad_inmutable BEFORE UPDATE OR DELETE ON firmas_conformidad
    FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();

-- Reporte de entrega: el PDF generado al cerrar, tal como se entregó.
ALTER TABLE reportes_entrega ADD COLUMN archivo_id UUID REFERENCES archivos_adjuntos(id);
CREATE TRIGGER trg_reportes_entrega_inmutable BEFORE UPDATE OR DELETE ON reportes_entrega
    FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();
