# Despliegue a producción

Una VPS con Ubuntu 24.04 (mínimo 2 vCPU y 4 GB) donde corren Postgres (Docker, solo en
127.0.0.1), el backend (systemd, solo en 127.0.0.1:8080) y Caddy (HTTPS público). La VPS no
compila: el jar se construye en la máquina de desarrollo y se copia.

| Archivo | Va a | Para qué |
|---|---|---|
| `instalar.sh` | se corre una vez en la VPS | paquetes, firewall, usuario, carpetas, claves, Postgres y servicios |
| `desplegar.sh` | se corre en la máquina de desarrollo | compila, respalda, copia el jar, reinicia y espera el chequeo de salud |
| `postgres.compose.yml` | `/opt/hauset/` | Postgres 15 en 127.0.0.1:5432 |
| `hauset.env.example` | `/etc/hauset/hauset.env` (600) | entorno del backend |
| `hauset.service` | `/etc/systemd/system/` | backend con reinicio automático y sin permisos de escritura salvo sus archivos |
| `Caddyfile` | `/etc/caddy/Caddyfile` | HTTPS; quita `Forwarded` y `X-Forwarded-Prefix` del cliente |
| `respaldo.sh` + `hauset-respaldo.*` | `/opt/hauset/`, `/etc/systemd/system/` | `pg_dump` y archivos locales cada día a las 03:30, 14 días |

## Primera vez

1. **DNS**: registro A `api.tudominio.com` → IP de la VPS. Acceso SSH solo con llave.
2. **Instalar** (desde la máquina de desarrollo):
   ```bash
   scp -r deploy/produccion root@VPS:/root/hauset-deploy
   ssh root@VPS /root/hauset-deploy/instalar.sh
   ```
   Genera claves nuevas en `/etc/hauset/`. Copia `HAUSET_CREDENCIALES_LLAVE` a un lugar seguro
   fuera de la VPS y aparte de los respaldos.
3. **Archivos en R2**: en Cloudflare → R2, crea un bucket privado `hauset-produccion` (sin dominio
   público ni acceso r2.dev) y un token de API de R2 con permiso *Object Read & Write* solo sobre ese
   bucket. Pon `R2_ACCOUNT_ID`, `R2_BUCKET`, `R2_ACCESS_KEY_ID` y `R2_SECRET_ACCESS_KEY` en
   `/etc/hauset/hauset.env` (ya trae `STORAGE_PROVEEDOR=R2`). La app nunca habla con el bucket: todo
   pasa por el backend, que revisa permisos.
4. **Primer jefe**: en `/etc/hauset/hauset.env` completa `HAUSET_PRIMER_JEFE_TELEFONO`, `_NOMBRE`
   y `_PASSWORD` (8 a 72 caracteres). Las cuentas de prueba de las migraciones quedan desactivadas (V26).
5. **Caddy**: pon el dominio en `/etc/caddy/Caddyfile.hauset`, cópialo sobre `/etc/caddy/Caddyfile`
   y `systemctl reload caddy`.
6. **Desplegar**: `deploy/produccion/desplegar.sh root@VPS`.
7. Entra a la app con el primer jefe, crea las demás cuentas, luego **borra las tres líneas
   `HAUSET_PRIMER_JEFE_*`** y `systemctl restart hauset`.
8. Limpia los proveedores y el catálogo de ejemplo que siembra V4, y haz una prueba completa: un
   lead, un informe, una cotización, un proyecto y su cierre.

## Cada actualización

```bash
deploy/produccion/desplegar.sh root@VPS
```

Respalda la base antes de reiniciar (Flyway migra al arrancar) y guarda el jar anterior en
`/opt/hauset/hauset.jar.anterior`.

## Operación

- Registros: `journalctl -u hauset -f`
- Estado: `systemctl status hauset`, `curl https://api.tudominio.com/actuator/health`
- Los archivos están en R2, no en la VPS: respalda el bucket aparte (por ejemplo `rclone sync`
  hacia otro bucket o proveedor), además de la base.
- Respaldos de la base en `/var/backups/hauset/`; cópialos fuera de la VPS (por ejemplo con `rclone` en otro
  timer). Prueba restaurar uno al menos una vez:
  `gunzip -c base-….sql.gz | docker compose -f /opt/hauset/postgres.compose.yml --env-file /etc/hauset/postgres.env exec -T postgres psql -U hauset -d app_domotica`
  (sobre una base vacía).
