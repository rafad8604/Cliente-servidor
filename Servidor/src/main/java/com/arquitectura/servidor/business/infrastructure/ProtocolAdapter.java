package com.arquitectura.servidor.business.infrastructure;

public interface ProtocolAdapter {

    CommunicationProtocol protocol();

    void start(int port);

    void stop();

    boolean isListening();

    String description();
}

