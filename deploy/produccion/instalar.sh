#!/usr/bin/env bash
# Instalación del backend en la VPS de producción. Se corre desde esta carpeta:  sudo ./instalar.sh
# Idempotente: se puede repetir (también para actualizar estos archivos); no regenera claves ni pisa
# /etc/hauset/hauset.env. No toca SSH, UFW ni fail2ban, ni abre puertos. No agrega a nadie al grupo docker.
set -euo pipefail
[[ $(id -u) == 0 ]] || { echo "Córrelo con sudo: sudo ./instalar.sh" >&2; exit 1; }
cd "$(dirname "$0")"

echo "== Paquetes"
apt-get update -q
DEBIAN_FRONTEND=noninteractive apt-get install -y -q openjdk-21-jre-headless docker.io docker-compose-v2 caddy unzip curl

echo "== Usuario de servicio y carpetas"
id hauset >/dev/null 2>&1 || useradd --system --home-dir /opt/hauset --shell /usr/sbin/nologin hauset
install -d -o root -g root -m 755 /opt/hauset /srv/descargas
install -d -o hauset -g hauset -m 750 /var/lib/hauset /var/lib/hauset/storage
install -d -o root -g root -m 700 /etc/hauset /var/backups/hauset

echo "== Archivos"
install -o root -g root -m 644 postgres.compose.yml /opt/hauset/
install -o root -g root -m 755 respaldo.sh /opt/hauset/
install -o root -g root -m 755 hauset-desplegar /usr/local/bin/
install -o root -g root -m 644 hauset.service hauset-respaldo.service hauset-respaldo.timer /etc/systemd/system/
install -o root -g root -m 644 Caddyfile /etc/caddy/Caddyfile

# GitHub Actions entra como deploy y solo puede correr hauset-desplegar como root (nunca NOPASSWD: ALL).
echo 'deploy ALL=(root) NOPASSWD: /usr/local/bin/hauset-desplegar' > /etc/sudoers.d/hauset-desplegar.nuevo
chmod 440 /etc/sudoers.d/hauset-desplegar.nuevo
visudo -cqf /etc/sudoers.d/hauset-desplegar.nuevo
mv /etc/sudoers.d/hauset-desplegar.nuevo /etc/sudoers.d/hauset-desplegar

echo "== Claves"
if [[ ! -f /etc/hauset/hauset.env ]]; then
  (umask 077
   sed -e "s|^SPRING_DATASOURCE_PASSWORD=.*|SPRING_DATASOURCE_PASSWORD=$(openssl rand -hex 24)|" \
       -e "s|^APP_JWTSECRET=.*|APP_JWTSECRET=$(openssl rand -base64 48 | tr -d '\n')|" \
       -e "s|^HAUSET_CREDENCIALES_LLAVE=.*|HAUSET_CREDENCIALES_LLAVE=$(openssl rand -base64 32)|" \
       hauset.env.example > /etc/hauset/hauset.env)
  echo "Creado /etc/hauset/hauset.env con claves nuevas (no se muestran)"
else
  echo "/etc/hauset/hauset.env ya existe: se conserva"
fi

echo "== Base de datos (Docker, solo en 127.0.0.1)"
# Misma clave que usa el backend; postgres.env se rehace desde hauset.env en cada corrida.
(umask 077; sed -n 's/^SPRING_DATASOURCE_PASSWORD=/POSTGRES_PASSWORD=/p' /etc/hauset/hauset.env > /etc/hauset/postgres.env)
systemctl enable --now docker
docker compose -f /opt/hauset/postgres.compose.yml --env-file /etc/hauset/postgres.env up -d --wait

echo "== Servicios"
caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
systemctl daemon-reload
systemctl enable caddy hauset hauset-respaldo.timer
systemctl start hauset-respaldo.timer
systemctl reload caddy

echo "Listo. Revisa /etc/hauset/hauset.env (primer jefe y R2). El backend arranca con el primer despliegue."
