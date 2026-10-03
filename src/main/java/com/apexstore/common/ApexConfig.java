package com.apexstore.common;

import java.util.UUID;

/** Utilidades compartidas: ids, endpoints y tiempos objetivo (RAS-02). */
public final class ApexConfig {
    private ApexConfig() {}

    // Endpoints ICE por nodo (topologia 4 nodos -> 3 JVM servidor + cliente)
    public static final String DB_ENDPOINT = "tcp -h 127.0.0.1 -p 10000";
    public static final String GATEWAYS_ENDPOINT = "tcp -h 127.0.0.1 -p 10001";
    public static final String BACKEND_ENDPOINT = "tcp -h 127.0.0.1 -p 10002";

    public static final String DB_IDENTITY = "PersistenciaDB";
    public static final String STRIPE_IDENTITY = "EstrategiaStripe";
    public static final String PSE_IDENTITY = "EstrategiaPSE";
    public static final String CRIPTO_IDENTITY = "EstrategiaCripto";
    public static final String BILLETERA_IDENTITY = "EstrategiaBilletera"; // RAS-04: nuevo medio sin tocar contexto
    public static final String PROCESADOR_IDENTITY = "ProcesadorPagos";
    public static final String CALLBACK_IDENTITY = "ProcesadorCallback";
    public static final String CHECKOUT_IDENTITY = "ServicioCheckout";

    /** P95 interno objetivo en ms (RAS-02: orquestacion < 250ms). */
    public static final long P95_OBJETIVO_MS = 250;

    public static String nuevaOrden(String prefijo) {
        return prefijo + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
