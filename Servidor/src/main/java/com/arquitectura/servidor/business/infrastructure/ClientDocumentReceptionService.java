package com.arquitectura.servidor.business.infrastructure;

import com.arquitectura.servidor.business.document.DocumentReceptionService;
import com.arquitectura.servidor.business.document.StoredDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ClientDocumentReceptionService {

    private final DocumentReceptionService documentReceptionService;
    private final ObjectMapper objectMapper;

    public ClientDocumentReceptionService(DocumentReceptionService documentReceptionService, ObjectMapper objectMapper) {
        this.documentReceptionService = documentReceptionService;
        this.objectMapper = objectMapper;
    }

    public String receiveDocumentJsonResponse(String metadataJson, InputStream payload) {
        StoredDocument document = documentReceptionService.receiveFromJson(metadataJson, payload);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "DOCUMENT_RECEIVED");
        response.put("documentId", document.documentId());
        response.put("type", document.documentType().name());
        response.put("fileName", document.originalFileName());
        response.put("filePath", document.originalFilePath());
        response.put("hash", document.sha256Hash());
        response.put("sizeBytes", document.originalSizeBytes());
        response.put("storedAt", document.storedAt().toString());
        return toJson(response);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la respuesta JSON", e);
        }
    }
}

