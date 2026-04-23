package com.app.server.http;

import com.app.server.dao.ClienteConectadoDAO;
import com.app.server.dao.LogDAO;
import com.app.server.models.ClienteConectado;
import com.app.server.models.Documento;
import com.app.server.models.Log;
import com.app.server.service.DocumentoService;
import com.app.server.service.LogService;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        registrarClienteHttp(exchange);

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

        registrarClienteHttp(exchange);

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

        registrarClienteHttp(exchange);

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

        registrarClienteHttp(exchange);

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

        registrarClienteHttp(exchange);

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

    private void registrarClienteHttp(HttpExchange exchange) {
        try {
            String ip = getClientIp(exchange);
            int puerto = 8080;
            String protocolo = "HTTP";
            ClienteConectado cliente = new ClienteConectado(ip, puerto, protocolo);
            clienteConectadoDAO.registrar(cliente);
        } catch (Exception e) {
            System.err.println("[HTTP] Error registrando cliente: " + e.getMessage());
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

            registrarClienteHttp(exchange);

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
