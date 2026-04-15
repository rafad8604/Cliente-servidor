package com.arquitectura.servidor.business.user;

import com.arquitectura.servidor.business.activity.ServerActivitySource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

class UserConnectionServiceTest {

    private UserConnectionService service;
    private UserConnectionConfig config;
    private ServerActivitySource activitySource;

    @BeforeEach
    void setUp() {
        config = new UserConnectionConfig();
        config.setMaxUsers(3);
        activitySource = new ServerActivitySource();
        service = new UserConnectionService(config, activitySource);
    }

    @AfterEach
    void tearDown() {
        service.shutdownAllConnections();
    }

    @Test
    void shouldAllowConnectionWhenBelowLimit() throws UserConnectionLimitExceededException {
        UserConnection conn = service.connect("User1");
        assertNotNull(conn);
        assertEquals("User1", conn.username());
        assertEquals("127.0.0.1", conn.ipAddress());
        assertEquals(1, service.getCurrentConnectionCount());
    }

    @Test
    void shouldAllowMultipleConnectionsUntilLimit() throws UserConnectionLimitExceededException {
        service.connect("User1");
        service.connect("User2");
        service.connect("User3");

        assertEquals(3, service.getCurrentConnectionCount());
    }

    @Test
    void shouldRejectConnectionWhenLimitExceeded() throws UserConnectionLimitExceededException {
        service.connect("User1");
        service.connect("User2");
        service.connect("User3");

        assertThrows(UserConnectionLimitExceededException.class, () -> service.connect("User4"));
        assertEquals(3, service.getCurrentConnectionCount());
    }

    @Test
    void shouldDisconnectUserAndAllowNewConnection() throws UserConnectionLimitExceededException {
        UserConnection conn1 = service.connect("User1");
        service.connect("User2");
        service.connect("User3");

        service.disconnect(conn1.userId());
        assertEquals(2, service.getCurrentConnectionCount());

        UserConnection conn4 = service.connect("User4");
        assertNotNull(conn4);
        assertEquals(3, service.getCurrentConnectionCount());
    }

    @Test
    void shouldReturnCopyOfActiveConnections() throws UserConnectionLimitExceededException {
        service.connect("User1");
        service.connect("User2");

        var connections = service.getActiveConnections();
        assertEquals(2, connections.size());
        assertThrows(UnsupportedOperationException.class, connections::clear);
    }

    @Test
    void shouldReuseWorkersAfterDisconnect() throws UserConnectionLimitExceededException {
        UserConnection conn1 = service.connect("User1");
        assertEquals(1, service.getInUseWorkerCount());

        service.disconnect(conn1.userId());
        assertEquals(0, service.getInUseWorkerCount());
        assertEquals(1, service.getAvailableWorkerCount());

        service.connect("User2");
        assertEquals(1, service.getInUseWorkerCount());
    }

    @Test
    void shouldRunEachConnectionOnItsOwnThread() throws UserConnectionLimitExceededException {
        UserConnection conn1 = service.connect("User1");
        UserConnection conn2 = service.connect("User2");

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertTrue(service.isConnectionThreadRunning(conn1.userId()));
        assertTrue(service.isConnectionThreadRunning(conn2.userId()));
    }

    @Test
    void shouldThrowExceptionOnInvalidMaxUsers() {
        assertThrows(IllegalArgumentException.class, () -> config.setMaxUsers(0));
        assertThrows(IllegalArgumentException.class, () -> config.setMaxUsers(-1));
    }

    @Test
    void shouldExposeConnectedClientsCountService() throws UserConnectionLimitExceededException {
        service.connect("User1");
        service.connect("User2");

        assertEquals(2, service.getConnectedClientsCount());
    }

    @Test
    void shouldExposeConnectedClientsListWithIpDateAndTime() throws UserConnectionLimitExceededException {
        service.connect("User1", "10.0.0.1");
        service.connect("User2", "10.0.0.2");

        var clients = service.getConnectedClientsInfo();

        assertEquals(2, clients.size());
        assertTrue(clients.stream().anyMatch(c -> c.ipAddress().equals("10.0.0.1") && c.startDate() != null && c.startTime() != null));
        assertTrue(clients.stream().anyMatch(c -> c.ipAddress().equals("10.0.0.2") && c.startDate() != null && c.startTime() != null));
    }
}

