#!/usr/bin/env bash
# Preparación de una VPS nueva con Ubuntu 24.04. Se corre UNA vez, como root, en la VPS:
#   scp -r deploy/produccion root@VPS:/root/hauset-deploy && ssh root@VPS /root/hauset-deploy/instalar.sh
# Es idempotente: volver a correrlo no borra datos ni claves.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo "Córrelo como root"; exit 1; }
aqui="$(cd "$(dirname "$0")" && pwd)"

echo "== Paquetes"
apt-get update -q
apt-get install -y -q openjdk-21-jre-headless docker.io docker-compose-v2 caddy ufw fail2ban

echo "== Firewall: solo SSH, HTTP y HTTPS"
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo "== Usuario y carpetas"
id hauset &>/dev/null || useradd --system --home-dir /opt/hauset --shell /usr/sbin/nologin hauset
install -d -o root -g root -m 755 /opt/hauset /srv/descargas
install -d -o hauset -g hauset -m 750 /var/lib/hauset /var/lib/hauset/storage
install -d -o root -g root -m 700 /etc/hauset
install -o root -g root -m 644 "$aqui/postgres.compose.yml" /opt/hauset/
install -o root -g root -m 755 "$aqui/respaldo.sh" /opt/hauset/

echo "== Claves (solo si no existen)"
if [[ ! -f /etc/hauset/postgres.env ]]; then
  clave_pg=$(openssl rand -base64 24 | tr -d '/+=')
  umask 077
  printf 'POSTGRES_PASSWORD=%s\n' "$clave_pg" > /etc/hauset/postgres.env
  sed -e "s|^SPRING_DATASOURCE_PASSWORD=.*|SPRING_DATASOURCE_PASSWORD=$clave_pg|" \
      -e "s|^APP_JWTSECRET=.*|APP_JWTSECRET=$(openssl rand -base64 48 | tr -d '\n')|" \
      -e "s|^HAUSET_CREDENCIALES_LLAVE=.*|HAUSET_CREDENCIALES_LLAVE=$(openssl rand -base64 32)|" \
      "$aqui/hauset.env.example" > /etc/hauset/hauset.env
  chmod 600 /etc/hauset/postgres.env /etc/hauset/hauset.env
  echo "   Claves generadas en /etc/hauset/. Copia HAUSET_CREDENCIALES_LLAVE a un lugar seguro FUERA de la VPS."
else
  echo "   /etc/hauset ya tiene claves; no se tocan."
fi

echo "== Postgres"
docker compose -f /opt/hauset/postgres.compose.yml --env-file /etc/hauset/postgres.env up -d --wait

echo "== Servicios"
install -m 644 "$aqui/hauset.service" "$aqui/hauset-respaldo.service" "$aqui/hauset-respaldo.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable hauset hauset-respaldo.timer
systemctl start hauset-respaldo.timer
if [[ ! -f /etc/caddy/Caddyfile.hauset ]]; then
  cp "$aqui/Caddyfile" /etc/caddy/Caddyfile.hauset
fi

cat <<'FIN'

Listo. Falta:
  1. Editar /etc/hauset/hauset.env: HAUSET_PRIMER_JEFE_TELEFONO, _NOMBRE y _PASSWORD.
  2. Poner el dominio real en /etc/caddy/Caddyfile.hauset, copiarlo sobre /etc/caddy/Caddyfile
     y correr: systemctl reload caddy
  3. Desde tu máquina: deploy/produccion/desplegar.sh root@VPS
FIN
