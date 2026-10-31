-- 1. Soporte de baja lógica (asegurar columnas activas)
ALTER TABLE proveedores ADD COLUMN IF NOT EXISTS activo BOOLEAN DEFAULT true;
ALTER TABLE catalogo_productos ADD COLUMN IF NOT EXISTS activo BOOLEAN DEFAULT true;

-- 2. Insertar usuarios de prueba para todos los roles con UUIDs deterministas (Contraseña: 123456)
-- Hash BCrypt: $2a$12$xUUOWV0G8mPfgvmMaGqpieELyv7pZm4qoLkfyKzYucCDYoicUuU3G
INSERT INTO usuarios (id, nombre, rol, telefono, password_hash, activo) VALUES
('a0000000-0000-0000-0000-000000000002', 'Carlos Electro', 'JEFE_ELECTROMECANICO', '77777777', '$2a$12$xUUOWV0G8mPfgvmMaGqpieELyv7pZm4qoLkfyKzYucCDYoicUuU3G', true),
('a0000000-0000-0000-0000-000000000003', 'Laura Industrial', 'INGENIERO_INDUSTRIAL', '66666666', '$2a$12$xUUOWV0G8mPfgvmMaGqpieELyv7pZm4qoLkfyKzYucCDYoicUuU3G', true),
('a0000000-0000-0000-0000-000000000004', 'Pedro Instalador', 'INSTALADOR', '55555555', '$2a$12$xUUOWV0G8mPfgvmMaGqpieELyv7pZm4qoLkfyKzYucCDYoicUuU3G', true),
('a0000000-0000-0000-0000-000000000005', 'Ana Secretaria', 'SECRETARIA', '44444444', '$2a$12$xUUOWV0G8mPfgvmMaGqpieELyv7pZm4qoLkfyKzYucCDYoicUuU3G', true),
('a0000000-0000-0000-0000-000000000006', 'Mario Marketing', 'MARKETING', '33333333', '$2a$12$xUUOWV0G8mPfgvmMaGqpieELyv7pZm4qoLkfyKzYucCDYoicUuU3G', true)
ON CONFLICT (id) DO NOTHING;

-- 3. Semilla inicial de Proveedores de prueba con UUIDs deterministas
INSERT INTO proveedores (id, nombre, categorias_asociadas, contacto, activo) VALUES
('b0000000-0000-0000-0000-000000000001', 'Seguridad Total S.A.', 'seguridad, control_acceso', '+591 70011223', true),
('b0000000-0000-0000-0000-000000000002', 'Iluminación Inteligente SRL', 'iluminacion, audio_automatizacion', '+591 70044556', true)
ON CONFLICT (id) DO NOTHING;

-- 4. Semilla inicial de Catálogo de Productos de prueba con UUIDs deterministas
INSERT INTO catalogo_productos (id, nombre, categoria, especificacion, activo) VALUES
('c0000000-0000-0000-0000-000000000001', 'Cámara Domo IP 4K', 'SEGURIDAD', 'Cámara domo antivandálica con visión nocturna y sensor de movimiento', true),
('c0000000-0000-0000-0000-000000000002', 'Interruptor Smart WiFi 2 Canales', 'ILUMINACION', 'Interruptor táctil empotrable compatible con protocolo Tuya', true),
('c0000000-0000-0000-0000-000000000003', 'Cerradura Biométrica Digital', 'CONTROL_ACCESO', 'Cerradura electrónica con lector de huellas y clave numérica', true)
ON CONFLICT (id) DO NOTHING;
