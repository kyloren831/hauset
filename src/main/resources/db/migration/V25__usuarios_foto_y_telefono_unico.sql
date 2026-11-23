-- Foto de perfil, ligada por id de archivo: solo el perfil decide cuál es la vigente.
ALTER TABLE usuarios ADD COLUMN foto_id UUID REFERENCES archivos_adjuntos(id);

-- El teléfono es el usuario con el que se inicia sesión: no puede repetirse.
ALTER TABLE usuarios ADD CONSTRAINT uk_usuarios_telefono UNIQUE (telefono);
