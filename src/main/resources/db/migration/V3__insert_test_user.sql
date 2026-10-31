INSERT INTO usuarios (id, nombre, rol, telefono, password_hash, activo)
VALUES ('a0000000-0000-0000-0000-000000000001', 'Administrador Prueba', 'JEFE_SISTEMAS', '88888888', '$2a$12$xUUOWV0G8mPfgvmMaGqpieELyv7pZm4qoLkfyKzYucCDYoicUuU3G', true)
ON CONFLICT (id) DO NOTHING;
