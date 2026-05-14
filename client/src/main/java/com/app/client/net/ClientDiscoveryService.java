package com.app.client.net;

import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Escucha el broadcast UDP de descubrimiento (puerto {@link #DISCOVERY_PORT})
 * para detectar servidores online en la LAN.
 *
 * <p>Usa {@code SO_REUSEADDR} para poder coexistir con un servidor que esta
 * corriendo en la misma maquina (caso PC1 = servidor + cliente). Mantiene
 * un {@link ConcurrentHashMap} con los servidores vistos y aplica TTL.</p>
 *
 * <p>Eventos manejados:</p>
 * <ul>
 *   <li>{@code PEER_HELLO} -- registra o refresca el servidor.</li>
 *   <li>{@code PEER_BYE}   -- elimina el servidor inmediatamente.</li>
 * </ul>
 */
public class ClientDiscoveryService {

    public static final int DISCOVERY_PORT = 9200;
    public static final long DEFAULT_TTL_MILLIS = 15_000;

    private final int port;
    private final long ttlMillis;
    private final Map<String, DiscoveredServer> servers = new ConcurrentHashMap<>();

    private DatagramSocket socket;
    private Thread listenerThread;
    private volatile boolean running = false;
    private volatile boolean verbose = true;

    public ClientDiscoveryService() {
        this(DISCOVERY_PORT, DEFAULT_TTL_MILLIS);
    }

    public ClientDiscoveryService(int port, long ttlMillis) {
        this.port = port;
        this.ttlMillis = ttlMillis;
    }

    /** Habilita / deshabilita logs por consola (los tests lo apagan). */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.setBroadcast(true);
        socket.bind(new InetSocketAddress(port));

        running = true;
        listenerThread = new Thread(this::loop, "client-discovery");
        listenerThread.setDaemon(true);
        listenerThread.start();

        log("[CLIENT-DISC] Escuchando broadcast UDP en 0.0.0.0:" + port
                + "  TTL=" + ttlMillis + "ms");
    }

    public synchronized void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        try {
            if (listenerThread != null) listenerThread.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isRunning() { return running; }

    /**
     * Snapshot de los servidores activos (no expirados), ordenados por host.
     */
    public List<DiscoveredServer> servidoresOnline() {
        depurarExpirados();
        List<DiscoveredServer> snapshot = new ArrayList<>(servers.values());
        snapshot.sort(Comparator.comparing(DiscoveredServer::getHost)
                .thenComparingInt(DiscoveredServer::getPuertoTcp));
        return snapshot;
    }

    /** Permite inyectar servidores manualmente (p.ej. al conectar a una IP fija). */
    public void registrarManual(DiscoveredServer server) {
        servers.put(server.getId(), server);
    }

    private void loop() {
        byte[] buffer = new byte[8192];
        while (running && !socket.isClosed()) {
            try {
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                socket.receive(dp);
                byte[] data = new byte[dp.getLength()];
                System.arraycopy(dp.getData(), 0, data, 0, dp.getLength());
                procesar(data, dp.getAddress().getHostAddress());
            } catch (SocketException e) {
                if (running) {
                    System.err.println("[CLIENT-DISC] Socket cerrado: " + e.getMessage());
                }
            } catch (Exception e) {
                System.err.println("[CLIENT-DISC] Error recibiendo: " + e.getMessage());
            }
        }
    }

    private void procesar(byte[] data, String origenHost) {
        String json = new String(data, StandardCharsets.UTF_8);
        Mensaje msg;
        try {
            msg = Mensaje.fromJson(json);
        } catch (Exception e) {
            log("[CLIENT-DISC] Descarto paquete malformado desde " + origenHost
                    + ": " + e.getMessage());
            return;
        }
        if (msg == null || msg.getComando() == null) {
            log("[CLIENT-DISC] Descarto paquete sin comando desde " + origenHost);
            return;
        }

        String id = msg.getString("id");
        if (id == null || id.isBlank()) {
            log("[CLIENT-DISC] Descarto hello sin id desde " + origenHost);
            return;
        }

        if (msg.getComando() == Comando.PEER_HELLO) {
            String host = msg.getString("host");
            if (host == null || host.isBlank() || "0.0.0.0".equals(host)
                    || host.startsWith("127.")) {
                host = origenHost;
            }
            String nombre = msg.getString("nombre");
            int puertoTcp = leerInt(msg.getDatos(), "puertoTcp");
            int puertoUdp = leerInt(msg.getDatos(), "puertoUdp");
            int puertoPeer = leerInt(msg.getDatos(), "puertoPeer");

            DiscoveredServer existente = servers.get(id);
            if (existente == null) {
                DiscoveredServer s = new DiscoveredServer(id, nombre, host,
                        puertoTcp, puertoUdp, puertoPeer);
                servers.put(id, s);
                log("[CLIENT-DISC] [+] Nuevo servidor: " + s.getNombre()
                        + "  host=" + host
                        + "  tcp=" + puertoTcp + " udp=" + puertoUdp + " peer=" + puertoPeer
                        + "  id=" + s.shortId()
                        + "  (origen UDP=" + origenHost + ")");
            } else {
                existente.marcarVisto();
                // Refresh: log solo si cambia algo importante.
                if (!existente.getHost().equals(host)) {
                    log("[CLIENT-DISC] (~) " + existente.getNombre() + " cambio host "
                            + existente.getHost() + " -> " + host);
                }
            }
        } else if (msg.getComando() == Comando.PEER_BYE) {
            DiscoveredServer removed = servers.remove(id);
            if (removed != null) {
                log("[CLIENT-DISC] [-] BYE: " + removed.getNombre()
                        + " (id=" + removed.shortId() + ")");
            }
        } else {
            log("[CLIENT-DISC] Comando ignorado en canal discovery: " + msg.getComando());
        }
    }

    private static int leerInt(Map<String, Object> datos, String key) {
        Object v = datos.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return 0;
        try { return Integer.parseInt(v.toString()); }
        catch (NumberFormatException e) { return 0; }
    }

    private void depurarExpirados() {
        servers.entrySet().removeIf(e -> {
            boolean expirado = e.getValue().expirado(ttlMillis);
            if (expirado) {
                log("[CLIENT-DISC] [-] Expirado por TTL: " + e.getValue().getNombre()
                        + "  host=" + e.getValue().getHost()
                        + "  id=" + e.getValue().shortId());
            }
            return expirado;
        });
    }

    private void log(String msg) {
        if (verbose) System.out.println(msg);
    }
}
