package com.app.server.net;

/**
 * Contexto de un cliente independiente del protocolo concreto.
 * Encapsula IP, puerto y protocolo ("TCP" o "UDP") y se utiliza en todas las
 * capas superiores para evitar depender de {@link java.net.Socket} o
 * {@link java.net.DatagramSocket}.
 */
public final class ClientContext {

    private final String ip;
    private final int port;
    private final String protocol;

    public ClientContext(String ip, int port, String protocol) {
        this.ip = ip;
        this.port = port;
        this.protocol = protocol;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }

    @Override
    public String toString() {
        return protocol + ":" + ip + ":" + port;
    }
}
