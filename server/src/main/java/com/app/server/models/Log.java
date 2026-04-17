package com.app.server.models;

import java.time.LocalDateTime;

/**
 * Modelo para la tabla 'logs' del servidor.
 */
public class Log {

    private long id;
    private String accion;
    private String ipOrigen;
    private LocalDateTime fechaHora;
    private String detalles;

    public Log() {
    }

    public Log(String accion, String ipOrigen, String detalles) {
        this.accion = accion;
        this.ipOrigen = ipOrigen;
        this.fechaHora = LocalDateTime.now();
        this.detalles = detalles;
    }

    // --- Getters y Setters ---

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    public String getIpOrigen() {
        return ipOrigen;
    }

    public void setIpOrigen(String ipOrigen) {
        this.ipOrigen = ipOrigen;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public String getDetalles() {
        return detalles;
    }

    public void setDetalles(String detalles) {
        this.detalles = detalles;
    }

    @Override
    public String toString() {
        return "Log{id=" + id + ", accion='" + accion + "', ip='" + ipOrigen + "', fecha=" + fechaHora + '}';
    }
}
