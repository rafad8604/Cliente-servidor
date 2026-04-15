package com.arquitectura.servidor.business.infrastructure;

import com.arquitectura.servidor.business.document.AvailableDocumentInfo;
import com.arquitectura.servidor.business.document.DocumentDeliveryMode;
import com.arquitectura.servidor.business.document.DocumentExchangeLogService;
import com.arquitectura.servidor.business.document.DocumentPayload;
import com.arquitectura.servidor.business.document.DocumentRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClientDocumentQueryService {

    private final DocumentRecordRepository documentRepository;
    private final DocumentExchangeLogService exchangeLogService;
    private final ObjectMapper objectMapper;

    public ClientDocumentQueryService(DocumentRecordRepository documentRepository,
                                      DocumentExchangeLogService exchangeLogService,
                                      ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.exchangeLogService = exchangeLogService;
        this.objectMapper = objectMapper;
    }

    public String getAvailableDocumentsJson() {
        List<Map<String, Object>> documents = documentRepository.findAllAvailable().stream()
            .map(this::toMap)
            .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", "DOCUMENTS_LIST");
        payload.put("documents", documents);
        return toJson(payload);
    }

    public String getLogsJson() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", "LOG_REPORT");
        payload.put("entries", exchangeLogService.getRecentLogs());
        return toJson(payload);
    }

    public DocumentPayload loadDocument(String documentId, DocumentDeliveryMode mode, String targetClientIp) {
        DocumentPayload payload = documentRepository.fetchPayload(documentId, mode);
        exchangeLogService.logDelivered(payload, mode, targetClientIp);
        return payload;
    }

    private Map<String, Object> toMap(AvailableDocumentInfo info) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentId", info.documentId());
        payload.put("name", info.name());
        payload.put("sizeBytes", info.sizeBytes());
        payload.put("extension", info.extension());
        payload.put("owner", info.ownerScope().name());
        payload.put("ownerIp", info.ownerIp());
        return payload;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la respuesta JSON", e);
        }
    }
}

