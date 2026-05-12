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
 * Representa una "conexion logica" UDP asociada a una sesion (sessionId) y a
 * una direccion/puerto remotos. Todas las operaciones de envio se realizan a
 * traves del mismo {@link DatagramSocket} compartido del servidor.
 *
 * Formato de datagrama:
 * {@code [1 byte tipo][4 bytes sessionId][4 bytes seqNum][payload]}
 *
 * <h3>Fragmentacion de mensajes de control</h3>
 * Si un mensaje JSON excede {@link #MAX_CONTROL_PAYLOAD}, el envio se fragmenta:
 * <ul>
 *   <li>N-1 datagramas {@link #TIPO_CONTROL_FRAG} con seqNum 0..N-2</li>
 *   <li>1 datagrama final {@link #TIPO_CONTROL_END} con seqNum=N-1</li>
 * </ul>
 * El receptor (cliente) reensambla por seqNum.
 */
public class UdpClientChannel implements ClientChannel {

    public static final int TIPO_CONTROL = 0;
    public static final int TIPO_DATOS = 1;
    public static final int TIPO_ACK = 2;
    public static final int TIPO_FIN = 3;
    public static final int TIPO_CONTROL_FRAG = 4;
    public static final int TIPO_CONTROL_END = 5;

    public static final int HEADER_SIZE = 9;
    public static final int MAX_DATAGRAM = 8000;
    public static final int MAX_CONTROL_PAYLOAD = MAX_DATAGRAM - HEADER_SIZE;

    private final DatagramSocket socket;
    private final InetAddress addr;
    private final int port;
    private final int sessionId;
    private final ClientContext context;
    private final Object writeLock = new Object();
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
        synchronized (writeLock) {
            if (payload.length <= MAX_CONTROL_PAYLOAD) {
                sendPaquete(TIPO_CONTROL, 0, payload, 0, payload.length);
                return;
            }
            int total = (payload.length + MAX_CONTROL_PAYLOAD - 1) / MAX_CONTROL_PAYLOAD;
            for (int i = 0; i < total; i++) {
                int off = i * MAX_CONTROL_PAYLOAD;
                int len = Math.min(MAX_CONTROL_PAYLOAD, payload.length - off);
                int tipo = (i == total - 1) ? TIPO_CONTROL_END : TIPO_CONTROL_FRAG;
                sendPaquete(tipo, i, payload, off, len);
            }
        }
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
        // socket.isClosed() es seguro; ambas lecturas se reflejan al instante.
        return open && !socket.isClosed();
    }

    @Override
    public void close() {
        open = false;
    }
}
