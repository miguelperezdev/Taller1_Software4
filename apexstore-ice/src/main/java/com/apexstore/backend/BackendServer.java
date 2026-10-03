package com.apexstore.backend;

import com.apexstore.common.ApexConfig;
import Ice.Communicator;
import Ice.ObjectAdapter;
import Ice.Util;

/**
 * Nodo 2 — Servidor E-Commerce (Backend Core). Puerto 10002.
 * Aloja: ServicioCheckout + ProcesadorPagosContexto + CallbackServant.
 *
 * Identidades:
 *  ServicioCheckout   -> fachada del front (gestionarCompra)
 *  ProcesadorPagos    -> contexto Strategy (iniciarPagoOrden)
 *  ProcesadorCallback -> receptor oneway de notificarTransaccionExitosa
 */
public class BackendServer {
    public static void main(String[] args) {
        try (Communicator ic = Util.initialize(args)) {
            ObjectAdapter adapter =
                    ic.createObjectAdapterWithEndpoints("BackendAdapter", ApexConfig.BACKEND_ENDPOINT);

            ProcesadorPagosContextoImpl contexto = new ProcesadorPagosContextoImpl(ic);
            adapter.add(contexto, ic.stringToIdentity(ApexConfig.PROCESADOR_IDENTITY));
            adapter.add(new ProcesadorPagosContextoImpl.CallbackServant(contexto),
                    ic.stringToIdentity(ApexConfig.CALLBACK_IDENTITY));
            adapter.add(new ServicioCheckoutImpl(ic),
                    ic.stringToIdentity(ApexConfig.CHECKOUT_IDENTITY));
            adapter.activate();
            System.out.println("[Nodo2-Backend] Checkout + Procesador + Callback listos en "
                    + ApexConfig.BACKEND_ENDPOINT);
            ic.waitForShutdown();
        }
    }
}
