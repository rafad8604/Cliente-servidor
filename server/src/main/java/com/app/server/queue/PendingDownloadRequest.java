package com.app.server.queue;

import java.time.Instant;

/**
 * Solicitud de descarga en espera mientras un peer remoto esta desconectado.
 */
public final class PendingDownloadRequest {

    private final long documentoId;
    private final String peerId;
    private final String clienteIp;
    private final int clientePuerto;
    private final String clienteProtocolo;
    private final Instant encoladaEn;
    private int intentos;

    public PendingDownloadRequest(long documentoId,
                                  String peerId,
                                  String clienteIp,
                                  int clientePuerto,
                                  String clienteProtocolo) {
        this.documentoId = documentoId;
        this.peerId = peerId;
        this.clienteIp = clienteIp;
        this.clientePuerto = clientePuerto;
        this.clienteProtocolo = clienteProtocolo;
        this.encoladaEn = Instant.now();
        this.intentos = 0;
    }

    public long getDocumentoId() {
        return documentoId;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getClienteIp() {
        return clienteIp;
    }

    public int getClientePuerto() {
        return clientePuerto;
    }

    public String getClienteProtocolo() {
        return clienteProtocolo;
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