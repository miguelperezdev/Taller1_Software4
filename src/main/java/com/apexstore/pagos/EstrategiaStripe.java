package com.apexstore.pagos;

import ApexStore.OrdenPago;
import ApexStore.ResultadoPago;

/**
 * Pago con tarjeta. La tarjeta se tokeniza en el cliente (tok_...), así el número y el CVC
 * nunca llegan a ApexStore (PCI-DSS).
 */
public class EstrategiaStripe extends EstrategiaPagoBase {

    private static final long serialVersionUID = 1L;

    public EstrategiaStripe() {
        super("stripe");
    }

    @Override
    protected String validar(String datosPago) {
        return datosPago.startsWith("tok_") ? null : "Stripe solo recibe tarjetas tokenizadas (tok_...)";
    }

    @Override
    protected ResultadoPago cobrar(OrdenPago orden) {
        if (orden.datosPago.contains("rechazada")) {
            return rechazado(orden, "DECLINADA", "El emisor declinó la tarjeta");
        }
        return aprobado(orden, "Cargo autorizado");
    }

    @Override
    protected long latenciaSimulada() {
        return entre(200, 700);
    }
}
