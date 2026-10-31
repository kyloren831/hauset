-- ============================================================================
-- V6__field_management_modules.sql
-- Hauset: Módulos de Operación de Campo, Visitas, Equipos y Postventa
-- Totalmente desacoplados de archivos (Opción B: gestionados en archivos_adjuntos)
-- ============================================================================

-- 1. Proyectos (derivados de cotización aceptada)
CREATE TABLE IF NOT EXISTS proyectos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cotizacion_id UUID NOT NULL UNIQUE REFERENCES cotizaciones(id),
    estado VARCHAR(50) NOT NULL DEFAULT 'COTIZADO', -- 'COTIZADO', 'EN_INSTALACION', 'REQUIERE_RETORNO', 'CERRADO'
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_proyectos_cotizacion ON proyectos(cotizacion_id);
CREATE INDEX IF NOT EXISTS idx_proyectos_estado ON proyectos(estado);

-- 2. Versiones de Instrucciones de Trabajo
CREATE TABLE IF NOT EXISTS instrucciones_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id UUID NOT NULL REFERENCES proyectos(id) ON DELETE CASCADE,
    numero_version INTEGER NOT NULL,
    generada_desde VARCHAR(50) NOT NULL, -- 'COTIZACION', 'CAMBIO_SITIO'
    creado_por UUID REFERENCES usuarios(id),
    fecha TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_proyecto_instruccion_version UNIQUE (proyecto_id, numero_version)
);
CREATE INDEX IF NOT EXISTS idx_instrucciones_version_proyecto ON instrucciones_version(proyecto_id);

-- 3. Cambios de Instrucción en Sitio
CREATE TABLE IF NOT EXISTS cambios_instruccion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id UUID NOT NULL REFERENCES proyectos(id),
    instruccion_anterior_id UUID NOT NULL REFERENCES instrucciones_version(id),
    instruccion_nueva_id UUID REFERENCES instrucciones_version(id),
    propuesto_por UUID NOT NULL REFERENCES usuarios(id),
    descripcion TEXT NOT NULL,
    requiere_material BOOLEAN NOT NULL DEFAULT false,
    estado VARCHAR(50) NOT NULL DEFAULT 'PENDIENTE_CONFIRMACION', -- 'PENDIENTE_CONFIRMACION', 'CONFIRMADO', 'RECHAZADO'
    confirmado_por UUID REFERENCES usuarios(id),
    extra_id UUID REFERENCES cotizacion_extras(id),
    fecha_propuesta TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_confirmacion TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cambios_instruccion_proyecto ON cambios_instruccion(proyecto_id);
CREATE INDEX IF NOT EXISTS idx_cambios_instruccion_propuesto ON cambios_instruccion(propuesto_por);

-- 4. Checklist de Tareas Operativas
CREATE TABLE IF NOT EXISTS checklist_tareas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instruccion_version_id UUID NOT NULL REFERENCES instrucciones_version(id) ON DELETE CASCADE,
    descripcion VARCHAR(300) NOT NULL,
    estado VARCHAR(50) NOT NULL DEFAULT 'PENDIENTE', -- 'PENDIENTE', 'COMPLETADA', 'NO_APLICA'
    notas TEXT,
    orden INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_checklist_tareas_instruccion ON checklist_tareas(instruccion_version_id);
CREATE INDEX IF NOT EXISTS idx_checklist_tareas_estado ON checklist_tareas(estado);

-- 5. Agenda de Visitas
CREATE TABLE IF NOT EXISTS visitas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id UUID NOT NULL REFERENCES proyectos(id),
    numero_visita INTEGER NOT NULL DEFAULT 1,
    asignado_a UUID NOT NULL REFERENCES usuarios(id),
    agendado_por UUID NOT NULL REFERENCES usuarios(id),
    fecha_hora TIMESTAMPTZ NOT NULL,
    estado VARCHAR(50) NOT NULL DEFAULT 'AGENDADA', -- 'AGENDADA', 'COMPLETADA', 'REQUIERE_RETORNO'
    motivo_retorno TEXT,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_visitas_proyecto ON visitas(proyecto_id);
CREATE INDEX IF NOT EXISTS idx_visitas_asignado ON visitas(asignado_a);
CREATE INDEX IF NOT EXISTS idx_visitas_fecha_hora ON visitas(fecha_hora);

-- 6. Firmas de Conformidad
CREATE TABLE IF NOT EXISTS firmas_conformidad (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    visita_id UUID NOT NULL UNIQUE REFERENCES visitas(id) ON DELETE CASCADE,
    firmado_por_cliente VARCHAR(150) NOT NULL,
    fecha TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_firmas_visita ON firmas_conformidad(visita_id);

-- 7. Reportes de Entrega
CREATE TABLE IF NOT EXISTS reportes_entrega (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id UUID NOT NULL UNIQUE REFERENCES proyectos(id),
    generado_por UUID NOT NULL REFERENCES usuarios(id),
    fecha_generacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_reportes_entrega_proyecto ON reportes_entrega(proyecto_id);

-- 8. Equipos Instalados (Inventario en domicilio del cliente)
CREATE TABLE IF NOT EXISTS equipos_instalados (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id UUID NOT NULL REFERENCES proyectos(id),
    producto_id UUID NOT NULL REFERENCES catalogo_productos(id),
    nombre_referencia VARCHAR(200) NOT NULL, -- ej. 'Cámara entrada principal'
    numero_serie VARCHAR(100),
    direccion_mac VARCHAR(50),
    fecha_instalacion DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_equipos_instalados_proyecto ON equipos_instalados(proyecto_id);
CREATE INDEX IF NOT EXISTS idx_equipos_instalados_producto ON equipos_instalados(producto_id);

-- 9. Credenciales de Equipos (Cifradas y centralizadas)
CREATE TABLE IF NOT EXISTS credenciales_equipo (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    equipo_id UUID NOT NULL REFERENCES equipos_instalados(id) ON DELETE CASCADE,
    tipo VARCHAR(50) NOT NULL, -- 'WIFI', 'APP_DISPOSITIVO', 'PANEL_ADMIN'
    usuario VARCHAR(150),
    contrasena_cifrada VARCHAR(500) NOT NULL,
    notas TEXT,
    actualizado_por UUID NOT NULL REFERENCES usuarios(id),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_credenciales_equipo ON credenciales_equipo(equipo_id);

-- 10. Auditoría de Accesos a Credenciales
CREATE TABLE IF NOT EXISTS credenciales_accesos_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credencial_id UUID NOT NULL REFERENCES credenciales_equipo(id) ON DELETE CASCADE,
    consultado_por UUID NOT NULL REFERENCES usuarios(id),
    fecha TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_credenciales_log_credencial ON credenciales_accesos_log(credencial_id);
CREATE INDEX IF NOT EXISTS idx_credenciales_log_usuario ON credenciales_accesos_log(consultado_por);

-- 11. Recordatorios de Mantenimiento Preventivo (Postventa)
CREATE TABLE IF NOT EXISTS mantenimiento_recordatorios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_id UUID NOT NULL REFERENCES proyectos(id),
    fecha_programada DATE NOT NULL,
    estado VARCHAR(50) NOT NULL DEFAULT 'PENDIENTE', -- 'PENDIENTE', 'CONTACTADO', 'OMITIDO'
    contactado_por UUID REFERENCES usuarios(id),
    fecha_contacto TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mantenimiento_proyecto ON mantenimiento_recordatorios(proyecto_id);
CREATE INDEX IF NOT EXISTS idx_mantenimiento_estado ON mantenimiento_recordatorios(estado);
