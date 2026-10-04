package com.apexstore.pagos;

import ApexStore.OrdenPago;
import ApexStore.ResultadoPago;

/**
 * Cobro en BTC. Si la red está congestionada la transacción tarda en confirmarse, así que
 * se reporta EnVerificacion y luego el resultado definitivo, igual que las demás estrategias
 * (ya no escribe en la base de datos por su cuenta).
 */
public class EstrategiaCripto extends EstrategiaPagoBase {

    private static final long serialVersionUID = 1L;

    public EstrategiaCripto() {
        super("cripto");
    }

    @Override
    protected String validar(String datosPago) {
        return datosPago.startsWith("wallet=") ? null : "Cripto requiere la dirección de la wallet (wallet=...)";
    }

    @Override
    protected ResultadoPago cobrar(OrdenPago orden) {
        if (orden.datosPago.contains("congestion")) {
            return enVerificacion(orden, "Red congestionada, esperando confirmaciones");
        }
        return aprobado(orden, "Transacción confirmada en la red");
    }

    @Override
    protected ResultadoPago verificar(OrdenPago orden) {
        return aprobado(orden, "Transacción confirmada tras 6 bloques");
    }

    @Override
    protected long latenciaSimulada() {
        return entre(400, 1200);
    }

    @Override
    protected long esperaVerificacion() {
        return 4000;
    }
}
