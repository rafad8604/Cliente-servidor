package com.app.server.net;

import com.app.server.service.DocumentoService;
import com.app.server.service.LogService;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Núcleo del servidor. Arranca los listeners TCP y UDP en hilos separados.
 */
public class ServerCore {

    private final int tcpPort;
    private final int udpPort;
    private final ClientPool clientPool;
    private final DocumentoService documentoService;
    private final LogService logService;

    private ServerSocket tcpServer;
    private DatagramSocket udpSocket;
    private Thread tcpThread;
    private Thread udpThread;
    private volatile boolean running = false;

    public ServerCore(int tcpPort, int udpPort, int maxClients, DocumentoService documentoService,
                      LogService logService) {
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.clientPool = new ClientPool(maxClients);
        this.documentoService = documentoService;
        this.logService = logService;
    }

    /**
     * Inicia el servidor TCP y UDP.
     */
    public void start() throws IOException {
        running = true;

        // Iniciar TCP
        tcpServer = new ServerSocket(tcpPort);
        tcpThread = new Thread(this::runTcp, "tcp-listener");
        tcpThread.setDaemon(false);
        tcpThread.start();
        System.out.println("[SERVER] TCP escuchando en puerto " + tcpPort);

        // Iniciar UDP
        udpSocket = new DatagramSocket(udpPort);
        UdpHandler udpHandler = new UdpHandler(udpSocket, documentoService, logService);
        udpThread = new Thread(udpHandler, "udp-listener");
        udpThread.setDaemon(true);
        udpThread.start();
        System.out.println("[SERVER] UDP escuchando en puerto " + udpPort);

        System.out.println("[SERVER] Servidor iniciado. Pool máximo: " + clientPool.getMaxClients() + " clientes.");
    }

    private void runTcp() {
        while (running) {
            try {
                Socket clientSocket = tcpServer.accept();
                System.out.println("[TCP] Nueva conexión de: " +
                        clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());

                // Intentar adquirir slot en el pool
                if (clientPool.tryAcquire()) {
                    ClientHandler handler = new ClientHandler(clientSocket, clientPool,
                            documentoService, logService);
                    clientPool.registerHandler(handler);

                    Thread handlerThread = new Thread(handler,
                            "client-" + clientSocket.getInetAddress().getHostAddress());
                    handlerThread.setDaemon(true);
                    handlerThread.start();
                } else {
                    // Pool lleno - rechazar conexión
                    System.out.println("[TCP] Pool lleno (" + clientPool.getActiveCount() +
                            "/" + clientPool.getMaxClients() + "). Rechazando conexión.");
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
                }
            }
        }
    }

    /**
     * Detiene el servidor de forma ordenada.
     */
    public void stop() {
        running = false;

        // Cerrar todos los handlers activos
        clientPool.shutdownAll();

        // Cerrar sockets
        try {
            if (tcpServer != null && !tcpServer.isClosed()) {
                tcpServer.close();
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Error cerrando TCP: " + e.getMessage());
        }

        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }

        // Esperar a que los hilos terminen
        try {
            if (tcpThread != null) tcpThread.join(5000);
            if (udpThread != null) udpThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("[SERVER] Servidor detenido.");
    }

    /**
     * Obtiene el pool de clientes (para monitoreo).
     */
    public ClientPool getClientPool() {
        return clientPool;
    }

    public boolean isRunning() {
        return running;
    }
}
