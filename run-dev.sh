#!/usr/bin/env bash
# Levanta el backend en desarrollo con Java 21. Lee .env.dev y escucha en SERVER_ADDRESS.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="$(ls -d "$HOME"/.local/jdk/jdk-21* | head -1)"
export PATH="$JAVA_HOME/bin:$PATH"
set -a; source .env.dev; set +a
exec ./mvnw -o -q spring-boot:run
