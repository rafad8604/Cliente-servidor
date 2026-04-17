package com.app.server.models;

import java.time.LocalDateTime;

/**
 * Modelo para la tabla 'documentos' del servidor.
 */
public class Documento {

    /**
     * Tipo de documento: mensaje de texto o archivo binario.
     */
    public enum Tipo {
        MENSAJE, ARCHIVO
    }

    private long id;
    private String nombre;
    private String extension;
    private long tamano;
    private String rutaLocalOriginal;
    private String hashSha256;
    private String ipPropietario;
    private Tipo tipo;
    private LocalDateTime fechaCreacion;

    public Documento() {
    }

    public Documento(String nombre, String extension, long tamano, String rutaLocalOriginal,
                     String hashSha256, String ipPropietario, Tipo tipo) {
        this.nombre = nombre;
        this.extension = extension;
        this.tamano = tamano;
        this.rutaLocalOriginal = rutaLocalOriginal;
        this.hashSha256 = hashSha256;
        this.ipPropietario = ipPropietario;
        this.tipo = tipo;
        this.fechaCreacion = LocalDateTime.now();
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

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public long getTamano() {
        return tamano;
    }

    public void setTamano(long tamano) {
        this.tamano = tamano;
    }

    public String getRutaLocalOriginal() {
        return rutaLocalOriginal;
    }

    public void setRutaLocalOriginal(String rutaLocalOriginal) {
        this.rutaLocalOriginal = rutaLocalOriginal;
    }

    public String getHashSha256() {
        return hashSha256;
    }

    public void setHashSha256(String hashSha256) {
        this.hashSha256 = hashSha256;
    }

    public String getIpPropietario() {
        return ipPropietario;
    }

    public void setIpPropietario(String ipPropietario) {
        this.ipPropietario = ipPropietario;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public void setTipo(Tipo tipo) {
        this.tipo = tipo;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    @Override
    public String toString() {
        return "Documento{id=" + id + ", nombre='" + nombre + "', tipo=" + tipo +
                ", tamano=" + tamano + ", hash='" + hashSha256 + "'}";
    }
}
