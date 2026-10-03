#!/bin/bash
# Uso: ./scripts/run-all.sh — levanta los 4 nodos en orden (DB -> Gateways -> Backend -> Cliente)
set -e
cd "$(dirname "$0")/.."
echo "== Compilando =="
gradle -q build
echo "== Nodo 4: DB (fondo) =="
gradle -q runDatabase > /tmp/apex-db.log 2>&1 &
sleep 2
echo "== Nodo 3: Gateways (fondo) =="
gradle -q runGateways > /tmp/apex-gw.log 2>&1 &
sleep 2
echo "== Nodo 2: Backend (fondo) =="
gradle -q runBackend > /tmp/apex-be.log 2>&1 &
sleep 3
echo "== Nodo 1: Cliente demo =="
gradle -q runClient --args="todos"
echo ""
echo "Logs: /tmp/apex-{db,gw,be}.log"
