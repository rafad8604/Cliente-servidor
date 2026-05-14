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
    void nombreCustomSeAlmacenaYHostEsFallback() {
        PeerInfo conNombre = new PeerInfo("id-x", "PC-SalaA", "10.0.0.5", 9100, 9000, 9001);
        assertEquals("PC-SalaA", conNombre.getNombre());

        PeerInfo sinNombre = new PeerInfo("id-y", null, "10.0.0.6", 9100, 9000, 9001);
        assertEquals("10.0.0.6", sinNombre.getNombre());

        PeerInfo nombreBlank = new PeerInfo("id-z", "  ", "10.0.0.7", 9100, 9000, 9001);
        assertEquals("10.0.0.7", nombreBlank.getNombre());
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
