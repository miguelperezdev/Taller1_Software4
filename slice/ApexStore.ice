// Contrato ICE de ApexStore.
//
// Cada interfaz corresponde a una interfaz provista del diagrama de despliegue
// corregido (Punto 3). Los nodos solo se conocen a través de estas interfaces.

#pragma once

module ApexStore
{
    // Pendiente:      la pasarela recibió el cobro y aún no responde.
    // EnVerificacion: no se sabe todavía si el dinero se movió (banco lento,
    //                 red blockchain congestionada, timeout). Nunca se marca
    //                 como fallido algo que pudo haberse cobrado.
    enum EstadoPago { Pendiente, EnVerificacion, Aprobado, Rechazado };

    struct OrdenPago
    {
        string idOrden;     // identifica la orden y sirve de clave de idempotencia
        string clienteId;
        string medio;       // "stripe", "pse", "cripto", "billetera", ...
        long monto;         // en la unidad mínima de la moneda (centavos, satoshis)
        string moneda;
        string datosPago;   // token del medio de pago; solo la estrategia lo interpreta
    };

    struct ResultadoPago
    {
        string idOrden;
        EstadoPago estado;
        string codigo;
        string mensaje;
    };

    exception OrdenNoEncontrada
    {
        string idOrden;
    };

    // Provista por ReceptorResultadosPagos (nodo 2).
    interface INotificacionPago
    {
        void notificarResultadoPago(ResultadoPago resultado);
    };

    // Provista por cada estrategia del nodo 3. Responde de inmediato con
    // Pendiente o Rechazado; el resultado final llega por el callback.
    interface IEstrategiaPago
    {
        ResultadoPago pagar(OrdenPago orden, INotificacionPago* callback);
    };

    // Provista por ProcesadorPagosContexto (nodo 2).
    interface IProcesadorPagos
    {
        ResultadoPago iniciarPagoOrden(OrdenPago orden);
        ResultadoPago consultarEstado(string idOrden) throws OrdenNoEncontrada;
    };

    // Provista por ServicioCheckout (nodo 2). Es lo único que ven los clientes.
    interface IGestionCompras
    {
        ResultadoPago gestionarCompra(OrdenPago orden);
        ResultadoPago consultarCompra(string idOrden) throws OrdenNoEncontrada;
    };

    // Provista por ServidorPersistencia (nodo 4).
    interface IPersistenciaTransaccional
    {
        // false si la orden ya existía: no se registra (ni se cobra) dos veces.
        bool registrarOrden(OrdenPago orden);

        // false si la transición no es válida, p. ej. Aprobado -> Pendiente.
        bool actualizarEstado(ResultadoPago resultado);

        ResultadoPago consultarTransaccion(string idOrden) throws OrdenNoEncontrada;
    };
};
