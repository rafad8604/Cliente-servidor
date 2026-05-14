package com.app.server.peer;

import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.shared.protocol.Mensaje;

import java.io.IOException;
import java.io.InputStream;

/**
 * Servicio de proxy hacia peers.
 *
 * <p>Resuelve la descarga de documentos que viven en otros servidores. El
 * caller le pasa el {@code peerId} (que obtuvo del catalogo) y el
 * {@code documentoId} del peer; este servicio se encarga de localizar el peer
 * en el {@link PeerRegistry} y abrir la descarga.</p>
 */
public class PeerProxyService {

    private final PeerRegistry registry;
    private final PeerClient peerClient;
    private final ServerEventBus eventBus;

    public PeerProxyService(PeerRegistry registry, PeerClient peerClient, ServerEventBus eventBus) {
        this.registry = registry;
        this.peerClient = peerClient;
        this.eventBus = eventBus;
    }

    public PeerClient.PeerDownload descargar(String peerId, long documentoId) throws IOException {
        PeerInfo peer = registry.getById(peerId)
                .orElseThrow(() -> new IOException("Peer no disponible: " + peerId));
        PeerClient.PeerDownload download = peerClient.descargarArchivo(peer, documentoId);
        if (eventBus != null) {
            eventBus.publish(ServerEventType.PEER_DESCARGA_PROXY, "peer-proxy",
                    "peer=" + peerId.substring(0, Math.min(8, peerId.length()))
                            + " docId=" + documentoId + " (entrante)");
        }
        return download;
    }

    public String hash(String peerId, long documentoId) throws IOException {
        PeerInfo peer = registry.getById(peerId)
                .orElseThrow(() -> new IOException("Peer no disponible: " + peerId));
        return peerClient.hash(peer, documentoId).getString("hash");
    }

    public PeerRegistry getRegistry() {
        return registry;
    }

    public PeerClient getPeerClient() {
        return peerClient;
    }

    public Mensaje relayEntregarMensaje(String peerId, Mensaje relayPayload) throws IOException {
        PeerInfo peer = registry.getById(peerId)
                .orElseThrow(() -> new IOException("Peer no disponible: " + peerId));
        return peerClient.entregarMensajePeer(peer, relayPayload);
    }

    public Mensaje relayEntregarArchivo(String peerId, Mensaje relayHeader, InputStream body, long tamano)
            throws IOException {
        PeerInfo peer = registry.getById(peerId)
                .orElseThrow(() -> new IOException("Peer no disponible: " + peerId));
        return peerClient.entregarArchivoPeer(peer, relayHeader, body, tamano);
    }
}
