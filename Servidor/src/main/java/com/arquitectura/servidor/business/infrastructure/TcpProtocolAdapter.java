package com.arquitectura.servidor.business.infrastructure;

import com.arquitectura.servidor.business.activity.ServerActivitySource;
import com.arquitectura.servidor.business.user.UserConnection;
import com.arquitectura.servidor.business.user.UserConnectionLimitExceededException;
import com.arquitectura.servidor.business.user.UserConnectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class TcpProtocolAdapter implements ProtocolAdapter {

    private static final int MAX_REQUEST_HEADER_BYTES = 64 * 1024;

    private final ProtocolRequestRouter requestRouter;
    private final UserConnectionService userConnectionService;
    private final ServerActivitySource activitySource;
    private final ObjectMapper objectMapper;
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();

    private volatile boolean listening;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public TcpProtocolAdapter(ProtocolRequestRouter requestRouter,
                              UserConnectionService userConnectionService,
                              ServerActivitySource activitySource,
                              ObjectMapper objectMapper) {
        this.requestRouter = requestRouter;
        this.userConnectionService = userConnectionService;
        this.activitySource = activitySource;
        this.objectMapper = objectMapper;
    }

    @Override
    public CommunicationProtocol protocol() {
        return CommunicationProtocol.TCP;
    }

    @Override
    public void start(int port) {
        if (listening) {
            return;
        }
        try {
            serverSocket = new ServerSocket(port);
            listening = true;
            acceptThread = new Thread(this::acceptLoop, "tcp-accept-loop");
            acceptThread.setDaemon(true);
            acceptThread.start();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo iniciar listener TCP en puerto " + port, e);
        }
    }

    @Override
    public void stop() {
        listening = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
    }

    @Override
    public boolean isListening() {
        return listening;
    }

    @Override
    public String description() {
        return "Transmission Control Protocol - conexion confiable";
    }

    private void acceptLoop() {
        while (listening && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                clientExecutor.submit(() -> handleClient(socket));
            } catch (IOException e) {
                if (listening) {
                    activitySource.emitActivity("PROTOCOLO_TCP_ERROR", "Error aceptando conexion TCP: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        String ip = socket.getInetAddress().getHostAddress();
        UserConnection connection;
        try {
            connection = userConnectionService.connect("tcp-" + ip, ip);
        } catch (UserConnectionLimitExceededException e) {
            writeRejection(socket, e.getMessage());
            closeQuietly(socket);
            return;
        }

        activitySource.emitActivity("TCP_CLIENTE", "Cliente TCP conectado desde " + ip);
        try (socket;
             InputStream input = socket.getInputStream();
             OutputStream output = socket.getOutputStream()) {

            while (listening && !socket.isClosed()) {
                String requestJson = readJsonLine(input);
                if (requestJson == null) {
                    break;
                }

                long payloadLength = extractPayloadLength(requestJson);
                InputStream payload = payloadLength > 0 ? new BoundedInputStream(input, payloadLength) : null;
                ProtocolResponse response = requestRouter.route(requestJson, payload, ip);

                writeJsonLine(output, response.json());
                if (response.binaryPayload() != null) {
                    try (InputStream responseStream = response.binaryPayload()) {
                        responseStream.transferTo(output);
                        output.flush();
                    }
                }
            }
        } catch (Exception e) {
            activitySource.emitActivity("TCP_CLIENTE_ERROR", "Error en cliente TCP " + ip + ": " + e.getMessage());
        } finally {
            userConnectionService.disconnect(connection.userId());
            activitySource.emitActivity("TCP_CLIENTE", "Cliente TCP desconectado " + ip);
        }
    }

    private long extractPayloadLength(String requestJson) {
        try {
            JsonNode node = objectMapper.readTree(requestJson);
            JsonNode payloadLengthNode = node.get("payloadLength");
            if (payloadLengthNode == null) {
                return 0L;
            }
            return payloadLengthNode.asLong(0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    private void writeRejection(Socket socket, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", "CONNECTION_REJECTED");
        payload.put("message", reason);
        try (socket;
             OutputStream output = socket.getOutputStream()) {
            writeJsonLine(output, objectMapper.writeValueAsString(payload));
        } catch (Exception ignored) {
        }
    }

    private String readJsonLine(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            int read = input.read();
            if (read == -1) {
                if (buffer.size() == 0) {
                    return null;
                }
                break;
            }
            if (read == '\n') {
                break;
            }
            buffer.write(read);
            if (buffer.size() > MAX_REQUEST_HEADER_BYTES) {
                throw new IllegalArgumentException("Cabecera JSON demasiado grande");
            }
        }
        return buffer.toString(StandardCharsets.UTF_8).trim();
    }

    private void writeJsonLine(OutputStream output, String json) throws IOException {
        output.write((json + "\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static class BoundedInputStream extends InputStream {

        private final InputStream delegate;
        private long remaining;

        BoundedInputStream(InputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int read = delegate.read();
            if (read != -1) {
                remaining--;
            }
            return read;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int maxRead = (int) Math.min(len, remaining);
            int read = delegate.read(b, off, maxRead);
            if (read != -1) {
                remaining -= read;
            }
            return read;
        }
    }
}

