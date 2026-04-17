package com.app.client.models;

import java.time.LocalDateTime;

/**
 * Modelo para la tabla 'historial_documentos' en la base H2 del cliente.
 */
public class HistorialDocumento {

    /**
     * Dirección del documento: enviado al servidor o recibido del servidor.
     */
    public enum Direccion {
        ENVIADO, RECIBIDO
    }

    private long id;
    private String nombre;
    private long tamano;
    private String tipo; // "MENSAJE" o "ARCHIVO"
    private Direccion direccion;
    private LocalDateTime fecha;

    public HistorialDocumento() {
    }

    public HistorialDocumento(String nombre, long tamano, String tipo, Direccion direccion) {
        this.nombre = nombre;
        this.tamano = tamano;
        this.tipo = tipo;
        this.direccion = direccion;
        this.fecha = LocalDateTime.now();
    }

    // --- Getters y Setters ---

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public long getTamano() {
        return tamano;
    }

    public void setTamano(long tamano) {
        this.tamano = tamano;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Direccion getDireccion() {
        return direccion;
    }

    public void setDireccion(Direccion direccion) {
        this.direccion = direccion;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    @Override
    public String toString() {
        return "HistorialDocumento{id=" + id + ", nombre='" + nombre + "', tipo='" + tipo +
                "', direccion=" + direccion + ", fecha=" + fecha + '}';
    }
}
