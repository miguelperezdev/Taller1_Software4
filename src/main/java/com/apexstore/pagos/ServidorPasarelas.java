package com.apexstore.pagos;

import com.apexstore.comun.Consola;
import java.util.List;

/**
 * Nodo 3: Servidor de Pasarelas y Estrategias de Pago.
 *
 * Publica una estrategia por medio de pago en el adaptador "Pagos" (config/pagos.config).
 * La identidad de cada objeto es la que el backend usa en Pagos.&lt;medio&gt;.Proxy.
 */
public final class ServidorPasarelas {

    private ServidorPasarelas() {
    }

    public static void main(String[] args) {
        List<EstrategiaPagoBase> estrategias = List.of(
                new EstrategiaStripe(),
                new EstrategiaPSE(),
                new EstrategiaCripto(),
                new EstrategiaBilleteraDigital());

        try (Ice.Communicator ic = Ice.Util.initialize(args, "config/pagos.config")) {
            Ice.ObjectAdapter adapter = ic.createObjectAdapter("Pagos");
            for (EstrategiaPagoBase estrategia : estrategias) {
                adapter.add(estrategia, Ice.Util.stringToIdentity(estrategia.getClass().getSimpleName()));
            }
            adapter.activate();

            Consola.info("nodo-pagos", "Estrategias publicadas en %s",
                    ic.getProperties().getProperty("Pagos.Endpoints"));
            ic.waitForShutdown();
        } finally {
            estrategias.forEach(EstrategiaPagoBase::detener);
        }
    }
}
