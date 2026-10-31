-- ============================================================================
-- V17__cambios_y_extras.sql
-- Fase 6: cambios de instrucción en sitio y extras de materiales
-- (RF-PRY-03..08, RF-COT-10/11, RN-05, RN-06).
--
-- Un cambio propuesto lleva líneas (agregar, cambiar cantidad, quitar) sobre el
-- checklist vigente. Al confirmarlo un jefe se crea la versión N+1 de las
-- instrucciones y, si hace falta material, un extra pendiente ligado a la
-- cotización. El extra suma al total facturado solo cuando un jefe lo confirma.
-- ============================================================================

-- 1. Cambios de instrucción: estados válidos y datos de la resolución
ALTER TABLE cambios_instruccion ADD COLUMN motivo_rechazo TEXT;
ALTER TABLE cambios_instruccion ADD CONSTRAINT chk_cambios_estado
    CHECK (estado IN ('PENDIENTE_CONFIRMACION', 'CONFIRMADO', 'RECHAZADO'));
ALTER TABLE cambios_instruccion ADD CONSTRAINT chk_cambios_resuelto
    CHECK ((estado = 'PENDIENTE_CONFIRMACION') = (confirmado_por IS NULL AND fecha_confirmacion IS NULL));
ALTER TABLE cambios_instruccion ADD CONSTRAINT chk_cambios_version_nueva
    CHECK ((estado = 'CONFIRMADO') = (instruccion_nueva_id IS NOT NULL));
ALTER TABLE cambios_instruccion ADD CONSTRAINT chk_cambios_rechazo
    CHECK (estado <> 'RECHAZADO' OR motivo_rechazo IS NOT NULL);
ALTER TABLE cambios_instruccion ADD CONSTRAINT chk_cambios_descripcion
    CHECK (length(trim(descripcion)) > 0);
CREATE INDEX idx_cambios_instruccion_estado ON cambios_instruccion(estado);

-- Un cambio no se borra, y una vez confirmado o rechazado ya no cambia (RF-PRY-07).
CREATE OR REPLACE FUNCTION fn_cambio_resuelto_inmutable() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Un cambio de instrucciones no se borra';
    END IF;
    IF OLD.estado <> 'PENDIENTE_CONFIRMACION' THEN
        RAISE EXCEPTION 'El cambio de instrucciones ya fue resuelto y no se modifica';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cambio_resuelto_inmutable
    BEFORE UPDATE OR DELETE ON cambios_instruccion
    FOR EACH ROW EXECUTE FUNCTION fn_cambio_resuelto_inmutable();

-- 2. Líneas del cambio: qué se agrega, qué tarea cambia de cantidad y cuál se quita
CREATE TABLE cambio_instruccion_lineas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cambio_id UUID NOT NULL REFERENCES cambios_instruccion(id),
    orden INTEGER NOT NULL DEFAULT 0,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('AGREGAR', 'CAMBIAR_CANTIDAD', 'QUITAR')),
    tarea_id UUID REFERENCES checklist_tareas(id),
    producto_id UUID REFERENCES catalogo_productos(id),
    ubicacion_id UUID REFERENCES informe_tecnico_ubicaciones(id),
    cantidad INTEGER CHECK (cantidad IS NULL OR cantidad > 0),
    descripcion VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_linea_cambio_forma CHECK (
        (tipo = 'AGREGAR' AND tarea_id IS NULL AND cantidad IS NOT NULL
            AND (producto_id IS NOT NULL OR length(trim(coalesce(descripcion, ''))) > 0))
        OR (tipo = 'CAMBIAR_CANTIDAD' AND tarea_id IS NOT NULL AND cantidad IS NOT NULL)
        OR (tipo = 'QUITAR' AND tarea_id IS NOT NULL))
);
CREATE INDEX idx_cambio_lineas_cambio ON cambio_instruccion_lineas(cambio_id);

CREATE TRIGGER trg_cambio_lineas_inmutable
    BEFORE UPDATE OR DELETE ON cambio_instruccion_lineas
    FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();

-- 3. Extras de materiales: número por cotización, origen, autor, margen y descarte
UPDATE cotizacion_extras SET estado = 'PENDIENTE' WHERE estado IS NULL;
UPDATE cotizacion_extras SET total_extra = 0 WHERE total_extra IS NULL;
ALTER TABLE cotizacion_extras
    ALTER COLUMN cotizacion_id SET NOT NULL,
    ALTER COLUMN estado SET NOT NULL,
    ALTER COLUMN estado TYPE VARCHAR(20),
    ALTER COLUMN total_extra SET NOT NULL,
    ALTER COLUMN total_extra SET DEFAULT 0,
    ADD COLUMN numero INTEGER,
    ADD COLUMN origen VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN creado_por UUID REFERENCES usuarios(id),
    ADD COLUMN margen_definido_por UUID REFERENCES usuarios(id),
    ADD COLUMN margen_definido_en TIMESTAMPTZ,
    ADD COLUMN descartado_por UUID REFERENCES usuarios(id),
    ADD COLUMN descartado_en TIMESTAMPTZ,
    ADD COLUMN motivo_descarte TEXT;
UPDATE cotizacion_extras e SET numero = n.fila
FROM (SELECT id, row_number() OVER (PARTITION BY cotizacion_id ORDER BY created_at, id) AS fila
      FROM cotizacion_extras) n
WHERE n.id = e.id;
ALTER TABLE cotizacion_extras ALTER COLUMN numero SET NOT NULL;
ALTER TABLE cotizacion_extras ADD CONSTRAINT uq_cotizacion_extras_numero UNIQUE (cotizacion_id, numero);
ALTER TABLE cotizacion_extras ADD CONSTRAINT chk_extras_estado
    CHECK (estado IN ('PENDIENTE', 'CONFIRMADO', 'DESCARTADO'));
ALTER TABLE cotizacion_extras ADD CONSTRAINT chk_extras_origen
    CHECK (origen IN ('MANUAL', 'CAMBIO_INSTRUCCION'));
ALTER TABLE cotizacion_extras ADD CONSTRAINT chk_extras_confirmado
    CHECK ((estado = 'CONFIRMADO') = (confirmado_por IS NOT NULL AND fecha IS NOT NULL));
ALTER TABLE cotizacion_extras ADD CONSTRAINT chk_extras_descartado
    CHECK ((estado = 'DESCARTADO') = (descartado_por IS NOT NULL AND descartado_en IS NOT NULL
                                      AND motivo_descarte IS NOT NULL));

-- Ítems del extra: misma fotografía que un ítem de cotización (RF-COT-04)
DELETE FROM cotizacion_extra_items WHERE extra_id IS NULL OR producto_id IS NULL OR cantidad IS NULL;
ALTER TABLE cotizacion_extra_items
    ALTER COLUMN extra_id SET NOT NULL,
    ALTER COLUMN producto_id SET NOT NULL,
    ALTER COLUMN cantidad SET NOT NULL,
    ADD COLUMN producto_nombre VARCHAR(255),
    ADD COLUMN orden INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN monto_iva DECIMAL(12,2),
    ADD COLUMN subtotal_con_iva DECIMAL(12,2);
ALTER TABLE cotizacion_extra_items ADD CONSTRAINT chk_extra_items_cantidad CHECK (cantidad > 0);
ALTER TABLE cotizacion_extra_items ADD CONSTRAINT chk_extra_items_neto CHECK (precio_neto IS NULL OR precio_neto > 0);
ALTER TABLE cotizacion_extra_items ADD CONSTRAINT chk_extra_items_margen
    CHECK (margen_porcentaje IS NULL OR margen_porcentaje >= 0);

-- 4. Lista de tareas: avisos automáticos de cambios y extras por resolver (RF-TAR-05)
ALTER TABLE tareas_equipo DROP CONSTRAINT chk_tareas_equipo_origen;
ALTER TABLE tareas_equipo ADD CONSTRAINT chk_tareas_equipo_origen
    CHECK (origen IN ('MANUAL', 'COTIZACION_ACEPTADA', 'CAMBIO_PROPUESTO', 'EXTRA_PENDIENTE'));
-- Id del cambio o del extra que originó la tarea automática, para cerrarla al resolverlo.
ALTER TABLE tareas_equipo ADD COLUMN origen_ref UUID;
CREATE INDEX idx_tareas_equipo_origen_ref ON tareas_equipo(origen_ref);
