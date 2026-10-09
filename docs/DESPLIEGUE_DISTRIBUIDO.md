# Guía de sustentación: despliegue en 2 PCs

> Los `config/*.config` versionados usan `127.0.0.1` (modo local). Las IPs de esta guía se
> aplican **únicamente el día de la sustentación**, solo para la comunicación entre los dos
> equipos, con `scripts/configurar-red.sh`. No hay que recompilar por cambiar de IP.

## Reparto

| Equipo | Nodos | Puertos |
|--------|-------|---------|
| Mi PC | persistencia + backend | 10000, 10002 |
| PC compañero | pagos | 10001 |
| Cliente | cualquiera de los dos | — (apunta al backend) |

## Paso 0 — Requisitos (ambos PCs)

```bash
java -version      # 17 o superior
gradle --version   # 8.x (solo donde se compile)
slice2java --version  # 3.7 (solo donde se compile)
```

Ambos con el mismo código:

```bash
git pull
```

## Paso 1 — Confirmar las IPs (ambos PCs)

```bash
ip addr | grep "inet 10\.\|inet 172"
```

Anota:

- Mi IP: `<MI_IP>`
- IP compañero: `<IP_COMPANERO>`

Tienen que estar en la **misma red** (los dos en `10.x` o los dos en `172.30.x`). Si están en
rangos distintos, no se van a ver: usen cable o el hotspot de un celular.

## Paso 2 — Aplicar las IPs (solo en un PC)

```bash
scripts/configurar-red.sh <MI_IP> <IP_COMPANERO>
```

Ejemplo:

```bash
scripts/configurar-red.sh 10.248.185.193 172.30.150.240
```

Verifica que el script muestre las dos IPs y las 9 líneas (`Endpoints` y `Proxy`).
Para volver a modo local en cualquier momento:

```bash
scripts/configurar-red.sh --local
```

## Paso 3 — Sincronizar el `config/` (importante)

Ambos PCs deben tener el **mismo** `config/`. Opción A (recomendada si hay internet):

```bash
# En el PC donde corriste el script:
git push
# En el otro PC:
git pull
```

Opción B (sin internet): copia la carpeta `config/` al otro PC por USB o cable.

## Paso 4 — Compilar (ambos PCs)

```bash
gradle assemble
```

Debe dejar los 4 jars en `build/libs/` (`persistencia.jar`, `pagos.jar`, `backend.jar`,
`cliente.jar`).

## Paso 5 — Abrir firewall

```bash
# En mi PC:
sudo ufw allow 10000/tcp
sudo ufw allow 10002/tcp

# En el PC del compañero:
sudo ufw allow 10001/tcp
```

## Paso 6 — Probar la red (antes de arrancar ICE)

```bash
ping <IP_DEL_OTRO>
```

Y los puertos (desde el PC contrario):

```bash
nc -zv <MI_IP> 10000 10002          # desde el PC del compañero
nc -zv <IP_COMPANERO> 10001         # desde mi PC
```

Si el `ping` falla, el problema es la red del salón (aislamiento de clientes WiFi), no el
código. Cambien de red y repitan desde el paso 1.

## Paso 7 — Arrancar en orden (no cambiarlo)

```bash
# Terminal 1 — MI PC:
scripts/persistencia.sh     # Nodo 4, puerto 10000

# Terminal 2 — PC COMPAÑERO (cuando la terminal 1 muestre que escucha):
scripts/pagos.sh            # Nodo 3, puerto 10001

# Terminal 3 — MI PC (cuando la terminal 2 muestre "Estrategias publicadas"):
scripts/backend.sh          # Nodo 2, puerto 10002

# Terminal 4 — cualquiera:
scripts/cliente.sh stripe
```

Prueba completa (los 8 escenarios):

```bash
scripts/cliente.sh
```

Resultado esperado: cada pago pasa `Pendiente → Aprobado` (PSE pasa por `EnVerificacion`
antes). Escenarios disponibles: `stripe pse cripto billetera rechazo invalido desconocido
duplicada`, ej: `scripts/cliente.sh pse duplicada`.

## Paso 8 — Apagar

`Ctrl+C` en cada terminal, o en cada PC:

```bash
scripts/detener.sh
```

## Fallas comunes

| Síntoma | Causa | Solución |
|---------|-------|----------|
| `No hay conexión con el backend` | backend apagado o `Checkout.Proxy` con otra IP | Arranca `backend.sh`; revisa `config/cliente.config` |
| `SIN-REGISTRO` en todo | persistencia apagada | Arranca `persistencia.sh` en mi PC |
| `PASARELA-NO-DISPONIBLE` | pagos apagado o `Pagos.*.Proxy` con otra IP | Arranca `pagos.sh`; re-corre el script del paso 2 |
| Pagos atorados en `Pendiente` | `Backend.Endpoints` quedó en `127.0.0.1` y las pasarelas no pueden devolver el resultado | Re-corre `scripts/configurar-red.sh` con tu IP real y reinicia el backend |
| `Address already in use` | quedó una instancia corriendo | `scripts/detener.sh` y reintenta |
| `slice2java: command not found` | ICE 3.7 no instalado | Solo afecta a compilar; compila en el otro PC y copia los jars |
