-- ============================================================================
-- V7__auth_hardening.sql
-- Rotación y revocación de refresh tokens, y registro de intentos de login.
-- ============================================================================

ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS revocado BOOLEAN NOT NULL DEFAULT false;
-- Todos los tokens emitidos a partir de un mismo login comparten familia; si se reutiliza
-- un token ya rotado se revoca la familia completa (indicio de token robado).
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS familia_id UUID;
UPDATE refresh_tokens SET familia_id = gen_random_uuid() WHERE familia_id IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN familia_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_usuario ON refresh_tokens(usuario_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_familia ON refresh_tokens(familia_id);

-- Registro de intentos de login para limitar fuerza bruta (compartido entre réplicas).
CREATE TABLE IF NOT EXISTS intentos_login (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    telefono VARCHAR(50) NOT NULL,
    ip VARCHAR(64),
    exitoso BOOLEAN NOT NULL,
    fecha TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_intentos_login_telefono_fecha ON intentos_login(telefono, fecha DESC);
CREATE INDEX IF NOT EXISTS idx_intentos_login_ip_fecha ON intentos_login(ip, fecha DESC);
