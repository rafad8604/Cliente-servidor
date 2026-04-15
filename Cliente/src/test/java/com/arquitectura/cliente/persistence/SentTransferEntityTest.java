package com.arquitectura.cliente.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SentTransferEntityTest {

    @Test
    void createsAndReadsTransferMetadata() {
        SentTransferEntity transfer = new SentTransferEntity();
        transfer.setSentAt(Instant.now());
        transfer.setServerHost("127.0.0.1");
        transfer.setServerPort(5000);
        transfer.setProtocol("TCP");
        transfer.setRecipientId("destino-1");
        transfer.setType("MESSAGE");
        transfer.setMessagePreview("Hola");
        transfer.setSizeBytes(4L);
        transfer.setStatus("SENT");

        assertNotNull(transfer.getSentAt());
        assertEquals("127.0.0.1", transfer.getServerHost());
        assertEquals(5000, transfer.getServerPort());
        assertEquals("TCP", transfer.getProtocol());
        assertEquals("destino-1", transfer.getRecipientId());
        assertEquals("MESSAGE", transfer.getType());
        assertEquals("Hola", transfer.getMessagePreview());
        assertEquals(4L, transfer.getSizeBytes());
        assertEquals("SENT", transfer.getStatus());
    }
}

