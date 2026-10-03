package com.apexstore.gateways;

import ApexStore.OrdenPago;
import ApexStore.EstadoPago;
import ApexStore.ResultadoPago;

/**
 * Nodo 3 — EstrategiaPSE SIMULADA.
 * Simula caidas/timeouts aleatorios (~15%) para probar tolerancia a fallos.
 */
public class EstrategiaPSEImpl extends EstrategiaBase {
    private static final long serialVersionUID = 1L;

    @Override protected String nombreMedio() { return "PSE"; }

    @Override protected int latenciaSimuladaMs() { return 200 + rnd.nextInt(500); }

    @Override
    protected ResultadoPago simularCobro(OrdenPago orden) {
        if (forzadoAFallar(orden)) {
            return noOk(orden, EstadoPago.Fallido, "PSE-TIMEOUT",
                    "PSE simulado: banco no responde (timeout)");
        }
        // 15% de fallo aleatorio simulado — el contexto debe aislarlo (Punto 2c)
        if (rnd.nextInt(100) < 15) {
            return noOk(orden, EstadoPago.Fallido, "PSE-503",
                    "PSE simulado: pasarela no disponible; reintente con otro medio");
        }
        if (orden.datosPago == null || !orden.datosPago.contains("banco")) {
            return noOk(orden, EstadoPago.Rechazado, "PSE-400",
                    "PSE simulado: codigoBanco/numCuenta invalidos");
        }
        return ok(orden, "PSE-DEBIT-" + orden.idOrden, "PSE simulado: debito COP exitoso");
    }
}
