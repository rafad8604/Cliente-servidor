package com.app.server.peer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PeerRegistryTest {

    @Test
    void aplicarHelloRegistraNuevoPeer() {
        PeerRegistry registry = new PeerRegistry("local-1", null);
        PeerInfo nuevo = new PeerInfo("peer-A", "1.2.3.4", 9100, 9000, 9001);

        PeerInfo aplicado = registry.aplicarHello(nuevo);

        assertNotNull(aplicado);
        assertEquals(1, registry.size());
        assertEquals(nuevo, registry.getById("peer-A").orElseThrow());
    }

    @Test
    void noSeAutoRegistra() {
        PeerRegistry registry = new PeerRegistry("local-1", null);

        PeerInfo aplicado = registry.aplicarHello(
                new PeerInfo("local-1", "127.0.0.1", 9100, 9000, 9001));

        assertNull(aplicado);
        assertEquals(0, registry.size());
    }

    @Test
    void helloDuplicadoActualizaTimestamp() throws InterruptedException {
        PeerRegistry registry = new PeerRegistry("local-1", null);
        PeerInfo inicial = new PeerInfo("peer-A", "1.2.3.4", 9100, 9000, 9001);

        registry.aplicarHello(inicial);
        var marcaInicial = registry.getById("peer-A").orElseThrow().getUltimaSenal();

        Thread.sleep(5);
        registry.aplicarHello(new PeerInfo("peer-A", "1.2.3.4", 9100, 9000, 9001));
        var marcaActualizada = registry.getById("peer-A").orElseThrow().getUltimaSenal();

        assertTrue(marcaActualizada.isAfter(marcaInicial));
        assertEquals(1, registry.size());
    }

    @Test
    void peerExpiradoSeLimpiaAlConsultar() throws InterruptedException {
        PeerRegistry registry = new PeerRegistry("local-1", null, 30);
        registry.aplicarHello(new PeerInfo("peer-A", "1.2.3.4", 9100, 9000, 9001));

        assertEquals(1, registry.size());

        Thread.sleep(50);

        assertTrue(registry.getById("peer-A").isEmpty());
        assertEquals(0, registry.size());
    }

    @Test
    void listarOnlineOrdenadoPorId() {
        PeerRegistry registry = new PeerRegistry("local-1", null);
        registry.aplicarHello(new PeerInfo("peer-Z", "1.1.1.1", 9100, 9000, 9001));
        registry.aplicarHello(new PeerInfo("peer-A", "2.2.2.2", 9100, 9000, 9001));
        registry.aplicarHello(new PeerInfo("peer-M", "3.3.3.3", 9100, 9000, 9001));

        var online = registry.listarOnline();
        assertEquals(3, online.size());
        assertEquals("peer-A", online.get(0).getId());
        assertEquals("peer-M", online.get(1).getId());
        assertEquals("peer-Z", online.get(2).getId());
    }

    @Test
    void removerEliminaElPeer() {
        PeerRegistry registry = new PeerRegistry("local-1", null);
        registry.aplicarHello(new PeerInfo("peer-A", "1.2.3.4", 9100, 9000, 9001));

        registry.remover("peer-A");

        assertEquals(0, registry.size());
    }
}
