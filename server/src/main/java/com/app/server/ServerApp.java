package com.app.server;

import com.app.server.dao.ClienteConectadoDAO;
import com.app.server.dao.DatabaseConnection;
import com.app.server.dao.LogDAO;
import com.app.server.events.ConsoleServerEventListener;
import com.app.server.events.InMemoryEventBuffer;
import com.app.server.events.ServerEvent;
import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.server.models.Log;
import com.app.server.http.HttpGateway;
import com.app.server.net.ServerCore;
import com.app.server.peer.PeerCatalog;
import com.app.server.peer.PeerClient;
import com.app.server.peer.PeerDiscoveryService;
import com.app.server.peer.PeerInfo;
import com.app.server.peer.PeerProxyService;
import com.app.server.peer.PeerRegistry;
import com.app.server.peer.PeerServer;
import com.app.server.service.DocumentoService;
import com.app.server.service.LogService;
import com.app.server.util.NetworkUtils;
import com.app.server.util.SessionLogManager;
import com.app.shared.util.CryptoUtil;

import javax.crypto.SecretKey;
import java.net.InetAddress;
import java.util.Scanner;
import java.util.UUID;

/**
 * Punto de entrada del servidor.
 *
 * <p>Soporta varios servidores corriendo en una misma LAN: el descubrimiento
 * UDP broadcast los conecta automaticamente y el catalogo cacheado permite
 * que un cliente vea documentos de otros servidores. Las descargas se hacen
 * por proxy desde el servidor local.</p>
 *
 * <p>Argumentos opcionales (mediante variables de entorno o flags
 * {@code --clave=valor}):</p>
 * <ul>
 *   <li>{@code --tcp=9000}</li>
 *   <li>{@code --udp=9001}</li>
 *   <li>{@code --http=8080}</li>
 *   <li>{@code --peer=9100}     puerto TCP entre servidores</li>
 *   <li>{@code --discovery=9200} puerto UDP broadcast</li>
 *   <li>{@code --max=10}        clientes maximos por pool</li>
 *   <li>{@code --peers=off}     deshabilita el modulo P2P</li>
 * </ul>
 */
public class ServerApp {

    private static final int DEFAULT_TCP_PORT = 9000;
    private static final int DEFAULT_UDP_PORT = 9001;
    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final int DEFAULT_PEER_PORT = 9100;
    private static final int DEFAULT_DISCOVERY_PORT = 9200;
    private static final int DEFAULT_MAX_CLIENTS = 10;

    public static void main(String[] args) {
        Args parsed = Args.parse(args);
        SessionLogManager sessionLog = null;
        int exitCode = 0;

        try { sessionLog = SessionLogManager.start(); }
        catch (Exception e) { System.err.println("[LOG] No se pudo iniciar log de sesion: " + e.getMessage()); }

        String nombreNodo = parsed.nombre != null ? parsed.nombre : detectarHostname();
        String ipLan = parsed.bindHost != null ? parsed.bindHost : detectarIpLan();

        System.out.println("============================================");
        System.out.println("  SERVIDOR DE MENSAJERIA Y ARCHIVOS (P2P)");
        System.out.println("  Nombre: " + nombreNodo);
        System.out.println("  Hostname SO: " + detectarHostname());
        System.out.println("  IP LAN detectada: " + ipLan
                + (parsed.bindHost != null ? "  (forzada via --host)" : "  (automatica)"));
        System.out.println("  TCP: " + parsed.tcpPort + " | UDP: " + parsed.udpPort
                + " | HTTP: " + parsed.httpPort);
        if (parsed.peersEnabled) {
            System.out.println("  Peer TCP: " + parsed.peerPort + " | Discovery UDP: " + parsed.discoveryPort);
        }
        System.out.println("--- Interfaces de red ---");
        for (String linea : NetworkUtils.describirInterfaces()) {
            System.out.println("  " + linea);
        }
        if (parsed.bindHost == null) {
            var candidatos = NetworkUtils.enumerarIpv4LAN();
            if (candidatos.size() > 1) {
                System.out.println("  (varias IPs candidatas; usa --host=<ip> si elige la equivocada:");
                for (var c : candidatos) System.out.println("     - " + c.getHostAddress());
                System.out.println("  )");
            }
        }
        System.out.println("============================================");

        ServerEventBus eventBus = new ServerEventBus();
        eventBus.subscribe(new ConsoleServerEventListener());
        InMemoryEventBuffer eventBuffer = new InMemoryEventBuffer();
        eventBus.subscribe(eventBuffer);

        ServerCore server = null;
        HttpGateway httpGateway = null;
        PeerServer peerServer = null;
        PeerDiscoveryService discovery = null;
        PeerCatalog peerCatalog = null;

        try {
            System.out.println("[INIT] Conectando a MySQL...");
            DatabaseConnection.getInstance().init();
            new ClienteConectadoDAO().limpiarTodos();

            SecretKey sessionKey = CryptoUtil.generateAESKey();
            System.out.println("[INIT] Clave AES-256 de sesion generada.");

            LogService logService = new LogService();
            DocumentoService documentoService = new DocumentoService(sessionKey, eventBus);

            PeerRegistry peerRegistry = null;
            PeerProxyService peerProxy = null;

            if (parsed.peersEnabled) {
                String localId = UUID.randomUUID().toString();
                String host = ipLan;
                String nombre = parsed.nombre != null ? parsed.nombre : detectarHostname();
                PeerInfo selfInfo = new PeerInfo(localId, nombre, host, parsed.peerPort,
                        parsed.tcpPort, parsed.udpPort);
                peerRegistry = new PeerRegistry(localId, eventBus);
                PeerClient peerClient = new PeerClient(selfInfo);
                peerCatalog = new PeerCatalog(peerRegistry, peerClient, eventBus);
                peerProxy = new PeerProxyService(peerRegistry, peerClient, eventBus);

                peerServer = new PeerServer(parsed.peerPort, peerRegistry, documentoService, eventBus);
                peerServer.start();

                discovery = new PeerDiscoveryService(peerRegistry, eventBus, selfInfo,
                        parsed.discoveryPort, PeerDiscoveryService.DEFAULT_HEARTBEAT_MILLIS);
                discovery.start();

                peerCatalog.start();

                System.out.println("[PEER] Servidor local nombre=" + nombre
                        + " id=" + localId.substring(0, 8) + "..."
                        + " host=" + host);
            } else {
                System.out.println("[INIT] Modulo P2P deshabilitado (--peers=off)");
            }

            LogDAO logDAO = new LogDAO();
            server = new ServerCore(parsed.tcpPort, parsed.udpPort,
                    parsed.maxClients, parsed.maxClients,
                    documentoService, logService, eventBus,
                    peerRegistry, peerCatalog, peerProxy,
                    eventBuffer, logDAO);
            server.start();

            httpGateway = new HttpGateway(parsed.httpPort, documentoService, logService,
                    peerRegistry, eventBuffer);
            httpGateway.start();
            System.out.println("[HTTP] Interfaz web disponible en:");
            System.out.println("       http://localhost:" + parsed.httpPort);
            System.out.println("       http://" + ipLan + ":" + parsed.httpPort + "  (LAN)");

            logService.registrar("SERVIDOR_INICIADO", "localhost",
                    "TCP:" + parsed.tcpPort + " UDP:" + parsed.udpPort
                            + " HTTP:" + parsed.httpPort + " Peers:" + parsed.peersEnabled);

            mostrarComandosConsola(parsed.peersEnabled);
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.equalsIgnoreCase("exit")) break;
                procesarConsola(line, server, peerRegistry, peerCatalog, eventBus,
                        eventBuffer, logDAO);
            }

            System.out.println("[SHUTDOWN] Deteniendo servidor...");
        } catch (Exception e) {
            System.err.println("[ERROR FATAL] " + e.getMessage());
            e.printStackTrace();
            exitCode = 1;
        } finally {
            try { if (httpGateway != null) httpGateway.stop(); } catch (Exception ignored) { }
            try { if (server != null) server.stop(); } catch (Exception ignored) { }
            try { if (peerCatalog != null) peerCatalog.stop(); } catch (Exception ignored) { }
            try { if (discovery != null) discovery.stop(); } catch (Exception ignored) { }
            try { if (peerServer != null) peerServer.stop(); } catch (Exception ignored) { }
            try { DatabaseConnection.getInstance().shutdown(); } catch (Exception ignored) { }
            try {
                eventBus.publish(ServerEventType.SERVIDOR_DETENIDO, "ServerApp", "shutdown");
                eventBus.shutdown();
            } catch (Exception ignored) { }
            if (sessionLog != null) sessionLog.close();
            System.out.println("[SHUTDOWN] Servidor detenido correctamente.");
        }

        if (exitCode != 0) System.exit(exitCode);
    }

    private static void mostrarComandosConsola(boolean peers) {
        System.out.println("\nComandos:");
        System.out.println("  status         | clientes en pool TCP/UDP");
        System.out.println("  events on/off  | activa/desactiva log de eventos en consola");
        System.out.println("  events [N]     | imprime los ultimos N eventos en memoria (default 20)");
        System.out.println("  logs [N]       | imprime los ultimos N logs de BD (default 20)");
        if (peers) {
            System.out.println("  peers          | lista peers en linea");
            System.out.println("  remotos        | documentos publicados por peers");
        }
        System.out.println("  exit           | apaga el servidor");
        System.out.println();
    }

    private static void procesarConsola(String line, ServerCore server,
                                        PeerRegistry registry, PeerCatalog catalog,
                                        ServerEventBus eventBus,
                                        InMemoryEventBuffer eventBuffer, LogDAO logDAO) {
        if (line.equalsIgnoreCase("status")) {
            System.out.println("TCP: " + server.getTcpPool().getActiveCount() + "/" + server.getTcpPool().getMaxClients());
            System.out.println("UDP: " + server.getUdpPool().getActiveCount() + "/" + server.getUdpPool().getMaxClients());
        } else if (line.equalsIgnoreCase("events on")) {
            eventBus.setEnabled(true);
            System.out.println("[EVT] listeners activados");
        } else if (line.equalsIgnoreCase("events off")) {
            eventBus.setEnabled(false);
            System.out.println("[EVT] listeners desactivados");
        } else if (line.toLowerCase().startsWith("events")) {
            int limit = parseLimitArg(line, "events", 20);
            for (ServerEvent ev : eventBuffer.snapshot(limit)) {
                System.out.println("  " + ev);
            }
            System.out.println("(" + Math.min(limit, eventBuffer.size()) + "/" + eventBuffer.size() + " eventos)");
        } else if (line.toLowerCase().startsWith("logs")) {
            int limit = parseLimitArg(line, "logs", 20);
            try {
                for (Log l : logDAO.listarUltimos(limit)) {
                    System.out.println("  [" + l.getFechaHora() + "] " + l.getAccion()
                            + " ip=" + l.getIpOrigen()
                            + (l.getDetalles() != null ? " | " + l.getDetalles() : ""));
                }
            } catch (Exception e) {
                System.err.println("[CONSOLA] Error consultando logs: " + e.getMessage());
            }
        } else if (line.equalsIgnoreCase("peers")) {
            if (registry == null) { System.out.println("P2P deshabilitado"); return; }
            var online = registry.listarOnline();
            System.out.println("Peers en linea: " + online.size());
            for (PeerInfo p : online) {
                System.out.println("  " + p.getNombre() + " (" + p.getId().substring(0, 8) + ")  "
                        + p.getHost() + ":" + p.getPuertoPeer()
                        + "  tcp=" + p.getPuertoTcp() + " udp=" + p.getPuertoUdp()
                        + "  ultimaSenal=" + p.getUltimaSenal());
            }
        } else if (line.equalsIgnoreCase("remotos")) {
            if (catalog == null) { System.out.println("P2P deshabilitado"); return; }
            var remotos = catalog.listarRemotos();
            System.out.println("Documentos remotos: " + remotos.size());
            for (PeerCatalog.RemoteDocumento d : remotos) {
                System.out.println("  [" + d.getPeerId().substring(0, 8) + "] id=" + d.getId()
                        + " " + d.getNombre() + " (" + d.getTamano() + " B)");
            }
        } else if (!line.isEmpty()) {
            System.out.println("Comando desconocido: " + line);
        }
    }

    private static int parseLimitArg(String line, String cmd, int defaultValue) {
        String rest = line.substring(cmd.length()).trim();
        if (rest.isEmpty()) return defaultValue;
        try { return Math.max(1, Integer.parseInt(rest)); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    /**
     * Detecta una IPv4 LAN. Delega a {@link NetworkUtils} que filtra
     * interfaces virtuales (Docker, Hyper-V, VirtualBox, VPN, ...) y prefiere
     * rangos privados (192.168/16 &gt; 10/8 &gt; 172.16-31/12).
     */
    static String detectarIpLan() {
        return NetworkUtils.detectarIpLan();
    }

    /** Nombre legible del nodo: hostname del SO o "servidor-N" si falla. */
    static String detectarHostname() {
        try {
            String h = InetAddress.getLocalHost().getHostName();
            if (h != null && !h.isBlank()) return h;
        } catch (Exception ignored) { }
        return "servidor";
    }

    /** Parser simple de argumentos {@code --clave=valor}. */
    private static final class Args {
        int tcpPort = DEFAULT_TCP_PORT;
        int udpPort = DEFAULT_UDP_PORT;
        int httpPort = DEFAULT_HTTP_PORT;
        int peerPort = DEFAULT_PEER_PORT;
        int discoveryPort = DEFAULT_DISCOVERY_PORT;
        int maxClients = DEFAULT_MAX_CLIENTS;
        boolean peersEnabled = true;
        String bindHost = null;
        String nombre = null;

        static Args parse(String[] args) {
            Args a = new Args();
            for (String raw : args) {
                if (!raw.startsWith("--")) continue;
                int eq = raw.indexOf('=');
                if (eq < 0) continue;
                String key = raw.substring(2, eq);
                String val = raw.substring(eq + 1);
                try {
                    switch (key) {
                        case "tcp": a.tcpPort = Integer.parseInt(val); break;
                        case "udp": a.udpPort = Integer.parseInt(val); break;
                        case "http": a.httpPort = Integer.parseInt(val); break;
                        case "peer": a.peerPort = Integer.parseInt(val); break;
                        case "discovery": a.discoveryPort = Integer.parseInt(val); break;
                        case "max": a.maxClients = Integer.parseInt(val); break;
                        case "peers": a.peersEnabled = !"off".equalsIgnoreCase(val); break;
                        case "host": a.bindHost = val; break;
                        case "nombre":
                        case "name": a.nombre = val; break;
                        default: System.err.println("[ARGS] Opcion desconocida: " + key);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("[ARGS] Valor invalido para " + key + ": " + val);
                }
            }
            return a;
        }
    }
}
