package com.arquitectura.servidor.business.infrastructure;

public enum CommunicationProtocol {
    TCP("TCP", "Transmission Control Protocol - conexion confiable"),
    UDP("UDP", "User Datagram Protocol - conexion sin garantia");

    private final String name;
    private final String description;

    CommunicationProtocol(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public static CommunicationProtocol fromString(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            throw new IllegalArgumentException("Protocolo no configurado. Debe ser TCP o UDP");
        }
        try {
            return CommunicationProtocol.valueOf(protocol.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Protocolo desconocido: " + protocol + ". Validos: TCP, UDP", e);
        }
    }
}


