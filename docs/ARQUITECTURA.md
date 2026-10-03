# Punto 3 -> Punto 4: rediseño aplicado y mapeo a código

## 1. Defectos del diagrama original y corrección

| # | Defecto (Punto 1) | Principio violado | Corrección (Punto 3) | Evidencia en código |
|---|---|---|---|---|
| D1 | Firmas heterogéneas por pasarela (`autorizarCargoStripe(tokenTarjeta,montoUSD,cvc)` vs `debitarTransferenciaPSE(...)` vs `generarCobroCriptoBtc(...)`) | OCP, DIP, LSP, ocultamiento de información | **Interfaz única** `EstrategiaPago.pagar(OrdenPago, PagoCallback*)` con `OrdenPago.datosPago` opaco | `slice/ApexStore.ice` + `gateways/EstrategiaBase.java` |
| D2 | `EstrategiaCripto -> persistirTransaccionExitosa` directo a DB, bypaseando al contexto (el contexto nunca se entera, Punto 2d) | SRP, encapsulamiento | **Prohibido escribir directo**; Cripto confirma vía `PagoCallback` y **solo el contexto** persiste | `gateways/EstrategiaCriptoImpl.java`, `backend/ProcesadorPagosContextoImpl.onCallback()` |
| D3 | DB con 2 operaciones (`persistirTransaccionPostgres` / `persistirTransaccionExitosa`) según quién llame | ISP, uniformidad | **Una sola** `persistirTransaccion(resultado, orden)` con upsert por `idOrden` (PERSIST del ACK + ACTUALIZA del callback) | `db/TransaccionesDBImpl.java` |
| D4 | Despacho síncrono bloqueante hacia bancos (15 s congelan thread pool) | Disponibilidad | **ACK Pendiente + callback oneway async** en pool propio por pasarela | `gateways/EstrategiaBase.pagar()` |
| D5 | Fallo de una pasarela (PSE caído / mempool BTC) contamina a las demás | Bulkhead / tolerancia a fallos | **try/catch + proxy por medio + pools separados**; el contexto aísla el fallo | `backend/ProcesadorPagosContextoImpl.iniciarPagoOrden()` |

## 2. Mapeo componente Slice -> clase Java -> nodo

| Interfaz Slice | Rol patrón | Clase servant | Nodo / proceso |
|---|---|---|---|
| `ServicioCheckout` | Facade | `ServicioCheckoutImpl` | N2 `BackendServer` |
| `ProcesadorPagos` | Strategy Context | `ProcesadorPagosContextoImpl` | N2 `BackendServer` |
| `PagoCallback` | Observer (oneway) | `CallbackServant` (interno del contexto) | N2 `BackendServer` (`ProcesadorCallback`) |
| `EstrategiaPago` | Strategy (x4) | `EstrategiaStripe/PSE/Cripto/BilleteraImpl` | N3 `GatewaysServer` |
| `PersistenciaTransaccional` | Repository ACID | `TransaccionesDBImpl` | N4 `DatabaseServer` |

## 3. Secuencia de un pago (ej. Stripe)

1. `ClienteApp.gestionarCompra(orden)` -> `ServicioCheckout` (N1->N2).
2. `ServicioCheckout` -> `ProcesadorPagos.iniciarPagoOrden` (intra-N2).
3. Contexto resuelve `Stripe -> EstrategiaStripe:tcp:10001`, llama `pagar(orden, callback)`; la
   pasarela retorna `Pendiente` en <50 ms; el contexto persiste el ACK (anti-huérfanos).
4. Hilo async de la pasarela simula el cobro (150-500 ms) y llama
   `ProcesadorCallback.notificarTransaccionExitosa(resultado)` **oneway** (N3->N2).
5. `onCallback` actualiza el estado en memoria y hace `persistirTransaccion` idempotente (N2->N4).
6. El cliente ve el resultado final con `consultarCompra` (polling; en producción: push).

## 4. Cobertura RAS

- **RAS-01:** ningún hilo del backend espera a bancos; callbacks oneway + pools por pasarela.
- **RAS-02:** despacho medido e impreso en consola (`despacho=Xms objetivo<250ms`).
- **RAS-03:** `synchronized` + upsert idempotente por `idOrden` (sin doble cobro)
  + ACK inicial anti-huérfanos + log de auditoría (ver `docs/PRUEBAS.md §2`).
- **RAS-04:** `BilleteraDigital` como prueba viva de OCP (1 clase + 2 registros).

## 5. Diagrama corregido (para redibujar en Visual Paradigm)

```
[Nodo1 Front] --gestionarCompra--> [Nodo2: ServicioCheckout] --iniciarPagoOrden--> [ProcesadorPagosContexto]
[ProcesadorPagosContexto] --pagar(OrdenPago)<<Strategy>>--> [EstrategiaStripe|PSE|Cripto|Billetera] (Nodo3)
[Estrategias] --notificarTransaccionExitosa [oneway, async]--> [ProcesadorCallback] (Nodo2)
[ProcesadorPagosContexto] --persistirTransaccion [unica op]--> [DB PostgreSQL] (Nodo4)
NOTA: no existe flecha Estrategia->DB. Todas las estrategias implementan la MISMA interfaz.
```
