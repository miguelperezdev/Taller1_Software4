package com.apexstore.cliente;

import ApexStore.EstadoPago;
import ApexStore.IGestionComprasPrx;
import ApexStore.IGestionComprasPrxHelper;
import ApexStore.OrdenNoEncontrada;
import ApexStore.OrdenPago;
import ApexStore.ResultadoPago;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Nodo 1: simula WebApp y MobileApp. Solo conoce IGestionCompras; nunca habla con las
 * pasarelas ni con la base de datos.
 *
 * Uso: java -jar build/libs/cliente.jar [escenario ...]
 * Sin argumentos ejecuta todos los escenarios.
 */
public final class ClienteApp {

    private static final long ESPERA_CONSULTA_MS = 700;
    private static final int MAX_CONSULTAS = 15;

    // medio, monto (unidad mínima), moneda, datos de pago (tokenizados en el dispositivo)
    private static final Map<String, String[]> ESCENARIOS = new LinkedHashMap<>();

    static {
        ESCENARIOS.put("stripe", new String[] {"stripe", "12000", "USD", "tok_visa_4242"});
        ESCENARIOS.put("pse", new String[] {"pse", "45000000", "COP", "banco=1007;cuenta=ahorros;demora"});
        ESCENARIOS.put("cripto", new String[] {"cripto", "250000", "BTC", "wallet=bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"});
        ESCENARIOS.put("billetera", new String[] {"billetera", "7500", "USD", "billetera=nequi:3001234567"});
        ESCENARIOS.put("rechazo", new String[] {"stripe", "9900", "USD", "tok_visa_rechazada"});
        ESCENARIOS.put("invalido", new String[] {"stripe", "9900", "USD", "4111111111111111"});
        ESCENARIOS.put("desconocido", new String[] {"paypal", "5000", "USD", "pp_cuenta"});
        ESCENARIOS.put("duplicada", new String[] {"billetera", "3000", "USD", "billetera=daviplata:3109876543"});
    }

    private ClienteApp() {
    }

    public static void main(String[] args) {
        List<String> pedidos = new ArrayList<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                pedidos.add(arg.toLowerCase(Locale.ROOT));
            }
        }
        if (pedidos.isEmpty()) {
            pedidos.addAll(ESCENARIOS.keySet());
        }

        try (Ice.Communicator ic = Ice.Util.initialize(args, "config/cliente.config")) {
            IGestionComprasPrx tienda = IGestionComprasPrxHelper.uncheckedCast(ic.propertyToProxy("Checkout.Proxy"));
            try {
                tienda.ice_ping();
            } catch (Ice.LocalException e) {
                System.out.println("No hay conexión con el backend (" + e.ice_id() + ").");
                System.out.println("Inicia primero persistencia, pagos y backend.");
                return;
            }

            for (String nombre : pedidos) {
                String[] datos = ESCENARIOS.get(nombre);
                if (datos == null) {
                    System.out.println("Escenario desconocido: " + nombre + ". Opciones: " + ESCENARIOS.keySet());
                    continue;
                }
                ejecutar(tienda, nombre, datos);
            }
        }
    }

    private static void ejecutar(IGestionComprasPrx tienda, String nombre, String[] datos) {
        OrdenPago orden = new OrdenPago(nuevoId(), "cliente-0042", datos[0], Long.parseLong(datos[1]), datos[2],
                datos[3]);
        System.out.printf("%n== %s: %s por %s %s ==%n", nombre, orden.medio, orden.monto, orden.moneda);

        long inicio = System.nanoTime();
        ResultadoPago respuesta = tienda.gestionarCompra(orden);
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        mostrar("respuesta (" + ms + " ms)", respuesta);

        if (nombre.equals("duplicada")) {
            // Mismo idOrden otra vez, como si el usuario presionara "pagar" dos veces.
            mostrar("reenvío", tienda.gestionarCompra(orden));
        }

        if (respuesta.estado == EstadoPago.Pendiente || respuesta.estado == EstadoPago.EnVerificacion) {
            esperarResultado(tienda, orden.idOrden);
        }
    }

    private static void esperarResultado(IGestionComprasPrx tienda, String idOrden) {
        EstadoPago ultimo = null;
        for (int i = 0; i < MAX_CONSULTAS; i++) {
            dormir(ESPERA_CONSULTA_MS);
            try {
                ResultadoPago estado = tienda.consultarCompra(idOrden);
                if (estado.estado != ultimo) {
                    mostrar("consulta", estado);
                    ultimo = estado.estado;
                }
                if (estado.estado == EstadoPago.Aprobado || estado.estado == EstadoPago.Rechazado) {
                    return;
                }
            } catch (OrdenNoEncontrada e) {
                System.out.println("  la orden " + e.idOrden + " no existe");
                return;
            }
        }
        System.out.println("  sin resultado final todavía; se puede consultar más tarde");
    }

    private static void mostrar(String etiqueta, ResultadoPago r) {
        System.out.printf("  %-20s %-15s %-28s %s%n", etiqueta, r.estado, r.codigo, r.mensaje);
    }

    private static String nuevoId() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
