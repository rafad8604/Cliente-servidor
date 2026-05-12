package com.app.server.peer;

import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro central de peers conocidos.
 *
 * Se llena con los anuncios de {@link PeerDiscoveryService} y se consulta
 * desde el resto del servidor para listar/saber quien esta online y como
 * contactarlo.
 *
 * Thread-safe. Limpia automaticamente peers expirados al consultar.
 */
public class PeerRegistry {

    /** TTL: peers que no envien hello en este intervalo se consideran offline. */
    public static final long DEFAULT_TTL_MILLIS = 15_000;

    private final String localId;
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private final ServerEventBus eventBus;
    private final long ttlMillis;

    public PeerRegistry(String localId, ServerEventBus eventBus) {
        this(localId, eventBus, DEFAULT_TTL_MILLIS);
    }

    public PeerRegistry(String localId, ServerEventBus eventBus, long ttlMillis) {
        this.localId = localId;
        this.eventBus = eventBus;
        this.ttlMillis = ttlMillis;
    }

    public String getLocalId() {
        return localId;
    }

    /**
     * Aplica un hello recibido: si es nuevo lo registra y emite DESCUBIERTO,
     * si ya existia solo actualiza la marca de tiempo.
     */
    public PeerInfo aplicarHello(PeerInfo info) {
        if (info.getId().equals(localId)) {
            // No nos registramos a nosotros mismos.
            return null;
        }
        PeerInfo existente = peers.get(info.getId());
        if (existente == null) {
            peers.put(info.getId(), info);
            if (eventBus != null) {
                eventBus.publish(ServerEventType.PEER_DESCUBIERTO, "peer-registry", info.toString());
            }
            return info;
        }
        existente.marcarVisto();
        return existente;
    }

    public Optional<PeerInfo> getById(String peerId) {
        PeerInfo info = peers.get(peerId);
        if (info == null) return Optional.empty();
        if (info.expirado(ttlMillis)) {
            peers.remove(peerId, info);
            notificarOffline(info);
            return Optional.empty();
        }
        return Optional.of(info);
    }

    public List<PeerInfo> listarOnline() {
        depurarExpirados();
        List<PeerInfo> snapshot = new ArrayList<>(peers.values());
        snapshot.sort(Comparator.comparing(PeerInfo::getId));
        return Collections.unmodifiableList(snapshot);
    }

    public int size() {
        depurarExpirados();
        return peers.size();
    }

    public void remover(String peerId) {
        PeerInfo info = peers.remove(peerId);
        if (info != null) notificarOffline(info);
    }

    private void depurarExpirados() {
        Collection<PeerInfo> snapshot = new ArrayList<>(peers.values());
        for (PeerInfo p : snapshot) {
            if (p.expirado(ttlMillis)) {
                if (peers.remove(p.getId(), p)) {
                    notificarOffline(p);
                }
            }
        }
    }

    private void notificarOffline(PeerInfo info) {
        if (eventBus != null) {
            eventBus.publish(ServerEventType.PEER_OFFLINE, "peer-registry", info.toString());
        }
    }
}
