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

    /**
     * Alcance de visibilidad en el catalogo publico vs entrega dirigida.
     */
    public enum EnvioAlcance {
        TODOS,
        DIRIGIDO
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

    private EnvioAlcance envioAlcance = EnvioAlcance.TODOS;
    private String destIp;
    private Integer destPuerto;
    private String destProtocolo;
    private String origenServidorEtiqueta;
    private String origenPeerId;
    private String remitenteNombre;
    private Integer remitentePuerto;
    private String remitenteProtocolo;

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

    public EnvioAlcance getEnvioAlcance() {
        return envioAlcance != null ? envioAlcance : EnvioAlcance.TODOS;
    }

    public void setEnvioAlcance(EnvioAlcance envioAlcance) {
        this.envioAlcance = envioAlcance != null ? envioAlcance : EnvioAlcance.TODOS;
    }

    public String getDestIp() {
        return destIp;
    }

    public void setDestIp(String destIp) {
        this.destIp = destIp;
    }

    public Integer getDestPuerto() {
        return destPuerto;
    }

    public void setDestPuerto(Integer destPuerto) {
        this.destPuerto = destPuerto;
    }

    public String getDestProtocolo() {
        return destProtocolo;
    }

    public void setDestProtocolo(String destProtocolo) {
        this.destProtocolo = destProtocolo;
    }

    public String getOrigenServidorEtiqueta() {
        return origenServidorEtiqueta;
    }

    public void setOrigenServidorEtiqueta(String origenServidorEtiqueta) {
        this.origenServidorEtiqueta = origenServidorEtiqueta;
    }

    public String getOrigenPeerId() {
        return origenPeerId;
    }

    public void setOrigenPeerId(String origenPeerId) {
        this.origenPeerId = origenPeerId;
    }

    public String getRemitenteNombre() {
        return remitenteNombre;
    }

    public void setRemitenteNombre(String remitenteNombre) {
        this.remitenteNombre = remitenteNombre;
    }

    public Integer getRemitentePuerto() {
        return remitentePuerto;
    }

    public void setRemitentePuerto(Integer remitentePuerto) {
        this.remitentePuerto = remitentePuerto;
    }

    public String getRemitenteProtocolo() {
        return remitenteProtocolo;
    }

    public void setRemitenteProtocolo(String remitenteProtocolo) {
        this.remitenteProtocolo = remitenteProtocolo;
    }

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
