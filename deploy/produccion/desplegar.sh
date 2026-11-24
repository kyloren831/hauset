#!/usr/bin/env bash
# Compila el backend en ESTA máquina y lo instala en la VPS de producción (que no compila nada).
#   deploy/produccion/desplegar.sh root@IP_O_DOMINIO_DE_LA_VPS
# Flyway aplica las migraciones al arrancar; antes se toma un respaldo de la base.
set -euo pipefail
destino=${1:?Uso: desplegar.sh root@vps}
cd "$(dirname "$0")/../.."

./mvnw -q -DskipTests package
jar=$(ls target/api-*.jar | grep -v plain | head -1)
scp "$jar" "$destino:/opt/hauset/hauset.jar.nuevo"

ssh "$destino" bash -s <<'REMOTO'
set -euo pipefail
/opt/hauset/respaldo.sh
[[ -f /opt/hauset/hauset.jar ]] && cp /opt/hauset/hauset.jar /opt/hauset/hauset.jar.anterior
mv /opt/hauset/hauset.jar.nuevo /opt/hauset/hauset.jar
chmod 644 /opt/hauset/hauset.jar
systemctl restart hauset
for _ in $(seq 60); do
  if curl -fsS http://127.0.0.1:8080/actuator/health | grep -q UP; then echo "Backend arriba"; exit 0; fi
  sleep 2
done
echo "El backend no respondió en 2 minutos: journalctl -u hauset -n 100"
echo "Para volver atrás: mv /opt/hauset/hauset.jar.anterior /opt/hauset/hauset.jar && systemctl restart hauset"
echo "(si ya migró la base, restaura también el último respaldo de /var/backups/hauset)"
exit 1
REMOTO
