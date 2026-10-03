package com.apexstore.client;

import ApexStore.MedioPago;
import ApexStore.OrdenPago;
import ApexStore.ResultadoPago;
import ApexStore.ServicioCheckoutPrx;
import ApexStore.ServicioCheckoutPrxHelper;
import com.apexstore.common.ApexConfig;

/**
 * Nodo 1 — Front-End simulado (WebApp React + MobileApp Flutter).
 * Los clientes SOLO conocen gestionarCompra/consultarCompra del checkout
 * (Dimension 1: Interaccion y Presentacion). Jamas hablan con pasarelas ni DB.
 *
 * Uso:
 *   runClient [stripe|pse|cripto|billetera|todos|fail]
 */
public class ClienteApp {

    public static void main(String[] args) {
        String modo = args.length > 0 ? args[0].toLowerCase() : "todos";
        try (Ice.Communicator ic = Ice.Util.initialize(args)) {
            Ice.ObjectPrx base = ic.stringToProxy(
                    ApexConfig.CHECKOUT_IDENTITY + ":" + ApexConfig.BACKEND_ENDPOINT);
            ServicioCheckoutPrx checkout = ServicioCheckoutPrxHelper.checkedCast(base);
            if (checkout == null) {
                System.err.println("[Cliente] No se pudo alcanzar ServicioCheckout. "
                        + "Encienda Nodo4 -> Nodo3 -> Nodo2 en ese orden.");
                return;
            }
            switch (modo) {
                case "stripe": demo(checkout, MedioPago.Stripe, "tok_visa123|cvc_321", 120.0, "USD"); break;
                case "pse": demo(checkout, MedioPago.PSE, "banco=Bancolombia|cta=123", 450000, "COP"); break;
                case "cripto": demo(checkout, MedioPago.Cripto, "wallet=bc1qxyz|red=BTC", 0.0025, "BTC"); break;
                case "billetera": demo(checkout, MedioPago.BilleteraDigital, "walletID=user@mail", 75.0, "USD"); break;
                case "fail": demo(checkout, MedioPago.PSE, "fail-forzado", 100000, "COP"); break;
                default:
                    demo(checkout, MedioPago.Stripe, "tok_visa123|cvc_321", 120.0, "USD");
                    demo(checkout, MedioPago.PSE, "banco=Bancolombia|cta=123", 450000, "COP");
                    demo(checkout, MedioPago.Cripto, "wallet=bc1qxyz|red=BTC", 0.0025, "BTC");
                    demo(checkout, MedioPago.BilleteraDigital, "walletID=user@mail", 75.0, "USD");
            }
        }
    }

    private static void demo(ServicioCheckoutPrx checkout, MedioPago medio,
                             String datosPago, double monto, String moneda) {
        OrdenPago orden = new OrdenPago();
        orden.idOrden = ApexConfig.nuevaOrden(medio.toString());
        orden.medio = medio;
        orden.monto = monto;
        orden.moneda = moneda;
        orden.clienteId = "cliente-demo-01";
        orden.datosPago = datosPago;

        System.out.println("\n[Cliente] >>> gestionarCompra " + orden.idOrden + " medio=" + medio);
        long t0 = System.currentTimeMillis();
        ResultadoPago ack = checkout.gestionarCompra(orden);
        long dt = System.currentTimeMillis() - t0;
        System.out.println("[Cliente] ACK en " + dt + "ms: estado=" + ack.estado + " | " + ack.mensaje);

        // Polling de confirmacion async (en produccion: push/websocket)
        for (int i = 0; i < 10; i++) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) { break; }
            ResultadoPago est = checkout.consultarCompra(orden.idOrden);
            System.out.println("[Cliente] poll[" + i + "] estado=" + est.estado
                    + " cod=" + est.codigoAutorizacion + " | " + est.mensaje);
            if (est.estado != ApexStore.EstadoPago.Pendiente) break;
        }
    }
}
