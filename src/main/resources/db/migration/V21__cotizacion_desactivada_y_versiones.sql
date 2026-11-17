-- Una cotización aceptada no se recotiza (RF-COT-21, RF-COT-22):
--  * para recotizarla hay que desactivarla (si todavía no tiene proyecto);
--  * una versión nueva de su informe actualiza esa misma cotización y su proyecto, y el material de más
--    queda como extra pendiente.

ALTER TABLE cotizaciones DROP CONSTRAINT chk_cotizaciones_estado;
ALTER TABLE cotizaciones ADD CONSTRAINT chk_cotizaciones_estado
    CHECK (estado IN ('BORRADOR', 'ENVIADA', 'ACEPTADA', 'RECHAZADA', 'DESACTIVADA'));

ALTER TABLE cotizaciones
    ADD COLUMN desactivada_por UUID REFERENCES usuarios (id),
    ADD COLUMN desactivada_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN motivo_desactivacion TEXT;
ALTER TABLE cotizaciones ADD CONSTRAINT chk_cotizaciones_desactivada
    CHECK ((estado = 'DESACTIVADA') = (desactivada_por IS NOT NULL AND desactivada_en IS NOT NULL
        AND motivo_desactivacion IS NOT NULL));

-- Ubicación de la versión anterior de la que sale cada ubicación (para seguirla entre versiones).
ALTER TABLE informe_tecnico_ubicaciones
    ADD COLUMN origen_ubicacion_id UUID REFERENCES informe_tecnico_ubicaciones (id);

-- Versión que fue la de una cotización aceptada hasta que una versión nueva la reemplazó.
ALTER TABLE informes_tecnicos
    ADD COLUMN reemplazada_en_cotizacion_id UUID REFERENCES cotizaciones (id);

ALTER TABLE cotizacion_extras DROP CONSTRAINT chk_extras_origen;
ALTER TABLE cotizacion_extras ADD CONSTRAINT chk_extras_origen
    CHECK (origen IN ('MANUAL', 'CAMBIO_INSTRUCCION', 'INFORME_TECNICO'));
