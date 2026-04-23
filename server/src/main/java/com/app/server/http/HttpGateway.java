package com.app.server.http;

import com.app.server.dao.ClienteConectadoDAO;
import com.app.server.dao.LogDAO;
import com.app.server.models.ClienteConectado;
import com.app.server.models.Documento;
import com.app.server.models.Log;
import com.app.server.service.DocumentoService;
import com.app.server.service.LogService;
import com.app.shared.protocol.Mensaje;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Gateway HTTP integrado para exponer una interfaz web simple y APIs REST.
 */
public class HttpGateway {

    private final int port;
    private final DocumentoService documentoService;
    private final LogService logService;
    private final ClienteConectadoDAO clienteConectadoDAO;
    private final LogDAO logDAO;
    private final Gson gson;
    private final Map<String, ClienteConectado> sesionesHttp = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> chatHistory = Collections.synchronizedList(new ArrayList<>());

    private HttpServer server;

    public HttpGateway(int port, DocumentoService documentoService, LogService logService) {
        this.port = port;
        this.documentoService = documentoService;
        this.logService = logService;
        this.clienteConectadoDAO = new ClienteConectadoDAO();
        this.logDAO = new LogDAO();
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/api/health", this::handleHealth);
        server.createContext("/api/documentos", this::handleDocumentos);
        server.createContext("/api/clientes", this::handleClientes);
        server.createContext("/api/logs", this::handleLogs);
        server.createContext("/api/upload", this::handleUpload);
        server.createContext("/api/connect", this::handleConnect);
        server.createContext("/api/disconnect", this::handleDisconnect);
        server.createContext("/api/chat", this::handleChat);
        server.createContext("/api/download", this::handleDownload);

        server.createContext("/", new StaticFileHandler());

        server.start();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("http", true);
        sendJson(exchange, 200, response);
    }

    private void handleDocumentos(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        try {
            List<Documento> documentos = documentoService.listarDocumentos();
            List<Map<String, Object>> data = new ArrayList<>();
            for (Documento d : documentos) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", d.getId());
                row.put("nombre", d.getNombre());
                row.put("extension", d.getExtension());
                row.put("tamano", d.getTamano());
                row.put("tipo", d.getTipo() != null ? d.getTipo().name() : null);
                row.put("hash", d.getHashSha256());
                row.put("ip", d.getIpPropietario());
                row.put("fecha", d.getFechaCreacion() != null ? d.getFechaCreacion().toString() : null);
                data.add(row);
            }
            sendJson(exchange, 200, data);
        } catch (Exception e) {
            sendError(exchange, 500, "No se pudieron listar documentos: " + e.getMessage());
        }
    }

    private void handleClientes(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        try {
            List<ClienteConectado> clientes = clienteConectadoDAO.listarTodos();
            List<Map<String, Object>> data = new ArrayList<>();
            for (ClienteConectado c : clientes) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("ip", c.getIp());
                row.put("puerto", c.getPuerto());
                row.put("protocolo", c.getProtocolo());
                row.put("fechaInicio", c.getFechaInicio() != null ? c.getFechaInicio().toString() : null);
                data.add(row);
            }
            sendJson(exchange, 200, data);
        } catch (SQLException e) {
            sendError(exchange, 500, "No se pudieron listar clientes: " + e.getMessage());
        }
    }

    private void handleLogs(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        try {
            int limit = parseLimit(exchange.getRequestURI().getQuery());
            List<Log> logs = logDAO.listarUltimos(limit);
            List<Map<String, Object>> data = new ArrayList<>();
            for (Log l : logs) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", l.getId());
                row.put("accion", l.getAccion());
                row.put("ipOrigen", l.getIpOrigen());
                row.put("fechaHora", l.getFechaHora() != null ? l.getFechaHora().toString() : null);
                row.put("detalles", l.getDetalles());
                data.add(row);
            }
            sendJson(exchange, 200, data);
        } catch (SQLException e) {
            sendError(exchange, 500, "No se pudieron listar logs: " + e.getMessage());
        }
    }

    private void handleUpload(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendMethodNotAllowed(exchange, "POST");
            return;
        }

        validarSesionHttp(exchange);

        String fileName = getFileName(exchange);
        if (fileName == null || fileName.isBlank()) {
            sendError(exchange, 400, "Debes enviar filename en query (?filename=) o header X-File-Name");
            return;
        }

        String safeFileName = sanitizeFileName(fileName);
        long declaredSize = parseContentLength(exchange.getRequestHeaders().getFirst("Content-Length"));

        try (InputStream body = exchange.getRequestBody()) {
            InputStream uploadStream = body;
            long size = declaredSize;

            if (size <= 0) {
                byte[] bytes = body.readAllBytes();
                uploadStream = new ByteArrayInputStream(bytes);
                size = bytes.length;
            }

            String ip = exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null
                    ? exchange.getRemoteAddress().getAddress().getHostAddress()
                    : "http-client";

            Documento doc = documentoService.procesarArchivo(safeFileName, size, ip, uploadStream);
            logService.registrar("HTTP_UPLOAD", ip, "Archivo: " + safeFileName + " (" + size + " bytes)");

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "OK");
            response.put("documentoId", doc.getId());
            response.put("nombre", doc.getNombre());
            response.put("hash", doc.getHashSha256());
            response.put("tamano", doc.getTamano());

            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendError(exchange, 500, "No se pudo procesar la subida: " + e.getMessage());
        }
    }

    private void handleConnect(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendMethodNotAllowed(exchange, "POST");
            return;
        }

        try {
            String ip = getClientIp(exchange);
            int puerto = parsePort(exchange);
            String protocolo = "HTTP";
            ClienteConectado cliente = new ClienteConectado(ip, puerto, protocolo);
            clienteConectadoDAO.registrar(cliente);
            String sessionId = UUID.randomUUID().toString();
            sesionesHttp.put(sessionId, cliente);
            logService.logConexion(ip, protocolo);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "OK");
            response.put("sessionId", sessionId);
            response.put("ip", ip);
            response.put("puerto", puerto);
            response.put("protocolo", protocolo);
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            sendError(exchange, 500, "No se pudo conectar cliente HTTP: " + e.getMessage());
        }
    }

    private void handleDisconnect(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendMethodNotAllowed(exchange, "POST");
            return;
        }

        String sessionId = getSessionId(exchange);
        if (sessionId == null || sessionId.isBlank()) {
            sendError(exchange, 400, "Debes enviar sessionId");
            return;
        }

        ClienteConectado cliente = sesionesHttp.remove(sessionId);
        if (cliente == null) {
            sendError(exchange, 404, "Sesion HTTP no encontrada");
            return;
        }

        try {
            clienteConectadoDAO.eliminar(cliente.getIp(), cliente.getPuerto());
            logService.logDesconexion(cliente.getIp());
            sendJson(exchange, 200, Mensaje.respuestaOk("mensaje", "Cliente HTTP desconectado").put("sessionId", sessionId));
        } catch (SQLException e) {
            sendError(exchange, 500, "No se pudo desconectar cliente HTTP: " + e.getMessage());
        }
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        if (isMethod(exchange, "GET")) {
            sendJson(exchange, 200, chatHistory);
            return;
        }
        if (!isMethod(exchange, "POST")) {
            sendMethodNotAllowed(exchange, "GET, POST");
            return;
        }

        ClienteConectado cliente = validarSesionHttp(exchange);
        if (cliente == null) {
            return;
        }

        try {
            Map<String, Object> payload = readJsonBody(exchange);
            String texto = payload.get("texto") != null ? payload.get("texto").toString().trim() : "";
            if (texto.isBlank()) {
                sendError(exchange, 400, "El campo 'texto' es obligatorio");
                return;
            }

            Documento doc = documentoService.procesarMensaje(texto, cliente.getIp());
            logService.logMensajeRecibido(cliente.getIp());

            Map<String, Object> chatMessage = new LinkedHashMap<>();
            chatMessage.put("remitente", cliente.getIp() + ":" + cliente.getPuerto());
            chatMessage.put("texto", texto);
            chatMessage.put("hash", doc.getHashSha256());
            chatMessage.put("documentoId", doc.getId());
            chatMessage.put("timestamp", java.time.LocalDateTime.now().toString());
            chatHistory.add(chatMessage);
            trimHistory();

            sendJson(exchange, 200, Mensaje.respuestaOk("hash", doc.getHashSha256())
                    .put("documentoId", doc.getId())
                    .put("mensaje", "Mensaje enviado")
                    .put("chat", chatMessage));
        } catch (Exception e) {
            sendError(exchange, 500, "No se pudo procesar el chat: " + e.getMessage());
        }
    }

    private void handleDownload(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        ClienteConectado cliente = validarSesionHttp(exchange);
        if (cliente == null) {
            return;
        }

        Map<String, String> params = getQueryParams(exchange.getRequestURI().getQuery());
        long documentoId;
        try {
            documentoId = Long.parseLong(params.getOrDefault("documentoId", ""));
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Parametro 'documentoId' invalido");
            return;
        }

        String tipo = params.getOrDefault("tipo", "ORIGINAL").toUpperCase();
        try {
            Documento doc = documentoService.obtenerDocumento(documentoId);
            if (doc == null) {
                sendError(exchange, 404, "Documento no encontrado: " + documentoId);
                return;
            }

            if ("HASH".equals(tipo)) {
                String hash = documentoService.getHash(documentoId);
                sendJson(exchange, 200, Mensaje.respuestaOk("hash", hash).put("documentoId", documentoId));
                logService.logDescarga(cliente.getIp(), doc.getNombre(), "HASH");
                return;
            }

            try (InputStream in = "ENCRIPTADO".equals(tipo)
                    ? documentoService.getArchivoEncriptadoStream(documentoId)
                    : documentoService.getArchivoOriginalStream(documentoId)) {
                if (in == null) {
                    sendError(exchange, 404, "No hay datos para el documento solicitado");
                    return;
                }

                String fileName = buildDownloadFileName(doc, tipo);
                byte[] bytes = in.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                logService.logDescarga(cliente.getIp(), doc.getNombre(), tipo);
            }
        } catch (Exception e) {
            sendError(exchange, 500, "No se pudo descargar documento: " + e.getMessage());
        }
    }

    private String getClientIp(HttpExchange exchange) {
        if (exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
            return exchange.getRemoteAddress().getAddress().getHostAddress();
        }
        String xForwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xForwarded != null && !xForwarded.isEmpty()) {
            return xForwarded.split(",")[0].trim();
        }
        return "http-client";
    }

    private ClienteConectado validarSesionHttp(HttpExchange exchange) throws IOException {
        String sessionId = getSessionId(exchange);
        if (sessionId == null || sessionId.isBlank()) {
            sendError(exchange, 401, "Sesion HTTP requerida. Usa /api/connect");
            return null;
        }
        ClienteConectado cliente = sesionesHttp.get(sessionId);
        if (cliente == null) {
            sendError(exchange, 401, "Sesion HTTP invalida o expirada");
            return null;
        }
        return cliente;
    }

    private String getSessionId(HttpExchange exchange) {
        String fromHeader = exchange.getRequestHeaders().getFirst("X-Session-Id");
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader.trim();
        }
        return getQueryParams(exchange.getRequestURI().getQuery()).get("sessionId");
    }

    private int parsePort(HttpExchange exchange) {
        String raw = getQueryParams(exchange.getRequestURI().getQuery()).get("port");
        if (raw == null || raw.isBlank()) return 8080;
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : 8080;
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    private static boolean isMethod(HttpExchange exchange, String expected) {
        return expected.equalsIgnoreCase(exchange.getRequestMethod());
    }

    private void sendMethodNotAllowed(HttpExchange exchange, String allowedMethod) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowedMethod);
        sendError(exchange, 405, "Metodo no permitido");
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "ERROR");
        body.put("detalle", message);
        sendJson(exchange, code, body);
    }

    private void sendJson(HttpExchange exchange, int code, Object payload) throws IOException {
        byte[] data = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private String getFileName(HttpExchange exchange) {
        String fromQuery = getQueryParams(exchange.getRequestURI().getQuery()).get("filename");
        if (fromQuery != null && !fromQuery.isBlank()) {
            return fromQuery;
        }

        String fromHeader = exchange.getRequestHeaders().getFirst("X-File-Name");
        if (fromHeader != null && !fromHeader.isBlank()) {
            return urlDecode(fromHeader);
        }

        return null;
    }

    private int parseLimit(String query) {
        Map<String, String> params = getQueryParams(query);
        String raw = params.getOrDefault("limit", "50");
        try {
            int value = Integer.parseInt(raw);
            if (value < 1) return 1;
            return Math.min(value, 500);
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    private long parseContentLength(String value) {
        if (value == null || value.isBlank()) return -1L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private String sanitizeFileName(String input) {
        return Paths.get(input).getFileName().toString();
    }

    private String buildDownloadFileName(Documento doc, String tipo) {
        String base = sanitizeFileName(doc.getNombre());
        if ("ENCRIPTADO".equals(tipo)) {
            return base + ".enc";
        }
        return base;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
        byte[] data = exchange.getRequestBody().readAllBytes();
        if (data.length == 0) {
            return new HashMap<>();
        }
        Map<String, Object> parsed = gson.fromJson(new String(data, StandardCharsets.UTF_8), Map.class);
        return parsed != null ? parsed : new HashMap<>();
    }

    private void trimHistory() {
        synchronized (chatHistory) {
            int overflow = chatHistory.size() - 300;
            if (overflow > 0) {
                chatHistory.subList(0, overflow).clear();
            }
        }
    }

    private Map<String, String> getQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) {
            return map;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx <= 0) continue;
            String key = urlDecode(pair.substring(0, idx));
            String value = urlDecode(pair.substring(idx + 1));
            map.put(key, value);
        }
        return map;
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isMethod(exchange, "GET")) {
                sendMethodNotAllowed(exchange, "GET");
                return;
            }

            String requestPath = exchange.getRequestURI().getPath();
            String resourcePath;
            if (requestPath == null || requestPath.equals("/")) {
                resourcePath = "/web/index.html";
            } else {
                resourcePath = "/web" + requestPath;
            }

        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                byte[] data = in.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", contentType(resourcePath));
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
            }
        }

        private String contentType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=utf-8";
            if (path.endsWith(".css")) return "text/css; charset=utf-8";
            if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (path.endsWith(".json")) return "application/json; charset=utf-8";
            return "application/octet-stream";
        }
    }
}
