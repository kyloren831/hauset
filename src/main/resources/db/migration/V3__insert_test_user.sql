-- Inserción de un usuario de prueba para poder validar el login (Contraseña: 123456)
-- Generado usando BCrypt.
INSERT INTO usuarios (nombre, rol, telefono, password_hash, activo) 
VALUES ('Administrador Prueba', 'JEFE_SISTEMAS', '88888888', '$2a$12$xUUOWV0G8mPfgvmMaGqpieELyv7pZm4qoLkfyKzYucCDYoicUuU3G', true);
