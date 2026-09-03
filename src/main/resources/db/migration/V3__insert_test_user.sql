-- Inserción de un usuario de prueba para poder validar el login (Contraseña: 123456)
-- Generado usando BCrypt.
INSERT INTO usuarios (nombre, rol, telefono, password_hash, activo) 
VALUES ('Administrador Prueba', 'jefe_sistemas', '88888888', '$2a$10$B.4nZqYk2vL8T/oH.cR38.h/5rQ3C4q5iY6eU7oT6nF5aD5v2x3.a', true);
