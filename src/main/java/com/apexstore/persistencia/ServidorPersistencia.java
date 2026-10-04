package com.apexstore.persistencia;

import ApexStore.EstadoPago;
import ApexStore.OrdenNoEncontrada;
import ApexStore.OrdenPago;
import ApexStore.ResultadoPago;
import ApexStore._IPersistenciaTransaccionalDisp;
import Ice.Current;
import com.apexstore.comun.Consola;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Componente ServidorPersistencia del nodo 4: implementa IPersistenciaTransaccional sobre JDBC.
 *
 * Cada operación es una transacción: el cambio de estado y su registro de auditoría se guardan
 * juntos o no se guarda ninguno (RAS-03). La orden se identifica por idOrden, así que una orden
 * repetida no se registra dos veces, y un estado final (Aprobado, Rechazado) ya no se puede cambiar.
 */
public class ServidorPersistencia extends _IPersistenciaTransaccionalDisp {

    private static final long serialVersionUID = 1L;
    private static final String COMPONENTE = "persistencia";
    private static final String LLAVE_DUPLICADA = "23505";

    private static final Map<EstadoPago, Set<EstadoPago>> TRANSICIONES = new EnumMap<>(EstadoPago.class);

    static {
        TRANSICIONES.put(EstadoPago.Pendiente,
                EnumSet.of(EstadoPago.EnVerificacion, EstadoPago.Aprobado, EstadoPago.Rechazado));
        TRANSICIONES.put(EstadoPago.EnVerificacion, EnumSet.of(EstadoPago.Aprobado, EstadoPago.Rechazado));
        TRANSICIONES.put(EstadoPago.Aprobado, EnumSet.noneOf(EstadoPago.class));
        TRANSICIONES.put(EstadoPago.Rechazado, EnumSet.noneOf(EstadoPago.class));
    }

    private final String url;
    private final String usuario;
    private final String clave;

    ServidorPersistencia(String url, String usuario, String clave) {
        this.url = url;
        this.usuario = usuario;
        this.clave = clave;
    }

    void crearEsquema() throws SQLException, IOException {
        String script;
        try (InputStream in = getClass().getResourceAsStream("esquema.sql")) {
            if (in == null) {
                throw new IOException("No se encontró esquema.sql");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection c = conectar(); Statement st = c.createStatement()) {
            for (String sentencia : script.replaceAll("--[^\n]*", "").split(";")) {
                if (!sentencia.isBlank()) {
                    st.execute(sentencia);
                }
            }
        }
    }

    @Override
    public boolean registrarOrden(OrdenPago orden, Current current) {
        try (Connection c = conectar()) {
            c.setAutoCommit(false);
            try {
                insertarOrden(c, orden);
                auditar(c, orden.idOrden, null, EstadoPago.Pendiente, "orden registrada");
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                if (LLAVE_DUPLICADA.equals(e.getSQLState())) {
                    Consola.info(COMPONENTE, "%s ya estaba registrada, se ignora", orden.idOrden);
                    return false;
                }
                throw e;
            }
            Consola.info(COMPONENTE, "%s registrada (%s, %d %s)", orden.idOrden, orden.medio, orden.monto,
                    orden.moneda);
            return true;
        } catch (SQLException e) {
            throw errorDeBaseDatos("registrar " + orden.idOrden, e);
        }
    }

    @Override
    public boolean actualizarEstado(ResultadoPago resultado, Current current) {
        try (Connection c = conectar()) {
            c.setAutoCommit(false);
            EstadoPago actual = estadoBloqueado(c, resultado.idOrden);

            if (actual == null) {
                c.rollback();
                Consola.error(COMPONENTE, "%s no existe, no se puede actualizar", resultado.idOrden);
                return false;
            }
            if (actual == resultado.estado) {
                // Notificación repetida (p. ej. un reintento del callback): no cambia nada.
                c.rollback();
                return true;
            }
            if (!TRANSICIONES.get(actual).contains(resultado.estado)) {
                c.rollback();
                Consola.error(COMPONENTE, "%s: transición %s -> %s no permitida", resultado.idOrden, actual,
                        resultado.estado);
                return false;
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE transaccion SET estado = ?, codigo = ?, mensaje = ?, actualizada = ? WHERE id_orden = ?")) {
                ps.setString(1, resultado.estado.name());
                ps.setString(2, resultado.codigo);
                ps.setString(3, resultado.mensaje);
                ps.setTimestamp(4, Timestamp.from(Instant.now()));
                ps.setString(5, resultado.idOrden);
                ps.executeUpdate();
            }
            auditar(c, resultado.idOrden, actual, resultado.estado, resultado.codigo);
            c.commit();

            Consola.info(COMPONENTE, "%s: %s -> %s", resultado.idOrden, actual, resultado.estado);
            return true;
        } catch (SQLException e) {
            throw errorDeBaseDatos("actualizar " + resultado.idOrden, e);
        }
    }

    @Override
    public ResultadoPago consultarTransaccion(String idOrden, Current current) throws OrdenNoEncontrada {
        try (Connection c = conectar();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT estado, codigo, mensaje FROM transaccion WHERE id_orden = ?")) {
            ps.setString(1, idOrden);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new OrdenNoEncontrada(idOrden);
                }
                return new ResultadoPago(idOrden, EstadoPago.valueOf(rs.getString("estado")),
                        rs.getString("codigo"), rs.getString("mensaje"));
            }
        } catch (SQLException e) {
            throw errorDeBaseDatos("consultar " + idOrden, e);
        }
    }

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(url, usuario, clave);
    }

    private void insertarOrden(Connection c, OrdenPago orden) throws SQLException {
        Timestamp ahora = Timestamp.from(Instant.now());
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO transaccion (id_orden, cliente_id, medio, monto, moneda, estado, codigo, mensaje,"
                        + " creada, actualizada) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, orden.idOrden);
            ps.setString(2, orden.clienteId);
            ps.setString(3, orden.medio);
            ps.setLong(4, orden.monto);
            ps.setString(5, orden.moneda);
            ps.setString(6, EstadoPago.Pendiente.name());
            ps.setString(7, "REGISTRADA");
            ps.setString(8, "Orden recibida");
            ps.setTimestamp(9, ahora);
            ps.setTimestamp(10, ahora);
            ps.executeUpdate();
        }
    }

    /** Lee el estado y bloquea la fila hasta el fin de la transacción. */
    private EstadoPago estadoBloqueado(Connection c, String idOrden) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT estado FROM transaccion WHERE id_orden = ? FOR UPDATE")) {
            ps.setString(1, idOrden);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? EstadoPago.valueOf(rs.getString(1)) : null;
            }
        }
    }

    private void auditar(Connection c, String idOrden, EstadoPago anterior, EstadoPago nuevo, String detalle)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO auditoria (id_orden, estado_anterior, estado_nuevo, detalle, fecha)"
                        + " VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, idOrden);
            ps.setString(2, anterior == null ? null : anterior.name());
            ps.setString(3, nuevo.name());
            ps.setString(4, detalle);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    private IllegalStateException errorDeBaseDatos(String operacion, SQLException e) {
        Consola.error(COMPONENTE, "error al %s: %s", operacion, e.getMessage());
        return new IllegalStateException("Error de base de datos al " + operacion, e);
    }
}
