package com.app.server.peer;

import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que un PeerServer y PeerClient se comunican correctamente para el
 * comando PEER_PING (que no requiere BD ni cifrado, lo que mantiene el test
 * sin dependencias externas).
 */
class PeerPingIntegrationTest {

    private PeerServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    private static int puertoLibre() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    void pingEntreServidoresDevuelveRespuestaOk() throws Exception {
        int puerto = puertoLibre();
        PeerRegistry registry = new PeerRegistry("server-A", null);
        // documentoService=null porque PING no lo usa; el constructor lo acepta.
        server = new PeerServer(puerto, registry, null, null, null);
        server.start();

        // Esperar un instante a que el accept thread este listo.
        Thread.sleep(100);

        PeerInfo self = new PeerInfo("server-B", "127.0.0.1", 9100, 9000, 9001);
        PeerClient client = new PeerClient(self, 2000);

        PeerInfo destino = new PeerInfo("server-A", "127.0.0.1", puerto, 9000, 9001);
        Mensaje respuesta = client.ping(destino);

        assertEquals(Comando.RESPUESTA, respuesta.getComando());
        assertTrue(respuesta.getBoolean("pong"));
    }

    @Test
    void helloDelClienteSeRegistraEnElServidor() throws Exception {
        int puerto = puertoLibre();
        PeerRegistry registry = new PeerRegistry("server-A", null);
        server = new PeerServer(puerto, registry, null, null, null);
        server.start();
        Thread.sleep(100);

        PeerInfo self = new PeerInfo("server-B", "127.0.0.1", 9100, 9500, 9501);
        PeerClient client = new PeerClient(self, 2000);
        client.ping(new PeerInfo("server-A", "127.0.0.1", puerto, 9000, 9001));

        // El handshake hizo aplicarHello en el registry del servidor.
        Thread.sleep(50);
        assertTrue(registry.getById("server-B").isPresent());
    }
}
