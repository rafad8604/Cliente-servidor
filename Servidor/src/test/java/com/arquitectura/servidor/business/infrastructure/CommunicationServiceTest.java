package com.arquitectura.servidor.business.infrastructure;

import com.arquitectura.servidor.business.activity.ServerActivitySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommunicationServiceTest {

    private CommunicationService service;
    private ProtocolConfig config;
    private ServerActivitySource activitySource;

    @BeforeEach
    void setUp() {
        config = new ProtocolConfig();
        config.setProtocol("TCP");
        config.setPort(5000);
        activitySource = new ServerActivitySource();
        service = new CommunicationService(
            config,
            List.of(new StubProtocolAdapter(CommunicationProtocol.TCP), new StubProtocolAdapter(CommunicationProtocol.UDP)),
            activitySource
        );
    }

    @Test
    void shouldStartListening() {
        assertFalse(service.isListening());
        service.startListening();
        assertTrue(service.isListening());
    }

    @Test
    void shouldStopListening() {
        service.startListening();
        assertTrue(service.isListening());
        service.stopListening();
        assertFalse(service.isListening());
    }

    @Test
    void shouldNotStartListeningTwice() {
        service.startListening();
        service.startListening(); // No debe hacer nada la segunda vez
        assertTrue(service.isListening());
    }

    @Test
    void shouldReturnProtocolAndPort() {
        assertEquals(CommunicationProtocol.TCP, service.getProtocol());
        assertEquals(5000, service.getPort());
    }

    @Test
    void shouldGenerateServerInfo() {
        String info = service.getServerInfo();
        assertNotNull(info);
        assertTrue(info.contains("TCP"));
        assertTrue(info.contains("5000"));
    }

    @Test
    void shouldHandleUDPProtocol() {
        config.setProtocol("UDP");
        config.setPort(6000);
        
        assertEquals(CommunicationProtocol.UDP, service.getProtocol());
        assertEquals(6000, service.getPort());
        
        String info = service.getServerInfo();
        assertTrue(info.contains("UDP"));
        assertTrue(info.contains("6000"));
    }

    @Test
    void shouldFailWhenProtocolHasNoAdapter() {
        CommunicationService onlyTcpService = new CommunicationService(
            config,
            List.of(new StubProtocolAdapter(CommunicationProtocol.TCP)),
            activitySource
        );
        config.setProtocol("UDP");

        assertThrows(IllegalStateException.class, onlyTcpService::startListening);
    }

    private static class StubProtocolAdapter implements ProtocolAdapter {
        private final CommunicationProtocol protocol;
        private boolean listening;

        private StubProtocolAdapter(CommunicationProtocol protocol) {
            this.protocol = protocol;
        }

        @Override
        public CommunicationProtocol protocol() {
            return protocol;
        }

        @Override
        public void start(int port) {
            listening = true;
        }

        @Override
        public void stop() {
            listening = false;
        }

        @Override
        public boolean isListening() {
            return listening;
        }

        @Override
        public String description() {
            return protocol.getDescription();
        }
    }
}


