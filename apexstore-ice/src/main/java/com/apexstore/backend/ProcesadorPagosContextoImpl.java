package com.apexstore.backend;

import ApexStore.EstrategiaPagoPrx;
import ApexStore.EstrategiaPagoPrxHelper;
import ApexStore.EstadoPago;
import ApexStore.MedioPago;
import ApexStore.OrdenPago;
import ApexStore.PagoCallbackPrx;
import ApexStore.PagoCallbackPrxHelper;
import ApexStore.PersistenciaTransaccionalPrx;
import ApexStore.PersistenciaTransaccionalPrxHelper;
import ApexStore.ResultadoPago;
import ApexStore._PagoCallbackDisp;
import ApexStore._ProcesadorPagosDisp;
import Ice.Current;
import com.apexstore.common.ApexConfig;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nodo 2 — ProcesadorPagosContexto [Strategy Context] + receptor de callbacks.
 *
 * Responsabilidades (SRP):
 *  1. Seleccionar la estrategia segun orden.medio (Strategy).
 *  2. Despachar pagar() con timeout/try-catch aislado por medio (Punto 2c).
 *  3. Recibir notificarTransaccionExitosa() y persistir UNA sola vez (C2/C3).
 *  4. Persistir el ACK Pendiente de inmediato (no pagos huerfanos, RAS-03).
 *
 * El contexto NUNCA se bloquea mas de ~ms: pagar() retorna ACK rapido y el
 * resultado final llega async (RAS-01/RAS-02).
 */
public class ProcesadorPagosContextoImpl extends _ProcesadorPagosDisp {

    private static final long serialVersionUID = 1L;

    private final Ice.Communicator ic;
    private final String callbackProxyStr; // proxy oneway hacia si mismo
    private final Map<MedioPago, String> estrategiaPorMedio = new EnumMap<>(MedioPago.class);
    private final ConcurrentHashMap<String, OrdenPago> ordenes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResultadoPago> estados = new ConcurrentHashMap<>();

    public ProcesadorPagosContextoImpl(Ice.Communicator ic) {
        this.ic = ic;
        this.callbackProxyStr = ApexConfig.CALLBACK_IDENTITY + ":"
                + ApexConfig.BACKEND_ENDPOINT;
        // OCP: agregar un medio = 1 linea aqui + 1 servant en Nodo 3.
        estrategiaPorMedio.put(MedioPago.Stripe, ApexConfig.STRIPE_IDENTITY + ":" + ApexConfig.GATEWAYS_ENDPOINT);
        estrategiaPorMedio.put(MedioPago.PSE, ApexConfig.PSE_IDENTITY + ":" + ApexConfig.GATEWAYS_ENDPOINT);
        estrategiaPorMedio.put(MedioPago.Cripto, ApexConfig.CRIPTO_IDENTITY + ":" + ApexConfig.GATEWAYS_ENDPOINT);
        estrategiaPorMedio.put(MedioPago.BilleteraDigital, ApexConfig.BILLETERA_IDENTITY + ":" + ApexConfig.GATEWAYS_ENDPOINT);
    }

    private PersistenciaTransaccionalPrx db() {
        Ice.ObjectPrx base = ic.stringToProxy(
                ApexConfig.DB_IDENTITY + ":" + ApexConfig.DB_ENDPOINT);
        return PersistenciaTransaccionalPrxHelper.checkedCast(base);
    }

    private PagoCallbackPrx selfCallback() {
        Ice.ObjectPrx base = ic.stringToProxy(callbackProxyStr);
        return PagoCallbackPrxHelper.uncheckedCast(base);
    }

    @Override
    public ResultadoPago iniciarPagoOrden(OrdenPago orden, Current __current) {
        long t0 = System.currentTimeMillis();
        ordenes.put(orden.idOrden, orden);

        String target = estrategiaPorMedio.get(orden.medio);
        if (target == null) {
            return fallido(orden, "CTX-400", "Medio no soportado: " + orden.medio);
        }
        try {
            Ice.ObjectPrx base = ic.stringToProxy(target);
            EstrategiaPagoPrx estrategia = EstrategiaPagoPrxHelper.checkedCast(base);
            if (estrategia == null) {
                return fallido(orden, "CTX-503", "Estrategia no disponible: " + orden.medio);
            }
            // Despacho aislado: el fallo de UNA estrategia no afecta a otras.
            ResultadoPago ack = estrategia.pagar(orden, selfCallback());
            estados.put(orden.idOrden, ack);
            db().persistirTransaccion(ack, orden); // traza inicial anti-huerfanos
            long dt = System.currentTimeMillis() - t0;
            System.out.println("[Contexto] iniciarPagoOrden idOrden=" + orden.idOrden
                    + " medio=" + orden.medio + " despacho=" + dt + "ms (objetivo<"
                    + ApexConfig.P95_OBJETIVO_MS + "ms)");
            return ack;
        } catch (Ice.Exception e) {
            System.out.println("[Contexto] FALLO aislado medio=" + orden.medio + ": " + e);
            return fallido(orden, "CTX-500", "Fallo de pasarela " + orden.medio + ": " + e.getMessage());
        }
    }

    @Override
    public ResultadoPago consultarEstado(String idOrden, Current __current) {
        ResultadoPago r = estados.get(idOrden);
        if (r != null) return r;
        try {
            return db().consultarTransaccion(idOrden);
        } catch (Exception e) {
            return fallido(idOrden, "CTX-DB-ERR", "No se pudo consultar: " + e.getMessage());
        }
    }

    /** Servant interno del callback: el adapter registra esta misma clase
     *  tambien como PagoCallback (ver BackendServer). */
    public static class CallbackServant extends _PagoCallbackDisp {
        private static final long serialVersionUID = 1L;
        private final ProcesadorPagosContextoImpl ctx;
        public CallbackServant(ProcesadorPagosContextoImpl ctx) { this.ctx = ctx; }

        @Override
        public void notificarTransaccionExitosa(ResultadoPago resultado, Current __current) {
            ctx.onCallback(resultado);
        }
    }

    void onCallback(ResultadoPago resultado) {
        System.out.println("[Contexto] callback idOrden=" + resultado.idOrden
                + " estado=" + resultado.estado + " cod=" + resultado.codigoAutorizacion);
        estados.put(resultado.idOrden, resultado);
        OrdenPago orden = ordenes.get(resultado.idOrden);
        if (orden == null) {
            // Defensa RAS-03: callback huerfano -> se persiste igual para auditoria
            orden = new OrdenPago();
            orden.idOrden = resultado.idOrden;
            orden.clienteId = "desconocido";
            orden.moneda = "?";
            orden.datosPago = "";
        }
        try {
            db().persistirTransaccion(resultado, orden); // upsert idempotente
        } catch (Exception e) {
            System.out.println("[Contexto] ERROR persistiendo callback: " + e);
        }
        // Aqui se enviaria la confirmacion al cliente (push/websocket/polling).
        System.out.println("[Contexto] orden " + resultado.idOrden + " actualizada; cliente puede hacer consultarCompra()");
    }

    private ResultadoPago fallido(OrdenPago o, String cod, String msg) {
        ResultadoPago r = new ResultadoPago();
        r.idOrden = o.idOrden;
        r.estado = EstadoPago.Fallido;
        r.codigoAutorizacion = cod;
        r.mensaje = msg;
        estados.put(o.idOrden, r);
        return r;
    }

    private ResultadoPago fallido(String idOrden, String cod, String msg) {
        OrdenPago o = new OrdenPago();
        o.idOrden = idOrden;
        return fallido(o, cod, msg);
    }
}
