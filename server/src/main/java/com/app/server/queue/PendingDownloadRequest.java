package com.app.server.queue;

import java.time.Instant;

import com.app.server.net.ClientHandler;

/**
 * Solicitud de descarga en espera mientras un peer remoto esta desconectado.
 */
public final class PendingDownloadRequest {

    private final long documentoId;
    private final String peerId;
    private final ClientHandler handler;
    private final Instant encoladaEn;
    private int intentos;

    public PendingDownloadRequest(long documentoId,
                                  String peerId,
                                  ClientHandler handler) {
        this.documentoId = documentoId;
        this.peerId = peerId;
        this.handler = handler;
        this.encoladaEn = Instant.now();
        this.intentos = 0;
    }

    public long getDocumentoId() {
        return documentoId;
    }

    public String getPeerId() {
        return peerId;
    }

    public ClientHandler getHandler() {
        return handler;
    }

    public Instant getEncoladaEn() {
        return encoladaEn;
    }

    public int incrementarIntentos() {
        intentos += 1;
        return intentos;
    }

    public int getIntentos() {
        return intentos;
    }
}