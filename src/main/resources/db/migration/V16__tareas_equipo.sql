-- =============================================================================
-- V16__tareas_equipo.sql
-- Lista de tareas de la oficina (SRS 3.8, RF-TAR-01..08): tablero de tres columnas
-- (por hacer, en proceso, hecha) para los dos jefes y la secretaria. Es distinta del
-- checklist de un proyecto (checklist_tareas), que trabajan los instaladores.
-- =============================================================================

CREATE TABLE tareas_equipo (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    titulo VARCHAR(200) NOT NULL,
    descripcion TEXT,
    estado VARCHAR(20) NOT NULL DEFAULT 'POR_HACER',
    -- Privada: solo la ve quien la creó (RN-12).
    privada BOOLEAN NOT NULL DEFAULT false,
    fecha_limite DATE,
    creada_por UUID NOT NULL REFERENCES usuarios(id),
    asignada_a UUID REFERENCES usuarios(id),
    -- Registro al que se refiere (RF-TAR-04): a lo sumo uno.
    lead_id UUID REFERENCES leads(id) ON DELETE SET NULL,
    cotizacion_id UUID REFERENCES cotizaciones(id) ON DELETE SET NULL,
    proyecto_id UUID REFERENCES proyectos(id) ON DELETE SET NULL,
    -- MANUAL, o el evento que la creó sola (RF-TAR-05).
    origen VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
    completada_por UUID REFERENCES usuarios(id),
    completada_en TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT chk_tareas_equipo_estado CHECK (estado IN ('POR_HACER', 'EN_PROCESO', 'HECHA')),
    CONSTRAINT chk_tareas_equipo_origen CHECK (origen IN ('MANUAL', 'COTIZACION_ACEPTADA')),
    CONSTRAINT chk_tareas_equipo_titulo CHECK (length(trim(titulo)) > 0),
    CONSTRAINT chk_tareas_equipo_un_registro CHECK (
        (lead_id IS NOT NULL)::int + (cotizacion_id IS NOT NULL)::int + (proyecto_id IS NOT NULL)::int <= 1),
    CONSTRAINT chk_tareas_equipo_hecha CHECK (
        (estado = 'HECHA') = (completada_en IS NOT NULL AND completada_por IS NOT NULL)),
    -- Las automáticas son de todos.
    CONSTRAINT chk_tareas_equipo_auto_publica CHECK (origen = 'MANUAL' OR NOT privada)
);

CREATE INDEX idx_tareas_equipo_estado ON tareas_equipo(estado);
CREATE INDEX idx_tareas_equipo_creada_por ON tareas_equipo(creada_por);
CREATE INDEX idx_tareas_equipo_cotizacion ON tareas_equipo(cotizacion_id);
