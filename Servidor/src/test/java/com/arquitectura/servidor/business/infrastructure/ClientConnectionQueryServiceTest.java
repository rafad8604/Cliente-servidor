package com.arquitectura.servidor.business.infrastructure;

import com.arquitectura.servidor.business.activity.ServerActivitySource;
import com.arquitectura.servidor.business.user.UserConnectionConfig;
import com.arquitectura.servidor.business.user.UserConnectionLimitExceededException;
import com.arquitectura.servidor.business.user.UserConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientConnectionQueryServiceTest {

    private UserConnectionService userConnectionService;
    private ClientConnectionQueryService queryService;

    @BeforeEach
    void setUp() {
        UserConnectionConfig config = new UserConnectionConfig();
        config.setMaxUsers(3);
        userConnectionService = new UserConnectionService(config, new ServerActivitySource());
        queryService = new ClientConnectionQueryService(userConnectionService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        userConnectionService.shutdownAllConnections();
    }

    @Test
    void shouldReturnConnectedClientsCount() throws UserConnectionLimitExceededException {
        userConnectionService.connect("User1", "10.1.1.1");
        userConnectionService.connect("User2", "10.1.1.2");

        assertEquals(2, queryService.getConnectedClientsCount());
    }

    @Test
    void shouldReturnConnectedClientsList() throws UserConnectionLimitExceededException {
        userConnectionService.connect("User1", "10.1.1.1");

        var list = queryService.getConnectedClientsList();

        assertEquals(1, list.size());
        assertEquals("10.1.1.1", list.getFirst().ipAddress());
    }

    @Test
    void shouldReturnConnectedClientsCountAsJson() throws UserConnectionLimitExceededException {
        userConnectionService.connect("User1", "10.1.1.1");

        String json = queryService.getConnectedClientsCountJson();

        assertEquals(true, json.contains("\"service\":\"CONNECTED_CLIENTS_COUNT\""));
        assertEquals(true, json.contains("\"connectedClients\":1"));
    }

    @Test
    void shouldReturnConnectedClientsListAsJson() throws UserConnectionLimitExceededException {
        userConnectionService.connect("User1", "10.1.1.1");

        String json = queryService.getConnectedClientsListJson();

        assertEquals(true, json.contains("\"service\":\"CONNECTED_CLIENTS_LIST\""));
        assertEquals(true, json.contains("\"ipAddress\":\"10.1.1.1\""));
        assertEquals(true, json.contains("\"startDate\":"));
        assertEquals(true, json.contains("\"startTime\":"));
    }
}

