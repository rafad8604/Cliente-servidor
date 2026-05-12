package com.app.server.peer;

import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.shared.protocol.Comando;
import com.app.shared.protocol.Mensaje;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de descubrimiento de peers via UDP broadcast.
 *
 * <p>Cada servidor:
 * <ol>
 *   <li>Abre un {@link DatagramSocket} en {@link #discoveryPort} con {@code SO_REUSEADDR}
 *       y {@code SO_BROADCAST} habilitado.</li>
 *   <li>Lanza un hilo lector que parsea {@link Comando#PEER_HELLO} y aplica el
 *       registro al {@link PeerRegistry}.</li>
 *   <li>Lanza una tarea programada que envia su propio {@code PEER_HELLO} en
 *       broadcast cada {@link #heartbeatMillis}.</li>
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

        System.out.println("[PEER] Descubrimiento iniciado en puerto " + discoveryPort);
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

        System.out.println("[PEER] Descubrimiento detenido");
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
                if (running) System.err.println("[PEER] Socket discovery cerrado: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("[PEER] Error receptor: " + e.getMessage());
            }
        }
    }

    private void procesarMensaje(byte[] data, InetAddress origen) {
        try {
            Mensaje msg = Mensaje.fromJson(new String(data, StandardCharsets.UTF_8));
            if (msg.getComando() == null) return;

            String peerId = msg.getString("id");
            if (peerId == null || peerId.equals(selfInfo.getId())) return;

            switch (msg.getComando()) {
                case PEER_HELLO: {
                    String host = msg.getString("host");
                    if (host == null) host = origen.getHostAddress();
                    String nombre = msg.getString("nombre");
                    int puertoPeer = msg.getInt("puertoPeer");
                    int puertoTcp = msg.getInt("puertoTcp");
                    int puertoUdp = msg.getInt("puertoUdp");
                    PeerInfo info = new PeerInfo(peerId, nombre, host, puertoPeer, puertoTcp, puertoUdp);
                    PeerInfo aplicado = registry.aplicarHello(info);
                    if (aplicado != null && eventBus != null) {
                        eventBus.publish(ServerEventType.PEER_HELLO_RECIBIDO, "discovery",
                                aplicado.toString());
                    }
                    break;
                }
                case PEER_BYE: {
                    registry.remover(peerId);
                    break;
                }
                default:
                    // ignorar otros comandos por el canal de descubrimiento
            }
        } catch (Exception e) {
            // mensajes mal formados se ignoran silenciosamente para no llenar el log
        }
    }

    private void enviarHelloBroadcast() {
        try {
            byte[] payload = buildHelloPayload(Comando.PEER_HELLO);
            broadcast(payload);
        } catch (Exception e) {
            System.err.println("[PEER] No se pudo enviar hello: " + e.getMessage());
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

    private void broadcast(byte[] payload) throws IOException {
        InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
        DatagramPacket dp = new DatagramPacket(payload, payload.length, broadcastAddr, discoveryPort);
        socket.send(dp);
    }
}
