package com.apexstore.db;

import com.apexstore.common.ApexConfig;
import Ice.Communicator;
import Ice.ObjectAdapter;
import Ice.Util;

/** Nodo 4 — Servidor Base de Datos Transaccional. Puerto 10000. */
public class DatabaseServer {
    public static void main(String[] args) {
        try (Communicator ic = Util.initialize(args)) {
            ObjectAdapter adapter =
                    ic.createObjectAdapterWithEndpoints("DBAdapter", ApexConfig.DB_ENDPOINT);
            adapter.add(new TransaccionesDBImpl(),
                    ic.stringToIdentity(ApexConfig.DB_IDENTITY));
            adapter.activate();
            System.out.println("[Nodo4-DB] PersistenciaDB lista en " + ApexConfig.DB_ENDPOINT);
            ic.waitForShutdown();
        }
    }
}
