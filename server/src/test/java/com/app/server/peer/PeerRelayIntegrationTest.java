package com.app.server.peer;

import com.app.server.models.Documento;
import com.app.server.service.DocumentoService;
import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;
import com.app.shared.util.CryptoUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Relay mínimo {@code PEER_ENTREGAR_MENSAJE}: un {@link PeerServer} con un
 * {@link DocumentoService} de prueba (sin BD) y un {@link PeerClient} que envía el payload.
 */
class PeerRelayIntegrationTest {

    private PeerServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private static int puertoLibre() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static final class StubDocumentoService extends DocumentoService {
        volatile boolean procesarMensajeInvocado;

        StubDocumentoService(SecretKey serverKey) {
            super(serverKey);
        }

        @Override
        public Documento procesarMensaje(String texto, String ipOrigen, DocumentoEnvioParams envio) {
            procesarMensajeInvocado = true;
            Documento doc = new Documento(
                    "relay_test", "txt",
                    texto != null ? texto.length() : 0,
                    null,
                    "stub-hash",
                    ipOrigen,
                    Documento.Tipo.MENSAJE);
            doc.setId(42L);
            return doc;
        }

        @Override
        public List<Documento> listarDocumentosPublicos() throws SQLException {
            return Collections.emptyList();
        }
    }

    @Test
    void entregarMensajePeerDevuelveOkYUsaDocumentoService() throws Exception {
        int puerto = puertoLibre();
        SecretKey key = CryptoUtil.generateAESKey();
        StubDocumentoService docSvc = new StubDocumentoService(key);
        PeerRegistry registry = new PeerRegistry("server-A", null);
        server = new PeerServer(puerto, registry, docSvc, null, null);
        server.start();
        Thread.sleep(100);

        PeerInfo self = new PeerInfo("server-B", "127.0.0.1", 9100, 9000, 9001);
        PeerClient client = new PeerClient(self, 2000);

        Mensaje relay = new Mensaje(Comando.ENVIAR_MENSAJE);
        relay.put("texto", "hola relay");
        relay.put("destIp", "10.0.0.2");
        relay.put("destPuerto", 5000);
        relay.put("destProtocolo", "TCP");
        relay.put("ipPropietario", "127.0.0.1");
        relay.put("remitentePuerto", 4000);
        relay.put("remitenteProtocolo", "TCP");
        relay.put("remitenteNombre", "Alice");
        relay.put("origenServidorEtiqueta", "server-B");
        relay.put("origenPeerId", "uuid-b");

        PeerInfo destino = new PeerInfo("server-A", "127.0.0.1", puerto, 9000, 9001);
        Mensaje respuesta = client.entregarMensajePeer(destino, relay);

        assertEquals(Comando.RESPUESTA, respuesta.getComando());
        assertEquals(42L, respuesta.getLong("documentoId"));
        assertTrue(docSvc.procesarMensajeInvocado);
    }
}
