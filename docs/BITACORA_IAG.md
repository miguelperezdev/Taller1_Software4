# Bitácora de uso de IAG — Nivel 3 (Taller 1 ApexStore)

**Curso:** Ingeniería de Software IV (09775-TIC) · **Periodo:** 2026-2 · **NRC:** 10865
**Equipo:** Miguel · Danna Isabel
**Entregable:** Punto 4 — Implementación Java + ICE del diagrama corregido (Punto 3 → RA4)
**Nivel de asistencia:** Nivel 3 — Asistencia en revisión, crítica y opinión. El diseño, el código y las
pruebas son de autoría del equipo; la IA solo se usó como revisor/oponente técnico.

## 1. Alcance y límites declarados

**Sí se usó la IA para:**

- Revisar redacción propia (tablas D1-D5, Slice, clases Java) y señalar violaciones a SOLID/RAS.
- Opinar sobre alternativas (firma única vs. firmas por medio, sync vs. async oneway, upsert vs. doble escritura).
- Criticar el diagrama corregido y la redacción de pruebas antes de cerrar el informe.
- Sugerir casos borde y preguntas de sustentación.

**No se usó la IA para:**

- No se le pidió generar el taller, el código completo, ni los diagramas desde cero.
- No se le pidió redactar el informe final ni las conclusiones.
- No se ejecutó código sugerido por la IA sin entenderlo, probarlo y ajustarlo.
- Todo cambio quedó registrado abajo con archivo afectado y decisión humana.

## 2. Herramientas y versiones

| Herramienta | Versión / acceso | Uso asignado |
|---|---|---|
| DeepSeek Chat | DeepSeek-V3.1 (web, sep–oct 2026) | Revisor de diseño y de código Java/ICE. Se le pasaron fragmentos propios para crítica SOLID, concurrencia y RAS. |
| Claude Haiku 4.5 | Anthropic Claude Haiku 4.5 (web, sep–oct 2026) | Segundo revisor / opinión cruzada. Se usó para contrastar lo opinado por DeepSeek, pulir redacción técnica y proponer casos de prueba. |

**Método de trabajo:** el equipo redactaba primero (tabla, `.ice`, clase), luego pedía revisión a una
herramienta, ajustaba, y pedía opinión cruzada a la otra. Solo se aceptaba un cambio si tenía sentido
contra el enunciado (RAS-01…04, Puntos 1–4) y se podía evidenciar en código o en `docs/PRUEBAS.md`.

## 3. Avance por fases (prompts reales de revisión)

> Los prompts están transcritos en estilo de revisión/opinión. Se adjunta la idea central de la
> respuesta y la decisión humana con el archivo afectado.

### Fase 1 — Interpretación del diagrama original (Punto 1)

**P1 — DeepSeek — 24/09/2026**

> Te comparto mi tabla de defectos D1-D5 del diagrama original de ApexStore (firmas heterogéneas,
> Cripto que escribe directo a DB, DB con dos operaciones, despacho bloqueante, fallo que contamina).
> Para cada fila asigné un principio (OCP/DIP/LSP, SRP, ISP, disponibilidad, bulkhead). ¿Te parece
> correcta la asignación? ¿Qué matizarías sin reescribirme la tabla?

- **Respuesta (idea):** confirmó D1-D4; matizó D5: no es solo try/catch, es aislamiento por proxy
  y pools separados (bulkhead). Sugirió separar “disponibilidad” de “tolerancia a fallos”.
- **Decisión humana:** aceptado parcial. Se dividió D4 (ACK async, disponibilidad) de D5 (aislamiento
  por medio, tolerancia). Afectado: `apexstore-ice/docs/ARQUITECTURA.md §1`.

**P2 — Claude Haiku 4.5 — 25/09/2026**

> Te paso mi redacción del Punto 2d: “Cripto no debe persistir directo, debe notificar por callback
> y solo el contexto persiste”. ¿Ves algún vacío argumental frente al profesor? ¿Qué contraargumento
> me harías en sustentación?

- **Respuesta (idea):** preguntó “¿qué pasa si el callback se pierde?” y “¿dónde queda el ACK inicial?”.
- **Decisión humana:** aceptado. Se agregó ACK inicial PERSIST anti-huérfanos + ACTUALIZA del callback.
  Afectado: `slice/ApexStore.ice`, `db/TransaccionesDBImpl.java`.

### Fase 2 — Rediseño y contrato Slice (Punto 3)

**P3 — DeepSeek — 27/09/2026**

> Redacté este `.ice` con `EstrategiaPago.pagar(OrdenPago, PagoCallback*)` y `OrdenPago.datosPago`
> opaco. ¿La firma respeta LSP para Stripe/PSE/Cripto/Billetera? ¿El campo opaco sí sostiene
> ocultamiento de información o me falta precisar algo?

- **Respuesta (idea):** validó firma única; observó que `datosPago` debía documentarse como
  `tok_… / banco=… / wallet=…` por estrategia para que LSP fuera verificable.
- **Decisión humana:** aceptado. Se documentó el formato por medio en el `.ice` y en
  `docs/DIAGRAMA_CORREGIDO.md §3-regla 4`. Afectado: `slice/ApexStore.ice`.

**P4 — Claude Haiku 4.5 — 28/09/2026**

> Mi corrector C2 dice “prohibido escribir directo Estrategia→DB”. Te muestro mi tabla de elementos
> para Visual Paradigm. ¿Debería existir alguna flecha punteada Estrategia→DB o definitivamente
> ninguna? Dame tu opinión con criterio ISP/SRP.

- **Respuesta (idea):** “ninguna; si dejas una punteada el jurado la leerá como bypass. Toda escritura
  por `persistirTransaccion` del contexto”.
- **Decisión humana:** aceptado. Se eliminó toda flecha Estrategia→DB del mermaid y de la tabla.
  Afectado: `docs/DIAGRAMA_CORREGIDO.md`, `docs/ARQUITECTURA.md §5`.

### Fase 3 — Implementación Java + ICE (Punto 4, Nodos 3 y 4)

**P5 — DeepSeek — 29/09/2026**

> Te comparto mi `EstrategiaBase.java` (retorna ACK Pendiente y confirma en hilo propio con
> `notificarTransaccionExitosa` oneway). ¿Este diseño sí garantiza RAS-01 (no bloquear backend)
> y RAS-02 (P95 < 250 ms en despacho)? ¿Qué medirías para demostrarlo?

- **Respuesta (idea):** sí, si el `pagar()` solo hace log + dispatch y el trabajo pesado va al pool;
  pidió medir `despacho=…ms` en el contexto y ACK en el cliente.
- **Decisión humana:** aceptado. Se agregó medición `despacho=Xms` en
  `backend/ProcesadorPagosContextoImpl.java` y `ACK en Yms` en `client/ClienteApp.java`.
  Afectado: esos dos archivos + `docs/PRUEBAS.md §2`.

**P6 — Claude Haiku 4.5 — 30/09/2026**

> Revisa mi `TransaccionesDBImpl.java`: uso `synchronized` + mapa por `idOrden` con PERSIST del ACK
> y ACTUALIZA del callback. ¿Ves condición de carrera o doble cobro si llegan dos callbacks del
> mismo `idOrden`? ¿Qué prueba me recomiendas?

- **Respuesta (idea):** el upsert es idempotente si la clave es `idOrden`; recomendó probar doble
  callback y modo `fail` de PSE para evidenciar que no hay duplicado.
- **Decisión humana:** aceptado parcial. Se mantuvo `synchronized` (simulación mononodo, documentado
  como no distribuido) y se agregó caso T6 `fail` + reintento. Afectado:
  `gateways/EstrategiaPSEImpl.java`, `docs/PRUEBAS.md (T6)`.

### Fase 4 — Backend, cliente y pruebas (Nodos 2 y 1)

**P7 — DeepSeek — 01/10/2026**

> Mi `ProcesadorPagosContextoImpl` resuelve proxy por medio y aísla fallos con try/catch por pedido.
> ¿Es suficiente como bulkhead o debería separar pools por pasarela? Opina sin darme el código.

- **Respuesta (idea):** try/catch evita contaminación, pero el bulkhead real exige no compartir el
  hilo del contexto con el cobro; como el cobro ya va en pool de la pasarela, el diseño es suficiente
  para el alcance simulado; lo pidió declararlo así en el informe.
- **Decisión humana:** aceptado. Se redactó así en `docs/ARQUITECTURA.md §4 (RAS-01)` y en
  `apexstore-ice/README.md §5`. Sin cambio de código, solo precisión conceptual.

**P8 — Claude Haiku 4.5 — 02/10/2026**

> Te paso mi borrador de `PRUEBAS.md` (T1-T8) y mis salidas (ACK 4-86 ms, despacho 66 ms,
> AUDIT PERSIST/ACTUALIZA). ¿Crees que evidencian RAS-02 y RAS-03? ¿Qué fila quitarías o
> agregarías antes de entregar el PDF?

- **Respuesta (idea):** pidió conservar T6 (fail aislado) porque es la evidencia de tolerancia;
  sugirió tabla de troubleshooting (`ice-compat` vs `ice`, `ObjectNotExistException`, puertos).
- **Decisión humana:** aceptado. Se agregó `§3 Solución de problemas` y se conservaron T6/T7.
  Afectado: `docs/PRUEBAS.md`, `apexstore-ice/README.md §4`.

## 4. Cambios aceptados vs. rechazados

| # | Sugerencia de la IA | Decisión | Motivo humano |
|---|---|---|---|
| S1 | Separar D4/D5 (disponibilidad vs. bulkhead) | Aceptada | El enunciado evalúa Punto 2b y 2c por separado. |
| S2 | Documentar `datosPago` por medio | Aceptada | Hace verificable el ocultamiento (LSP). |
| S3 | Eliminar toda flecha Estrategia→DB | Aceptada | Cierra el Punto 2d sin ambigüedad. |
| S4 | Medir despacho y ACK en ms | Aceptada | Evidencia RAS-02 en `PRUEBAS.md`. |
| S5 | Usar DB distribuida real / transacciones XA | Rechazada | Fuera de alcance: simulación mononodo declarada en README. |
| S6 | Generar el informe en PDF directamente | Rechazada | El PDF lo arma el equipo en Visual Paradigm + Word/LaTeX. |
| S7 | Agregar más medios (Nequi, Daviplata) en código | Rechazada | Basta BilleteraDigital como prueba OCP; más medios dispersan la evidencia. |

## 5. Verificación humana (cómo se validó cada aporte)

1. **Contraste cruzado:** toda opinión de DeepSeek se contrastó con Claude Haiku 4.5 y viceversa.
2. **Compilación y ejecución real:** `gradle build`, `runDatabase`, `runGateways`, `runBackend`,
   `runClient --args="todos|fail"` — salidas en `docs/PRUEBAS.md §2`.
3. **Trazabilidad:** cada cambio aceptado tiene archivo afectado (ver Fase 3). Nada quedó solo en el chat.
4. **Límites del simulador declarados:** pools ICE por config, `Thread.sleep` + aleatoriedad controlada,
   `synchronized` mononodo, polling en cliente — ver `apexstore-ice/README.md §5` y `ARQUITECTURA.md §4`.

## 6. Declaración de autoría

Declaramos que el diagrama corregido (Punto 3), el contrato `ApexStore.ice`, las clases de los Nodos
1–4, los scripts y las pruebas fueron diseñados, escritos y ejecutados por el equipo. DeepSeek y
Claude Haiku 4.5 actuaron únicamente como revisores y opinantes, conforme al Nivel 3 exigido.
Conservamos los hilos de chat para auditoría del docente.
