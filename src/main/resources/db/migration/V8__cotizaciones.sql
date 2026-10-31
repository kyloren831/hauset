-- ============================================================================
-- V8__cotizaciones.sql
-- Flujo comercial: clientes y leads, informe técnico, cotización con fotografía
-- de precio/margen/IVA, historial de precios inmutable y paso a proyecto.
-- Las tablas afectadas estaban vacías al escribir esta migración.
-- ============================================================================

-- 1. Parámetros del sistema (clave/valor), con autor del último cambio
CREATE TABLE IF NOT EXISTS parametros_sistema (
    clave VARCHAR(80) PRIMARY KEY,
    valor VARCHAR(255) NOT NULL,
    actualizado_por UUID REFERENCES usuarios(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO parametros_sistema (clave, valor) VALUES ('iva_porcentaje_defecto', '13.00')
ON CONFLICT (clave) DO NOTHING;

-- 2. Clientes: sin contraseña (no inician sesión) y con baja lógica
ALTER TABLE clientes DROP COLUMN IF EXISTS password_hash;
ALTER TABLE clientes ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE clientes ALTER COLUMN nombre SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_clientes_nombre ON clientes (lower(nombre));

-- 3. Leads
ALTER TABLE leads ADD COLUMN IF NOT EXISTS notas TEXT;
ALTER TABLE leads ALTER COLUMN cliente_id SET NOT NULL;
ALTER TABLE leads ALTER COLUMN origen SET DEFAULT 'WHATSAPP';
ALTER TABLE leads ALTER COLUMN fecha SET DEFAULT now();
ALTER TABLE leads ALTER COLUMN estado SET DEFAULT 'NUEVO';
UPDATE leads SET estado = 'NUEVO' WHERE estado IS NULL;
ALTER TABLE leads ALTER COLUMN estado SET NOT NULL;
ALTER TABLE leads ADD CONSTRAINT chk_leads_estado CHECK (estado IN ('NUEVO', 'COTIZADO', 'DESCARTADO'));

-- 4. Informe técnico (sin precios)
ALTER TABLE informes_tecnicos ALTER COLUMN lead_id SET NOT NULL;
ALTER TABLE informes_tecnicos ALTER COLUMN ingeniero_id SET NOT NULL;
ALTER TABLE informes_tecnicos ALTER COLUMN fecha SET DEFAULT now();
ALTER TABLE informe_tecnico_items ALTER COLUMN informe_id SET NOT NULL;
ALTER TABLE informe_tecnico_items ALTER COLUMN producto_id SET NOT NULL;
ALTER TABLE informe_tecnico_items ALTER COLUMN cantidad SET NOT NULL;
ALTER TABLE informe_tecnico_items ADD COLUMN IF NOT EXISTS orden INTEGER NOT NULL DEFAULT 0;
ALTER TABLE informe_tecnico_items ADD CONSTRAINT chk_informe_items_cantidad CHECK (cantidad > 0);

-- 5. Cotizaciones: estado borrador, número legible, IVA como fotografía y totales con/sin IVA
CREATE SEQUENCE IF NOT EXISTS cotizacion_numero_seq START 1;
ALTER TABLE cotizaciones DROP COLUMN IF EXISTS total;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS numero VARCHAR(30);
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS iva_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 13.00;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS iva_definido_por UUID REFERENCES usuarios(id);
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS total_sin_iva DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS monto_iva DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS total_con_iva DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS margen_definido_en TIMESTAMPTZ;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS validez_dias INTEGER NOT NULL DEFAULT 15;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS notas TEXT;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS fecha_envio TIMESTAMPTZ;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS fecha_respuesta TIMESTAMPTZ;
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS motivo_rechazo TEXT;
ALTER TABLE cotizaciones ALTER COLUMN fecha SET DEFAULT now();
ALTER TABLE cotizaciones ALTER COLUMN estado SET DEFAULT 'BORRADOR';
UPDATE cotizaciones SET estado = upper(estado) WHERE estado IS NOT NULL;
UPDATE cotizaciones SET estado = 'BORRADOR' WHERE estado IS NULL;
ALTER TABLE cotizaciones ALTER COLUMN estado SET NOT NULL;
ALTER TABLE cotizaciones ALTER COLUMN cliente_id SET NOT NULL;
ALTER TABLE cotizaciones ALTER COLUMN armada_por SET NOT NULL;
ALTER TABLE cotizaciones ADD CONSTRAINT uq_cotizaciones_numero UNIQUE (numero);
ALTER TABLE cotizaciones ADD CONSTRAINT chk_cotizaciones_estado
    CHECK (estado IN ('BORRADOR', 'ENVIADA', 'ACEPTADA', 'RECHAZADA'));
ALTER TABLE cotizaciones ADD CONSTRAINT chk_cotizaciones_iva CHECK (iva_porcentaje BETWEEN 0 AND 100);
ALTER TABLE cotizaciones ADD CONSTRAINT chk_cotizaciones_validez CHECK (validez_dias > 0);
-- Una cotización que salió de borrador ya tiene margen definido por un jefe (RF-COT-05)
ALTER TABLE cotizaciones ADD CONSTRAINT chk_cotizaciones_margen_al_enviar
    CHECK (estado = 'BORRADOR' OR (margen_definido_por IS NOT NULL AND numero IS NOT NULL));
CREATE INDEX IF NOT EXISTS idx_cotizaciones_estado ON cotizaciones(estado);

-- 6. Ítems de cotización: fotografía completa del momento (RF-COT-04, RN-01)
ALTER TABLE cotizacion_items ADD COLUMN IF NOT EXISTS producto_nombre VARCHAR(255);
ALTER TABLE cotizacion_items ADD COLUMN IF NOT EXISTS orden INTEGER NOT NULL DEFAULT 0;
ALTER TABLE cotizacion_items ADD COLUMN IF NOT EXISTS monto_iva DECIMAL(12,2);
ALTER TABLE cotizacion_items ADD COLUMN IF NOT EXISTS subtotal_con_iva DECIMAL(12,2);
ALTER TABLE cotizacion_items ALTER COLUMN cotizacion_id SET NOT NULL;
ALTER TABLE cotizacion_items ALTER COLUMN producto_id SET NOT NULL;
ALTER TABLE cotizacion_items ALTER COLUMN cantidad SET NOT NULL;
-- En borrador el proveedor, el neto y el margen pueden faltar; se exigen al enviar.
ALTER TABLE cotizacion_items ADD CONSTRAINT chk_cot_items_cantidad CHECK (cantidad > 0);
ALTER TABLE cotizacion_items ADD CONSTRAINT chk_cot_items_neto CHECK (precio_neto IS NULL OR precio_neto > 0);
ALTER TABLE cotizacion_items ADD CONSTRAINT chk_cot_items_margen CHECK (margen_porcentaje IS NULL OR margen_porcentaje >= 0);

-- 7. Extras: estados en mayúscula e IVA, igual que la cotización (se completan en el módulo de proyectos)
ALTER TABLE cotizacion_extras ALTER COLUMN estado SET DEFAULT 'PENDIENTE';
UPDATE cotizacion_extras SET estado = upper(estado) WHERE estado IS NOT NULL;
ALTER TABLE cotizacion_extras ADD COLUMN IF NOT EXISTS iva_porcentaje DECIMAL(5,2);
ALTER TABLE cotizacion_extras ADD COLUMN IF NOT EXISTS monto_iva DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE cotizacion_extras ADD COLUMN IF NOT EXISTS total_con_iva DECIMAL(12,2) NOT NULL DEFAULT 0;

-- 8. Historial de precios: origen en la cotización y solo inserción (RF-CAT-03/04)
ALTER TABLE precios_historial ADD COLUMN IF NOT EXISTS cotizacion_item_id UUID REFERENCES cotizacion_items(id);
ALTER TABLE precios_historial ALTER COLUMN fecha SET DEFAULT now();
ALTER TABLE precios_historial ADD CONSTRAINT chk_precios_historial_neto CHECK (precio_neto > 0);

CREATE OR REPLACE FUNCTION fn_solo_insercion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La tabla % es de solo inserción: no se puede % un registro', TG_TABLE_NAME, lower(TG_OP);
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_precios_historial_inmutable ON precios_historial;
CREATE TRIGGER trg_precios_historial_inmutable
    BEFORE UPDATE OR DELETE ON precios_historial
    FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();

-- 9. Contenido de cada versión de instrucciones (inmutable una vez creada)
CREATE TABLE IF NOT EXISTS instruccion_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instruccion_version_id UUID NOT NULL REFERENCES instrucciones_version(id),
    orden INTEGER NOT NULL DEFAULT 0,
    producto_id UUID REFERENCES catalogo_productos(id),
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    descripcion VARCHAR(300) NOT NULL,
    origen_item_id UUID REFERENCES instruccion_items(id),
    accion VARCHAR(20) NOT NULL DEFAULT 'AGREGADO'
        CHECK (accion IN ('AGREGADO', 'MODIFICADO', 'QUITADO', 'SIN_CAMBIO')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_instruccion_items_version ON instruccion_items(instruccion_version_id);

DROP TRIGGER IF EXISTS trg_instruccion_items_inmutable ON instruccion_items;
CREATE TRIGGER trg_instruccion_items_inmutable
    BEFORE UPDATE OR DELETE ON instruccion_items
    FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();
