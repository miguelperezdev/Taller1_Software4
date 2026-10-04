package com.apexstore.backend;

import ApexStore.EstadoPago;
import ApexStore.IEstrategiaPagoPrx;
import ApexStore.IEstrategiaPagoPrxHelper;
import ApexStore.INotificacionPagoPrx;
import ApexStore.IPersistenciaTransaccionalPrx;
import ApexStore.OrdenNoEncontrada;
import ApexStore.OrdenPago;
import ApexStore.ResultadoPago;
import ApexStore._IProcesadorPagosDisp;
import Ice.Current;
import com.apexstore.comun.Consola;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contexto del patrón Strategy (nodo 2). Elige la estrategia según el medio de la orden y solo
 * la conoce por IEstrategiaPago: no sabe si detrás hay una tarjeta, un banco o una wallet.
 *
 * Las estrategias se resuelven por configuración (Pagos.&lt;medio&gt;.Proxy), así que un medio
 * nuevo no exige modificar esta clase (RAS-04).
 *
 * El flujo de una orden es:
 *  1. registrarla como Pendiente antes de cobrar, para que nunca exista un cobro sin orden;
 *  2. despacharla a la estrategia con un tiempo máximo de espera;
 *  3. devolver el acuse; el resultado final llega a ReceptorResultadosPagos.
 *
 * No guarda estado propio: todo queda en la base de datos, de modo que el nodo se puede replicar.
 */
public class ProcesadorPagosContexto extends _IProcesadorPagosDisp {

    private static final long serialVersionUID = 1L;
    private static final String COMPONENTE = "contexto";

    private final transient Ice.Communicator ic;
    private final IPersistenciaTransaccionalPrx persistencia;
    private final INotificacionPagoPrx receptor;

    private final int timeoutMs;
    private final int fallosParaAbrir;
    private final long esperaCircuitoMs;

    private final Map<String, IEstrategiaPagoPrx> estrategias = new ConcurrentHashMap<>();
    private final Map<String, InterruptorCircuito> circuitos = new ConcurrentHashMap<>();

    public ProcesadorPagosContexto(Ice.Communicator ic, IPersistenciaTransaccionalPrx persistencia,
                                   INotificacionPagoPrx receptor) {
        this.ic = ic;
        this.persistencia = persistencia;
        this.receptor = receptor;

        Ice.Properties props = ic.getProperties();
        this.timeoutMs = props.getPropertyAsIntWithDefault("Pagos.TimeoutMs", 2000);
        this.fallosParaAbrir = props.getPropertyAsIntWithDefault("Pagos.FallosParaAbrirCircuito", 3);
        this.esperaCircuitoMs = props.getPropertyAsIntWithDefault("Pagos.EsperaCircuitoMs", 15000);
    }

    @Override
    public ResultadoPago iniciarPagoOrden(OrdenPago orden, Current current) {
        long inicio = System.nanoTime();
        String medio = orden.medio.trim().toLowerCase(Locale.ROOT);

        IEstrategiaPagoPrx estrategia = estrategiaPara(medio);
        if (estrategia == null) {
            return new ResultadoPago(orden.idOrden, EstadoPago.Rechazado, "MEDIO-NO-SOPORTADO",
                    "No hay una estrategia configurada para el medio " + orden.medio);
        }

        try {
            if (!persistencia.registrarOrden(orden)) {
                // La orden ya se había recibido (reintento del cliente): se responde su estado actual.
                return persistencia.consultarTransaccion(orden.idOrden);
            }
        } catch (OrdenNoEncontrada | Ice.LocalException e) {
            Consola.error(COMPONENTE, "%s: no se pudo registrar la orden (%s)", orden.idOrden,
                    e.getClass().getSimpleName());
            return new ResultadoPago(orden.idOrden, EstadoPago.Rechazado, "SIN-REGISTRO",
                    "No fue posible registrar la orden; no se realizó ningún cobro");
        }

        ResultadoPago acuse = despachar(orden, medio, estrategia);
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        Consola.info(COMPONENTE, "%s via %s -> %s en %d ms", orden.idOrden, medio, acuse.estado, ms);
        return acuse;
    }

    @Override
    public ResultadoPago consultarEstado(String idOrden, Current current) throws OrdenNoEncontrada {
        return persistencia.consultarTransaccion(idOrden);
    }

    private ResultadoPago despachar(OrdenPago orden, String medio, IEstrategiaPagoPrx estrategia) {
        InterruptorCircuito circuito = circuitos.computeIfAbsent(medio,
                m -> new InterruptorCircuito(fallosParaAbrir, esperaCircuitoMs));

        if (!circuito.permitePaso()) {
            return guardar(new ResultadoPago(orden.idOrden, EstadoPago.Rechazado, "MEDIO-NO-DISPONIBLE",
                    "El medio " + medio + " no está disponible en este momento, intenta con otro"));
        }

        try {
            ResultadoPago acuse = estrategia.pagar(orden, receptor);
            circuito.registrarExito();
            return acuse.estado == EstadoPago.Pendiente ? acuse : guardar(acuse);
        } catch (Ice.ConnectFailedException e) {
            // La orden nunca llegó a la pasarela, así que es seguro rechazarla.
            fallo(circuito, medio);
            return guardar(new ResultadoPago(orden.idOrden, EstadoPago.Rechazado, "PASARELA-NO-DISPONIBLE",
                    "No hubo conexión con la pasarela " + medio + ", intenta con otro medio"));
        } catch (Ice.LocalException e) {
            // Timeout o conexión perdida: la pasarela pudo haber cobrado. No se da por fallido,
            // queda en verificación hasta que llegue su notificación.
            fallo(circuito, medio);
            Consola.error(COMPONENTE, "%s: sin respuesta de %s (%s)", orden.idOrden, medio, e.ice_id());
            return guardar(new ResultadoPago(orden.idOrden, EstadoPago.EnVerificacion, "SIN-RESPUESTA",
                    "La pasarela no respondió a tiempo; el pago queda en verificación"));
        }
    }

    /** Guarda el resultado y devuelve el estado que realmente quedó en la base de datos. */
    private ResultadoPago guardar(ResultadoPago resultado) {
        try {
            if (persistencia.actualizarEstado(resultado)) {
                return resultado;
            }
            // Otra notificación llegó primero (p. ej. el callback antes que el timeout).
            return persistencia.consultarTransaccion(resultado.idOrden);
        } catch (OrdenNoEncontrada | Ice.LocalException e) {
            Consola.error(COMPONENTE, "%s: no se pudo guardar el estado %s (%s)", resultado.idOrden,
                    resultado.estado, e.getClass().getSimpleName());
            return resultado;
        }
    }

    private void fallo(InterruptorCircuito circuito, String medio) {
        if (circuito.registrarFallo()) {
            Consola.error(COMPONENTE, "circuito de %s abierto por %d ms", medio, esperaCircuitoMs);
        }
    }

    private IEstrategiaPagoPrx estrategiaPara(String medio) {
        return estrategias.computeIfAbsent(medio, m -> {
            Ice.ObjectPrx base = ic.propertyToProxy("Pagos." + m + ".Proxy");
            if (base == null) {
                return null;
            }
            return IEstrategiaPagoPrxHelper.uncheckedCast(base.ice_invocationTimeout(timeoutMs));
        });
    }
}
