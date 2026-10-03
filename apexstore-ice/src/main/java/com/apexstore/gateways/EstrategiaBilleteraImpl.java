package com.apexstore.gateways;

import ApexStore.OrdenPago;
import ApexStore.ResultadoPago;

/**
 * RAS-04 / OCP en accion: nuevo medio agregado SIN modificar el contexto ni
 * las demas estrategias. Basta implementar EstrategiaPago y registrarla en
 * GatewaysServer + el mapa del ProcesadorPagosContexto.
 */
public class EstrategiaBilleteraImpl extends EstrategiaBase {
    private static final long serialVersionUID = 1L;

    @Override protected String nombreMedio() { return "BilleteraDigital"; }

    @Override protected int latenciaSimuladaMs() { return 100 + rnd.nextInt(200); }

    @Override
    protected ResultadoPago simularCobro(OrdenPago orden) {
        if (forzadoAFallar(orden)) {
            return noOk(orden, ApexStore.EstadoPago.Rechazado, "WALLET-DECLINED",
                    "Billetera simulada: fondos insuficientes");
        }
        return ok(orden, "WALLET-AUTH-" + orden.idOrden, "Billetera simulada: pago autorizado");
    }
}
