// ============================================================================
// ApexStore — Contrato Slice UNIFICADO (diagrama corregido Punto 3)
// Middleware: ICE 3.7 (compat mapping) — slice2java --compat
// ============================================================================
// Correcciones aplicadas respecto al diagrama original:
//  C1. Firmas heterogeneas (autorizarCargoStripe / debitarTransferenciaPSE /
//      generarCobroCriptoBtc) -> UNA sola interfaz EstrategiaPago.pagar().
//      (OCP/DIP/LSP + ocultamiento de informacion, RAS-04)
//  C2. Cripto escribia directo a DB (persistirTransaccionExitosa) bypaseando
//      al contexto -> ELIMINADO. Toda estrategia notifica via PagoCallback y
//      solo el contexto persiste. (SRP, RAS-03 ACID, Punto 2d)
//  C3. DB exponia 2 operaciones distintas -> UNA sola: persistirTransaccion.
//  C4. Despacho bloqueante -> pagar() retorna ACK Pendiente en <50ms y el
//      cobro real se confirma async con notificarTransaccionExitosa oneway.
//      (RAS-01 disponibilidad, RAS-02 P95 < 250ms, Punto 2b)
// ============================================================================

module ApexStore {

    enum EstadoPago {
        Pendiente,
        Autorizado,
        Rechazado,
        Fallido,
        Reembolsado
    };

    enum MedioPago {
        Stripe,
        PSE,
        Cripto,
        BilleteraDigital
    };

    // Orden agnostica al medio: datosPago es opaco (token/banco/wallet) para
    // preservar ocultamiento de informacion. Cada estrategia lo interpreta.
    struct OrdenPago {
        string idOrden;
        MedioPago medio;
        double monto;
        string moneda;
        string clienteId;
        string datosPago;
    };

    struct ResultadoPago {
        string idOrden;
        EstadoPago estado;
        string codigoAutorizacion;
        string mensaje;
    };

    // Callback async invocado por las pasarelas ( Patron Observer via ICE ).
    // Se declara oneway: la pasarela no se bloquea esperando al contexto.
    interface PagoCallback {
        ["oneway"] void notificarTransaccionExitosa(ResultadoPago resultado);
    };

    // Interfaz ESTRATEGIA comun ( Patron Strategy ). Todas las pasarelas
    // implementan EXACTAMENTE esta firma. Agregar un medio nuevo = crear una
    // clase mas que implemente esta interfaz, sin tocar el contexto (OCP).
    interface EstrategiaPago {
        // Retorna ACK inmediato (normalmente Pendiente). La confirmacion
        // final llega despues via callback. SIMULADO: sin red bancaria real.
        ResultadoPago pagar(OrdenPago orden, PagoCallback* callback);
    };

    // Contexto Strategy (Nodo 2). Selecciona estrategia segun orden.medio.
    interface ProcesadorPagos {
        ResultadoPago iniciarPagoOrden(OrdenPago orden);
        ResultadoPago consultarEstado(string idOrden);
    };

    // Fachada del backend hacia el front (Nodo 1 -> Nodo 2, HTTPS en el
    // diagrama; aqui TCP ICE local).
    interface ServicioCheckout {
        ResultadoPago gestionarCompra(OrdenPago orden);
        ResultadoPago consultarCompra(string idOrden);
    };

    // Persistencia UNICA (Nodo 4). Un solo metodo para todos los medios.
    // Garantia ACID simulada: atomicidad + idempotencia (no doble cobro,
    // no pagos huerfanos) + log de auditoria.
    interface PersistenciaTransaccional {
        bool persistirTransaccion(ResultadoPago resultado, OrdenPago orden);
        ResultadoPago consultarTransaccion(string idOrden);
    };
};
