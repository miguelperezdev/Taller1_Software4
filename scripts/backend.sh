#!/bin/bash
# Nodo 2: Servidor E-Commerce (puerto 10002)
cd "$(dirname "$0")/.." || exit 1
[ -f build/libs/backend.jar ] || gradle -q assemble || exit 1
exec java -jar build/libs/backend.jar "$@"
