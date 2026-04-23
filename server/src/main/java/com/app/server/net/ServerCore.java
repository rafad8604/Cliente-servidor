package com.app.server.net;

import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.server.pool.SemaphoreResourcePool;
import com.app.server.service.DocumentoService;
import com.app.server.service.LogService;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Núcleo del servidor.
 *
 * Mantiene dos {@link ClientPool}s separados (uno para TCP, otro para UDP).
 * Cada pool es un adapter sobre un {@code ResourcePool} basado en Semaphore.
 * El servidor depende únicamente de estas abstracciones.
 */
public class ServerCore {

    private final int tcpPort;
    private final int udpPort;
    private final ClientPool tcpPool;
    private final ClientPool udpPool;
    private final DocumentoService documentoService;
    private final LogService logService;
    private final ServerEventBus eventBus;

    private ServerSocket tcpServer;
    private DatagramSocket udpSocket;
    private Thread tcpThread;
    private Thread udpThread;
    private UdpHandler udpHandler;
    private volatile boolean running = false;

    public ServerCore(int tcpPort, int udpPort, int maxClients,
                      DocumentoService documentoService, LogService logService) {
        this(tcpPort, udpPort, maxClients, maxClients, documentoService, logService, null);
    }

    public ServerCore(int tcpPort, int udpPort, int tcpMax, int udpMax,
                      DocumentoService documentoService, LogService logService,
                      ServerEventBus eventBus) {
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.documentoService = documentoService;
        this.logService = logService;
        this.eventBus = eventBus;
        this.tcpPool = new ClientPool(new SemaphoreResourcePool("tcp-pool", tcpMax), eventBus);
        this.udpPool = new ClientPool(new SemaphoreResourcePool("udp-pool", udpMax), eventBus);
    }

    public void start() throws IOException {
        running = true;

        tcpServer = new ServerSocket(tcpPort);
        tcpThread = new Thread(this::runTcp, "tcp-listener");
        tcpThread.setDaemon(false);
        tcpThread.start();
        System.out.println("[SERVER] TCP escuchando en puerto " + tcpPort);

        udpSocket = new DatagramSocket(udpPort);
        udpHandler = new UdpHandler(udpSocket, documentoService, logService, udpPool, eventBus);
        udpThread = new Thread(udpHandler, "udp-listener");
        udpThread.setDaemon(true);
        udpThread.start();
        System.out.println("[SERVER] UDP escuchando en puerto " + udpPort);

        System.out.println("[SERVER] Pools -> tcp:" + tcpPool.getMaxClients()
                + " udp:" + udpPool.getMaxClients());

        if (eventBus != null) {
            eventBus.publish(ServerEventType.SERVIDOR_INICIADO, "ServerCore",
                    "tcp=" + tcpPort + " udp=" + udpPort);
        }
    }

    private void runTcp() {
        while (running) {
            try {
                Socket clientSocket = tcpServer.accept();
                System.out.println("[TCP] Nueva conexión de: " +
                        clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());

                if (tcpPool.tryAcquire()) {
                    TcpClientChannel channel = new TcpClientChannel(clientSocket);
                    ClientHandler handler = new ClientHandler(channel, tcpPool,
                            documentoService, logService, eventBus);
                    tcpPool.registerHandler(handler);

                    if (eventBus != null) {
                        eventBus.publish(ServerEventType.TCP_CONEXION_ABIERTA,
                                channel.getContext(), null);
                    }

                    Thread handlerThread = new Thread(handler,
                            "client-" + clientSocket.getInetAddress().getHostAddress());
                    handlerThread.setDaemon(true);
                    handlerThread.start();
                } else {
                    System.out.println("[TCP] Pool lleno (" + tcpPool.getActiveCount() +
                            "/" + tcpPool.getMaxClients() + "). Rechazando conexión.");
                    if (eventBus != null) {
                        eventBus.publish(ServerEventType.TCP_CONEXION_RECHAZADA,
                                new ClientContext(
                                        clientSocket.getInetAddress().getHostAddress(),
                                        clientSocket.getPort(), "TCP"),
                                "pool lleno");
                    }
                    try {
                        clientSocket.getOutputStream().write(
                                "{\"comando\":\"ERROR\",\"datos\":{\"status\":\"ERROR\",\"detalle\":\"Servidor lleno\"},\"timestamp\":\"\"}\n"
                                        .getBytes());
                        clientSocket.close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[TCP] Error aceptando conexión: " + e.getMessage());
                    if (eventBus != null) {
                        eventBus.publishError("tcp-listener", e, null);
                    }
                }
            }
        }
    }

    public void stop() {
        running = false;

        tcpPool.shutdownAll();

        try {
            if (tcpServer != null && !tcpServer.isClosed()) tcpServer.close();
        } catch (IOException e) {
            System.err.println("[SERVER] Error cerrando TCP: " + e.getMessage());
        }

        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        if (udpHandler != null) udpHandler.stop();

        try {
            if (tcpThread != null) tcpThread.join(5000);
            if (udpThread != null) udpThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (eventBus != null) {
            eventBus.publish(ServerEventType.SERVIDOR_DETENIDO, "ServerCore", null);
        }
        System.out.println("[SERVER] Servidor detenido.");
    }

    /**
     * Compat: devuelve el pool TCP (usado por status de consola original).
     */
    public ClientPool getClientPool() {
        return tcpPool;
    }

    public ClientPool getTcpPool() {
        return tcpPool;
    }

    public ClientPool getUdpPool() {
        return udpPool;
    }

    public boolean isRunning() {
        return running;
    }
}
