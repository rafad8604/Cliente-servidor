package com.arquitectura.cliente.application;

import com.arquitectura.cliente.domain.ConnectionSettings;
import com.arquitectura.cliente.domain.TransportProtocol;
import com.arquitectura.cliente.infrastructure.protocol.TcpTransportClient;
import com.arquitectura.cliente.infrastructure.protocol.TransportClient;
import com.arquitectura.cliente.infrastructure.protocol.UdpTransportClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Gestor centralizado de conexión al servidor.
 * Maneja la creación y ciclo de vida del cliente TCP/UDP.
 */
@Service
public class ConnectionManager {

    private final ObjectMapper objectMapper;
    @Value("${client.network.tcp-timeout-ms:5000}")
    private int tcpTimeoutMs;
    @Value("${client.network.udp-timeout-ms:5000}")
    private int udpTimeoutMs;

    private TransportClient currentClient;
    private ConnectionSettings currentSettings;

    public ConnectionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void connect(String host, int port, String protocol) throws IOException {
        TransportProtocol proto = TransportProtocol.fromString(protocol);
        ConnectionSettings settings = new ConnectionSettings(host, port, proto);

        TransportClient client = createClient(proto);
        client.connect(host, port);

        this.currentClient = client;
        this.currentSettings = settings;
    }

    public void disconnect() throws IOException {
        if (currentClient != null) {
            currentClient.disconnect();
            currentClient = null;
            currentSettings = null;
        }
    }

    public boolean isConnected() {
        return currentClient != null && currentClient.isConnected();
    }

    public ConnectionSettings getSettings() {
        return currentSettings;
    }

    public String sendRequest(String requestJson) throws IOException {
        if (!isConnected()) {
            throw new IllegalStateException("No conectado al servidor");
        }
        return currentClient.sendRequest(requestJson);
    }

    public String sendRequestWithPayload(String requestJson, byte[] payload) throws IOException {
        if (!isConnected()) {
            throw new IllegalStateException("No conectado al servidor");
        }
        return currentClient.sendRequestWithPayload(requestJson, payload);
    }

    public byte[] receivePayload(long expectedLength) throws IOException {
        if (!isConnected()) {
            throw new IllegalStateException("No conectado al servidor");
        }
        return currentClient.receivePayload(expectedLength);
    }

    private TransportClient createClient(TransportProtocol protocol) {
        return switch (protocol) {
            case TCP -> {
                TcpTransportClient client = new TcpTransportClient();
                client.setTimeout(tcpTimeoutMs);
                yield client;
            }
            case UDP -> {
                UdpTransportClient client = new UdpTransportClient();
                client.setTimeout(udpTimeoutMs);
                yield client;
            }
        };
    }
}

