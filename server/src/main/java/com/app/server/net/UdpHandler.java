package com.app.server.net;

import com.app.server.dao.ClienteConectadoDAO;
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
 * Servidor UDP que maneja la comunicación vía datagramas.
 * 
 * Protocolo UDP fiable para archivos grandes:
 * - Tipo 0: Control (JSON)
 * - Tipo 1: Datos (fragmento de archivo) con número de secuencia
 * - Tipo 2: ACK (confirmación de recepción)
 * - Tipo 3: FIN (señal de fin de transmisión)
 * 
 * Formato de datagrama:
 * [1 byte tipo][4 bytes sesionId][4 bytes seqNum][datos...]
 */
public class UdpHandler implements Runnable {

    private static final int MAX_DATAGRAM_SIZE = 60000; // Justo debajo del límite UDP
    private static final int HEADER_SIZE = 9; // 1 tipo + 4 sesionId + 4 seqNum
    private static final int DATA_PAYLOAD_SIZE = MAX_DATAGRAM_SIZE - HEADER_SIZE;
    private static final int TIPO_CONTROL = 0;
    private static final int TIPO_DATOS = 1;
    private static final int TIPO_ACK = 2;
    private static final int TIPO_FIN = 3;

    private final DatagramSocket socket;
    private final DocumentoService documentoService;
    private final LogService logService;
    private final ClienteConectadoDAO clienteDAO;
    private volatile boolean running = true;

    // Sesiones activas de transferencia: sessionId -> datos acumulados
    private final Map<Integer, UdpSession> sessions = new ConcurrentHashMap<>();

    public UdpHandler(DatagramSocket socket, DocumentoService documentoService, LogService logService) {
        this.socket = socket;
        this.documentoService = documentoService;
        this.logService = logService;
        this.clienteDAO = new ClienteConectadoDAO();
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
                InetAddress addr = packet.getAddress();
                int port = packet.getPort();

                procesarPaquete(data, addr, port);

            } catch (SocketException e) {
                if (running) {
                    System.err.println("[UDP] Socket cerrado: " + e.getMessage());
                }
            } catch (Exception e) {
                System.err.println("[UDP] Error: " + e.getMessage());
            }
        }
    }

    private void procesarPaquete(byte[] data, InetAddress addr, int port) throws Exception {
        if (data.length < HEADER_SIZE) return;

        int tipo = data[0] & 0xFF;
        int sessionId = ByteBuffer.wrap(data, 1, 4).getInt();
        int seqNum = ByteBuffer.wrap(data, 5, 4).getInt();
        byte[] payload = Arrays.copyOfRange(data, HEADER_SIZE, data.length);

        String clientIp = addr.getHostAddress();

        switch (tipo) {
            case TIPO_CONTROL -> procesarControl(payload, addr, port, sessionId, clientIp);
            case TIPO_DATOS -> procesarDatos(sessionId, seqNum, payload, addr, port);
            case TIPO_FIN -> procesarFin(sessionId, addr, port, clientIp);
            default -> System.err.println("[UDP] Tipo de paquete desconocido: " + tipo);
        }
    }

    private void procesarControl(byte[] payload, InetAddress addr, int port, int sessionId, String clientIp)
            throws Exception {

        String json = new String(payload, StandardCharsets.UTF_8);
        Mensaje msg = Mensaje.fromJson(json);
        Comando cmd = msg.getComando();

        System.out.println("[UDP] Comando de " + clientIp + ":" + port + " -> " + cmd);

        // Registrar cliente UDP
        try {
            clienteDAO.registrar(new ClienteConectado(clientIp, port, "UDP"));
        } catch (Exception e) {
            // ignorar si ya existe
        }

        switch (cmd) {
            case ENVIAR_ARCHIVO -> {
                // Iniciar sesión de transferencia
                String nombre = msg.getString("nombre");
                long tamano = msg.getLong("tamano");
                UdpSession session = new UdpSession(nombre, tamano, clientIp);
                sessions.put(sessionId, session);

                // Enviar ACK de inicio
                Mensaje ack = Mensaje.respuestaOk("mensaje", "Listo para recibir");
                enviarControl(ack, addr, port, sessionId);
                logService.registrar("UDP_INICIO_ARCHIVO", clientIp, "Archivo: " + nombre);
            }
            case ENVIAR_MENSAJE -> {
                String texto = msg.getString("texto");
                Documento doc = documentoService.procesarMensaje(texto, clientIp);
                logService.logMensajeRecibido(clientIp);

                Mensaje resp = Mensaje.respuestaOk("hash", doc.getHashSha256())
                        .put("documentoId", doc.getId());
                enviarControl(resp, addr, port, sessionId);
            }
            case LISTAR_DOCUMENTOS -> {
                List<Documento> docs = documentoService.listarDocumentos();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < docs.size(); i++) {
                    Documento d = docs.get(i);
                    if (i > 0) sb.append(",");
                    sb.append(String.format(
                            "{\"id\":%d,\"nombre\":\"%s\",\"extension\":\"%s\",\"tamano\":%d,\"tipo\":\"%s\",\"hash\":\"%s\",\"ip\":\"%s\",\"fecha\":\"%s\"}",
                            d.getId(), d.getNombre(), d.getExtension() != null ? d.getExtension() : "",
                            d.getTamano(), d.getTipo().name(), d.getHashSha256(), d.getIpPropietario(),
                            d.getFechaCreacion().toString()));
                }
                sb.append("]");
                Mensaje resp = Mensaje.respuestaOk("documentos", sb.toString()).put("total", docs.size());
                enviarControl(resp, addr, port, sessionId);
            }
            case LISTAR_CLIENTES -> {
                List<ClienteConectado> clientes = clienteDAO.listarTodos();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < clientes.size(); i++) {
                    ClienteConectado c = clientes.get(i);
                    if (i > 0) sb.append(",");
                    sb.append(String.format(
                            "{\"ip\":\"%s\",\"puerto\":%d,\"protocolo\":\"%s\",\"fechaInicio\":\"%s\"}",
                            c.getIp(), c.getPuerto(), c.getProtocolo(), c.getFechaInicio().toString()));
                }
                sb.append("]");
                Mensaje resp = Mensaje.respuestaOk("clientes", sb.toString()).put("total", clientes.size());
                enviarControl(resp, addr, port, sessionId);
            }
            case DESCARGAR_ARCHIVO -> {
                long docId = msg.getLong("documentoId");
                Documento doc = documentoService.obtenerDocumento(docId);
                if (doc == null) {
                    enviarControl(Mensaje.error("Documento no encontrado"), addr, port, sessionId);
                    return;
                }
                logService.logDescarga(clientIp, doc.getNombre(), "ORIGINAL_UDP");

                InputStream stream = documentoService.getArchivoOriginalStream(docId);
                // Enviar metadatos
                Mensaje header = Mensaje.respuestaOk()
                        .put("nombre", doc.getNombre())
                        .put("tamano", doc.getTamano())
                        .put("hash", doc.getHashSha256())
                        .put("tipoDescarga", "ORIGINAL");
                enviarControl(header, addr, port, sessionId);

                // Enviar datos en fragmentos con ACK
                enviarStreamUdp(stream, addr, port, sessionId);
            }
            case DESCARGAR_HASH -> {
                long docId = msg.getLong("documentoId");
                String hash = documentoService.getHash(docId);
                if (hash == null) {
                    enviarControl(Mensaje.error("Documento no encontrado"), addr, port, sessionId);
                    return;
                }
                Mensaje resp = Mensaje.respuestaOk("hash", hash).put("tipoDescarga", "HASH");
                enviarControl(resp, addr, port, sessionId);
            }
            case DESCARGAR_ENCRIPTADO -> {
                long docId = msg.getLong("documentoId");
                Documento doc = documentoService.obtenerDocumento(docId);
                if (doc == null) {
                    enviarControl(Mensaje.error("Documento no encontrado"), addr, port, sessionId);
                    return;
                }
                logService.logDescarga(clientIp, doc.getNombre(), "ENCRIPTADO_UDP");

                InputStream stream = documentoService.getArchivoEncriptadoStream(docId);
                Mensaje header = Mensaje.respuestaOk()
                        .put("nombre", doc.getNombre() + ".enc")
                        .put("hash", doc.getHashSha256())
                        .put("tipoDescarga", "ENCRIPTADO");
                enviarControl(header, addr, port, sessionId);

                enviarStreamUdp(stream, addr, port, sessionId);
            }
            default -> enviarControl(Mensaje.error("Comando no soportado por UDP"), addr, port, sessionId);
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
        enviarAck(seqNum, addr, port, sessionId);
    }

    private void procesarFin(int sessionId, InetAddress addr, int port, String clientIp) throws Exception {
        UdpSession session = sessions.remove(sessionId);
        if (session == null) {
            enviarControl(Mensaje.error("Sesión no encontrada"), addr, port, sessionId);
            return;
        }

        // Reconstruir archivo desde chunks ordenados
        InputStream stream = session.toInputStream();

        Documento doc = documentoService.procesarArchivo(
                session.nombre, session.tamano, clientIp, stream);

        logService.logArchivoRecibido(clientIp, session.nombre, session.tamano);

        Mensaje resp = Mensaje.respuestaOk("hash", doc.getHashSha256())
                .put("documentoId", doc.getId())
                .put("mensaje", "Archivo recibido via UDP");
        enviarControl(resp, addr, port, sessionId);
    }

    // --- Envío UDP ---

    private void enviarControl(Mensaje msg, InetAddress addr, int port, int sessionId) throws IOException {
        byte[] payload = msg.toJson().getBytes(StandardCharsets.UTF_8);
        enviarPaquete(TIPO_CONTROL, sessionId, 0, payload, addr, port);
    }

    private void enviarAck(int seqNum, InetAddress addr, int port, int sessionId) throws IOException {
        enviarPaquete(TIPO_ACK, sessionId, seqNum, new byte[0], addr, port);
    }

    private void enviarPaquete(int tipo, int sessionId, int seqNum, byte[] payload,
                               InetAddress addr, int port) throws IOException {
        byte[] packet = new byte[HEADER_SIZE + payload.length];
        packet[0] = (byte) tipo;
        ByteBuffer.wrap(packet, 1, 4).putInt(sessionId);
        ByteBuffer.wrap(packet, 5, 4).putInt(seqNum);
        System.arraycopy(payload, 0, packet, HEADER_SIZE, payload.length);

        DatagramPacket dp = new DatagramPacket(packet, packet.length, addr, port);
        socket.send(dp);
    }

    private void enviarStreamUdp(InputStream stream, InetAddress addr, int port, int sessionId)
            throws IOException {
        byte[] buffer = new byte[DATA_PAYLOAD_SIZE];
        int seqNum = 0;
        int bytesRead;

        while ((bytesRead = stream.read(buffer)) != -1) {
            byte[] chunk = bytesRead < buffer.length ?
                    Arrays.copyOf(buffer, bytesRead) : buffer.clone();

            enviarPaquete(TIPO_DATOS, sessionId, seqNum, chunk, addr, port);
            seqNum++;

            // Pequeña pausa para no saturar la red
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Enviar FIN
        enviarPaquete(TIPO_FIN, sessionId, seqNum, new byte[0], addr, port);
        stream.close();
    }

    public void stop() {
        running = false;
    }

    /**
     * Sesión UDP para acumular chunks de un archivo recibido.
     */
    private static class UdpSession {
        final String nombre;
        final long tamano;
        final String clientIp;
        final ConcurrentHashMap<Integer, byte[]> chunks = new ConcurrentHashMap<>();

        UdpSession(String nombre, long tamano, String clientIp) {
            this.nombre = nombre;
            this.tamano = tamano;
            this.clientIp = clientIp;
        }

        void addChunk(int seqNum, byte[] data) {
            chunks.put(seqNum, data);
        }

        InputStream toInputStream() {
            // Ordenar chunks por número de secuencia
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
