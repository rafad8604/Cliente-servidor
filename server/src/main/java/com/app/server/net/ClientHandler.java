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
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Maneja una conexión TCP individual.
 *
 * Tras el refactor:
 *   - Usa {@link TcpClientChannel} (abstracción) para enviar respuestas.
 *   - Usa {@link CommandDispatcher} para comandos "simples" (sin stream).
 *   - Publica eventos en el {@link ServerEventBus}.
 *   - Sólo mantiene lógica específica de TCP para comandos con stream
 *     (ENVIAR_ARCHIVO / DESCARGAR_ARCHIVO / DESCARGAR_ENCRIPTADO).
 */
public class ClientHandler implements Runnable, Closeable {

    private final TcpClientChannel channel;
    private final ClientPool pool;
    private final DocumentoService documentoService;
    private final LogService logService;
    private final ClienteConectadoDAO clienteDAO;
    private final CommandDispatcher dispatcher;
    private final ServerEventBus eventBus;
    private final ClientContext ctx;
    private volatile boolean running = true;

    /**
     * Constructor principal (usado por {@link ServerCore}).
     */
    public ClientHandler(TcpClientChannel channel, ClientPool pool,
                         DocumentoService documentoService, LogService logService,
                         ServerEventBus eventBus) {
        this.channel = channel;
        this.pool = pool;
        this.documentoService = documentoService;
        this.logService = logService;
        this.clienteDAO = new ClienteConectadoDAO();
        this.eventBus = eventBus;
        this.dispatcher = new CommandDispatcher(documentoService, logService, clienteDAO, eventBus);
        this.ctx = channel.getContext();
    }

    /**
     * Constructor de compatibilidad (Socket directo). Mantiene la firma usada
     * por los tests existentes y crea internamente el adapter.
     */
    public ClientHandler(Socket socket, ClientPool pool,
                         DocumentoService documentoService, LogService logService) {
        this(new TcpClientChannel(socket), pool, documentoService, logService, null);
    }

    @Override
    public void run() {
        System.out.println("[HANDLER] Cliente conectado: " + ctx);

        try {
            clienteDAO.registrar(new ClienteConectado(ctx.getIp(), ctx.getPort(), "TCP"));
            if (logService != null) logService.logConexion(ctx.getIp(), "TCP");

            Mensaje sesion = new Mensaje(Comando.SESION_INFO)
                    .put("status", "CONECTADO")
                    .put("mensaje", "Conectado al servidor de mensajería");
            channel.sendMensaje(sesion);

            InputStream socketIn = channel.inputStream();

            while (running && channel.isOpen()) {
                String line = leerLinea(socketIn);
                if (line == null) break;

                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    Mensaje msg = Mensaje.fromJson(line);
                    procesarComando(msg, socketIn);
                } catch (Exception e) {
                    System.err.println("[HANDLER] Error procesando comando: " + e.getMessage());
                    if (eventBus != null) eventBus.publishError("tcp-handler", e, ctx);
                    channel.sendMensaje(Mensaje.error("Error procesando comando: " + e.getMessage()));
                }
            }

        } catch (IOException e) {
            if (running) {
                System.err.println("[HANDLER] Error I/O con " + ctx + ": " + e.getMessage());
                if (eventBus != null) eventBus.publishError("tcp-handler", e, ctx);
            }
        } catch (Exception e) {
            System.err.println("[HANDLER] Error inesperado: " + e.getMessage());
            if (eventBus != null) eventBus.publishError("tcp-handler", e, ctx);
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void procesarComando(Mensaje msg, InputStream socketIn) throws Exception {
        Comando cmd = msg.getComando();
        System.out.println("[HANDLER] Comando recibido de " + ctx.getIp() + ": " + cmd);

        // Primero comandos específicos de TCP (con stream)
        switch (cmd) {
            case ENVIAR_ARCHIVO:
                procesarEnviarArchivo(msg, socketIn);
                return;
            case DESCARGAR_ARCHIVO:
                procesarDescargarArchivo(msg);
                return;
            case DESCARGAR_ENCRIPTADO:
                procesarDescargarEncriptado(msg);
                return;
            default:
                break;
        }

        // Luego delega en el dispatcher compartido
        if (!dispatcher.dispatchSimple(channel, msg)) {
            channel.sendMensaje(Mensaje.error("Comando no reconocido: " + cmd));
        }
    }

    private void procesarEnviarArchivo(Mensaje msg, InputStream socketIn) throws Exception {
        String nombre = msg.getString("nombre");
        long tamano = msg.getLong("tamano");

        System.out.println("[HANDLER] Recibiendo archivo: " + nombre + " (" + tamano + " bytes)");

        InputStream limitedStream = new BoundedInputStream(socketIn, tamano);
        Documento doc = documentoService.procesarArchivo(nombre, tamano, ctx.getIp(), limitedStream);

        if (logService != null) logService.logArchivoRecibido(ctx.getIp(), nombre, tamano);

        channel.sendMensaje(Mensaje.respuestaOk("hash", doc.getHashSha256())
                .put("documentoId", doc.getId())
                .put("mensaje", "Archivo recibido y procesado correctamente"));
    }

    private void procesarDescargarArchivo(Mensaje msg) throws Exception {
        long docId = msg.getLong("documentoId");
        Documento doc = documentoService.obtenerDocumento(docId);
        if (doc == null) {
            channel.sendMensaje(Mensaje.error("Documento no encontrado: " + docId));
            return;
        }

        if (logService != null) logService.logDescarga(ctx.getIp(), doc.getNombre(), "ORIGINAL");

        InputStream stream = documentoService.getArchivoOriginalStream(docId);
        long tamanoReal = doc.getTamano();
        if (doc.getRutaLocalOriginal() != null) {
            File f = new File(doc.getRutaLocalOriginal());
            if (f.exists()) tamanoReal = f.length();
        }

        channel.sendMensaje(Mensaje.respuestaOk()
                .put("nombre", doc.getNombre())
                .put("tamano", tamanoReal)
                .put("hash", doc.getHashSha256())
                .put("tipoDescarga", "ORIGINAL"));

        enviarStream(stream, tamanoReal);
    }

    private void procesarDescargarEncriptado(Mensaje msg) throws Exception {
        long docId = msg.getLong("documentoId");
        Documento doc = documentoService.obtenerDocumento(docId);
        if (doc == null) {
            channel.sendMensaje(Mensaje.error("Documento no encontrado: " + docId));
            return;
        }

        if (logService != null) logService.logDescarga(ctx.getIp(), doc.getNombre(), "ENCRIPTADO");

        InputStream stream = documentoService.getArchivoEncriptadoStream(docId);

        channel.sendMensaje(Mensaje.respuestaOk()
                .put("nombre", doc.getNombre() + ".enc")
                .put("tamano", -1L)
                .put("hash", doc.getHashSha256())
                .put("tipoDescarga", "ENCRIPTADO"));

        enviarStreamConFin(stream);
    }

    private void enviarStream(InputStream stream, long expectedSize) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalSent = 0;
        try {
            while ((bytesRead = stream.read(buffer)) != -1) {
                channel.sendBytes(buffer, 0, bytesRead);
                totalSent += bytesRead;
            }
            channel.flush();
            if (expectedSize >= 0 && totalSent != expectedSize) {
                throw new IOException("Transferencia incompleta: enviados=" + totalSent + " esperado=" + expectedSize);
            }
            System.out.println("[HANDLER] Stream enviado: " + totalSent + " bytes");
        } finally {
            stream.close();
        }
    }

    private void enviarStreamConFin(InputStream stream) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalSent = 0;
        try {
            while ((bytesRead = stream.read(buffer)) != -1) {
                channel.sendChunkedBlock(buffer, bytesRead);
                totalSent += bytesRead;
            }
            // Marcador de fin
            channel.sendChunkedBlock(new byte[0], 0);
            channel.flush();
            System.out.println("[HANDLER] Stream encriptado enviado: " + totalSent + " bytes");
        } finally {
            stream.close();
        }
    }

    private void cleanup() {
        try {
            clienteDAO.eliminar(ctx.getIp(), ctx.getPort());
        } catch (Exception e) {
            System.err.println("[HANDLER] Error eliminando cliente: " + e.getMessage());
        }
        if (logService != null) logService.logDesconexion(ctx.getIp());
        pool.unregisterHandler(this);
        pool.release();
        channel.close();
        if (eventBus != null) {
            eventBus.publish(ServerEventType.TCP_CONEXION_CERRADA, ctx, null);
        }
        System.out.println("[HANDLER] Cliente desconectado: " + ctx);
    }

    @Override
    public void close() {
        running = false;
        channel.close();
    }

    private String leerLinea(InputStream input) throws IOException {
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(256);
        while (true) {
            int b = input.read();
            if (b == -1) {
                if (lineBuffer.size() == 0) {
                    return null;
                }
                break;
            }
            if (b == '\n') break;
            if (b != '\r') lineBuffer.write(b);
        }
        return lineBuffer.toString(StandardCharsets.UTF_8);
    }

    /**
     * InputStream que limita la lectura a un número específico de bytes.
     * Evita que el handler lea más allá del archivo en el stream compartido.
     */
    static class BoundedInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        BoundedInputStream(InputStream in, long limit) {
            this.in = in;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = in.read();
            if (b != -1) remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            int bytesRead = in.read(b, off, toRead);
            if (bytesRead > 0) remaining -= bytesRead;
            return bytesRead;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(in.available(), remaining);
        }
    }
}
