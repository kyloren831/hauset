CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    token VARCHAR NOT NULL UNIQUE,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL
);
