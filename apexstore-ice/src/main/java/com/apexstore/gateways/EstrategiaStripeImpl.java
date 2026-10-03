package com.apexstore.gateways;

import ApexStore.OrdenPago;
import ApexStore.EstadoPago;
import ApexStore.ResultadoPago;

/** Nodo 3 — EstrategiaStripe SIMULADA (sin red bancaria real). */
public class EstrategiaStripeImpl extends EstrategiaBase {
    private static final long serialVersionUID = 1L;

    @Override protected String nombreMedio() { return "Stripe"; }

    @Override protected int latenciaSimuladaMs() { return 150 + rnd.nextInt(350); }

    @Override
    protected ResultadoPago simularCobro(OrdenPago orden) {
        // datosPago esperado (opaco): "tok_xxx|cvc_123" — validacion simulada
        if (forzadoAFallar(orden)) {
            return noOk(orden, EstadoPago.Rechazado, "STRIPE-DECLINED",
                    "Stripe simulado: tarjeta declinada");
        }
        if (orden.datosPago == null || !orden.datosPago.contains("tok_")) {
            return noOk(orden, EstadoPago.Rechazado, "STRIPE-400",
                    "Stripe simulado: tokenTarjeta invalido");
        }
        return ok(orden, "STRIPE-AUTH-" + orden.idOrden, "Stripe simulado: cargo USD autorizado");
    }
}
