package com.arquitectura.servidor.business.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommunicationProtocolTest {

    @Test
    void shouldParseTCPProtocol() {
        CommunicationProtocol protocol = CommunicationProtocol.fromString("TCP");
        assertEquals(CommunicationProtocol.TCP, protocol);
        assertEquals("TCP", protocol.getName());
    }

    @Test
    void shouldParseUDPProtocol() {
        CommunicationProtocol protocol = CommunicationProtocol.fromString("UDP");
        assertEquals(CommunicationProtocol.UDP, protocol);
        assertEquals("UDP", protocol.getName());
    }

    @Test
    void shouldParseProtocolCaseInsensitive() {
        CommunicationProtocol tcp1 = CommunicationProtocol.fromString("tcp");
        CommunicationProtocol tcp2 = CommunicationProtocol.fromString("TCP");
        assertEquals(tcp1, tcp2);
    }

    @Test
    void shouldThrowExceptionOnInvalidProtocol() {
        assertThrows(IllegalArgumentException.class, () -> CommunicationProtocol.fromString("INVALID"));
    }

    @Test
    void shouldThrowExceptionForNullOrBlankProtocol() {
        assertThrows(IllegalArgumentException.class, () -> CommunicationProtocol.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> CommunicationProtocol.fromString(" "));
    }

    @Test
    void shouldHaveDescriptions() {
        assertNotNull(CommunicationProtocol.TCP.getDescription());
        assertNotNull(CommunicationProtocol.UDP.getDescription());
        assertTrue(CommunicationProtocol.TCP.getDescription().contains("confiable"));
        assertTrue(CommunicationProtocol.UDP.getDescription().contains("garantia"));
    }
}


