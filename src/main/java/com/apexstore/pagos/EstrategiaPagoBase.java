package com.apexstore.pagos;

import ApexStore.EstadoPago;
import ApexStore.INotificacionPagoPrx;
import ApexStore.OrdenPago;
import ApexStore.ResultadoPago;
import ApexStore._IEstrategiaPagoDisp;
import Ice.Current;
import com.apexstore.comun.Consola;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Comportamiento común de las estrategias de pago del nodo 3 (patrón Strategy).
 *
 * pagar() solo valida los datos y deja el cobro en manos de la pasarela; responde Pendiente
 * de inmediato para no retener hilos del backend (RAS-01). El resultado se envía después por
 * INotificacionPago, con reintentos porque la notificación es idempotente.
 *
 * Cada estrategia tiene su propio pool de hilos: si una pasarela se pone lenta solo se llena
 * su cola y las demás siguen respondiendo.
 *
 * Las pasarelas son simuladas: ninguna estrategia se conecta a un servicio financiero real.
 */
public abstract class EstrategiaPagoBase extends _IEstrategiaPagoDisp {

    private static final long serialVersionUID = 1L;
    private static final int INTENTOS_NOTIFICACION = 3;
    private static final long ESPERA_REINTENTO_MS = 500;

    private final String nombre;
    private final transient ScheduledExecutorService pasarela;

    protected EstrategiaPagoBase(String nombre) {
        this.nombre = nombre;
        this.pasarela = Executors.newScheduledThreadPool(2, tarea -> {
            Thread hilo = new Thread(tarea, "pasarela-" + nombre);
            hilo.setDaemon(true);
            return hilo;
        });
    }

    @Override
    public final ResultadoPago pagar(OrdenPago orden, INotificacionPagoPrx callback, Current current) {
        String error = validar(orden.datosPago == null ? "" : orden.datosPago);
        if (error != null) {
            Consola.info(nombre, "%s rechazada: %s", orden.idOrden, error);
            return rechazado(orden, "DATOS", error);
        }
        if (callback == null) {
            return rechazado(orden, "SIN-CALLBACK", "No se indicó a quién notificar el resultado");
        }

        pasarela.schedule(() -> procesar(orden, callback), latenciaSimulada(), TimeUnit.MILLISECONDS);
        Consola.info(nombre, "%s recibida, en proceso", orden.idOrden);
        return resultado(orden, EstadoPago.Pendiente, "RECIBIDO", "Pago en proceso");
    }

    /** Devuelve el motivo de rechazo, o null si los datos del medio son válidos. */
    protected abstract String validar(String datosPago);

    /** Respuesta de la pasarela externa. Puede ser EnVerificacion si todavía no hay certeza. */
    protected abstract ResultadoPago cobrar(OrdenPago orden);

    /** Respuesta definitiva para un cobro que quedó en verificación. */
    protected ResultadoPago verificar(OrdenPago orden) {
        return aprobado(orden, "Pago confirmado");
    }

    protected abstract long latenciaSimulada();

    protected long esperaVerificacion() {
        return 3000;
    }

    protected ResultadoPago aprobado(OrdenPago orden, String mensaje) {
        return resultado(orden, EstadoPago.Aprobado, "APROBADO", mensaje);
    }

    protected ResultadoPago rechazado(OrdenPago orden, String motivo, String mensaje) {
        return resultado(orden, EstadoPago.Rechazado, motivo, mensaje);
    }

    protected ResultadoPago enVerificacion(OrdenPago orden, String mensaje) {
        return resultado(orden, EstadoPago.EnVerificacion, "VERIFICANDO", mensaje);
    }

    protected static long entre(long minimo, long maximo) {
        return ThreadLocalRandom.current().nextLong(minimo, maximo);
    }

    void detener() {
        pasarela.shutdownNow();
    }

    private void procesar(OrdenPago orden, INotificacionPagoPrx callback) {
        ResultadoPago respuesta = cobrar(orden);
        notificar(callback, respuesta, 1);
        if (respuesta.estado == EstadoPago.EnVerificacion) {
            pasarela.schedule(() -> notificar(callback, verificar(orden), 1), esperaVerificacion(),
                    TimeUnit.MILLISECONDS);
        }
    }

    private void notificar(INotificacionPagoPrx callback, ResultadoPago respuesta, int intento) {
        try {
            callback.notificarResultadoPago(respuesta);
            Consola.info(nombre, "%s -> %s notificado", respuesta.idOrden, respuesta.estado);
        } catch (Ice.LocalException e) {
            if (intento < INTENTOS_NOTIFICACION) {
                Consola.error(nombre, "no se pudo notificar %s (intento %d): %s", respuesta.idOrden, intento,
                        e.ice_id());
                pasarela.schedule(() -> notificar(callback, respuesta, intento + 1),
                        ESPERA_REINTENTO_MS * intento, TimeUnit.MILLISECONDS);
            } else {
                Consola.error(nombre, "%s: se agotaron los reintentos de notificación", respuesta.idOrden);
            }
        }
    }

    private ResultadoPago resultado(OrdenPago orden, EstadoPago estado, String codigo, String mensaje) {
        return new ResultadoPago(orden.idOrden, estado, nombre.toUpperCase(Locale.ROOT) + "-" + codigo, mensaje);
    }
}
