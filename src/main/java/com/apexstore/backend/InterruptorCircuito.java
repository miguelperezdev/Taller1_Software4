package com.apexstore.backend;

/**
 * Circuit breaker de un medio de pago. Después de varios fallos seguidos deja de enviar cobros
 * a ese medio durante un tiempo y responde de inmediato, para que una pasarela caída no consuma
 * hilos ni tiempo de las compras con otros medios.
 */
final class InterruptorCircuito {

    private final int fallosParaAbrir;
    private final long esperaMs;

    private int fallosSeguidos;
    private long abiertoHasta;

    InterruptorCircuito(int fallosParaAbrir, long esperaMs) {
        this.fallosParaAbrir = fallosParaAbrir;
        this.esperaMs = esperaMs;
    }

    synchronized boolean permitePaso() {
        return System.currentTimeMillis() >= abiertoHasta;
    }

    synchronized void registrarExito() {
        fallosSeguidos = 0;
    }

    /** @return true si con este fallo el circuito se abrió */
    synchronized boolean registrarFallo() {
        fallosSeguidos++;
        if (fallosSeguidos < fallosParaAbrir) {
            return false;
        }
        fallosSeguidos = 0;
        abiertoHasta = System.currentTimeMillis() + esperaMs;
        return true;
    }
}
