package com.apexstore.pagos;

import ApexStore.OrdenPago;
import ApexStore.ResultadoPago;

/**
 * Medio de pago agregado después del diseño original (RAS-04). Para incluirlo solo hizo falta
 * esta clase, registrarla en ServidorPasarelas y una línea en backend.config; el contexto y las
 * otras estrategias no cambiaron.
 */
public class EstrategiaBilleteraDigital extends EstrategiaPagoBase {

    private static final long serialVersionUID = 1L;

    public EstrategiaBilleteraDigital() {
        super("billetera");
    }

    @Override
    protected String validar(String datosPago) {
        return datosPago.startsWith("billetera=") ? null : "Falta el identificador de la billetera (billetera=...)";
    }

    @Override
    protected ResultadoPago cobrar(OrdenPago orden) {
        if (orden.datosPago.contains("sin-saldo")) {
            return rechazado(orden, "SIN-SALDO", "Saldo insuficiente en la billetera");
        }
        return aprobado(orden, "Pago autorizado desde la billetera");
    }

    @Override
    protected long latenciaSimulada() {
        return entre(150, 400);
    }
}
