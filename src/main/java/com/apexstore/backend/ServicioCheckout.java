package com.apexstore.backend;

import ApexStore.EstadoPago;
import ApexStore.IProcesadorPagosPrx;
import ApexStore.OrdenNoEncontrada;
import ApexStore.OrdenPago;
import ApexStore.ResultadoPago;
import ApexStore._IGestionComprasDisp;
import Ice.Current;
import com.apexstore.comun.Consola;

/**
 * Fachada del backend hacia WebApp y MobileApp. Valida la orden y la delega al procesador;
 * los clientes no conocen el procesador, las estrategias ni la base de datos.
 */
public class ServicioCheckout extends _IGestionComprasDisp {

    private static final long serialVersionUID = 1L;

    private final IProcesadorPagosPrx procesador;

    public ServicioCheckout(IProcesadorPagosPrx procesador) {
        this.procesador = procesador;
    }

    @Override
    public ResultadoPago gestionarCompra(OrdenPago orden, Current current) {
        String error = validar(orden);
        if (error != null) {
            Consola.info("checkout", "orden %s rechazada: %s", orden.idOrden, error);
            return new ResultadoPago(orden.idOrden, EstadoPago.Rechazado, "ORDEN-INVALIDA", error);
        }
        Consola.info("checkout", "orden %s de %s por %d %s con %s", orden.idOrden, orden.clienteId, orden.monto,
                orden.moneda, orden.medio);
        return procesador.iniciarPagoOrden(orden);
    }

    @Override
    public ResultadoPago consultarCompra(String idOrden, Current current) throws OrdenNoEncontrada {
        return procesador.consultarEstado(idOrden);
    }

    private static String validar(OrdenPago orden) {
        if (vacio(orden.idOrden)) {
            return "La orden no tiene identificador";
        }
        if (vacio(orden.clienteId)) {
            return "La orden no tiene cliente";
        }
        if (vacio(orden.medio)) {
            return "No se indicó el medio de pago";
        }
        if (orden.monto <= 0) {
            return "El monto debe ser mayor que cero";
        }
        if (vacio(orden.moneda)) {
            return "No se indicó la moneda";
        }
        return null;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
