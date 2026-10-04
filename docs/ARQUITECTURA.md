# Del diagrama corregido al código (Puntos 3 y 4)

## 1. Nodos, artefactos y componentes

Cada nodo del diagrama de despliegue es un programa independiente y cada artefacto es el jar
que se instala en ese nodo.

| Nodo del diagrama | Artefacto | Clase principal | Componentes (clases) |
|---|---|---|---|
| Computador / Smartphone del Cliente | `cliente.jar` | `cliente.ClienteApp` | WebApp y MobileApp (simuladas por `ClienteApp`) |
| Servidor E-Commerce (Backend Core) | `backend.jar` | `backend.ServidorECommerce` | `ServicioCheckout`, `ProcesadorPagosContexto`, `ReceptorResultadosPagos` |
| Servidor de Pasarelas y Estrategias de Pago | `pagos.jar` | `pagos.ServidorPasarelas` | `EstrategiaStripe`, `EstrategiaPSE`, `EstrategiaCripto`, `EstrategiaBilleteraDigital` |
| Servidor Base de Datos Transaccional | `persistencia.jar` | `persistencia.ServidorBaseDatos` | `ServidorPersistencia` + esquema `apexstore_tx` (JDBC) |

Los caminos de comunicación del diagrama son los proxies de `config/`: el cliente solo conoce el
backend, el backend conoce las pasarelas y la base de datos, y las pasarelas solo conocen el
receptor del backend (por el proxy que reciben en cada pago). No hay conexión entre el nodo de
pagos y el de base de datos.

## 2. Interfaces

| Interfaz (diagrama y Slice) | La provee | La requiere | Operaciones |
|---|---|---|---|
| `IGestionCompras` | ServicioCheckout | WebApp, MobileApp | `gestionarCompra`, `consultarCompra` |
| `IProcesadorPagos` | ProcesadorPagosContexto | ServicioCheckout | `iniciarPagoOrden`, `consultarEstado` |
| `IEstrategiaPago` | las 4 estrategias | ProcesadorPagosContexto | `pagar(orden, callback)` |
| `INotificacionPago` | ReceptorResultadosPagos | las 4 estrategias | `notificarResultadoPago` |
| `IPersistenciaTransaccional` | ServidorPersistencia | ProcesadorPagosContexto, ReceptorResultadosPagos | `registrarOrden`, `actualizarEstado`, `consultarTransaccion` |

Cada componente depende de la interfaz del otro y no de su clase, incluso dentro del mismo nodo:
`ServidorECommerce` publica los tres componentes en el adaptador ICE y les pasa proxies.

## 3. Correcciones del Punto 1 y dónde están en el código

| Mal uso en el diagrama original | Corrección | Código |
|---|---|---|
| Tres interfaces distintas para las estrategias; el contexto conocía cada medio | Una sola `IEstrategiaPago`; los datos del medio viajan como token opaco en `OrdenPago.datosPago` | `ApexStore.ice`, `EstrategiaPagoBase` |
| Dependencia circular contexto ↔ estrategias por `notificarTransaccionExitosa` | El callback lo provee un componente aparte; las dependencias quedan contexto → estrategias → receptor → persistencia | `ReceptorResultadosPagos` |
| El callback solo cubría el éxito | `notificarResultadoPago` informa aprobado, rechazado o en verificación | `INotificacionPago` |
| EstrategiaCripto escribía directo en la base de datos | Cripto notifica igual que las demás; ninguna estrategia conoce la persistencia | `EstrategiaCripto` |
| El cliente no tenía cómo conocer el resultado | `consultarCompra` / `consultarEstado` | `ServicioCheckout`, `ClienteApp` |
| El contexto debía modificarse por cada medio nuevo | Las estrategias se buscan por configuración (`Pagos.<medio>.Proxy`) | `ProcesadorPagosContexto.estrategiaPara` |

## 4. Flujo de un pago

1. `ClienteApp` llama `gestionarCompra(orden)` en `ServicioCheckout`, que valida la orden.
2. `ProcesadorPagosContexto` busca la estrategia del medio y registra la orden como Pendiente
   (`registrarOrden`). Si la orden ya existía, devuelve su estado sin volver a cobrar.
3. El contexto llama `pagar(orden, receptor)` con un tiempo máximo de 2 s. La estrategia valida
   el token, deja el cobro en su propio pool de hilos y responde Pendiente en pocos milisegundos.
4. Cuando la pasarela simulada responde, la estrategia llama `notificarResultadoPago` en el
   receptor, que guarda el nuevo estado con `actualizarEstado`. Si la notificación falla se
   reintenta hasta 3 veces; como es idempotente, repetirla no cambia nada.
5. El cliente consulta con `consultarCompra` hasta ver Aprobado o Rechazado.

## 5. Requerimientos arquitectónicos

**RAS-01 (disponibilidad).** Ningún hilo del backend espera al banco: `pagar` solo devuelve un
acuse y el resultado llega por callback. Cada estrategia tiene su propio pool, así una pasarela
lenta no ocupa los hilos de las otras. El contexto tiene un circuit breaker por medio
(`InterruptorCircuito`): tras 3 fallos seguidos deja de intentar ese medio durante 15 s y responde
de inmediato.

**RAS-02 (latencia).** El despacho interno mide su tiempo y lo registra (`contexto ... en N ms`);
en las pruebas locales está entre 2 y 50 ms, lejos de los 250 ms del P95. El tiempo máximo de 2 s
sobre `pagar` acota el peor caso.

**RAS-03 (integridad).** La orden se registra antes de cobrar, así nunca existe un cobro sin orden.
`idOrden` es la llave primaria: una orden repetida no se registra ni se cobra otra vez. Cada cambio
de estado es una transacción JDBC que bloquea la fila (`SELECT ... FOR UPDATE`), valida la
transición (un pago aprobado no vuelve a pendiente) y escribe la tabla `auditoria`. Si la pasarela
no responde a tiempo, el pago queda EnVerificacion en lugar de fallido, porque pudo haberse cobrado.
Los datos del medio de pago no se guardan en la base de datos.

**RAS-04 (extensibilidad).** `EstrategiaBilleteraDigital` se agregó con una clase nueva, una línea
en `ServidorPasarelas` y una en `backend.config`, sin tocar el contexto, las otras estrategias ni
el contrato Slice.

## 6. Diferencias deliberadas con el diagrama

- Entre clientes y backend el diagrama indica HTTPS. El cliente de prueba usa ICE sobre TCP porque
  es una aplicación Java; en producción ese camino sería ICE sobre WSS/TLS o HTTPS.
- La base de datos por defecto es H2 en memoria en modo PostgreSQL para que la demo no requiera
  instalar nada. Con `BaseDatos.Url` se apunta al PostgreSQL del diagrama sin cambiar código.
- Una orden que queda EnVerificacion sin notificación posterior requiere conciliación con la
  pasarela; ese proceso está fuera del alcance de la tarea.
