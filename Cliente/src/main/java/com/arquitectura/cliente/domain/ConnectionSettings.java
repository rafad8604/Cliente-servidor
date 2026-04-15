package com.arquitectura.cliente.domain;

public record ConnectionSettings(String host, int port, TransportProtocol protocol) {

    public ConnectionSettings {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("La direccion IP/host es obligatoria");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535");
        }
        if (protocol == null) {
            throw new IllegalArgumentException("El protocolo es obligatorio");
        }
    }
}

