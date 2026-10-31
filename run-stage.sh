#!/usr/bin/env bash
# Backend de stage: su propia base (app_domotica_stage), puerto 8081 solo en localhost y su carpeta de archivos.
# Caddy lo publica en https://stage.aaron-devserver.online. Corre desde una copia del jar para no chocar con dev.
# Actualizar stage:  ./mvnw -o -q package -DskipTests && cp target/<jar> stage/hauset-stage.jar  y reiniciar.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="$(ls -d "$HOME"/.local/jdk/jdk-21* | head -1)"
export PATH="$JAVA_HOME/bin:$PATH"
set -a; source .env.stage; set +a
exec java -jar stage/hauset-stage.jar
