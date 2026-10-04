package com.apexstore.backend;

import ApexStore.IPersistenciaTransaccionalPrx;
import ApexStore.ResultadoPago;
import ApexStore._INotificacionPagoDisp;
import Ice.Current;
import com.apexstore.comun.Consola;

/**
 * Recibe el resultado final de las estrategias (aprobado, rechazado o en verificación) y lo
 * guarda. Es un componente aparte del contexto para que la dependencia no sea circular:
 * contexto -> estrategias -> receptor -> persistencia.
 *
 * Si la base de datos falla se propaga la excepción, y la estrategia reintenta la notificación.
 */
public class ReceptorResultadosPagos extends _INotificacionPagoDisp {

    private static final long serialVersionUID = 1L;

    private final IPersistenciaTransaccionalPrx persistencia;

    public ReceptorResultadosPagos(IPersistenciaTransaccionalPrx persistencia) {
        this.persistencia = persistencia;
    }

    @Override
    public void notificarResultadoPago(ResultadoPago resultado, Current current) {
        boolean aplicado = persistencia.actualizarEstado(resultado);
        if (aplicado) {
            Consola.info("receptor", "%s -> %s (%s)", resultado.idOrden, resultado.estado, resultado.mensaje);
        } else {
            Consola.info("receptor", "%s: se ignora %s, la orden ya tiene un estado final", resultado.idOrden,
                    resultado.estado);
        }
    }
}
