package com.app.server.peer;

import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.shared.protocol.Mensaje;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Catalogo en cache (TTL) de documentos publicados por peers remotos.
 *
 * <p>Un hilo programado refresca cada {@link #refreshMillis} milisegundos
 * la lista de documentos de cada peer online consultando
 * {@code PEER_LISTAR_DOCS}. Cada peer tiene su propia entrada con
 * timestamp; entradas mas viejas que {@link #ttlMillis} se descartan.</p>
 */
public class PeerCatalog {

    public static final long DEFAULT_TTL_MILLIS = 30_000;
    public static final long DEFAULT_REFRESH_MILLIS = 10_000;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final PeerRegistry registry;
    private final PeerClient peerClient;
    private final ServerEventBus eventBus;
    private final long ttlMillis;
    private final long refreshMillis;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public PeerCatalog(PeerRegistry registry, PeerClient peerClient, ServerEventBus eventBus) {
        this(registry, peerClient, eventBus, DEFAULT_TTL_MILLIS, DEFAULT_REFRESH_MILLIS);
    }

    public PeerCatalog(PeerRegistry registry, PeerClient peerClient, ServerEventBus eventBus,
                       long ttlMillis, long refreshMillis) {
        this.registry = registry;
        this.peerClient = peerClient;
        this.eventBus = eventBus;
        this.ttlMillis = ttlMillis;
        this.refreshMillis = refreshMillis;
    }

    public synchronized void start() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "peer-catalog-refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::refrescarTodo,
                refreshMillis, refreshMillis, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * Refresca de inmediato el catalogo de un peer (uso en tests o forzado manual).
     */
    public void refrescarPeer(PeerInfo peer) {
        try {
            Mensaje respuesta = peerClient.listarDocs(peer);
            String docsJson = respuesta.getString("documentos");
            if (docsJson == null) {
                cache.put(peer.getId(), new CacheEntry(Collections.emptyList(), Instant.now()));
                return;
            }
            List<Map<String, Object>> rows = GSON.fromJson(docsJson,
                    new TypeToken<List<Map<String, Object>>>() {}.getType());
            if (rows == null) rows = Collections.emptyList();

            List<RemoteDocumento> docs = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                docs.add(RemoteDocumento.fromMap(row, peer.getId()));
            }
            cache.put(peer.getId(), new CacheEntry(docs, Instant.now()));
            if (eventBus != null) {
                eventBus.publish(ServerEventType.PEER_CATALOGO_ACTUALIZADO, "peer-catalog",
                        "peer=" + peer.getId().substring(0, 8) + " docs=" + docs.size());
            }
        } catch (IOException e) {
            System.err.println("[PEER-CATALOG] No se pudo refrescar " + peer + ": " + e.getMessage());
            if (eventBus != null) eventBus.publishError("peer-catalog", e, null);
        }
    }

    private void refrescarTodo() {
        for (PeerInfo peer : registry.listarOnline()) {
            refrescarPeer(peer);
        }
        purgarExpirados();
    }

    private void purgarExpirados() {
        long now = Instant.now().toEpochMilli();
        cache.entrySet().removeIf(e -> now - e.getValue().timestamp.toEpochMilli() > ttlMillis);
    }

    /**
     * Devuelve los documentos remotos validos (no expirados), unidos de todos los peers.
     */
    public List<RemoteDocumento> listarRemotos() {
        long now = Instant.now().toEpochMilli();
        List<RemoteDocumento> all = new ArrayList<>();
        for (CacheEntry entry : cache.values()) {
            if (now - entry.timestamp.toEpochMilli() > ttlMillis) continue;
            all.addAll(entry.docs);
        }
        all.sort(Comparator.comparing(RemoteDocumento::getPeerId)
                .thenComparing(RemoteDocumento::getId));
        return Collections.unmodifiableList(all);
    }

    public List<RemoteDocumento> listarDePeer(String peerId) {
        CacheEntry entry = cache.get(peerId);
        if (entry == null) return Collections.emptyList();
        return Collections.unmodifiableList(entry.docs);
    }

    private static final class CacheEntry {
        final List<RemoteDocumento> docs;
        final Instant timestamp;
        CacheEntry(List<RemoteDocumento> docs, Instant timestamp) {
            this.docs = docs;
            this.timestamp = timestamp;
        }
    }

    /**
     * Vista DTO de un documento de otro peer. Se asocia con el {@code peerId}
     * del servidor que lo publica.
     */
    public static final class RemoteDocumento {
        private final long id;
        private final String nombre;
        private final String extension;
        private final long tamano;
        private final String tipo;
        private final String hash;
        private final String ip;
        private final String fecha;
        private final String peerId;

        public RemoteDocumento(long id, String nombre, String extension, long tamano,
                               String tipo, String hash, String ip, String fecha, String peerId) {
            this.id = id;
            this.nombre = nombre;
            this.extension = extension;
            this.tamano = tamano;
            this.tipo = tipo;
            this.hash = hash;
            this.ip = ip;
            this.fecha = fecha;
            this.peerId = peerId;
        }

        public long getId() { return id; }
        public String getNombre() { return nombre; }
        public String getExtension() { return extension; }
        public long getTamano() { return tamano; }
        public String getTipo() { return tipo; }
        public String getHash() { return hash; }
        public String getIp() { return ip; }
        public String getFecha() { return fecha; }
        public String getPeerId() { return peerId; }

        static RemoteDocumento fromMap(Map<String, Object> row, String peerId) {
            return new RemoteDocumento(
                    asLong(row.get("id")),
                    asString(row.get("nombre")),
                    asString(row.get("extension")),
                    asLong(row.get("tamano")),
                    asString(row.get("tipo")),
                    asString(row.get("hash")),
                    asString(row.get("ip")),
                    asString(row.get("fecha")),
                    peerId);
        }

        private static long asLong(Object v) {
            if (v instanceof Number n) return n.longValue();
            if (v == null) return 0;
            try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return 0; }
        }

        private static String asString(Object v) {
            return v == null ? "" : v.toString();
        }
    }
}
