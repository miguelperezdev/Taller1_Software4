#!/bin/bash
# Detiene los servidores de ApexStore que estén corriendo en este equipo.
for jar in backend pagos persistencia; do
    if pkill -f "build/libs/$jar.jar"; then
        echo "$jar detenido"
    fi
done
