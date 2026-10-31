-- ============================================================================
-- V10__catalogo_estados.sql
-- Productos y proveedores pasan de "activo sí/no" a tres estados:
--   ACTIVO    se usa en informes y cotizaciones nuevas.
--   INACTIVO  visible y editable, pero no se ofrece para informes ni cotizaciones nuevas.
--   ARCHIVADO oculto por defecto y de solo lectura; se puede restaurar.
-- Las fotos de producto van en archivos_adjuntos (entidad PRODUCTO_CATALOGO, categoría FOTO_PRODUCTO).
-- ============================================================================

ALTER TABLE catalogo_productos ADD COLUMN estado VARCHAR(12) NOT NULL DEFAULT 'ACTIVO';
UPDATE catalogo_productos SET estado = CASE WHEN activo IS FALSE THEN 'INACTIVO' ELSE 'ACTIVO' END;
ALTER TABLE catalogo_productos DROP COLUMN activo;
ALTER TABLE catalogo_productos ADD CONSTRAINT chk_productos_estado
    CHECK (estado IN ('ACTIVO', 'INACTIVO', 'ARCHIVADO'));
CREATE INDEX idx_productos_estado ON catalogo_productos(estado, lower(nombre));

ALTER TABLE proveedores ADD COLUMN estado VARCHAR(12) NOT NULL DEFAULT 'ACTIVO';
UPDATE proveedores SET estado = CASE WHEN activo IS FALSE THEN 'INACTIVO' ELSE 'ACTIVO' END;
ALTER TABLE proveedores DROP COLUMN activo;
ALTER TABLE proveedores ADD CONSTRAINT chk_proveedores_estado
    CHECK (estado IN ('ACTIVO', 'INACTIVO', 'ARCHIVADO'));
CREATE INDEX idx_proveedores_estado ON proveedores(estado, lower(nombre));
