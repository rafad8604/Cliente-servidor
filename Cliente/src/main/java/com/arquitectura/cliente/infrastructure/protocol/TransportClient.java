package com.arquitectura.cliente.infrastructure.protocol;

import java.io.IOException;

/**
 * Interfaz para adaptadores de protocolo TCP y UDP.
 */
public interface TransportClient {
    void connect(String host, int port) throws IOException;
    void disconnect() throws IOException;
    boolean isConnected();
    String sendRequest(String requestJson) throws IOException;
    String sendRequestWithPayload(String requestJson, byte[] payload) throws IOException;
    byte[] receivePayload(long expectedLength) throws IOException;
    void setTimeout(int timeoutMs);
}

