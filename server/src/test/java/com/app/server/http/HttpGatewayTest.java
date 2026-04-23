package com.app.server.http;

import com.app.server.service.DocumentoService;
import com.app.server.service.LogService;
import com.app.shared.util.CryptoUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración para HttpGateway.
 * 
 * NOTA: Deshabilitados por defecto ya que requieren levantar servidor HTTP real.
 * Para habilitarlos: elimina @Disabled de la clase o ejecuta solo estos tests.
 * Runtimes esperado: ~5-10 segundos por test.
 * 
 * Ejecutar solo estos tests:
 *   mvn test -Dtest=HttpGatewayTest
 */
@Disabled("Requiere servidor HTTP real - ejecutar manualmente para integración")
class HttpGatewayTest {

    private HttpGateway gateway;
    private DocumentoService documentoService;
    private LogService logService;
    private int testPort = 8888;

    @BeforeEach
    void setup() throws Exception {
        documentoService = new DocumentoService(CryptoUtil.generateAESKey());
        logService = new LogService();
        gateway = new HttpGateway(testPort, documentoService, logService);
        gateway.start();
    }

    @AfterEach
    void cleanup() {
        gateway.stop();
    }

    @Test
    void healthEndpointResponde() throws IOException {
        URL url = new URL("http://localhost:" + testPort + "/api/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        assertEquals(200, conn.getResponseCode());
        
        String response = readResponse(conn);
        assertNotNull(response);
        assertTrue(response.contains("status"));
        
        conn.disconnect();
    }

    @Test
    void healthEndpointRetornaJson() throws IOException {
        URL url = new URL("http://localhost:" + testPort + "/api/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        String response = readResponse(conn);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        
        assertTrue(json.has("status"));
        assertTrue(json.has("timestamp"));
        
        conn.disconnect();
    }

    @Test
    void documentosEndpointResponde() throws IOException {
        URL url = new URL("http://localhost:" + testPort + "/api/documentos");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        assertEquals(200, conn.getResponseCode());
        
        String response = readResponse(conn);
        assertNotNull(response);
        // Debería ser un array JSON (puede estar vacío)
        assertTrue(response.startsWith("[") || response.contains("documentos"));
        
        conn.disconnect();
    }

    @Test
    void clientesEndpointResponde() throws IOException {
        URL url = new URL("http://localhost:" + testPort + "/api/clientes");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        assertEquals(200, conn.getResponseCode());
        
        String response = readResponse(conn);
        assertNotNull(response);
        
        conn.disconnect();
    }

    @Test
    void logsEndpointResponde() throws IOException {
        URL url = new URL("http://localhost:" + testPort + "/api/logs");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        assertEquals(200, conn.getResponseCode());
        
        String response = readResponse(conn);
        assertNotNull(response);
        
        conn.disconnect();
    }

    @Test
    void logsEndpointConLimitQuery() throws IOException {
        URL url = new URL("http://localhost:" + testPort + "/api/logs?limit=5");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        assertEquals(200, conn.getResponseCode());
        
        String response = readResponse(conn);
        assertNotNull(response);
        
        conn.disconnect();
    }

    @Test
    void staticFileHandlerResponde() throws IOException {
        URL url = new URL("http://localhost:" + testPort + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        // Puede retornar 200 o 404 dependiendo si existe index.html
        int code = conn.getResponseCode();
        assertTrue(code == 200 || code == 404);
        
        conn.disconnect();
    }

    @Test
    void uploadEndpointRechazaSinContenido() throws IOException {
        URL url = new URL("http://localhost:" + testPort + "/api/upload");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(false);
        
        int code = conn.getResponseCode();
        // Debería retornar 400 o similar
        assertTrue(code >= 400);
        
        conn.disconnect();
    }

    @Test
    void contentTypeHeaders() throws IOException {
        URL url = new URL("http://localhost:" + testPort + "/api/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        String contentType = conn.getContentType();
        assertNotNull(contentType);
        assertTrue(contentType.contains("application/json"));
        
        conn.disconnect();
    }

    @Test
    void gatewayPuedePararYReiniciar() throws IOException {
        gateway.stop();
        
        // Esperar a que se libere el puerto
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertDoesNotThrow(() -> {
            gateway.start();
        });
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        StringBuilder response = new StringBuilder();
        try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
            while (scanner.hasNextLine()) {
                response.append(scanner.nextLine());
            }
        }
        return response.toString();
    }
}
