package com.arquitectura.cliente.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransportProtocolTest {

    @Test
    void testFromStringTcp() {
        TransportProtocol protocol = TransportProtocol.fromString("TCP");
        assertEquals(TransportProtocol.TCP, protocol);
    }

    @Test
    void testFromStringUdp() {
        TransportProtocol protocol = TransportProtocol.fromString("UDP");
        assertEquals(TransportProtocol.UDP, protocol);
    }

    @Test
    void testFromStringCaseInsensitive() {
        assertEquals(TransportProtocol.TCP, TransportProtocol.fromString("tcp"));
        assertEquals(TransportProtocol.UDP, TransportProtocol.fromString("udp"));
    }

    @Test
    void testFromStringInvalid() {
        assertThrows(IllegalArgumentException.class, () -> 
            TransportProtocol.fromString("INVALID")
        );
    }

    @Test
    void testFromStringNull() {
        assertThrows(IllegalArgumentException.class, () -> 
            TransportProtocol.fromString(null)
        );
    }

    @Test
    void testFromStringBlank() {
        assertThrows(IllegalArgumentException.class, () -> 
            TransportProtocol.fromString("   ")
        );
    }
}

