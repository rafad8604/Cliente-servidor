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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor UDP.
 *
 * <ul>
 *   <li>Usa {@link UdpClientChannel} como adapter de envio (Adapter).</li>
 *   <li>Adquiere/libera un slot del pool UDP por sesion.</li>
 *   <li>Publica eventos en el {@link ServerEventBus}.</li>
 *   <li>Delega en {@link CommandDispatcher} los comandos simples.</li>
 *   <li>Procesa el {@code FIN} en un executor para no bloquear el lector UDP
 *       con tareas largas (cifrado, insercion en BD).</li>
 * </ul>
 *
 * Formato de datagrama (ver {@link UdpClientChannel}):
 * {@code [1 byte tipo][4 bytes sessionId][4 bytes seqNum][payload]}.
 */
public class UdpHandler implements Runnable {

    /** Tamano maximo del datagrama UDP. Conservador para compatibilidad cross-OS. */
    public static final int MAX_DATAGRAM_SIZE = 8000;
    public static final int DATA_PAYLOAD_SIZE = MAX_DATAGRAM_SIZE - UdpClientChannel.HEADER_SIZE;

    private final DatagramSocket socket;
    private final DocumentoService documentoService;
    private final LogService logService;
    private final ClienteConectadoDAO clienteDAO;
    private final ClientPool udpPool;
    private final ServerEventBus eventBus;
    private final CommandDispatcher dispatcher;
    private final ExecutorService finExecutor;
    private volatile boolean running = true;

    private final Map<Integer, UdpSession> sessions = new ConcurrentHashMap<>();

    public UdpHandler(DatagramSocket socket, DocumentoService documentoService, LogService logService) {
        this(socket, documentoService, logService, null, null, null);
    }

    public UdpHandler(DatagramSocket socket, DocumentoService documentoService, LogService logService,
                      ClientPool udpPool, ServerEventBus eventBus) {
        this(socket, documentoService, logService, udpPool, eventBus, null);
    }

    public UdpHandler(DatagramSocket socket, DocumentoService documentoService, LogService logService,
                      ClientPool udpPool, ServerEventBus eventBus, CommandDispatcher dispatcher) {
        this.socket = socket;
        this.documentoService = documentoService;
        this.logService = logService;
        this.clienteDAO = new ClienteConectadoDAO();
        this.udpPool = udpPool;
        this.eventBus = eventBus;
        this.dispatcher = dispatcher != null
                ? dispatcher
                : new CommandDispatcher(documentoService, logService, clienteDAO, eventBus);
        this.finExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "udp-fin-worker");
            t.setDaemon(true);
            return t;
        });

        configurarBuffersSocket();
    }

    private void configurarBuffersSocket() {
        try {
            socket.setReceiveBufferSize(Math.max(socket.getReceiveBufferSize(), MAX_DATAGRAM_SIZE * 4));
            socket.setSendBufferSize(Math.max(socket.getSendBufferSize(), MAX_DATAGRAM_SIZE * 4));
        } catch (SocketException e) {
            System.err.println("[UDP] No se pudieron ajustar buffers: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        System.out.println("[UDP] Handler iniciado en puerto " + socket.getLocalPort());

        byte[] buffer = new byte[MAX_DATAGRAM_SIZE];

        while (running && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());

                procesarPaquete(data, packet.getAddress(), packet.getPort());
            } catch (SocketException e) {
                if (running) System.err.println("[UDP] Socket cerrado: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("[UDP] Error: " + e.getMessage());
                if (eventBus != null) eventBus.publishError("udp-handler", e, null);
            }
        }
    }

    private void procesarPaquete(byte[] data, InetAddress addr, int port) {
        if (data.length < UdpClientChannel.HEADER_SIZE) return;

        int tipo = data[0] & 0xFF;
        int sessionId = ByteBuffer.wrap(data, 1, 4).getInt();
        int seqNum = ByteBuffer.wrap(data, 5, 4).getInt();
        byte[] payload = new byte[data.length - UdpClientChannel.HEADER_SIZE];
        System.arraycopy(data, UdpClientChannel.HEADER_SIZE, payload, 0, payload.length);

        try {
            switch (tipo) {
                case UdpClientChannel.TIPO_CONTROL:
                    procesarControl(payload, addr, port, sessionId);
                    break;
                case UdpClientChannel.TIPO_DATOS:
                    procesarDatos(sessionId, seqNum, payload);
                    break;
                case UdpClientChannel.TIPO_FIN:
                    procesarFin(sessionId, addr, port);
                    break;
                default:
                    System.err.println("[UDP] Tipo de paquete desconocido: " + tipo);
            }
        } catch (Exception e) {
            System.err.println("[UDP] Error procesando paquete tipo=" + tipo + " sid=" + sessionId
                    + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (eventBus != null) eventBus.publishError("udp-handler", e, null);
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
        } catch (Exception e) {
            System.err.println("[UDP] Aviso registrando cliente " + clientIp + ":" + port
                    + " -> " + e.getMessage());
        }

        if (cmd == null) {
            channel.sendMensaje(Mensaje.error("Comando ausente"));
            return;
        }

        switch (cmd) {
            case ENVIAR_ARCHIVO:
                iniciarSesionEnvio(channel, msg, sessionId, clientIp);
                break;
            case DESCARGAR_ARCHIVO:
                enviarDescarga(channel, msg, clientIp, false);
                break;
            case DESCARGAR_ENCRIPTADO:
                enviarDescarga(channel, msg, clientIp, true);
                break;
            default:
                if (!dispatcher.dispatchSimple(channel, msg)) {
                    channel.sendMensaje(Mensaje.error("Comando no soportado por UDP: " + cmd));
                }
        }
    }

    private void iniciarSesionEnvio(UdpClientChannel channel, Mensaje msg, int sessionId, String clientIp)
            throws Exception {
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
        UdpSession nueva = new UdpSession(nombre, tamano, clientIp, channel);
        UdpSession previa = sessions.putIfAbsent(sessionId, nueva);
        if (previa != null) {
            // Sesion duplicada (mismo sessionId): liberamos el slot y rechazamos.
            if (udpPool != null) udpPool.release();
            channel.sendMensaje(Mensaje.error("Sesion UDP duplicada"));
            return;
        }

        if (eventBus != null) {
            eventBus.publish(ServerEventType.UDP_SESION_INICIADA, channel.getContext(),
                    "sessionId=" + sessionId + " archivo=" + nombre);
        }

        channel.sendMensaje(Mensaje.respuestaOk("mensaje", "Listo para recibir"));
        logService.registrar("UDP_INICIO_ARCHIVO", clientIp, "Archivo: " + nombre);
    }

    private void enviarDescarga(UdpClientChannel channel, Mensaje msg, String clientIp, boolean encriptado)
            throws Exception {
        if (!msg.getDatos().containsKey("documentoId")) {
            channel.sendMensaje(Mensaje.error("Parametro 'documentoId' requerido"));
            return;
        }
        long docId = msg.getLong("documentoId");
        Documento doc = documentoService.obtenerDocumento(docId);
        if (doc == null) {
            channel.sendMensaje(Mensaje.error("Documento no encontrado"));
            return;
        }

        logService.logDescarga(clientIp, doc.getNombre(),
                encriptado ? "ENCRIPTADO_UDP" : "ORIGINAL_UDP");

        Mensaje header = Mensaje.respuestaOk()
                .put("nombre", encriptado ? doc.getNombre() + ".enc" : doc.getNombre())
                .put("tamano", doc.getTamano())
                .put("hash", doc.getHashSha256())
                .put("tipoDescarga", encriptado ? "ENCRIPTADO" : "ORIGINAL");
        channel.sendMensaje(header);

        try (InputStream stream = encriptado
                ? documentoService.getArchivoEncriptadoStream(docId)
                : documentoService.getArchivoOriginalStream(docId)) {
            enviarStreamUdp(stream, channel);
        }
    }

    private void procesarDatos(int sessionId, int seqNum, byte[] data) throws IOException {
        UdpSession session = sessions.get(sessionId);
        if (session == null) {
            System.err.println("[UDP] Datos para sesion desconocida: " + sessionId);
            return;
        }
        session.addChunk(seqNum, data);
        session.channel.sendAck(seqNum);
    }

    private void procesarFin(int sessionId, InetAddress addr, int port) {
        UdpSession session = sessions.remove(sessionId);
        if (session == null) {
            try {
                new UdpClientChannel(socket, addr, port, sessionId)
                        .sendMensaje(Mensaje.error("Sesion no encontrada"));
            } catch (IOException ignored) {
                // ya no se puede responder
            }
            return;
        }
        // Procesamiento (cifrado/insertar BD) en background.
        finExecutor.submit(() -> completarSesion(sessionId, session));
    }

    private void completarSesion(int sessionId, UdpSession session) {
        try {
            try (InputStream stream = session.toInputStream()) {
                Documento doc = documentoService.procesarArchivo(
                        session.nombre, session.tamano, session.clientIp, stream);

                logService.logArchivoRecibido(session.clientIp, session.nombre, session.tamano);

                session.channel.sendMensaje(Mensaje.respuestaOk("hash", doc.getHashSha256())
                        .put("documentoId", doc.getId())
                        .put("mensaje", "Archivo recibido via UDP"));

                if (eventBus != null) {
                    eventBus.publish(ServerEventType.UDP_SESION_FINALIZADA, session.channel.getContext(),
                            "sessionId=" + sessionId + " docId=" + doc.getId());
                }
            }
        } catch (Exception e) {
            System.err.println("[UDP] Error completando sesion " + sessionId + ": "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (eventBus != null) eventBus.publishError("udp-fin", e, session.channel.getContext());
            try {
                session.channel.sendMensaje(Mensaje.error("Error procesando archivo: " + e.getMessage()));
            } catch (IOException ignored) {
                // best-effort
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
            // Pequena pausa para no saturar el buffer del receptor.
            if ((seqNum & 0x1F) == 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        channel.sendFin(seqNum);
    }

    public void stop() {
        running = false;
        finExecutor.shutdown();
    }

    /**
     * Sesion UDP de recepcion. Acumula chunks ordenados por seqNum y mantiene
     * el canal de respuesta.
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
            List<InputStream> streams = new ArrayList<>(keys.size());
            for (int key : keys) {
                streams.add(new ByteArrayInputStream(chunks.get(key)));
            }
            return new SequenceInputStream(Collections.enumeration(streams));
        }
    }
}
