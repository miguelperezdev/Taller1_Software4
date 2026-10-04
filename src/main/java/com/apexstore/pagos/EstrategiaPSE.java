package com.apexstore.pagos;

import ApexStore.OrdenPago;
import ApexStore.ResultadoPago;

/**
 * Débito bancario por PSE. El usuario aprueba en la página de su banco, así que la respuesta
 * puede tardar: en ese caso el pago queda EnVerificacion hasta que el banco confirme.
 */
public class EstrategiaPSE extends EstrategiaPagoBase {

    private static final long serialVersionUID = 1L;

    public EstrategiaPSE() {
        super("pse");
    }

    @Override
    protected String validar(String datosPago) {
        return datosPago.startsWith("banco=") ? null : "PSE requiere el código del banco (banco=...)";
    }

    @Override
    protected ResultadoPago cobrar(OrdenPago orden) {
        if (orden.datosPago.contains("rechazar")) {
            return rechazado(orden, "DEBITO-RECHAZADO", "El banco rechazó el débito");
        }
        if (orden.datosPago.contains("demora")) {
            return enVerificacion(orden, "El banco aún no confirma el débito");
        }
        return aprobado(orden, "Débito aprobado por el banco");
    }

    @Override
    protected ResultadoPago verificar(OrdenPago orden) {
        return aprobado(orden, "El banco confirmó el débito");
    }

    @Override
    protected long latenciaSimulada() {
        return entre(500, 1500);
    }
}
