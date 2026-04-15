package com.arquitectura.servidor.business.infrastructure;

import com.arquitectura.servidor.business.user.ConnectedClientInfo;
import com.arquitectura.servidor.business.user.UserConnectionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClientConnectionQueryService {

    private final UserConnectionService userConnectionService;
    private final ObjectMapper objectMapper;

    public ClientConnectionQueryService(UserConnectionService userConnectionService, ObjectMapper objectMapper) {
        this.userConnectionService = userConnectionService;
        this.objectMapper = objectMapper;
    }

    public int getConnectedClientsCount() {
        return userConnectionService.getConnectedClientsCount();
    }

    public List<ConnectedClientInfo> getConnectedClientsList() {
        return userConnectionService.getConnectedClientsInfo();
    }

    public String getConnectedClientsCountJson() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", "CONNECTED_CLIENTS_COUNT");
        payload.put("connectedClients", getConnectedClientsCount());
        return toJson(payload);
    }

    public String getConnectedClientsListJson() {
        List<Map<String, String>> clients = getConnectedClientsList().stream()
            .map(client -> {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("ipAddress", client.ipAddress());
                item.put("startDate", client.startDate().toString());
                item.put("startTime", client.startTime().toString());
                return item;
            })
            .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", "CONNECTED_CLIENTS_LIST");
        payload.put("clients", clients);
        return toJson(payload);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la respuesta JSON", e);
        }
    }
}

