package com.arquitectura.cliente.infrastructure.protocol;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Cliente TCP para comunicación con el servidor.
 * Utiliza JSON por línea para requests y payloadLength + datos binarios para archivo.
 */
public class TcpTransportClient implements TransportClient {

    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private int timeoutMs = 5000;

    @Override
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(timeoutMs);
        input = socket.getInputStream();
        output = socket.getOutputStream();
    }

    @Override
    public void disconnect() throws IOException {
        if (input != null) input.close();
        if (output != null) output.close();
        if (socket != null) socket.close();
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public String sendRequest(String requestJson) throws IOException {
        writeJsonLine(requestJson);
        return readJsonLine();
    }

    @Override
    public String sendRequestWithPayload(String requestJson, byte[] payload) throws IOException {
        writeJsonLine(requestJson);
        if (payload != null && payload.length > 0) {
            output.write(payload);
            output.flush();
        }
        return readJsonLine();
    }

    @Override
    public byte[] receivePayload(long expectedLength) throws IOException {
        byte[] buffer = new byte[(int) Math.min(expectedLength, 8192)];
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        long received = 0;

        while (received < expectedLength) {
            int toRead = (int) Math.min(buffer.length, expectedLength - received);
            int read = input.read(buffer, 0, toRead);
            if (read == -1) {
                throw new IOException("Conexión cerrada antes de recibir todo el payload");
            }
            result.write(buffer, 0, read);
            received += read;
        }

        return result.toByteArray();
    }

    @Override
    public void setTimeout(int timeoutMs) {
        this.timeoutMs = timeoutMs;
        if (socket != null) {
            try {
                socket.setSoTimeout(timeoutMs);
            } catch (Exception e) {
                // Ignorar si no se puede establecer
            }
        }
    }

    private void writeJsonLine(String json) throws IOException {
        output.write((json + "\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private String readJsonLine() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int read;
        while ((read = input.read()) != -1) {
            if (read == '\n') {
                break;
            }
            buffer.write(read);
        }
        return buffer.toString(StandardCharsets.UTF_8).trim();
    }
}

