package com.app.server.peer;

import com.app.server.dao.ClienteConectadoDAO;
import com.app.server.events.ServerEventBus;
import com.app.server.events.ServerEventType;
import com.app.server.service.DocumentoService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Servidor TCP dedicado a comunicacion entre peers (server-to-server).
 *
 * <p>Escucha en un puerto exclusivo (no compartido con clientes). Por cada
 * conexion entrante crea una {@link PeerSession} en un hilo daemon. Las
 * sesiones reutilizan el protocolo JSON-linea del cliente para los comandos
 * de control y stream binario crudo para los datos de archivo.</p>
 */
public class PeerServer {

    private final int port;
    private final PeerRegistry registry;
    private final DocumentoService documentoService;
    private final ClienteConectadoDAO clienteDAO;
    private final ServerEventBus eventBus;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running = false;

    public PeerServer(int port,
                      PeerRegistry registry,
                      DocumentoService documentoService,
                      ClienteConectadoDAO clienteDAO,
                      ServerEventBus eventBus) {
        this.port = port;
        this.registry = registry;
        this.documentoService = documentoService;
        this.clienteDAO = clienteDAO;
        this.eventBus = eventBus;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        serverSocket = new ServerSocket(port);
        running = true;
        acceptThread = new Thread(this::loopAccept, "peer-server-accept");
        acceptThread.setDaemon(false);
        acceptThread.start();
        System.out.println("[PEER] PeerServer escuchando en puerto " + port);
    }

    public synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) { }
        try {
            if (acceptThread != null) acceptThread.join(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int getPort() { return port; }

    private void loopAccept() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                if (eventBus != null) {
                    eventBus.publish(ServerEventType.PEER_CONEXION_ENTRANTE, "peer-server",
                            socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
                }
                PeerSession session = new PeerSession(socket, registry, documentoService, clienteDAO, eventBus);
                Thread t = new Thread(session, "peer-session-" + socket.getInetAddress().getHostAddress());
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (running) {
                    System.err.println("[PEER] Error aceptando peer: " + e.getMessage());
                    if (eventBus != null) eventBus.publishError("peer-server", e, null);
                }
            }
        }
    }
}
