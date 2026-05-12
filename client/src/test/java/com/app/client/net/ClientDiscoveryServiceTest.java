package com.app.client.net;

import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientDiscoveryServiceTest {

    private ClientDiscoveryService service;
    private DatagramSocket emisor;

    @AfterEach
    void tearDown() {
        if (service != null) service.stop();
        if (emisor != null && !emisor.isClosed()) emisor.close();
    }

    private static int puertoLibre() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    void detectaHelloUdpYRegistraServidor() throws Exception {
        int port = puertoLibre();
        service = new ClientDiscoveryService(port, 5000);
        service.start();
        Thread.sleep(100);

        emisor = new DatagramSocket();
        emisor.setBroadcast(true);
        Mensaje hello = new Mensaje(Comando.PEER_HELLO)
                .put("id", "server-uuid-123")
                .put("nombre", "PC-SalaA")
                .put("host", "10.0.0.50")
                .put("puertoTcp", 9000)
                .put("puertoUdp", 9001)
                .put("puertoPeer", 9100);
        byte[] payload = hello.toJson().getBytes(StandardCharsets.UTF_8);
        DatagramPacket dp = new DatagramPacket(payload, payload.length,
                InetAddress.getLoopbackAddress(), port);
        emisor.send(dp);
        Thread.sleep(150);

        List<DiscoveredServer> online = service.servidoresOnline();
        assertEquals(1, online.size());
        DiscoveredServer s = online.get(0);
        assertEquals("server-uuid-123", s.getId());
        assertEquals("PC-SalaA", s.getNombre());
        assertEquals("10.0.0.50", s.getHost());
        assertEquals(9000, s.getPuertoTcp());
        assertEquals(9001, s.getPuertoUdp());
    }

    @Test
    void byeQuitaServidorDelRegistro() throws Exception {
        int port = puertoLibre();
        service = new ClientDiscoveryService(port, 5000);
        service.start();
        Thread.sleep(100);

        emisor = new DatagramSocket();
        Mensaje hello = new Mensaje(Comando.PEER_HELLO)
                .put("id", "server-x").put("host", "1.2.3.4")
                .put("puertoTcp", 9000).put("puertoUdp", 9001).put("puertoPeer", 9100);
        byte[] helloBytes = hello.toJson().getBytes(StandardCharsets.UTF_8);
        emisor.send(new DatagramPacket(helloBytes, helloBytes.length,
                InetAddress.getLoopbackAddress(), port));
        Thread.sleep(100);
        assertEquals(1, service.servidoresOnline().size());

        Mensaje bye = new Mensaje(Comando.PEER_BYE).put("id", "server-x");
        byte[] byeBytes = bye.toJson().getBytes(StandardCharsets.UTF_8);
        emisor.send(new DatagramPacket(byeBytes, byeBytes.length,
                InetAddress.getLoopbackAddress(), port));
        Thread.sleep(100);

        assertEquals(0, service.servidoresOnline().size());
    }

    @Test
    void servidorExpiraSiNoLlegaHello() throws Exception {
        int port = puertoLibre();
        service = new ClientDiscoveryService(port, 200);
        service.start();
        Thread.sleep(50);

        emisor = new DatagramSocket();
        Mensaje hello = new Mensaje(Comando.PEER_HELLO)
                .put("id", "server-y").put("host", "5.6.7.8")
                .put("puertoTcp", 9000).put("puertoUdp", 9001).put("puertoPeer", 9100);
        byte[] payload = hello.toJson().getBytes(StandardCharsets.UTF_8);
        emisor.send(new DatagramPacket(payload, payload.length,
                InetAddress.getLoopbackAddress(), port));
        Thread.sleep(60);
        assertEquals(1, service.servidoresOnline().size());

        Thread.sleep(250);
        assertEquals(0, service.servidoresOnline().size());
    }
}
