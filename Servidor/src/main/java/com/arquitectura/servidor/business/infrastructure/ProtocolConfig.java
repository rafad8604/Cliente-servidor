package com.arquitectura.servidor.business.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:protocol.properties")
public class ProtocolConfig {

    @Value("${server.protocol}")
    private String protocolString;

    @Value("${server.port}")
    private int port;

    private CommunicationProtocol protocol;

    public CommunicationProtocol getProtocol() {
        if (protocol == null) {
            protocol = CommunicationProtocol.fromString(protocolString);
        }
        return protocol;
    }

    public int getPort() {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Puerto debe estar entre 1 y 65535, recibido: " + port);
        }
        return port;
    }

    public String getProtocolName() {
        return getProtocol().getName();
    }

    public String getProtocolDescription() {
        return getProtocol().getDescription();
    }

    public void setProtocol(String protocolString) {
        this.protocol = CommunicationProtocol.fromString(protocolString);
        this.protocolString = protocolString;
    }

    public void setPort(int port) {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Puerto debe estar entre 1 y 65535, recibido: " + port);
        }
        this.port = port;
    }
}


