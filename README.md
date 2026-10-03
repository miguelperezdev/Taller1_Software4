# Taller 1 — ApexStore · Ingeniería de Software IV (09775-TIC)

Implementación en **Java + ICE** del diagrama arquitectónico corregido (Punto 3 → Punto 4, RA4).

**Equipo:** Miguel · Danna Isabel
**Periodo:** 2026-2 · **NRC:** 10865
**Middleware:** ICE 3.7.10 (compat mapping, `slice2java --compat`) · **Build:** Gradle · **Java:** 11+ (probado con 21)

> Las pasarelas de pago son **100% simuladas**: ningún código sale a redes
> bancarias/blockchain reales. Los retardos y fallos son `Thread.sleep` +
> aleatoriedad controlada para demostrar tolerancia a fallos.

## Contenido del repo

```
.
├── slice/ApexStore.ice            # Contrato Slice UNIFICADO (diseño corregido Punto 3)
├── src/main/java/com/apexstore/
│   ├── common/ApexConfig.java     # Endpoints, identidades, P95 objetivo
│   ├── db/                        # NODO 4: TransaccionesDBImpl, DatabaseServer (:10000)
│   ├── gateways/                  # NODO 3: EstrategiaBase + Stripe/PSE/Cripto/Billetera, GatewaysServer (:10001)
│   ├── backend/                   # NODO 2: ServicioCheckoutImpl, ProcesadorPagosContextoImpl, BackendServer (:10002)
│   └── client/                    # NODO 1: ClienteApp (WebApp/MobileApp simulados)
├── config/                        # db/gateways/backend/client.config (pools y timeouts ICE)
├── scripts/                       # run-all.sh (levanta todo) / stop-all.sh (detiene nodos)
├── docs/                          # Documentación del taller
│   ├── Tarea_1_Interpretacion_Diseno.pdf  # Enunciado oficial (Puntos 1–4 + RAS)
│   ├── Diagrama_ApexStore.pdf             # Diagrama exportado de Visual Paradigm
│   ├── ARQUITECTURA.md                    # Defectos Punto 1 → correcciones Punto 3 → mapeo a código
│   ├── DIAGRAMA_CORREGIDO.md              # Referencia para redibujar en Visual Paradigm + mermaid
│   ├── PRUEBAS.md                         # Plan T1-T8 + evidencias reales + troubleshooting
│   └── BITACORA_IAG.md                    # Bitácora IAG Nivel 3 (DeepSeek + Claude Haiku 4.5)
├── build.gradle / settings.gradle # ice-compat 3.7.10 + tarea generateSlice + tareas por nodo
└── README.md                      # Este archivo
```

## Topología (4 nodos del diagrama → 3 JVM + cliente)

| Nodo diagrama | Proceso JVM | Puerto | Identidades ICE |
|---|---|---|---|
| N1 Front-End | `ClienteApp` | — (solo llama) | — |
| N2 Backend Core | `BackendServer` | 10002 | `ServicioCheckout`, `ProcesadorPagos`, `ProcesadorCallback` |
| N3 Pasarelas | `GatewaysServer` | 10001 | `EstrategiaStripe`, `EstrategiaPSE`, `EstrategiaCripto`, `EstrategiaBilletera` |
| N4 DB | `DatabaseServer` | 10000 | `PersistenciaDB` |

Flujo: `Cliente --gestionarCompra--> Checkout --iniciarPagoOrden--> Contexto
--pagar--> Estrategia --(async oneway)--> ProcesadorCallback --persistir--> DB`.

## Inicio rápido (5 min)

Requisitos: `java 11+`, `slice2java 3.7.x`, `gradle 8.x`, internet (Maven Central).

```bash
gradle build          # genera código Slice + compila
bash scripts/run-all.sh   # levanta DB → Gateways → Backend → demo cliente
```

Ejecución por nodos:

```bash
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

# Detener todo:
bash scripts/stop-all.sh
```

Orden de encendido obligatorio: **DB → Gateways → Backend → Cliente**
(el backend resuelve proxies de DB y pasarelas al recibir el primer pedido).

Gradle descarga `com.zeroc:ice-compat:3.7.10` de Maven Central automáticamente
(runtime del mapping compat generado con `slice2java --compat`).

## Qué demuestra cada requisito (resumen)

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

## Agregar un medio nuevo (p. ej. Nequi) — 3 pasos

1. Crear `EstrategiaNequiImpl extends EstrategiaBase` (copiar `EstrategiaBilleteraImpl`).
2. Registrarla en `GatewaysServer` + identidad en `ApexConfig`.
3. Añadir 1 línea en el mapa `estrategiaPorMedio` del contexto + valor en el
   `enum MedioPago` del `.ice` (regenerar con `gradle generateSlice`).

## Fragmento de código para el informe (Punto 4)

```java
// Strategy uniforme: todas las pasarelas implementan la MISMA firma
ResultadoPago ack = estrategia.pagar(orden, selfCallback()); // < 50 ms
// ...y confirman async sin bloquear:
cbOneway.notificarTransaccionExitosa(resultadoFinal);        // oneway
```

## Documentación

| Documento | Contenido |
|---|---|
| `docs/Tarea_1_Interpretacion_Diseno.pdf` | Enunciado: RAS-01…04, Puntos 1–4 |
| `docs/Diagrama_ApexStore.pdf` | Diagrama exportado de Visual Paradigm para el informe |
| `docs/ARQUITECTURA.md` | Defectos Punto 1 → correcciones Punto 3 → mapeo a código |
| `docs/DIAGRAMA_CORREGIDO.md` | Diagrama corregido (referencia para Visual Paradigm) |
| `docs/PRUEBAS.md` | Plan de pruebas + evidencias reales de ejecución |
| `docs/BITACORA_IAG.md` | Bitácora IAG Nivel 3 (DeepSeek + Claude Haiku 4.5, solo revisión) |

## Entregable (PDF)

Un único PDF `INGESOFT IV 20262 T1 Apellido1 Apellido2 Apellido3.pdf` con el
informe (Puntos 1–4), diagramas referenciados, fragmentos de código en bloques
monoespaciados y el anexo **Bitácora de uso de IAG** (Nivel 3, obligatoria).
Ver [`docs/BITACORA_IAG.md`](docs/BITACORA_IAG.md).
