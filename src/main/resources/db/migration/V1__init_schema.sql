CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE usuarios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR,
    rol VARCHAR,
    telefono VARCHAR,
    password_hash VARCHAR,
    activo BOOLEAN DEFAULT true
);

CREATE TABLE clientes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR,
    telefono VARCHAR,
    password_hash VARCHAR,
    direccion VARCHAR,
    notas TEXT
);

CREATE TABLE leads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id UUID REFERENCES clientes(id),
    origen VARCHAR DEFAULT 'whatsapp',
    fecha TIMESTAMP,
    estado VARCHAR
);

CREATE TABLE proveedores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR,
    categorias_asociadas VARCHAR,
    contacto VARCHAR,
    activo BOOLEAN DEFAULT true
);

CREATE TABLE catalogo_productos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR,
    categoria VARCHAR,
    especificacion TEXT,
    activo BOOLEAN DEFAULT true
);

CREATE TABLE precios_historial (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    producto_id UUID REFERENCES catalogo_productos(id),
    proveedor_id UUID REFERENCES proveedores(id),
    precio_neto DECIMAL,
    disponible BOOLEAN,
    registrado_por UUID REFERENCES usuarios(id),
    fecha TIMESTAMP
);

CREATE TABLE informes_tecnicos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id UUID REFERENCES leads(id),
    ingeniero_id UUID REFERENCES usuarios(id),
    notas TEXT,
    fecha TIMESTAMP
);

CREATE TABLE informe_tecnico_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    informe_id UUID REFERENCES informes_tecnicos(id),
    producto_id UUID REFERENCES catalogo_productos(id),
    cantidad INTEGER
);

CREATE TABLE cotizaciones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    informe_id UUID REFERENCES informes_tecnicos(id),
    cliente_id UUID REFERENCES clientes(id),
    armada_por UUID REFERENCES usuarios(id),
    margen_definido_por UUID REFERENCES usuarios(id),
    fecha TIMESTAMP,
    estado VARCHAR,
    total DECIMAL,
    pdf_url VARCHAR,
    version BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE cotizacion_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cotizacion_id UUID REFERENCES cotizaciones(id),
    producto_id UUID REFERENCES catalogo_productos(id),
    proveedor_id UUID REFERENCES proveedores(id),
    cantidad INTEGER,
    precio_neto DECIMAL,
    margen_porcentaje DECIMAL,
    precio_venta DECIMAL,
    subtotal DECIMAL
);

CREATE TABLE cotizacion_extras (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cotizacion_id UUID REFERENCES cotizaciones(id),
    motivo TEXT,
    estado VARCHAR,
    confirmado_por UUID REFERENCES usuarios(id),
    fecha TIMESTAMP,
    total_extra DECIMAL,
    version BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE cotizacion_extra_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    extra_id UUID REFERENCES cotizacion_extras(id),
    producto_id UUID REFERENCES catalogo_productos(id),
    proveedor_id UUID REFERENCES proveedores(id),
    cantidad INTEGER,
    precio_neto DECIMAL,
    margen_porcentaje DECIMAL,
    precio_venta DECIMAL,
    subtotal DECIMAL
);
