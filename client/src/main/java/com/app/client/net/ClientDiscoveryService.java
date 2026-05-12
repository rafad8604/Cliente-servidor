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

    public ClientDiscoveryService() {
        this(DISCOVERY_PORT, DEFAULT_TTL_MILLIS);
    }

    public ClientDiscoveryService(int port, long ttlMillis) {
        this.port = port;
        this.ttlMillis = ttlMillis;
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
                if (running) System.err.println("[CLIENT-DISC] Socket cerrado: " + e.getMessage());
            } catch (Exception e) {
                // Mensajes mal formados se ignoran.
            }
        }
    }

    private void procesar(byte[] data, String origenHost) {
        try {
            Mensaje msg = Mensaje.fromJson(new String(data, StandardCharsets.UTF_8));
            if (msg.getComando() == null) return;
            String id = msg.getString("id");
            if (id == null) return;

            if (msg.getComando() == Comando.PEER_HELLO) {
                String host = msg.getString("host");
                if (host == null || host.isBlank()) host = origenHost;
                String nombre = msg.getString("nombre");
                int puertoTcp = leerInt(msg.getDatos(), "puertoTcp");
                int puertoUdp = leerInt(msg.getDatos(), "puertoUdp");
                int puertoPeer = leerInt(msg.getDatos(), "puertoPeer");

                DiscoveredServer s = servers.get(id);
                if (s == null) {
                    servers.put(id, new DiscoveredServer(id, nombre, host, puertoTcp, puertoUdp, puertoPeer));
                } else {
                    s.marcarVisto();
                }
            } else if (msg.getComando() == Comando.PEER_BYE) {
                servers.remove(id);
            }
        } catch (Exception e) {
            // ignorar mensajes invalidos
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
        servers.entrySet().removeIf(e -> e.getValue().expirado(ttlMillis));
    }
}
