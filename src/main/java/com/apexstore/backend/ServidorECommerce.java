package com.apexstore.backend;

import ApexStore.INotificacionPagoPrx;
import ApexStore.INotificacionPagoPrxHelper;
import ApexStore.IPersistenciaTransaccionalPrx;
import ApexStore.IPersistenciaTransaccionalPrxHelper;
import ApexStore.IProcesadorPagosPrx;
import ApexStore.IProcesadorPagosPrxHelper;
import com.apexstore.comun.Consola;

/**
 * Nodo 2: Servidor E-Commerce (Backend Core).
 *
 * Publica en el adaptador "Backend" los tres componentes del nodo. Entre ellos también se
 * comunican por proxies ICE, de modo que cada uno depende solo de la interfaz del otro:
 * ServicioCheckout -> IProcesadorPagos, y las estrategias reciben el proxy de INotificacionPago.
 */
public final class ServidorECommerce {

    private ServidorECommerce() {
    }

    public static void main(String[] args) {
        try (Ice.Communicator ic = Ice.Util.initialize(args, "config/backend.config")) {
            Ice.ObjectPrx base = ic.propertyToProxy("Persistencia.Proxy");
            if (base == null) {
                Consola.error("nodo-backend", "Falta Persistencia.Proxy en la configuración");
                return;
            }
            IPersistenciaTransaccionalPrx persistencia = IPersistenciaTransaccionalPrxHelper.uncheckedCast(base);

            Ice.ObjectAdapter adapter = ic.createObjectAdapter("Backend");

            INotificacionPagoPrx receptor = INotificacionPagoPrxHelper.uncheckedCast(adapter.add(
                    new ReceptorResultadosPagos(persistencia), Ice.Util.stringToIdentity("ReceptorResultadosPagos")));

            IProcesadorPagosPrx procesador = IProcesadorPagosPrxHelper.uncheckedCast(adapter.add(
                    new ProcesadorPagosContexto(ic, persistencia, receptor), Ice.Util.stringToIdentity("ProcesadorPagos")));

            adapter.add(new ServicioCheckout(procesador), Ice.Util.stringToIdentity("ServicioCheckout"));
            adapter.activate();

            Consola.info("nodo-backend", "Backend listo en %s", ic.getProperties().getProperty("Backend.Endpoints"));
            ic.waitForShutdown();
        }
    }
}
