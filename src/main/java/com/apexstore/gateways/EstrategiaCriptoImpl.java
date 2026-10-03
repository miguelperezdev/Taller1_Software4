package com.apexstore.gateways;

import ApexStore.OrdenPago;
import ApexStore.EstadoPago;
import ApexStore.ResultadoPago;

/**
 * Nodo 3 — EstrategiaCripto SIMULADA.
 * CORRECCION C2: ya NO escribe directo a DB; confirma via callback como
 * todas las demas estrategias (Punto 2d).
 */
public class EstrategiaCriptoImpl extends EstrategiaBase {
    private static final long serialVersionUID = 1L;

    @Override protected String nombreMedio() { return "Cripto"; }

    @Override protected int latenciaSimuladaMs() { return 300 + rnd.nextInt(600); }

    @Override
    protected ResultadoPago simularCobro(OrdenPago orden) {
        if (forzadoAFallar(orden)) {
            return noOk(orden, EstadoPago.Fallido, "BTC-CONGESTION",
                    "Cripto simulado: red blockchain congestionada");
        }
        if (orden.datosPago == null || !orden.datosPago.contains("wallet")) {
            return noOk(orden, EstadoPago.Rechazado, "BTC-400",
                    "Cripto simulado: direccionWallet invalida");
        }
        if (rnd.nextInt(100) < 10) {
            return noOk(orden, EstadoPago.Fallido, "BTC-MEMPOOL",
                    "Cripto simulado: mempool saturada, reintente");
        }
        return ok(orden, "BTC-TX-" + orden.idOrden, "Cripto simulado: cobro BTC confirmado");
    }
}
