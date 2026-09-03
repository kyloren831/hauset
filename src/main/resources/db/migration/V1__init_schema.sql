CREATE TABLE usuarios (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR,
    rol VARCHAR,
    telefono VARCHAR,
    password_hash VARCHAR,
    activo BOOLEAN DEFAULT true
);

CREATE TABLE clientes (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR,
    telefono VARCHAR,
    password_hash VARCHAR,
    direccion VARCHAR,
    notas TEXT
);

CREATE TABLE leads (
    id SERIAL PRIMARY KEY,
    cliente_id INTEGER REFERENCES clientes(id),
    origen VARCHAR DEFAULT 'whatsapp',
    fecha TIMESTAMP,
    estado VARCHAR
);

CREATE TABLE proveedores (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR,
    categorias_asociadas VARCHAR,
    contacto VARCHAR
);

CREATE TABLE catalogo_productos (
    id SERIAL PRIMARY KEY,
    nombre VARCHAR,
    categoria VARCHAR,
    especificacion TEXT
);

CREATE TABLE precios_historial (
    id SERIAL PRIMARY KEY,
    producto_id INTEGER REFERENCES catalogo_productos(id),
    proveedor_id INTEGER REFERENCES proveedores(id),
    precio_neto DECIMAL,
    disponible BOOLEAN,
    registrado_por INTEGER REFERENCES usuarios(id),
    fecha TIMESTAMP
);

CREATE TABLE informes_tecnicos (
    id SERIAL PRIMARY KEY,
    lead_id INTEGER REFERENCES leads(id),
    ingeniero_id INTEGER REFERENCES usuarios(id),
    notas TEXT,
    fecha TIMESTAMP
);

CREATE TABLE informe_tecnico_items (
    id SERIAL PRIMARY KEY,
    informe_id INTEGER REFERENCES informes_tecnicos(id),
    producto_id INTEGER REFERENCES catalogo_productos(id),
    cantidad INTEGER
);

CREATE TABLE cotizaciones (
    id SERIAL PRIMARY KEY,
    informe_id INTEGER REFERENCES informes_tecnicos(id),
    cliente_id INTEGER REFERENCES clientes(id),
    armada_por INTEGER REFERENCES usuarios(id),
    margen_definido_por INTEGER REFERENCES usuarios(id),
    fecha TIMESTAMP,
    estado VARCHAR,
    total DECIMAL,
    pdf_url VARCHAR
);

CREATE TABLE cotizacion_items (
    id SERIAL PRIMARY KEY,
    cotizacion_id INTEGER REFERENCES cotizaciones(id),
    producto_id INTEGER REFERENCES catalogo_productos(id),
    proveedor_id INTEGER REFERENCES proveedores(id),
    cantidad INTEGER,
    precio_neto DECIMAL,
    margen_porcentaje DECIMAL,
    precio_venta DECIMAL,
    subtotal DECIMAL
);

CREATE TABLE cotizacion_extras (
    id SERIAL PRIMARY KEY,
    cotizacion_id INTEGER REFERENCES cotizaciones(id),
    motivo TEXT,
    estado VARCHAR,
    confirmado_por INTEGER REFERENCES usuarios(id),
    fecha TIMESTAMP,
    total_extra DECIMAL
);

CREATE TABLE cotizacion_extra_items (
    id SERIAL PRIMARY KEY,
    extra_id INTEGER REFERENCES cotizacion_extras(id),
    producto_id INTEGER REFERENCES catalogo_productos(id),
    proveedor_id INTEGER REFERENCES proveedores(id),
    cantidad INTEGER,
    precio_neto DECIMAL,
    margen_porcentaje DECIMAL,
    precio_venta DECIMAL,
    subtotal DECIMAL
);
