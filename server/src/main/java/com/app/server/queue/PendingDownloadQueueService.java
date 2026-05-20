package com.app.server.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.server.net.ClientHandler;
import com.app.server.peer.PeerInfo;
import com.app.server.peer.PeerProxyService;

/**
 * Cola en memoria para solicitudes de descarga hacia peers desconectados.
 *
 * Reintenta automaticamente cuando detecta que el peer vuelve a estar online.
 */
public class PendingDownloadQueueService implements AutoCloseable {

    private static final int MAX_REINTENTOS = 3;
    private static final int RETRY_INTERVAL_SECONDS = 5;

    private final PeerProxyService peerProxy;
    private final ServerEventBus eventBus;
    private final Map<String, ConcurrentLinkedQueue<PendingDownloadRequest>> byPeer = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public PendingDownloadQueueService(PeerProxyService peerProxy, ServerEventBus eventBus) {
        this.peerProxy = peerProxy;
        this.eventBus = eventBus;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pending-download-queue");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::retryOnlinePeers,
                RETRY_INTERVAL_SECONDS,
                RETRY_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    public void enqueue(long documentoId, String peerId, ClientHandler handler) {
        PendingDownloadRequest req = new PendingDownloadRequest(documentoId, peerId, handler);
        byPeer.computeIfAbsent(peerId, ignored -> new ConcurrentLinkedQueue<>()).offer(req);
        if (eventBus != null) {
            eventBus.publish(ServerEventType.PEER_ERROR,
                    "pending-download-queue",
                    "encolada docId=" + documentoId + " peer=" + shortPeer(peerId)
                            + " cliente=" + handler.getContext());
        }
    }

    public int size() {
        int total = 0;
        for (ConcurrentLinkedQueue<PendingDownloadRequest> q : byPeer.values()) {
            total += q.size();
        }
        return total;
    }

    public String resolveServerDisplayName(String peerId) {
        if (peerProxy == null || peerProxy.getRegistry() == null) {
            return shortPeer(peerId);
        }
        PeerInfo info = peerProxy.getRegistry().getById(peerId).orElse(null);
        if (info == null) {
            return shortPeer(peerId);
        }
        String nombre = info.getNombre();
        if (nombre == null || nombre.isBlank()) {
            return shortPeer(peerId);
        }
        return nombre;
    }

    private void retryOnlinePeers() {
        if (peerProxy == null || peerProxy.getRegistry() == null) {
            return;
        }
        List<String> peerIds = new ArrayList<>(byPeer.keySet());
        for (String peerId : peerIds) {
            if (peerProxy.getRegistry().getById(peerId).isEmpty()) {
                continue;
            }
            procesarPeer(peerId);
        }
    }

    private void procesarPeer(String peerId) {
        ConcurrentLinkedQueue<PendingDownloadRequest> queue = byPeer.get(peerId);
        if (queue == null) {
            return;
        }

        PendingDownloadRequest req;
        while ((req = queue.poll()) != null) {
            try {
                req.incrementarIntentos();
                if (eventBus != null) {
                    eventBus.publish(ServerEventType.PEER_DESCARGA_PROXY,
                        "pending-download-queue",
                        "reintentando docId=" + req.getDocumentoId()
                            + " peer=" + shortPeer(peerId)
                            + " cliente=" + req.getHandler().getContext());
                }
                // Traza adicional para facilitar debug en consola.
                System.out.println("[PENDING-QUEUE] Reintentando entrega docId=" + req.getDocumentoId()
                    + " peer=" + shortPeer(peerId)
                    + " cliente=" + req.getHandler().getContext());
                req.getHandler().retryQueuedDownload(req.getDocumentoId(), peerId);
            } catch (Exception e) {
                if (!req.getHandler().isActive()) {
                    if (eventBus != null) {
                        eventBus.publish(ServerEventType.PEER_ERROR,
                                "pending-download-queue",
                                "cliente desconectado, descartando solicitud docId=" + req.getDocumentoId()
                                        + " peer=" + shortPeer(peerId));
                    }
                    continue;
                }
                if (req.getIntentos() < MAX_REINTENTOS) {
                    queue.offer(req);
                } else if (eventBus != null) {
                    eventBus.publishError("pending-download-queue",
                            new IllegalStateException("agotados reintentos docId="
                                    + req.getDocumentoId() + " peer=" + shortPeer(peerId)
                                    + " ultimoError=" + e.getMessage()),
                            null);
                }
            }
        }

        if (queue.isEmpty()) {
            byPeer.remove(peerId, queue);
        }
    }

    private static String shortPeer(String peerId) {
        if (peerId == null || peerId.isBlank()) {
            return "desconocido";
        }
        return peerId.substring(0, Math.min(8, peerId.length()));
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}