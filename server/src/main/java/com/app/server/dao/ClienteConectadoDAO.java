package com.app.server.dao;

import com.app.server.models.ClienteConectado;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla 'clientes_conectados'.
 */
public class ClienteConectadoDAO {

    private final DatabaseConnection dbPool;

    public ClienteConectadoDAO() {
        this.dbPool = DatabaseConnection.getInstance();
    }

    /**
     * Registra un nuevo cliente conectado. Si ya existe, actualiza la fecha.
     */
    public void registrar(ClienteConectado cliente) throws SQLException {
        String sql = "INSERT INTO clientes_conectados (ip, puerto, protocolo, fecha_inicio, nombre) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE fecha_inicio = VALUES(fecha_inicio), " +
                "protocolo = VALUES(protocolo), nombre = VALUES(nombre)";
        Connection conn = dbPool.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cliente.getIp());
            ps.setInt(2, cliente.getPuerto());
            ps.setString(3, cliente.getProtocolo());
            ps.setTimestamp(4, Timestamp.valueOf(cliente.getFechaInicio()));
            ps.setString(5, cliente.getNombre() != null ? cliente.getNombre() : "");
            ps.executeUpdate();
        } catch (SQLException e) {
            // Fallback si la columna 'nombre' aun no existe en el esquema
            String sqlLegacy = "INSERT INTO clientes_conectados (ip, puerto, protocolo, fecha_inicio) " +
                    "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE fecha_inicio = VALUES(fecha_inicio), " +
                    "protocolo = VALUES(protocolo)";
            try (PreparedStatement ps = conn.prepareStatement(sqlLegacy)) {
                ps.setString(1, cliente.getIp());
                ps.setInt(2, cliente.getPuerto());
                ps.setString(3, cliente.getProtocolo());
                ps.setTimestamp(4, Timestamp.valueOf(cliente.getFechaInicio()));
                ps.executeUpdate();
            }
        } finally {
            dbPool.releaseConnection(conn);
        }
    }

    public void actualizarNombre(String ip, int puerto, String nombre) throws SQLException {
        String sql = "UPDATE clientes_conectados SET nombre = ? WHERE ip = ? AND puerto = ?";
        Connection conn = dbPool.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre != null ? nombre : "");
            ps.setString(2, ip);
            ps.setInt(3, puerto);
            ps.executeUpdate();
        } finally {
            dbPool.releaseConnection(conn);
        }
    }

    /**
     * Elimina un cliente al desconectarse.
     */
    public void eliminar(String ip, int puerto) throws SQLException {
        String sql = "DELETE FROM clientes_conectados WHERE ip = ? AND puerto = ?";
        Connection conn = dbPool.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.setInt(2, puerto);
            ps.executeUpdate();
        } finally {
            dbPool.releaseConnection(conn);
        }
    }

    /**
     * Lista todos los clientes actualmente conectados.
     */
    public List<ClienteConectado> listarTodos() throws SQLException {
        String sql = "SELECT * FROM clientes_conectados ORDER BY fecha_inicio DESC";
        List<ClienteConectado> clientes = new ArrayList<>();
        Connection conn = dbPool.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ClienteConectado c = new ClienteConectado();
                c.setIp(rs.getString("ip"));
                c.setPuerto(rs.getInt("puerto"));
                c.setProtocolo(rs.getString("protocolo"));
                c.setFechaInicio(rs.getTimestamp("fecha_inicio").toLocalDateTime());
                try { c.setNombre(rs.getString("nombre")); } catch (Exception ignored) { }
                clientes.add(c);
            }
        } finally {
            dbPool.releaseConnection(conn);
        }
        return clientes;
    }

    /**
     * Limpia todos los clientes conectados (para reinicio del servidor).
     */
    public void limpiarTodos() throws SQLException {
        String sql = "DELETE FROM clientes_conectados";
        Connection conn = dbPool.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } finally {
            dbPool.releaseConnection(conn);
        }
    }
}
