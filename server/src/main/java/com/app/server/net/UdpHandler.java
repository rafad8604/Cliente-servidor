package com.app.server.net;

import com.app.server.dao.ClienteConectadoDAO;
import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.server.models.ClienteConectado;
import com.app.server.models.Documento;
import com.app.server.service.DocumentoService;
import com.app.server.service.LogService;
import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servidor UDP.
 *
 * Tras el refactor:
 *   - Usa {@link UdpClientChannel} como adapter de envío (Adapter).
 *   - Adquiere/libera un slot del pool UDP por sesión (limita concurrencia
 *     sin exponer Semaphore).
 *   - Publica eventos en el {@link ServerEventBus}.
 *   - Delega en {@link CommandDispatcher} los comandos simples.
 *
 * Formato de datagrama (sin cambios):
 *   [1 byte tipo][4 bytes sessionId][4 bytes seqNum][payload]
 */
public class UdpHandler implements Runnable {

    private static final int MAX_DATAGRAM_SIZE = 60000;
    private static final int DATA_PAYLOAD_SIZE = MAX_DATAGRAM_SIZE - UdpClientChannel.HEADER_SIZE;

    private final DatagramSocket socket;
    private final DocumentoService documentoService;
    private final LogService logService;
    private final ClienteConectadoDAO clienteDAO;
    private final ClientPool udpPool;
    private final ServerEventBus eventBus;
    private final CommandDispatcher dispatcher;
    private volatile boolean running = true;

    private final Map<Integer, UdpSession> sessions = new ConcurrentHashMap<>();

    /**
     * Constructor de compatibilidad (sin pool UDP ni eventBus).
     */
    public UdpHandler(DatagramSocket socket, DocumentoService documentoService, LogService logService) {
        this(socket, documentoService, logService, null, null);
    }

    public UdpHandler(DatagramSocket socket, DocumentoService documentoService, LogService logService,
                      ClientPool udpPool, ServerEventBus eventBus) {
        this.socket = socket;
        this.documentoService = documentoService;
        this.logService = logService;
        this.clienteDAO = new ClienteConectadoDAO();
        this.udpPool = udpPool;
        this.eventBus = eventBus;
        this.dispatcher = new CommandDispatcher(documentoService, logService, clienteDAO, eventBus);
    }

    @Override
    public void run() {
        System.out.println("[UDP] Handler iniciado en puerto " + socket.getLocalPort());

        byte[] buffer = new byte[MAX_DATAGRAM_SIZE];

        while (running && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                procesarPaquete(data, packet.getAddress(), packet.getPort());

            } catch (SocketException e) {
                if (running) System.err.println("[UDP] Socket cerrado: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("[UDP] Error: " + e.getMessage());
                if (eventBus != null) eventBus.publishError("udp-handler", e, null);
            }
        }
    }

    private void procesarPaquete(byte[] data, InetAddress addr, int port) throws Exception {
        if (data.length < UdpClientChannel.HEADER_SIZE) return;

        int tipo = data[0] & 0xFF;
        int sessionId = ByteBuffer.wrap(data, 1, 4).getInt();
        int seqNum = ByteBuffer.wrap(data, 5, 4).getInt();
        byte[] payload = Arrays.copyOfRange(data, UdpClientChannel.HEADER_SIZE, data.length);

        switch (tipo) {
            case UdpClientChannel.TIPO_CONTROL:
                procesarControl(payload, addr, port, sessionId);
                break;
            case UdpClientChannel.TIPO_DATOS:
                procesarDatos(sessionId, seqNum, payload, addr, port);
                break;
            case UdpClientChannel.TIPO_FIN:
                procesarFin(sessionId, addr, port);
                break;
            default:
                System.err.println("[UDP] Tipo de paquete desconocido: " + tipo);
        }
    }

    private void procesarControl(byte[] payload, InetAddress addr, int port, int sessionId)
            throws Exception {
        String json = new String(payload, StandardCharsets.UTF_8);
        Mensaje msg = Mensaje.fromJson(json);
        Comando cmd = msg.getComando();

        UdpClientChannel channel = new UdpClientChannel(socket, addr, port, sessionId);
        String clientIp = channel.getContext().getIp();

        System.out.println("[UDP] Comando de " + clientIp + ":" + port + " -> " + cmd);

        try {
            clienteDAO.registrar(new ClienteConectado(clientIp, port, "UDP"));
        } catch (Exception ignored) {
            // cliente UDP "ya existe"
        }

        switch (cmd) {
            case ENVIAR_ARCHIVO: {
                // Adquirir slot UDP (si hay pool); si está lleno, rechazar
                if (udpPool != null && !udpPool.tryAcquire()) {
                    if (eventBus != null) {
                        eventBus.publish(ServerEventType.UDP_CONEXION_RECHAZADA,
                                channel.getContext(), "pool lleno");
                    }
                    channel.sendMensaje(Mensaje.error("Servidor UDP lleno"));
                    return;
                }
                String nombre = msg.getString("nombre");
                long tamano = msg.getLong("tamano");
                UdpSession session = new UdpSession(nombre, tamano, clientIp, channel);
                sessions.put(sessionId, session);

                if (eventBus != null) {
                    eventBus.publish(ServerEventType.UDP_SESION_INICIADA, channel.getContext(),
                            "sessionId=" + sessionId + " archivo=" + nombre);
                }

                channel.sendMensaje(Mensaje.respuestaOk("mensaje", "Listo para recibir"));
                logService.registrar("UDP_INICIO_ARCHIVO", clientIp, "Archivo: " + nombre);
                break;
            }
            case DESCARGAR_ARCHIVO: {
                long docId = msg.getLong("documentoId");
                Documento doc = documentoService.obtenerDocumento(docId);
                if (doc == null) {
                    channel.sendMensaje(Mensaje.error("Documento no encontrado"));
                    return;
                }
                logService.logDescarga(clientIp, doc.getNombre(), "ORIGINAL_UDP");
                InputStream stream = documentoService.getArchivoOriginalStream(docId);

                channel.sendMensaje(Mensaje.respuestaOk()
                        .put("nombre", doc.getNombre())
                        .put("tamano", doc.getTamano())
                        .put("hash", doc.getHashSha256())
                        .put("tipoDescarga", "ORIGINAL"));

                enviarStreamUdp(stream, channel);
                break;
            }
            case DESCARGAR_ENCRIPTADO: {
                long docId = msg.getLong("documentoId");
                Documento doc = documentoService.obtenerDocumento(docId);
                if (doc == null) {
                    channel.sendMensaje(Mensaje.error("Documento no encontrado"));
                    return;
                }
                logService.logDescarga(clientIp, doc.getNombre(), "ENCRIPTADO_UDP");
                InputStream stream = documentoService.getArchivoEncriptadoStream(docId);
                channel.sendMensaje(Mensaje.respuestaOk()
                        .put("nombre", doc.getNombre() + ".enc")
                        .put("hash", doc.getHashSha256())
                        .put("tipoDescarga", "ENCRIPTADO"));
                enviarStreamUdp(stream, channel);
                break;
            }
            default: {
                // Delegar todo lo "simple" al dispatcher compartido
                if (!dispatcher.dispatchSimple(channel, msg)) {
                    channel.sendMensaje(Mensaje.error("Comando no soportado por UDP"));
                }
            }
        }
    }

    private void procesarDatos(int sessionId, int seqNum, byte[] data, InetAddress addr, int port)
            throws IOException {
        UdpSession session = sessions.get(sessionId);
        if (session == null) {
            System.err.println("[UDP] Sesión no encontrada: " + sessionId);
            return;
        }
        session.addChunk(seqNum, data);
        session.channel.sendAck(seqNum);
    }

    private void procesarFin(int sessionId, InetAddress addr, int port) throws Exception {
        UdpSession session = sessions.remove(sessionId);
        if (session == null) {
            new UdpClientChannel(socket, addr, port, sessionId)
                    .sendMensaje(Mensaje.error("Sesión no encontrada"));
            return;
        }

        try {
            InputStream stream = session.toInputStream();
            Documento doc = documentoService.procesarArchivo(
                    session.nombre, session.tamano, session.clientIp, stream);

            if (logService != null) {
                logService.logArchivoRecibido(session.clientIp, session.nombre, session.tamano);
            }

            session.channel.sendMensaje(Mensaje.respuestaOk("hash", doc.getHashSha256())
                    .put("documentoId", doc.getId())
                    .put("mensaje", "Archivo recibido via UDP"));

            if (eventBus != null) {
                eventBus.publish(ServerEventType.UDP_SESION_FINALIZADA, session.channel.getContext(),
                        "sessionId=" + sessionId + " docId=" + doc.getId());
            }
        } finally {
            if (udpPool != null) udpPool.release();
        }
    }

    private void enviarStreamUdp(InputStream stream, UdpClientChannel channel) throws IOException {
        byte[] buffer = new byte[DATA_PAYLOAD_SIZE];
        int seqNum = 0;
        int bytesRead;

        while ((bytesRead = stream.read(buffer)) != -1) {
            channel.sendDataChunk(seqNum, buffer, 0, bytesRead);
            seqNum++;
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        channel.sendFin(seqNum);
        stream.close();
    }

    public void stop() {
        running = false;
    }

    /**
     * Sesión UDP de recepción. Acumula chunks y mantiene el canal de respuesta.
     */
    private static class UdpSession {
        final String nombre;
        final long tamano;
        final String clientIp;
        final UdpClientChannel channel;
        final ConcurrentHashMap<Integer, byte[]> chunks = new ConcurrentHashMap<>();

        UdpSession(String nombre, long tamano, String clientIp, UdpClientChannel channel) {
            this.nombre = nombre;
            this.tamano = tamano;
            this.clientIp = clientIp;
            this.channel = channel;
        }

        void addChunk(int seqNum, byte[] data) {
            chunks.put(seqNum, data);
        }

        InputStream toInputStream() {
            List<Integer> keys = new ArrayList<>(chunks.keySet());
            Collections.sort(keys);
            List<InputStream> streams = new ArrayList<>();
            for (int key : keys) {
                streams.add(new ByteArrayInputStream(chunks.get(key)));
            }
            return new SequenceInputStream(Collections.enumeration(streams));
        }
    }
}
