# Taller 1 — ApexStore · Ingeniería de Software IV (09775-TIC)

Implementación en **Java + ICE** del diagrama arquitectónico corregido (Punto 3 → Punto 4, RA4).

**Equipo:** Miguel · Danna Isabel
**Periodo:** 2026-2 · **NRC:** 10865

## Contenido del repo

```
.
├── docs/
│   ├── Tarea_1_Interpretacion_Diseno.pdf  # Enunciado oficial (Puntos 1–4 + RAS)
│   └── Diagrama_ApexStore.pdf             # Diagrama para el informe (Visual Paradigm)
├── apexstore-ice/                          # Proyecto Java + ICE (Punto 4)
│   ├── slice/ApexStore.ice                 # Contrato Slice unificado
│   ├── src/main/java/com/apexstore/         # Nodos 1–4 (common, db, gateways, backend, client)
│   ├── config/                             # Configs ICE por nodo (db, gateways, backend, client)
│   ├── scripts/                            # run-all.sh / stop-all.sh
│   ├── docs/                               # ARQUITECTURA, DIAGRAMA_CORREGIDO, PRUEBAS, BITACORA_IAG
│   ├── build.gradle / settings.gradle      # ice-compat 3.7.10 + tarea generateSlice
│   └── README.md                           # Guía completa del proyecto
├── .gitignore                              # Único gitignore (raíz, cubre build/ y generados)
└── README.md                               # Este archivo
```

## Inicio rápido (5 min)

Requisitos: `java 11+`, `slice2java 3.7.x`, `gradle 8.x`, internet (Maven Central).

```bash
cd apexstore-ice
gradle build          # genera código Slice + compila
bash scripts/run-all.sh   # levanta DB → Gateways → Backend → demo cliente
```

Detalle completo, topología, evidencias y solución de problemas: ver
**[`apexstore-ice/README.md`](apexstore-ice/README.md)** y
**[`apexstore-ice/docs/`](apexstore-ice/docs/)**.

## Documentación

| Documento | Contenido |
|---|---|
| `docs/Tarea_1_Interpretacion_Diseno.pdf` | Enunciado: RAS-01…04, Puntos 1–4 |
| `docs/Diagrama_ApexStore.pdf` | Diagrama exportado de Visual Paradigm para el informe |
| `apexstore-ice/README.md` | Guía del proyecto: estructura, ejecución, RAS, bitácora IAG |
| `apexstore-ice/docs/ARQUITECTURA.md` | Defectos Punto 1 → correcciones Punto 3 → mapeo a código |
| `apexstore-ice/docs/DIAGRAMA_CORREGIDO.md` | Diagrama corregido (referencia para Visual Paradigm) |
| `apexstore-ice/docs/PRUEBAS.md` | Plan de pruebas + evidencias reales de ejecución |
| `apexstore-ice/docs/BITACORA_IAG.md` | Bitácora IAG Nivel 3 (DeepSeek + Claude Haiku 4.5, solo revisión) |

## Entregable (PDF)

Un único PDF `INGESOFT IV 20262 T1 Apellido1 Apellido2 Apellido3.pdf` con el
informe (Puntos 1–4), diagramas referenciados, fragmentos de código en bloques
monoespaciados y el anexo **Bitácora de uso de IAG** (Nivel 3, obligatoria).
