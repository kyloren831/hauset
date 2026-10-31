-- ============================================================================
-- V13__idempotencia.sql
-- Cada operación que modifica datos puede traer la cabecera Idempotency-Key.
-- La primera vez se ejecuta y se guarda la respuesta; si la misma operación
-- llega otra vez (reintento por mala señal), se devuelve la respuesta guardada
-- sin repetirla. Las claves se borran a las 48 h.
-- ============================================================================
CREATE TABLE solicitudes_idempotentes (
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    clave VARCHAR(100) NOT NULL,
    -- Método y ruta: la misma clave no puede reutilizarse para otra operación.
    huella VARCHAR(300) NOT NULL,
    estado VARCHAR(12) NOT NULL CHECK (estado IN ('EN_PROCESO', 'COMPLETADA')),
    http_status INTEGER,
    content_type VARCHAR(150),
    cuerpo BYTEA,
    creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, clave)
);
CREATE INDEX idx_solicitudes_idempotentes_fecha ON solicitudes_idempotentes(creada_en);
