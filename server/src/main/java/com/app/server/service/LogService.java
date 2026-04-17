package com.app.server.service;

import com.app.server.models.Log;
import com.app.server.dao.LogDAO;

import java.sql.SQLException;

/**
 * Servicio para logging de acciones del servidor.
 */
public class LogService {

    private final LogDAO logDAO;

    public LogService() {
        this.logDAO = new LogDAO();
    }

    /**
     * Registra una acción en el log.
     */
    public void registrar(String accion, String ip, String detalles) {
        try {
            Log log = new Log(accion, ip, detalles);
            logDAO.insertar(log);
        } catch (SQLException e) {
            System.err.println("[LOG] Error registrando log: " + e.getMessage());
        }
    }

    /**
     * Log de conexión de un cliente.
     */
    public void logConexion(String ip, String protocolo) {
        registrar("CONEXION", ip, "Cliente conectado vía " + protocolo);
    }

    /**
     * Log de desconexión de un cliente.
     */
    public void logDesconexion(String ip) {
        registrar("DESCONEXION", ip, "Cliente desconectado");
    }

    /**
     * Log de archivo recibido.
     */
    public void logArchivoRecibido(String ip, String nombre, long tamano) {
        registrar("ARCHIVO_RECIBIDO", ip, "Archivo: " + nombre + " (" + tamano + " bytes)");
    }

    /**
     * Log de mensaje recibido.
     */
    public void logMensajeRecibido(String ip) {
        registrar("MENSAJE_RECIBIDO", ip, "Mensaje de texto recibido");
    }

    /**
     * Log de descarga.
     */
    public void logDescarga(String ip, String nombre, String tipo) {
        registrar("DESCARGA", ip, "Descarga " + tipo + ": " + nombre);
    }

    /**
     * Log de error.
     */
    public void logError(String ip, String detalle) {
        registrar("ERROR", ip, detalle);
    }
}
