package com.apexstore.persistencia;

import com.apexstore.comun.Consola;

/**
 * Nodo 4: Servidor Base de Datos Transaccional.
 *
 * Publica ServidorPersistencia en el adaptador "Persistencia" (config/persistencia.config).
 * La base de datos se elige con BaseDatos.Url: H2 en memoria por defecto o un PostgreSQL real.
 */
public final class ServidorBaseDatos {

    private ServidorBaseDatos() {
    }

    public static void main(String[] args) throws Exception {
        try (Ice.Communicator ic = Ice.Util.initialize(args, "config/persistencia.config")) {
            Ice.Properties props = ic.getProperties();
            String url = props.getProperty("BaseDatos.Url");
            cargarDriver(url);

            ServidorPersistencia persistencia = new ServidorPersistencia(url,
                    props.getProperty("BaseDatos.Usuario"), props.getProperty("BaseDatos.Clave"));
            persistencia.crearEsquema();

            Ice.ObjectAdapter adapter = ic.createObjectAdapter("Persistencia");
            adapter.add(persistencia, Ice.Util.stringToIdentity("Persistencia"));
            adapter.activate();

            Consola.info("nodo-bd", "Persistencia lista en %s (%s)",
                    props.getProperty("Persistencia.Endpoints"), url);
            ic.waitForShutdown();
        }
    }

    // Los dos drivers van en el mismo jar, así que se cargan explícitamente según la URL.
    private static void cargarDriver(String url) throws ClassNotFoundException {
        if (url.startsWith("jdbc:h2:")) {
            Class.forName("org.h2.Driver");
        } else if (url.startsWith("jdbc:postgresql:")) {
            Class.forName("org.postgresql.Driver");
        } else {
            throw new IllegalArgumentException("BaseDatos.Url no soportada: " + url);
        }
    }
}
