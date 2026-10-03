package com.apexstore.gateways;

import com.apexstore.common.ApexConfig;
import Ice.Communicator;
import Ice.ObjectAdapter;
import Ice.Util;

/** Nodo 3 — Servidor de Pasarelas y Estrategias de Pago. Puerto 10001. */
public class GatewaysServer {
    public static void main(String[] args) {
        try (Communicator ic = Util.initialize(args)) {
            ObjectAdapter adapter =
                    ic.createObjectAdapterWithEndpoints("GatewaysAdapter", ApexConfig.GATEWAYS_ENDPOINT);
            adapter.add(new EstrategiaStripeImpl(), ic.stringToIdentity(ApexConfig.STRIPE_IDENTITY));
            adapter.add(new EstrategiaPSEImpl(), ic.stringToIdentity(ApexConfig.PSE_IDENTITY));
            adapter.add(new EstrategiaCriptoImpl(), ic.stringToIdentity(ApexConfig.CRIPTO_IDENTITY));
            adapter.add(new EstrategiaBilleteraImpl(), ic.stringToIdentity(ApexConfig.BILLETERA_IDENTITY));
            adapter.activate();
            System.out.println("[Nodo3-Gateways] Estrategias Stripe/PSE/Cripto/Billetera listas en "
                    + ApexConfig.GATEWAYS_ENDPOINT);
            ic.waitForShutdown();
        }
    }
}
