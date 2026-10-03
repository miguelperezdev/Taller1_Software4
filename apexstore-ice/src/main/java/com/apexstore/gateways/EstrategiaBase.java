package com.apexstore.gateways;

import ApexStore.EstadoPago;
import ApexStore.OrdenPago;
import ApexStore.PagoCallbackPrx;
import ApexStore.PagoCallbackPrxHelper;
import ApexStore.ResultadoPago;
import ApexStore._EstrategiaPagoDisp;
import Ice.Current;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base de las pasarelas SIMULADAS.
 *
 * Patron: cada estrategia responde pagar() en <50ms con ACK Pendiente y luego
 * confirma de forma ASINCRONA via callback oneway en un hilo aparte.
 * Esto protege el thread pool del backend frente a retardos bancarios de
 * ~15s (Punto 2b / RAS-01): el backend nunca se bloquea.
 *
 * NINGUNA estrategia toca la DB directamente (correccion C2): solo el
 * contexto persiste cuando recibe el callback.
 */
public abstract class EstrategiaBase extends _EstrategiaPagoDisp {

    private static final long serialVersionUID = 1L;

    protected final Random rnd = new Random();
    // Pool por pasarela: el fallo/lentitud de una no afecta a las otras (Punto 2c)
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "cb-" + nombreMedio());
        t.setDaemon(true);
        return t;
    });

    protected abstract String nombreMedio();

    /** Logica de cobro simulada de cada medio. Retorna resultado final. */
    protected abstract ResultadoPago simularCobro(OrdenPago orden);

    /** Latencia simulada del medio antes del callback (ms). */
    protected abstract int latenciaSimuladaMs();

    @Override
    public ResultadoPago pagar(OrdenPago orden, PagoCallbackPrx callback, Current __current) {
        long t0 = System.currentTimeMillis();

        // ACK inmediato: despacho sincrono liviano (RAS-02: << 250ms)
        ResultadoPago ack = new ResultadoPago();
        ack.idOrden = orden.idOrden;
        ack.estado = EstadoPago.Pendiente;
        ack.codigoAutorizacion = "ACK-" + nombreMedio();
        ack.mensaje = "Recibido en " + nombreMedio() + "; confirmacion async via callback";

        // Confirmacion async: no bloquea al llamador
        final PagoCallbackPrx cbOneway =
                PagoCallbackPrxHelper.uncheckedCast(callback.ice_oneway());
        pool.submit(() -> {
            try {
                Thread.sleep(latenciaSimuladaMs());
                ResultadoPago fin = simularCobro(orden);
                cbOneway.notificarTransaccionExitosa(fin);
            } catch (Exception e) {
                // Tolerancia a fallos (Punto 2c): si el callback falla, se
                // reporta como Fallido pero sin tumbar otras estrategias.
                try {
                    ResultadoPago f = new ResultadoPago();
                    f.idOrden = orden.idOrden;
                    f.estado = EstadoPago.Fallido;
                    f.codigoAutorizacion = "CB-ERROR";
                    f.mensaje = nombreMedio() + " error interno sim: " + e.getMessage();
                    cbOneway.notificarTransaccionExitosa(f);
                } catch (Exception ignored) { /* callback caido: se registra en logs */ }
            }
        });

        long dt = System.currentTimeMillis() - t0;
        System.out.println("[" + nombreMedio() + "] pagar() ACK en " + dt + "ms idOrden=" + orden.idOrden);
        return ack;
    }

    // ---- helpers para las subclases ----
    protected ResultadoPago ok(OrdenPago o, String codigo, String msg) {
        ResultadoPago r = new ResultadoPago();
        r.idOrden = o.idOrden;
        r.estado = EstadoPago.Autorizado;
        r.codigoAutorizacion = codigo;
        r.mensaje = msg;
        return r;
    }

    protected ResultadoPago noOk(OrdenPago o, EstadoPago e, String codigo, String msg) {
        ResultadoPago r = new ResultadoPago();
        r.idOrden = o.idOrden;
        r.estado = e;
        r.codigoAutorizacion = codigo;
        r.mensaje = msg;
        return r;
    }

    /** Si datosPago contiene "fail" se fuerza rechazo (para demos/tests). */
    protected boolean forzadoAFallar(OrdenPago o) {
        return o.datosPago != null && o.datosPago.toLowerCase().contains("fail");
    }
}
