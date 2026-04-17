package com.app.server.dao;

import com.app.server.models.Log;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla 'logs'.
 */
public class LogDAO {

    private final DatabaseConnection dbPool;

    public LogDAO() {
        this.dbPool = DatabaseConnection.getInstance();
    }

    /**
     * Registra un nuevo log en la base de datos.
     */
    public void insertar(Log log) throws SQLException {
        String sql = "INSERT INTO logs (accion, ip_origen, fecha_hora, detalles) VALUES (?, ?, ?, ?)";
        Connection conn = dbPool.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, log.getAccion());
            ps.setString(2, log.getIpOrigen());
            ps.setTimestamp(3, Timestamp.valueOf(log.getFechaHora()));
            ps.setString(4, log.getDetalles());
            ps.executeUpdate();
        } finally {
            dbPool.releaseConnection(conn);
        }
    }

    /**
     * Lista los últimos N logs.
     */
    public List<Log> listarUltimos(int limit) throws SQLException {
        String sql = "SELECT * FROM logs ORDER BY fecha_hora DESC LIMIT ?";
        List<Log> logs = new ArrayList<>();
        Connection conn = dbPool.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapFromResultSet(rs));
                }
            }
        } finally {
            dbPool.releaseConnection(conn);
        }
        return logs;
    }

    private Log mapFromResultSet(ResultSet rs) throws SQLException {
        Log log = new Log();
        log.setId(rs.getLong("id"));
        log.setAccion(rs.getString("accion"));
        log.setIpOrigen(rs.getString("ip_origen"));
        log.setFechaHora(rs.getTimestamp("fecha_hora").toLocalDateTime());
        log.setDetalles(rs.getString("detalles"));
        return log;
    }
}
