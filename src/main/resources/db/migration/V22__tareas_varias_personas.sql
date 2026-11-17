-- Una tarea de la oficina puede tener varias personas asignadas (RF-TAR-01); cualquiera de ellas, o
-- cualquiera de los tres que usan la lista, la puede marcar hecha (RF-TAR-03).

CREATE TABLE tarea_equipo_asignadas (
    tarea_id UUID NOT NULL REFERENCES tareas_equipo (id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuarios (id),
    PRIMARY KEY (tarea_id, usuario_id)
);
CREATE INDEX idx_tarea_equipo_asignadas_usuario ON tarea_equipo_asignadas (usuario_id);

INSERT INTO tarea_equipo_asignadas (tarea_id, usuario_id)
SELECT id, asignada_a FROM tareas_equipo WHERE asignada_a IS NOT NULL;

ALTER TABLE tareas_equipo DROP COLUMN asignada_a;
