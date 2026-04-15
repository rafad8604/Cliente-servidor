package com.arquitectura.servidor.business.infrastructure;

import com.arquitectura.servidor.business.activity.ServerActivitySource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class UdpProtocolAdapter implements ProtocolAdapter {

    private static final int BUFFER_SIZE = 65507;

    private final ProtocolRequestRouter requestRouter;
    private final ServerActivitySource activitySource;
    private final ObjectMapper objectMapper;

    private volatile boolean listening;
    private DatagramSocket datagramSocket;
    private Thread listenerThread;

    public UdpProtocolAdapter(ProtocolRequestRouter requestRouter,
                              ServerActivitySource activitySource,
                              ObjectMapper objectMapper) {
        this.requestRouter = requestRouter;
        this.activitySource = activitySource;
        this.objectMapper = objectMapper;
    }

    @Override
    public CommunicationProtocol protocol() {
        return CommunicationProtocol.UDP;
    }

    @Override
    public void start(int port) {
        if (listening) {
            return;
        }
        try {
            datagramSocket = new DatagramSocket(port);
            listening = true;
            listenerThread = new Thread(this::receiveLoop, "udp-listener-loop");
            listenerThread.setDaemon(true);
            listenerThread.start();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo iniciar listener UDP en puerto " + port, e);
        }
    }

    @Override
    public void stop() {
        listening = false;
        if (datagramSocket != null && !datagramSocket.isClosed()) {
            datagramSocket.close();
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }

    @Override
    public boolean isListening() {
        return listening;
    }

    @Override
    public String description() {
        return "User Datagram Protocol - conexion sin garantia";
    }

    private void receiveLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (listening && datagramSocket != null && !datagramSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                datagramSocket.receive(packet);

                String requestJson = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                String clientIp = packet.getAddress().getHostAddress();
                String response = handleUdpRequest(requestJson, clientIp);
                byte[] payload = response.getBytes(StandardCharsets.UTF_8);

                DatagramPacket responsePacket = new DatagramPacket(payload, payload.length, packet.getAddress(), packet.getPort());
                datagramSocket.send(responsePacket);
            } catch (Exception e) {
                if (listening) {
                    activitySource.emitActivity("PROTOCOLO_UDP_ERROR", "Error procesando datagrama UDP: " + e.getMessage());
                }
            }
        }
    }

    private String handleUdpRequest(String requestJson, String clientIp) {
        try {
            JsonNode root = objectMapper.readTree(requestJson);
            String service = root.path("service").asText("");
            if ("SEND_DOCUMENT".equals(service)) {
                return error("UDP no soporta envio binario de documentos. Use TCP");
            }
            if ("RECEIVE_DOCUMENT".equals(service) && "FILE".equalsIgnoreCase(root.path("type").asText())) {
                return error("UDP no soporta recepcion de archivos binarios. Use TCP");
            }

            ProtocolResponse response = requestRouter.route(requestJson, null, clientIp);
            if (response.binaryPayload() != null) {
                response.binaryPayload().close();
                return error("UDP no soporta respuesta binaria");
            }
            return response.json();
        } catch (Exception e) {
            return error("Solicitud UDP invalida: " + e.getMessage());
        }
    }

    private String error(String message) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("service", "ERROR");
            payload.put("message", message);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"service\":\"ERROR\",\"message\":\"Error interno UDP\"}";
        }
    }
}

