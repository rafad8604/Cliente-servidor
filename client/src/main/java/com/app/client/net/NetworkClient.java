package com.app.client.net;

import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Cliente de red TCP/UDP. El protocolo se selecciona al conectar.
 *
 * <h3>Soporte UDP</h3>
 * Maneja fragmentacion automatica de mensajes de control:
 * <ul>
 *   <li>{@code TIPO_CONTROL (0)}: paquete unico (mensaje cabe en un datagrama).</li>
 *   <li>{@code TIPO_CONTROL_FRAG (4)} + {@code TIPO_CONTROL_END (5)}: fragmentado;
 *       se reensambla por {@code seqNum} antes de parsear el JSON.</li>
 * </ul>
 */
public class NetworkClient implements Closeable {

    public enum Protocolo { TCP, UDP }

    private static final int MAX_DATAGRAM_SIZE = 8000;
    private static final int HEADER_SIZE = 9;
    private static final int DATA_PAYLOAD_SIZE = MAX_DATAGRAM_SIZE - HEADER_SIZE;

    private static final int TIPO_CONTROL = 0;
    private static final int TIPO_DATOS = 1;
    private static final int TIPO_FIN = 3;
    private static final int TIPO_CONTROL_FRAG = 4;
    private static final int TIPO_CONTROL_END = 5;

    private final String host;
    private final int port;
    private final Protocolo protocolo;

    // TCP
    private Socket tcpSocket;
    private InputStream tcpIn;
    private OutputStream tcpOut;

    // UDP
    private DatagramSocket udpSocket;
    private InetAddress udpAddress;
    private int udpPort;

    private Consumer<Mensaje> onMessageReceived;
    private volatile boolean connected = false;

    private final ExecutorService fileUploadPool = Executors.newFixedThreadPool(3);
    private final Random random = new Random();
    private final Object tcpRequestLock = new Object();

    public NetworkClient(String host, int port, Protocolo protocolo) {
        this.host = host;
        this.port = port;
        this.protocolo = protocolo;
    }

    public Mensaje conectar() throws IOException {
        if (protocolo == Protocolo.TCP) {
            return conectarTcp();
        }
        return conectarUdp();
    }

    private Mensaje conectarTcp() throws IOException {
        tcpSocket = new Socket();
        tcpSocket.connect(new InetSocketAddress(host, port), 10000);
        tcpSocket.setSoTimeout(0);
        tcpIn = tcpSocket.getInputStream();
        tcpOut = tcpSocket.getOutputStream();
        connected = true;

        String firstLine = leerLineaTcp();
        return (firstLine != null) ? Mensaje.fromJson(firstLine) : new Mensaje(Comando.SESION_INFO);
    }

    private Mensaje conectarUdp() throws IOException {
        udpSocket = new DatagramSocket();
        udpSocket.setSoTimeout(10000);
        udpSocket.setReceiveBufferSize(Math.max(udpSocket.getReceiveBufferSize(), MAX_DATAGRAM_SIZE * 4));
        udpSocket.setSendBufferSize(Math.max(udpSocket.getSendBufferSize(), MAX_DATAGRAM_SIZE * 4));
        udpAddress = InetAddress.getByName(host);
        this.udpPort = port;
        connected = true;
        System.out.println("[UDP] Conectando a " + host + ":" + udpPort);

        // Handshake: enviar LISTAR_CLIENTES para registrar la sesion UDP.
        int sessionId = random.nextInt(Integer.MAX_VALUE);
        try {
            enviarControlUdp(new Mensaje(Comando.LISTAR_CLIENTES), sessionId);
            recibirControlUdp(sessionId);
        } catch (Exception e) {
            System.err.println("[UDP] Advertencia en handshake UDP: " + e.getMessage());
        }

        return new Mensaje(Comando.SESION_INFO).put("status", "CONECTADO").put("protocolo", "UDP");
    }

    public CompletableFuture<Mensaje> enviarArchivo(File file, Consumer<Long> onProgress) {
        return enviarArchivo(file, onProgress, null);
    }

    public CompletableFuture<Mensaje> enviarArchivo(File file, Consumer<Long> onProgress, DestinoEnvio destino) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return (protocolo == Protocolo.TCP)
                        ? enviarArchivoTcp(file, onProgress, destino)
                        : enviarArchivoUdp(file, onProgress, destino);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, fileUploadPool);
    }

    private Mensaje enviarArchivoTcp(File file, Consumer<Long> onProgress, DestinoEnvio destino) throws Exception {
        synchronized (tcpRequestLock) {
            Mensaje header = new Mensaje(Comando.ENVIAR_ARCHIVO)
                    .put("nombre", file.getName())
                    .put("tamano", file.length());
            aplicarDestinoAlMensaje(header, destino);
            enviarLineaTcp(header.toJson());

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalSent = 0;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    synchronized (tcpOut) {
                        tcpOut.write(buffer, 0, bytesRead);
                    }
                    totalSent += bytesRead;
                    if (onProgress != null) onProgress.accept(totalSent);
                }
                synchronized (tcpOut) {
                    tcpOut.flush();
                }
            }
            return esperarRespuestaTcp();
        }
    }

    private Mensaje enviarArchivoUdp(File file, Consumer<Long> onProgress, DestinoEnvio destino) throws Exception {
        int sessionId = random.nextInt(Integer.MAX_VALUE);

        Mensaje header = new Mensaje(Comando.ENVIAR_ARCHIVO)
                .put("nombre", file.getName())
                .put("tamano", file.length());
        aplicarDestinoAlMensaje(header, destino);
        enviarControlUdp(header, sessionId);
        recibirControlUdp(sessionId);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[DATA_PAYLOAD_SIZE];
            int seqNum = 0;
            int bytesRead;
            long totalSent = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunk = (bytesRead < buffer.length)
                        ? Arrays.copyOf(buffer, bytesRead)
                        : buffer.clone();
                enviarDatosUdp(sessionId, seqNum, chunk);
                seqNum++;
                totalSent += bytesRead;
                if (onProgress != null) onProgress.accept(totalSent);

                if ((seqNum & 0x1F) == 0) {
                    Thread.sleep(1);
                }
            }
        }

        enviarFinUdp(sessionId);
        // El servidor responde DESPUES de procesar el archivo (puede tardar).
        udpSocket.setSoTimeout(60000);
        try {
            return recibirControlUdp(sessionId);
        } finally {
            udpSocket.setSoTimeout(10000);
        }
    }

    public Mensaje enviarMensaje(String texto) throws IOException {
        return enviarMensaje(texto, null);
    }

    public Mensaje enviarMensaje(String texto, DestinoEnvio destino) throws IOException {
        Mensaje msg = new Mensaje(Comando.ENVIAR_MENSAJE).put("texto", texto);
        aplicarDestinoAlMensaje(msg, destino);
        return enviarComandoSimple(msg);
    }

    public Mensaje listarDocumentosPrivados() throws IOException {
        return enviarComandoSimple(new Mensaje(Comando.LISTAR_DOCUMENTOS_PRIVADOS));
    }

    public Mensaje listarDocumentos() throws IOException {
        return enviarComandoSimple(new Mensaje(Comando.LISTAR_DOCUMENTOS));
    }

    public Mensaje listarClientes() throws IOException {
        return enviarComandoSimple(new Mensaje(Comando.LISTAR_CLIENTES));
    }

    public Mensaje setNombre(String nombre) throws IOException {
        return enviarComandoSimple(new Mensaje(Comando.SET_NOMBRE).put("nombre", nombre));
    }

    public Mensaje listarServidores() throws IOException {
        return enviarComandoSimple(new Mensaje(Comando.LISTAR_SERVIDORES));
    }

    public Mensaje descargarHash(long documentoId) throws IOException {
        return enviarComandoSimple(new Mensaje(Comando.DESCARGAR_HASH).put("documentoId", documentoId));
    }

    public Mensaje obtenerEventos(int limit) throws IOException {
        return enviarComandoSimple(new Mensaje(Comando.OBTENER_EVENTOS).put("limit", limit));
    }

    public Mensaje obtenerLogs(int limit) throws IOException {
        return enviarComandoSimple(new Mensaje(Comando.OBTENER_LOGS).put("limit", limit));
    }

    private Mensaje enviarComandoSimple(Mensaje msg) throws IOException {
        if (protocolo == Protocolo.TCP) {
            synchronized (tcpRequestLock) {
                enviarLineaTcp(msg.toJson());
                return esperarRespuestaTcp();
            }
        }
        int sessionId = random.nextInt(Integer.MAX_VALUE);
        enviarControlUdp(msg, sessionId);
        return recibirControlUdp(sessionId);
    }

    public void descargarArchivo(long documentoId, File destino, Consumer<Long> onProgress) throws Exception {
        descargarArchivo(documentoId, null, destino, onProgress);
    }

    /**
     * Descarga indicando opcionalmente el {@code servidor} (peerId) origen.
     * Si no es {@code null} ni "local", el servidor al que estoy conectado
     * actuara como proxy hacia ese peer.
     */
    public void descargarArchivo(long documentoId, String servidor, File destino, Consumer<Long> onProgress)
            throws Exception {
        if (protocolo == Protocolo.TCP) {
            descargarArchivoTcp(documentoId, servidor, destino, onProgress, Comando.DESCARGAR_ARCHIVO);
        } else {
            descargarArchivoUdp(documentoId, destino, onProgress, Comando.DESCARGAR_ARCHIVO);
        }
    }

    public void descargarEncriptado(long documentoId, File destino, Consumer<Long> onProgress) throws Exception {
        if (protocolo == Protocolo.TCP) {
            descargarEncriptadoTcp(documentoId, destino, onProgress);
        } else {
            descargarArchivoUdp(documentoId, destino, onProgress, Comando.DESCARGAR_ENCRIPTADO);
        }
    }

    public List<CompletableFuture<Mensaje>> enviarArchivosParalelo(List<File> archivos, Consumer<Long> onProgress) {
        return archivos.stream().map(f -> enviarArchivo(f, onProgress)).toList();
    }

    // ==================== TCP internals ====================

    private void enviarLineaTcp(String json) throws IOException {
        byte[] data = (json + "\n").getBytes(StandardCharsets.UTF_8);
        synchronized (tcpOut) {
            tcpOut.write(data);
            tcpOut.flush();
        }
    }

    private Mensaje esperarRespuestaTcp() throws IOException {
        String line = leerLineaTcp();
        if (line == null) throw new IOException("Conexion TCP cerrada por el servidor");
        line = line.trim();
        if (line.isEmpty()) throw new IOException("Respuesta TCP vacia");
        return Mensaje.fromJson(line);
    }

    private String leerLineaTcp() throws IOException {
        if (tcpIn == null) throw new IOException("Socket TCP no inicializado");
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(256);
        while (true) {
            int b = tcpIn.read();
            if (b == -1) {
                if (lineBuffer.size() == 0) return null;
                break;
            }
            if (b == '\n') break;
            if (b != '\r') lineBuffer.write(b);
        }
        return lineBuffer.toString(StandardCharsets.UTF_8);
    }

    private void descargarArchivoTcp(long documentoId, String servidor,
                                     File destino, Consumer<Long> onProgress, Comando comando)
            throws Exception {
        synchronized (tcpRequestLock) {
            Mensaje msg = new Mensaje(comando).put("documentoId", documentoId);
            if (servidor != null && !servidor.isBlank()) msg.put("servidor", servidor);
            enviarLineaTcp(msg.toJson());

            Mensaje header = esperarRespuestaTcp();
            if (header.getComando() == Comando.ERROR) {
                throw new IOException("Error: " + header.getString("detalle"));
            }
            long tamano = header.getLong("tamano");

            try (FileOutputStream fos = new FileOutputStream(destino)) {
                DataInputStream dis = new DataInputStream(tcpIn);
                byte[] buffer = new byte[8192];
                long remaining = tamano;
                long totalRead = 0;

                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int bytesRead = dis.read(buffer, 0, toRead);
                    if (bytesRead == -1) break;
                    fos.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                    totalRead += bytesRead;
                    if (onProgress != null) onProgress.accept(totalRead);
                }
                if (remaining != 0) {
                    throw new IOException("Descarga incompleta: faltan " + remaining
                            + " bytes (recibidos " + totalRead + ")");
                }
            }
        }
    }

    private void descargarEncriptadoTcp(long documentoId, File destino, Consumer<Long> onProgress)
            throws Exception {
        synchronized (tcpRequestLock) {
            Mensaje msg = new Mensaje(Comando.DESCARGAR_ENCRIPTADO).put("documentoId", documentoId);
            enviarLineaTcp(msg.toJson());

            Mensaje header = esperarRespuestaTcp();
            if (header.getComando() == Comando.ERROR) {
                throw new IOException("Error: " + header.getString("detalle"));
            }

            DataInputStream dis = new DataInputStream(tcpIn);
            try (FileOutputStream fos = new FileOutputStream(destino)) {
                long totalRead = 0;
                while (true) {
                    int blockSize = dis.readInt();
                    if (blockSize == 0) break;
                    byte[] block = new byte[blockSize];
                    dis.readFully(block);
                    fos.write(block);
                    totalRead += blockSize;
                    if (onProgress != null) onProgress.accept(totalRead);
                }
            }
        }
    }

    // ==================== UDP internals ====================

    private void enviarControlUdp(Mensaje msg, int sessionId) throws IOException {
        byte[] payload = msg.toJson().getBytes(StandardCharsets.UTF_8);
        if (payload.length <= DATA_PAYLOAD_SIZE) {
            enviarPaqueteUdp(TIPO_CONTROL, sessionId, 0, payload);
            return;
        }
        // Fragmentar el comando si excede el payload de un datagrama.
        int total = (payload.length + DATA_PAYLOAD_SIZE - 1) / DATA_PAYLOAD_SIZE;
        for (int i = 0; i < total; i++) {
            int off = i * DATA_PAYLOAD_SIZE;
            int len = Math.min(DATA_PAYLOAD_SIZE, payload.length - off);
            byte[] chunk = new byte[len];
            System.arraycopy(payload, off, chunk, 0, len);
            int tipo = (i == total - 1) ? TIPO_CONTROL_END : TIPO_CONTROL_FRAG;
            enviarPaqueteUdp(tipo, sessionId, i, chunk);
        }
    }

    private void enviarDatosUdp(int sessionId, int seqNum, byte[] data) throws IOException {
        enviarPaqueteUdp(TIPO_DATOS, sessionId, seqNum, data);
    }

    private void enviarFinUdp(int sessionId) throws IOException {
        enviarPaqueteUdp(TIPO_FIN, sessionId, 0, new byte[0]);
    }

    private void enviarPaqueteUdp(int tipo, int sessionId, int seqNum, byte[] payload) throws IOException {
        byte[] packet = new byte[HEADER_SIZE + payload.length];
        packet[0] = (byte) tipo;
        ByteBuffer.wrap(packet, 1, 4).putInt(sessionId);
        ByteBuffer.wrap(packet, 5, 4).putInt(seqNum);
        System.arraycopy(payload, 0, packet, HEADER_SIZE, payload.length);
        DatagramPacket dp = new DatagramPacket(packet, packet.length, udpAddress, udpPort);
        udpSocket.send(dp);
    }

    /**
     * Recibe una respuesta de control para {@code expectedSessionId}, reensamblando
     * fragmentos si llegan en multiples paquetes. Ignora ACK, FIN y paquetes de
     * otras sesiones.
     */
    private Mensaje recibirControlUdp(int expectedSessionId) throws IOException {
        byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
        DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
        TreeMap<Integer, byte[]> fragmentos = new TreeMap<>();
        int intentos = 0;
        final int MAX_INTENTOS = 5;

        while (intentos < MAX_INTENTOS) {
            try {
                udpSocket.receive(dp);
                int len = dp.getLength();
                if (len < HEADER_SIZE) continue;

                int tipo = buffer[0] & 0xFF;
                int sessionId = ByteBuffer.wrap(buffer, 1, 4).getInt();
                int seqNum = ByteBuffer.wrap(buffer, 5, 4).getInt();
                if (sessionId != expectedSessionId) continue;

                if (tipo == TIPO_CONTROL) {
                    byte[] payload = new byte[len - HEADER_SIZE];
                    System.arraycopy(buffer, HEADER_SIZE, payload, 0, payload.length);
                    return Mensaje.fromJson(new String(payload, StandardCharsets.UTF_8));
                }

                if (tipo == TIPO_CONTROL_FRAG || tipo == TIPO_CONTROL_END) {
                    byte[] frag = new byte[len - HEADER_SIZE];
                    System.arraycopy(buffer, HEADER_SIZE, frag, 0, frag.length);
                    fragmentos.put(seqNum, frag);
                    if (tipo == TIPO_CONTROL_END) {
                        return reensamblarFragmentos(fragmentos);
                    }
                }
                // Otros tipos (ACK, DATOS, FIN) se ignoran aqui.
            } catch (SocketTimeoutException e) {
                if (!fragmentos.isEmpty()) {
                    // Si ya tenemos fragmentos pero falta el END, no insistir mas.
                    throw new IOException("Respuesta UDP fragmentada incompleta (" + fragmentos.size() + " fragmentos)");
                }
                intentos++;
            }
        }
        throw new IOException("Timeout esperando respuesta UDP despues de " + intentos + " intentos");
    }

    private Mensaje reensamblarFragmentos(TreeMap<Integer, byte[]> fragmentos) throws IOException {
        int total = 0;
        for (byte[] f : fragmentos.values()) total += f.length;
        byte[] full = new byte[total];
        int pos = 0;
        for (byte[] f : fragmentos.values()) {
            System.arraycopy(f, 0, full, pos, f.length);
            pos += f.length;
        }
        return Mensaje.fromJson(new String(full, StandardCharsets.UTF_8));
    }

    private void descargarArchivoUdp(long documentoId, File destino, Consumer<Long> onProgress, Comando comando)
            throws Exception {
        int sessionId = random.nextInt(Integer.MAX_VALUE);

        Mensaje msg = new Mensaje(comando).put("documentoId", documentoId);
        enviarControlUdp(msg, sessionId);

        Mensaje header = recibirControlUdp(sessionId);
        if (header.getComando() == Comando.ERROR) {
            throw new IOException("Error: " + header.getString("detalle"));
        }
        long tamanoEsperado = header.getDatos().containsKey("tamano") ? header.getLong("tamano") : -1;

        try (FileOutputStream fos = new FileOutputStream(destino)) {
            byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
            long totalBytesReceived = 0;
            TreeMap<Integer, byte[]> recibidos = new TreeMap<>();

            while (true) {
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(dp);
                int len = dp.getLength();
                if (len < HEADER_SIZE) continue;

                int tipo = buffer[0] & 0xFF;
                int incomingSessionId = ByteBuffer.wrap(buffer, 1, 4).getInt();
                int seqNum = ByteBuffer.wrap(buffer, 5, 4).getInt();
                if (incomingSessionId != sessionId) continue;

                if (tipo == TIPO_FIN) break;

                if (tipo == TIPO_DATOS) {
                    byte[] payload = new byte[len - HEADER_SIZE];
                    System.arraycopy(buffer, HEADER_SIZE, payload, 0, payload.length);
                    recibidos.put(seqNum, payload);
                    totalBytesReceived += payload.length;
                    if (onProgress != null) onProgress.accept(totalBytesReceived);
                }
            }
            // Escribir en orden de seqNum.
            for (byte[] chunk : recibidos.values()) {
                fos.write(chunk);
            }
            if (tamanoEsperado >= 0 && totalBytesReceived != tamanoEsperado) {
                throw new IOException("Descarga UDP incompleta: esperado="
                        + tamanoEsperado + " recibido=" + totalBytesReceived);
            }
        }
    }

    // ==================== Configuracion ====================

    /**
     * Destino de envio: todos los clientes del servidor o un cliente concreto
     * (posiblemente en otro servidor vinculado por {@code destServidor} = peerId).
     */
    public static final class DestinoEnvio {
        public final boolean todos;
        public final String destIp;
        public final int destPuerto;
        public final String destProtocolo;
        public final String destServidor;

        public static DestinoEnvio todos() {
            return new DestinoEnvio(true, null, 0, null, "local");
        }

        public static DestinoEnvio cliente(String destIp, int destPuerto, String destProtocolo, String destServidor) {
            String srv = destServidor != null && !destServidor.isBlank() ? destServidor : "local";
            return new DestinoEnvio(false, destIp, destPuerto, destProtocolo, srv);
        }

        private DestinoEnvio(boolean todos, String destIp, int destPuerto, String destProtocolo, String destServidor) {
            this.todos = todos;
            this.destIp = destIp;
            this.destPuerto = destPuerto;
            this.destProtocolo = destProtocolo;
            this.destServidor = destServidor;
        }
    }

    private static void aplicarDestinoAlMensaje(Mensaje m, DestinoEnvio d) {
        if (d == null || d.todos) {
            return;
        }
        m.put("envioAlcance", "DIRIGIDO");
        m.put("destIp", d.destIp);
        m.put("destPuerto", d.destPuerto);
        m.put("destProtocolo", d.destProtocolo);
        m.put("destServidor", d.destServidor);
    }

    public void setOnMessageReceived(Consumer<Mensaje> callback) {
        this.onMessageReceived = callback;
    }

    public boolean isConnected() {
        return connected;
    }

    public Protocolo getProtocolo() {
        return protocolo;
    }

    @Override
    public void close() throws IOException {
        connected = false;
        fileUploadPool.shutdownNow();
        if (tcpSocket != null && !tcpSocket.isClosed()) tcpSocket.close();
        if (udpSocket != null && !udpSocket.isClosed()) udpSocket.close();
    }
}
