#!/bin/bash
# Nodo 3: Servidor de Pasarelas y Estrategias de Pago (puerto 10001)
cd "$(dirname "$0")/.." || exit 1
[ -f build/libs/pagos.jar ] || gradle -q assemble || exit 1
exec java -jar build/libs/pagos.jar "$@"
