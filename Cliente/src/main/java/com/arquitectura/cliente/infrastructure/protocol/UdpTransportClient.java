package com.arquitectura.cliente.infrastructure.protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Cliente UDP para comunicación con el servidor.
 * UDP solo soporta JSON (no payloads binarios).
 */
public class UdpTransportClient implements TransportClient {

    private static final int MAX_DATAGRAM_SIZE = 65507;

    private DatagramSocket socket;
    private InetAddress address;
    private int port;
    private int timeoutMs = 5000;

    @Override
    public void connect(String host, int portNum) throws IOException {
        socket = new DatagramSocket();
        socket.setSoTimeout(timeoutMs);
        address = InetAddress.getByName(host);
        this.port = portNum;
    }

    @Override
    public void disconnect() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    @Override
    public String sendRequest(String requestJson) throws IOException {
        if (requestJson.getBytes(StandardCharsets.UTF_8).length > MAX_DATAGRAM_SIZE) {
            throw new IOException("Request UDP demasiado grande (máx " + MAX_DATAGRAM_SIZE + " bytes)");
        }

        byte[] sendData = requestJson.getBytes(StandardCharsets.UTF_8);
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);
        socket.send(sendPacket);

        byte[] receiveBuffer = new byte[MAX_DATAGRAM_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        socket.receive(receivePacket);

        return new String(receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength(), StandardCharsets.UTF_8);
    }

    @Override
    public String sendRequestWithPayload(String requestJson, byte[] payload) throws IOException {
        // UDP no soporta payloads binarios
        throw new IOException("UDP no soporta envío de archivos binarios. Use TCP");
    }

    @Override
    public byte[] receivePayload(long expectedLength) throws IOException {
        throw new IOException("UDP no soporta recepción de archivos binarios. Use TCP");
    }

    @Override
    public void setTimeout(int timeoutMs) {
        this.timeoutMs = timeoutMs;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.setSoTimeout(timeoutMs);
            } catch (Exception e) {
                // Ignorar si no se puede establecer
            }
        }
    }
}

