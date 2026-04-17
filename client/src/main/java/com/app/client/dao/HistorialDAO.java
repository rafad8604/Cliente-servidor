package com.app.client.dao;

import com.app.client.models.HistorialDocumento;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla 'historial_documentos' en H2 (cliente).
 */
public class HistorialDAO {

    private final H2Connection h2;

    public HistorialDAO() {
        this.h2 = H2Connection.getInstance();
    }

    /**
     * Registra un evento en el historial del cliente.
     */
    public void registrar(HistorialDocumento historial) throws SQLException {
        String sql = "INSERT INTO historial_documentos (nombre, tamano, tipo, direccion, fecha) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = h2.getConnection().prepareStatement(sql)) {
            ps.setString(1, historial.getNombre());
            ps.setLong(2, historial.getTamano());
            ps.setString(3, historial.getTipo());
            ps.setString(4, historial.getDireccion().name());
            ps.setTimestamp(5, Timestamp.valueOf(historial.getFecha()));
            ps.executeUpdate();
        }
    }

    /**
     * Lista todo el historial, más reciente primero.
     */
    public List<HistorialDocumento> listarTodo() throws SQLException {
        String sql = "SELECT * FROM historial_documentos ORDER BY fecha DESC";
        List<HistorialDocumento> lista = new ArrayList<>();
        try (PreparedStatement ps = h2.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapFromResultSet(rs));
            }
        }
        return lista;
    }

    /**
     * Lista el historial filtrado por dirección.
     */
    public List<HistorialDocumento> listarPorDireccion(HistorialDocumento.Direccion dir) throws SQLException {
        String sql = "SELECT * FROM historial_documentos WHERE direccion = ? ORDER BY fecha DESC";
        List<HistorialDocumento> lista = new ArrayList<>();
        try (PreparedStatement ps = h2.getConnection().prepareStatement(sql)) {
            ps.setString(1, dir.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapFromResultSet(rs));
                }
            }
        }
        return lista;
    }

    private HistorialDocumento mapFromResultSet(ResultSet rs) throws SQLException {
        HistorialDocumento h = new HistorialDocumento();
        h.setId(rs.getLong("id"));
        h.setNombre(rs.getString("nombre"));
        h.setTamano(rs.getLong("tamano"));
        h.setTipo(rs.getString("tipo"));
        h.setDireccion(HistorialDocumento.Direccion.valueOf(rs.getString("direccion")));
        h.setFecha(rs.getTimestamp("fecha").toLocalDateTime());
        return h;
    }
}
