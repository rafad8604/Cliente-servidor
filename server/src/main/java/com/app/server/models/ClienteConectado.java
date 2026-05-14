package com.app.server.models;

import java.time.LocalDateTime;

/**
 * Modelo para la tabla 'clientes_conectados' del servidor.
 */
public class ClienteConectado {

    private String ip;
    private int puerto;
    private String protocolo; // "TCP" o "UDP"
    private LocalDateTime fechaInicio;
    private String nombre;

    public ClienteConectado() {
    }

    public ClienteConectado(String ip, int puerto, String protocolo) {
        this.ip = ip;
        this.puerto = puerto;
        this.protocolo = protocolo;
        this.fechaInicio = LocalDateTime.now();
        this.nombre = "";
    }

    // --- Getters y Setters ---

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPuerto() {
        return puerto;
    }

    public void setPuerto(int puerto) {
        this.puerto = puerto;
    }

    public String getProtocolo() {
        return protocolo;
    }

    public void setProtocolo(String protocolo) {
        this.protocolo = protocolo;
    }

    public LocalDateTime getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDateTime fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre != null ? nombre : "";
    }

    @Override
    public String toString() {
        return "ClienteConectado{ip='" + ip + "', puerto=" + puerto +
                ", protocolo='" + protocolo + "', desde=" + fechaInicio +
                (nombre != null && !nombre.isEmpty() ? ", nombre='" + nombre + "'" : "") + '}';
    }
}
