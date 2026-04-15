package com.arquitectura.servidor.business.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentExchangeLogService {

    private static final int MAX_LOG_LINES = 200;

    private final Path logFilePath;
    private final ObjectMapper objectMapper;

    public DocumentExchangeLogService(DocumentProcessingConfig config, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.logFilePath = config.getOriginalDirectoryPath().resolveSibling("logs").resolve("document-exchange.log");
        ensureLogDirectory();
    }

    public synchronized void logReceived(DocumentRecord record) {
        Map<String, Object> payload = basePayload("RECEIVED", record);
        append(payload);
    }

    public synchronized void logDelivered(DocumentPayload payload, DocumentDeliveryMode mode, String targetClientIp) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", Instant.now().toString());
        event.put("event", "DELIVERED");
        event.put("documentId", payload.documentId());
        event.put("fileName", payload.fileName());
        event.put("mode", mode.name());
        event.put("targetClientIp", targetClientIp);
        event.put("sizeBytes", payload.payloadSizeBytes());
        append(event);
    }

    public synchronized List<String> getRecentLogs() {
        try {
            if (!Files.exists(logFilePath)) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(logFilePath, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - MAX_LOG_LINES);
            return lines.subList(from, lines.size());
        } catch (IOException e) {
            throw new DocumentStorageException("No se pudieron leer logs de intercambio", e);
        }
    }

    private Map<String, Object> basePayload(String event, DocumentRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("event", event);
        payload.put("documentId", record.documentId());
        payload.put("senderId", record.senderId());
        payload.put("recipientId", record.recipientId());
        payload.put("ownerScope", record.ownerScope().name());
        payload.put("ownerIp", record.ownerIp());
        payload.put("documentType", record.documentType().name());
        payload.put("fileName", record.originalFileName());
        payload.put("sizeBytes", record.originalSizeBytes());
        payload.put("sha256", record.sha256Hash());
        return payload;
    }

    private void append(Map<String, Object> payload) {
        try {
            String line = objectMapper.writeValueAsString(payload) + System.lineSeparator();
            Files.writeString(logFilePath, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el log JSON", e);
        } catch (IOException e) {
            throw new DocumentStorageException("No se pudo persistir log de intercambio", e);
        }
    }

    private void ensureLogDirectory() {
        try {
            Files.createDirectories(logFilePath.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear directorio de logs", e);
        }
    }
}

