#!/bin/bash
# Levanta los tres servidores en este equipo, ejecuta el cliente y los apaga al terminar.
# Los registros de cada nodo quedan en build/logs/.
cd "$(dirname "$0")/.." || exit 1
gradle -q assemble || exit 1
mkdir -p build/logs

pids=()
detener() { kill "${pids[@]}" 2>/dev/null; wait 2>/dev/null; }
trap detener EXIT

for nodo in persistencia pagos backend; do
    java -jar "build/libs/$nodo.jar" > "build/logs/$nodo.log" 2>&1 &
    pids+=($!)
    sleep 1.5
done

java -jar build/libs/cliente.jar "$@"
echo
echo "Registros de los servidores en build/logs/"
