CREATE TABLE refresh_tokens (
    id SERIAL PRIMARY KEY,
    usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
    token VARCHAR NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL
);
