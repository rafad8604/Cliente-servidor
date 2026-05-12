package com.app.server.peer;

import java.time.Instant;
import java.util.Objects;

/**
 * Informacion de un peer conocido.
 *
 * Inmutable salvo por {@code ultimaSenal}, que se actualiza con cada heartbeat
 * recibido. Identidad por {@code id} (UUID generado al iniciar cada servidor).
 */
public final class PeerInfo {

    private final String id;
    private final String host;
    private final int puertoPeer;
    private final int puertoTcp;
    private final int puertoUdp;
    private volatile Instant ultimaSenal;

    public PeerInfo(String id, String host, int puertoPeer, int puertoTcp, int puertoUdp) {
        this.id = Objects.requireNonNull(id, "id");
        this.host = Objects.requireNonNull(host, "host");
        this.puertoPeer = puertoPeer;
        this.puertoTcp = puertoTcp;
        this.puertoUdp = puertoUdp;
        this.ultimaSenal = Instant.now();
    }

    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPuertoPeer() { return puertoPeer; }
    public int getPuertoTcp() { return puertoTcp; }
    public int getPuertoUdp() { return puertoUdp; }
    public Instant getUltimaSenal() { return ultimaSenal; }

    public void marcarVisto() {
        this.ultimaSenal = Instant.now();
    }

    public boolean expirado(long ttlMillis) {
        return Instant.now().toEpochMilli() - ultimaSenal.toEpochMilli() > ttlMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PeerInfo other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "Peer[" + id.substring(0, Math.min(8, id.length())) + " " + host + ":" + puertoPeer + "]";
    }
}
