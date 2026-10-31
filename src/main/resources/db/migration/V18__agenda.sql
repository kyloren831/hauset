-- ============================================================================
-- V18__agenda.sql
-- Agenda compartida con aviso de cruces (RF-AGE-01..05, E12).
-- Sin duración no se puede saber si dos visitas de la misma persona se cruzan:
-- cada visita dura 2 horas salvo que quien agenda diga otra cosa (15 min a 12 h).
-- ============================================================================

ALTER TABLE visitas ADD COLUMN duracion_minutos INTEGER NOT NULL DEFAULT 120;
ALTER TABLE visitas ADD CONSTRAINT chk_visitas_duracion CHECK (duracion_minutos BETWEEN 15 AND 720);

-- Buscar cruces: visitas agendadas de una persona alrededor de una hora.
CREATE INDEX idx_visitas_asignado_fecha ON visitas(asignado_a, fecha_hora) WHERE estado = 'AGENDADA';
