package com.apexstore.db;

import ApexStore.OrdenPago;
import ApexStore.EstadoPago;
import ApexStore.ResultadoPago;
import ApexStore._PersistenciaTransaccionalDisp;
import Ice.Current;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nodo 4 — DB PostgreSQL Transacciones (SIMULADA en memoria).
 *
 * Garantias (RAS-03 ACID simulado):
 *  - Atomicidad: bloque synchronized por idOrden (una sola escritura gana).
 *  - Idempotencia: si idOrden ya existe se retorna el registro previo, nunca
 *    se duplica el cobro (no doble cobro / no pagos huerfanos).
 *  - Auditoria: log append-only con marca temporal.
 *
 * CORRECCION C2/C3 del Punto 3: expone UN SOLO metodo persistirTransaccion
 * para todos los medios. Ninguna pasarela escribe directo; solo el contexto.
 */
public class TransaccionesDBImpl extends _PersistenciaTransaccionalDisp {

    private static final long serialVersionUID = 1L;

    private final ConcurrentHashMap<String, Registro> store = new ConcurrentHashMap<>();
    private final List<String> auditLog = Collections.synchronizedList(new ArrayList<>());

    static class Registro {
        final ResultadoPago resultado;
        final OrdenPago orden;
        Registro(ResultadoPago r, OrdenPago o) { this.resultado = r; this.orden = o; }
    }

    private void auditar(String linea) {
        String entry = Instant.now() + " | " + linea;
        auditLog.add(entry);
        System.out.println("[DB][AUDIT] " + entry);
    }

    @Override
    public boolean persistirTransaccion(ResultadoPago resultado, OrdenPago orden, Current __current) {
        if (resultado == null || orden == null || resultado.idOrden == null) {
            auditar("RECHAZO persistencia con datos nulos");
            return false;
        }
        // Idempotencia + atomicidad por clave de orden: una sola entrada por
        // idOrden (nunca se duplica el cobro), pero el ESTADO se actualiza
        // cuando llega el callback final (ACK Pendiente -> Autorizado/Fallido).
        synchronized (store) {
            if (store.containsKey(resultado.idOrden)) {
                store.put(resultado.idOrden, new Registro(clone(resultado), clone(orden)));
                auditar("ACTUALIZA idOrden=" + resultado.idOrden + " nuevoEstado=" + resultado.estado
                        + " cod=" + resultado.codigoAutorizacion);
                return true;
            }
            store.put(resultado.idOrden, new Registro(clone(resultado), clone(orden)));
            auditar("PERSIST idOrden=" + resultado.idOrden
                    + " medio=" + orden.medio + " monto=" + orden.monto + orden.moneda
                    + " estado=" + resultado.estado);
            return true;
        }
    }

    @Override
    public ResultadoPago consultarTransaccion(String idOrden, Current __current) {
        Registro reg = store.get(idOrden);
        if (reg == null) {
            ResultadoPago r = new ResultadoPago();
            r.idOrden = idOrden;
            r.estado = EstadoPago.Fallido;
            r.codigoAutorizacion = "NOT-FOUND";
            r.mensaje = "Orden no encontrada en DB";
            return r;
        }
        return clone(reg.resultado);
    }

    // Clones defensivos (Slice genera clases mutables)
    private static ResultadoPago clone(ResultadoPago r) {
        ResultadoPago c = new ResultadoPago();
        c.idOrden = r.idOrden;
        c.estado = r.estado;
        c.codigoAutorizacion = r.codigoAutorizacion;
        c.mensaje = r.mensaje;
        return c;
    }

    private static OrdenPago clone(OrdenPago o) {
        OrdenPago c = new OrdenPago();
        c.idOrden = o.idOrden;
        c.medio = o.medio;
        c.monto = o.monto;
        c.moneda = o.moneda;
        c.clienteId = o.clienteId;
        c.datosPago = o.datosPago;
        return c;
    }
}
