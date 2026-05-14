package com.app.client.net;

import java.time.Instant;
import java.util.Objects;

/**
 * Servidor descubierto en la LAN via UDP broadcast (PEER_HELLO).
 *
 * <p>El cliente conserva una lista de estos para mostrar al usuario los
 * servidores online y permitirle seleccionar a cual conectarse.</p>
 */
public final class DiscoveredServer {

    private final String id;
    private final String nombre;
    private final String host;
    private final int puertoTcp;
    private final int puertoUdp;
    private final int puertoPeer;
    private volatile Instant ultimaSenal;

    public DiscoveredServer(String id, String host, int puertoTcp, int puertoUdp, int puertoPeer) {
        this(id, null, host, puertoTcp, puertoUdp, puertoPeer);
    }

    public DiscoveredServer(String id, String nombre, String host, int puertoTcp, int puertoUdp, int puertoPeer) {
        this.id = Objects.requireNonNull(id);
        this.nombre = (nombre == null || nombre.isBlank()) ? host : nombre;
        this.host = Objects.requireNonNull(host);
        this.puertoTcp = puertoTcp;
        this.puertoUdp = puertoUdp;
        this.puertoPeer = puertoPeer;
        this.ultimaSenal = Instant.now();
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getHost() { return host; }
    public int getPuertoTcp() { return puertoTcp; }
    public int getPuertoUdp() { return puertoUdp; }
    public int getPuertoPeer() { return puertoPeer; }
    public Instant getUltimaSenal() { return ultimaSenal; }

    public void marcarVisto() { this.ultimaSenal = Instant.now(); }

    public boolean expirado(long ttlMillis) {
        return Instant.now().toEpochMilli() - ultimaSenal.toEpochMilli() > ttlMillis;
    }

    public String shortId() {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiscoveredServer d)) return false;
        return id.equals(d.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return nombre + " " + host + " (TCP " + puertoTcp + ", UDP " + puertoUdp + ")";
    }
}
