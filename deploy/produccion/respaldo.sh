#!/usr/bin/env bash
# Respaldo diario (lo corre hauset-respaldo.timer como root): base + archivos locales, 14 días en la VPS.
# Con STORAGE_PROVEEDOR=R2 los archivos viven en el bucket y la carpeta local queda casi vacía:
# respalda el bucket aparte (por ejemplo rclone sync hacia otro bucket o proveedor).
# Cópialos además FUERA de la VPS (rclone, rsync a otra máquina…): un respaldo en el mismo disco no
# sirve si se pierde la VPS. La llave HAUSET_CREDENCIALES_LLAVE nunca se guarda junto a ellos.
set -euo pipefail
destino=/var/backups/hauset
fecha=$(date +%Y%m%d-%H%M)
install -d -m 700 "$destino"
docker compose -f /opt/hauset/postgres.compose.yml --env-file /etc/hauset/postgres.env \
  exec -T postgres pg_dump -U hauset -d app_domotica | gzip > "$destino/base-$fecha.sql.gz"
tar -czf "$destino/archivos-$fecha.tar.gz" -C /var/lib/hauset storage
find "$destino" -type f -mtime +14 -delete
