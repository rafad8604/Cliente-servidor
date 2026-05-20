package com.app.server.net;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.app.server.dao.ClienteConectadoDAO;
import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.server.models.ClienteConectado;
import com.app.server.models.Documento;
import com.app.server.peer.PeerClient;
import com.app.server.peer.PeerInfo;
import com.app.server.peer.PeerProxyService;
import com.app.server.queue.PendingDownloadQueueService;
import com.app.server.service.DocumentoService;
import com.app.server.service.LogService;
import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;

/**
 * Maneja una conexion TCP individual.
 *
 * <p>Cuando el cliente solicita un archivo cuyo {@code servidor} es un peer
 * remoto, el handler hace proxy via {@link PeerProxyService}: descarga del
 * peer y reenvia al cliente como si fuera local.</p>
 */
public class ClientHandler implements Runnable, Closeable {

    private final TcpClientChannel channel;
    private final ClientPool pool;
    private final DocumentoService documentoService;
    private final LogService logService;
    private final ClienteConectadoDAO clienteDAO;
    private final CommandDispatcher dispatcher;
    private final ServerEventBus eventBus;
    private final PeerProxyService peerProxy;
    private final PendingDownloadQueueService pendingQueue;
    private final ClientContext ctx;
    private volatile boolean running = true;

    public ClientHandler(TcpClientChannel channel, ClientPool pool,
                         DocumentoService documentoService, LogService logService,
                         ServerEventBus eventBus) {
        this(channel, pool, documentoService, logService, eventBus, null, null, null);
    }

    public ClientHandler(TcpClientChannel channel, ClientPool pool,
                         DocumentoService documentoService, LogService logService,
                         ServerEventBus eventBus,
                         CommandDispatcher dispatcher,
                         PeerProxyService peerProxy,
                         PendingDownloadQueueService pendingQueue) {
        this.channel = channel;
        this.pool = pool;
        this.documentoService = documentoService;
        this.logService = logService;
        this.clienteDAO = new ClienteConectadoDAO();
        this.eventBus = eventBus;
        this.peerProxy = peerProxy;
                this.pendingQueue = pendingQueue;
        this.dispatcher = dispatcher != null
                ? dispatcher
                : new CommandDispatcher(documentoService, logService, clienteDAO, eventBus);
        this.ctx = channel.getContext();
    }

    /**
     * Constructor de compatibilidad (Socket directo). Mantiene la firma usada
     * por los tests existentes.
     */
    public ClientHandler(Socket socket, ClientPool pool,
                         DocumentoService documentoService, LogService logService) {
        this(new TcpClientChannel(socket), pool, documentoService, logService, null);
    }

    @Override
    public void run() {
        System.out.println("[HANDLER] Cliente conectado: " + ctx);

        try {
            try {
                clienteDAO.registrar(new ClienteConectado(ctx.getIp(), ctx.getPort(), "TCP"));
            } catch (Exception e) {
                System.err.println("[HANDLER] Warning: no se pudo registrar cliente (BD): " + e.getMessage());
            }
            if (logService != null) {
                try { logService.logConexion(ctx.getIp(), "TCP"); } catch (Exception ignored) { }
            }

            Mensaje sesion = new Mensaje(Comando.SESION_INFO)
                    .put("status", "CONECTADO")
                    .put("mensaje", "Conectado al servidor de mensajeria");
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
        } finally {
            cleanup();
        }
    }

    private void procesarComando(Mensaje msg, InputStream socketIn) throws Exception {
        Comando cmd = msg.getComando();
        if (cmd == null) {
            channel.sendMensaje(Mensaje.error("Comando ausente"));
            return;
        }
        System.out.println("[HANDLER] Comando recibido de " + ctx.getIp() + ": " + cmd);

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

        if (!dispatcher.dispatchSimple(channel, msg)) {
            channel.sendMensaje(Mensaje.error("Comando no reconocido: " + cmd));
        }
    }

    private void procesarEnviarArchivo(Mensaje msg, InputStream socketIn) throws Exception {
        String nombre = msg.getString("nombre");
        long tamano = msg.getLong("tamano");

        System.out.println("[HANDLER] Recibiendo archivo: " + nombre + " (" + tamano + " bytes)");

        String remitenteNombre = "";
        DocumentoService.DocumentoEnvioParams envio = DocumentoEnvioHelper.buildLocalParams(
                msg, ctx, remitenteNombre);
        String destServidor = msg.getString("destServidor");

        InputStream limitedStream = new BoundedInputStream(socketIn, tamano);

        if (peerProxy != null && DocumentoEnvioHelper.esPeerRemoto(destServidor, peerProxy.getRegistry())) {
            if (envio.getAlcance() != Documento.EnvioAlcance.DIRIGIDO) {
                channel.sendMensaje(Mensaje.error("Envio a otro servidor requiere envioAlcance DIRIGIDO"));
                return;
            }
            if (envio.getDestIp() == null || envio.getDestPuerto() == null || envio.getDestProtocolo() == null) {
                channel.sendMensaje(Mensaje.error("Destino incompleto: destIp, destPuerto, destProtocolo"));
                return;
            }
            PeerInfo destPeer = peerProxy.getRegistry().getById(destServidor.trim()).orElse(null);
            if (destPeer == null) {
                channel.sendMensaje(Mensaje.error("Peer destino no encontrado u offline"));
                return;
            }
            String localId = peerProxy.getRegistry().getLocalId();
            String origenEtiqueta = peerProxy.getPeerClient().getSelfInfo().getNombre()
                    + " (" + localId.substring(0, Math.min(8, localId.length())) + ")";
            Mensaje relay = DocumentoEnvioHelper.buildRelayArchivoHeader(
                    msg, nombre, tamano, ctx, remitenteNombre, origenEtiqueta, localId);
            try {
                Mensaje respPeer = peerProxy.relayEntregarArchivo(destServidor.trim(), relay, limitedStream, tamano);
                if (respPeer.getComando() == Comando.ERROR) {
                    channel.sendMensaje(Mensaje.error(
                            respPeer.getString("detalle") != null
                                    ? respPeer.getString("detalle") : "Error en peer remoto"));
                    return;
                }
                channel.sendMensaje(respPeer);
            } catch (Exception e) {
                channel.sendMensaje(Mensaje.error("Relay archivo a peer: " + e.getMessage()));
            }
            if (logService != null) {
                logService.logArchivoRecibido(ctx.getIp(), nombre + " (relay)", tamano);
            }
            return;
        }

        if (envio.getAlcance() == Documento.EnvioAlcance.DIRIGIDO) {
            if (envio.getDestIp() == null || envio.getDestPuerto() == null || envio.getDestProtocolo() == null) {
                channel.sendMensaje(Mensaje.error("Destino incompleto para envio DIRIGIDO"));
                return;
            }
        }

        Documento doc = documentoService.procesarArchivo(nombre, tamano, ctx.getIp(), limitedStream, envio);

        if (logService != null) logService.logArchivoRecibido(ctx.getIp(), nombre, tamano);

        channel.sendMensaje(Mensaje.respuestaOk("hash", doc.getHashSha256())
                .put("documentoId", doc.getId())
                .put("mensaje", "Archivo recibido y procesado correctamente"));
    }

    private void procesarDescargarArchivo(Mensaje msg) throws Exception {
        long docId = msg.getLong("documentoId");
        String servidor = msg.getString("servidor");

        if (esRemoto(servidor)) {
            descargarProxy(docId, servidor);
            return;
        }

        Documento doc = documentoService.obtenerDocumento(docId);
        if (doc == null) {
            channel.sendMensaje(Mensaje.error("Documento no encontrado: " + docId));
            return;
        }
        if (!documentoService.puedeAccederDocumentoLocal(docId, ctx.getIp(), ctx.getPort(), ctx.getProtocol())) {
            channel.sendMensaje(Mensaje.error("Acceso denegado al documento"));
            return;
        }
        if (logService != null) logService.logDescarga(ctx.getIp(), doc.getNombre(), "ORIGINAL");

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

        try (InputStream stream = documentoService.getArchivoOriginalStream(docId)) {
            enviarStream(stream, tamanoReal);
        }
    }

    private void procesarDescargarEncriptado(Mensaje msg) throws Exception {
        long docId = msg.getLong("documentoId");
        String servidor = msg.getString("servidor");
        if (esRemoto(servidor)) {
            // Para version encriptada remota: no soportado por ahora (requeriria
            // forwardear cipher entre servidores con misma clave). Devolvemos error claro.
            channel.sendMensaje(Mensaje.error("Descarga encriptada de peers remotos no soportada"));
            return;
        }
        Documento doc = documentoService.obtenerDocumento(docId);
        if (doc == null) {
            channel.sendMensaje(Mensaje.error("Documento no encontrado: " + docId));
            return;
        }
        if (!documentoService.puedeAccederDocumentoLocal(docId, ctx.getIp(), ctx.getPort(), ctx.getProtocol())) {
            channel.sendMensaje(Mensaje.error("Acceso denegado al documento"));
            return;
        }
        if (logService != null) logService.logDescarga(ctx.getIp(), doc.getNombre(), "ENCRIPTADO");

        channel.sendMensaje(Mensaje.respuestaOk()
                .put("nombre", doc.getNombre() + ".enc")
                .put("tamano", -1L)
                .put("hash", doc.getHashSha256())
                .put("tipoDescarga", "ENCRIPTADO"));

        try (InputStream stream = documentoService.getArchivoEncriptadoStream(docId)) {
            enviarStreamConFin(stream);
        }
    }

    private void descargarProxy(long docId, String peerId) throws Exception {
        if (peerProxy == null) {
            channel.sendMensaje(Mensaje.error("Proxy a peers no disponible en este servidor"));
            return;
        }

        try {
            realizarDescargaProxy(docId, peerId);
        } catch (IOException e) {
            if (isPeerUnavailableError(e)) {
                if (pendingQueue != null) {
                    pendingQueue.enqueue(docId, peerId, this);
                }
                String serverName = pendingQueue != null
                        ? pendingQueue.resolveServerDisplayName(peerId)
                        : peerId.substring(0, Math.min(8, peerId.length()));
                channel.sendMensaje(Mensaje.respuestaOk()
                        .put("encolada", true)
                        .put("servidor", serverName)
                        .put("mensaje", "Peticion rechazada, servidor \""
                                + serverName + "\" desconectado, vuelva a intentarlo mas tarde"));
                return;
            }
            throw e;
        }
    }

    private void realizarDescargaProxy(long docId, String peerId) throws Exception {
        try (PeerClient.PeerDownload download = peerProxy.descargar(peerId, docId)) {
            channel.sendMensaje(Mensaje.respuestaOk()
                    .put("nombre", download.getNombre())
                    .put("tamano", download.getTamano())
                    .put("hash", download.getHash())
                    .put("tipoDescarga", "ORIGINAL")
                    .put("origen", "remoto")
                    .put("servidor", peerId));

            byte[] buffer = new byte[8192];
            long remaining = download.getTamano();
            long total = 0;
            InputStream src = download.getStream();
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int n = src.read(buffer, 0, toRead);
                if (n == -1) break;
                channel.sendBytes(buffer, 0, n);
                total += n;
                remaining -= n;
            }
            channel.flush();
            if (logService != null) {
                logService.logDescarga(ctx.getIp(),
                        download.getNombre() + " @peer=" + peerId.substring(0, Math.min(8, peerId.length())),
                        "ORIGINAL_PROXY");
            }
            if (total != download.getTamano()) {
                throw new IOException("Proxy incompleto: enviados=" + total + " esperado=" + download.getTamano());
            }
        }
    }

    public void retryQueuedDownload(long documentoId, String peerId) throws Exception {
        if (!channel.isOpen()) {
            throw new IOException("Cliente desconectado, no se puede reintentar entrega");
        }
        System.out.println("[HANDLER] retryQueuedDownload: docId=" + documentoId + " peer=" + (peerId == null ? "(nulo)" : peerId)
                + " cliente=" + ctx);
        try {
            realizarDescargaProxy(documentoId, peerId);
        } catch (Exception e) {
            System.err.println("[HANDLER] Error reentregando docId=" + documentoId + " a cliente=" + ctx + ": " + e.getMessage());
            throw e;
        }
    }

    public ClientContext getContext() {
        return ctx;
    }

    public boolean isActive() {
        return channel.isOpen();
    }

    private boolean isPeerUnavailableError(Throwable t) {
        if (t == null) {
            return false;
        }
        if (t instanceof IOException) {
            String msg = t.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("peer no disponible")
                        || lower.contains("connection refused")
                        || lower.contains("connection reset")
                        || lower.contains("connection reset by peer")
                        || lower.contains("connect timed out")
                        || lower.contains("timed out")
                        || lower.contains("no route to host")
                        || lower.contains("conexion peer cerrada inesperadamente")
                        || lower.contains("respuesta vacia del peer")
                        || lower.contains("socket closed")) {
                    return true;
                }
            }
        }
        return isPeerUnavailableError(t.getCause());
    }

    private boolean esRemoto(String servidor) {
        return servidor != null && !servidor.isBlank() && !"local".equalsIgnoreCase(servidor);
    }

    private void enviarStream(InputStream stream, long expectedSize) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalSent = 0;
        while ((bytesRead = stream.read(buffer)) != -1) {
            channel.sendBytes(buffer, 0, bytesRead);
            totalSent += bytesRead;
        }
        channel.flush();
        if (expectedSize >= 0 && totalSent != expectedSize) {
            throw new IOException("Transferencia incompleta: enviados=" + totalSent + " esperado=" + expectedSize);
        }
    }

    private void enviarStreamConFin(InputStream stream) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = stream.read(buffer)) != -1) {
            channel.sendChunkedBlock(buffer, bytesRead);
        }
        channel.sendChunkedBlock(new byte[0], 0);
        channel.flush();
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
                if (lineBuffer.size() == 0) return null;
                break;
            }
            if (b == '\n') break;
            if (b != '\r') lineBuffer.write(b);
        }
        return lineBuffer.toString(StandardCharsets.UTF_8);
    }
}
