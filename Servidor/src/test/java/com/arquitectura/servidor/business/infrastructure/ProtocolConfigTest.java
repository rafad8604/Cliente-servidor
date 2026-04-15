package com.arquitectura.servidor.business.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolConfigTest {

    @Test
    void shouldValidatePortRange() {
        ProtocolConfig config = new ProtocolConfig();
        
        assertThrows(IllegalArgumentException.class, () -> config.setPort(0));
        assertThrows(IllegalArgumentException.class, () -> config.setPort(-1));
        assertThrows(IllegalArgumentException.class, () -> config.setPort(65536));
    }

    @Test
    void shouldAcceptValidPorts() {
        ProtocolConfig config = new ProtocolConfig();
        
        config.setPort(1);
        assertEquals(1, config.getPort());
        
        config.setPort(5000);
        assertEquals(5000, config.getPort());
        
        config.setPort(65535);
        assertEquals(65535, config.getPort());
    }

    @Test
    void shouldSetAndGetProtocol() {
        ProtocolConfig config = new ProtocolConfig();
        
        config.setProtocol("TCP");
        assertEquals(CommunicationProtocol.TCP, config.getProtocol());
        
        config.setProtocol("UDP");
        assertEquals(CommunicationProtocol.UDP, config.getProtocol());
    }

    @Test
    void shouldThrowExceptionOnInvalidProtocol() {
        ProtocolConfig config = new ProtocolConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setProtocol("INVALID"));
    }

    @Test
    void shouldReturnProtocolInfo() {
        ProtocolConfig config = new ProtocolConfig();
        config.setProtocol("TCP");
        
        assertEquals("TCP", config.getProtocolName());
        assertNotNull(config.getProtocolDescription());
        assertTrue(config.getProtocolDescription().contains("Control Protocol"));
    }
}


