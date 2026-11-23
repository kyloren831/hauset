-- Las cuentas de prueba de V3 y V4 nacen activas con una clave pública (123456) en toda base nueva,
-- incluida producción. Se desactivan las que todavía conservan ese hash sembrado; las que ya
-- cambiaron de clave no se tocan. El primer jefe real se crea con HAUSET_PRIMER_JEFE_* al arrancar.
UPDATE usuarios SET activo = false
WHERE password_hash = '$2a$12$xUUOWV0G8mPfgvmMaGqpieELyv7pZm4qoLkfyKzYucCDYoicUuU3G';

DELETE FROM refresh_tokens
WHERE usuario_id IN (SELECT id FROM usuarios
                     WHERE password_hash = '$2a$12$xUUOWV0G8mPfgvmMaGqpieELyv7pZm4qoLkfyKzYucCDYoicUuU3G');

-- El PDF enviado de una cotización se sirve por id: un archivo subido después no lo reemplaza.
ALTER TABLE cotizaciones ADD COLUMN pdf_archivo_id UUID REFERENCES archivos_adjuntos(id);

UPDATE cotizaciones c SET pdf_archivo_id = (
    SELECT a.id FROM archivos_adjuntos a
    WHERE a.entidad_tipo = 'COTIZACION' AND a.entidad_id = c.id AND a.categoria = 'DOCUMENTO_PDF'
      AND a.deleted_at IS NULL
    ORDER BY a.created_at DESC
    LIMIT 1);
