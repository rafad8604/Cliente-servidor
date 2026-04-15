package com.arquitectura.cliente.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionSettingsTest {

    @Test
    void testValidConnectionSettings() {
        ConnectionSettings settings = new ConnectionSettings("localhost", 5000, TransportProtocol.TCP);
        assertEquals("localhost", settings.host());
        assertEquals(5000, settings.port());
        assertEquals(TransportProtocol.TCP, settings.protocol());
    }

    @Test
    void testInvalidHostNull() {
        assertThrows(IllegalArgumentException.class, () ->
            new ConnectionSettings(null, 5000, TransportProtocol.TCP)
        );
    }

    @Test
    void testInvalidHostBlank() {
        assertThrows(IllegalArgumentException.class, () ->
            new ConnectionSettings("   ", 5000, TransportProtocol.TCP)
        );
    }

    @Test
    void testInvalidPortTooLow() {
        assertThrows(IllegalArgumentException.class, () ->
            new ConnectionSettings("localhost", 0, TransportProtocol.TCP)
        );
    }

    @Test
    void testInvalidPortTooHigh() {
        assertThrows(IllegalArgumentException.class, () ->
            new ConnectionSettings("localhost", 65536, TransportProtocol.TCP)
        );
    }

    @Test
    void testInvalidProtocolNull() {
        assertThrows(IllegalArgumentException.class, () ->
            new ConnectionSettings("localhost", 5000, null)
        );
    }

    @Test
    void testValidPortBoundaries() {
        ConnectionSettings settings1 = new ConnectionSettings("localhost", 1, TransportProtocol.TCP);
        assertEquals(1, settings1.port());

        ConnectionSettings settings2 = new ConnectionSettings("localhost", 65535, TransportProtocol.UDP);
        assertEquals(65535, settings2.port());
    }
}

