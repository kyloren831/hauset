-- Fase 8: equipos instalados, credenciales cifradas y recordatorios de mantenimiento (RF-MAN-01..09).

-- 1. Fecha de cierre del proyecto: de ahí sale la fecha de instalación y el recordatorio a 6 meses (RN-08).
ALTER TABLE proyectos ADD COLUMN fecha_cierre DATE;
UPDATE proyectos p
SET fecha_cierre = (SELECT (max(f.fecha) AT TIME ZONE 'America/Costa_Rica')::date
                    FROM firmas_conformidad f JOIN visitas v ON v.id = f.visita_id
                    WHERE v.proyecto_id = p.id)
WHERE p.estado = 'CERRADO';
UPDATE proyectos SET fecha_cierre = (updated_at AT TIME ZONE 'America/Costa_Rica')::date
WHERE estado = 'CERRADO' AND fecha_cierre IS NULL;

-- 2. Equipos (E14): la fecha de instalación queda vacía hasta el cierre (RF-MAN-02); puede ser un equipo
--    que no viene del catálogo (por ejemplo el router del cliente); baja lógica para no perder el historial.
ALTER TABLE equipos_instalados
    ALTER COLUMN fecha_instalacion DROP DEFAULT,
    ALTER COLUMN fecha_instalacion DROP NOT NULL,
    ALTER COLUMN producto_id DROP NOT NULL,
    ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN registrado_por UUID REFERENCES usuarios(id),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
UPDATE equipos_instalados e SET fecha_instalacion = NULL
FROM proyectos p WHERE p.id = e.proyecto_id AND p.estado <> 'CERRADO';
UPDATE equipos_instalados e SET fecha_instalacion = p.fecha_cierre
FROM proyectos p WHERE p.id = e.proyecto_id AND p.estado = 'CERRADO';
-- La MAC se guarda normalizada (AA:BB:CC:DD:EE:FF); la serie es única por producto, no global.
CREATE UNIQUE INDEX ux_equipos_mac ON equipos_instalados (direccion_mac)
    WHERE activo AND direccion_mac IS NOT NULL;
CREATE UNIQUE INDEX ux_equipos_serie ON equipos_instalados (producto_id, upper(numero_serie))
    WHERE activo AND numero_serie IS NOT NULL AND producto_id IS NOT NULL;

-- 3. Credenciales (E13): nunca se borran (la bitácora las referencia), se dan de baja.
ALTER TABLE credenciales_equipo ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE credenciales_equipo DROP CONSTRAINT credenciales_equipo_equipo_id_fkey;
ALTER TABLE credenciales_equipo ADD CONSTRAINT credenciales_equipo_equipo_id_fkey
    FOREIGN KEY (equipo_id) REFERENCES equipos_instalados(id) ON DELETE RESTRICT;
ALTER TABLE credenciales_equipo ADD CONSTRAINT ck_credencial_tipo
    CHECK (tipo IN ('WIFI', 'APP', 'PANEL_ADMIN', 'OTRO')) NOT VALID;
-- AES-256-GCM: "v1:" + Base64(nonce ‖ cifrado). NOT VALID: no revisa filas viejas, sí toda escritura nueva.
ALTER TABLE credenciales_equipo ADD CONSTRAINT ck_credencial_cifrada
    CHECK (contrasena_cifrada LIKE 'v1:%') NOT VALID;

-- 4. Bitácora de consultas (RF-MAN-06): no se edita ni se borra, ni en cascada.
ALTER TABLE credenciales_accesos_log DROP CONSTRAINT credenciales_accesos_log_credencial_id_fkey;
ALTER TABLE credenciales_accesos_log ADD CONSTRAINT credenciales_accesos_log_credencial_id_fkey
    FOREIGN KEY (credencial_id) REFERENCES credenciales_equipo(id) ON DELETE RESTRICT;
CREATE TRIGGER trg_credenciales_accesos_inmutable BEFORE UPDATE OR DELETE ON credenciales_accesos_log
    FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();

-- 5. Recordatorios (E15): uno por proyecto y fecha, la tarea diaria inserta con ON CONFLICT DO NOTHING.
ALTER TABLE mantenimiento_recordatorios
    ADD COLUMN nota TEXT,
    ADD CONSTRAINT ux_recordatorio_proyecto_fecha UNIQUE (proyecto_id, fecha_programada),
    ADD CONSTRAINT ck_recordatorio_estado CHECK (estado IN ('PENDIENTE', 'CONTACTADO', 'OMITIDO'));
CREATE INDEX idx_mantenimiento_estado_fecha ON mantenimiento_recordatorios (estado, fecha_programada);
