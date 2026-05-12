package com.app.server.peer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PeerInfoTest {

    @Test
    void igualdadPorId() {
        PeerInfo a = new PeerInfo("id-1", "1.1.1.1", 1, 2, 3);
        PeerInfo b = new PeerInfo("id-1", "2.2.2.2", 4, 5, 6);
        PeerInfo c = new PeerInfo("id-2", "1.1.1.1", 1, 2, 3);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void marcarVistoActualizaTimestamp() throws InterruptedException {
        PeerInfo info = new PeerInfo("id-1", "1.1.1.1", 1, 2, 3);
        var t1 = info.getUltimaSenal();
        Thread.sleep(5);
        info.marcarVisto();
        assertTrue(info.getUltimaSenal().isAfter(t1));
    }

    @Test
    void expirado() throws InterruptedException {
        PeerInfo info = new PeerInfo("id-1", "1.1.1.1", 1, 2, 3);
        assertFalse(info.expirado(1000));
        Thread.sleep(50);
        assertTrue(info.expirado(10));
    }
}
