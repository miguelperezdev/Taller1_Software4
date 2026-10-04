#!/bin/bash
# Nodo 4: Servidor Base de Datos Transaccional (puerto 10000)
cd "$(dirname "$0")/.." || exit 1
[ -f build/libs/persistencia.jar ] || gradle -q assemble || exit 1
exec java -jar build/libs/persistencia.jar "$@"
