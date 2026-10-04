package com.apexstore.comun;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Salida por consola con hora y componente, para seguir el flujo entre nodos. */
public final class Consola {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Consola() {
    }

    public static void info(String componente, String formato, Object... args) {
        System.out.println(linea(componente, formato, args));
    }

    public static void error(String componente, String formato, Object... args) {
        System.err.println(linea(componente, formato, args));
    }

    private static String linea(String componente, String formato, Object... args) {
        return String.format("%s [%-12s] %s", LocalTime.now().format(HORA), componente,
                String.format(formato, args));
    }
}
