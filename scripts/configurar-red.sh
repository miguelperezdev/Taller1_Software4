#!/bin/bash
# Configura las IPs de los 4 nodos sin editar a mano.
#
# Reparto asumido:
#   MI PC       -> persistencia (10000) + backend (10002)
#   COMPAÑERO   -> pagos (10001)
#   Cliente     -> apunta a MI PC
#
# Uso:
#   scripts/configurar-red.sh <MI_IP> <IP_COMPANERO>   # distribuido en 2 PCs
#   scripts/configurar-red.sh --local                  # todo en 127.0.0.1
#   scripts/configurar-red.sh                          # = --local
#
# Ejemplo:
#   scripts/configurar-red.sh 10.248.185.193 172.30.150.240
cd "$(dirname "$0")/.." || exit 1

if [ "$1" = "--local" ] || [ "$1" = "local" ] || [ $# -eq 0 ]; then
  MI="127.0.0.1"
  COMP="127.0.0.1"
elif [ $# -eq 1 ]; then
  MI="$1"
  COMP="$1"
elif [ $# -eq 2 ]; then
  MI="$1"
  COMP="$2"
else
  echo "Uso: $0 <MI_IP> <IP_COMPANERO> | --local"
  exit 1
fi

# Validación básica de formato IPv4
for ip in "$MI" "$COMP"; do
  if ! echo "$ip" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then
    echo "IP inválida: $ip"
    exit 1
  fi
done

sed -i -E "s/^Persistencia\.Endpoints=.*/Persistencia.Endpoints=tcp -h ${MI} -p 10000/" config/persistencia.config
sed -i -E "s/^Pagos\.Endpoints=.*/Pagos.Endpoints=tcp -h ${COMP} -p 10001/" config/pagos.config
sed -i -E "s/^Backend\.Endpoints=.*/Backend.Endpoints=tcp -h ${MI} -p 10002/" config/backend.config
sed -i -E "s/^Persistencia\.Proxy=.*/Persistencia.Proxy=Persistencia:tcp -h ${MI} -p 10000/" config/backend.config
sed -i -E "s/^(Pagos\.[^.]+\.Proxy=[^:]+:tcp -h )[^ ]+( -p 10001)/\1${COMP}\2/" config/backend.config
sed -i -E "s/^Checkout\.Proxy=.*/Checkout.Proxy=ServicioCheckout:tcp -h ${MI} -p 10002/" config/cliente.config

echo "MI PC (backend+persistencia): ${MI}"
echo "COMPANERO (pagos):            ${COMP}"
echo "---"
grep -H "Endpoints\|Proxy=" config/*.config
