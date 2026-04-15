package com.arquitectura.cliente.application;

import com.arquitectura.cliente.persistence.SentTransferEntity;
import com.arquitectura.cliente.persistence.SentTransferRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servicio de transferencias: enviar/recibir mensajes y archivos.
 */
@Service
public class TransferService {

    private final ConnectionManager connectionManager;
    private final SentTransferRepository transferRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService uploadExecutor;

    public TransferService(ConnectionManager connectionManager,
                          SentTransferRepository transferRepository,
                          ObjectMapper objectMapper) {
        this.connectionManager = connectionManager;
        this.transferRepository = transferRepository;
        this.objectMapper = objectMapper;
        this.uploadExecutor = Executors.newFixedThreadPool(4);
    }

    public String sendMessage(String senderId, String recipientId, String message) throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("service", "RECEIVE_DOCUMENT");
        request.put("senderId", senderId);
        request.put("recipientId", recipientId);
        request.put("senderIp", "127.0.0.1");
        request.put("type", "MESSAGE");
        request.put("message", message);

        String response = connectionManager.sendRequest(objectMapper.writeValueAsString(request));
        
        // Registrar en BD
        SentTransferEntity transfer = new SentTransferEntity();
        transfer.setSentAt(Instant.now());
        transfer.setServerHost(connectionManager.getSettings().host());
        transfer.setServerPort(connectionManager.getSettings().port());
        transfer.setProtocol(connectionManager.getSettings().protocol().name());
        transfer.setRecipientId(recipientId);
        transfer.setType("MESSAGE");
        transfer.setMessagePreview(message.substring(0, Math.min(100, message.length())));
        transfer.setStatus("SENT");
        transfer.setServerDocumentId(extractDocumentId(response));
        transferRepository.save(transfer);

        return response;
    }

    public String sendFile(String senderId, String recipientId, String fileName, byte[] fileContent) throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("service", "RECEIVE_DOCUMENT");
        request.put("senderId", senderId);
        request.put("recipientId", recipientId);
        request.put("senderIp", "127.0.0.1");
        request.put("type", "FILE");
        request.put("fileName", fileName);
        request.put("payloadLength", fileContent.length);

        String response = connectionManager.sendRequestWithPayload(
            objectMapper.writeValueAsString(request),
            fileContent
        );

        // Registrar en BD
        SentTransferEntity transfer = new SentTransferEntity();
        transfer.setSentAt(Instant.now());
        transfer.setServerHost(connectionManager.getSettings().host());
        transfer.setServerPort(connectionManager.getSettings().port());
        transfer.setProtocol(connectionManager.getSettings().protocol().name());
        transfer.setRecipientId(recipientId);
        transfer.setType("FILE");
        transfer.setFileName(fileName);
        transfer.setSizeBytes((long) fileContent.length);
        transfer.setStatus("SENT");
        transfer.setServerDocumentId(extractDocumentId(response));
        transferRepository.save(transfer);

        return response;
    }

    public CompletableFuture<String> sendFileAsync(String senderId, String recipientId, String fileName, byte[] fileContent) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendFile(senderId, recipientId, fileName, fileContent);
            } catch (IOException e) {
                throw new RuntimeException("Error enviando archivo: " + e.getMessage(), e);
            }
        }, uploadExecutor);
    }

    public void downloadDocument(String documentId, String mode, String downloadPath) throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("service", "SEND_DOCUMENT");
        request.put("documentId", documentId);
        request.put("mode", mode); // ORIGINAL, ORIGINAL_WITH_HASH, ENCRYPTED

        String response = connectionManager.sendRequest(objectMapper.writeValueAsString(request));
        JsonNode json = objectMapper.readTree(response);

        long payloadLength = json.path("payloadLength").asLong(0);
        if (payloadLength > 0) {
            byte[] payload = connectionManager.receivePayload(payloadLength);
            // Aquí se guardaría el archivo en downloadPath
            System.out.println("Descargado " + payloadLength + " bytes para " + documentId);
        }
    }

    private String extractDocumentId(String response) {
        try {
            JsonNode json = objectMapper.readTree(response);
            return json.path("documentId").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    public void shutdown() {
        uploadExecutor.shutdown();
    }
}

