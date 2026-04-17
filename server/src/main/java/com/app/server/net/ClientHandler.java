package com.app.server.net;

import com.app.server.dao.ClienteConectadoDAO;
import com.app.server.models.ClienteConectado;
import com.app.server.models.Documento;
import com.app.server.service.DocumentoService;
import com.app.server.service.LogService;
import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Maneja una conexión individual de un cliente TCP.
 * Procesa comandos JSON de metadata y streams binarios.
 */
public class ClientHandler implements Runnable, Closeable {

    private final Socket socket;
    private final ClientPool pool;
    private final DocumentoService documentoService;
    private final LogService logService;
    private final ClienteConectadoDAO clienteDAO;
    private final String clientIp;
    private final int clientPort;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, ClientPool pool, DocumentoService documentoService,
                         LogService logService) {
        this.socket = socket;
        this.pool = pool;
        this.documentoService = documentoService;
        this.logService = logService;
        this.clienteDAO = new ClienteConectadoDAO();
        this.clientIp = socket.getInetAddress().getHostAddress();
        this.clientPort = socket.getPort();
    }

    @Override
    public void run() {
        System.out.println("[HANDLER] Cliente conectado: " + clientIp + ":" + clientPort);

        try {
            // Registrar cliente
            clienteDAO.registrar(new ClienteConectado(clientIp, clientPort, "TCP"));
            logService.logConexion(clientIp, "TCP");

            // Enviar clave de sesión al cliente
            // (En producción se haría Diffie-Hellman, pero aquí enviamos info de sesión)
            Mensaje sesion = new Mensaje(Comando.SESION_INFO)
                    .put("status", "CONECTADO")
                    .put("mensaje", "Conectado al servidor de mensajería");
            enviarMensaje(sesion);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            DataInputStream dataIn = new DataInputStream(socket.getInputStream());

            // Loop principal: leer comandos JSON
            while (running && !socket.isClosed()) {
                String line = reader.readLine();
                if (line == null) break; // Cliente desconectado

                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    Mensaje msg = Mensaje.fromJson(line);
                    procesarComando(msg, dataIn);
                } catch (Exception e) {
                    System.err.println("[HANDLER] Error procesando comando: " + e.getMessage());
                    enviarMensaje(Mensaje.error("Error procesando comando: " + e.getMessage()));
                }
            }

        } catch (IOException e) {
            if (running) {
                System.err.println("[HANDLER] Error I/O con " + clientIp + ": " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[HANDLER] Error inesperado: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void procesarComando(Mensaje msg, DataInputStream dataIn) throws Exception {
        Comando cmd = msg.getComando();
        System.out.println("[HANDLER] Comando recibido de " + clientIp + ": " + cmd);

        switch (cmd) {
            case ENVIAR_ARCHIVO -> procesarEnviarArchivo(msg, dataIn);
            case ENVIAR_MENSAJE -> procesarEnviarMensaje(msg);
            case LISTAR_DOCUMENTOS -> procesarListarDocumentos();
            case DESCARGAR_ARCHIVO -> procesarDescargarArchivo(msg);
            case DESCARGAR_HASH -> procesarDescargarHash(msg);
            case DESCARGAR_ENCRIPTADO -> procesarDescargarEncriptado(msg);
            case LISTAR_CLIENTES -> procesarListarClientes();
            default -> enviarMensaje(Mensaje.error("Comando no reconocido: " + cmd));
        }
    }

    private void procesarEnviarArchivo(Mensaje msg, DataInputStream dataIn) throws Exception {
        String nombre = msg.getString("nombre");
        long tamano = msg.getLong("tamano");

        System.out.println("[HANDLER] Recibiendo archivo: " + nombre + " (" + tamano + " bytes)");

        // Leer exactamente 'tamano' bytes del stream
        InputStream limitedStream = new BoundedInputStream(dataIn, tamano);

        Documento doc = documentoService.procesarArchivo(nombre, tamano, clientIp, limitedStream);

        logService.logArchivoRecibido(clientIp, nombre, tamano);

        Mensaje respuesta = Mensaje.respuestaOk("hash", doc.getHashSha256())
                .put("documentoId", doc.getId())
                .put("mensaje", "Archivo recibido y procesado correctamente");

        enviarMensaje(respuesta);
    }

    private void procesarEnviarMensaje(Mensaje msg) throws Exception {
        String texto = msg.getString("texto");
        if (texto == null || texto.isEmpty()) {
            enviarMensaje(Mensaje.error("Mensaje vacío"));
            return;
        }

        Documento doc = documentoService.procesarMensaje(texto, clientIp);
        logService.logMensajeRecibido(clientIp);

        Mensaje respuesta = Mensaje.respuestaOk("hash", doc.getHashSha256())
                .put("documentoId", doc.getId())
                .put("mensaje", "Mensaje recibido y procesado");

        enviarMensaje(respuesta);
    }

    private void procesarListarDocumentos() throws Exception {
        List<Documento> docs = documentoService.listarDocumentos();

        // Convertir a JSON-friendly format
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < docs.size(); i++) {
            Documento d = docs.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(
                    "{\"id\":%d,\"nombre\":\"%s\",\"extension\":\"%s\",\"tamano\":%d,\"tipo\":\"%s\",\"hash\":\"%s\",\"ip\":\"%s\",\"fecha\":\"%s\"}",
                    d.getId(), escapeJson(d.getNombre()), escapeJson(d.getExtension() != null ? d.getExtension() : ""),
                    d.getTamano(), d.getTipo().name(), d.getHashSha256(), d.getIpPropietario(),
                    d.getFechaCreacion().toString()));
        }
        sb.append("]");

        Mensaje respuesta = Mensaje.respuestaOk("documentos", sb.toString())
                .put("total", docs.size());
        enviarMensaje(respuesta);
    }

    private void procesarDescargarArchivo(Mensaje msg) throws Exception {
        long docId = msg.getLong("documentoId");
        Documento doc = documentoService.obtenerDocumento(docId);
        if (doc == null) {
            enviarMensaje(Mensaje.error("Documento no encontrado: " + docId));
            return;
        }

        logService.logDescarga(clientIp, doc.getNombre(), "ORIGINAL");

        // Obtener stream original
        InputStream stream = documentoService.getArchivoOriginalStream(docId);

        // Enviar metadatos primero
        Mensaje header = Mensaje.respuestaOk()
                .put("nombre", doc.getNombre())
                .put("tamano", doc.getTamano())
                .put("hash", doc.getHashSha256())
                .put("tipoDescarga", "ORIGINAL");
        enviarMensaje(header);

        // Luego enviar datos binarios
        enviarStream(stream, doc.getTamano());
    }

    private void procesarDescargarHash(Mensaje msg) throws Exception {
        long docId = msg.getLong("documentoId");
        String hash = documentoService.getHash(docId);
        if (hash == null) {
            enviarMensaje(Mensaje.error("Documento no encontrado: " + docId));
            return;
        }

        logService.logDescarga(clientIp, "doc-" + docId, "HASH");

        byte[] hashBytes = hash.getBytes(StandardCharsets.UTF_8);
        Mensaje respuesta = Mensaje.respuestaOk("hash", hash)
                .put("tamano", hashBytes.length)
                .put("tipoDescarga", "HASH");
        enviarMensaje(respuesta);
    }

    private void procesarDescargarEncriptado(Mensaje msg) throws Exception {
        long docId = msg.getLong("documentoId");
        Documento doc = documentoService.obtenerDocumento(docId);
        if (doc == null) {
            enviarMensaje(Mensaje.error("Documento no encontrado: " + docId));
            return;
        }

        logService.logDescarga(clientIp, doc.getNombre(), "ENCRIPTADO");

        InputStream stream = documentoService.getArchivoEncriptadoStream(docId);

        // Para encriptado, no sabemos el tamaño exacto fácilmente, usamos -1
        Mensaje header = Mensaje.respuestaOk()
                .put("nombre", doc.getNombre() + ".enc")
                .put("tamano", -1L)
                .put("hash", doc.getHashSha256())
                .put("tipoDescarga", "ENCRIPTADO");
        enviarMensaje(header);

        // Enviar chunks con marcador de fin
        enviarStreamConFin(stream);
    }

    private void procesarListarClientes() throws Exception {
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

        Mensaje respuesta = Mensaje.respuestaOk("clientes", sb.toString())
                .put("total", clientes.size());
        enviarMensaje(respuesta);
    }

    // --- Métodos de utilidad de red ---

    private void enviarMensaje(Mensaje msg) throws IOException {
        String json = msg.toJson() + "\n";
        synchronized (socket.getOutputStream()) {
            socket.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        }
    }

    private void enviarStream(InputStream stream, long expectedSize) throws IOException {
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalSent = 0;
        while ((bytesRead = stream.read(buffer)) != -1) {
            synchronized (socket.getOutputStream()) {
                dos.write(buffer, 0, bytesRead);
            }
            totalSent += bytesRead;
        }
        dos.flush();
        stream.close();
        System.out.println("[HANDLER] Stream enviado: " + totalSent + " bytes");
    }

    private void enviarStreamConFin(InputStream stream) throws IOException {
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalSent = 0;

        while ((bytesRead = stream.read(buffer)) != -1) {
            synchronized (socket.getOutputStream()) {
                dos.writeInt(bytesRead); // Enviar tamaño del bloque
                dos.write(buffer, 0, bytesRead);
            }
            totalSent += bytesRead;
        }
        // Marcador de fin
        synchronized (socket.getOutputStream()) {
            dos.writeInt(0);
            dos.flush();
        }
        stream.close();
        System.out.println("[HANDLER] Stream encriptado enviado: " + totalSent + " bytes");
    }

    private void cleanup() {
        try {
            clienteDAO.eliminar(clientIp, clientPort);
        } catch (Exception e) {
            System.err.println("[HANDLER] Error eliminando cliente: " + e.getMessage());
        }
        logService.logDesconexion(clientIp);
        pool.unregisterHandler(this);
        pool.release();
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException e) {
            // ignore
        }
        System.out.println("[HANDLER] Cliente desconectado: " + clientIp + ":" + clientPort);
    }

    @Override
    public void close() {
        running = false;
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException e) {
            // ignore
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * InputStream que limita la lectura a un número específico de bytes.
     * Evita que el handler lea más allá del archivo en el stream compartido.
     */
    private static class BoundedInputStream extends InputStream {
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
