# ApexStore — Base de implementación Java + ICE (Punto 4, RA4)

**Curso:** Ingeniería de Software IV (09775-TIC) · **Tarea 1 — Taller ApexStore**
**Integrantes:** Miguel · Danna Isabel (+ 1)
**Middleware:** ICE 3.7.10 (compat mapping, `slice2java --compat`) · **Build:** Gradle · **Java:** 11+ (probado con 21)

> Las pasarelas de pago son **100% simuladas**: ningún código sale a redes
> bancarias/blockchain reales. Los retardos y fallos son `Thread.sleep` +
> aleatoriedad controlada para demostrar tolerancia a fallos.

---

## 1. Estructura de carpetas

```
apexstore-ice/
├── slice/ApexStore.ice            # Contrato Slice UNIFICADO (diseño corregido Punto 3)
├── src/main/java/com/apexstore/
│   ├── common/ApexConfig.java     # Endpoints, identidades, P95 objetivo
│   ├── db/                        # NODO 4: TransaccionesDBImpl, DatabaseServer (:10000)
│   ├── gateways/                  # NODO 3: EstrategiaBase + Stripe/PSE/Cripto/Billetera, GatewaysServer (:10001)
│   ├── backend/                   # NODO 2: ServicioCheckoutImpl, ProcesadorPagosContextoImpl, BackendServer (:10002)
│   └── client/                    # NODO 1: ClienteApp (WebApp/MobileApp simulados)
├── config/                        # db/gateways/backend/client.config
├── scripts/                       # run-all.sh (levanta todo) / stop-all.sh (detiene nodos)
├── build.gradle / settings.gradle # ice-compat 3.7.10 + tarea generateSlice
└── docs/                          # ARQUITECTURA, DIAGRAMA_CORREGIDO, PRUEBAS
```

## 2. Topología (4 nodos del diagrama -> 3 JVM + cliente)

| Nodo diagrama | Proceso JVM | Puerto | Identidades ICE |
|---|---|---|---|
| N1 Front-End | `ClienteApp` | — (solo llama) | — |
| N2 Backend Core | `BackendServer` | 10002 | `ServicioCheckout`, `ProcesadorPagos`, `ProcesadorCallback` |
| N3 Pasarelas | `GatewaysServer` | 10001 | `EstrategiaStripe`, `EstrategiaPSE`, `EstrategiaCripto`, `EstrategiaBilletera` |
| N4 DB | `DatabaseServer` | 10000 | `PersistenciaDB` |

Flujo: `Cliente --gestionarCompra--> Checkout --iniciarPagoOrden--> Contexto
--pagar--> Estrategia --(async oneway)--> ProcesadorCallback --persistir--> DB`.

## 3. Prerrequisitos

```bash
java -version        # 11+
slice2java --version # 3.7.x
gradle --version     # 8.x
```

Gradle descarga `com.zeroc:ice-compat:3.7.10` de Maven Central automáticamente
(runtime del mapping compat generado con `slice2java --compat`).

## 4. Compilar y ejecutar

```bash
cd apexstore-ice
gradle build                  # genera Slice + compila

# Terminal 1 — Nodo 4 DB
gradle runDatabase
# Terminal 2 — Nodo 3 Pasarelas
gradle runGateways
# Terminal 3 — Nodo 2 Backend
gradle runBackend
# Terminal 4 — Nodo 1 Cliente (todos los medios)
gradle runClient --args="todos"
# Variantes: stripe | pse | cripto | billetera | fail
gradle runClient --args="pse"
gradle runClient --args="fail"   # fuerza fallo para ver tolerancia

# O todo de una vez:
bash scripts/run-all.sh    # DB -> Gateways -> Backend -> demo cliente
bash scripts/stop-all.sh   # detiene los 3 servidores
```

Orden de encendido obligatorio: **DB -> Gateways -> Backend -> Cliente**
(el backend resuelve proxies de DB y pasarelas al recibir el primer pedido).

## 5. Qué demuestra cada requisito (resumen)

- **RAS-01 (async desacoplado):** `pagar()` retorna ACK `Pendiente` en ms;
  el cobro real se confirma después por `notificarTransaccionExitosa` **oneway**
  en hilo aparte (`EstrategiaBase`). El backend jamás se bloquea 15 s.
- **RAS-02 (P95 < 250 ms):** el despacho síncrono es liviano (log + proxy);
  ver `ProcesadorPagosContextoImpl` que mide e imprime el tiempo de despacho.
- **RAS-03 (ACID):** `TransaccionesDBImpl` con `synchronized` + idempotencia
  por `idOrden` (sin doble cobro) + log de auditoría.
- **RAS-04 (extensibilidad):** `EstrategiaBilleteraImpl` añadida sin tocar el
  contexto salvo 1 línea de registro. Ver `docs/ARQUITECTURA.md` y
  `docs/DIAGRAMA_CORREGIDO.md`.
- **Punto 2d:** Cripto **ya no** escribe directo a DB; todo pasa por el callback.
- **Evidencias de ejecución:** ver `docs/PRUEBAS.md` (salidas reales + troubleshooting).

## 6. Agregar un medio nuevo (p. ej. Nequi) — 3 pasos

1. Crear `EstrategiaNequiImpl extends EstrategiaBase` (copiar `EstrategiaBilleteraImpl`).
2. Registrarla en `GatewaysServer` + identidad en `ApexConfig`.
3. Añadir 1 línea en el mapa `estrategiaPorMedio` del contexto + valor en el
   `enum MedioPago` del `.ice` (regenerar con `gradle generateSlice`).

## 7. Fragmento de código para el informe (Punto 4)

```java
// Strategy uniforme: todas las pasarelas implementan la MISMA firma
ResultadoPago ack = estrategia.pagar(orden, selfCallback()); // < 50 ms
// ...y confirman async sin bloquear:
cbOneway.notificarTransaccionExitosa(resultadoFinal);        // oneway
```

## 8. Anexo — Bitácora de uso de IAG (Nivel 3, obligatoria en el PDF)

1. **Herramienta y versión:** Muse Spark 1.3 (OpenCode) — generación de base.
2. **Prompts principales:** "generar base Java+ICE del diagrama corregido Punto 4,
   pasarelas simuladas, organizar carpetas" (ver historial del chat).
3. **Evaluación crítica y corrección humana:** [COMPLETAR por el equipo:
   qué errores/omisiones detectaron en la base, qué ajustaron al diagrama
   corregido de su Punto 3, pruebas realizadas y tiempos medidos.]
