package com.app.server.net;

import com.app.shared.protocol.Mensaje;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Adapter de {@link ClientChannel} para UDP.
 *
 * Representa una "conexión lógica" UDP asociada a una sesión (sessionId) y a
 * una dirección/puerto remotos. Todas las operaciones de envío se realizan a
 * través del mismo {@link DatagramSocket} compartido del servidor.
 *
 * Formato de datagrama:
 *   [1 byte tipo][4 bytes sessionId][4 bytes seqNum][payload]
 */
public class UdpClientChannel implements ClientChannel {

    public static final int TIPO_CONTROL = 0;
    public static final int TIPO_DATOS = 1;
    public static final int TIPO_ACK = 2;
    public static final int TIPO_FIN = 3;

    public static final int HEADER_SIZE = 9;

    private final DatagramSocket socket;
    private final InetAddress addr;
    private final int port;
    private final int sessionId;
    private final ClientContext context;
    private volatile boolean open = true;

    public UdpClientChannel(DatagramSocket socket, InetAddress addr, int port, int sessionId) {
        this.socket = socket;
        this.addr = addr;
        this.port = port;
        this.sessionId = sessionId;
        this.context = new ClientContext(addr.getHostAddress(), port, "UDP");
    }

    @Override
    public ClientContext getContext() {
        return context;
    }

    public int getSessionId() {
        return sessionId;
    }

    @Override
    public void sendMensaje(Mensaje mensaje) throws IOException {
        byte[] payload = mensaje.toJson().getBytes(StandardCharsets.UTF_8);
        sendPaquete(TIPO_CONTROL, 0, payload, 0, payload.length);
    }

    @Override
    public void sendBytes(byte[] data, int offset, int length) throws IOException {
        sendPaquete(TIPO_DATOS, 0, data, offset, length);
    }

    public void sendAck(int seqNum) throws IOException {
        sendPaquete(TIPO_ACK, seqNum, null, 0, 0);
    }

    public void sendFin(int seqNum) throws IOException {
        sendPaquete(TIPO_FIN, seqNum, null, 0, 0);
    }

    public void sendDataChunk(int seqNum, byte[] data, int offset, int length) throws IOException {
        sendPaquete(TIPO_DATOS, seqNum, data, offset, length);
    }

    private void sendPaquete(int tipo, int seqNum, byte[] payload, int off, int len)
            throws IOException {
        int total = HEADER_SIZE + len;
        byte[] packet = new byte[total];
        packet[0] = (byte) tipo;
        ByteBuffer.wrap(packet, 1, 4).putInt(sessionId);
        ByteBuffer.wrap(packet, 5, 4).putInt(seqNum);
        if (payload != null && len > 0) {
            System.arraycopy(payload, off, packet, HEADER_SIZE, len);
        }
        DatagramPacket dp = new DatagramPacket(packet, packet.length, addr, port);
        socket.send(dp);
    }

    @Override
    public boolean isOpen() {
        return open && !socket.isClosed();
    }

    @Override
    public void close() {
        open = false;
    }
}
