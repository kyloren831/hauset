-- ============================================================================
-- V5__multimedia_and_schema_fixes.sql
-- Hauset: Tabla maestra de multimedia (Opción B) y normalización del schema
-- ============================================================================

-- 1. Tabla Maestra Centralizada de Archivos y Evidencias Fotográficas (Opción B)
CREATE TABLE IF NOT EXISTS archivos_adjuntos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entidad_tipo VARCHAR(50) NOT NULL, 
        -- 'INFORME_TECNICO', 'CHECKLIST_TAREA', 'CAMBIO_INSTRUCCION', 
        -- 'EQUIPO_INSTALADO', 'CATALOGO_PRODUCTO', 'USUARIO', 
        -- 'FIRMA_CONFORMIDAD', 'COTIZACION', 'REPORTE_ENTREGA'
    entidad_id UUID NOT NULL,
    categoria VARCHAR(50) NOT NULL,
        -- 'EVIDENCIA_ANTES', 'EVIDENCIA_DESPUES', 'PLANO_CROQUIS',
        -- 'FOTO_PLACA_MAC', 'FOTO_INSTALACION', 'AVATAR',
        -- 'FIRMA_CLIENTE', 'FICHA_TECNICA', 'DOCUMENTO_PDF'
    storage_provider VARCHAR(30) NOT NULL DEFAULT 'DISCO_LOCAL', -- 'DISCO_LOCAL'
    storage_key VARCHAR(500) NOT NULL UNIQUE, -- ruta relativa en el disco simulado (ej. 'tareas/2026/09/uuid.webp')
    nombre_original VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL, -- image/webp, image/jpeg, application/pdf
    tamano_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL, -- Idempotencia y control de duplicados
    ancho_px INTEGER,
    alto_px INTEGER,
    thumbnail_storage_key VARCHAR(500), -- Miniatura generada asíncronamente
    estado VARCHAR(30) NOT NULL DEFAULT 'DISPONIBLE', -- 'PENDIENTE_SUBIDA', 'DISPONIBLE', 'ERROR'
    notas TEXT,
    subido_por UUID REFERENCES usuarios(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ -- Soft delete para purga asíncrona física
);

CREATE INDEX IF NOT EXISTS idx_archivos_entidad ON archivos_adjuntos(entidad_tipo, entidad_id, categoria) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_archivos_checksum ON archivos_adjuntos(checksum_sha256);
CREATE INDEX IF NOT EXISTS idx_archivos_estado ON archivos_adjuntos(estado) WHERE estado != 'DISPONIBLE';
CREATE INDEX IF NOT EXISTS idx_archivos_subido_por ON archivos_adjuntos(subido_por);

-- 2. Correcciones de Tipos y Precisión sobre tablas creadas en V1
ALTER TABLE precios_historial 
    ALTER COLUMN precio_neto TYPE DECIMAL(12, 2),
    ALTER COLUMN fecha TYPE TIMESTAMPTZ;

ALTER TABLE cotizaciones 
    ALTER COLUMN total TYPE DECIMAL(12, 2),
    ALTER COLUMN fecha TYPE TIMESTAMPTZ;

ALTER TABLE cotizacion_items 
    ALTER COLUMN precio_neto TYPE DECIMAL(12, 2),
    ALTER COLUMN margen_porcentaje TYPE DECIMAL(5, 2),
    ALTER COLUMN precio_venta TYPE DECIMAL(12, 2),
    ALTER COLUMN subtotal TYPE DECIMAL(12, 2);

ALTER TABLE cotizacion_extras 
    ALTER COLUMN total_extra TYPE DECIMAL(12, 2),
    ALTER COLUMN fecha TYPE TIMESTAMPTZ;

ALTER TABLE cotizacion_extra_items 
    ALTER COLUMN precio_neto TYPE DECIMAL(12, 2),
    ALTER COLUMN margen_porcentaje TYPE DECIMAL(5, 2),
    ALTER COLUMN precio_venta TYPE DECIMAL(12, 2),
    ALTER COLUMN subtotal TYPE DECIMAL(12, 2);

ALTER TABLE leads 
    ALTER COLUMN fecha TYPE TIMESTAMPTZ;

ALTER TABLE informes_tecnicos 
    ALTER COLUMN fecha TYPE TIMESTAMPTZ;

-- Eliminar columna redundante de PDF en cotizaciones (se gestiona vía archivos_adjuntos)
ALTER TABLE cotizaciones DROP COLUMN IF EXISTS pdf_url;

-- 3. Auditoría uniforme (created_at, updated_at) en tablas existentes
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE clientes ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE clientes ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE proveedores ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE proveedores ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE catalogo_productos ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE catalogo_productos ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE leads ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE leads ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE informes_tecnicos ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE informes_tecnicos ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE cotizaciones ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE cotizacion_extras ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE cotizacion_extras ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- 4. Índices en Foreign Keys para optimización de Joins
CREATE INDEX IF NOT EXISTS idx_leads_cliente_id ON leads(cliente_id);
CREATE INDEX IF NOT EXISTS idx_precios_hist_producto ON precios_historial(producto_id);
CREATE INDEX IF NOT EXISTS idx_precios_hist_proveedor ON precios_historial(proveedor_id);
CREATE INDEX IF NOT EXISTS idx_informes_tecnicos_lead ON informes_tecnicos(lead_id);
CREATE INDEX IF NOT EXISTS idx_informes_tecnicos_ing ON informes_tecnicos(ingeniero_id);
CREATE INDEX IF NOT EXISTS idx_informe_items_informe ON informe_tecnico_items(informe_id);
CREATE INDEX IF NOT EXISTS idx_informe_items_prod ON informe_tecnico_items(producto_id);
CREATE INDEX IF NOT EXISTS idx_cotizaciones_informe ON cotizaciones(informe_id);
CREATE INDEX IF NOT EXISTS idx_cotizaciones_cliente ON cotizaciones(cliente_id);
CREATE INDEX IF NOT EXISTS idx_cotizacion_items_cot ON cotizacion_items(cotizacion_id);
CREATE INDEX IF NOT EXISTS idx_cotizacion_items_prod ON cotizacion_items(producto_id);
CREATE INDEX IF NOT EXISTS idx_cotizacion_extras_cot ON cotizacion_extras(cotizacion_id);
CREATE INDEX IF NOT EXISTS idx_cotizacion_extra_items ON cotizacion_extra_items(extra_id);
