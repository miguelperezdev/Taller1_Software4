package com.apexstore.backend;

import ApexStore.OrdenPago;
import ApexStore.ProcesadorPagosPrx;
import ApexStore.ProcesadorPagosPrxHelper;
import ApexStore.ResultadoPago;
import ApexStore._ServicioCheckoutDisp;
import Ice.Current;
import com.apexstore.common.ApexConfig;

/**
 * Nodo 2 — ServicioCheckout (fachada para Nodo 1).
 * Provee gestionarCompra; delega el pago al contexto via iniciarPagoOrden.
 */
public class ServicioCheckoutImpl extends _ServicioCheckoutDisp {

    private static final long serialVersionUID = 1L;

    private final Ice.Communicator ic;

    public ServicioCheckoutImpl(Ice.Communicator ic) {
        this.ic = ic;
    }

    private ProcesadorPagosPrx procesador() {
        Ice.ObjectPrx base = ic.stringToProxy(
                ApexConfig.PROCESADOR_IDENTITY + ":" + ApexConfig.BACKEND_ENDPOINT);
        return ProcesadorPagosPrxHelper.checkedCast(base);
    }

    @Override
    public ResultadoPago gestionarCompra(OrdenPago orden, Current __current) {
        System.out.println("[Checkout] gestionarCompra idOrden=" + orden.idOrden
                + " cliente=" + orden.clienteId + " medio=" + orden.medio);
        // Validacion minima de borde (el cobro real lo simula la pasarela)
        if (orden.monto <= 0) {
            ResultadoPago r = new ResultadoPago();
            r.idOrden = orden.idOrden;
            r.estado = ApexStore.EstadoPago.Rechazado;
            r.codigoAutorizacion = "CHECKOUT-400";
            r.mensaje = "Monto invalido";
            return r;
        }
        return procesador().iniciarPagoOrden(orden);
    }

    @Override
    public ResultadoPago consultarCompra(String idOrden, Current __current) {
        return procesador().consultarEstado(idOrden);
    }
}
