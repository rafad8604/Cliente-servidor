package com.app.server.peer;

import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Cliente para invocar comandos sobre otro peer.
 *
 * <p>Cada metodo abre una conexion TCP nueva contra el {@link PeerInfo}
 * objetivo, hace el handshake {@code PEER_HELLO} y ejecuta el comando.
 * Los metodos de control devuelven la respuesta como {@link Mensaje}.
 * {@link #descargarArchivo} retorna un {@link PeerDownload} que mantiene
 * el socket abierto hasta que el caller lo cierre.</p>
 */
public class PeerClient {

    public static final int DEFAULT_TIMEOUT_MILLIS = 10_000;

    private final PeerInfo selfInfo;
    private final int timeoutMillis;

    public PeerClient(PeerInfo selfInfo) {
        this(selfInfo, DEFAULT_TIMEOUT_MILLIS);
    }

    public PeerClient(PeerInfo selfInfo, int timeoutMillis) {
        this.selfInfo = selfInfo;
        this.timeoutMillis = timeoutMillis;
    }

    public Mensaje ping(PeerInfo peer) throws IOException {
        return ejecutarSimple(peer, new Mensaje(Comando.PEER_PING));
    }

    public Mensaje listarDocs(PeerInfo peer) throws IOException {
        return ejecutarSimple(peer, new Mensaje(Comando.PEER_LISTAR_DOCS));
    }

    public Mensaje hash(PeerInfo peer, long documentoId) throws IOException {
        return ejecutarSimple(peer, new Mensaje(Comando.PEER_DESCARGAR_HASH).put("documentoId", documentoId));
    }

    /**
     * Descarga un archivo de un peer. Retorna un handle con el header parseado
     * y el {@link InputStream} de bytes. El caller DEBE cerrar el handle.
     */
    public PeerDownload descargarArchivo(PeerInfo peer, long documentoId) throws IOException {
        Socket socket = abrirSocket(peer);
        try {
            handshake(socket);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            enviarLinea(out, new Mensaje(Comando.PEER_DESCARGAR_ARCHIVO)
                    .put("documentoId", documentoId).toJson());

            Mensaje header = leerMensaje(in);
            if (header.getComando() == Comando.ERROR) {
                socket.close();
                throw new IOException("Peer rechazo descarga: " + header.getString("detalle"));
            }
            long tamano = header.getLong("tamano");
            String hash = header.getString("hash");
            String nombre = header.getString("nombre");

            return new PeerDownload(socket, in, nombre, tamano, hash);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private Mensaje ejecutarSimple(PeerInfo peer, Mensaje comando) throws IOException {
        try (Socket socket = abrirSocket(peer)) {
            handshake(socket);
            enviarLinea(socket.getOutputStream(), comando.toJson());
            return leerMensaje(socket.getInputStream());
        }
    }

    private Socket abrirSocket(PeerInfo peer) throws IOException {
        Socket socket = new Socket();
        socket.setSoTimeout(timeoutMillis);
        socket.connect(new InetSocketAddress(peer.getHost(), peer.getPuertoPeer()), timeoutMillis);
        return socket;
    }

    private void handshake(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        // Servidor envia primero su saludo.
        leerMensaje(in);
        // Respondemos con nuestro HELLO.
        Mensaje hello = new Mensaje(Comando.PEER_HELLO)
                .put("id", selfInfo.getId())
                .put("host", selfInfo.getHost())
                .put("puertoPeer", selfInfo.getPuertoPeer())
                .put("puertoTcp", selfInfo.getPuertoTcp())
                .put("puertoUdp", selfInfo.getPuertoUdp());
        enviarLinea(out, hello.toJson());
        leerMensaje(in);
    }

    private static void enviarLinea(OutputStream out, String json) throws IOException {
        out.write((json + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static Mensaje leerMensaje(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(256);
        while (true) {
            int b = in.read();
            if (b == -1) {
                if (buf.size() == 0) throw new IOException("Conexion peer cerrada inesperadamente");
                break;
            }
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
        }
        String line = buf.toString(StandardCharsets.UTF_8).trim();
        if (line.isEmpty()) throw new IOException("Respuesta vacia del peer");
        return Mensaje.fromJson(line);
    }

    /**
     * Handle de una descarga abierta contra un peer. Cierre {@code close()} para
     * liberar el socket subyacente.
     */
    public static final class PeerDownload implements Closeable {
        private final Socket socket;
        private final InputStream stream;
        private final String nombre;
        private final long tamano;
        private final String hash;

        PeerDownload(Socket socket, InputStream stream, String nombre, long tamano, String hash) {
            this.socket = socket;
            this.stream = stream;
            this.nombre = nombre;
            this.tamano = tamano;
            this.hash = hash;
        }

        public InputStream getStream() { return stream; }
        public String getNombre() { return nombre; }
        public long getTamano() { return tamano; }
        public String getHash() { return hash; }

        @Override
        public void close() throws IOException {
            if (!socket.isClosed()) socket.close();
        }
    }
}
