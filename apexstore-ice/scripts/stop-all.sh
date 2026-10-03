#!/bin/bash
# Uso: ./scripts/stop-all.sh — detiene los servidores ApexStore (DB, Gateways, Backend)
for main in "com.apexstore.db.DatabaseServer" "com.apexstore.gateways.GatewaysServer" "com.apexstore.backend.BackendServer"; do
    if pgrep -f "$main" > /dev/null; then
        echo "Deteniendo $main..."
        pkill -f "$main"
    else
        echo "No corría: $main"
    fi
done
sleep 2
echo "Listo."
