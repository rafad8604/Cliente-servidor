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
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Cliente de red que soporta TCP y UDP.
 * El protocolo se selecciona al momento de la conexión.
 */
public class NetworkClient implements Closeable {

    public enum Protocolo { TCP, UDP }

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
    private int udpPort = 9001; // Puerto por defecto para UDP
    private static final int MAX_DATAGRAM_SIZE = 60000;
    private static final int HEADER_SIZE = 9;
    private static final int DATA_PAYLOAD_SIZE = MAX_DATAGRAM_SIZE - HEADER_SIZE;

    // Callback para mensajes entrantes (chat, notificaciones)
    private Consumer<Mensaje> onMessageReceived;
    private volatile boolean connected = false;

    // Pool de hilos para envío de múltiples archivos
    private final ExecutorService fileUploadPool = Executors.newFixedThreadPool(3);

    // Para generar session IDs únicos (UDP)
    private final Random random = new Random();
    private final Object tcpRequestLock = new Object();

    public NetworkClient(String host, int port, Protocolo protocolo) {
        this.host = host;
        this.port = port;
        this.protocolo = protocolo;
    }

    /**
     * Conecta al servidor usando el protocolo configurado.
     */
    public Mensaje conectar() throws IOException {
        if (protocolo == Protocolo.TCP) {
            return conectarTcp();
        } else {
            return conectarUdp();
        }
    }

    private Mensaje conectarTcp() throws IOException {
        tcpSocket = new Socket();
        tcpSocket.connect(new InetSocketAddress(host, port), 10000);
        tcpSocket.setSoTimeout(0); // Sin timeout para el listener
        tcpIn = tcpSocket.getInputStream();
        tcpOut = tcpSocket.getOutputStream();

        connected = true;

        // Leer mensaje de sesión del servidor
        String firstLine = leerLineaTcp();
        Mensaje sesion = (firstLine != null) ? Mensaje.fromJson(firstLine) : new Mensaje(Comando.SESION_INFO);

        return sesion;
    }

    private Mensaje conectarUdp() throws IOException {
        udpSocket = new DatagramSocket();
        udpSocket.setSoTimeout(10000);
        udpAddress = InetAddress.getByName(host);
        
        // UDP usa puerto = puerto_tcp + 1 (por convención: 9000 TCP -> 9001 UDP)
        this.udpPort = (port == 9000) ? 9001 : (port + 1);
        System.out.println("[UDP] Conectando a " + host + ":" + udpPort);
        connected = true;

        // Enviar paquete de handshake al servidor para registrarse
        Mensaje handshake = new Mensaje(Comando.LISTAR_CLIENTES);
        int sessionId = random.nextInt(Integer.MAX_VALUE);
        try {
            byte[] payload = handshake.toJson().getBytes(StandardCharsets.UTF_8);
            byte[] packet = new byte[HEADER_SIZE + payload.length];
            packet[0] = 0; // tipo control
            ByteBuffer.wrap(packet, 1, 4).putInt(sessionId);
            ByteBuffer.wrap(packet, 5, 4).putInt(0);
            System.arraycopy(payload, 0, packet, HEADER_SIZE, payload.length);
            DatagramPacket dp = new DatagramPacket(packet, packet.length, udpAddress, udpPort);
            udpSocket.send(dp);
            
            recibirControlUdp(sessionId);
        } catch (Exception e) {
            System.err.println("[UDP] Advertencia en handshake UDP: " + e.getMessage());
        }

        return new Mensaje(Comando.SESION_INFO).put("status", "CONECTADO").put("protocolo", "UDP");
    }

    /**
     * Envía un archivo al servidor.
     */
    public CompletableFuture<Mensaje> enviarArchivo(File file, Consumer<Long> onProgress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (protocolo == Protocolo.TCP) {
                    return enviarArchivoTcp(file, onProgress);
                } else {
                    return enviarArchivoUdp(file, onProgress);
                }
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, fileUploadPool);
    }

    private Mensaje enviarArchivoTcp(File file, Consumer<Long> onProgress) throws Exception {
        synchronized (tcpRequestLock) {
            // 1. Enviar header JSON
            Mensaje header = new Mensaje(Comando.ENVIAR_ARCHIVO)
                    .put("nombre", file.getName())
                    .put("tamano", file.length());

            enviarLineaTcp(header.toJson());

            // 2. Enviar datos del archivo
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

            // 3. Esperar respuesta
            return esperarRespuestaTcp();
        }
    }

    private Mensaje enviarArchivoUdp(File file, Consumer<Long> onProgress) throws Exception {
        int sessionId = random.nextInt(Integer.MAX_VALUE);

        // 1. Enviar comando de control
        Mensaje header = new Mensaje(Comando.ENVIAR_ARCHIVO)
                .put("nombre", file.getName())
                .put("tamano", file.length());

        enviarControlUdp(header, sessionId);

        // Esperar ACK de inicio
        recibirControlUdp(sessionId);

        // 2. Enviar datos fragmentados
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[DATA_PAYLOAD_SIZE];
            int seqNum = 0;
            int bytesRead;
            long totalSent = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunk = bytesRead < buffer.length ?
                        Arrays.copyOf(buffer, bytesRead) : buffer.clone();
                enviarDatosUdp(sessionId, seqNum, chunk);
                seqNum++;
                totalSent += bytesRead;
                if (onProgress != null) onProgress.accept(totalSent);

                // Pequeña pausa para control de flujo
                Thread.sleep(1);
            }
        }

        // 3. Enviar FIN
        enviarFinUdp(sessionId);

        // 4. Esperar respuesta
        return recibirControlUdp(sessionId);
    }

    /**
     * Envía un mensaje de texto al servidor.
     */
    public Mensaje enviarMensaje(String texto) throws IOException {
        Mensaje msg = new Mensaje(Comando.ENVIAR_MENSAJE).put("texto", texto);

        if (protocolo == Protocolo.TCP) {
            synchronized (tcpRequestLock) {
                enviarLineaTcp(msg.toJson());
                return esperarRespuestaTcp();
            }
        } else {
            int sessionId = random.nextInt(Integer.MAX_VALUE);
            enviarControlUdp(msg, sessionId);
            return recibirControlUdp(sessionId);
        }
    }

    /**
     * Solicita la lista de documentos en el servidor.
     */
    public Mensaje listarDocumentos() throws IOException {
        Mensaje msg = new Mensaje(Comando.LISTAR_DOCUMENTOS);
        if (protocolo == Protocolo.TCP) {
            synchronized (tcpRequestLock) {
                enviarLineaTcp(msg.toJson());
                return esperarRespuestaTcp();
            }
        } else {
            int sessionId = random.nextInt(Integer.MAX_VALUE);
            enviarControlUdp(msg, sessionId);
            return recibirControlUdp(sessionId);
        }
    }

    /**
     * Solicita la lista de clientes conectados.
     */
    public Mensaje listarClientes() throws IOException {
        Mensaje msg = new Mensaje(Comando.LISTAR_CLIENTES);
        if (protocolo == Protocolo.TCP) {
            synchronized (tcpRequestLock) {
                enviarLineaTcp(msg.toJson());
                return esperarRespuestaTcp();
            }
        } else {
            int sessionId = random.nextInt(Integer.MAX_VALUE);
            enviarControlUdp(msg, sessionId);
            return recibirControlUdp(sessionId);
        }
    }

    /**
     * Descarga un archivo original del servidor (TCP).
     */
    public void descargarArchivo(long documentoId, File destino, Consumer<Long> onProgress) throws Exception {
        if (protocolo == Protocolo.TCP) {
            descargarArchivoTcp(documentoId, destino, onProgress, "DESCARGAR_ARCHIVO");
        } else {
            descargarArchivoUdp(documentoId, destino, onProgress, Comando.DESCARGAR_ARCHIVO);
        }
    }

    /**
     * Descarga la versión encriptada de un archivo (TCP).
     */
    public void descargarEncriptado(long documentoId, File destino, Consumer<Long> onProgress) throws Exception {
        if (protocolo == Protocolo.TCP) {
            descargarEncriptadoTcp(documentoId, destino, onProgress);
        } else {
            descargarArchivoUdp(documentoId, destino, onProgress, Comando.DESCARGAR_ENCRIPTADO);
        }
    }

    /**
     * Obtiene el hash de un documento.
     */
    public Mensaje descargarHash(long documentoId) throws IOException {
        Mensaje msg = new Mensaje(Comando.DESCARGAR_HASH).put("documentoId", documentoId);
        if (protocolo == Protocolo.TCP) {
            synchronized (tcpRequestLock) {
                enviarLineaTcp(msg.toJson());
                return esperarRespuestaTcp();
            }
        } else {
            int sessionId = random.nextInt(Integer.MAX_VALUE);
            enviarControlUdp(msg, sessionId);
            return recibirControlUdp(sessionId);
        }
    }

    /**
     * Envía múltiples archivos en paralelo usando ExecutorService.
     */
    public List<CompletableFuture<Mensaje>> enviarArchivosParalelo(List<File> archivos, Consumer<Long> onProgress) {
        return archivos.stream()
                .map(f -> enviarArchivo(f, onProgress))
                .toList();
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
        if (line == null) {
            throw new IOException("Conexión TCP cerrada por el servidor");
        }
        line = line.trim();
        if (line.isEmpty()) {
            throw new IOException("Respuesta TCP vacía");
        }
        return Mensaje.fromJson(line);
    }

    private String leerLineaTcp() throws IOException {
        if (tcpIn == null) {
            throw new IOException("Socket TCP no inicializado");
        }

        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(256);
        while (true) {
            int b = tcpIn.read();
            if (b == -1) {
                if (lineBuffer.size() == 0) {
                    return null;
                }
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                lineBuffer.write(b);
            }
        }
        return lineBuffer.toString(StandardCharsets.UTF_8);
    }

    private void descargarArchivoTcp(long documentoId, File destino, Consumer<Long> onProgress, String tipo)
            throws Exception {
        synchronized (tcpRequestLock) {
            Mensaje msg = new Mensaje(Comando.DESCARGAR_ARCHIVO).put("documentoId", documentoId);
            enviarLineaTcp(msg.toJson());

            // Esperar header de respuesta
            Mensaje header = esperarRespuestaTcp();
            if (header.getComando() == Comando.ERROR) {
                throw new IOException("Error: " + header.getString("detalle"));
            }

            long tamano = header.getLong("tamano");

            // Leer datos binarios
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
                    throw new IOException("Descarga incompleta: faltan " + remaining + " bytes (recibidos " + totalRead + ")");
                }
            }
        }
    }

    private void descargarEncriptadoTcp(long documentoId, File destino, Consumer<Long> onProgress)
            throws Exception {
        synchronized (tcpRequestLock) {
            Mensaje msg = new Mensaje(Comando.DESCARGAR_ENCRIPTADO).put("documentoId", documentoId);
            enviarLineaTcp(msg.toJson());

            // Esperar header
            Mensaje header = esperarRespuestaTcp();
            if (header.getComando() == Comando.ERROR) {
                throw new IOException("Error: " + header.getString("detalle"));
            }

            // Leer bloques con marcador de fin
            DataInputStream dis = new DataInputStream(tcpIn);
            try (FileOutputStream fos = new FileOutputStream(destino)) {
                long totalRead = 0;
                while (true) {
                    int blockSize = dis.readInt();
                    if (blockSize == 0) break; // FIN

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
        enviarPaqueteUdp(0, sessionId, 0, payload);
    }

    private void enviarDatosUdp(int sessionId, int seqNum, byte[] data) throws IOException {
        enviarPaqueteUdp(1, sessionId, seqNum, data);
    }

    private void enviarFinUdp(int sessionId) throws IOException {
        enviarPaqueteUdp(3, sessionId, 0, new byte[0]);
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

    private Mensaje recibirControlUdp(int expectedSessionId) throws IOException {
        byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
        DatagramPacket dp = new DatagramPacket(buffer, buffer.length);

        int intentos = 0;
        while (intentos < 5) {
            try {
                udpSocket.receive(dp);
                byte[] data = Arrays.copyOf(dp.getData(), dp.getLength());

                if (data.length < HEADER_SIZE) continue;

                int tipo = data[0] & 0xFF;
                int sessionId = ByteBuffer.wrap(data, 1, 4).getInt();

                if (tipo == 0 && sessionId == expectedSessionId) {
                    byte[] payload = Arrays.copyOfRange(data, HEADER_SIZE, data.length);
                    return Mensaje.fromJson(new String(payload, StandardCharsets.UTF_8));
                }
            } catch (SocketTimeoutException e) {
                intentos++;
            }
        }
        throw new IOException("Timeout esperando respuesta UDP después de " + intentos + " intentos");
    }

    private void descargarArchivoUdp(long documentoId, File destino, Consumer<Long> onProgress, Comando comando) throws Exception {
        int sessionId = random.nextInt(Integer.MAX_VALUE);

        Mensaje msg = new Mensaje(comando).put("documentoId", documentoId);
        enviarControlUdp(msg, sessionId);

        // Esperar header
        Mensaje header = recibirControlUdp(sessionId);
        if (header.getComando() == Comando.ERROR) {
            throw new IOException("Error: " + header.getString("detalle"));
        }

        // Recibir datos fragmentados
        try (FileOutputStream fos = new FileOutputStream(destino)) {
            byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
            long totalBytesReceived = 0;

            while (true) {
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(dp);
                byte[] data = Arrays.copyOf(dp.getData(), dp.getLength());

                if (data.length < HEADER_SIZE) continue;

                int tipo = data[0] & 0xFF;
                int incomingSessionId = ByteBuffer.wrap(data, 1, 4).getInt();

                if (incomingSessionId != sessionId) continue;

                if (tipo == 3) break; // FIN

                if (tipo == 1) {
                    byte[] payload = Arrays.copyOfRange(data, HEADER_SIZE, data.length);
                    fos.write(payload);
                    totalBytesReceived += payload.length;
                    if (onProgress != null) onProgress.accept(totalBytesReceived);
                }
            }
        }
    }

    // ==================== Configuración ====================

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

        if (tcpSocket != null && !tcpSocket.isClosed()) {
            tcpSocket.close();
        }
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
    }
}
