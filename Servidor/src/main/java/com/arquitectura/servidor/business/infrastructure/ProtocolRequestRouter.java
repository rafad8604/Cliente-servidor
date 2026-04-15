package com.arquitectura.servidor.business.infrastructure;

import com.arquitectura.servidor.business.document.DocumentDeliveryMode;
import com.arquitectura.servidor.business.document.DocumentPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ProtocolRequestRouter {

    private final ClientConnectionQueryService connectionQueryService;
    private final ClientDocumentReceptionService documentReceptionService;
    private final ClientDocumentQueryService documentQueryService;
    private final ObjectMapper objectMapper;

    public ProtocolRequestRouter(ClientConnectionQueryService connectionQueryService,
                                 ClientDocumentReceptionService documentReceptionService,
                                 ClientDocumentQueryService documentQueryService,
                                 ObjectMapper objectMapper) {
        this.connectionQueryService = connectionQueryService;
        this.documentReceptionService = documentReceptionService;
        this.documentQueryService = documentQueryService;
        this.objectMapper = objectMapper;
    }

    public ProtocolResponse route(String requestJson, InputStream payload, String clientIp) {
        JsonNode request = parse(requestJson);
        String service = requiredText(request, "service");

        return switch (service) {
            case "CONNECTED_CLIENTS_COUNT" -> ProtocolResponse.jsonOnly(connectionQueryService.getConnectedClientsCountJson());
            case "CONNECTED_CLIENTS_LIST" -> ProtocolResponse.jsonOnly(connectionQueryService.getConnectedClientsListJson());
            case "DOCUMENTS_LIST" -> ProtocolResponse.jsonOnly(documentQueryService.getAvailableDocumentsJson());
            case "LOG_REPORT" -> ProtocolResponse.jsonOnly(documentQueryService.getLogsJson());
            case "RECEIVE_DOCUMENT" -> ProtocolResponse.jsonOnly(documentReceptionService.receiveDocumentJsonResponse(requestJson, payload));
            case "SEND_DOCUMENT" -> buildSendDocumentResponse(request, clientIp);
            default -> ProtocolResponse.jsonOnly(error("Servicio no soportado: " + service));
        };
    }

    private ProtocolResponse buildSendDocumentResponse(JsonNode request, String clientIp) {
        String documentId = requiredText(request, "documentId");
        String modeRaw = requiredText(request, "mode");
        DocumentDeliveryMode mode = DocumentDeliveryMode.valueOf(modeRaw.toUpperCase(Locale.ROOT));

        DocumentPayload payload = documentQueryService.loadDocument(documentId, mode, clientIp);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "SEND_DOCUMENT");
        response.put("documentId", payload.documentId());
        response.put("fileName", payload.fileName());
        response.put("payloadLength", payload.payloadSizeBytes());
        response.put("sha256", payload.sha256Hash());
        response.put("encryption", payload.encryptionAlgorithm());
        response.put("mode", mode.name());
        return new ProtocolResponse(toJson(response), payload.payloadStream(), payload.payloadSizeBytes());
    }

    private JsonNode parse(String requestJson) {
        try {
            return objectMapper.readTree(requestJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Solicitud JSON invalida", e);
        }
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.asText().isBlank()) {
            throw new IllegalArgumentException("Campo obligatorio ausente: " + field);
        }
        return value.asText();
    }

    private String error(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", "ERROR");
        payload.put("message", message);
        return toJson(payload);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar respuesta de protocolo", e);
        }
    }
}

