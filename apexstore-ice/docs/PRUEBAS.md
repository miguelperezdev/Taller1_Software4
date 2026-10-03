# Plan de pruebas + evidencias (Punto 4)

Entorno verificado: `openjdk 21`, `slice2java 3.7.10`, `gradle 8.12`,
`com.zeroc:ice-compat:3.7.10` (Maven Central). Orden de encendido: **DB →
Gateways → Backend → Cliente**.

## 1. Casos de prueba

| # | Caso | Comando | Esperado |
|---|---|---|---|
| T1 | Compilación limpia desde cero | `rm -rf build .gradle src/generated && gradle build` | `BUILD SUCCESSFUL` |
| T2 | Compra Stripe | `gradle runClient --args="stripe"` | ACK `Pendiente` en ms → `Autorizado STRIPE-AUTH-…` |
| T3 | Compra PSE | `gradle runClient --args="pse"` | ACK `Pendiente` → `Autorizado PSE-DEBIT-…` |
| T4 | Compra Cripto (latencia mayor) | `gradle runClient --args="cripto"` | 1–2 polls en `Pendiente` → `Autorizado BTC-TX-…` |
| T5 | Nuevo medio Billetera (RAS-04) | `gradle runClient --args="billetera"` | `Autorizado WALLET-AUTH-…` sin cambios al contexto |
| T6 | Fallo aislado PSE (Punto 2c) | `gradle runClient --args="fail"` | `Fallido PSE-TIMEOUT`, resto de medios operativos |
| T7 | Demo completa | `gradle runClient --args="todos"` | 4/4 autorizados |
| T8 | Script todo-en-uno | `bash scripts/run-all.sh` | Levanta 3 nodos + demo |

## 2. Evidencias reales (salidas observadas)

**T7 — cliente (ACKs ≪ 250 ms, RAS-02):**

```
[Cliente] >>> gestionarCompra Stripe-1392C434 medio=Stripe
[Cliente] ACK en 86ms: estado=Pendiente | Recibido en Stripe; confirmacion async via callback
[Cliente] poll[0] estado=Autorizado cod=STRIPE-AUTH-Stripe-1392C434 | Stripe simulado: cargo USD autorizado
[Cliente] >>> gestionarCompra PSE-0FA853AC medio=PSE
[Cliente] ACK en 4ms: estado=Pendiente | Recibido en PSE; confirmacion async via callback
[Cliente] poll[0] estado=Autorizado cod=PSE-DEBIT-PSE-0FA853AC | PSE simulado: debito COP exitoso
[Cliente] >>> gestionarCompra Cripto-17ECD65A medio=Cripto
[Cliente] poll[1] estado=Autorizado cod=BTC-TX-Cripto-17ECD65A | Cripto simulado: cobro BTC confirmado
[Cliente] >>> gestionarCompra BilleteraDigital-34237531 medio=BilleteraDigital
[Cliente] poll[0] estado=Autorizado cod=WALLET-AUTH-BilleteraDigital-34237531 | Billetera simulada: pago autorizado
```

**Backend — despacho síncrono medido por pedido:**

```
[Contexto] iniciarPagoOrden idOrden=Stripe-1392C434 medio=Stripe despacho=66ms (objetivo<250ms)
[Contexto] callback idOrden=Stripe-1392C434 estado=Autorizado cod=STRIPE-AUTH-Stripe-1392C434
[Contexto] orden Stripe-1392C434 actualizada; cliente puede hacer consultarCompra()
```

**DB — auditoría ACID (anti-huérfanos + upsert final, RAS-03):**

```
[DB][AUDIT] ... | PERSIST idOrden=Stripe-E6D91AF9 medio=Stripe monto=120.0USD estado=Pendiente
[DB][AUDIT] ... | ACTUALIZA idOrden=Stripe-E6D91AF9 nuevoEstado=Autorizado cod=STRIPE-AUTH-Stripe-E6D91AF9
```

**T6 — tolerancia a fallos:**

```
[Cliente] >>> gestionarCompra PSE-D9B1102D medio=PSE
[Cliente] ACK en 14ms: estado=Pendiente | Recibido en PSE; confirmacion async via callback
[Cliente] poll[0] estado=Fallido cod=PSE-TIMEOUT | PSE simulado: banco no responde (timeout)
```

## 3. Solución de problemas

| Síntoma | Causa | Solución |
|---|---|---|
| `package Ice does not exist` | Dependencia `com.zeroc:ice` (mapping nuevo) en vez de `ice-compat` | `build.gradle` ya usa `com.zeroc:ice-compat:3.7.10`; no cambiar |
| `ObjectNotExistException` en callback | Backend apagado o encendido después del pedido | Encender en orden DB → Gateways → Backend |
| Cliente no conecta a Checkout | Backend apagado | `gradle runBackend` y reintentar |
| Puerto ocupado (10000–10002) | Servidores previos en fondo | `bash scripts/stop-all.sh` |
