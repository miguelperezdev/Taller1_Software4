#!/bin/bash
# Nodo 1: cliente de prueba.
# Uso: scripts/cliente.sh [stripe pse cripto billetera rechazo invalido desconocido duplicada]
# Sin argumentos ejecuta todos los escenarios.
cd "$(dirname "$0")/.." || exit 1
[ -f build/libs/cliente.jar ] || gradle -q assemble || exit 1
exec java -jar build/libs/cliente.jar "$@"
