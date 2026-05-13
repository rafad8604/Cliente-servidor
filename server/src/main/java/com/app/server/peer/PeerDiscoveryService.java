package com.app.server.peer;

import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.server.util.NetworkUtils;
import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio de descubrimiento de peers via UDP broadcast.
 *
 * <p>Cada servidor:
 * <ol>
 *   <li>Abre un {@link DatagramSocket} en {@link #discoveryPort} con {@code SO_REUSEADDR}
 *       y {@code SO_BROADCAST} habilitado (bind a {@code 0.0.0.0} para recibir
 *       de cualquier interfaz).</li>
 *   <li>Lanza un hilo lector que parsea {@link Comando#PEER_HELLO} y aplica el
 *       registro al {@link PeerRegistry}.</li>
 *   <li>Lanza una tarea programada que envia su propio {@code PEER_HELLO} en
 *       broadcast cada {@link #heartbeatMillis} <b>por cada interfaz LAN</b>
 *       (255.255.255.255 solo sale por la ruta por defecto, lo que en Windows
 *       con Docker / VPN suele dejar atras la LAN real).</li>
 * </ol>
 *
 * <p>El paquete {@code PEER_HELLO} es un {@link Mensaje} JSON con campos
 * {@code id}, {@code host}, {@code puertoPeer}, {@code puertoTcp}, {@code puertoUdp}.
 * Por ser broadcast local (LAN) no se usa cifrado.
 */
public class PeerDiscoveryService {

    public static final int DEFAULT_DISCOVERY_PORT = 9200;
    public static final long DEFAULT_HEARTBEAT_MILLIS = 5_000;

    private final PeerRegistry registry;
    private final ServerEventBus eventBus;
    private final int discoveryPort;
    private final long heartbeatMillis;
    private final PeerInfo selfInfo;
    private final AtomicLong helloCount = new AtomicLong();

    private DatagramSocket socket;
    private Thread listenerThread;
    private ScheduledExecutorService heartbeatExecutor;
    private volatile boolean running = false;

    public PeerDiscoveryService(PeerRegistry registry,
                                ServerEventBus eventBus,
                                PeerInfo selfInfo) {
        this(registry, eventBus, selfInfo, DEFAULT_DISCOVERY_PORT, DEFAULT_HEARTBEAT_MILLIS);
    }

    public PeerDiscoveryService(PeerRegistry registry,
                                ServerEventBus eventBus,
                                PeerInfo selfInfo,
                                int discoveryPort,
                                long heartbeatMillis) {
        this.registry = registry;
        this.eventBus = eventBus;
        this.selfInfo = selfInfo;
        this.discoveryPort = discoveryPort;
        this.heartbeatMillis = heartbeatMillis;
    }

    public synchronized void start() throws IOException {
        if (running) return;

        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.setBroadcast(true);
        socket.bind(new java.net.InetSocketAddress(discoveryPort));

        running = true;

        listenerThread = new Thread(this::loopReceptor, "peer-discovery-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "peer-discovery-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(this::enviarHelloBroadcast,
                0, heartbeatMillis, TimeUnit.MILLISECONDS);

        System.out.println("[PEER-DISC] Servicio iniciado");
        System.out.println("[PEER-DISC]   id=" + corto(selfInfo.getId())
                + " nombre=" + selfInfo.getNombre()
                + " host=" + selfInfo.getHost());
        System.out.println("[PEER-DISC]   escuchando UDP 0.0.0.0:" + discoveryPort
                + "  heartbeat=" + heartbeatMillis + "ms");

        List<InetAddress> bcast = NetworkUtils.direccionesBroadcast();
        System.out.println("[PEER-DISC]   broadcast targets (" + bcast.size() + "):");
        for (InetAddress a : bcast) {
            System.out.println("[PEER-DISC]     -> " + a.getHostAddress());
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;

        try {
            if (heartbeatExecutor != null) heartbeatExecutor.shutdownNow();
        } catch (Exception ignored) { }

        // Anuncio de despedida (best effort).
        try { enviarBye(); } catch (Exception ignored) { }

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        try {
            if (listenerThread != null) listenerThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("[PEER-DISC] Servicio detenido");
    }

    private void loopReceptor() {
        byte[] buffer = new byte[8192];
        while (running && !socket.isClosed()) {
            try {
                DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
                socket.receive(dp);
                byte[] data = new byte[dp.getLength()];
                System.arraycopy(dp.getData(), 0, data, 0, dp.getLength());
                procesarMensaje(data, dp.getAddress());
            } catch (SocketException e) {
                if (running) System.err.println("[PEER-DISC] Socket discovery cerrado: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("[PEER-DISC] Error receptor: " + e.getMessage());
                if (eventBus != null) eventBus.publishError("peer-discovery", e, null);
            }
        }
    }

    private void procesarMensaje(byte[] data, InetAddress origen) {
        String json = new String(data, StandardCharsets.UTF_8);
        Mensaje msg;
        try {
            msg = Mensaje.fromJson(json);
        } catch (Exception e) {
            System.err.println("[PEER-DISC] Mensaje malformado desde " + origen.getHostAddress()
                    + ": " + e.getMessage());
            return;
        }
        if (msg == null || msg.getComando() == null) {
            System.err.println("[PEER-DISC] Descartado: mensaje sin comando desde "
                    + origen.getHostAddress());
            return;
        }

        String peerId = msg.getString("id");
        if (peerId == null || peerId.isBlank()) {
            System.err.println("[PEER-DISC] Descartado: hello sin id desde "
                    + origen.getHostAddress());
            return;
        }
        if (peerId.equals(selfInfo.getId())) {
            // Eco de nuestro propio hello: silencioso (es lo esperado en broadcast).
            return;
        }

        switch (msg.getComando()) {
            case PEER_HELLO: {
                String host = msg.getString("host");
                if (host == null || host.isBlank() || "0.0.0.0".equals(host)
                        || host.startsWith("127.")) {
                    host = origen.getHostAddress();
                }
                String nombre = msg.getString("nombre");
                int puertoPeer = leerIntSeguro(msg, "puertoPeer");
                int puertoTcp = leerIntSeguro(msg, "puertoTcp");
                int puertoUdp = leerIntSeguro(msg, "puertoUdp");
                PeerInfo info = new PeerInfo(peerId, nombre, host, puertoPeer, puertoTcp, puertoUdp);

                boolean nuevo = registry.getById(peerId).isEmpty();
                PeerInfo aplicado = registry.aplicarHello(info);
                if (aplicado != null) {
                    System.out.println("[PEER-DISC] HELLO recibido de "
                            + (nombre != null ? nombre : "?") + " id=" + corto(peerId)
                            + " host=" + host
                            + " tcp=" + puertoTcp + " udp=" + puertoUdp + " peer=" + puertoPeer
                            + (nuevo ? "  [NUEVO]" : "  [refresh]"));
                    if (eventBus != null) {
                        eventBus.publish(ServerEventType.PEER_HELLO_RECIBIDO, "discovery",
                                aplicado.toString());
                    }
                }
                break;
            }
            case PEER_BYE: {
                System.out.println("[PEER-DISC] BYE recibido de id=" + corto(peerId));
                registry.remover(peerId);
                break;
            }
            default:
                System.err.println("[PEER-DISC] Comando inesperado en canal discovery: "
                        + msg.getComando());
        }
    }

    private void enviarHelloBroadcast() {
        try {
            byte[] payload = buildHelloPayload(Comando.PEER_HELLO);
            int envios = broadcast(payload);
            long n = helloCount.incrementAndGet();
            // Para no saturar la consola, log detallado solo en los primeros
            // envios y luego cada 10. La traza completa siempre va al evento.
            if (n <= 3 || n % 10 == 0) {
                System.out.println("[PEER-DISC] HELLO #" + n + " enviado a " + envios
                        + " destino(s)  id=" + corto(selfInfo.getId())
                        + " host=" + selfInfo.getHost()
                        + " tcp=" + selfInfo.getPuertoTcp()
                        + " udp=" + selfInfo.getPuertoUdp()
                        + " peer=" + selfInfo.getPuertoPeer());
            }
        } catch (Exception e) {
            System.err.println("[PEER-DISC] No se pudo enviar hello: " + e.getMessage());
            if (eventBus != null) eventBus.publishError("peer-discovery", e, null);
        }
    }

    private void enviarBye() throws IOException {
        byte[] payload = buildHelloPayload(Comando.PEER_BYE);
        broadcast(payload);
    }

    private byte[] buildHelloPayload(Comando comando) {
        Mensaje msg = new Mensaje(comando)
                .put("id", selfInfo.getId())
                .put("nombre", selfInfo.getNombre())
                .put("host", selfInfo.getHost())
                .put("puertoPeer", selfInfo.getPuertoPeer())
                .put("puertoTcp", selfInfo.getPuertoTcp())
                .put("puertoUdp", selfInfo.getPuertoUdp());
        return msg.toJson().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Envia el payload por <b>cada</b> direccion de broadcast LAN encontrada y,
     * adicionalmente, por loopback. Esto soluciona dos problemas:
     * <ul>
     *   <li>Windows envia {@code 255.255.255.255} solo por la interfaz de la
     *       ruta por defecto; si esa es virtual (Docker/VPN), la LAN real no
     *       recibe nada. Iterar por las {@code InterfaceAddress.getBroadcast()}
     *       garantiza que sale por todas las interfaces fisicas.</li>
     *   <li>Si en una misma PC corren servidor + cliente, {@code SO_REUSEADDR}
     *       no siempre entrega el paquete de broadcast al "otro" socket local
     *       en Windows. Enviar tambien a {@code 127.0.0.1} asegura la entrega
     *       local.</li>
     * </ul>
     *
     * @return cantidad de destinos a los que se envio realmente.
     */
    private int broadcast(byte[] payload) {
        int enviados = 0;

        for (InetAddress addr : NetworkUtils.direccionesBroadcast()) {
            try {
                DatagramPacket dp = new DatagramPacket(payload, payload.length,
                        addr, discoveryPort);
                socket.send(dp);
                enviados++;
            } catch (IOException e) {
                System.err.println("[PEER-DISC] Fallo enviando a "
                        + addr.getHostAddress() + ": " + e.getMessage());
            }
        }

        // Loopback: imprescindible cuando servidor + cliente conviven en la
        // misma maquina (PC1 = nodo + GUI cliente).
        try {
            DatagramPacket dp = new DatagramPacket(payload, payload.length,
                    InetAddress.getByName("127.0.0.1"), discoveryPort);
            socket.send(dp);
            enviados++;
        } catch (IOException e) {
            // Loopback fallando es muy raro pero no es fatal.
            System.err.println("[PEER-DISC] Fallo enviando a 127.0.0.1: " + e.getMessage());
        }

        return enviados;
    }

    private static int leerIntSeguro(Mensaje msg, String key) {
        try {
            return msg.getInt(key);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String corto(String id) {
        if (id == null) return "?";
        return id.length() > 8 ? id.substring(0, 8) : id;
    }
}
