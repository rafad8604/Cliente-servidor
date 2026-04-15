package com.arquitectura.cliente.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Servicio de consultas al servidor (listar clientes, documentos, logs).
 */
@Service
public class QueryService {

    private final ConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    public QueryService(ConnectionManager connectionManager, ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
    }

    public int getConnectedClientsCount() throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("service", "CONNECTED_CLIENTS_COUNT");

        String response = connectionManager.sendRequest(objectMapper.writeValueAsString(request));
        JsonNode json = objectMapper.readTree(response);
        return json.path("connectedClients").asInt(0);
    }

    public List<Map<String, String>> getConnectedClientsList() throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("service", "CONNECTED_CLIENTS_LIST");

        String response = connectionManager.sendRequest(objectMapper.writeValueAsString(request));
        JsonNode json = objectMapper.readTree(response);
        List<Map<String, String>> clients = new ArrayList<>();

        for (JsonNode node : json.path("clients")) {
            Map<String, String> client = new LinkedHashMap<>();
            client.put("ipAddress", node.path("ipAddress").asText());
            client.put("startDate", node.path("startDate").asText());
            client.put("startTime", node.path("startTime").asText());
            clients.add(client);
        }

        return clients;
    }

    public List<Map<String, Object>> getAvailableDocuments() throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("service", "DOCUMENTS_LIST");

        String response = connectionManager.sendRequest(objectMapper.writeValueAsString(request));
        JsonNode json = objectMapper.readTree(response);
        List<Map<String, Object>> documents = new ArrayList<>();

        for (JsonNode node : json.path("documents")) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("documentId", node.path("documentId").asText());
            doc.put("name", node.path("name").asText());
            doc.put("sizeBytes", node.path("sizeBytes").asLong());
            doc.put("extension", node.path("extension").asText());
            doc.put("owner", node.path("owner").asText());
            doc.put("ownerIp", node.path("ownerIp").asText());
            documents.add(doc);
        }

        return documents;
    }

    public List<Map<String, Object>> getLogs() throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("service", "LOG_REPORT");

        String response = connectionManager.sendRequest(objectMapper.writeValueAsString(request));
        JsonNode json = objectMapper.readTree(response);
        List<Map<String, Object>> logs = new ArrayList<>();

        for (JsonNode node : json.path("entries")) {
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("timestamp", node.path("timestamp").asText());
            log.put("senderId", node.path("senderId").asText());
            log.put("documentType", node.path("documentType").asText());
            log.put("fileName", node.path("fileName").asText());
            logs.add(log);
        }

        return logs;
    }
}

