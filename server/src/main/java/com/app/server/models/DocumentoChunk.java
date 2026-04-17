package com.app.server.models;

/**
 * Modelo para la tabla 'documentos_chunks' del servidor.
 * Cada chunk es un bloque de datos encriptados de hasta 50MB.
 */
public class DocumentoChunk {

    private long id;
    private long documentoId;
    private int chunkIndex;
    private byte[] datosEncriptados;

    public DocumentoChunk() {
    }

    public DocumentoChunk(long documentoId, int chunkIndex, byte[] datosEncriptados) {
        this.documentoId = documentoId;
        this.chunkIndex = chunkIndex;
        this.datosEncriptados = datosEncriptados;
    }

    // --- Getters y Setters ---

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getDocumentoId() {
        return documentoId;
    }

    public void setDocumentoId(long documentoId) {
        this.documentoId = documentoId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public byte[] getDatosEncriptados() {
        return datosEncriptados;
    }

    public void setDatosEncriptados(byte[] datosEncriptados) {
        this.datosEncriptados = datosEncriptados;
    }

    @Override
    public String toString() {
        return "DocumentoChunk{id=" + id + ", documentoId=" + documentoId +
                ", chunkIndex=" + chunkIndex + ", size=" +
                (datosEncriptados != null ? datosEncriptados.length : 0) + " bytes}";
    }
}
